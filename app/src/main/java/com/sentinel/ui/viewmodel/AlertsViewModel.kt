package com.sentinel.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.core.alerts.AlertDatabase
import com.sentinel.core.alerts.AlertEntity
import com.sentinel.core.logs.ChangeAction
import com.sentinel.core.logs.ChangeHistory
import com.sentinel.core.logs.ChangeValue
import com.sentinel.core.logs.LogRetention
import com.sentinel.core.rules.FirewallOptionsStore
import com.sentinel.core.rules.RuleEngine
import com.sentinel.core.vpn.SentinelVpnService
import com.sentinel.core.vpn.vpnRunning
import com.sentinel.ui.lock.AppLockSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AlertDatabase.getInstance(app).alertDao()
    private val ruleEngine = RuleEngine.getInstance(app)

    val alerts: StateFlow<List<AlertEntity>> =
        dao.allFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when Activity history is set to keep nothing, so alerts are not kept either. */
    val historyOff: StateFlow<Boolean> = FirewallOptionsStore.flow(app)
        .map { it.logRetentionHours == LogRetention.OFF }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Blocklist key → name, for failed-update alerts. A removed list keeps its key. */
    val listNames: StateFlow<Map<String, String>> = ruleEngine.db.blocklistSourceDao().getAllFlow()
        .map { list -> list.associate { it.sourceKey to it.displayName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Sites the user already blocks, so "Block this site" shows "Blocked" instead. */
    private val _userBlocked = MutableStateFlow<Set<String>>(emptySet())
    val userBlocked: StateFlow<Set<String>> = _userBlocked

    init { refreshUserBlocked() }

    private fun refreshUserBlocked() {
        viewModelScope.launch(Dispatchers.IO) {
            _userBlocked.value = try {
                ruleEngine.db.ruleDao().getUserBlockedDomains().map { it.domain }.toSet()
            } catch (_: Exception) { emptySet() }
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { try { dao.markRead(id) } catch (_: Exception) { } }
    }

    fun markAllRead() {
        viewModelScope.launch(Dispatchers.IO) { try { dao.markAllRead() } catch (_: Exception) { } }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) { try { dao.deleteAll() } catch (_: Exception) { } }
    }

    /**
     * Blocks a site a look-alike warning named, through the same path as the Protect tab:
     * a USER rule, a change-history entry and a reload of the running firewall.
     */
    fun blockSite(domain: String) {
        if (AppLockSession.blockChange(getApplication())) return
        val clean = domain.trim().lowercase()
        if (clean.isBlank() || !clean.contains('.')) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ruleEngine.addBlockRule(clean, -1, "", "USER")
                ChangeHistory.record(getApplication(), ChangeAction.DOMAIN_BLOCKED, clean,
                    before = ChangeValue.ALLOWED, after = ChangeValue.BLOCKED)
                notifyVpnRulesChanged()
                _userBlocked.value = _userBlocked.value + clean
            } catch (e: Exception) {
                android.util.Log.w("AlertsVM", "blockSite failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun notifyVpnRulesChanged() {
        val ctx = getApplication<Application>()
        if (!ctx.vpnRunning()) return
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, SentinelVpnService::class.java).apply {
                action = SentinelVpnService.ACTION_RELOAD_RULES
            })
        } catch (_: Exception) { }
    }
}
