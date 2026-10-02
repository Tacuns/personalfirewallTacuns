package com.sentinel.core.logs

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One recorded change to a rule or setting, with the value before and after it.
 *
 * Stored in LogDatabase (history), never in RuleDatabase (live rules), so recording
 * history can never disturb the rules the firewall reads.
 *
 * `action`, `beforeValue` and `afterValue` hold STABLE KEYS, never translated text:
 * the screen turns them into words in the language the user is reading right now.
 * Free values (a domain, an app name, a time range) are stored as they are.
 */
@Entity(tableName = "change_history")
data class ChangeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val action: String,
    val target: String,
    val beforeValue: String = "",
    val afterValue: String = ""
)

/** Stable keys for the kind of change. Stored in the database — never rename these. */
object ChangeAction {
    const val DOMAIN_BLOCKED   = "domain_blocked"
    const val DOMAIN_UNBLOCKED = "domain_unblocked"
    const val DOMAIN_RENAMED   = "domain_renamed"
    const val DOMAIN_ALWAYS_ALLOWED = "domain_always_allowed"   // added to the Always allow list
    const val DOMAIN_ALLOW_REMOVED  = "domain_allow_removed"    // taken off the Always allow list
    const val APP_POLICY       = "app_policy"
    const val SCHEDULE         = "schedule"
    const val BLOCKLIST        = "blocklist"
    const val RULES_IMPORTED   = "rules_imported"
    const val BACKUP_RESTORED  = "backup_restored"
    const val DNS_SERVER       = "dns_server"      // target "main" or "backup"
    const val PROTECTION_LEVEL = "protection_level" // values: ProtectionProfile keys or "custom"
}

/** Stable keys for a before/after value that is a state rather than a free value. */
object ChangeValue {
    const val ALLOWED = "allowed"
    const val BLOCKED = "blocked"
    const val ON      = "on"
    const val OFF     = "off"
}
