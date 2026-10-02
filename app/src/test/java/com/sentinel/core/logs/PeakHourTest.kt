package com.sentinel.core.logs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class PeakHourTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")     // UTC+5:30
    private val utc = TimeZone.getTimeZone("UTC")
    private val ny  = TimeZone.getTimeZone("America/New_York")  // has daylight saving

    // 2026-09-18 01:10 UTC = 06:40 IST (the emulator case that showed "1:00 AM").
    private val t0110Utc = 1_789_693_800_000L

    @Test fun emptyGivesNoHour() = assertEquals(-1, PeakHour.busiest(emptyList(), ist))

    @Test fun usesLocalHourNotUtc() {
        val blocks = listOf(t0110Utc, t0110Utc + 60_000, t0110Utc + 120_000)
        assertEquals(6, PeakHour.busiest(blocks, ist))
        assertEquals(1, PeakHour.busiest(blocks, utc))
    }

    @Test fun halfHourZoneSplitsAtLocalHour() {
        // 01:25 UTC = 06:55 IST, 01:35 UTC = 07:05 IST: same UTC hour, different local hours.
        val a = t0110Utc + 15 * 60_000L
        val b = t0110Utc + 25 * 60_000L
        assertEquals(7, PeakHour.busiest(listOf(a, b, b), ist))
    }

    @Test fun tieKeepsEarliestHour() {
        val h6 = t0110Utc                  // 06 IST
        val h9 = t0110Utc + 3 * 3_600_000L // 09 IST
        assertEquals(6, PeakHour.busiest(listOf(h9, h6), ist))
    }

    @Test fun followsDaylightSaving() {
        // 2026-07-01 16:00 UTC = 12:00 EDT (UTC-4); 2026-01-15 17:00 UTC = 12:00 EST (UTC-5).
        val summer = 1_782_921_600_000L
        val winter = 1_768_496_400_000L
        assertEquals(12, PeakHour.busiest(listOf(summer), ny))
        assertEquals(12, PeakHour.busiest(listOf(winter), ny))
    }
}
