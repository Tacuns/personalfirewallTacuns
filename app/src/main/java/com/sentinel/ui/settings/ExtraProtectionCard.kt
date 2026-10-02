package com.sentinel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.core.rules.FirewallOptions
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R

/**
 * Settings › Protection: the three extra firewall switches.
 * Same card, icon tile and switch colours as Boot on Start and App Lock above it.
 */
@Composable
fun ExtraProtectionCard(
    options:      FirewallOptions,
    onWatchOnly:  (Boolean) -> Unit,
    onLookalikes: (Boolean) -> Unit,
    onSafeSearch: (Boolean) -> Unit,
    onSafeSearchYouTube: (Boolean) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            OptionRow(Icons.Default.Search, stringResource(R.string.safe_search_title),
                stringResource(R.string.safe_search_desc), options.safeSearch, onSafeSearch)
            // YouTube has its own switch, shown while safe search is on, so search sites can
            // stay safe without forcing YouTube Restricted Mode.
            if (options.safeSearch) {
                OptionRow(Icons.Default.OndemandVideo, stringResource(R.string.safe_search_youtube_title),
                    stringResource(R.string.safe_search_youtube_desc), options.safeSearchYouTube,
                    onSafeSearchYouTube)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp),
                color = BgBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            OptionRow(Icons.Default.Warning, stringResource(R.string.lookalike_block_title),
                stringResource(R.string.lookalike_block_desc), options.blockLookalikes, onLookalikes)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp),
                color = BgBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            // Switching this on lowers protection, so it turns amber while on.
            OptionRow(Icons.Default.Visibility, stringResource(R.string.watch_only_title),
                stringResource(R.string.watch_only_desc), options.watchOnly, onWatchOnly, warnWhenOn = true)
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector, title: String, desc: String,
    checked: Boolean, onChange: (Boolean) -> Unit, warnWhenOn: Boolean = false
) {
    val warn = warnWhenOn && checked
    val accent = if (warn) AmberMedium else CyanPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (warn) AmberMedium.copy(alpha = 0.12f) else Color(0xFF062028)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = if (warn) AmberMedium else TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,   // the whole row toggles, so the switch is not a second target
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = accent,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BgBorder
            )
        )
    }
}
