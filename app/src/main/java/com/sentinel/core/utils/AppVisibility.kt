package com.sentinel.core.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether the app's screen is currently visible (main process only).
 *
 * Screen refreshes, the VPN status poll and the analysis loops wait on this, so they stop
 * using CPU while the app is closed or in the background. Measured on the emulator: the
 * main process kept using CPU with the screen off before this. The firewall itself runs in
 * the :vpn process and never reads this, so protection is not affected.
 */
object AppVisibility {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    val isVisible: Boolean get() = _visible.value

    fun set(visible: Boolean) { _visible.value = visible }

    /** Suspends until the app is on screen. Returns immediately if it already is. */
    suspend fun awaitVisible() { _visible.first { it } }
}
