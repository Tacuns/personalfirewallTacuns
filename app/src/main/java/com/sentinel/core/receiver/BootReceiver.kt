package com.sentinel.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import com.sentinel.core.settings.AppPreferencesRepository
import com.sentinel.core.vpn.SentinelVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val bootEnabled = AppPreferencesRepository(context).bootOnStart.first()
                if (bootEnabled) {
                    // After a reboot Android starts with no app allowed to create a VPN
                    // (Vpn.java sets mPackage = LEGACY_VPN), and establish() returns null for
                    // an app that has not been prepared again. prepare() re-claims the VPN
                    // without any dialog when the user already consented. Without this call
                    // the tunnel silently failed after every reboot until the app was opened.
                    if (VpnService.prepare(context) != null) {
                        // Consent is gone (revoked, or another VPN app took over). A dialog
                        // cannot be shown from here; the app asks again when it is opened.
                        Log.w("SentinelBoot", "VPN consent missing at boot. Not starting.")
                        com.sentinel.core.alerts.SecurityAlerts.record(context, com.sentinel.core.alerts.AlertType.PROTECTION_NOT_STARTED,
                            com.sentinel.core.alerts.AlertSeverity.HIGH)
                    } else {
                        Log.d("SentinelBoot", "Boot restore enabled. Starting firewall...")
                        ContextCompat.startForegroundService(
                            context, Intent(context, SentinelVpnService::class.java)
                        )
                    }
                } else {
                    Log.d("SentinelBoot", "Boot restore disabled by user. Skipping.")
                }
            } catch (e: Exception) {
                Log.e("SentinelBoot", "Boot preference read failed: ${e.message}")
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
