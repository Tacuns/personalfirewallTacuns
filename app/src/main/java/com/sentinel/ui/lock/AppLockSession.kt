package com.sentinel.ui.lock

import android.content.Context
import android.widget.Toast
import com.tacu.nsfwzerotrust.R

/**
 * Whether the person using the app right now signed in with a view-only App Lock account.
 *
 * Set when the app is unlocked, cleared when no lock is active. Every action that changes
 * rules, settings or accounts — and turning the firewall off — asks [blockChange] first.
 * The check sits in the ViewModels and the firewall switch, not only in the screens, so a
 * view-only account cannot make a change by reaching a control some other way.
 *
 * Lives in the app (UI) process only. The firewall service runs in its own process and
 * never reads this, so protection is never affected by who is signed in.
 */
object AppLockSession {

    @Volatile var readOnly: Boolean = false

    /** True — after telling the user — when the current account may not make changes. */
    fun blockChange(context: Context): Boolean {
        if (!readOnly) return false
        showDenied(context)
        return true
    }

    fun showDenied(context: Context) {
        Toast.makeText(context.applicationContext, R.string.app_lock_view_only_denied, Toast.LENGTH_SHORT).show()
    }
}
