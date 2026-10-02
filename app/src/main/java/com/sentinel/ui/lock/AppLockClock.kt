package com.sentinel.ui.lock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Reads the clocks App Lock's wait uses (see [AppLockThrottle.remainingMs]). */
object AppLockClock {

    fun now(context: Context): AppLockThrottle.Now = AppLockThrottle.Now(
        wallMs    = System.currentTimeMillis(),
        elapsedMs = SystemClock.elapsedRealtime(),
        boot      = bootCount(context)
    )

    // A restart ends every app process, so the count cannot change while this one runs.
    // Reading it once keeps the once-a-second countdown free of system calls.
    @Volatile private var cachedBoot: Int? = null

    /** How many times the phone has started; -1 when Android does not say. */
    fun bootCount(context: Context): Int = cachedBoot ?: run {
        val boot = try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }
                   catch (_: Exception) { -1 }
        if (boot >= 0) cachedBoot = boot
        boot
    }
}
