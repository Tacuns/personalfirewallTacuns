package com.sentinel.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTimingTest {

    private fun min(h: Int, m: Int) = h * 60 + m
    private fun expected(minutes: Int, nowSecond: Int = 0) =
        minutes * 60_000L - nowSecond * 1_000L + ScheduleTiming.MARGIN_MS

    @Test fun waitsForTheStartWhenItComesFirst() {
        // now 21:00, window 22:07 → 06:00 : next boundary is the start, 67 minutes away
        assertEquals(expected(67), ScheduleTiming.msUntilNextBoundary(min(21, 0), 0, min(22, 7), min(6, 0)))
    }

    @Test fun waitsForTheEndWhenInsideTheWindow() {
        // now 23:00, window 22:07 → 06:00 : next boundary is the end, 7 hours away (overnight)
        assertEquals(expected(7 * 60), ScheduleTiming.msUntilNextBoundary(min(23, 0), 0, min(22, 7), min(6, 0)))
    }

    @Test fun secondsAlreadyPassedAreSubtracted() {
        assertEquals(expected(10, nowSecond = 30), ScheduleTiming.msUntilNextBoundary(min(9, 50), 30, min(10, 0), min(12, 0)))
    }

    @Test fun boundaryInTheCurrentMinuteMeansTheOtherOneOrTomorrow() {
        // now exactly 10:00 = start; the end 12:00 is next
        assertEquals(expected(120), ScheduleTiming.msUntilNextBoundary(min(10, 0), 0, min(10, 0), min(12, 0)))
    }

    @Test fun wrapsPastMidnight() {
        // now 23:59, window 00:01 → 01:00 : start is 2 minutes away
        assertEquals(expected(2), ScheduleTiming.msUntilNextBoundary(min(23, 59), 0, min(0, 1), min(1, 0)))
    }

    @Test fun equalStartAndEndHasNothingToWaitFor() {
        assertNull(ScheduleTiming.msUntilNextBoundary(min(8, 0), 0, min(9, 0), min(9, 0)))
    }

    @Test fun neverWaitsMoreThanADay() {
        val ms = ScheduleTiming.msUntilNextBoundary(min(0, 0), 0, min(0, 0), min(0, 0) + 1)!!
        assertEquals(expected(1), ms)
    }
}
