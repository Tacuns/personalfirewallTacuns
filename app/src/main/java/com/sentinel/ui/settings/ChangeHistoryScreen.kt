package com.sentinel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.core.logs.ChangeAction
import com.sentinel.core.logs.ChangeHistoryEntity
import com.sentinel.core.logs.ChangeValue
import com.sentinel.core.schedule.ScheduleCategories
import com.sentinel.ui.components.SecureBackground
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.components.SecureScreen
import com.sentinel.ui.components.SecureTopBar
import com.sentinel.ui.viewmodel.ChangeHistoryViewModel
import com.tacu.nsfwzerotrust.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings, Change history: every rule and setting change the app recorded, newest first,
 * with the value before and after it. Read-only; nothing here changes a rule.
 *
 * Rows can contain websites the user blocked, so this screen is FLAG_SECURE like the
 * Activity screen: no screenshots, blank recents thumbnail.
 */
@Composable
fun ChangeHistoryScreen(
    onBack: () -> Unit,
    viewModel: ChangeHistoryViewModel = viewModel()
) {
    SecureScreen()

    val changes by viewModel.changes.collectAsState()
    var askClear by remember { mutableStateOf(false) }

    SecureBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            SecureTopBar(stringResource(R.string.ch_title), onBack)

            if (changes.isEmpty()) {
                EmptyHistory()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(changes, key = { it.id }) { ChangeRow(it) }
                }
                TextButton(
                    onClick = { askClear = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Text(stringResource(R.string.ch_clear), color = SecurePalette.TextSoft, fontSize = 13.sp)
                }
            }
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.ch_clear_confirm_title)) },
            text = { Text(stringResource(R.string.ch_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clear(); askClear = false }) {
                    Text(stringResource(R.string.ch_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
            containerColor = SecurePalette.GlassTop,
            titleContentColor = SecurePalette.TextMain,
            textContentColor = SecurePalette.TextSoft
        )
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.History, null, tint = SecurePalette.TextFaint, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.ch_empty_title),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ch_empty_body),
            fontSize = 13.sp, color = SecurePalette.TextSoft, lineHeight = 18.sp
        )
    }
}

@Composable
private fun ChangeRow(entry: ChangeHistoryEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SecurePalette.GlassBrush)
            .border(1.dp, SecurePalette.Edge, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(actionIcon(entry.action), null, tint = SecurePalette.Orange, modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    actionLabel(entry.action),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain
                )
                Spacer(Modifier.height(2.dp))
                Text(targetLabel(entry), fontSize = 12.sp, color = SecurePalette.TextSoft, lineHeight = 16.sp)

                val before = valueLabel(entry.action, entry.beforeValue)
                val after  = valueLabel(entry.action, entry.afterValue)
                if (before.isNotEmpty() || after.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (before.isNotEmpty()) {
                            Text(before, fontSize = 12.sp, color = SecurePalette.TextFaint)
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward, null,
                                tint = SecurePalette.TextFaint, modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(after, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SecurePalette.TextMain)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(formatWhen(entry.timestampMs), fontSize = 11.sp, color = SecurePalette.TextFaint)
            }
        }
    }
}

private fun actionIcon(action: String): ImageVector = when (action) {
    ChangeAction.DOMAIN_BLOCKED   -> Icons.Default.Block
    ChangeAction.DOMAIN_UNBLOCKED -> Icons.Default.CheckCircle
    ChangeAction.DOMAIN_RENAMED   -> Icons.Default.Edit
    ChangeAction.DOMAIN_ALWAYS_ALLOWED -> Icons.Default.CheckCircle
    ChangeAction.DOMAIN_ALLOW_REMOVED  -> Icons.Default.Block
    ChangeAction.APP_POLICY       -> Icons.Default.Apps
    ChangeAction.SCHEDULE         -> Icons.Default.Schedule
    ChangeAction.BLOCKLIST        -> Icons.Default.FilterList
    ChangeAction.RULES_IMPORTED   -> Icons.Default.Download
    ChangeAction.BACKUP_RESTORED  -> Icons.Default.Restore
    ChangeAction.DNS_SERVER       -> Icons.Default.Dns
    ChangeAction.PROTECTION_LEVEL -> Icons.Default.Shield
    else                          -> Icons.Default.History
}

