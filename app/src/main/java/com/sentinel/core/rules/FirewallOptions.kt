package com.sentinel.core.rules

import android.content.Context
import com.sentinel.core.logs.LogRetention
import com.sentinel.core.vpn.DnsResolverChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.util.Properties

/** Extra firewall switches the user controls in Settings. All off by default. */
data class FirewallOptions(
    val watchOnly: Boolean = false,        // report website blocks, never enforce them
    val blockLookalikes: Boolean = false,  // block sites that imitate trusted names
    val safeSearch: Boolean = false,       // force safe results on major search sites
    // How many hours of activity history to keep. 0 means the firewall records nothing.
    // Lives here rather than in DataStore because the :vpn process has to read it too.
    val logRetentionHours: Int = LogRetention.DEFAULT_HOURS,
    // Safe search also restricts YouTube. On by default so anyone who already had safe search
    // on keeps exactly the protection they had; a separate switch lets them turn YouTube off.
    val safeSearchYouTube: Boolean = true,
    // Which DNS servers allowed lookups go to: a main server and an optional backup, both the
    // user's choice (see DnsResolverChoice). Defaults keep today's behaviour: Google first.
    val dnsMain: String = DnsResolverChoice.DEFAULT_MAIN,
    val dnsMainCustom: String = "",
    val dnsBackup: String = DnsResolverChoice.DEFAULT_BACKUP,
    val dnsBackupCustom: String = ""
) {
    val dnsSettings: DnsResolverChoice.Settings
        get() = DnsResolverChoice.Settings(dnsMain, dnsMainCustom, dnsBackup, dnsBackupCustom)
}

/**
 * Keeps [FirewallOptions] in a tiny file that both processes can read.
 *
 * DataStore is not safe to share between the app and the :vpn process (see LogManager),
 * so the app process writes this file atomically (temp file + rename) and the VPN reads
 * it again on every rule reload. A missing or unreadable file means all switches off.
 */
object FirewallOptionsStore {

    private const val FILE = "firewall_options.properties"

    private val state = MutableStateFlow(FirewallOptions())
    @Volatile private var loaded = false

    fun read(context: Context): FirewallOptions {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return FirewallOptions()
        return try {
            val p = Properties()
            file.inputStream().use { p.load(it) }
            FirewallOptions(
                watchOnly       = p.getProperty("watchOnly") == "true",
                blockLookalikes = p.getProperty("blockLookalikes") == "true",
                safeSearch      = p.getProperty("safeSearch") == "true",
                logRetentionHours = LogRetention.sanitize(
                    p.getProperty("logRetentionHours")?.toIntOrNull() ?: LogRetention.DEFAULT_HOURS
                ),
                // Missing means an older version wrote the file: keep YouTube restricted.
                safeSearchYouTube = p.getProperty("safeSearchYouTube") != "false",
                dnsMain = DnsResolverChoice.sanitizeMain(p.getProperty("dnsMain")),
                dnsMainCustom = p.getProperty("dnsMainCustom") ?: "",
                dnsBackup = DnsResolverChoice.sanitizeBackup(p.getProperty("dnsBackup")),
                dnsBackupCustom = p.getProperty("dnsBackupCustom") ?: ""
            )
        } catch (e: Exception) {
            FirewallOptions()
        }
    }

    /** Live options for screens in the app process. */
    fun flow(context: Context): StateFlow<FirewallOptions> {
        if (!loaded) synchronized(this) {
            if (!loaded) { state.value = read(context); loaded = true }
        }
        return state.asStateFlow()
    }

    @Synchronized
    fun update(context: Context, change: (FirewallOptions) -> FirewallOptions): FirewallOptions {
        val next = change(read(context))
        val p = Properties().apply {
            setProperty("watchOnly", next.watchOnly.toString())
            setProperty("blockLookalikes", next.blockLookalikes.toString())
            setProperty("safeSearch", next.safeSearch.toString())
            setProperty("logRetentionHours", next.logRetentionHours.toString())
            setProperty("safeSearchYouTube", next.safeSearchYouTube.toString())
            setProperty("dnsMain", next.dnsMain)
            setProperty("dnsMainCustom", next.dnsMainCustom)
            setProperty("dnsBackup", next.dnsBackup)
            setProperty("dnsBackupCustom", next.dnsBackupCustom)
        }
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.outputStream().use { p.store(it, null) }
        if (!tmp.renameTo(File(context.filesDir, FILE))) throw IOException("Could not save firewall options")
        state.value = next
        loaded = true
        return next
    }
}
