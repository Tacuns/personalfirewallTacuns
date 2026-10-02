package com.sentinel.core.logs

/**
 * Why a log entry was blocked or allowed, as the Activity details sheet explains it.
 *
 * Built only from what the entry recorded (its status and threat label), read the same way
 * the row's badge reads it, so the sheet never says something the row does not. A label is
 * only a reason for a BLOCKED entry; the one exception is watch-only, which is logged as
 * ALLOWED with "WOULD BLOCK".
 */
enum class LogReason {
    AD_LIST,        // on an ad/tracker blocklist
    USER_RULE,      // the user's own block rule (or one on a parent website)
    SCHEDULE,       // the blocking schedule
    LOOKALIKE,      // name imitates a trusted website
    APP_BLOCKED,    // the whole app is blocked in the Apps tab
    WATCH_ONLY,     // would have been blocked, but watch-only mode only reports
    ALLOWED,        // nothing blocked it
    BLOCKED_OTHER;  // blocked, with a label this version does not know

    companion object {
        fun of(status: String, threatLabel: String): LogReason {
            val label = LogLabels.displayLabel(status, threatLabel)
            if (label == "WOULD BLOCK") return WATCH_ONLY
            if (status != "BLOCKED") return ALLOWED
            return when (label) {
                "AD/TRACKER"          -> AD_LIST
                "USER BLOCK"          -> USER_RULE
                "SCHEDULE"            -> SCHEDULE
                "LOOK-ALIKE"          -> LOOKALIKE
                LogLabels.APP_BLOCKED -> APP_BLOCKED
                else                  -> BLOCKED_OTHER
            }
        }
    }
}

/** Counts for one website across the saved activity (all apps). */
data class DestinationSummary(
    val total: Int,
    val blocked: Int,
    val apps: Int,
    val firstMs: Long?
) {
    val allowed: Int get() = total - blocked
}
