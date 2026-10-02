package com.sentinel.core.vpn

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.sentinel.core.logs.LogDatabase
import com.sentinel.core.logs.LogManager
import com.sentinel.core.rules.RuleEngine
import com.sentinel.core.rules.TrustedDomainsCache
import com.sentinel.ui.logs.PacketLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TacU-NSFW VpnService – Stability-First + Real-Time Rule Updates
 *
 * Design Principles:
 * 1. Single-instance enforcement: never establishes twice.
 * 2. Zero packet processing: no InputStreams, no loops, no CPU usage.
 * 3. Clean lifecycle: guaranteed resource release in all exit paths.
 * 4. ACTION_RESTART: re-builds tunnel with updated rules without UI flicker.
 *    _isRunning stays TRUE during the brief teardown/re-establish cycle so
 *    the Dashboard never flashes OFF.
 */
class SentinelVpnService : VpnService() {

    companion object {
        private const val TAG = "SentinelVPN"
        const val ACTION_STOP         = "com.sentinel.ACTION_STOP"
        const val ACTION_RESTART      = "com.sentinel.ACTION_RESTART"      // Rebuilds VPN tunnel with updated app policies
        const val ACTION_RELOAD_RULES = "com.sentinel.ACTION_RELOAD_RULES" // Reloads rule caches without tunnel rebuild
        // Posts the protection notification again. Android drops a foreground notification
        // posted while notifications were blocked and never shows it after they are allowed
        // again (emulator: service isForeground=true but no NotificationRecord for the app).
        const val ACTION_REFRESH_NOTIFICATION = "com.sentinel.ACTION_REFRESH_NOTIFICATION"

        // Single source of truth for VPN running state — observed by the UI.
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var active = false
    private val isRestarting = AtomicBoolean(false)
    private var restartJob: Job? = null
    private var notifJob: Job? = null
    private var dnsInterceptor: DnsInterceptor? = null

    // Tracks whether the device is currently on WiFi or Cellular.
    // Updated by buildTunnel() and the NetworkCallback so DnsInterceptor
    // always reads the correct transport type for per-packet blocking decisions.
    @Volatile private var currentNetworkIsWifi = true
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private lateinit var ruleEngine: RuleEngine

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        LogManager.initialize(applicationContext)
        ruleEngine = RuleEngine.getInstance(this)
        serviceScope.launch {
            try { ruleEngine.reloadCache() } catch (e: Exception) {
                Log.w(TAG, "Cache preload failed: ${e.message}")
            }
        }
        // Load community blocklist into memory — async, never delays VPN start
        serviceScope.launch(Dispatchers.IO) {
            try { ruleEngine.reloadBlocklist() } catch (e: Exception) {
                Log.w(TAG, "Blocklist load failed: ${e.message}")
            }
        }
        // Look-Alike Domain Shield: preload the trusted-domain cache — async, never delays VPN start.
        // Seeded with a static list by default, so DnsInterceptor has a usable cache from packet one.
        serviceScope.launch(Dispatchers.IO) {
            try { TrustedDomainsCache.refresh(applicationContext) } catch (e: Exception) {
                Log.w(TAG, "Trusted domains preload failed: ${e.message}")
            }
        }
        // BlocklistSyncWorker.schedule() is called from SentinelActivity (main process).
        // WorkManager is not initialized in the :vpn process — calling it here crashes the service.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP    -> { stopVpn(); START_NOT_STICKY }
            ACTION_REFRESH_NOTIFICATION -> {
                if (active) {
                    serviceScope.launch {
                        try {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                            }
                            val count = LogDatabase.getInstance(applicationContext).packetLogDao()
                                .countByStatusSince("BLOCKED", cal.timeInMillis)
                            NotificationHelper.updateNotification(applicationContext, count)
                        } catch (e: Exception) {
                            Log.w(TAG, "Notification refresh failed: ${e.message}")
                        }
                    }
                }
                START_STICKY
            }
            ACTION_RESTART -> { scheduleRestart(); START_STICKY }
            ACTION_RELOAD_RULES -> {
                // Reloads user rules + blocklist into this process's RuleEngine cache.
                // Called by the UI process after domain/schedule/blocklist changes so
                // the VPN interceptor sees new rules without a full tunnel rebuild.
                serviceScope.launch {
                    try {
                        ruleEngine.reloadCache()
                        ruleEngine.reloadBlocklist()
                        Log.i(TAG, "Rules reloaded from UI request.")
                        // A changed DNS server shows on the DNS screen at once, not only
                        // after the next lookup some app happens to make.
                        dnsInterceptor?.probeDns()
                    } catch (e: Exception) {
                        Log.w(TAG, "Rule reload failed: ${e.message}")
                    }
                }
                START_STICKY
            }
            else           -> {
                if (!active) startVpn()
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
        serviceScope.cancel()
    }

