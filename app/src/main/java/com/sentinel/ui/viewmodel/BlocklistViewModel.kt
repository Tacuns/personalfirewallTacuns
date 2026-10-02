package com.sentinel.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.sentinel.core.logs.ChangeAction
import com.sentinel.core.logs.ChangeHistory
import com.sentinel.core.logs.ChangeValue
import com.sentinel.core.rules.BlocklistSource
import com.sentinel.core.rules.RuleEngine
import com.sentinel.core.vpn.BlocklistSchedule
import com.sentinel.core.vpn.BlocklistSyncStatus
import com.sentinel.core.vpn.BlocklistSyncWorker
import com.sentinel.core.vpn.CustomBlocklist
import com.sentinel.core.vpn.SentinelVpnService
import com.sentinel.core.vpn.vpnRunning
import com.sentinel.ui.lock.AppLockSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of adding a list by link. */
enum class CustomListResult { ADDED, INVALID_LINK, DUPLICATE, LIMIT, NOT_ALLOWED, FAILED }

class BlocklistViewModel(app: Application) : AndroidViewModel(app) {

    private val ruleEngine = RuleEngine.getInstance(app)
    private val sourceDao  = ruleEngine.db.blocklistSourceDao()
    private val ruleDao    = ruleEngine.db.ruleDao()

    val sources: StateFlow<List<BlocklistSource>> = sourceDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The saved reason each list's last download failed, re-read on every write to the lists
    // table. After a failure the worker only touches the row, so the list equals the last one
    // and `sources` (a StateFlow, which skips equal values) does not emit; without this the
    // open screen showed the error only after it was reopened (seen on the emulator).
    private var _errors by mutableStateOf(emptyMap<String, Pair<String, Long>>())

    init {
        viewModelScope.launch {
            sourceDao.getAllFlow().collect { list ->
                _errors = list.mapNotNull { s ->
                    BlocklistSyncStatus.get(getApplication(), s.sourceKey)?.let { s.sourceKey to it }
                }.toMap()
            }
        }
    }

    // Keys currently syncing — drives per-row loading spinners
    private var _syncingKeys by mutableStateOf(emptySet<String>())
    val syncingKeys: Set<String> get() = _syncingKeys

    // How often each list updates, as chosen on this screen (see BlocklistSchedule).
    private var _intervals by mutableStateOf(emptyMap<String, Int>())

    fun intervalFor(sourceKey: String): Int =
        _intervals[sourceKey] ?: BlocklistSchedule.intervalHours(getApplication(), sourceKey)

    fun setInterval(sourceKey: String, hours: Int) {
        if (AppLockSession.blockChange(getApplication())) return
        BlocklistSchedule.setIntervalHours(getApplication(), sourceKey, hours)
        _intervals = _intervals + (sourceKey to BlocklistSchedule.sanitize(hours))
    }

    /** True once the list has new data, or its download failed after [startMs]. */
    private fun syncFinished(list: List<BlocklistSource>, sourceKey: String, prevMs: Long, startMs: Long): Boolean =
        (list.find { it.sourceKey == sourceKey }?.lastUpdatedMs ?: 0L) > prevMs ||
        (BlocklistSyncStatus.get(getApplication(), sourceKey)?.second ?: 0L) >= startMs

    fun refreshSource(sourceKey: String) {
        val prevMs = sources.value.find { it.sourceKey == sourceKey }?.lastUpdatedMs ?: 0L
        val startMs = System.currentTimeMillis()
        _syncingKeys = _syncingKeys + sourceKey
        BlocklistSyncWorker.runNow(getApplication(), sourceKey)
        viewModelScope.launch {
            // Read the table itself, not `sources`: after a failed download the row is only
            // touched, the list stays equal, and the StateFlow would not emit, so the spinner
            // ran on to the timeout although the error was already saved.
            withTimeoutOrNull(60_000L) {
                sourceDao.getAllFlow().first { list -> syncFinished(list, sourceKey, prevMs, startMs) }
            }
            _syncingKeys = _syncingKeys - sourceKey
        }
    }

    /**
     * Turns a blocklist source on or off.
     *
     * Disabling MUST delete the source's rules, not just clear the `enabled` flag:
     * RuleDao.getBlocklistDomains() selects on `source LIKE 'BLOCKLIST%'` and never
     * consults blocklist_sources.enabled, so leaving the rows in place would keep
     * every domain blocked while the UI claimed the list was off.
     *
     * Enabling downloads the list immediately instead of waiting for the periodic worker.
     */
    fun setSourceEnabled(sourceKey: String, enabled: Boolean) {
        if (AppLockSession.blockChange(getApplication())) return
        _syncingKeys = _syncingKeys + sourceKey
        viewModelScope.launch {
            try {
                val listName = sources.value.find { it.sourceKey == sourceKey }?.displayName ?: sourceKey
                sourceDao.setEnabled(sourceKey, enabled)
                ChangeHistory.record(
                    getApplication(), ChangeAction.BLOCKLIST, listName,
                    before = if (enabled) ChangeValue.OFF else ChangeValue.ON,
                    after  = if (enabled) ChangeValue.ON else ChangeValue.OFF
                )
                if (enabled) {
                    val prevMs = sources.value.find { it.sourceKey == sourceKey }?.lastUpdatedMs ?: 0L
                    val startMs = System.currentTimeMillis()
                    BlocklistSyncWorker.runNow(getApplication(), sourceKey)
                    withTimeoutOrNull(60_000L) {
                        sources.first { list -> syncFinished(list, sourceKey, prevMs, startMs) }
                    }
                } else {
                    ruleDao.deleteBlocklistBySource(sourceKey)
                    sourceDao.updateStats(sourceKey, 0, 0L)
                    ruleEngine.reloadBlocklist()
                    notifyVpnRulesChanged()
                }
            } catch (e: Exception) {
                Log.w("BlocklistVM", "setSourceEnabled($sourceKey=$enabled) failed: ${e.message}")
            } finally {
                _syncingKeys = _syncingKeys - sourceKey
            }
        }
    }

