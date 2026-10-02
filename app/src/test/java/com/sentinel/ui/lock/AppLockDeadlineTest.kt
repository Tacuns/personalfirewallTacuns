package com.sentinel.ui.lock

import com.sentinel.ui.lock.AppLockThrottle.Deadline
import com.sentinel.ui.lock.AppLockThrottle.Now
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockDeadlineTest {

    private val wait  = 120_000L                  // a 2-minute wait
    private val wall0 = 1_789_700_000_000L        // wall clock when the wait started
    private val up0   = 5_000_000L                // time since phone start when it started
    private val boot  = 7
    private val deadline = Deadline(wallMs = wall0 + wait, elapsedMs = up0 + wait, boot = boot)

    @Test fun waitCountsDownNormally() {
        assertEquals(wait, AppLockThrottle.remainingMs(deadline, Now(wall0, up0, boot)))
        assertEquals(60_000L, AppLockThrottle.remainingMs(deadline, Now(wall0 + 60_000, up0 + 60_000, boot)))
        assertEquals(0L, AppLockThrottle.remainingMs(deadline, Now(wall0 + wait, up0 + wait, boot)))
    }

    @Test fun movingClockForwardDoesNotSkipTheWait() {
        // The emulator attack: one hour added to the wall clock, 2 real seconds later.
        val now = Now(wall0 + 3_600_000 + 2_000, up0 + 2_000, boot)
        assertEquals(wait - 2_000, AppLockThrottle.remainingMs(deadline, now))
    }

    @Test fun movingClockBackwardDoesNotLengthenTheWait() {
        val now = Now(wall0 - 86_400_000, up0 + 30_000, boot)
        assertEquals(wait - 30_000, AppLockThrottle.remainingMs(deadline, now))
    }

    @Test fun afterRestartTheWallClockDecidesAsBefore() {
        // Time since start restarts near zero after a reboot, so it cannot be compared.
        val afterReboot = Now(wall0 + 30_000, 20_000, boot + 1)
        assertEquals(wait - 30_000, AppLockThrottle.remainingMs(deadline, afterReboot))
    }

    @Test fun unknownBootFallsBackToWallClock() {
        val noBoot = deadline.copy(boot = -1)
        assertEquals(wait, AppLockThrottle.remainingMs(noBoot, Now(wall0, up0, -1)))
        assertEquals(wait, AppLockThrottle.remainingMs(deadline, Now(wall0, up0, -1)))
    }

    @Test fun waitSavedByOlderVersionStillWorks() {
        // Older versions saved only the wall-clock deadline.
        val old = Deadline(wallMs = wall0 + wait)
        assertEquals(wait, AppLockThrottle.remainingMs(old, Now(wall0, up0, boot)))
        assertEquals(0L, AppLockThrottle.remainingMs(Deadline(0L), Now(wall0, up0, boot)))
    }

    @Test fun neverLongerThanTheMaximumWait() {
        val far = Deadline(wallMs = Long.MAX_VALUE / 2, elapsedMs = Long.MAX_VALUE / 2, boot = boot)
        assertEquals(AppLockThrottle.MAX_WAIT_MS, AppLockThrottle.remainingMs(far, Now(wall0, up0, boot)))
    }
}
