package com.sentinel.ui.lock

import com.sentinel.core.settings.AppPreferencesRepository.AppLockConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockThrottleTest {

    // ── wait rules ──────────────────────────────────────────────────────────

    @Test fun firstFourWrongTriesHaveNoWait() {
        for (failures in 0..4) assertEquals(0L, AppLockThrottle.waitAfter(failures))
    }

    @Test fun fifthWrongTryWaitsThirtySeconds() {
        assertEquals(30_000L, AppLockThrottle.waitAfter(5))
    }

    @Test fun waitDoublesAfterEachFurtherWrongTry() {
        assertEquals(60_000L,  AppLockThrottle.waitAfter(6))
        assertEquals(120_000L, AppLockThrottle.waitAfter(7))
        assertEquals(240_000L, AppLockThrottle.waitAfter(8))
        assertEquals(480_000L, AppLockThrottle.waitAfter(9))
    }

    @Test fun waitNeverExceedsFifteenMinutes() {
        assertEquals(AppLockThrottle.MAX_WAIT_MS, AppLockThrottle.waitAfter(10))
        assertEquals(AppLockThrottle.MAX_WAIT_MS, AppLockThrottle.waitAfter(1_000_000))
    }

    // ── remaining time ──────────────────────────────────────────────────────

    @Test fun noWaitWhenNeverLockedOrAlreadyExpired() {
        assertEquals(0L, AppLockThrottle.remainingMs(0L, 1_000L))
        assertEquals(0L, AppLockThrottle.remainingMs(5_000L, 9_000L))
    }

    @Test fun remainingCountsDown() {
        assertEquals(20_000L, AppLockThrottle.remainingMs(30_000L, 10_000L))
    }

    @Test fun clockMovedBackwardsCannotMakeTheWaitLonger() {
        assertEquals(AppLockThrottle.MAX_WAIT_MS, AppLockThrottle.remainingMs(99_000_000L, 0L))
    }

    // ── display ─────────────────────────────────────────────────────────────

    @Test fun formatShowsMinutesAndSeconds() {
        assertEquals("0:30",  AppLockThrottle.format(30_000L))
        assertEquals("1:05",  AppLockThrottle.format(65_000L))
        assertEquals("15:00", AppLockThrottle.format(AppLockThrottle.MAX_WAIT_MS))
    }

    @Test fun formatRoundsUpSoItNeverShowsZeroWhileWaiting() {
        assertEquals("0:01", AppLockThrottle.format(1L))
        assertEquals("0:30", AppLockThrottle.format(29_001L))
        assertEquals("0:00", AppLockThrottle.format(0L))
    }

    // ── existing configs ────────────────────────────────────────────────────

    @Test fun configWithoutCounterStartsUnlocked() {
        val cfg = AppLockConfig(true, emptyList(), null, null, null, null)
        assertEquals(0, cfg.failedAttempts)
        assertEquals(0L, cfg.lockedUntilMs)
    }
}
