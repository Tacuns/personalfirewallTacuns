package com.sentinel.ui.lock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-lifetime scope for App Lock writes that must NOT be cancelled midway.
 *
 * Why this exists: saving credentials runs PBKDF2 at 600,000 iterations, which takes
 * seconds. Previously that ran in `viewModelScope`, and `SettingsScreen` lives in its own
 * NavBackStackEntry — so tapping any bottom tab before hashing finished cleared the
 * ViewModel, cancelled the coroutine, and silently saved nothing.
 *
 * Official guidance is to run such work in "an external scope managed by a class that
 * lives longer than the current screen", and explicitly NOT in GlobalScope. This app has
 * no custom Application class, so a documented singleton is used — the same pattern as
 * RuleEngine and LogManager elsewhere in this codebase.
 *
 * SupervisorJob so one failed save cannot tear down later ones. Dispatchers.Default is
 * stated explicitly because the work is CPU-bound key derivation.
 */
internal object AppLockScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
