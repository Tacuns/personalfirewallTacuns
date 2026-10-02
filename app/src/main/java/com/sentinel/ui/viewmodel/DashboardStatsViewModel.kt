package com.sentinel.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.core.logs.LogDatabase
import com.sentinel.core.logs.PeakHour
import com.sentinel.core.logs.StatRow
import com.sentinel.core.rules.RuleEngine
import com.sentinel.core.settings.AppPreferencesRepository
import com.sentinel.core.vpn.vpnRunning
import com.sentinel.core.vpn.vpnRunningFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import com.sentinel.core.utils.AppVisibility
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardStatsViewModel(application: Application) : AndroidViewModel(application) {

    enum class ScoreGrade(val label: String) {
        OFFLINE("OFFLINE"),
        AT_RISK("AT RISK"),
        MONITORING("MONITORING"),
        PROTECTED("PROTECTED"),
        OPTIMAL("OPTIMAL")
    }

    data class DashboardStats(
        val blockedToday: Int = 0,
        val allowedToday: Int = 0,
        val allTimeBlocked: Int = 0,
        val blocklistSize: Int = 0,
        val enabledBlocklists: Int = 0,
        // ProtectionProfile key the settings match, or "custom"; empty until first read.
        val protectionLevel: String = "",
        val topBlockedDomains: List<StatRow> = emptyList(),
        val topApps: List<StatRow> = emptyList(),
        val securityScore: Int = 0,
        val scoreGrade: ScoreGrade = ScoreGrade.OFFLINE,
        val bootEnabled: Boolean = false,
        val hourlyBlocked: List<Int> = List(24) { 0 },
        val hourlyAllowed: List<Int> = List(24) { 0 },
        val weeklyBlocked: Int = 0,
        val weeklyAllowed: Int = 0,
        val topDomain7d: String = "",
        val topDomain7dCount: Int = 0,
        val mostTargetedApp: String = "",
        val mostTargetedAppCount: Int = 0,
        val busiestHour: Int = -1
    )

    private val logDao      = LogDatabase.getInstance(application).packetLogDao()
    private val ruleEngine  = RuleEngine.getInstance(application)
    private val prefsRepo   = AppPreferencesRepository(application)

    private val _stats = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    init {
        refresh()
        // Reacts immediately when VPN connects or disconnects — works across processes
        // because vpnRunningFlow() uses ConnectivityManager, not the in-process StateFlow.
        viewModelScope.launch {
            getApplication<Application>().vpnRunningFlow().collect { refresh() }
        }
        // Re-triggers refresh when new DNS packets arrive from the VPN process.
        // Room's enableMultiInstanceInvalidation() propagates table changes across
        // the process boundary so this Flow fires even when VPN is in a separate process.
        viewModelScope.launch {
            @Suppress("OPT_IN_USAGE")
            LogDatabase.getInstance(getApplication())
                .packetLogDao()
                .getRecentFlow(1)
                .debounce(2_000)
                .collect { if (AppVisibility.isVisible) refresh() }
        }
        // Skipped refreshes while closed are caught up once, when the app is opened again.
        viewModelScope.launch {
            AppVisibility.visible.drop(1).collect { if (it) refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val startOfDay   = startOfTodayMs()
            val since24h     = System.currentTimeMillis() - 24L * 3_600_000
            val since7d      = System.currentTimeMillis() - 7L * 24 * 3_600_000
            val blockedToday = logDao.countByStatusSince("BLOCKED", startOfDay)
            val allowedToday = logDao.countByStatusSince("ALLOWED", startOfDay)
            val blistSize    = ruleEngine.getBlocklistCount()
            val enabledLists = ruleEngine.db.blocklistSourceDao().getEnabled().size
            val adListOn     = ruleEngine.db.blocklistSourceDao()
                .getByKey(com.sentinel.core.rules.ProtectionProfile.AD_LIST_KEY)?.enabled == true
            val level        = com.sentinel.core.rules.detectProfile(
                com.sentinel.core.rules.FirewallOptionsStore.read(getApplication()), adListOn
            )?.key ?: com.sentinel.core.rules.ProtectionProfile.CUSTOM_KEY
            val isVpnActive  = getApplication<Application>().vpnRunning()
            val bootEnabled  = prefsRepo.bootOnStart.first()

            val score = computeScore(isVpnActive, blistSize, blockedToday, allowedToday, bootEnabled)

            val nowSlot      = System.currentTimeMillis() / 3_600_000
            val blockedHMap  = logDao.blockedPerHourSlot(since24h).associate { it.name.toLongOrNull() to it.count }
            val allowedHMap  = logDao.allowedPerHourSlot(since24h).associate { it.name.toLongOrNull() to it.count }
            val hourlyBlocked = List(24) { i -> blockedHMap[nowSlot - 23 + i] ?: 0 }
            val hourlyAllowed = List(24) { i -> allowedHMap[nowSlot - 23 + i] ?: 0 }

            // 7-day report
            val weeklyBlocked   = logDao.countByStatusSince("BLOCKED", since7d)
            val weeklyAllowed   = logDao.countByStatusSince("ALLOWED", since7d)
            val topDomain7d     = logDao.topDestinations("BLOCKED", since7d, 1).firstOrNull()
            val mostTargeted    = logDao.topBlockedByApp(since7d, 1).firstOrNull()
            // Local clock hour, not UTC (see PeakHour).
            val busiestHour = if (weeklyBlocked > 0) PeakHour.busiest(logDao.blockedTimestampsSince(since7d)) else -1

            _stats.value = DashboardStats(
                blockedToday         = blockedToday,
                allowedToday         = allowedToday,
                allTimeBlocked       = 0,
                blocklistSize        = blistSize,
                enabledBlocklists    = enabledLists,
                protectionLevel      = level,
                topBlockedDomains    = logDao.topDestinations("BLOCKED", startOfDay, 3),
                topApps              = logDao.topApps(startOfDay, 3),
                securityScore        = score,
                scoreGrade           = scoreToGrade(score, isVpnActive),
                bootEnabled          = bootEnabled,
                hourlyBlocked        = hourlyBlocked,
                hourlyAllowed        = hourlyAllowed,
                weeklyBlocked        = weeklyBlocked,
                weeklyAllowed        = weeklyAllowed,
                topDomain7d          = topDomain7d?.name ?: "",
                topDomain7dCount     = topDomain7d?.count ?: 0,
                mostTargetedApp      = mostTargeted?.name ?: "",
                mostTargetedAppCount = mostTargeted?.count ?: 0,
                busiestHour          = busiestHour
            )
        }
    }

    private fun computeScore(
        vpnActive: Boolean,
        blocklistSize: Int,
        blockedToday: Int,
        allowedToday: Int,
        bootEnabled: Boolean
    ): Int {
        if (!vpnActive) return 0
        var score = 30
        if (blocklistSize > 1_000) score += 25
        if (blockedToday > 0) score += 15
        if (bootEnabled) score += 10
        val total = blockedToday + allowedToday
        if (total > 0) score += ((blockedToday.toFloat() / total) * 20).toInt().coerceAtMost(20)
        return score.coerceIn(0, 100)
    }

    private fun scoreToGrade(score: Int, vpnActive: Boolean): ScoreGrade = when {
        !vpnActive  -> ScoreGrade.OFFLINE
        score >= 85 -> ScoreGrade.OPTIMAL
        score >= 65 -> ScoreGrade.PROTECTED
        score >= 40 -> ScoreGrade.MONITORING
        else        -> ScoreGrade.AT_RISK
    }

    private fun startOfTodayMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
