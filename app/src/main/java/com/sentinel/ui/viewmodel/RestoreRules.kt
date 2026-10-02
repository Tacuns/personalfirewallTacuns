package com.sentinel.ui.viewmodel

import com.sentinel.core.schedule.ScheduleCategories

/**
 * What a restore may write. A backup file can be edited by hand, so every value is checked
 * before it is saved; anything outside what the app's own screens could produce is skipped.
 *
 * Pure functions only, so the rules are unit-tested on the JVM.
 */
object RestoreRules {

    private val DAYS = Regex("^[01]{7}$")
    private val STATUSES = setOf("ALLOWED", "BLOCKED")

    /** Allowed clock difference between phones, so a backup made a moment ago is accepted. */
    private const val CLOCK_SLACK_MS = 10 * 60_000L

    /** Same limits as the schedule controls: hours 0–23, minutes 0–59, a known category, 7 days. */
    fun isValidSchedule(category: String, startHour: Int, startMin: Int,
                        endHour: Int, endMin: Int, days: String): Boolean =
        category in ScheduleCategories.all &&
        startHour in 0..23 && endHour in 0..23 &&
        startMin in 0..59 && endMin in 0..59 &&
        DAYS.matches(days)

    /** A log row must have a known result and a time that is not in the future. */
    fun isValidLog(status: String, timestampMs: Long, nowMs: Long): Boolean =
        status in STATUSES && timestampMs in 1..(nowMs + CLOCK_SLACK_MS)

    /**
     * An app rule is never restored for the firewall itself: blocking it cut off its own
     * blocklist downloads (seen on the emulator).
     */
    fun isRestorableApp(packageName: String, ownPackage: String): Boolean =
        packageName.isNotEmpty() && packageName != ownPackage

    /**
     * A blocklist switch is restored only for lists the user can switch on the Blocklists
     * screen. Locked (PRO) lists stay as they are.
     */
    fun isSwitchableList(isDefault: Boolean): Boolean = isDefault
}