@Composable
private fun actionLabel(action: String): String = when (action) {
    ChangeAction.DOMAIN_BLOCKED   -> stringResource(R.string.ch_act_domain_blocked)
    ChangeAction.DOMAIN_UNBLOCKED -> stringResource(R.string.ch_act_domain_unblocked)
    ChangeAction.DOMAIN_RENAMED   -> stringResource(R.string.ch_act_domain_renamed)
    ChangeAction.DOMAIN_ALWAYS_ALLOWED -> stringResource(R.string.ch_act_domain_always_allowed)
    ChangeAction.DOMAIN_ALLOW_REMOVED  -> stringResource(R.string.ch_act_domain_allow_removed)
    ChangeAction.APP_POLICY       -> stringResource(R.string.ch_act_app_policy)
    ChangeAction.SCHEDULE         -> stringResource(R.string.ch_act_schedule)
    ChangeAction.BLOCKLIST        -> stringResource(R.string.ch_act_blocklist)
    ChangeAction.RULES_IMPORTED   -> stringResource(R.string.ch_act_rules_imported)
    ChangeAction.BACKUP_RESTORED  -> stringResource(R.string.ch_act_backup_restored)
    ChangeAction.DNS_SERVER       -> stringResource(R.string.ch_act_dns_server)
    ChangeAction.PROTECTION_LEVEL -> stringResource(R.string.ch_act_protection_level)
    else                          -> action
}

/** What the change was about: a website, an app, a list, or how many rules came in. */
@Composable
private fun targetLabel(entry: ChangeHistoryEntity): String = when (entry.action) {
    ChangeAction.RULES_IMPORTED, ChangeAction.BACKUP_RESTORED ->
        stringResource(R.string.ch_rules_added, entry.target)
    ChangeAction.SCHEDULE -> when (entry.target) {
        ScheduleCategories.SOCIAL    -> stringResource(R.string.schedule_cat_social)
        ScheduleCategories.STREAMING -> stringResource(R.string.schedule_cat_streaming)
        ScheduleCategories.GAMING    -> stringResource(R.string.schedule_cat_gaming)
        ScheduleCategories.NEWS      -> stringResource(R.string.schedule_cat_news)
        else                         -> entry.target
    }
    ChangeAction.DNS_SERVER -> stringResource(
        if (entry.target == "backup") R.string.dns_backup_title else R.string.dns_main_title
    )
    else -> entry.target
}

/**
 * Turns a stored value into words. Stored values are stable keys or real values
 * (a time window, a domain), never translated text, so the history still reads
 * correctly after the user switches the app to another language.
 */
@Composable
private fun valueLabel(action: String, raw: String): String {
    if (raw.isEmpty()) return ""
    if (action == ChangeAction.APP_POLICY) {
        val parts = raw.split(",")
        val wifi = parts.firstOrNull { it.startsWith("wifi=") }?.removePrefix("wifi=").orEmpty()
        val data = parts.firstOrNull { it.startsWith("data=") }?.removePrefix("data=").orEmpty()
        return stringResource(R.string.toggle_wifi) + ": " + stateWord(wifi) + "   " +
               stringResource(R.string.toggle_mobile_data) + ": " + stateWord(data)
    }
    if (action == ChangeAction.PROTECTION_LEVEL) {
        // Stored as a stable key ("strict", or "custom"), shown in the current language.
        return profileName(com.sentinel.core.rules.ProtectionProfile.fromKey(raw))
    }
    if (action == ChangeAction.DNS_SERVER) {
        // Stored as a stable key ("google"), or "custom:<address>" for a typed server.
        return if (raw.startsWith("custom:")) dnsChoiceName("custom") + " " + raw.removePrefix("custom:")
               else dnsChoiceName(raw)
    }
    return stateWord(raw)
}

@Composable
private fun stateWord(raw: String): String = when (raw) {
    ChangeValue.ALLOWED -> stringResource(R.string.ch_val_allowed)
    ChangeValue.BLOCKED -> stringResource(R.string.ch_val_blocked)
    ChangeValue.ON      -> stringResource(R.string.ch_val_on)
    ChangeValue.OFF     -> stringResource(R.string.ch_val_off)
    else                -> raw
}

private fun formatWhen(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))
