package com.sentinel.core.rules

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_4 = object : Migration(2, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop table removed in v4
        db.execSQL("DROP TABLE IF EXISTS `domain_reputations`")

        // New table added in v4
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `blocklist_sources` (
                `sourceKey` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `url` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `isDefault` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                `domainCount` INTEGER NOT NULL,
                `lastUpdatedMs` INTEGER NOT NULL,
                PRIMARY KEY(`sourceKey`)
            )""".trimIndent())

        // Index added on firewall_rules.source in v4 schema
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_firewall_rules_source` ON `firewall_rules` (`source`)")

        // Seed default blocklist sources — SEED_CALLBACK.onCreate/onDestructiveMigration
        // do not fire for explicit migrations, so we seed here.
        val sql = "INSERT OR IGNORE INTO blocklist_sources " +
            "(sourceKey, displayName, url, format, description, isDefault, enabled, domainCount, lastUpdatedMs) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)"
        listOf(
            arrayOf<Any>("BLOCKLIST_STEVENBLACK", "StevenBlack Hosts",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                "hosts", "Unified ads + malware hosts list. MIT license.", 1, 1),
            arrayOf<Any>("BLOCKLIST_OISD", "OISD Big",
                "https://big.oisd.nl/",
                "abp", "403k+ domains. Hourly Cloudflare CDN updates.", 0, 0),
            arrayOf<Any>("BLOCKLIST_HAGEZI", "HaGeZi Pro",
                "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/pro.txt",
                "domains", "Ads, tracking, telemetry and phishing. Daily updates.", 0, 0),
            arrayOf<Any>("BLOCKLIST_ADGUARD", "AdGuard DNS Filter",
                "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
                "abp", "Security-focused. Malware and phishing protection.", 0, 0),
            arrayOf<Any>("BLOCKLIST_POLLOCK", "Dan Pollock Hosts",
                "https://someonewhocares.org/hosts/zero/hosts",
                "hosts", "Curated spyware and tracker list.", 0, 0)
        ).forEach { args -> db.execSQL(sql, args) }
    }
}
