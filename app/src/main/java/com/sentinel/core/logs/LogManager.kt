package com.sentinel.core.logs

import android.content.Context
import com.sentinel.core.rules.FirewallOptionsStore
import com.sentinel.ui.logs.PacketLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedList

object LogManager {

    private const val MAX_MEMORY = 200   // in-memory cap

    // How many inserts may go by before the size cap is re-checked. Trimming on every
    // single insert (the old behaviour) ran a sub-select per DNS answer; the periodic
    // LogRetentionWorker does the real work and this is only a backstop for a burst.
    private const val TRIM_EVERY_N_INSERTS = 500

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val buffer = LinkedList<PacketLog>()

    private val _recentLogs = MutableStateFlow<List<PacketLog>>(emptyList())
    val recentLogs: StateFlow<List<PacketLog>> = _recentLogs.asStateFlow()

    private val throttleCache = mutableMapOf<String, Long>()

    // Debounce: one pending UI emit at a time — cancelled and replaced on each new log entry.
    // Prevents a DNS burst (10–20 queries in 100ms) from triggering 10–20 Compose recompositions.
    private var emitJob: Job? = null

    private var dao: PacketLogDao? = null

    // Mirrors FirewallOptions.logRetentionHours. Read on the DNS thread for every packet,
    // so it is kept in memory and pushed in from RuleEngine.reloadCache() rather than read
    // from disk here. LogRetention.OFF means nothing is recorded anywhere.
    @Volatile private var retentionHours = LogRetention.DEFAULT_HOURS

    private var insertsSinceTrim = 0

    // Called from SentinelVpnService.onCreate() — no StatsRepository param.
    // DataStore (StatsRepository) must NOT be opened from the :vpn process because
    // DataStore is not multi-process safe; concurrent access corrupts the preferences
    // file and causes an IOException crash in the UI process after extended use.
    fun initialize(context: Context) {
        if (dao == null) {
            dao = LogDatabase.getInstance(context).packetLogDao()
            retentionHours = FirewallOptionsStore.read(context).logRetentionHours
            // Nothing is shown from a history the user asked not to keep.
            if (retentionHours != LogRetention.OFF) loadFromDb()
            // Catches rows left behind by a window that was shortened while the app was closed.
            trimNow()
        }
    }

    /**
     * Applies the user's retention choice. Called from RuleEngine.reloadCache(), which already
     * reads the options file and runs in both processes whenever the setting changes, so the
     * VPN stops recording the moment the user turns logging off - no restart needed.
     *
     * Switching to OFF also clears what is already stored: leaving yesterday's browsing behind
     * after the user asked for no history would not be honest.
     */
    fun setRetentionHours(hours: Int) {
        val next = LogRetention.sanitize(hours)
        if (next == retentionHours) return
        retentionHours = next
        if (next == LogRetention.OFF) {
            clearLogs()
        } else {
            trimNow()
        }
    }

    /** Drops whatever the current window no longer covers. Safe to call at any time. */
    fun trimNow() {
        val d = dao ?: return
        val hours = retentionHours
        scope.launch(Dispatchers.IO) {
            try { LogTrimmer.trim(d, hours) } catch (e: Exception) {
                android.util.Log.w("LogManager", "trim failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun loadFromDb() {
        scope.launch(Dispatchers.IO) {
            try {
                val saved = dao?.getRecent(MAX_MEMORY) ?: return@launch
                synchronized(this@LogManager) {
                    buffer.clear()
                    saved.forEach { buffer.addLast(it.toPacketLog()) }
                }
                _recentLogs.value = buffer.toList()
            } catch (e: Exception) {
                android.util.Log.w("LogManager", "loadFromDb failed: ${e.message}")
            }
        }
    }

    // Answers handed to the phone now expire after 20 s (DnsTtl), so one visit can repeat the
    // same lookup several times. Recording it once a minute per app keeps counts, "blocked
    // N times" and the 500-row history meaningful instead of filling them with repeats.
    private const val REPEAT_WINDOW_MS = 60_000L

    @Synchronized
    fun logPacket(log: PacketLog) {
        // The user asked for no activity history. Nothing is buffered and nothing is stored.
        if (retentionHours == LogRetention.OFF) return

        val key = "${log.packageName}|${log.destination}|${log.status}"
        val now = System.currentTimeMillis()

        if (now - (throttleCache[key] ?: 0L) < REPEAT_WINDOW_MS) return
        throttleCache[key] = now

        if (throttleCache.size > 500) throttleCache.clear()

        buffer.addFirst(log)
        while (buffer.size > MAX_MEMORY) buffer.removeLast()

        val snapshot = buffer.toList()
        emitJob?.cancel()
        emitJob = scope.launch {
            delay(300)
            _recentLogs.value = snapshot
        }

        val entity = log.toEntity()
        val trimNow = ++insertsSinceTrim >= TRIM_EVERY_N_INSERTS
        if (trimNow) insertsSinceTrim = 0
        val hours = retentionHours
        scope.launch(Dispatchers.IO) {
            try {
                dao?.insert(entity)
                if (trimNow) dao?.let { LogTrimmer.trim(it, hours) }
            } catch (e: Exception) {
                android.util.Log.w("LogManager", "DB write failed: ${e.message}")
            }
        }
    }

    fun clearLogs() {
        synchronized(this) { buffer.clear() }
        emitJob?.cancel()
        _recentLogs.value = emptyList()
        scope.launch(Dispatchers.IO) {
            try { dao?.deleteAll() } catch (e: Exception) {
                android.util.Log.w("LogManager", "clearLogs failed: ${e.message}")
            }
        }
    }
}
