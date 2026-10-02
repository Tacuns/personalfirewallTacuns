package com.sentinel.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the current screen out of screenshots, screen recordings and the recent-apps
 * thumbnail with FLAG_SECURE
 * (developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE).
 *
 * Used on the sensitive screens (App Lock, activity log, change history, alerts). When the
 * whole app is secure (BuildConfig.BLOCK_ALL_SCREENSHOTS, set in SentinelActivity.onCreate)
 * the flag is only ever added. When the app-wide block is off, the flag is cleared again on
 * leaving the screen, so only these screens are protected.
 */
@Composable
fun SecureScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!com.tacu.nsfwzerotrust.BuildConfig.BLOCK_ALL_SCREENSHOTS) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

/** Unwraps the Activity from a (possibly wrapped) Compose context. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
