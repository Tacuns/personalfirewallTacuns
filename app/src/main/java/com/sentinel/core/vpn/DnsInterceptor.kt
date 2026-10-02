package com.sentinel.core.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.sentinel.core.logs.LogManager
import com.sentinel.core.rules.LookalikeDomainDetector
import com.sentinel.core.rules.RuleEngine
import com.sentinel.core.rules.TrustedDomainsCache
import com.sentinel.core.utils.AppResolver
import com.sentinel.ui.logs.PacketLog
import com.tacu.nsfwzerotrust.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.nio.ByteBuffer

class DnsInterceptor(
    private val vpnInterface: ParcelFileDescriptor,
    private val scope: CoroutineScope,
    private val context: Context,
    private val ruleEngine: RuleEngine,
    private val isWifi: () -> Boolean,
    private val protectSocket: (DatagramSocket) -> Unit
) {
    companion object {
        private const val TAG = "DnsInterceptor"
        private const val MTU = 1500
        private const val PROTOCOL_TCP = 6
        private const val PROTOCOL_UDP = 17
        private const val DNS_PORT = 53
        private const val SESSION_PRUNE_INTERVAL_MS = 60_000L
        const val LABEL_LOOKALIKE   = "LOOK-ALIKE"
        const val LABEL_WOULD_BLOCK = "WOULD BLOCK"
    }

    private var dnsForwarder: DnsForwarder? = null
    private val sessionManager = SessionManager(context)
    private var sessionPruneJob: Job? = null

    // uid → package name. Asking Android on every lookup was measurable CPU. Cleared whenever
    // an app is installed, updated or removed, so a reused uid never shows the wrong app.
    private val packageNameCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    private val packageChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: android.content.Intent?) {
            packageNameCache.clear()
            AppResolver.clearCache()
            Log.i(TAG, "App list changed (${intent?.action}), app name caches cleared")
        }
    }
    private var receiverRegistered = false

    private fun packageNameFor(uid: Int): String {
        packageNameCache[uid]?.let { return it }
        val name = try { context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "" }
                   catch (e: Exception) { "" }
        packageNameCache[uid] = name
        return name
    }

    fun start() {
        dnsForwarder = DnsForwarder(scope) { socket -> protectSocket(socket) }

        try {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, packageChangeReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch app installs: ${e.message}")
        }

        // Periodically evicts stale entries from SessionManager's UID-lookup cache so it
        // doesn't grow unbounded over a multi-day VPN session. Fully independent coroutine —
        // never blocks or delays the packet-read loop below. Cancelled in stop().
        sessionPruneJob?.cancel()
        sessionPruneJob = scope.launch {
            while (isActive) {
                delay(SESSION_PRUNE_INTERVAL_MS)
                sessionManager.pruneStaleSessions()
            }
        }

        scope.launch(Dispatchers.IO) {
            val inputStream = FileInputStream(vpnInterface.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
            val packetBuffer = ByteArray(MTU)
            val byteBuffer = ByteBuffer.wrap(packetBuffer)

            Log.i(TAG, "DNS forwarder running.")

            while (isActive) {
                val length: Int
                try {
                    length = inputStream.read(packetBuffer)
                } catch (e: IOException) {
                    Log.w(TAG, "Read error — forwarder stopped: ${e.message}")
                    break
                }

                if (length <= 0) continue

                byteBuffer.clear()
                byteBuffer.limit(length)

                // Filter: IPv4 only
                if (!PacketEngine.isIpv4(byteBuffer)) continue

                val protocol = PacketEngine.getProtocol(byteBuffer)
                val ipHeaderLen = PacketEngine.getIPHeaderLength(byteBuffer)

                // TCP packets only arrive here in blocking mode (full tunnel).
                // Resolve the app UID before RST-ing: if addDisallowedApplication() failed
                // for a non-blocked app its traffic still enters the tunnel — RST-ing it
                // would silently break a healthy app. Only RST when the app is actually
                // blocked or the UID is unknown (safe default = current behavior).
                if (protocol == PROTOCOL_TCP) {
                    if (length >= ipHeaderLen + 20) {
                        val tcpSrcIp   = PacketEngine.getSourceIP(byteBuffer)
                        val tcpSrcPort = PacketEngine.getSourcePort(byteBuffer, ipHeaderLen)
                        val tcpDstIp   = PacketEngine.getDestIP(byteBuffer)
                        val tcpDstPort = PacketEngine.getDestPort(byteBuffer, ipHeaderLen)
                        val tcpUid     = sessionManager.resolveUid(
                            PROTOCOL_TCP, tcpSrcIp, tcpSrcPort, tcpDstIp, tcpDstPort
                        )
                        // tcpUid < 0 → INVALID_UID (unknown) → RST to preserve current behaviour.
                        // tcpUid >= 0 → real app or System (uid=0) → only RST if actually blocked.
                        val shouldRst = tcpUid < 0 || ruleEngine.isAppBlocked(tcpUid, isWifi())
                        if (shouldRst) {
                            val snapshot = packetBuffer.copyOf(length)
                            scope.launch {
                                val rst = PacketEngine.createTcpRstPacket(ByteBuffer.wrap(snapshot), ipHeaderLen)
                                writeToTun(outputStream, rst)
                            }
                        }
                    }
                    continue
                }

                // Filter: UDP only (DNS is UDP)
                if (protocol != PROTOCOL_UDP) continue
                // Filter: DNS (port 53) only
                if (PacketEngine.getDestPort(byteBuffer, ipHeaderLen) != DNS_PORT) continue
                // Validate DNS payload is present
                if (length <= ipHeaderLen + 8) continue

                // Log domain with app attribution before forwarding
                val domain = extractDnsName(packetBuffer, ipHeaderLen + 8, length)
                val srcPort = PacketEngine.getSourcePort(byteBuffer, ipHeaderLen)
                val srcIP   = PacketEngine.getSourceIP(byteBuffer)
                val uid     = sessionManager.resolveUid(PROTOCOL_UDP, srcIP, srcPort, "10.0.0.1", DNS_PORT)
                val appLabel = when {
                    uid > 0  -> AppResolver.getAppName(context, uid)
                    uid == 0 -> "System"
                    else     -> "Unknown"
                }
                val pkgName = if (uid > 0) packageNameFor(uid) else ""
                // Block if app is blocked on the current transport type OR specific domain is blocked
                val options = ruleEngine.options
                val isAppBlocked = ruleEngine.isAppBlocked(uid, isWifi())
                val ruleBlocked = ruleEngine.isDomainBlocked(domain ?: "")
                // Look-Alike Domain Shield check. Skipped for sites already blocked and for
                // sites on the Always allow list. It only blocks when the user turned that on.
                val lookalikeMatch = if (!isAppBlocked && !ruleBlocked && domain != null &&
                    !ruleEngine.isDomainAllowed(domain)) {
                    try {
                        val trustedSet = TrustedDomainsCache.get()
                        // Only real matches are logged: building a debug line for every lookup
                        // cost CPU on the busiest path in the app.
                        LookalikeDomainDetector.findLookalike(domain, trustedSet).also { match ->
                            // Domains are the user's browsing data — only ever logged in debug builds.
                            if (match != null && BuildConfig.DEBUG) Log.d(TAG, "Lookalike match: domain=[$domain] match=$match")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Lookalike check failed: ${e.message}")
                        null
                    }
                } else null
                val lookalikeBlocked = lookalikeMatch != null && options.blockLookalikes
                val domainWouldBlock = ruleBlocked || lookalikeBlocked
                // Watch-only mode reports website blocks but never enforces them. App blocks stay.
                val isBlocked = isAppBlocked || (domainWouldBlock && !options.watchOnly)
                val threatLabel = when {
                    !domainWouldBlock                  -> ""
                    options.watchOnly && !isAppBlocked -> LABEL_WOULD_BLOCK
                    lookalikeBlocked                   -> LABEL_LOOKALIKE
                    else                               -> ruleEngine.getDomainThreatLabel(domain ?: "")
                }
                LogManager.logPacket(
                    PacketLog(
                        appName     = appLabel,
                        packageName = pkgName,
                        destination = domain ?: "unknown",
                        status      = if (isBlocked) "BLOCKED" else "ALLOWED",
                        threatLabel = threatLabel
                    )
                )

                // Look-Alike Domain Shield: purely observational, runs after the block/allow
                // decision above and never influences it. Fully isolated in try/catch so a
                // bug here can never affect DNS processing — same reasoning as the log call
                // above it, just one line lower. No DB I/O: TrustedDomainsCache.get() is an
                // in-memory read, refreshed only at VPN start (see SentinelVpnService.onCreate()).
                // The check itself ran above; this part only sends the warning notification.
                if (lookalikeMatch != null) {
                    try {
                        val match: LookalikeDomainDetector.LookalikeMatch? = lookalikeMatch
                        if (match != null) {
                            val notified = SecurityAlertNotifier.notifyLookalike(context, match, blocked = isBlocked)
                            // Also kept in the Alerts list (once a day per site), unlike the
                            // notification, which is capped and disappears.
                            com.sentinel.core.alerts.SecurityAlerts.record(
                                context,
                                if (isBlocked) com.sentinel.core.alerts.AlertType.LOOKALIKE_BLOCKED else com.sentinel.core.alerts.AlertType.LOOKALIKE_WARNED,
                                if (isBlocked) com.sentinel.core.alerts.AlertSeverity.HIGH else com.sentinel.core.alerts.AlertSeverity.MEDIUM,
                                target = match.queriedDomain, extra = match.matchedTrustedDomain
                            )
                            if (BuildConfig.DEBUG) Log.d(TAG, "Lookalike notify: domain=$domain notified=$notified")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Lookalike check failed: ${e.message}")
                    }
                }

                // Snapshot the packet before the next loop iteration overwrites packetBuffer.
                // Each forward suspends (no thread held) — read loop is never blocked.
                val snapshot = packetBuffer.copyOf(length)
                val capturedIpHeaderLen = ipHeaderLen
                val capturedIsBlocked = isBlocked
                val capturedSafeTarget =
                    if (!isBlocked && options.safeSearch && domain != null) SafeSearch.targetFor(domain) else null
                scope.launch forward@{
                    val dnsPayload = snapshot.copyOfRange(capturedIpHeaderLen + 8, length)
                    val snapshotBuffer = ByteBuffer.wrap(snapshot)
                    if (capturedIsBlocked) {
                        val nxPayload = PacketEngine.forgeNxDomain(snapshotBuffer, capturedIpHeaderLen, 8)
                            ?: return@forward
                        writeToTun(outputStream, PacketEngine.createResponsePacket(
                            ByteBuffer.wrap(snapshot), capturedIpHeaderLen, nxPayload))
                    } else if (capturedSafeTarget != null) {
                        // Safe search: answer the search site as an alias of its safe address.
                        val reply = SafeSearch.answer(dnsPayload, capturedSafeTarget) { q -> dnsForwarder?.forward(q) }
                            ?: return@forward
                        writeToTun(outputStream, PacketEngine.createResponsePacket(snapshotBuffer, capturedIpHeaderLen, reply))
                    } else {
                        // Short TTL so the phone asks again soon and a new block applies quickly.
                        val response = dnsForwarder?.forward(dnsPayload)?.let { DnsTtl.cap(it) } ?: return@forward
                        val responsePacket = PacketEngine.createResponsePacket(snapshotBuffer, capturedIpHeaderLen, response)
                        writeToTun(outputStream, responsePacket)
                    }
                }
            }

            Log.i(TAG, "DNS forwarder exited.")
        }
    }

    /**
     * Sends one test question through the forwarder (see DnsProbe) so the DNS status reflects
     * the current server choice straight away. Never touches the tunnel; the answer is only
     * used for the status the forwarder records.
     */
    fun probeDns() {
        val forwarder = dnsForwarder ?: return
        scope.launch(Dispatchers.IO) {
            try { forwarder.forward(DnsProbe.query()) } catch (e: Exception) {
                Log.w(TAG, "DNS probe failed: ${e.javaClass.simpleName}")
            }
        }
    }

    fun stop() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(packageChangeReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        sessionPruneJob?.cancel()
        sessionPruneJob = null
        dnsForwarder?.close()
        dnsForwarder = null
    }

    // Synchronized: multiple concurrent launch blocks share this output stream.
    @Synchronized
    private fun writeToTun(outputStream: FileOutputStream, packet: ByteArray) {
        try {
            outputStream.write(packet)
        } catch (e: Exception) {
            Log.w(TAG, "TUN write failed: ${e.message}")
        }
    }

    // Lowercased, so a mixed-case query cannot slip past the lowercase rule sets (see DnsName).
    private fun extractDnsName(buffer: ByteArray, dnsOffset: Int, length: Int): String? =
        DnsName.fromQuery(buffer, dnsOffset, length)
}
