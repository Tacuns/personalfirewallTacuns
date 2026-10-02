package com.sentinel.core.rules

import android.content.Context
import com.sentinel.core.logs.LogManager
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * RuleEngine — singleton.
 *
 * There must be exactly ONE instance shared by SentinelVpnService,
 * AppControlViewModel, and DashboardStatsViewModel so that every
 * in-memory cache update is immediately visible to the VPN interceptor
 * without requiring a tunnel restart.
 *
 * Thread-safety — volatile snapshot publication (copy-on-write):
 *   - Each cache is an IMMUTABLE collection held in a @Volatile field. Readers take
 *     one volatile read and then work on a collection that never changes underneath
 *     them, so no lock is taken on the per-packet DNS path.
 *   - Writers build a brand-new collection and publish it with a single assignment.
 *     Kotlin's @Volatile guarantees that write is "always made visible to other
 *     threads", and that a reader "sees not only that value, but all side effects
 *     that led to writing that value" — so the fully-populated collection is safely
 *     published. Without this, the long-lived DnsInterceptor thread had no
 *     happens-before edge with the writer and could keep serving a stale view until
 *     the tunnel was rebuilt (a rebuild creates a new thread, which is why a VPN
 *     restart always appeared to "fix" rule updates).
 *   - Writes still synchronize on `this`, because volatile alone does not make
 *     read-modify-write atomic. Writes are rare (UI actions and workers), so the
 *     lock costs nothing on the hot path.
 *   - INVARIANT: a published collection is never mutated afterwards. The read-only
 *     Set/Map types make an accidental mutation a compile error.
 */
class RuleEngine private constructor(context: Context) {

