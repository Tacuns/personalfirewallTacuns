package com.sentinel.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.core.logs.AppBlockNow
import com.sentinel.core.logs.CurrentRule
import com.sentinel.core.logs.DestinationSummary
import com.sentinel.core.logs.LogDatabase
import com.sentinel.core.logs.LogManager
import com.sentinel.core.logs.LogWhy
import com.sentinel.core.rules.DomainCheck
import com.sentinel.core.rules.RuleActions
import com.sentinel.core.rules.RuleEngine
import com.sentinel.core.rules.TrustedBrandDomains
import com.sentinel.ui.logs.PacketLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogViewModel(application: Application) : AndroidViewModel(application) {

    init {
        LogManager.initialize(application)
    }

    // Room Flow — re-emits whenever packet_logs changes, including writes from the
    // VPN service process (separate :vpn process) via enableMultiInstanceInvalidation.
    // Type is identical to the previous StateFlow so LogScreen needs no changes.
    // While the app is closed the database is not watched, and the last list stays on screen;
    // opening the app subscribes again and Room emits the current rows straight away.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val logsFlow: StateFlow<List<PacketLog>> =
        com.sentinel.core.utils.AppVisibility.visible
            .flatMapLatest { visible ->
                if (visible) LogDatabase.getInstance(application).packetLogDao().getRecentFlow(200)
                else emptyFlow()
            }
            .map { entities -> entities.map { it.toPacketLog() } }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * True when the user has chosen to keep no activity history at all. The Activity screen
     * uses it to explain an empty list instead of implying nothing has happened yet.
     */
    val loggingOff: StateFlow<Boolean> =
        com.sentinel.core.rules.FirewallOptionsStore.flow(application)
            .map { it.logRetentionHours == com.sentinel.core.logs.LogRetention.OFF }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Permanently deletes the on-device activity history (in-memory buffer + the
     * packet_logs table). The Room Flow re-emits an empty list, so the screen
     * updates on its own. Safe no-op if the DB write fails (logged inside LogManager).
     */
    fun clearLogs() {
        LogManager.clearLogs()
    }

    /** Everything the details sheet shows about one website. */
    data class LogDetail(
        val summary: DestinationSummary,
        val entries: List<PacketLog>,
        /** Today's deciding rule, or null if the rules could not be read (the sheet falls back). */
        val currentRule: CurrentRule?,
        /** Today's per-network block of the entry's app, or null when unknown. */
        val appBlock: AppBlockNow?
    )

    /**
     * What the details sheet shows for one website: counts over the whole saved activity
     * (the list on screen holds only the latest 200 entries, so its counts can be lower),
     * the newest [DETAIL_ENTRIES] entries, and which list or rule decides the website today.
     * Read-only. Null if the activity database could not be read.
     */
    suspend fun loadDetail(destination: String, packageName: String): LogDetail? =
        withContext(Dispatchers.IO) {
            try {
                val dao = LogDatabase.getInstance(getApplication()).packetLogDao()
                val summary = dao.destinationSummary(destination)
                val entries = dao.entriesFor(destination, DETAIL_ENTRIES).map { it.toPacketLog() }
                // The rules are read separately: if they fail, the counts still show and the
                // sheet uses its general wording instead of naming a rule.
                val (rule, appBlock) = try {
                    val db = RuleEngine.getInstance(getApplication()).db
                    val rules = db.ruleDao().findRulesFor(DomainCheck.candidates(destination))
                    val names = db.blocklistSourceDao().getAllFlow().first()
                        .associate { it.sourceKey to it.displayName }
                    val policies = db.ruleDao().getAllPolicies()
                        .map { it.packageName to (it.wifiBlocked to it.cellBlocked) }
                    LogWhy.currentRule(destination, rules, names) to LogWhy.appBlockNow(packageName, policies)
                } catch (e: Exception) { null to null }
                LogDetail(summary, entries, rule, appBlock)
            } catch (e: Exception) { null }
        }

    /** Block / always-allow from the details sheet; the caller has checked App Lock's view-only mode. */
    suspend fun blockSite(domain: String): RuleActions.Result =
        withContext(Dispatchers.IO) { RuleActions.block(getApplication(), domain) }

    suspend fun allowSite(domain: String): RuleActions.Result =
        withContext(Dispatchers.IO) { RuleActions.allow(getApplication(), domain) }

    suspend fun undo(undo: RuleActions.Undo): Boolean =
        withContext(Dispatchers.IO) { RuleActions.undo(getApplication(), undo) }

    companion object {
        const val DETAIL_ENTRIES = 100
    }

    // Look-Alike Domain Shield reference set for the Logs badge — seeded with the static
    // brand list immediately, refined once with the user's own frequent domains.
    private val _trustedDomains = MutableStateFlow(TrustedBrandDomains.STATIC_LIST)
    val trustedDomainsFlow: StateFlow<Set<String>> = _trustedDomains.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sinceMs = System.currentTimeMillis() - 30L * 24 * 3_600_000
                val topDomains = LogDatabase.getInstance(application)
                    .packetLogDao()
                    .topDestinations("ALLOWED", sinceMs, 40)
                    .map { it.name }
                _trustedDomains.value = TrustedBrandDomains.buildTrustedSet(topDomains)
            } catch (e: Exception) { /* keep the static-list seed on failure */ }
        }
    }
}
