package com.sentinel.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.core.rules.BlocklistSource
import com.sentinel.core.vpn.BlocklistSchedule
import com.sentinel.core.vpn.BlocklistSyncStatus
import com.sentinel.ui.components.InlineAlert
import com.sentinel.ui.components.SecurePalette
import com.tacu.nsfwzerotrust.R

/** What the "next update" part of a list row says. */
sealed interface NextUpdate {
    /** Due now: it downloads the next time the phone is online (and retries after a failure). */
    data object WhenOnline : NextUpdate
    data class InHours(val hours: Int) : NextUpdate
    data class InDays(val days: Int) : NextUpdate
}

/**
 * When the list is next downloaded, from its last successful download and its interval.
 * Rounded up, so "about 1h" never shows for a list that is minutes from due; days from 48 h on.
 */
fun nextUpdate(source: BlocklistSource, intervalHours: Int, nowMs: Long): NextUpdate {
    if (BlocklistSchedule.isDue(source.lastUpdatedMs, source.domainCount, intervalHours, nowMs)) {
        return NextUpdate.WhenOnline
    }
    val left  = BlocklistSchedule.dueAtMs(source.lastUpdatedMs, intervalHours) - nowMs
    val hours = ((left + 3_599_999L) / 3_600_000L).toInt().coerceAtLeast(1)
    return if (hours < 48) NextUpdate.InHours(hours) else NextUpdate.InDays((hours + 23) / 24)
}

/** "Updates every week · next in about 5d ›" — tap to change how often the list updates. */
@Composable
fun BlocklistUpdateLine(
    source:        BlocklistSource,
    intervalHours: Int,
    color:         Color,
    onClick:       () -> Unit
) {
    val every = stringResource(when (intervalHours) {
        BlocklistSchedule.HOURS_6  -> R.string.blocklist_updates_6h
        BlocklistSchedule.HOURS_24 -> R.string.blocklist_updates_day
        else                       -> R.string.blocklist_updates_week
    })
    val next = when (val n = nextUpdate(source, intervalHours, System.currentTimeMillis())) {
        NextUpdate.WhenOnline -> stringResource(R.string.blocklist_next_online)
        is NextUpdate.InHours -> stringResource(R.string.blocklist_next_hours, n.hours)
        is NextUpdate.InDays  -> stringResource(R.string.blocklist_next_days, n.days)
    }
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Wraps without pushing the arrow off the row.
        Text("$every · $next", fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Default.ChevronRight, null, tint = color, modifier = Modifier.size(14.dp))
    }
}

/** Why the latest download failed, and — when a copy is stored — that it still blocks. */
@Composable
fun BlocklistErrorLine(code: String, hasStoredCopy: Boolean) {
    val reason = blocklistErrorText(code)
    InlineAlert(if (hasStoredCopy) "$reason\n${stringResource(R.string.blocklist_keeps_old_copy)}" else reason)
}

@Composable
fun blocklistErrorText(code: String): String = when (code) {
    BlocklistSyncStatus.HTTP      -> stringResource(R.string.blocklist_custom_error_http)
    BlocklistSyncStatus.TOO_LARGE -> stringResource(R.string.blocklist_custom_error_too_large)
    BlocklistSyncStatus.EMPTY     -> stringResource(R.string.blocklist_custom_error_empty)
    BlocklistSyncStatus.LINK      -> stringResource(R.string.blocklist_custom_err_link)
    else                          -> stringResource(R.string.blocklist_custom_error_network)
}

@Composable
fun BlocklistIntervalDialog(
    listName:  String,
    current:   Int,
    onPick:    (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SecurePalette.GlassTop,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Text(stringResource(R.string.blocklist_interval_title, listName), fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = SecurePalette.TextMain)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BlocklistSchedule.CHOICES.forEach { hours ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(hours) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = hours == current,
                            onClick  = { onPick(hours) },
                            colors   = RadioButtonDefaults.colors(selectedColor = SecurePalette.Orange)
                        )
                        Text(
                            stringResource(when (hours) {
                                BlocklistSchedule.HOURS_6  -> R.string.blocklist_interval_6h
                                BlocklistSchedule.HOURS_24 -> R.string.blocklist_interval_day
                                else                       -> R.string.blocklist_interval_week
                            }),
                            fontSize = 13.5.sp, color = SecurePalette.TextMain
                        )
                    }
                }
                Text(stringResource(R.string.blocklist_interval_note), fontSize = 11.5.sp,
                    color = SecurePalette.TextSoft, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = SecurePalette.TextSoft)
            }
        }
    )
}