    // Called when the user disconnects this VPN in Android's settings or another VPN app takes
    // over. That is a deliberate stop, so the app must not turn protection back on by itself.
    override fun onRevoke() {
        Log.i(TAG, "VPN revoked by the user or another VPN app.")
        com.sentinel.core.alerts.SecurityAlerts.record(this, com.sentinel.core.alerts.AlertType.PROTECTION_STOPPED, com.sentinel.core.alerts.AlertSeverity.HIGH)
        ProtectionIntent.setWanted(this, false)
        super.onRevoke()
    }

    // -------------------------------------------------------------------------
    // VPN Start
    // -------------------------------------------------------------------------

    private fun startVpn() {
        // startForeground MUST be called synchronously on the calling thread within
        // a few seconds of onStartCommand — it stays here, on the main thread.
        try {
            val notification = NotificationHelper.createNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.getNotificationId(),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationHelper.getNotificationId(), notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            releaseResources()
            return
        }

        // Private DNS in strict mode and this firewall cannot run together: Android then
        // rejects the firewall's answers and the phone loses its internet (PrivateDnsGuard).
        PrivateDnsGuard.strictHostname(this)?.let { provider ->
            pauseForPrivateDns(provider)
            return
        }

        ProtectionIntent.setWanted(this, true)

        // Watch for WiFi ↔ Cellular switches so the tunnel can be rebuilt with the
        // correct per-transport blocked-apps list when the user changes networks.
        registerNetworkTypeCallback()

        // All heavy work (cache reload + tunnel build) moves off the main thread.
        // Previously this used runBlocking which blocked onStartCommand's main thread
        // and caused ANRs when Room was under load after extended runtime.
        serviceScope.launch(Dispatchers.IO) {
            try { buildTunnel() } catch (e: Exception) {
                Log.e(TAG, "buildTunnel failed", e)
                isRestarting.set(false)
                stopAfterFailedStart()
            }
        }
    }

