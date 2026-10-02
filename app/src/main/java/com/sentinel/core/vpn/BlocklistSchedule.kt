package com.sentinel.core.vpn

import android.content.Context

/**
 * How often each blocklist is downloaded again, chosen per list on the Blocklists screen.
 *
 * The periodic worker wakes every [CHECK_EVERY_HOURS] hours and downloads only the lists
 * that are due. A list is due when its interval has passed since its last successful
 * download, so a failed download stays due and is tried again, while the copy already
 * stored keeps blocking. The shortest choice is 6 hours: the lists are several megabytes,
 * and the community lists this app uses are published at most a few times a day.
 *
 * Main process only (the screen and the worker), so plain SharedPreferences are enough.
 */
object BlocklistSchedule {

    const val HOURS_6  = 6
    const val HOURS_24 = 24
    const val HOURS_168 = 24 * 7

    /** Offered on the screen, in this order. */
    val CHOICES = listOf(HOURS_6, HOURS_24, HOURS_168)

    /** Weekly, which is what every list used before the choice existed. */
    const val DEFAULT_HOURS = HOURS_168

    /** How often the periodic worker wakes to look for lists that are due. */
    const val CHECK_EVERY_HOURS = 6L

    private const val HOUR_MS = 3_600_000L

    fun sanitize(hours: Int): Int = if (hours in CHOICES) hours else DEFAULT_HOURS

    private fun prefs(context: Context) =
        context.getSharedPreferences("blocklist_schedule", Context.MODE_PRIVATE)

    fun intervalHours(context: Context, sourceKey: String): Int =
        sanitize(prefs(context).getInt(sourceKey, DEFAULT_HOURS))

    fun setIntervalHours(context: Context, sourceKey: String, hours: Int) {
        prefs(context).edit().putInt(sourceKey, sanitize(hours)).apply()
    }

    fun forget(context: Context, sourceKey: String) {
        prefs(context).edit().remove(sourceKey).apply()
    }

    /** True when the list should be downloaded now: never downloaded, or its interval has passed. */
    fun isDue(lastUpdatedMs: Long, domainCount: Int, intervalHours: Int, nowMs: Long): Boolean =
        domainCount == 0 || lastUpdatedMs <= 0L || nowMs - lastUpdatedMs >= sanitize(intervalHours) * HOUR_MS

    /**
     * When the list becomes due. The download itself can start up to [CHECK_EVERY_HOURS]
     * later, and Android may delay background work further, so the screen says "about".
     */
    fun dueAtMs(lastUpdatedMs: Long, intervalHours: Int): Long =
        lastUpdatedMs + sanitize(intervalHours) * HOUR_MS
}
