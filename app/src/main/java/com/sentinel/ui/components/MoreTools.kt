package com.sentinel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R

/**
 * The one "More tools" row of a main tab (progressive disclosure: the few things most users need
 * stay on top, the rest is one tap away, never more than one level deep). [summary] names the
 * tools inside and the state of any that are switched on, so nothing in use is out of sight.
 * Same card style as the main tabs (BgCard, BgBorder, radius 12).
 */
@Composable
fun MoreToolsToggle(
    open: Boolean,
    summary: String,
    onToggle: () -> Unit,
    title: String = stringResource(R.string.more_tools_title),
    showLabel: String = stringResource(R.string.cd_more_tools_show),
    hideLabel: String = stringResource(R.string.cd_more_tools_hide)
) {
    val action = if (open) hideLabel else showLabel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = CyanPrimary
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        // The arrow sits in its own outlined box so it is easy to spot again when closing (owner, 2026-10-01).
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CyanPrimary.copy(alpha = 0.12f))
                .border(1.5.dp, CyanPrimary, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = CyanPrimary
            )
        }
    }
}
