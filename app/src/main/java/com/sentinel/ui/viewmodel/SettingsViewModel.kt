package com.sentinel.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.core.settings.AppPreferencesRepository
import com.sentinel.core.vpn.vpnRunning
import com.sentinel.core.settings.AppPreferencesRepository.AppLockConfig
import com.sentinel.ui.lock.AppLockAuth
import com.sentinel.ui.lock.AppLockCrypto
import com.sentinel.ui.lock.AppLockScope
import com.sentinel.ui.lock.AppLockSession
import com.sentinel.ui.lock.AppLockThrottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import com.sentinel.core.rules.withProfile
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppPreferencesRepository(app)

    val bootOnStart: StateFlow<Boolean> = repo.bootOnStart.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true
    )

    fun setBootOnStart(enabled: Boolean) {
        if (AppLockSession.blockChange(getApplication())) return
        viewModelScope.launch {
            try { repo.setBootOnStart(enabled) } catch (_: Exception) { }
        }
    }

    val appLockConfig: StateFlow<AppLockConfig> = repo.appLockConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppLockConfig(false, emptyList(), null, null, null, null)
    )

    /** True only when the lock is switched on AND an account exists to unlock it. */
    val appLockEnabled: StateFlow<Boolean> = repo.appLockEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    /** Shown while credentials are being derived, so the toggle is never silently slow. */
    var appLockBusy by mutableStateOf(false)
        private set

    /**
     * Flips the lock on or off WITHOUT touching stored accounts.
     * This is instant — no hashing — because the accounts already exist.
     */
    fun setAppLockEnabled(enabled: Boolean) {
        if (AppLockSession.blockChange(getApplication())) return
        viewModelScope.launch {
            try { repo.setAppLockEnabled(enabled) } catch (_: Exception) { }
        }
    }

    /**
     * First-time setup: creates the initial account plus the shared recovery data.
     *
     * Runs in the process-lifetime scope, NOT viewModelScope — SettingsScreen has its own
     * NavBackStackEntry, so navigating away used to cancel the hashing and silently save
     * nothing. The three derivations run CONCURRENTLY, cutting the wait to roughly the
     * cost of one instead of three.
     */
    fun createAppLock(
        username: String, password: String,
        question: String, answer: String, recoveryCode: String
    ) {
        if (AppLockSession.blockChange(getApplication())) return
        appLockBusy = true
        AppLockScope.scope.launch {
            try {
                val user   = async { AppLockAuth.newUser(username, password) }
                val ansH   = async { AppLockCrypto.create(AppLockCrypto.normalizeAnswer(answer)) }
                val recH   = async { AppLockCrypto.create(AppLockCrypto.normalizeCode(recoveryCode)) }
                repo.setAppLockUsers(listOf(user.await()))
                repo.setAppLockRecovery(question, ansH.await(), recH.await())
                repo.setAppLockEnabled(true)
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { appLockBusy = false }
            }
        }
    }

    /** Adds another account, up to [AppLockAuth.MAX_USERS]. Requires a valid existing sign-in. */
    fun addAppLockUser(username: String, password: String, onResult: (Boolean) -> Unit) {
        if (AppLockSession.blockChange(getApplication())) { onResult(false); return }
        val cfg = appLockConfig.value
        if (cfg.users.size >= AppLockAuth.MAX_USERS || AppLockAuth.isTaken(cfg, username)) {
            onResult(false); return
        }
        appLockBusy = true
        AppLockScope.scope.launch {
            var ok = false
            try {
                val user = AppLockAuth.newUser(username, password)
                repo.setAppLockUsers(cfg.users + user)
                ok = true
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { appLockBusy = false; onResult(ok) }
            }
        }
    }

    /** Removes an account. Refuses to remove the last one, which would strand the lock. */
    fun removeAppLockUser(username: String, onResult: (Boolean) -> Unit) {
        val cfg = appLockConfig.value
        if (AppLockSession.blockChange(getApplication())) { onResult(false); return }
        if (cfg.users.size <= 1 || !AppLockAuth.keepsAFullAccount(cfg.users, username)) { onResult(false); return }
        viewModelScope.launch {
            var ok = false
            try {
                repo.setAppLockUsers(
                    cfg.users.filterNot {
                        AppLockAuth.normalizeUsername(it.username) == AppLockAuth.normalizeUsername(username)
                    }
                )
                ok = true
            } catch (_: Exception) { }
            onResult(ok)
        }
    }

    /** Changes an account's username and/or password after verifying the current password. */
    fun changeAppLockUser(
        currentUsername: String, currentPassword: String,
        newUsername: String, newPassword: String,
        onResult: (Boolean) -> Unit
    ) {
        if (AppLockSession.blockChange(getApplication())) { onResult(false); return }
        val cfg = appLockConfig.value
        if (isLockedOut(cfg)) { onResult(false); return }
        appLockBusy = true
        AppLockScope.scope.launch {
            var ok = false
            try {
                val matched = AppLockAuth.authenticate(cfg, currentUsername, currentPassword)
                recordAttempt(matched != null)
                if (matched != null) {
                    val renaming = AppLockAuth.normalizeUsername(newUsername) !=
                                   AppLockAuth.normalizeUsername(matched)
                    if (!renaming || !AppLockAuth.isTaken(cfg, newUsername)) {
                        // Keeps the account view-only if it was — a password change must
                        // never silently give an account more rights.
                        val replacement = AppLockAuth.newUser(newUsername, newPassword)
                            .copy(readOnly = AppLockAuth.isReadOnly(cfg, matched))
                        val others = cfg.users.filterNot {
                            AppLockAuth.normalizeUsername(it.username) ==
                            AppLockAuth.normalizeUsername(matched)
                        }
                        repo.setAppLockUsers(others + replacement)
                        ok = true
                    }
                }
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { appLockBusy = false; onResult(ok) }
            }
        }
    }

    /** Verifies any existing account — used to gate the manage-users screen. */
    fun verifyAppLock(username: String, password: String, onResult: (Boolean) -> Unit) {
        val cfg = appLockConfig.value
        if (isLockedOut(cfg)) { onResult(false); return }
        appLockBusy = true
        AppLockScope.scope.launch {
            val matched = try { AppLockAuth.authenticate(cfg, username, password) }
                          catch (_: Exception) { null }
            recordAttempt(matched != null)
            // Managing accounts needs an account that can make changes.
            val viewOnly = matched != null && AppLockAuth.isReadOnly(cfg, matched)
            withContext(Dispatchers.Main) {
                appLockBusy = false
                if (viewOnly) AppLockSession.showDenied(getApplication()) else onResult(matched != null)
            }
        }
    }

    /** Makes an account view-only or gives it full access again. */
    fun setAppLockUserReadOnly(username: String, readOnly: Boolean, onResult: (Boolean) -> Unit) {
        if (AppLockSession.blockChange(getApplication())) { onResult(false); return }
        val cfg = appLockConfig.value
        if (readOnly && !AppLockAuth.keepsAFullAccount(cfg.users, username)) { onResult(false); return }
        viewModelScope.launch {
            var ok = false
            try {
                repo.setAppLockUsers(cfg.users.map {
                    if (AppLockAuth.normalizeUsername(it.username) == AppLockAuth.normalizeUsername(username))
                        it.copy(readOnly = readOnly) else it
                })
                ok = true
            } catch (_: Exception) { }
            onResult(ok)
        }
    }

    // ── Extra protection: watch-only, look-alike blocking, safe search ──────

    val firewallOptions: StateFlow<com.sentinel.core.rules.FirewallOptions> =
        com.sentinel.core.rules.FirewallOptionsStore.flow(app)

    // ── Activity history retention ───────────────────────────────────────────

    /** Real figures for the retention card. Nothing here is estimated or rounded up. */
    data class LogStats(
        val rows: Int = 0,
        val oldestMs: Long? = null,
        val bytesOnDisk: Long = 0L,
        val loaded: Boolean = false
    )

    private val _logStats = kotlinx.coroutines.flow.MutableStateFlow(LogStats())
    val logStats: StateFlow<LogStats> = _logStats

    fun refreshLogStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val stats = try {
                val dao = com.sentinel.core.logs.LogDatabase.getInstance(ctx).packetLogDao()
                val rows = dao.countAll()
                val oldest = if (rows == 0) null else dao.oldestTimestamp()
                // The whole history database as it actually sits on disk, write-ahead log
                // included, rather than a guess based on row count.
                val bytes = listOf("", "-wal", "-shm").sumOf { suffix ->
                    val f = ctx.getDatabasePath("sentinel-logs.db$suffix")
                    if (f.exists()) f.length() else 0L
                }
                LogStats(rows = rows, oldestMs = oldest, bytesOnDisk = bytes, loaded = true)
            } catch (e: Exception) {
                LogStats(loaded = true)
            }
            _logStats.value = stats
        }
    }

    /**
     * Applies a new retention window. Goes through the same options file the VPN process
     * reads, then trims immediately so the user sees the effect straight away rather than
     * waiting up to six hours for the periodic job.
     */
    fun setLogRetentionHours(hours: Int) {
        if (AppLockSession.blockChange(getApplication())) return
        val safe = com.sentinel.core.logs.LogRetention.sanitize(hours)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.sentinel.core.rules.FirewallOptionsStore.update(getApplication()) {
                    it.copy(logRetentionHours = safe)
                }
                val dao = com.sentinel.core.logs.LogDatabase
                    .getInstance(getApplication()).packetLogDao()
                com.sentinel.core.logs.LogTrimmer.trim(dao, safe)
                com.sentinel.core.alerts.SecurityAlerts.applyRetention(getApplication())
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.w("SettingsVM", "setLogRetentionHours failed: ${e.javaClass.simpleName}")
            }
            refreshLogStats()
        }
    }

    // ── DNS server choice (main + backup) ─────────────────────────────────

    /** What the DNS screen shows. Everything here is read from the phone, never assumed. */
    data class DnsInfo(
        val systemServers: List<String> = emptyList(),
        val privateDnsActive: Boolean = false,
        val onWifi: Boolean? = null,
        val status: com.sentinel.core.vpn.DnsResolverChoice.Status? = null,
        val vpnRunning: Boolean = false
    )

    private val _dnsInfo = kotlinx.coroutines.flow.MutableStateFlow(DnsInfo())
    val dnsInfo: StateFlow<DnsInfo> = _dnsInfo

    fun refreshDnsInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val choice = com.sentinel.core.vpn.DnsResolverChoice
            choice.attach(ctx)
            _dnsInfo.value = try {
                val running = ctx.vpnRunning()
                DnsInfo(
                    systemServers = choice.systemDnsServers().mapNotNull { it.hostAddress },
                    privateDnsActive = choice.isPrivateDnsActive(ctx),
                    onWifi = choice.underlyingIsWifi(),
                    // A status from a VPN session that has ended says nothing about now.
                    status = if (running) choice.readStatus(ctx) else null,
                    vpnRunning = running
                )
            } catch (e: Exception) {
                DnsInfo()
            }
        }
    }

    /**
     * Saves the main or backup server. [custom] is only used with the Custom choice and must
     * already be a valid address (the screen checks it). Records the change in history.
     */
    fun setDnsServer(isBackup: Boolean, choice: String, custom: String = "") {
        if (AppLockSession.blockChange(getApplication())) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val store = com.sentinel.core.rules.FirewallOptionsStore
                val before = store.read(ctx)
                val cleanCustom = if (choice == com.sentinel.core.vpn.DnsResolverChoice.CUSTOM) custom.trim() else ""
                val after = store.update(ctx) {
                    if (isBackup) it.copy(dnsBackup = choice, dnsBackupCustom = cleanCustom)
                    else it.copy(dnsMain = choice, dnsMainCustom = cleanCustom)
                }
                val old = if (isBackup) dnsKey(before.dnsBackup, before.dnsBackupCustom)
                          else dnsKey(before.dnsMain, before.dnsMainCustom)
                val new = if (isBackup) dnsKey(after.dnsBackup, after.dnsBackupCustom)
                          else dnsKey(after.dnsMain, after.dnsMainCustom)
                if (old != new) {
                    com.sentinel.core.logs.ChangeHistory.record(
                        ctx, com.sentinel.core.logs.ChangeAction.DNS_SERVER,
                        if (isBackup) "backup" else "main", before = old, after = new
                    )
                }
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.w("SettingsVM", "setDnsServer failed: ${e.javaClass.simpleName}")
            }
            refreshDnsInfo()
        }
    }

    /** Stable history value: the choice key, with the address for a custom server. */
    private fun dnsKey(choice: String, custom: String): String =
        if (choice == com.sentinel.core.vpn.DnsResolverChoice.CUSTOM) "custom:$custom" else choice

    // ── Protection level (Normal / Strict / Kids) ────────────────────────────

    /** Whether the free ad and tracker list is on; part of what a protection level sets. */
    val adListEnabled: StateFlow<Boolean> =
        com.sentinel.core.rules.RuleEngine.getInstance(app).db.blocklistSourceDao().getAllFlow()
            .map { list -> list.any { it.sourceKey == com.sentinel.core.rules.ProtectionProfile.AD_LIST_KEY && it.enabled } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Applies a protection level through the same paths as the individual switches: one
     * options write, the ad list switched on (and downloaded) if it was off, one rules
     * reload, and one change-history entry for the level.
     */
    fun applyProfile(profile: com.sentinel.core.rules.ProtectionProfile, onDone: () -> Unit = {}) {
        if (AppLockSession.blockChange(getApplication())) return
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            try {
                val store  = com.sentinel.core.rules.FirewallOptionsStore
                val dao    = com.sentinel.core.rules.RuleEngine.getInstance(ctx).db.blocklistSourceDao()
                val key    = com.sentinel.core.rules.ProtectionProfile.AD_LIST_KEY
                val list   = dao.getByKey(key)
                val listOn = list?.enabled == true
                val before = com.sentinel.core.rules.detectProfile(store.read(ctx), listOn)?.key
                    ?: com.sentinel.core.rules.ProtectionProfile.CUSTOM_KEY

                store.update(ctx) { it.withProfile(profile) }

                if (profile.adList && list != null && !listOn) {
                    dao.setEnabled(key, true)
                    com.sentinel.core.logs.ChangeHistory.record(
                        ctx, com.sentinel.core.logs.ChangeAction.BLOCKLIST, list.displayName,
                        before = com.sentinel.core.logs.ChangeValue.OFF, after = com.sentinel.core.logs.ChangeValue.ON
                    )
                    // Downloads now; the stored copy is used as soon as it arrives.
                    com.sentinel.core.vpn.BlocklistSyncWorker.runNow(ctx, key)
                }
                if (before != profile.key) {
                    com.sentinel.core.logs.ChangeHistory.record(
                        ctx, com.sentinel.core.logs.ChangeAction.PROTECTION_LEVEL, "",
                        before = before, after = profile.key
                    )
                }
                notifyVpnRulesChanged()
                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                android.util.Log.w("SettingsVM", "applyProfile failed: ${e.message}")
            }
        }
    }

    fun setWatchOnly(on: Boolean) = updateOptions { it.copy(watchOnly = on) }
    fun setBlockLookalikes(on: Boolean) = updateOptions { it.copy(blockLookalikes = on) }
    fun setSafeSearch(on: Boolean) = updateOptions { it.copy(safeSearch = on) }
    fun setSafeSearchYouTube(on: Boolean) = updateOptions { it.copy(safeSearchYouTube = on) }

    private fun updateOptions(change: (com.sentinel.core.rules.FirewallOptions) -> com.sentinel.core.rules.FirewallOptions) {
        if (AppLockSession.blockChange(getApplication())) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.sentinel.core.rules.FirewallOptionsStore.update(getApplication(), change)
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.w("SettingsVM", "updateOptions failed: ${e.message}")
            }
        }
    }

    // Same guarded pattern as the other screens: RELOAD_RULES must only reach a running VPN.
    private fun notifyVpnRulesChanged() {
        val ctx = getApplication<Application>()
        if (!ctx.vpnRunning()) return
        try {
            val intent = android.content.Intent(ctx, com.sentinel.core.vpn.SentinelVpnService::class.java).apply {
                action = com.sentinel.core.vpn.SentinelVpnService.ACTION_RELOAD_RULES
            }
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        } catch (_: Exception) { }
    }

    /** True while App Lock is making the user wait after too many wrong tries. */
    private fun isLockedOut(cfg: AppLockConfig): Boolean =
        AppLockThrottle.remainingMs(cfg.lockDeadline, com.sentinel.ui.lock.AppLockClock.now(getApplication())) > 0

    private suspend fun recordAttempt(success: Boolean) {
        try { repo.recordAppLockAttempt(success, AppLockThrottle::waitAfter) } catch (_: Exception) { }
    }
}