    /**
     * No tunnel could be started and none was running. Releasing alone left the service in
     * the foreground with its "Active" notification, so the phone looked protected while
     * nothing was filtered (seen on the emulator after VPN permission was taken away).
     * The user's wish to be protected is kept, so opening the app can try again.
     */
    private fun stopAfterFailedStart() {
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Stops (or does not start) protection because Private DNS is set to a provider, and says
     * why. Keeping the tunnel would leave the phone without internet. The user's wish is set
     * to off so opening the app does not start it again into the same problem.
     */
    private fun pauseForPrivateDns(provider: String) {
        Log.w(TAG, "Private DNS is in strict mode; protection paused to keep the internet working.")
        ProtectionIntent.setWanted(this, false)
        com.sentinel.core.alerts.SecurityAlerts.record(this,
            com.sentinel.core.alerts.AlertType.PRIVATE_DNS_CONFLICT,
            com.sentinel.core.alerts.AlertSeverity.HIGH, target = provider)
        SecurityAlertNotifier.notifyPrivateDnsPaused(this, provider)
        restartJob?.cancel()
        isRestarting.set(false)
        stopAfterFailedStart()
    }

    // Returns true if the device is currently connected via WiFi, false for Cellular.
    // Iterates allNetworks (system-wide) and skips the VPN network so the physical
    // transport type is always returned correctly even in full-tunnel mode.
    private fun detectCurrentNetworkIsWifi(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true
            }
        }
        return false
    }

    // Registers a ConnectivityManager callback that rebuilds the tunnel whenever
    // the device switches between WiFi and Cellular transport.  Only fires on an
    // actual transport-type change (WiFi → Cellular or vice versa) — unrelated
    // network events (signal strength, IP changes) are ignored.
    private fun registerNetworkTypeCallback() {
        if (networkCallback != null) return  // already registered
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val nowWifi = detectCurrentNetworkIsWifi()
                if (nowWifi != currentNetworkIsWifi) {
                    Log.i(TAG, "Transport changed → ${if (nowWifi) "WiFi" else "Cellular"}, rebuilding tunnel")
                    currentNetworkIsWifi = nowWifi
                    if (active) scheduleRestart()
                }
            }
            override fun onLinkPropertiesChanged(network: Network, lp: android.net.LinkProperties) {
                // Private DNS switched to a provider while protection is on: stop, or the
                // phone loses its internet (see PrivateDnsGuard).
                val caps = getSystemService(ConnectivityManager::class.java)?.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return
                val provider = PrivateDnsGuard.strictHostname(lp) ?: return
                if (active) serviceScope.launch(Dispatchers.Main) { if (active) pauseForPrivateDns(provider) }
            }
            override fun onLost(network: Network) {
                val nowWifi = detectCurrentNetworkIsWifi()
                if (nowWifi != currentNetworkIsWifi) {
                    currentNetworkIsWifi = nowWifi
                    if (active) scheduleRestart()
                }
            }
        }
        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkTypeCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) { }
    }

    /**
     * Builds (or atomically reconfigures) the VPN tunnel on the IO dispatcher.
     *
     * Two routing modes:
     *   • DNS-only  (0 apps blocked): addRoute("10.0.0.1", 32) — only DNS enters tun.
     *     Domain blocking via NXDOMAIN works for all apps.
     *   • Full-tunnel (≥1 app blocked): addRoute("0.0.0.0", 0) +
     *       addDisallowedApplication(pkg) for every NON-blocked app — only the blocked
     *       apps' entire traffic (DNS + TCP + UDP) enters tun. Non-blocked apps bypass
     *       VPN entirely. DnsInterceptor returns NXDOMAIN AND RSTs TCP so blocked apps
     *       cannot bypass via DoH or cached IPs.
     *
     * Atomic replacement: establish() is called WITHOUT closing the old tunnel first.
     * Android automatically closes the previous tun fd when establish() succeeds,
     * so there is no window where no VPN is active. If establish() fails, the old
     * tunnel remains alive and continues serving (graceful degradation — VPN stays
     * online in the previous mode, and DNS-layer blocking still applies).
     */
    private suspend fun buildTunnel() {
        try { ruleEngine.reloadCache() } catch (e: Exception) {
            Log.w(TAG, "Cache preload failed: ${e.message}")
        }

        // Detect physical network type so the tunnel only routes the apps that are
        // actually blocked on the current transport (WiFi-blocked apps bypass when
        // on Cellular, and vice versa).
        currentNetworkIsWifi = detectCurrentNetworkIsWifi()
        val blockedPackages = ruleEngine.getBlockedPackages(currentNetworkIsWifi)

        val builder = Builder()
            .setSession("TacU-NS Firewall")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.1")
            .setMtu(1500)
            .setBlocking(true)

        if (blockedPackages.isEmpty()) {
            // Split tunnel: only DNS traffic enters tun. All other traffic bypasses VPN.
            builder.addRoute("10.0.0.1", 32)
            Log.i(TAG, "Mode: DNS-only (no apps blocked)")
        } else {
            // Pre-verify packages exist before choosing tunnel mode — prevents a full-tunnel
            // with an empty allowlist if all blocked packages are stale/uninstalled.
            val verifiedPackages = blockedPackages.filter { pkg ->
                try { packageManager.getApplicationInfo(pkg, 0); true }
                catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Blocked app $pkg no longer installed, skipping"); false
                }
            }
            if (verifiedPackages.isEmpty()) {
                builder.addRoute("10.0.0.1", 32)
                Log.i(TAG, "Mode: DNS-only (all blocked packages were stale)")
            } else {
                builder.addRoute("0.0.0.0", 0)
                for (pkg in verifiedPackages) {
                    try { builder.addAllowedApplication(pkg) } catch (e: Exception) {
                        Log.w(TAG, "addAllowedApplication failed for $pkg: ${e.message}")
                    }
                }
                Log.i(TAG, "Mode: full-tunnel, blocking ${verifiedPackages.size} apps")
            }
        }

        // Call establish() without closing the old tunnel first.
        // On success Android atomically replaces the old tun fd with the new one.
        // On failure the old tunnel (if any) remains active — VPN stays online.
        try {
            val iface = builder.establish()
            if (iface != null) {
                // Snapshot old references before overwriting state.
                val prevInterceptor = dnsInterceptor
                val prevIface       = vpnInterface

                vpnInterface = iface
                active       = true
                _isRunning.value = true
                isRestarting.set(false)

                // Stop old interceptor (its read loop exits on IOException from the
                // atomically-closed old fd; explicit stop also closes DnsForwarder).
                prevInterceptor?.stop()
                dnsInterceptor = null
                try { prevIface?.close() } catch (_: Exception) { } // already replaced atomically

                notifJob?.cancel()
                val logDao = LogDatabase.getInstance(applicationContext).packetLogDao()
                notifJob = serviceScope.launch {
                    // Recounting and redrawing on every new log row ran on each lookup. Now at
                    // most once every 30 s (the latest change is kept), and only when it changed.
                    var lastCount = -1
                    logDao.getRecentFlow(1).conflate().collect {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                        }
                        val count = logDao.countByStatusSince("BLOCKED", cal.timeInMillis)
                        if (count != lastCount) {
                            NotificationHelper.updateNotification(applicationContext, count)
                            lastCount = count
                        }
                        delay(30_000)
                    }
                }

                dnsInterceptor = DnsInterceptor(
                    vpnInterface  = iface,
                    scope         = serviceScope,
                    context       = this@SentinelVpnService,
                    ruleEngine    = ruleEngine,
                    isWifi        = { currentNetworkIsWifi },
                    protectSocket = { socket -> protect(socket) }
                )
                dnsInterceptor?.start()
                Log.i(TAG, "Tunnel established.")
            } else {
                isRestarting.set(false)
                if (vpnInterface == null) {
                    // No existing tunnel to fall back to — fully release.
                    Log.e(TAG, "establish() failed with no existing tunnel. Releasing resources.")
                    stopAfterFailedStart()
                } else {
                    // Graceful degradation: old tunnel remains active, VPN stays online.
                    Log.w(TAG, "establish() returned null — keeping existing tunnel active.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during tunnel creation", e)
            isRestarting.set(false)
            if (vpnInterface == null) stopAfterFailedStart()
            // else: old tunnel still active, VPN stays online
        }
    }

    // -------------------------------------------------------------------------
    // VPN Restart (real-time rule update)
    // -------------------------------------------------------------------------

    /**
     * Reconfigures the tunnel with updated app-policy rules.
     *
     * Does NOT close the existing tunnel before calling buildTunnel() — the
     * atomic replacement inside buildTunnel() keeps the VPN continuously active.
     *
     * Bug fixed: restartJob?.cancel() could leave isRestarting stuck at true if the
     * cancelled coroutine had already set it but hadn't yet cleared it. Explicitly
     * resetting the flag here before launching the new coroutine guarantees it is
     * always false when compareAndSet runs.
     */
    private fun scheduleRestart() {
        restartJob?.cancel()
        isRestarting.set(false)  // Reset before new launch — prevents stuck-flag deadlock
        restartJob = serviceScope.launch {
            if (!isRestarting.compareAndSet(false, true)) return@launch
            delay(200)  // Brief debounce for rapid consecutive block/unblock actions
            Log.i(TAG, "Tunnel reconfiguration: re-establishing with updated rules.")
            try { buildTunnel() } catch (e: Exception) {
                Log.e(TAG, "buildTunnel during restart failed", e)
                isRestarting.set(false)
                // Old tunnel (if active) remains serving — no releaseResources() here
            }
        }
    }

    // -------------------------------------------------------------------------
    // VPN Stop
    // -------------------------------------------------------------------------

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN tunnel.")
        // Only a user action reaches here (app toggle or the notification's Stop button).
        ProtectionIntent.setWanted(this, false)
        restartJob?.cancel()
        isRestarting.set(false)
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Single cleanup point — called from stopVpn() and onDestroy().
     * Closing the ParcelFileDescriptor releases the tun device so
     * WiFi / mobile data reconnect instantly.
     */
    private fun releaseResources() {
        active = false
        _isRunning.value = false
        notifJob?.cancel()
        notifJob = null
        unregisterNetworkTypeCallback()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing VPN interface: ${e.message}")
        } finally {
            vpnInterface = null
            dnsInterceptor?.stop()
            dnsInterceptor = null
        }
    }
}
