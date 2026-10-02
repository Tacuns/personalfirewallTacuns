package com.sentinel.core.rules

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FirewallRule)

    @Query("SELECT * FROM app_policies WHERE uid = :uid")
    suspend fun getPolicy(uid: Int): AppPolicy?

    @Query("SELECT * FROM app_policies")
    suspend fun getAllPolicies(): List<AppPolicy>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePolicy(policy: AppPolicy)

    @Query("SELECT * FROM firewall_rules WHERE domain = :domain")
    suspend fun getRuleForDomain(domain: String): FirewallRule?

    /** Read-only, for the Test-a-domain check: a domain and all its parents in one query. */
    @Query("SELECT * FROM firewall_rules WHERE domain IN (:domains) AND isBlocked = 1")
    suspend fun findBlockedRules(domains: List<String>): List<FirewallRule>

    // ── User domain rules ────────────────────────────────────────────────────

    @Query("SELECT * FROM firewall_rules WHERE source = 'USER' AND isBlocked = 1")
    suspend fun getUserBlockedDomains(): List<FirewallRule>

    /**
     * What the firewall enforces from this table: the user's own rules plus any schedule
     * rules the schedule check has switched on. Blocklists are loaded separately.
     * The UI list, export and statistics keep using getUserBlockedDomains().
     */
    @Query("SELECT * FROM firewall_rules WHERE source IN ('USER', 'SCHEDULE') AND isBlocked = 1")
    suspend fun getEnforcedDomainRules(): List<FirewallRule>

    /**
     * Only the rules the schedule switched on. Used to label a block in the activity log
     * as "scheduled" instead of claiming the user blocked the site by hand. A domain the
     * user already blocked keeps source 'USER' (the schedule insert IGNOREs it), so the
     * two sets never overlap.
     */
    @Query("SELECT * FROM firewall_rules WHERE source = 'SCHEDULE' AND isBlocked = 1")
    suspend fun getScheduleBlockedDomains(): List<FirewallRule>

    @Query("DELETE FROM firewall_rules WHERE domain = :domain AND source = 'USER'")
    suspend fun deleteUserRule(domain: String)

    // ── Always allow list — source 'ALLOW', isBlocked = 0 ────────────────────

    @Query("SELECT * FROM firewall_rules WHERE source = 'ALLOW'")
    suspend fun getAllowRules(): List<FirewallRule>

    @Query("DELETE FROM firewall_rules WHERE domain = :domain AND source = 'ALLOW'")
    suspend fun deleteAllowRule(domain: String)

    /** Read-only, for the Test-a-domain check: every rule (blocking or allowing) for these names. */
    @Query("SELECT * FROM firewall_rules WHERE domain IN (:domains)")
    suspend fun findRulesFor(domains: List<String>): List<FirewallRule>

    // ── Community blocklist (all sources matching BLOCKLIST%) ─────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlocklistBatch(rules: List<FirewallRule>)

    @Query("DELETE FROM firewall_rules WHERE source = :source")
    suspend fun deleteBlocklistBySource(source: String)

    @Query("SELECT * FROM firewall_rules WHERE source LIKE 'BLOCKLIST%' AND isBlocked = 1")
    suspend fun getBlocklistDomains(): List<FirewallRule>

    @Query("SELECT COUNT(*) FROM firewall_rules WHERE source LIKE 'BLOCKLIST%'")
    suspend fun getBlocklistCount(): Int

    // ── Schedule rules — IGNORE so USER rules are never overwritten ───────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScheduleRule(rule: FirewallRule)

    @Query("DELETE FROM firewall_rules WHERE source = 'SCHEDULE'")
    suspend fun deleteAllScheduleRules()
}

@Dao
interface BlocklistSourceDao {
    // Live reactive query — UI updates automatically when worker writes to DB
    @Query("SELECT * FROM blocklist_sources ORDER BY isDefault DESC, displayName ASC")
    fun getAllFlow(): Flow<List<BlocklistSource>>

    @Query("SELECT * FROM blocklist_sources WHERE enabled = 1")
    suspend fun getEnabled(): List<BlocklistSource>

    @Query("SELECT * FROM blocklist_sources WHERE sourceKey = :key LIMIT 1")
    suspend fun getByKey(key: String): BlocklistSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: BlocklistSource)

    @Query("UPDATE blocklist_sources SET enabled = :enabled WHERE sourceKey = :sourceKey")
    suspend fun setEnabled(sourceKey: String, enabled: Boolean)

    @Query("UPDATE blocklist_sources SET domainCount = :count, lastUpdatedMs = :updatedMs WHERE sourceKey = :sourceKey")
    suspend fun updateStats(sourceKey: String, count: Int, updatedMs: Long)

    /** Removes a list the user added. Its rules must be deleted in the same transaction. */
    @Query("DELETE FROM blocklist_sources WHERE sourceKey = :sourceKey")
    suspend fun deleteByKey(sourceKey: String)

    /** Changes nothing, but tells open screens to re-read the row (for example after a failed download). */
    @Query("UPDATE blocklist_sources SET lastUpdatedMs = lastUpdatedMs WHERE sourceKey = :sourceKey")
    suspend fun touch(sourceKey: String)
}

@Database(
    entities = [FirewallRule::class, AppPolicy::class, BlocklistSource::class],
    version = 4
)
abstract class RuleDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun blocklistSourceDao(): BlocklistSourceDao

    companion object {
        val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedBlocklistSources(db)
            }

            // Room calls this instead of onCreate() when doing a destructive migration.
            // Without this override, upgrading devices never get the seed rows.
            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                seedBlocklistSources(db)
            }

            private fun seedBlocklistSources(db: SupportSQLiteDatabase) {
                val sql = "INSERT OR IGNORE INTO blocklist_sources " +
                    "(sourceKey, displayName, url, format, description, isDefault, enabled, domainCount, lastUpdatedMs) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)"
                listOf(
                    arrayOf("BLOCKLIST_STEVENBLACK", "StevenBlack Hosts",
                        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                        "hosts", "Unified ads + malware hosts list. MIT license.", 1, 1),
                    arrayOf("BLOCKLIST_OISD", "OISD Big",
                        "https://big.oisd.nl/",
                        "abp", "403k+ domains. Hourly Cloudflare CDN updates.", 0, 0),
                    arrayOf("BLOCKLIST_HAGEZI", "HaGeZi Pro",
                        "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/pro.txt",
                        "domains", "Ads, tracking, telemetry and phishing. Daily updates.", 0, 0),
                    arrayOf("BLOCKLIST_ADGUARD", "AdGuard DNS Filter",
                        "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
                        "abp", "Security-focused. Malware and phishing protection.", 0, 0),
                    arrayOf("BLOCKLIST_POLLOCK", "Dan Pollock Hosts",
                        "https://someonewhocares.org/hosts/zero/hosts",
                        "hosts", "Curated spyware and tracker list.", 0, 0)
                ).forEach { args -> db.execSQL(sql, args) }
            }
        }
    }
}
