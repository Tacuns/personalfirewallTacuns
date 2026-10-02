package com.sentinel.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Properties

/**
 * Which DNS server the firewall forwards allowed lookups to: a main server and an optional
 * backup, both chosen by the user. The backup is only ever used when the user picked one;
 * the firewall never falls back to a server nobody chose, so a filtering resolver (for
 * example a family DNS set on the home router) is never bypassed behind the user's back.
 *
 * "System DNS" is the DNS server of the network the phone is really on - the Wi-Fi router,
 * or the mobile carrier - read with LinkProperties.getDnsServers() from the underlying
 * network, never from our own VPN.
 */
object DnsResolverChoice {

    const val GOOGLE = "google"
    const val CLOUDFLARE = "cloudflare"
    const val SYSTEM = "system"
    const val CUSTOM = "custom"
    const val NONE = "none"

    const val DEFAULT_MAIN = GOOGLE
    const val DEFAULT_BACKUP = CLOUDFLARE

    val MAIN_CHOICES = listOf(SYSTEM, GOOGLE, CLOUDFLARE, CUSTOM)
    val BACKUP_CHOICES = listOf(NONE, SYSTEM, GOOGLE, CLOUDFLARE, CUSTOM)

    private val GOOGLE_ADDR: InetAddress = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
    private val CLOUDFLARE_ADDR: InetAddress = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))

    /** Addresses of our own tunnel. Forwarding to them would loop lookups back into the VPN. */
    private val TUNNEL_ADDRS = setOf("10.0.0.1", "10.0.0.2")

    /** One server to try, with the user's name for it (for the status line). */
    data class Server(val choice: String, val address: InetAddress)

    /** Main servers first, then backup servers. An empty backup list means "none chosen". */
    data class Plan(val main: List<Server>, val backup: List<Server>) {
        val key: String get() = (main + backup).joinToString { it.address.hostAddress ?: "" }
    }

    /** Settings as saved (see FirewallOptions). */
    data class Settings(
        val main: String = DEFAULT_MAIN,
        val mainCustom: String = "",
        val backup: String = DEFAULT_BACKUP,
        val backupCustom: String = ""
    )

    // ── Validation (pure; unit-tested) ─────────────────────────────────────

    fun sanitizeMain(value: String?): String = if (value in MAIN_CHOICES) value!! else DEFAULT_MAIN

    fun sanitizeBackup(value: String?): String = if (value in BACKUP_CHOICES) value!! else DEFAULT_BACKUP

    /**
     * A custom server must be a literal IP address that can actually answer DNS. Names are
     * refused (resolving them would need DNS), as are our own tunnel addresses, loopback,
     * "any" and multicast.
     */
    fun parseCustom(text: String, isNumeric: (String) -> Boolean, parse: (String) -> InetAddress): InetAddress? {
        val t = text.trim()
        if (t.isEmpty() || t in TUNNEL_ADDRS || !isNumeric(t)) return null
        val a = try { parse(t) } catch (e: Exception) { return null }
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isMulticastAddress) return null
        return a
    }

    fun isValidCustom(text: String): Boolean =
        parseCustom(text, InetAddresses::isNumericAddress, InetAddresses::parseNumericAddress) != null

    /**
     * Builds the plan from the settings and the network's own DNS servers. Duplicate servers
     * are dropped from the backup (a backup equal to the main server is not a backup).
     */
    fun buildPlan(
        settings: Settings,
        systemServers: List<InetAddress>,
        custom: (String) -> InetAddress?
    ): Plan {
        fun resolve(choice: String, customText: String): List<Server> = when (choice) {
            GOOGLE -> listOf(Server(GOOGLE, GOOGLE_ADDR))
            CLOUDFLARE -> listOf(Server(CLOUDFLARE, CLOUDFLARE_ADDR))
            SYSTEM -> systemServers
                .filter { (it.hostAddress ?: "") !in TUNNEL_ADDRS && !it.isLoopbackAddress }
                .map { Server(SYSTEM, it) }
            CUSTOM -> custom(customText)?.let { listOf(Server(CUSTOM, it)) } ?: emptyList()
            else -> emptyList()
        }
        val main = resolve(settings.main, settings.mainCustom)
        val mainAddrs = main.map { it.address }.toSet()
        val backup = if (settings.backup == NONE) emptyList()
            else resolve(settings.backup, settings.backupCustom).filter { it.address !in mainAddrs }
        return Plan(main, backup)
    }

    // ── Live state in the :vpn process ─────────────────────────────────────

    @Volatile private var appContext: Context? = null
    @Volatile private var settings = Settings()
    @Volatile private var cachedPlan: Plan? = null
    @Volatile private var cachedAtMs = 0L
    private const val PLAN_REFRESH_MS = 5_000L

    /**
     * Called from RuleEngine.reloadCache(), which runs whenever the settings change, so a new
     * choice applies without restarting the VPN.
     */
    fun apply(context: Context, newSettings: Settings) {
        appContext = context.applicationContext
        if (newSettings != settings) {
            settings = newSettings
            cachedPlan = null
        }
    }

    /**
     * The servers to use right now. Re-read at most every few seconds so System DNS follows
     * the phone from Wi-Fi to mobile data without a binder call on every lookup.
     */
    fun currentPlan(): Plan {
        val now = System.currentTimeMillis()
        cachedPlan?.let { if (now - cachedAtMs < PLAN_REFRESH_MS) return it }
        val s = settings
        // Only ask the system for its DNS servers when the user actually chose them.
        val system = if (s.main == SYSTEM || s.backup == SYSTEM) systemDnsServers() else emptyList()
        val plan = buildPlan(s, system) {
            parseCustom(it, InetAddresses::isNumericAddress, InetAddresses::parseNumericAddress)
        }
        cachedPlan = plan
        cachedAtMs = now
        return plan
    }

    /** The DNS servers of the real network in use (never our VPN), IPv4 first. */
    @Suppress("DEPRECATION")
    fun systemDnsServers(): List<InetAddress> {
        val lp = underlyingLinkProperties() ?: return emptyList()
        return lp.dnsServers.sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    /** Lets the app screen read the network's DNS servers (the :vpn process gets this via apply). */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** True when the phone is on Wi-Fi (or Ethernet) rather than mobile data; null if offline. */
    fun underlyingIsWifi(): Boolean? {
        val ctx = appContext ?: return null
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = underlyingLinkProperties() ?: return null
        @Suppress("DEPRECATION")
        val net = cm.allNetworks.firstOrNull { cm.getLinkProperties(it)?.interfaceName == lp.interfaceName }
            ?: return null
        val caps = cm.getNetworkCapabilities(net) ?: return null
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** True when Android's Private DNS is on for the network in use. */
    fun isPrivateDnsActive(context: Context): Boolean {
        appContext = context.applicationContext
        return underlyingLinkProperties()?.isPrivateDnsActive == true
    }

    @Suppress("DEPRECATION")
    private fun underlyingLinkProperties(): LinkProperties? {
        val ctx = appContext ?: return null
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        fun usable(n: android.net.Network): Boolean {
            val c = cm.getNetworkCapabilities(n) ?: return false
            return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        // Same preference as the system: the network in use, else an unmetered one, else any.
        val net = cm.activeNetwork?.takeIf(::usable)
            ?: cm.allNetworks.filter(::usable).let { list ->
                list.firstOrNull {
                    cm.getNetworkCapabilities(it)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
                } ?: list.firstOrNull()
            }
            ?: return null
        return cm.getLinkProperties(net)
    }

    // ── Status shared with the app screen ─────────────────────────────────
    // The :vpn process writes a tiny file only when the state changes; the Settings screen
    // reads it. Same cross-process pattern as the options file.

    const val STATE_MAIN = "main"         // answering from the main server
    const val STATE_BACKUP = "backup"     // main not answering, backup in use
    const val STATE_FAILING = "failing"   // nothing is answering
    private const val STATUS_FILE = "dns_status.properties"

    data class Status(val state: String, val server: String, val sinceMs: Long)

    @Volatile private var lastStatus: Status? = null

    fun report(state: String, server: InetAddress?) {
        val ctx = appContext ?: return
        val addr = server?.hostAddress ?: ""
        val prev = lastStatus
        if (prev != null && prev.state == state && prev.server == addr) return
        val next = Status(state, addr, System.currentTimeMillis())
        lastStatus = next
        try {
            val p = Properties().apply {
                setProperty("state", next.state)
                setProperty("server", next.server)
                setProperty("since", next.sinceMs.toString())
            }
            val tmp = File(ctx.filesDir, "$STATUS_FILE.tmp")
            tmp.outputStream().use { p.store(it, null) }
            tmp.renameTo(File(ctx.filesDir, STATUS_FILE))
        } catch (e: Exception) {
            android.util.Log.w("DnsResolverChoice", "status write failed: ${e.javaClass.simpleName}")
        }
    }

    fun readStatus(context: Context): Status? {
        return try {
            val f = File(context.filesDir, STATUS_FILE)
            if (!f.exists()) return null
            val p = Properties().apply { f.inputStream().use { load(it) } }
            val state = p.getProperty("state") ?: return null
            Status(state, p.getProperty("server") ?: "", p.getProperty("since")?.toLongOrNull() ?: 0L)
        } catch (e: Exception) {
            null
        }
    }
}
