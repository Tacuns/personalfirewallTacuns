package com.sentinel.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.core.rules.ProtectionProfile
import com.sentinel.ui.theme.BgBorder
import com.sentinel.ui.theme.BgCard
import com.sentinel.ui.theme.CyanPrimary
import com.sentinel.ui.theme.TextMuted
import com.sentinel.ui.theme.TextPrimary
import com.sentinel.ui.theme.TextSecondary
import com.tacu.nsfwzerotrust.R

@Composable
fun profileName(profile: ProtectionProfile?): String = stringResource(when (profile) {
    ProtectionProfile.NORMAL -> R.string.profile_normal
    ProtectionProfile.STRICT -> R.string.profile_strict
    ProtectionProfile.KIDS   -> R.string.profile_kids
    null                     -> R.string.protection_level_custom
})

@Composable
private fun profileDescription(profile: ProtectionProfile): String = stringResource(when (profile) {
    ProtectionProfile.NORMAL -> R.string.profile_normal_desc
    ProtectionProfile.STRICT -> R.string.profile_strict_desc
    ProtectionProfile.KIDS   -> R.string.profile_kids_desc
})

/**
 * Settings › Protection › Protection level. [current] is worked out from the live settings,
 * so it reads "Custom" as soon as any switch no longer matches a level.
 */
@Composable
fun ProtectionLevelCard(
    current: ProtectionProfile?,
    onApply: (ProtectionProfile, onDone: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<ProtectionProfile?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF062028)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.protection_level_title), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(stringResource(R.string.protection_level_subtitle), style = MaterialTheme.typography.labelSmall,
                        color = TextMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.protection_level_current, profileName(current)),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = CyanPrimary)
                }
            }
            if (current == null) {
                Text(stringResource(R.string.protection_level_custom_hint), style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
            }
            ProtectionProfile.entries.forEach { profile ->
                HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                val selected = profile == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) CyanPrimary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable(enabled = !selected) { confirm = profile }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(profileName(profile), style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold, color = if (selected) CyanPrimary else TextPrimary)
                        Text(profileDescription(profile), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    if (selected) {
                        Icon(Icons.Default.CheckCircle, null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    confirm?.let { target ->
        val name = profileName(target)
        val applied = stringResource(R.string.protection_level_applied, name)
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor   = BgCard,
            title = { Text(stringResource(R.string.protection_level_confirm_title, name), color = TextPrimary,
                fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.protection_level_confirm_body, name), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    onApply(target) { Toast.makeText(context, applied, Toast.LENGTH_SHORT).show() }
                }) { Text(stringResource(R.string.protection_level_confirm_btn), color = CyanPrimary,
                        fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text(stringResource(R.string.btn_cancel), color = TextMuted)
                }
            }
        )
    }
}