    companion object {
        /** Activity-log wording for a block the schedule switched on (see getDomainThreatLabel). */
        const val LABEL_SCHEDULE = "SCHEDULE"

        @Volatile private var INSTANCE: RuleEngine? = null

        fun getInstance(context: Context): RuleEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RuleEngine(context.applicationContext).also { INSTANCE = it }
            }
    }

    val db: RuleDatabase = Room.databaseBuilder(
        context.applicationContext,
        RuleDatabase::class.java,
        "sentinel-rules.db"
    )
    .addMigrations(MIGRATION_2_4)
    .addCallback(RuleDatabase.SEED_CALLBACK)
    // Tells Home when a damaged database file was replaced by an empty one (see RulesLossNotice).
    .addCallback(object : RoomDatabase.Callback() {
        private val ctx = context.applicationContext
        override fun onCreate(db: SupportSQLiteDatabase) = RulesLossNotice.onDatabaseCreated(ctx)
        override fun onOpen(db: SupportSQLiteDatabase) = RulesLossNotice.onDatabaseOpened(ctx)
    })
    .build()

    private val dao = db.ruleDao()

    private val appContext = context.applicationContext

    // The user's Always allow list (source='ALLOW'). Beats a block on the same name.
    @Volatile private var allowCache: Set<String> = emptySet()

    // Watch-only, look-alike blocking and safe search switches, re-read on every reload.
    @Volatile var options: FirewallOptions = FirewallOptions()
        private set

    // User-added block rules plus active schedule rules (small, synced to DB)
    @Volatile private var blockedCache: Set<String> = emptySet()

    // Just the names the schedule switched on — a disjoint subset of blockedCache, used
    // ONLY to label a log row. Blocking decisions never read it, so the block path is
    // unchanged. Kept separate because blockedCache carries no source tag.
    @Volatile private var scheduleCache: Set<String> = emptySet()

    // Per-app network policies (wifi/cell blocked flags)
    @Volatile private var appPolicyCache: Map<Int, AppPolicy> = emptyMap()

    // Community StevenBlack blocklist (~84k domains) — loaded async on VPN start
    @Volatile private var blocklistCache: Set<String> = emptySet()

    // -------------------------------------------------------------------------
    // Cache reload — DB reads happen outside the lock (suspend-safe);
    // the in-memory swap is synchronised so reads never see a partial clear.
    // -------------------------------------------------------------------------

    suspend fun reloadCache() {
        // USER + SCHEDULE. The schedule check runs in the main process, so loading only
        // USER rules here meant schedule rules never reached the :vpn process at all.
        val userBlocked = dao.getEnforcedDomainRules()
        val scheduled   = dao.getScheduleBlockedDomains()
        val policies    = dao.getAllPolicies()
        val allowed     = dao.getAllowRules()
        val opts        = FirewallOptionsStore.read(appContext)
        val newAllowed  = HashSet<String>(allowed.size * 4 / 3 + 1)
        allowed.forEach { newAllowed.add(it.domain) }
        // Built fully BEFORE publication, so a reader never observes a half-filled
        // collection — the old clear()/addAll() left a window where everything was
        // momentarily unblocked.
        val newBlocked  = HashSet<String>(userBlocked.size * 4 / 3 + 1)
        userBlocked.forEach { newBlocked.add(it.domain) }
        val newSchedule = HashSet<String>(scheduled.size * 4 / 3 + 1)
        scheduled.forEach { newSchedule.add(it.domain) }
        val newPolicies = HashMap<Int, AppPolicy>(policies.size * 4 / 3 + 1)
        // The firewall's own app is left out, so it can never be blocked or put the tunnel
        // into full-tunnel mode for itself alone (see SelfProtection).
        val ownUid = appContext.applicationInfo.uid
        val ownPackage = appContext.packageName
        policies.forEach {
            if (!SelfProtection.isOwn(it, ownUid, ownPackage)) newPolicies[it.uid] = it
        }
        synchronized(this) {
            blockedCache   = newBlocked
            scheduleCache  = newSchedule
            appPolicyCache = newPolicies
            allowCache     = newAllowed
            options        = opts
        }
        // Both processes reach this on every reload, so turning logging off in Settings
        // stops the VPN recording straight away instead of at the next restart.
        LogManager.setRetentionHours(opts.logRetentionHours)
        com.sentinel.core.vpn.SafeSearch.includeYouTube = opts.safeSearchYouTube
        com.sentinel.core.vpn.DnsResolverChoice.apply(appContext, opts.dnsSettings)
    }

    suspend fun reloadBlocklist() {
        val entries = dao.getBlocklistDomains()
        // Pre-sized to avoid rehashing an ~84k-entry set while building it.
        val newBlocklist = HashSet<String>(entries.size * 4 / 3 + 1)
        entries.forEach { newBlocklist.add(it.domain) }
        synchronized(this) { blocklistCache = newBlocklist }
    }

    // -------------------------------------------------------------------------
    // App policy queries
    // -------------------------------------------------------------------------

    // Each reader snapshots the volatile field ONCE into a local, then works on an
    // immutable collection — no lock, and no chance of the set changing mid-call.

    fun getBlockedPackages(isWifi: Boolean): List<String> =
        appPolicyCache.values
            .filter { (isWifi && it.wifiBlocked) || (!isWifi && it.cellBlocked) }
            .map { it.packageName }

    fun isAppBlocked(uid: Int, isWifi: Boolean): Boolean =
        appPolicyCache[uid]?.let { if (isWifi) it.wifiBlocked else it.cellBlocked } ?: false

    // -------------------------------------------------------------------------
    // Domain blocking — cache updated immediately; VPN sees change on next packet
    // -------------------------------------------------------------------------

    // Closest rule wins; an Always allow entry beats a block on the same name.
    // With an empty allow list this gives exactly the previous result (DomainCheckTest).
    fun isDomainBlocked(domain: String): Boolean =
        DomainCheck.isBlocked(domain, blockedCache, allowCache, blocklistCache)

    /** True when the Always allow list decides [domain]. */
    fun isDomainAllowed(domain: String): Boolean =
        DomainCheck.isAllowed(domain, blockedCache, allowCache)

    fun getDomainThreatLabel(domain: String): String {
        val user = blockedCache          // one volatile read for the whole parent walk
        val sched = scheduleCache        // ditto — decides USER BLOCK vs SCHEDULE
        if (blocklistCache.contains(domain)) return "AD/TRACKER"
        if (user.contains(domain))          return labelFor(domain, sched)
        var parent = domain
        while (parent.contains('.')) {
            parent = parent.substringAfter('.')
            if (user.contains(parent)) return labelFor(parent, sched)
        }
        return ""
    }

    // The matched rule name decides the wording: a name the schedule switched on is
    // reported as SCHEDULE, everything else stays USER BLOCK so the per-rule hit
    // counters (which look for 'USER BLOCK') keep working exactly as before.
    private fun labelFor(matched: String, sched: Set<String>): String =
        if (sched.contains(matched)) LABEL_SCHEDULE else "USER BLOCK"

    suspend fun addBlockRule(domain: String, uid: Int, packageName: String, source: String = "USER") {
        dao.insertRule(FirewallRule(domain, uid, packageName, true, source))
        // Copy-on-write: build a new set, then publish it. Re-read inside the lock so
        // two concurrent adds cannot lose one another. The insert replaced any allow row
        // for this name (same key), so it leaves the allow list too.
        synchronized(this) {
            blockedCache = blockedCache + domain
            allowCache   = allowCache - domain
            // The row just REPLACEd any schedule row for this name, so the label must
            // follow the new source (a hand-added rule reads USER BLOCK again).
            scheduleCache = if (source == "SCHEDULE") scheduleCache + domain
                            else                      scheduleCache - domain
        }
    }

    /**
     * Puts a name on the Always allow list. The row shares the domain key, so it replaces
     * any user, schedule or blocklist row for that exact name.
     */
    suspend fun addAllowRule(domain: String) {
        // Remember which blocklist listed this name (kept in the unused packageName field),
        // so taking it off the allow list can put the blocklist entry back.
        val previous = dao.getRuleForDomain(domain)
        val listSource = previous?.source?.takeIf { it.startsWith("BLOCKLIST") }
            ?: previous?.packageName?.takeIf { previous.source == DomainCheck.SOURCE_ALLOW && it.startsWith("BLOCKLIST") }
            ?: ""
        dao.insertRule(FirewallRule(domain, -1, listSource, false, DomainCheck.SOURCE_ALLOW))
        synchronized(this) {
            allowCache    = allowCache + domain
            blockedCache  = blockedCache - domain
            scheduleCache = scheduleCache - domain
        }
    }

    suspend fun removeAllowRule(domain: String) {
        val row = dao.getRuleForDomain(domain)?.takeIf { it.source == DomainCheck.SOURCE_ALLOW }
        dao.deleteAllowRule(domain)
        // Without this, a blocklisted site stayed open after leaving the allow list until the
        // list's next download (seen on the emulator). Only restored while that list is on.
        val listSource = row?.packageName?.takeIf { it.startsWith("BLOCKLIST") }
        val restore = listSource != null && db.blocklistSourceDao().getByKey(listSource)?.enabled == true
        if (restore) dao.insertBlocklistBatch(listOf(FirewallRule(domain, -1, "", true, listSource!!)))
        synchronized(this) {
            allowCache = allowCache - domain
            if (restore) blocklistCache = blocklistCache + domain
        }
    }

    /** Removes a user-added block rule from both DB and the live cache. */
    suspend fun removeBlockRule(domain: String) {
        dao.deleteUserRule(domain)
        synchronized(this) {
            blockedCache  = blockedCache - domain
            scheduleCache = scheduleCache - domain
        }
    }

    // -------------------------------------------------------------------------
    // Schedule rules — source='SCHEDULE', IGNORE so USER rules survive
    // -------------------------------------------------------------------------

    suspend fun addScheduleRule(domain: String) {
        dao.insertScheduleRule(FirewallRule(domain, -1, "", true, "SCHEDULE"))
        // The insert IGNOREs an existing USER row, so only tag this name as scheduled
        // when the stored rule really is the schedule's.
        val isSchedule = dao.getRuleForDomain(domain)?.source == "SCHEDULE"
        synchronized(this) {
            blockedCache = blockedCache + domain
            if (isSchedule) scheduleCache = scheduleCache + domain
        }
    }

    // Removes all SCHEDULE-sourced rules and rebuilds blockedCache from USER rules only.
    // Called when the schedule window ends or the schedule is disabled.
    suspend fun removeAllScheduleRules() {
        dao.deleteAllScheduleRules()
        val userBlocked = dao.getUserBlockedDomains()
        val rebuilt = HashSet<String>(userBlocked.size * 4 / 3 + 1)
        userBlocked.forEach { rebuilt.add(it.domain) }
        synchronized(this) {
            blockedCache  = rebuilt
            scheduleCache = emptySet()   // every schedule row was just deleted
        }
    }

    // -------------------------------------------------------------------------
    // Blocklist stats
    // -------------------------------------------------------------------------

    suspend fun getBlocklistCount(): Int = dao.getBlocklistCount()

}
