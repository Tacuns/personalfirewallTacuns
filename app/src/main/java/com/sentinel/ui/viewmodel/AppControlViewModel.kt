package com.sentinel.ui.viewmodel

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sentinel.core.logs.ChangeAction
import com.sentinel.core.logs.ChangeHistory
import com.sentinel.core.logs.ChangeValue
import com.sentinel.core.logs.LogDatabase
import com.sentinel.core.logs.RuleHits
import com.sentinel.core.logs.StatRow
import com.sentinel.core.rules.AppPolicy
import com.sentinel.core.rules.DomainCheck
import com.sentinel.core.rules.FirewallRule
import com.sentinel.core.rules.FirewallOptionsStore
import com.sentinel.core.rules.SelfProtection
import com.sentinel.core.rules.RuleEngine
import com.sentinel.core.schedule.ScheduleCategories
import com.sentinel.core.schedule.ScheduleCheckWorker
import com.sentinel.core.settings.AppPreferencesRepository
import com.sentinel.core.vpn.BlocklistSchedule
import com.sentinel.core.vpn.BlocklistSyncWorker
import com.sentinel.core.vpn.CustomBlocklist
import com.sentinel.core.vpn.SentinelVpnService
import com.sentinel.core.vpn.vpnRunning
import com.sentinel.ui.lock.AppLockSession
import com.sentinel.ui.rules.AppRule
import com.tacu.nsfwzerotrust.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.sentinel.core.logs.PacketLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AppControlViewModel(private val context: Context) : ViewModel() {

    // Singleton — same instance the VPN service uses, so cache changes are
    // immediately visible to DnsInterceptor without a tunnel restart.
    private val ruleEngine     = RuleEngine.getInstance(context)
    private val packageManager = context.packageManager
    private val logDao         = LogDatabase.getInstance(context).packetLogDao()
    private val prefsRepo      = AppPreferencesRepository(context)

    // Rate limit state — 2 combined export+import uses per 24-hour window
    data class RateLimitState(
        val usesRemaining: Int = 2,  // 0, 1, or 2
        val resetAtMs: Long    = 0L  // absolute ms when the block lifts; 0 = not blocked
    )
    val rateLimitState = mutableStateOf(RateLimitState())

    // Schedule state — 1 active schedule included in free tier
    data class ScheduleState(
        val enabled:   Boolean = false,
        val category:  String  = ScheduleCategories.SOCIAL,
        val startHour: Int     = 22,   // default 10 PM → 6 AM (night mode)
        val startMin:  Int     = 0,
        val endHour:   Int     = 6,
        val endMin:    Int     = 0,
        val days:      String  = "1111100",  // Mon–Fri; index 0=Mon … 6=Sun
        val activeNow: Boolean = false
    )
    val scheduleState = mutableStateOf(ScheduleState())

    val appList         = mutableStateListOf<AppRule>()
    // Blocked apps across BOTH lists (Installed + System), not just the rows on screen.
    val blockedAppCount = mutableStateOf(0)
    // One UID per app that either list can show; filled by loadApps(), used for the count.
    @Volatile private var visibleAppUids: List<Int> = emptyList()
    // Keeps a list reload and a switch tap from interleaving (see loadApps).
    private val policyRowsLock = Mutex()
    // Apps that run under one shared UID, by UID; see sharedUidGroups().
    val sharedUidApps = mutableStateOf<Map<Int, List<Pair<String, String>>>>(emptyMap())
    val blockedDomains  = mutableStateListOf<String>()
    val allowedDomains  = mutableStateListOf<String>()   // Always allow list
    val appRiskMap      = mutableStateMapOf<String, Int>()
    val appQueryRateMap = mutableStateMapOf<String, Int>()
    val appAnomalyMap   = mutableStateMapOf<String, Float>()   // ratio vs 7d baseline; >0 = spike
    val appBeaconMap        = mutableStateMapOf<String, Int>()   // pkg -> beacon interval in seconds
    val appUpdateChangeMap  = mutableStateMapOf<String, Float>() // pkg -> post/pre hourly ratio; >0 = behavior changed after update
    val suggestedBlocks     = mutableStateListOf<String>()
    val exportMessage       = mutableStateOf<String?>(null)
    val importMessage       = mutableStateOf<String?>(null)
    val canUndoRestore      = mutableStateOf(false)   // true while the last restore can be undone
    val ruleHits            = mutableStateMapOf<String, Int>()   // domain rule -> recent blocks

    // App Connection Profile — populated on demand when user taps an app card
    data class AppProfile(
        val totalQueries: Int          = 0,
        val blockedCount: Int          = 0,
        val allowedCount: Int          = 0,
        val topBlocked:   List<StatRow> = emptyList(),
        val topAllowed:   List<StatRow> = emptyList()
    )
    val selectedApp     = mutableStateOf<AppRule?>(null)
    val selectedProfile = mutableStateOf(AppProfile())
    var showSystemApps  = mutableStateOf(false)
        private set

    // Serialises schedule updates so a rapid disable→enable cannot leave rules erased
    private var scheduleUpdateJob: Job? = null

    // Debounces VPN tunnel rebuilds for rapid block/unblock app-policy changes
    private var restartDebounceJob: Job? = null

    init {
        // This ViewModel is created while the screen is being built, and its state lists
        // belong to that build until it finishes. A background load that reads them before
        // then fails with "Reading a state that was created after the snapshot was taken",
        // which left the blocked-domain list empty after reopening the app (the rules were
        // still saved). Dispatchers.Main (not .immediate) posts this block, so it starts
        // only after the screen build has been applied.
        viewModelScope.launch(Dispatchers.Main) {
            loadApps()
            loadBlockedDomains()
            loadAllowedDomains()
            loadRiskScores()
            loadSuggestions()
            loadRateLimit()
            loadSchedule()
            viewModelScope.launch(Dispatchers.IO) {
                val exists = RestoreUndo.exists(context)
                withContext(Dispatchers.Main) { canUndoRestore.value = exists }
            }
            // Light analysis: query-rate anomalies and beaconing — fast, safe every 30 s.
            viewModelScope.launch {
                while (true) {
                    com.sentinel.core.utils.AppVisibility.awaitVisible()   // paused while the app is closed
                    loadQueryRates()
                    detectBeaconing()
                    delay(30_000)
                }
            }
            // Heavy analysis: iterates every installed app with 2 DB queries each.
            // Running this every 30 s was queuing hundreds of Room calls per minute
            // after extended uptime. 5-minute cycle gives identical results with ~10×
            // less DB pressure. Logic inside detectUpdateBehaviorChange() is unchanged.
            viewModelScope.launch {
                while (true) {
                    com.sentinel.core.utils.AppVisibility.awaitVisible()   // paused while the app is closed
                    detectUpdateBehaviorChange()
                    delay(300_000)   // 5 minutes
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Domain blocking
    // RuleEngine.addBlockRule / removeBlockRule update the singleton cache
    // immediately → DnsInterceptor sees the change on the very next DNS packet.
    // No VPN tunnel restart is needed for domain rules.
    // -------------------------------------------------------------------------

    // The Activity details sheet blocks and allows websites straight in the database, and this
    // ViewModel stays alive while the Protect tab is in the background, so its lists would keep
    // the copy read when Protect was first opened. The first showing is covered by init.
    private var shownBefore = false

    fun onScreenShown() {
        if (!shownBefore) { shownBefore = true; loadSuggestions(); return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = ruleEngine.db.ruleDao()
                val blocked = dao.getUserBlockedDomains().map { it.domain }
                val allowed = dao.getAllowRules().map { it.domain }.sorted()
                withContext(Dispatchers.Main) {
                    syncKeepingOrder(blockedDomains, blocked)
                    syncKeepingOrder(allowedDomains, allowed)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "refresh lists failed: ${e.message}")
            }
            loadSuggestions()   // after the lists, so a website blocked elsewhere is not suggested
        }
    }

    // Same websites keep their place on screen; removed ones go, new ones go on top.
    private fun syncKeepingOrder(list: MutableList<String>, saved: List<String>) {
        val savedSet = saved.toSet()
        list.removeAll { it !in savedSet }
        val shown = list.toSet()
        list.addAll(0, saved.filter { it !in shown })
    }

    private fun loadBlockedDomains() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rules = ruleEngine.db.ruleDao().getUserBlockedDomains()
                blockedDomains.clear()
                blockedDomains.addAll(rules.map { it.domain })
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "loadBlockedDomains failed: ${e.message}")
            }
        }
    }

    // A leading "*." is stripped to the apex domain: RuleEngine.isDomainBlocked() already
    // walks parent domains, so blocking "tumblr.com" blocks every subdomain. Storing the
    // literal "*.tumblr.com" would never match anything and fail silently.
    // Any other "*" is rejected — mid-label wildcards are not supported by the matcher.
    fun blockDomain(domain: String) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        val clean = domain.trim().lowercase()
            .removePrefix("*.")
            .removePrefix("www.")
        if (clean.isBlank() || clean.contains(" ") || clean.contains("*") || !clean.contains(".")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ruleEngine.addBlockRule(clean, -1, "", "USER")
                if (!blockedDomains.contains(clean)) blockedDomains.add(0, clean)
                withContext(Dispatchers.Main) { allowedDomains.remove(clean) }
                ChangeHistory.record(
                    context, ChangeAction.DOMAIN_BLOCKED, clean,
                    before = ChangeValue.ALLOWED, after = ChangeValue.BLOCKED
                )
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "blockDomain failed: ${e.message}")
            }
        }
    }

    fun unblockDomain(domain: String) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ruleEngine.removeBlockRule(domain)
                blockedDomains.remove(domain)
                ChangeHistory.record(
                    context, ChangeAction.DOMAIN_UNBLOCKED, domain,
                    before = ChangeValue.BLOCKED, after = ChangeValue.ALLOWED
                )
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "unblockDomain failed: ${e.message}")
            }
        }
    }

    /**
     * Bulk unblock for multi-select. Deletes every rule first, then notifies the VPN
     * exactly ONCE — looping unblockDomain() would fire one ACTION_RELOAD_RULES per
     * domain and hammer the :vpn process with dozens of redundant cache reloads.
     */
    fun unblockDomains(domains: Collection<String>) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        if (domains.isEmpty()) return
        val targets = domains.toList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                targets.forEach { ruleEngine.removeBlockRule(it) }
                targets.forEach {
                    ChangeHistory.record(
                        context, ChangeAction.DOMAIN_UNBLOCKED, it,
                        before = ChangeValue.BLOCKED, after = ChangeValue.ALLOWED
                    )
                }
                withContext(Dispatchers.Main) { blockedDomains.removeAll(targets.toSet()) }
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "unblockDomains failed: ${e.message}")
            }
        }
    }

    /**
     * Corrects a mistyped domain. `domain` is the table's primary key, so this is
     * add-then-remove rather than an UPDATE.
     *
     * Order matters: the replacement is added FIRST. If the second step somehow fails
     * the user is left over-blocking (both rules), never unprotected.
     */
    fun renameDomain(oldDomain: String, newDomain: String) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        val clean = newDomain.trim().lowercase()
            .removePrefix("*.")
            .removePrefix("www.")
        if (clean.isBlank() || clean.contains(" ") || clean.contains("*") || !clean.contains(".")) return
        if (clean == oldDomain) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ruleEngine.addBlockRule(clean, -1, "", "USER")
                ruleEngine.removeBlockRule(oldDomain)
                ChangeHistory.record(
                    context, ChangeAction.DOMAIN_RENAMED, clean,
                    before = oldDomain, after = clean
                )
                withContext(Dispatchers.Main) {
                    val idx = blockedDomains.indexOf(oldDomain)
                    when {
                        // Replacement was already in the list — just drop the old entry.
                        blockedDomains.contains(clean) -> if (idx >= 0) blockedDomains.removeAt(idx)
                        idx >= 0                       -> blockedDomains[idx] = clean
                        else                           -> blockedDomains.add(0, clean)
                    }
                }
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "renameDomain failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Test a domain — read-only, changes nothing. Asks the rules database (what the
    // VPN loads its rules from), not this process's RuleEngine, which may not hold
    // the large blocklist.
    // -------------------------------------------------------------------------

    /**
     * Recent block counts for each of the user's domain rules, from the activity log.
     * Read-only; refreshed when the Rules screen opens, never polled.
     */
    fun loadRuleHits() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rules  = ruleEngine.db.ruleDao().getUserBlockedDomains().map { it.domain }
                val counts = RuleHits.count(rules, logDao.userRuleBlocksByDestination())
                withContext(Dispatchers.Main) {
                    ruleHits.clear()
                    ruleHits.putAll(counts)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "loadRuleHits failed: ${e.message}")
            }
        }
    }

    /** [input] is what was tested, so a stale result is hidden once the text changes. */
    data class DomainTestResult(
        val input:      String,
        val domain:     String?,        // null = not a usable website address
        val rule:       FirewallRule?,  // null = not blocked
        val firewallOn: Boolean,
        val appBlockActive: Boolean = false,  // an app is blocked: website rules skip other apps
        val watchOnly: Boolean = false        // watch-only mode: nothing is blocked right now
    )
    val domainTestResult = mutableStateOf<DomainTestResult?>(null)

    /** Hides the last "Check if blocked" answer once anything it depends on changes. */
    fun clearDomainTest() { domainTestResult.value = null }

    fun testDomain(input: String) {
        val clean = DomainCheck.normalize(input)
        if (clean == null) {
            domainTestResult.value = DomainTestResult(input, null, null, context.vpnRunning())
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rules  = ruleEngine.db.ruleDao().findRulesFor(DomainCheck.candidates(clean))
                val appBlocked = ruleEngine.db.ruleDao().getAllPolicies().any { it.wifiBlocked || it.cellBlocked }
                val result = DomainTestResult(input, clean, DomainCheck.decide(clean, rules),
                                              context.vpnRunning(), appBlocked,
                                              FirewallOptionsStore.read(context).watchOnly)
                withContext(Dispatchers.Main) { domainTestResult.value = result }
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "testDomain failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Geo TLD blocking — adds bare ccTLD (e.g. "cn") as a USER rule so the
    // parent-domain walk in RuleEngine blocks all *.cn domains. Stored as
    // source=USER so reloadCache() picks it up on VPN restart.
    // -------------------------------------------------------------------------

    fun blockGeoTld(tld: String) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ruleEngine.addBlockRule(tld, -1, "", "USER")
                if (!blockedDomains.contains(tld)) blockedDomains.add(0, tld)
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "blockGeoTld failed: ${e.message}")
            }
        }
    }

    fun unblockGeoTld(tld: String) = unblockDomain(tld)

    // -------------------------------------------------------------------------
    // Always allow list — sites that no list, schedule or look-alike check blocks.
    // Stored as source='ALLOW' rows in the same table, so no database change.
    // -------------------------------------------------------------------------

    private fun loadAllowedDomains() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val domains = ruleEngine.db.ruleDao().getAllowRules().map { it.domain }.sorted()
                withContext(Dispatchers.Main) {
                    allowedDomains.clear()
                    allowedDomains.addAll(domains)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "loadAllowedDomains failed: ${e.message}")
            }
        }
    }

    fun allowDomain(input: String) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        val clean = DomainCheck.normalize(input)?.removePrefix("www.") ?: return
        if (!clean.contains('.')) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (clean !in allowedDomains && allowedDomains.size >= MAX_ALLOWED) return@launch
                ruleEngine.addAllowRule(clean)
                ChangeHistory.record(context, ChangeAction.DOMAIN_ALWAYS_ALLOWED, clean,
                    before = ChangeValue.BLOCKED, after = ChangeValue.ALLOWED)
                withContext(Dispatchers.Main) {
                    if (!allowedDomains.contains(clean)) allowedDomains.add(0, clean)
                    blockedDomains.remove(clean)   // the allow row replaced a block on this name
                }
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "allowDomain failed: ${e.message}")
            }
        }
    }

    fun removeAllowed(domain: String) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ruleEngine.removeAllowRule(domain)
                ChangeHistory.record(context, ChangeAction.DOMAIN_ALLOW_REMOVED, domain,
                    before = ChangeValue.ALLOWED, after = ChangeValue.BLOCKED)
                withContext(Dispatchers.Main) { allowedDomains.remove(domain) }
                notifyVpnRulesChanged()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "removeAllowed failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // App Connection Profile — loads 7-day DNS breakdown for a single app
    // -------------------------------------------------------------------------

    fun loadAppProfile(app: AppRule) {
        selectedApp.value = app
        selectedProfile.value = AppProfile()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val since7d    = System.currentTimeMillis() - 7L * 24 * 3_600_000
                val blocked    = logDao.countByAppAndStatus(app.packageName, "BLOCKED", since7d)
                val allowed    = logDao.countByAppAndStatus(app.packageName, "ALLOWED", since7d)
                val topBlocked = logDao.topDestinationsForApp(app.packageName, "BLOCKED", since7d, 5)
                val topAllowed = logDao.topDestinationsForApp(app.packageName, "ALLOWED", since7d, 5)
                withContext(Dispatchers.Main) {
                    selectedProfile.value = AppProfile(
                        totalQueries = blocked + allowed,
                        blockedCount = blocked,
                        allowedCount = allowed,
                        topBlocked   = topBlocked,
                        topAllowed   = topAllowed
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "loadAppProfile failed: ${e.message}")
            }
        }
    }

    fun clearAppProfile() {
        selectedApp.value = null
        selectedProfile.value = AppProfile()
    }

    // -------------------------------------------------------------------------
    // Import / Export Rules
    // Exports all source='USER' rules (custom domains + geo TLDs) to a plain-text
    // file in MediaStore Downloads — no storage permission needed on minSdk 29.
    // Import reads any .txt file and merges new domains (skips duplicates).
    // -------------------------------------------------------------------------

    /**
     * [includeHistory]: the activity history (websites and the app that looked them up) is
     * private browsing data, so it is only added when the user ticks it in the backup dialog.
     * Restore accepts files with or without it.
     */
    fun exportRules(includeHistory: Boolean = false) {
        if (AppLockSession.blockChange(context)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Gate check — does NOT consume a use yet
                if (isRateLimited()) {
                    withContext(Dispatchers.Main) {
                        exportMessage.value =
                            "Daily limit reached — resets in ${formatTimeUntil(rateLimitState.value.resetAtMs)}"
                    }
                    return@launch
                }
                val domains  = ruleEngine.db.ruleDao().getUserBlockedDomains().map { it.domain }.sorted()
                val policies = ruleEngine.db.ruleDao().getAllPolicies()
                    .filter { it.wifiBlocked || it.cellBlocked }
                val sources  = ruleEngine.db.blocklistSourceDao().getAllFlow().first()
                val sched    = scheduleState.value
                // LogManager caps the table at MAX_DB (500) rows, so this reads the whole
                // table and the size stays bounded and predictable.
                val logs     = if (includeHistory) logDao.getRecent(LOG_BACKUP_CAP) else emptyList()
                val allowed  = ruleEngine.db.ruleDao().getAllowRules().map { it.domain }.sorted()
                val options  = FirewallOptionsStore.read(context)

                if (domains.isEmpty() && policies.isEmpty() && logs.isEmpty() && allowed.isEmpty()) {
                    // Nothing worth backing up — don't consume a use
                    withContext(Dispatchers.Main) { exportMessage.value = "Nothing to back up yet" }
                    return@launch
                }

                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())

                // App policies are stored by UID, but UIDs are reassigned on reinstall —
                // so the backup records packageName and the UID is re-resolved on restore.
                val content = org.json.JSONObject().apply {
                    put("format", BACKUP_FORMAT)
                    put("version", BACKUP_VERSION)
                    put("exportedAt", date)
                    put("domains", org.json.JSONArray(domains))
                    put("allowed", org.json.JSONArray(allowed))
                    put("options", org.json.JSONObject().apply {
                        put("blockLookalikes", options.blockLookalikes)
                        put("safeSearch", options.safeSearch)
                        put("safeSearchYouTube", options.safeSearchYouTube)
                        put("dnsMain", options.dnsMain)
                        put("dnsMainCustom", options.dnsMainCustom)
                        put("dnsBackup", options.dnsBackup)
                        put("dnsBackupCustom", options.dnsBackupCustom)
                    })
                    put("apps", org.json.JSONArray().apply {
                        policies.forEach { p ->
                            put(org.json.JSONObject().apply {
                                put("package", p.packageName)
                                put("wifiBlocked", p.wifiBlocked)
                                put("cellBlocked", p.cellBlocked)
                            })
                        }
                    })
                    put("schedule", org.json.JSONObject().apply {
                        put("enabled", sched.enabled)
                        put("category", sched.category)
                        put("startHour", sched.startHour)
                        put("startMin", sched.startMin)
                        put("endHour", sched.endHour)
                        put("endMin", sched.endMin)
                        put("days", sched.days)
                    })
                    put("blocklists", org.json.JSONArray().apply {
                        sources.forEach { s ->
                            put(org.json.JSONObject().apply {
                                put("key", s.sourceKey)
                                put("enabled", s.enabled)
                                put("refreshHours", BlocklistSchedule.intervalHours(context, s.sourceKey))
                                // A list the user added exists only on this phone, so keep
                                // what is needed to recreate it on restore.
                                if (CustomBlocklist.isCustom(s.sourceKey)) {
                                    put("name", s.displayName)
                                    put("url", s.url)
                                }
                            })
                        }
                    })
                    // Activity history, only when the user chose it. Short keys keep the file
                    // small, and LOG_BACKUP_CAP bounds it.
                    if (includeHistory) put("logs", org.json.JSONArray().apply {
                        logs.forEach { l ->
                            put(org.json.JSONObject().apply {
                                put("app", l.appName)
                                put("pkg", l.packageName)
                                put("dest", l.destination)
                                put("status", l.status)
                                put("ts", l.timestampMs)
                                put("threat", l.threatLabel)
                            })
                        }
                    })
                }.toString(2)

                val fileName = "sentinel-backup-$date.json"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    // Only consume a use after the file is written successfully
                    consumeRateLimitUse()
                    withContext(Dispatchers.Main) {
                        exportMessage.value =
                            "Backed up ${domains.size} domain${if (domains.size == 1) "" else "s"}, " +
                            "${policies.size} app rule${if (policies.size == 1) "" else "s"}" +
                            (if (includeHistory) ", ${logs.size} log entr${if (logs.size == 1) "y" else "ies"}" else "") +
                            " → Downloads/$fileName" +
                            (if (includeHistory) " · contains your activity history — share with care" else "")
                    }
                    // Share sheet — sends the saved backup FILE (EXTRA_STREAM), never its text.
                    // The backup may contain activity history, and a whole JSON document placed in
                    // EXTRA_TEXT is too large for many apps (WhatsApp: "Couldn't share").
                    // The MediaStore row belongs to this app; the read grant lets only the app
                    // the user picks open this one file.
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "TacU-NS Firewall Backup")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(fileName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Share firewall backup").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                } else {
                    // File creation failed — don't consume a use
                    withContext(Dispatchers.Main) { exportMessage.value = "Export failed: could not create file" }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { exportMessage.value = "Export failed: ${e.message}" }
                android.util.Log.e("AppControlVM", "exportRules failed: ${e.message}")
            }
        }
    }

    fun importRules(uri: Uri) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Gate check — does NOT consume a use yet
                if (isRateLimited()) {
                    withContext(Dispatchers.Main) {
                        importMessage.value =
                            "Daily limit reached — resets in ${formatTimeUntil(rateLimitState.value.resetAtMs)}"
                    }
                    return@launch
                }
                // Choose the parser by peeking the first non-whitespace character.
                // A JSON backup has to be read whole, so restoreBackup() enforces a hard
                // byte cap; the legacy plain-text path below keeps streaming line by line,
                // which is the original fix for the production OutOfMemoryError.
                val isJsonBackup = context.contentResolver.openInputStream(uri)?.use { ins ->
                    val reader = ins.bufferedReader()
                    var c = reader.read()
                    while (c != -1 && c.toChar().isWhitespace()) c = reader.read()
                    c == '{'.code
                }
                if (isJsonBackup == null) {
                    withContext(Dispatchers.Main) { importMessage.value = "Could not read file" }
                    return@launch
                }
                if (isJsonBackup) {
                    restoreBackup(uri)
                    return@launch
                }

                val bufferedReader = context.contentResolver.openInputStream(uri)?.bufferedReader()
                if (bufferedReader == null) {
                    withContext(Dispatchers.Main) { importMessage.value = "Could not read file" }
                    return@launch
                }

                val existing   = blockedDomains.toSet()
                val newDomains = mutableListOf<String>()
                var skipped    = 0
                val importCap  = 5_000   // guard against OOM from abnormally large files

                // Streams the file one line at a time instead of materializing the whole file
                // into a List<String> first — readLines() previously caused an OutOfMemoryError
                // on abnormally large files before importCap could ever take effect.
                // useLines() also closes the underlying reader when the block completes.
                bufferedReader.useLines { lines ->
                    for (raw in lines) {
                        val domain = raw.trim().lowercase().removePrefix("www.")
                        if (domain.isEmpty() || domain.startsWith("#")) continue
                        // Standard domains, or short bare endings like "cn", "ru"
                        if (!DomainCheck.isValidRuleName(domain)) continue

                        if (newDomains.size >= importCap) break
                        if (domain in existing || domain in newDomains) {
                            skipped++
                        } else {
                            ruleEngine.addBlockRule(domain, -1, "", "USER")
                            newDomains.add(domain)
                        }
                    }
                }

                if (newDomains.isNotEmpty()) {
                    withContext(Dispatchers.Main) { blockedDomains.addAll(0, newDomains) }
                    saveRestoreUndo(RestoreUndo(newDomains.toList(), emptyList(), null, emptyMap()))
                    notifyVpnRulesChanged()
                    // Only consume a use when at least one new domain was actually imported.
                    // Importing a file full of duplicates or with no valid domains is free.
                    consumeRateLimitUse()
                }

                val msg = when {
                    newDomains.isEmpty() && skipped == 0 ->
                        "No valid domains found in file"
                    newDomains.isEmpty() ->
                        "All $skipped rule${if (skipped == 1) "" else "s"} already exist"
                    skipped == 0 ->
                        "Imported ${newDomains.size} rule${if (newDomains.size == 1) "" else "s"}"
                    else ->
                        "Imported ${newDomains.size} rule${if (newDomains.size == 1) "" else "s"} ($skipped already existed)"
                }
                if (newDomains.isNotEmpty()) {
                    ChangeHistory.record(
                        context, ChangeAction.RULES_IMPORTED, newDomains.size.toString()
                    )
                }
                withContext(Dispatchers.Main) { importMessage.value = msg }
            } catch (e: Exception) {
                // File read error — don't consume a use
                withContext(Dispatchers.Main) { importMessage.value = "Import failed: ${e.message}" }
                android.util.Log.e("AppControlVM", "importRules failed: ${e.message}")
            }
        }
    }

    /**
     * Restores a full JSON backup: domains, per-app rules, schedule and blocklist states.
     *
     * Unlike the plain-text path this must hold the whole document in memory to parse it,
     * so the read is hard-capped. A genuine backup is a few KB; anything past the cap is
     * rejected rather than risking the OutOfMemoryError that importRules() once produced.
     */
    private suspend fun restoreBackup(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.use { ins ->
            BackupReader.readCapped(ins, MAX_BACKUP_BYTES)
        }
        if (text == null) {
            withContext(Dispatchers.Main) { importMessage.value = "Backup file is too large or unreadable" }
            return
        }

        val root = try { org.json.JSONObject(text) } catch (e: Exception) { null }
        if (root == null || root.optString("format") != BACKUP_FORMAT) {
            withContext(Dispatchers.Main) { importMessage.value = "Not a TacU-NS backup file" }
            return
        }

        // A backup can be edited by hand, so each value is checked (RestoreRules) and
        // anything the app's own screens could not have produced is skipped and counted.
        var invalid = 0

        // ── Domains ──────────────────────────────────────────────────────────
        val existing   = blockedDomains.toSet()
        val newDomains = mutableListOf<String>()
        root.optJSONArray("domains")?.let { arr ->
            for (i in 0 until minOf(arr.length(), IMPORT_CAP)) {
                val d = arr.optString(i).trim().lowercase()
                    .removePrefix("*.").removePrefix("www.")
                if (!DomainCheck.isValidRuleName(d)) { invalid++; continue }
                if (d in existing || d in newDomains) continue
                ruleEngine.addBlockRule(d, -1, "", "USER")
                newDomains.add(d)
            }
        }

        // ── Always allow list ────────────────────────────────────────────────
        root.optJSONArray("allowed")?.let { arr ->
            val current = allowedDomains.toSet()
            for (i in 0 until minOf(arr.length(), MAX_ALLOWED)) {
                val d = DomainCheck.normalize(arr.optString(i))?.removePrefix("www.")
                if (d == null || !d.contains('.') || !DomainCheck.isValidRuleName(d)) { invalid++; continue }
                if (d in current || d in newDomains) continue
                ruleEngine.addAllowRule(d)
            }
        }

        // ── Extra protection switches. Watch-only is never switched on from a file:
        //    a backup must not be able to quietly turn blocking off.
        root.optJSONObject("options")?.let { o ->
            FirewallOptionsStore.update(context) {
                // Older backups have no YouTube or DNS keys: keep YouTube restricted and today's
                // DNS servers. A custom address is only taken back if it is still a valid server.
                val choice = com.sentinel.core.vpn.DnsResolverChoice
                val mainCustom = o.optString("dnsMainCustom").takeIf { choice.isValidCustom(it) } ?: ""
                val backupCustom = o.optString("dnsBackupCustom").takeIf { choice.isValidCustom(it) } ?: ""
                var main = choice.sanitizeMain(o.optString("dnsMain").ifEmpty { null })
                var backup = choice.sanitizeBackup(o.optString("dnsBackup").ifEmpty { null })
                if (main == choice.CUSTOM && mainCustom.isEmpty()) main = choice.DEFAULT_MAIN
                if (backup == choice.CUSTOM && backupCustom.isEmpty()) backup = choice.DEFAULT_BACKUP
                it.copy(
                    blockLookalikes = o.optBoolean("blockLookalikes"),
                    safeSearch = o.optBoolean("safeSearch"),
                    safeSearchYouTube = o.optBoolean("safeSearchYouTube", true),
                    dnsMain = main, dnsMainCustom = mainCustom,
                    dnsBackup = backup, dnsBackupCustom = backupCustom
                )
            }
        }

        // ── Per-app rules — UID is re-resolved from packageName, because UIDs
        //    are reassigned when an app is reinstalled or moved to another device.
        var appsRestored = 0
        var appsMissing  = 0
        val previousApps = mutableListOf<AppPolicy>()
        root.optJSONArray("apps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o   = arr.optJSONObject(i) ?: continue
                val pkg = o.optString("package")
                if (!RestoreRules.isRestorableApp(pkg, context.packageName)) { invalid++; continue }
                val uid = try { packageManager.getApplicationInfo(pkg, 0).uid }
                          catch (e: Exception) { appsMissing++; continue }
                if (previousApps.none { it.uid == uid }) {
                    previousApps += ruleEngine.db.ruleDao().getPolicy(uid) ?: AppPolicy(uid, pkg, false, false)
                }
                ruleEngine.db.ruleDao().updatePolicy(
                    SelfProtection.sanitize(context,
                        AppPolicy(uid, pkg, o.optBoolean("wifiBlocked"), o.optBoolean("cellBlocked")))
                )
                appsRestored++
            }
        }

        // ── Schedule ─────────────────────────────────────────────────────────
        var previousSchedule: ScheduleState? = null
        root.optJSONObject("schedule")?.let { s ->
            val category  = s.optString("category", ScheduleCategories.SOCIAL)
            val startHour = s.optInt("startHour")
            val startMin  = s.optInt("startMin")
            val endHour   = s.optInt("endHour")
            val endMin    = s.optInt("endMin")
            val days      = s.optString("days", "1111100")
            // A schedule the time controls could not show is skipped whole; the current one stays.
            if (!RestoreRules.isValidSchedule(category, startHour, startMin, endHour, endMin, days)) {
                invalid++
                return@let
            }
            previousSchedule = scheduleState.value
            val on = s.optBoolean("enabled")
            prefsRepo.setSchedule(
                enabled   = on,
                category  = category,
                startHour = startHour,
                startMin  = startMin,
                endHour   = endHour,
                endMin    = endMin,
                days      = days
            )
            // Writing the preference alone is not enough — the periodic worker has to be
            // started or cancelled to match, exactly as updateSchedule() does.
            if (on) ScheduleCheckWorker.start(context) else ScheduleCheckWorker.stop(context)
            // Apply it now — including clearing schedule rules when it is switched off.
            ScheduleCheckWorker.runOnce(context)
        }

        // ── Blocklist on/off states ──────────────────────────────────────────
        // Disabling must also drop that source's rules: getBlocklistDomains() filters on
        // `source LIKE 'BLOCKLIST%'` and never reads blocklist_sources.enabled, so clearing
        // the flag alone would leave every domain still blocked.
        val previousBlocklists = mutableMapOf<String, Boolean>()
        val previousIntervals  = mutableMapOf<String, Int>()
        root.optJSONArray("blocklists")?.let { arr ->
            val dao = ruleEngine.db.blocklistSourceDao()
            for (i in 0 until arr.length()) {
                val o   = arr.optJSONObject(i) ?: continue
                val key = o.optString("key")
                if (key.isEmpty()) continue
                val on = o.optBoolean("enabled")
                // A list the user added is missing on a new phone: recreate it from its link,
                // within the usual limit. Undo switches it back off.
                var created = false
                if (CustomBlocklist.isCustom(key) && dao.getByKey(key) == null) {
                    val link   = CustomBlocklist.normalizeLink(o.optString("url")) ?: continue
                    val custom = dao.getAllFlow().first().filter { CustomBlocklist.isCustom(it.sourceKey) }
                    if (custom.size >= CustomBlocklist.MAX_LISTS ||
                        custom.any { it.url.equals(link, ignoreCase = true) }) continue
                    dao.upsert(CustomBlocklist.newSource(key, o.optString("name"), link))
                    previousBlocklists[key] = false
                    created = true
                }
                // Only lists the Blocklists screen lets the user switch; locked lists stay as they are.
                val source = dao.getByKey(key)
                if (source == null || !RestoreRules.isSwitchableList(source.isDefault)) { invalid++; continue }
                if (key !in previousBlocklists) {
                    dao.getByKey(key)?.let { if (it.enabled != on) previousBlocklists[key] = it.enabled }
                }
                dao.setEnabled(key, on)
                // Older backup files have no interval; the list keeps its current one.
                if (o.has("refreshHours")) {
                    val before = BlocklistSchedule.intervalHours(context, key)
                    val after  = BlocklistSchedule.sanitize(o.optInt("refreshHours"))
                    if (before != after) previousIntervals[key] = before
                    BlocklistSchedule.setIntervalHours(context, key, after)
                }
                if (!on) {
                    ruleEngine.db.ruleDao().deleteBlocklistBySource(key)
                    dao.updateStats(key, 0, 0L)
                } else if (created) {
                    BlocklistSyncWorker.runNow(context, key)
                }
            }
            ruleEngine.reloadBlocklist()
        }

        // ── Activity history ─────────────────────────────────────────────────
        // Merged, not replaced: existing entries are left alone. Restored rows do count
        // toward Dashboard statistics, because those are computed from this table.
        var logsRestored = 0
        val nowMs = System.currentTimeMillis()
        root.optJSONArray("logs")?.let { arr ->
            for (i in 0 until minOf(arr.length(), LOG_BACKUP_CAP)) {
                val o = arr.optJSONObject(i) ?: continue
                val dest = o.optString("dest")
                if (dest.isBlank()) continue
                val status = o.optString("status")
                val ts     = o.optLong("ts")
                if (!RestoreRules.isValidLog(status, ts, nowMs)) { invalid++; continue }
                logDao.insert(
                    PacketLogEntity(
                        appName     = o.optString("app"),
                        packageName = o.optString("pkg"),
                        destination = dest,
                        status      = status,
                        timestampMs = ts,
                        threatLabel = o.optString("threat")
                    )
                )
                logsRestored++
            }
        }

        ruleEngine.reloadCache()
        withContext(Dispatchers.Main) {
            if (newDomains.isNotEmpty()) blockedDomains.addAll(0, newDomains)
        }
        notifyVpnRulesChanged()
        // App policy changes are baked into establish(), so they need a tunnel rebuild.
        if (appsRestored > 0) scheduleVpnRestartIfNeeded()
        saveRestoreUndo(RestoreUndo(newDomains.toList(), previousApps, previousSchedule, previousBlocklists,
            previousIntervals))
        ChangeHistory.record(context, ChangeAction.BACKUP_RESTORED, newDomains.size.toString())
        loadApps()
        loadSchedule()
        loadAllowedDomains()
        consumeRateLimitUse()

        withContext(Dispatchers.Main) {
            importMessage.value = buildString {
                append("Restored ${newDomains.size} domain${if (newDomains.size == 1) "" else "s"}")
                append(", $appsRestored app rule${if (appsRestored == 1) "" else "s"}")
                append(", $logsRestored log entr${if (logsRestored == 1) "y" else "ies"}")
                if (appsMissing > 0) append(" — $appsMissing app${if (appsMissing == 1) "" else "s"} not installed")
                if (invalid > 0) append(" — $invalid invalid item${if (invalid == 1) "" else "s"} skipped")
            }
        }
    }

    /** Keeps the newest restore undoable. A restore that changed nothing clears it. */
    private suspend fun saveRestoreUndo(undo: RestoreUndo) {
        val saved = try {
            if (undo.isEmpty) { RestoreUndo.clear(context); false }
            else { RestoreUndo.save(context, undo); true }
        } catch (e: Exception) {
            android.util.Log.w("AppControlVM", "saveRestoreUndo failed: ${e.message}")
            false
        }
        withContext(Dispatchers.Main) { canUndoRestore.value = saved }
    }

    /**
     * Puts back what the last restore changed. Uses the same paths as the normal
     * controls: removeBlockRule, updatePolicy, the blocklist on/off steps and
     * updateSchedule(). Does not use up a daily import/export.
     */
    fun undoLastRestore() {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val undo = RestoreUndo.load(context)
                if (undo == null) {
                    RestoreUndo.clear(context)
                    withContext(Dispatchers.Main) {
                        canUndoRestore.value = false
                        importMessage.value  = context.getString(R.string.restore_undo_failed)
                    }
                    return@launch
                }
                undo.addedDomains.forEach { ruleEngine.removeBlockRule(it) }
                undo.previousApps.forEach {
                    ruleEngine.db.ruleDao().updatePolicy(SelfProtection.sanitize(context, it))
                }
                val sourceDao = ruleEngine.db.blocklistSourceDao()
                undo.previousBlocklists.forEach { (key, wasOn) ->
                    sourceDao.setEnabled(key, wasOn)
                    if (wasOn) {
                        BlocklistSyncWorker.runNow(context, key)
                    } else {
                        ruleEngine.db.ruleDao().deleteBlocklistBySource(key)
                        sourceDao.updateStats(key, 0, 0L)
                    }
                }
                undo.previousIntervals.forEach { (key, hours) ->
                    BlocklistSchedule.setIntervalHours(context, key, hours)
                }
                if (undo.previousBlocklists.isNotEmpty()) ruleEngine.reloadBlocklist()
                ruleEngine.reloadCache()
                RestoreUndo.clear(context)
                withContext(Dispatchers.Main) {
                    blockedDomains.removeAll(undo.addedDomains.toSet())
                    canUndoRestore.value = false
                    importMessage.value = context.getString(R.string.restore_undone)
                    // updateSchedule() applies or removes schedule rules and notifies the VPN.
                    undo.previousSchedule?.let { updateSchedule(it) }
                }
                notifyVpnRulesChanged()
                // App policies are baked into the tunnel, so they need a rebuild.
                if (undo.previousApps.isNotEmpty()) scheduleVpnRestartIfNeeded()
                loadApps()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "undoLastRestore failed: ${e.message}")
                withContext(Dispatchers.Main) { importMessage.value = context.getString(R.string.restore_undo_failed) }
            }
        }
    }

    fun clearExportMessage() { exportMessage.value = null }
    fun clearImportMessage() { importMessage.value = null }

    // Reads saved usage from DataStore and refreshes rateLimitState on screen entry
    private fun loadRateLimit() {
        viewModelScope.launch {
            val now     = System.currentTimeMillis()
            val count   = prefsRepo.importExportCount.first()
            val resetAt = prefsRepo.importExportResetAt.first()
            val expired = resetAt == 0L || (now - resetAt) >= 24 * 3_600_000L
            rateLimitState.value = if (expired) {
                RateLimitState(usesRemaining = 2)
            } else {
                val remaining = (2 - count).coerceAtLeast(0)
                RateLimitState(
                    usesRemaining = remaining,
                    resetAtMs     = if (remaining == 0) resetAt + 24 * 3_600_000L else 0L
                )
            }
        }
    }

    // Check only — does NOT increment the counter.
    // Returns true when the user is blocked, and updates rateLimitState with the reset time.
    private suspend fun isRateLimited(): Boolean {
        val now     = System.currentTimeMillis()
        val count   = prefsRepo.importExportCount.first()
        val resetAt = prefsRepo.importExportResetAt.first()
        val expired = resetAt == 0L || (now - resetAt) >= 24 * 3_600_000L
        return if (!expired && count >= 2) {
            withContext(Dispatchers.Main) {
                rateLimitState.value = RateLimitState(
                    usesRemaining = 0,
                    resetAtMs     = resetAt + 24 * 3_600_000L
                )
            }
            true
        } else false
    }

    // Consume one use — call ONLY after a successful export or import.
    // This way a failed attempt (empty list, file error, parse error) never costs the user a use.
    private suspend fun consumeRateLimitUse() {
        val now     = System.currentTimeMillis()
        val count   = prefsRepo.importExportCount.first()
        val resetAt = prefsRepo.importExportResetAt.first()
        val expired = resetAt == 0L || (now - resetAt) >= 24 * 3_600_000L
        val newResetAt = if (expired) now else resetAt
        val newCount   = if (expired) 1 else (count + 1).coerceAtMost(2)
        val remaining  = 2 - newCount
        prefsRepo.setImportExportUsage(newCount, newResetAt)
        withContext(Dispatchers.Main) {
            rateLimitState.value = RateLimitState(
                usesRemaining = remaining,
                resetAtMs     = if (remaining == 0) newResetAt + 24 * 3_600_000L else 0L
            )
        }
    }

    private fun formatTimeUntil(targetMs: Long): String {
        val remaining = targetMs - System.currentTimeMillis()
        if (remaining <= 0) return "soon"
        val hours = remaining / 3_600_000
        val mins  = (remaining % 3_600_000) / 60_000
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    // -------------------------------------------------------------------------
    // Scheduled Blocking — Feature #16
    // Free tier: 1 schedule, all categories, any time range.
    // Worker runs every 15 min; applies/removes SCHEDULE rules without VPN restart.
    // -------------------------------------------------------------------------

    fun loadSchedule() {
        viewModelScope.launch {
            val enabled   = prefsRepo.scheduleEnabled.first()
            val category  = prefsRepo.scheduleCategory.first()
            val startHour = prefsRepo.scheduleStartHour.first()
            val startMin  = prefsRepo.scheduleStartMin.first()
            val endHour   = prefsRepo.scheduleEndHour.first()
            val endMin    = prefsRepo.scheduleEndMin.first()
            val days      = prefsRepo.scheduleDays.first()
            scheduleState.value = ScheduleState(
                enabled   = enabled,
                category  = category,
                startHour = startHour,
                startMin  = startMin,
                endHour   = endHour,
                endMin    = endMin,
                days      = days,
                activeNow = computeScheduleActive(enabled, startHour, startMin, endHour, endMin, days)
            )
        }
    }

    // The + / - buttons send a burst of updates. The state from BEFORE the burst is
    // kept here so the history shows one entry with the real old value, not one per tap.
    private var scheduleBeforeEdit: ScheduleState? = null

    /** Stable text for a per-app rule, turned into words by the history screen. */
    private fun appPolicyValue(wifi: Boolean, data: Boolean): String =
        "wifi=" + (if (wifi) ChangeValue.BLOCKED else ChangeValue.ALLOWED) +
        ",data=" + (if (data) ChangeValue.BLOCKED else ChangeValue.ALLOWED)

    /** Off, or the real time window the user set. */
    private fun scheduleValue(s: ScheduleState): String =
        if (!s.enabled) ChangeValue.OFF
        else String.format(
            java.util.Locale.US, "%02d:%02d-%02d:%02d",
            s.startHour, s.startMin, s.endHour, s.endMin
        )

    private fun recordScheduleChange(new: ScheduleState) {
        val old = scheduleBeforeEdit ?: return
        scheduleBeforeEdit = null
        val before = scheduleValue(old)
        val after  = scheduleValue(new)
        if (before == after) return
        ChangeHistory.record(context, ChangeAction.SCHEDULE, new.category, before, after)
    }

    fun updateSchedule(new: ScheduleState) {
        if (AppLockSession.blockChange(context)) return
        clearDomainTest()
        if (scheduleBeforeEdit == null) scheduleBeforeEdit = scheduleState.value
        scheduleState.value = new
        scheduleUpdateJob?.cancel()
        scheduleUpdateJob = viewModelScope.launch(Dispatchers.IO) {
            // Saved straight away, so a change is never lost if the screen closes.
            prefsRepo.setSchedule(
                enabled   = new.enabled,
                category  = new.category,
                startHour = new.startHour,
                startMin  = new.startMin,
                endHour   = new.endHour,
                endMin    = new.endMin,
                days      = new.days
            )
            if (new.enabled) {
                ScheduleCheckWorker.start(context)
                ScheduleCheckWorker.scheduleNextExactCheck(context, new.startHour, new.startMin, new.endHour, new.endMin)
                // Stepping minutes with + / - sends a burst of changes. Applying rules and
                // reloading the firewall waits until the taps pause, so it happens once.
                // If the screen closes first, the schedule check applies the saved schedule.
                delay(400)
                // Apply immediately without waiting for the 15-min tick
                val active = computeScheduleActive(new.enabled, new.startHour, new.startMin, new.endHour, new.endMin, new.days)
                if (active) {
                    ScheduleCategories.domainsFor(new.category).forEach { ruleEngine.addScheduleRule(it) }
                } else {
                    ruleEngine.removeAllScheduleRules()
                }
                withContext(Dispatchers.Main) { scheduleState.value = new.copy(activeNow = active) }
                recordScheduleChange(new)
                notifyVpnRulesChanged()
            } else {
                // Switching off is a single tap: clear straight away, never delayed.
                ScheduleCheckWorker.stop(context)
                ruleEngine.removeAllScheduleRules()
                withContext(Dispatchers.Main) { scheduleState.value = new.copy(activeNow = false) }
                recordScheduleChange(new)
                notifyVpnRulesChanged()
            }
        }
    }

    private fun computeScheduleActive(
        enabled: Boolean,
        startHour: Int, startMin: Int,
        endHour: Int,   endMin: Int,
        days: String
    ): Boolean {
        if (!enabled) return false
        val cal      = Calendar.getInstance()
        val dayIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (dayIndex >= days.length || days[dayIndex] != '1') return false
        val nowMins   = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMins = startHour * 60 + startMin
        val endMins   = endHour   * 60 + endMin
        return when {
            startMins == endMins -> false
            startMins < endMins  -> nowMins in startMins until endMins
            else                 -> nowMins >= startMins || nowMins < endMins
        }
    }

    // -------------------------------------------------------------------------
    // Beaconing detection — regular-interval pings are a malware persistence pattern
    // Uses coefficient of variation (stdDev/mean) on inter-query gaps:
    // CV < 0.25 with mean 10–1800 s and ≥ 6 samples = likely beacon
    // -------------------------------------------------------------------------

    private fun detectBeaconing() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val since2h = System.currentTimeMillis() - 2L * 3_600_000
                val entries = logDao.getRecent(500).filter { it.timestampMs >= since2h }

                val newBeacons = mutableMapOf<String, Int>()

                entries.groupBy { it.packageName }.forEach { (pkg, logs) ->
                    if (logs.size < 6) return@forEach
                    val ts = logs.map { it.timestampMs }.sorted()
                    val gaps = ts.zipWithNext { a, b -> (b - a) / 1000.0 }
                        .filter { it in 5.0..1800.0 }
                    if (gaps.size < 5) return@forEach

                    val mean   = gaps.average()
                    val stdDev = kotlin.math.sqrt(gaps.map { (it - mean) * (it - mean) }.average())
                    val cv     = if (mean > 0) stdDev / mean else 1.0

                    if (cv < 0.25 && mean in 10.0..1800.0) {
                        newBeacons[pkg] = mean.toInt()
                    }
                }

                appBeaconMap.clear()
                appBeaconMap.putAll(newBeacons)
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "detectBeaconing failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Update behavior change detection — compares DNS rate before vs after last
    // app update. Flags apps where post-update hourly rate ≥ 2× pre-update rate.
    // Uses PackageManager.lastUpdateTime as the split point; requires ≥2h of
    // post-update data and a non-zero pre-update baseline.
    // -------------------------------------------------------------------------

    private fun detectUpdateBehaviorChange() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now     = System.currentTimeMillis()
                val since7d = now - 7L * 24 * 3_600_000
                val newChanges = mutableMapOf<String, Float>()

                appList.forEach { app ->
                    val pkgInfo = runCatching {
                        context.packageManager.getPackageInfo(app.packageName, 0)
                    }.getOrNull() ?: return@forEach

                    val updateTime  = pkgInfo.lastUpdateTime
                    val installTime = pkgInfo.firstInstallTime

                    // Skip: never updated, too old to be relevant, or not enough post-update time
                    if (updateTime == installTime) return@forEach
                    if (updateTime < since7d) return@forEach
                    if ((now - updateTime) < 2L * 3_600_000) return@forEach

                    val preCount  = logDao.countByPackageInRange(app.packageName, updateTime - 7L * 24 * 3_600_000, updateTime)
                    val postCount = logDao.countByPackageInRange(app.packageName, updateTime, now)

                    val preHourly  = preCount / (7 * 24f)
                    val postHourly = postCount / ((now - updateTime) / 3_600_000f)

                    if (preHourly > 0f && postHourly >= 5f && postHourly > preHourly * 2f) {
                        newChanges[app.packageName] = postHourly / preHourly
                    }
                }

                appUpdateChangeMap.clear()
                appUpdateChangeMap.putAll(newChanges)
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "detectUpdateBehaviorChange failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Smart suggestions — top allowed domains from last 24 h that look suspicious
    // -------------------------------------------------------------------------

    fun loadSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val since10min = System.currentTimeMillis() - 10L * 60_000
                val since24h   = System.currentTimeMillis() - 24L * 3_600_000
                val alreadyBlocked = blockedDomains.toSet()

                // Prefer recent 10-min window for real-time feel; fall back to 24h if sparse
                var topAllowed = logDao.topDestinations("ALLOWED", since10min, 20)
                if (topAllowed.size < 3) {
                    topAllowed = logDao.topDestinations("ALLOWED", since24h, 20)
                }

                val suggestions = topAllowed
                    .map { it.name }
                    .filter { d ->
                        d.isNotBlank() &&
                        d != "No domain info" &&
                        d.contains('.') &&
                        d !in alreadyBlocked &&
                        !isCoreServiceDomain(d)
                    }
                    .take(10)
                suggestedBlocks.clear()
                suggestedBlocks.addAll(suggestions)
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "loadSuggestions failed: ${e.message}")
            }
        }
    }

    fun blockDomainFromSuggestion(domain: String) {
        if (AppLockSession.blockChange(context)) return
        suggestedBlocks.remove(domain)
        blockDomain(domain)
    }

    fun dismissSuggestion(domain: String) {
        suggestedBlocks.remove(domain)
    }

    private fun isCoreServiceDomain(domain: String): Boolean {
        val d = domain.lowercase()
        return listOf(
            "google", "gstatic", "googleapis", "googlevideo", "gvt1",
            "android.com", "apple.com", "icloud.com", "microsoft.com",
            "windows.com", "amazonaws.com", "cloudfront.net",
            "connectivitycheck", "play.google"
        ).any { d.contains(it) }
    }

    // -------------------------------------------------------------------------
    // Risk scores — blocked DNS count per app over the last 7 days
    // Reads existing packet_logs; no new data is collected
    // -------------------------------------------------------------------------

    fun loadRiskScores() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                val rows = logDao.blockedCountPerApp(sevenDaysAgo)
                appRiskMap.clear()
                appRiskMap.putAll(rows.associate { it.name to it.count })
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "loadRiskScores failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Query rate — DNS requests per app in the last 60 seconds
    // Refreshed every 30 s by the init loop; also called on screen entry
    // -------------------------------------------------------------------------

    fun loadQueryRates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val since60s = System.currentTimeMillis() - 60_000L
                val since7d  = System.currentTimeMillis() - 7L * 24 * 3_600_000

                val currentRows  = logDao.queriesPerApp(since60s)
                val historicalRows = logDao.queriesPerApp(since7d)
                val historicalMap  = historicalRows.associate { it.name to it.count }

                appQueryRateMap.clear()
                appQueryRateMap.putAll(currentRows.associate { it.name to it.count })

                // Anomaly: current extrapolated hourly rate vs 7-day avg hourly rate
                val newAnomalies = mutableMapOf<String, Float>()
                currentRows.forEach { row ->
                    val currentPerHour   = row.count * 60f
                    val sevenDayAvgPerHr = (historicalMap[row.name] ?: 0).toFloat() / (7 * 24)
                    if (sevenDayAvgPerHr > 0f &&
                        currentPerHour >= 10f &&
                        currentPerHour > sevenDayAvgPerHr * 3f) {
                        newAnomalies[row.name] = currentPerHour / sevenDayAvgPerHr
                    }
                }
                appAnomalyMap.clear()
                appAnomalyMap.putAll(newAnomalies)
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "loadQueryRates failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // App list — PackageManager scan runs on IO thread to avoid main-thread jank
    // -------------------------------------------------------------------------

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val installed = packageManager.getInstalledApplications(0)
                val isSystemApp  = { app: ApplicationInfo -> (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
                val canBeListed  = installed.filter { app ->
                    isSystemApp(app) || packageManager.getLaunchIntentForPackage(app.packageName) != null
                }
                visibleAppUids = canBeListed.map { it.uid }
                val sharedGroups = sharedUidGroups(
                    canBeListed.groupBy { it.uid }.filterValues { it.size > 1 }.values.flatten()
                        .map { Triple(it.uid, it.packageName, packageManager.getApplicationLabel(it).toString()) }
                )
                refreshBlockedAppCount()
                val filtered = canBeListed.filter { app ->
                    // Mutually exclusive lists: the toggle switches between
                    // "apps you installed" and "system apps", never both at once.
                    if (showSystemApps.value) isSystemApp(app) else !isSystemApp(app)
                }.map { app ->
                    AppRule(
                        name        = packageManager.getApplicationLabel(app).toString(),
                        packageName = app.packageName,
                        uid         = app.uid
                    )
                }.distinctBy { it.packageName }
                 .sortedBy { it.name }

                // The saved rules are read and the list replaced as one step that a switch tap
                // cannot interleave with; otherwise a rule saved in between was overwritten on
                // screen by the older value this load had read (seen on the emulator).
                policyRowsLock.withLock {
                    val policies = ruleEngine.db.ruleDao().getAllPolicies().associateBy { it.uid }
                    val rows = filtered.map { row ->
                        val policy = policies[row.uid]
                        row.copy(
                            isWifiBlocked = policy?.wifiBlocked ?: false,
                            isDataBlocked = policy?.cellBlocked ?: false
                        )
                    }
                    // Changed on the main thread, where the screen reads it, in one step.
                    withContext(Dispatchers.Main) {
                        appList.clear()
                        appList.addAll(rows)
                        sharedUidApps.value = sharedGroups
                    }
                }
                loadRiskScores()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "loadApps failed: ${e.message}")
            }
        }
    }

    // Reads the saved rules, so the number matches what the firewall enforces.
    private suspend fun refreshBlockedAppCount() {
        val count = countBlockedApps(
            visibleAppUids, ruleEngine.db.ruleDao().getAllPolicies(),
            context.applicationInfo.uid, context.packageName
        )
        withContext(Dispatchers.Main) { blockedAppCount.value = count }
    }

    fun toggleSystemApps(show: Boolean) {
        showSystemApps.value = show
        loadApps()
    }

    // -------------------------------------------------------------------------
    // App policy update — changing which apps are blocked requires a full tunnel
    // rebuild because addDisallowedApplication() is baked into establish().
    // scheduleVpnRestartIfNeeded() sends ACTION_RESTART (with 300ms debounce)
    // only when the VPN is currently running; no-op otherwise.
    // -------------------------------------------------------------------------

    fun updatePolicy(app: AppRule, wifi: Boolean, data: Boolean) {
        if (AppLockSession.blockChange(context)) return
        // The firewall never blocks itself; its card offers no switches, and this keeps any
        // other path from saving one.
        if (SelfProtection.isOwnPackage(context, app.packageName)) return
        clearDomainTest()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                policyRowsLock.withLock {
                    ruleEngine.db.ruleDao().updatePolicy(
                        AppPolicy(app.uid, app.packageName, wifi, data)
                    )
                    // Every app sharing this UID gets the rule; each row keeps its own identity.
                    withContext(Dispatchers.Main) { applyPolicyToRows(appList, app.uid, wifi, data) }
                }
                ruleEngine.reloadCache()

                if (app.isWifiBlocked != wifi || app.isDataBlocked != data) {
                    ChangeHistory.record(
                        context, ChangeAction.APP_POLICY, app.name,
                        before = appPolicyValue(app.isWifiBlocked, app.isDataBlocked),
                        after  = appPolicyValue(wifi, data)
                    )
                }

                refreshBlockedAppCount()

                scheduleVpnRestartIfNeeded()
            } catch (e: Exception) {
                android.util.Log.e("AppControlVM", "updatePolicy failed: ${e.message}")
            }
        }
    }

    // The trash button resets the app to fully allowed, which is what the card shows
    // afterwards. It used to change only the screen: the saved rule and the tunnel kept
    // blocking the app until the app was reopened (seen on the emulator).
    fun purgeApp(app: AppRule) = updatePolicy(app, wifi = false, data = false)

    // -------------------------------------------------------------------------
    // Cross-process rule sync — tells the VPN service (separate :vpn process) to
    // reload its in-memory rule caches from the DB after a domain/schedule change.
    // Guard: startForegroundService requires startForeground() within 5 s. The
    // ACTION_RELOAD_RULES branch in SentinelVpnService never calls startForeground(),
    // so starting it while stopped crashes with ForegroundServiceDidNotStartInTimeException.
    // When VPN is off the reload is unnecessary — rules are read from DB on next start.
    // -------------------------------------------------------------------------

    private fun notifyVpnRulesChanged() {
        if (!context.vpnRunning()) return
        try {
            val intent = Intent(context, SentinelVpnService::class.java).apply {
                action = SentinelVpnService.ACTION_RELOAD_RULES
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            // VPN stopped between check and notify — safe to ignore
        }
    }

    // Sends ACTION_RESTART so buildTunnel() re-runs with the updated blocked-app set.
    // Debounced 300 ms to coalesce rapid block/unblock toggles into a single rebuild.
    // No-op when VPN is not running — VPN will pick up the new rules from DB on next start.
    companion object {
        private const val MAX_ALLOWED      = 500
        private const val BACKUP_FORMAT    = "tacuns-firewall-backup"
        private const val BACKUP_VERSION   = 1
        private const val IMPORT_CAP       = 5_000
        private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024   // 2 MB; a real backup is a few KB
        // The activity table is no longer capped at 500 rows (the user picks a retention
        // window now), so this is an independent cap that keeps the backup's log section
        // to a fixed upper bound (~60-100 KB of JSON) however long their history is.
        private const val LOG_BACKUP_CAP   = 500
    }

    private fun scheduleVpnRestartIfNeeded() {
        if (!context.vpnRunning()) return
        restartDebounceJob?.cancel()
        restartDebounceJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            try {
                val intent = Intent(context, SentinelVpnService::class.java).apply {
                    action = SentinelVpnService.ACTION_RESTART
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // VPN stopped between check and restart — safe to ignore
            }
        }
    }

}
