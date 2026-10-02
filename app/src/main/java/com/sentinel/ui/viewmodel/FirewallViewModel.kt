package com.sentinel.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.core.vpn.vpnRunningFlow
import kotlinx.coroutines.launch

/**
 * FirewallViewModel – cross-process safe VPN state.
 *
 * Uses ConnectivityManager TRANSPORT_VPN check (VpnState.kt) instead of
 * SentinelVpnService.isRunning companion StateFlow. The companion StateFlow
 * only lives in the :vpn process — reading it from the UI process always
 * returns false after android:process=":vpn" was added to the manifest.
 */
class FirewallViewModel(application: Application) : AndroidViewModel(application) {

    // Binary state observed by Dashboard: true = SECURE, false = OFF
    var isVpnRunning = mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            getApplication<Application>().vpnRunningFlow().collect { running ->
                isVpnRunning.value = running
            }
        }
    }

}
