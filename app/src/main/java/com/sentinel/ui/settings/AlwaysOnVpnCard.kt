package com.sentinel.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R

/**
 * Settings › Protection: opens Android's VPN settings so the user can turn on Always-on VPN.
 * With it, Android restarts the firewall by itself after stopping it (verified on the
 * emulator after turning notifications off). Same card style as Boot on Start.
 */
@Composable
fun AlwaysOnVpnCard() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
            .clickable {
                try {
                    context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF062028)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VpnLock, null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.always_on_title), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(stringResource(R.string.always_on_desc), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(stringResource(R.string.always_on_button), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, color = CyanPrimary, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}
