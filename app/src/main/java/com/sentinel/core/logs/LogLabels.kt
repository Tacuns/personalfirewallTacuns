package com.sentinel.core.logs

/**
 * The reason shown next to a log row.
 *
 * DnsInterceptor gives every website block a label (AD/TRACKER, USER BLOCK, SCHEDULE,
 * LOOK-ALIKE, WOULD BLOCK) but leaves it empty when the only reason is that the whole app
 * is blocked in the Apps tab. Those rows used to show no reason at all.
 *
 * "BLOCKED with an empty label" can only mean an app block: blocklists match the exact name
 * and always label AD/TRACKER, your own and schedule rules always label USER BLOCK or
 * SCHEDULE, and look-alike or watch-only blocks carry their own labels. So the reason is
 * filled in here, when the row is shown, which also fixes rows logged before this change.
 */
object LogLabels {

    const val APP_BLOCKED = "APP BLOCKED"

    fun displayLabel(status: String, threatLabel: String): String =
        if (status == "BLOCKED" && threatLabel.isEmpty()) APP_BLOCKED else threatLabel
}
