package com.sentinel.core.alerts

import android.content.Context
import android.util.Log
import com.sentinel.core.logs.LogRetention
import com.sentinel.core.rules.FirewallOptionsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Stable keys stored in the alerts table. Never shown as-is; the screen turns them into words. */
object AlertType {
    const val LOOKALIKE_BLOCKED = "lookalike_blocked"   // target = site, extra = trusted site
    const val LOOKALIKE_WARNED  = "lookalike_warned"    // same, while blocking is off or watch-only
    const val BLOCKLIST_FAILED  = "blocklist_failed"    // target = list key, extra = reason code
    const val PROTECTION_STOPPED = "protection_stopped" // Android or another VPN app took the VPN
    const val PROTECTION_NOT_STARTED = "protection_not_started" // after a restart, permission missing
    const val RULES_LOST        = "rules_lost"          // the rules database was found damaged
    const val PRIVATE_DNS_CONFLICT = "private_dns"      // target = Private DNS provider; protection paused
}

/** Three of the five firewall severity levels (informational … critical) are used. */
object AlertSeverity {
    const val HIGH   = "high"
    const val MEDIUM = "medium"
    const val LOW    = "low"
}

/**
 * The rules for keeping alerts, kept free of Android so they can be tested.
 *
 * Alerts may name websites, so they follow the Activity history setting: kept as long as
 * the history, never more than [MAX_DAYS] days, and not at all when history is off.
 * The same event about the same site or list is recorded once per [dedupeWindowMs], so a
 * page that keeps retrying a blocked look-alike site does not flood the list.
 */
object AlertRules {
    const val MAX_DAYS = 30
    const val MAX_ROWS = 500
    private const val HOUR_MS = 3_600_000L

    fun keepHours(historyHours: Int): Int =
        if (historyHours == LogRetention.OFF) 0 else minOf(historyHours, MAX_DAYS * 24)

    fun cutoffMs(historyHours: Int, nowMs: Long): Long = nowMs - keepHours(historyHours) * HOUR_MS

    fun dedupeWindowMs(type: String): Long = when (type) {
        AlertType.LOOKALIKE_BLOCKED, AlertType.LOOKALIKE_WARNED, AlertType.BLOCKLIST_FAILED -> 24 * HOUR_MS
        AlertType.PROTECTION_STOPPED, AlertType.PROTECTION_NOT_STARTED,
        AlertType.PRIVATE_DNS_CONFLICT -> 10 * 60_000L
        else -> 0L
    }

    fun isDuplicate(lastMs: Long?, type: String, nowMs: Long): Boolean =
        lastMs != null && nowMs - lastMs < dedupeWindowMs(type)

    /**
     * Whether a failed blocklist download is worth an alert. A network failure on the first
     * try is often momentary — on a new install the first download can start while the
     * firewall itself is still connecting — and the download is retried automatically, so
     * it is only reported if the retry fails too. Other failures (a broken link, an empty
     * or too-large list) are not retried and are reported at once. The Blocklists screen
     * still shows every failure as it happens.
     */
    fun alertForBlocklistFailure(code: String, attempt: Int): Boolean =
        code != com.sentinel.core.vpn.BlocklistSyncStatus.NETWORK || attempt >= 1
}

/**
 * Writes alerts. Fire-and-forget on a background thread: a failure here must never affect
 * blocking, the download, or the notification. Nothing that names a site is logged.
 */
object SecurityAlerts {

    private const val TAG = "SecurityAlerts"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun record(context: Context, type: String, severity: String, target: String = "", extra: String = "") {
        val app = context.applicationContext
        scope.launch {
            try {
                val historyHours = FirewallOptionsStore.read(app).logRetentionHours
                if (AlertRules.keepHours(historyHours) == 0) return@launch
                val dao = AlertDatabase.getInstance(app).alertDao()
                val now = System.currentTimeMillis()
                if (AlertRules.isDuplicate(dao.lastTime(type, target), type, now)) return@launch
                dao.insert(AlertEntity(timestampMs = now, type = type, severity = severity,
                    target = target, extra = extra))
                dao.deleteOlderThan(AlertRules.cutoffMs(historyHours, now))
                dao.deleteExcess(AlertRules.MAX_ROWS)
            } catch (e: Exception) {
                Log.w(TAG, "Could not record an alert: ${e.javaClass.simpleName}")
            }
        }
    }

    /** Applies the history setting to alerts already stored (called when it changes). */
    fun applyRetention(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                val hours = FirewallOptionsStore.read(app).logRetentionHours
                val dao = AlertDatabase.getInstance(app).alertDao()
                if (AlertRules.keepHours(hours) == 0) dao.deleteAll()
                else dao.deleteOlderThan(AlertRules.cutoffMs(hours, System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.w(TAG, "Could not trim alerts: ${e.javaClass.simpleName}")
            }
        }
    }
}
