package com.sentinel.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tacu.nsfwzerotrust.R

/**
 * Shown instead of starting protection while Android's Private DNS is set to a provider:
 * both together leave the phone without internet (see PrivateDnsGuard).
 */
@Composable
fun PrivateDnsDialog(provider: String, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SecurePalette.GlassTop,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Text(stringResource(R.string.private_dns_title), fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = SecurePalette.TextMain)
        },
        text = {
            Text(stringResource(R.string.private_dns_body, provider), fontSize = 12.5.sp,
                color = SecurePalette.TextSoft, lineHeight = 18.sp)
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.private_dns_open), color = SecurePalette.Orange,
                    fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close), color = SecurePalette.TextSoft)
            }
        }
    )
}

/**
 * Opens Android's network settings, where Private DNS is (Network & internet on Pixel,
 * Connections › More connection settings on Samsung). There is no public screen action for
 * Private DNS itself, so the general wireless settings screen is used.
 */
fun openNetworkSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { }
    }
}