    /** Why the latest download of this list failed, or null when it is fine. */
    fun errorFor(source: BlocklistSource): String? =
        _errors[source.sourceKey]
            ?.takeIf { it.second >= source.lastUpdatedMs }
            ?.first

    /**
     * Adds a list from a link the user typed, switches it on and starts the download.
     * [onResult] is called on the main thread as soon as the list is saved (or rejected);
     * the download continues in the background with the row's spinner showing.
     */
    fun addCustomList(name: String, link: String, onResult: (CustomListResult) -> Unit) {
        if (AppLockSession.blockChange(getApplication())) { onResult(CustomListResult.NOT_ALLOWED); return }
        val clean = CustomBlocklist.normalizeLink(link)
        if (clean == null) { onResult(CustomListResult.INVALID_LINK); return }
        viewModelScope.launch {
            val source = try {
                val custom = sourceDao.getAllFlow().first().filter { CustomBlocklist.isCustom(it.sourceKey) }
                when {
                    custom.any { it.url.equals(clean, ignoreCase = true) } -> {
                        onResult(CustomListResult.DUPLICATE); return@launch
                    }
                    custom.size >= CustomBlocklist.MAX_LISTS -> {
                        onResult(CustomListResult.LIMIT); return@launch
                    }
                }
                CustomBlocklist.newSource(CustomBlocklist.newKey(), name, clean)
                    .copy(enabled = true)
                    .also { sourceDao.upsert(it) }
            } catch (e: Exception) {
                Log.w("BlocklistVM", "addCustomList failed: ${e.message}")
                onResult(CustomListResult.FAILED); return@launch
            }
            onResult(CustomListResult.ADDED)

            val key = source.sourceKey
            val startMs = System.currentTimeMillis()
            _syncingKeys = _syncingKeys + key
            try {
                BlocklistSyncWorker.runNow(getApplication(), key)
                withTimeoutOrNull(120_000L) {
                    sourceDao.getAllFlow().first { list -> syncFinished(list, key, 0L, startMs) }
                }
            } finally {
                _syncingKeys = _syncingKeys - key
            }
        }
    }

    /**
     * Removes a list the user added, together with its rules. The row is deleted first and
     * both deletes share one transaction; the worker re-checks the row inside its own
     * transaction, so a download finishing at the same moment cannot leave rules behind.
     */
    fun removeCustomList(sourceKey: String) {
        if (!CustomBlocklist.isCustom(sourceKey)) return
        if (AppLockSession.blockChange(getApplication())) return
        viewModelScope.launch {
            try {
                ruleEngine.db.withTransaction {
                    sourceDao.deleteByKey(sourceKey)
                    ruleDao.deleteBlocklistBySource(sourceKey)
                }
                BlocklistSyncStatus.clear(getApplication(), sourceKey)
                BlocklistSchedule.forget(getApplication(), sourceKey)
                _intervals = _intervals - sourceKey
                ruleEngine.reloadBlocklist()
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                Log.w("BlocklistVM", "removeCustomList($sourceKey) failed: ${e.message}")
            }
        }
    }

    // Same guarded pattern as BlocklistSyncWorker: the ACTION_RELOAD_RULES branch in
    // SentinelVpnService never calls startForeground(), so starting it while the VPN is
    // stopped would crash with ForegroundServiceDidNotStartInTimeException. When the VPN
    // is off the reload is unnecessary — fresh rules are read from the DB on next start.
    private fun notifyVpnRulesChanged() {
        val ctx = getApplication<Application>()
        if (!ctx.vpnRunning()) return
        try {
            val intent = Intent(ctx, SentinelVpnService::class.java).apply {
                action = SentinelVpnService.ACTION_RELOAD_RULES
            }
            ContextCompat.startForegroundService(ctx, intent)
        } catch (e: Exception) {
            Log.w("BlocklistVM", "Could not notify VPN of blocklist change: ${e.message}")
        }
    }

    fun refreshAll() {
        val prevTimes = sources.value.filter { it.enabled }
            .associate { it.sourceKey to it.lastUpdatedMs }
        val startMs = System.currentTimeMillis()
        _syncingKeys = _syncingKeys + prevTimes.keys
        BlocklistSyncWorker.runNow(getApplication())
        viewModelScope.launch {
            withTimeoutOrNull(120_000L) {
                sourceDao.getAllFlow().first { list ->
                    list.filter { it.enabled }.all { s ->
                        syncFinished(list, s.sourceKey, prevTimes[s.sourceKey] ?: 0L, startMs)
                    }
                }
            }
            _syncingKeys = _syncingKeys - prevTimes.keys
        }
    }
}
