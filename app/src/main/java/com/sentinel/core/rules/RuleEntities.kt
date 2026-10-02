package com.sentinel.core.rules

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "firewall_rules", indices = [Index(value = ["source"])])
data class FirewallRule(
    @PrimaryKey val domain: String,
    val uid: Int,
    val packageName: String,
    val isBlocked: Boolean = true,
    val source: String = "USER",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_policies")
data class AppPolicy(
    @PrimaryKey val uid: Int,
    val packageName: String,
    val wifiBlocked: Boolean = false,
    val cellBlocked: Boolean = false
)

@Entity(tableName = "blocklist_sources")
data class BlocklistSource(
    @PrimaryKey val sourceKey: String,
    val displayName: String,
    val url: String,
    val format: String,          // "hosts" | "domains" | "abp"
    val description: String,
    val isDefault: Boolean,      // true = free tier (always enabled by default)
    val enabled: Boolean,
    val domainCount: Int,
    val lastUpdatedMs: Long
)
