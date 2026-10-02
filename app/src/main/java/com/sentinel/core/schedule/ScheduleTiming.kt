package com.sentinel.core.schedule

/**
 * When the schedule should next be checked so it starts and stops close to the chosen minute.
 *
 * The regular check only runs every 15 minutes (Android's minimum for repeating work), so on
 * top of it one extra check is booked for the next start or end minute. Pure functions only,
 * so the timing is unit-tested on the JVM.
 */
object ScheduleTiming {

    /** Runs the check just after the boundary minute, never just before it. */
    const val MARGIN_MS = 5_000L

    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * Milliseconds until the next start or end minute (plus [MARGIN_MS]), or null when start and
     * end are equal — such a schedule is never active, so there is nothing to wait for.
     * A boundary that falls in the current minute counts as tomorrow's: this run handles today's.
     */
    fun msUntilNextBoundary(nowMinuteOfDay: Int, nowSecond: Int, startMinuteOfDay: Int, endMinuteOfDay: Int): Long? {
        if (startMinuteOfDay == endMinuteOfDay) return null
        fun minutesUntil(target: Int): Int {
            val d = (target - nowMinuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return if (d == 0) MINUTES_PER_DAY else d
        }
        val minutes = minOf(minutesUntil(startMinuteOfDay), minutesUntil(endMinuteOfDay))
        return minutes * 60_000L - nowSecond * 1_000L + MARGIN_MS
    }
}
