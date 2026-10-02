package com.sentinel.core.logs

import java.util.Calendar
import java.util.TimeZone

/**
 * The hour of the day (0–23, phone's own time zone) with the most blocks.
 *
 * Counting by `timestamp / 1 hour % 24` gave the UTC hour, so a phone in India showed
 * "1:00 AM" for blocks made at 6:40 AM. Each block is placed by its local clock time
 * instead, which also handles half-hour zones and daylight-saving changes.
 *
 * Pure function only, so it is unit-tested on the JVM.
 */
object PeakHour {

    /** Returns -1 when there are no blocks. On a tie the earliest hour wins. */
    fun busiest(timestamps: List<Long>, zone: TimeZone = TimeZone.getDefault()): Int {
        if (timestamps.isEmpty()) return -1
        val counts = IntArray(24)
        val cal = Calendar.getInstance(zone)
        for (ts in timestamps) {
            cal.timeInMillis = ts
            counts[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        return counts.indices.maxByOrNull { counts[it] } ?: -1
    }
}
