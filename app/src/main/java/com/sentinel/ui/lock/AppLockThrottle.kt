package com.sentinel.ui.lock

import java.util.Locale

/**
 * Slows down password guessing on App Lock.
 *
 * The first [FREE_TRIES] wrong tries cost nothing. After that every wrong try starts a
 * wait that doubles each time, from 30 seconds up to [MAX_WAIT_MS]. A correct password
 * or a successful recovery clears the count.
 *
 * There is never a permanent lock: the longest wait is capped, and [remainingMs] is capped
 * too, so moving the phone clock backwards cannot create a longer wait.
 *
 * Pure functions only, so the rules are unit-tested on the JVM.
 */
object AppLockThrottle {

    const val FREE_TRIES = 5
    private const val FIRST_WAIT_MS = 30_000L
    const val MAX_WAIT_MS = 15 * 60_000L

    /** How long to wait after the [failures]-th wrong try in a row. */
    fun waitAfter(failures: Int): Long {
        if (failures < FREE_TRIES) return 0L
        val doublings = (failures - FREE_TRIES).coerceAtMost(10)   // bounded, so no overflow
        return (FIRST_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
    }

    /** Milliseconds left before the next try is allowed; 0 means try now. */
    fun remainingMs(lockedUntilMs: Long, nowMs: Long): Long =
        (lockedUntilMs - nowMs).coerceIn(0L, MAX_WAIT_MS)

    /**
     * When the wait ends, on two clocks: the wall clock, and the time since the phone
     * started ([elapsedMs], with the [boot] it was measured in). The user can move the wall
     * clock, but not the time since start.
     */
    data class Deadline(val wallMs: Long, val elapsedMs: Long = 0L, val boot: Int = -1)

    /** The same two clocks, read now. */
    data class Now(val wallMs: Long, val elapsedMs: Long, val boot: Int)

    /**
     * Milliseconds left before the next try is allowed.
     *
     * Moving the phone clock forward used to end the wait at once (seen on the emulator).
     * While the phone has not restarted, the time since start decides, so changing the clock
     * does nothing. After a restart that time starts again from zero, so the wall clock
     * decides, exactly as before this change. An unknown boot number also falls back to it.
     */
    fun remainingMs(deadline: Deadline, now: Now): Long =
        if (deadline.boot >= 0 && deadline.boot == now.boot && deadline.elapsedMs > 0L)
            (deadline.elapsedMs - now.elapsedMs).coerceIn(0L, MAX_WAIT_MS)
        else
            remainingMs(deadline.wallMs, now.wallMs)

    /** "m:ss", rounded up so the screen never shows 0:00 while still waiting. */
    fun format(ms: Long): String {
        val seconds = (ms + 999) / 1000
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}
