package com.sentinel.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.core.alerts.AlertEntity
import com.sentinel.core.alerts.AlertSeverity
import com.sentinel.core.alerts.AlertType
import com.sentinel.ui.components.GradientButton
import com.sentinel.ui.components.QuietButton
import com.sentinel.ui.components.ScreenHeaderCard
import com.sentinel.ui.components.SecureBackground
import com.sentinel.ui.components.SecureCard
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.components.SecureScreen
import com.sentinel.ui.components.SecureTopBar
import com.sentinel.ui.settings.blocklistErrorText
import com.sentinel.ui.theme.AmberMedium
import com.sentinel.ui.theme.CyanPrimary
import com.sentinel.ui.theme.RedCritical
import com.sentinel.ui.viewmodel.AlertsViewModel
import com.tacu.nsfwzerotrust.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where an alert's action button goes. */
enum class AlertDestination { HOME, BLOCKLISTS, BACKUP, NETWORK_SETTINGS }

/**
 * Alerts: every security event kept in one list, so a warning that disappeared from the
 * notification shade can still be read. Rows may name websites, so the screen blocks
 * screenshots like the activity log does.
 */
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onNavigate: (AlertDestination) -> Unit,
    viewModel: AlertsViewModel = viewModel()
) {
    SecureScreen()
    val alerts by viewModel.alerts.collectAsState()
    val historyOff by viewModel.historyOff.collectAsState()
    val listNames by viewModel.listNames.collectAsState()
    val userBlocked by viewModel.userBlocked.collectAsState()
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    SecureBackground {
        Column(Modifier.fillMaxSize()) {
            SecureTopBar(stringResource(R.string.alerts_title), onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "header") {
                    ScreenHeaderCard(
                        icon = Icons.Default.NotificationsActive, tint = CyanPrimary,
                        title = stringResource(R.string.alerts_title),
                        subtitle = stringResource(R.string.alerts_subtitle)
                    )
                }
                item(key = "note") {
                    Text(
                        stringResource(if (historyOff) R.string.alerts_history_off else R.string.alerts_keep_note),
                        fontSize = 11.5.sp, color = if (historyOff) AmberMedium else SecurePalette.TextFaint,
                        lineHeight = 16.sp
                    )
                }
                if (alerts.isNotEmpty()) {
                    item(key = "actions") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (alerts.any { !it.read }) {
                                QuietButton(stringResource(R.string.alerts_mark_all_read), { viewModel.markAllRead() }, CyanPrimary)
                            }
                            QuietButton(stringResource(R.string.alerts_clear_all), { confirmClear = true }, RedCritical)
                        }
                    }
                }
                if (alerts.isEmpty()) {
                    item(key = "empty") {
                        SecureCard {
                            Text(stringResource(R.string.alerts_empty), fontSize = 12.5.sp,
                                color = SecurePalette.TextSoft, lineHeight = 18.sp)
                        }
                    }
                }
                items(alerts, key = { it.id }) { alert ->
                    AlertRow(
                        alert       = alert,
                        expanded    = expandedId == alert.id,
                        listName    = listNames[alert.target] ?: alert.target,
                        siteBlocked = alert.target in userBlocked,
                        onTap = {
                            expandedId = if (expandedId == alert.id) null else alert.id
                            if (!alert.read) viewModel.markRead(alert.id)
                        },
                        onBlockSite = { viewModel.blockSite(alert.target) },
                        onNavigate  = onNavigate
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor   = SecurePalette.GlassTop,
            shape            = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.alerts_clear_confirm_title), fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = SecurePalette.TextMain) },
            text = { Text(stringResource(R.string.alerts_clear_confirm_body), fontSize = 12.5.sp,
                color = SecurePalette.TextSoft, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); confirmClear = false }) {
                    Text(stringResource(R.string.alerts_clear_all), color = RedCritical, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.btn_cancel), color = SecurePalette.TextSoft)
                }
            }
        )
    }
}

private fun severityColor(severity: String): Color = when (severity) {
    AlertSeverity.HIGH   -> RedCritical
    AlertSeverity.MEDIUM -> AmberMedium
    else                 -> CyanPrimary
}

@Composable
private fun alertTitle(type: String): String = stringResource(when (type) {
    AlertType.LOOKALIKE_BLOCKED      -> R.string.alert_lookalike_blocked_title
    AlertType.LOOKALIKE_WARNED       -> R.string.alert_lookalike_warned_title
    AlertType.BLOCKLIST_FAILED       -> R.string.alert_blocklist_failed_title
    AlertType.PROTECTION_STOPPED     -> R.string.alert_protection_stopped_title
    AlertType.PROTECTION_NOT_STARTED -> R.string.alert_not_started_title
    AlertType.PRIVATE_DNS_CONFLICT   -> R.string.private_dns_paused_title
    else                             -> R.string.alert_rules_lost_title
})

@Composable
private fun alertDetail(alert: AlertEntity, listName: String): String = when (alert.type) {
    AlertType.LOOKALIKE_BLOCKED, AlertType.LOOKALIKE_WARNED ->
        stringResource(R.string.alert_lookalike_detail, alert.target, alert.extra)
    AlertType.BLOCKLIST_FAILED       -> "$listName: ${blocklistErrorText(alert.extra)}"
    AlertType.PROTECTION_STOPPED     -> stringResource(R.string.alert_protection_stopped_detail)
    AlertType.PROTECTION_NOT_STARTED -> stringResource(R.string.alert_not_started_detail)
    AlertType.PRIVATE_DNS_CONFLICT   -> stringResource(R.string.private_dns_body, alert.target)
    else                             -> stringResource(R.string.alert_rules_lost_detail)
}

private fun formatWhen(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
private fun AlertRow(
    alert: AlertEntity,
    expanded: Boolean,
    listName: String,
    siteBlocked: Boolean,
    onTap: () -> Unit,
    onBlockSite: () -> Unit,
    onNavigate: (AlertDestination) -> Unit
) {
    SecureCard(modifier = Modifier.clickable(onClick = onTap)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(severityColor(alert.severity)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(alertTitle(alert.type), fontSize = 13.5.sp,
                    fontWeight = if (alert.read) FontWeight.Medium else FontWeight.Bold,
                    color = SecurePalette.TextMain)
                Text(alertDetail(alert, listName), fontSize = 12.sp, color = SecurePalette.TextSoft,
                    lineHeight = 17.sp, maxLines = if (expanded) Int.MAX_VALUE else 2)
                Text(formatWhen(alert.timestampMs), fontSize = 11.sp, color = SecurePalette.TextFaint)
            }
            if (!alert.read) {
                Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(CyanPrimary))
            }
        }
        if (expanded) {
            when (alert.type) {
                AlertType.LOOKALIKE_WARNED ->
                    if (siteBlocked) Text(stringResource(R.string.alert_action_site_blocked), fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold, color = CyanPrimary)
                    else GradientButton(label = stringResource(R.string.alert_action_block_site), onClick = onBlockSite)
                AlertType.BLOCKLIST_FAILED ->
                    QuietButton(stringResource(R.string.alert_action_open_blocklists), { onNavigate(AlertDestination.BLOCKLISTS) }, CyanPrimary)
                AlertType.PROTECTION_STOPPED, AlertType.PROTECTION_NOT_STARTED ->
                    QuietButton(stringResource(R.string.alert_action_open_home), { onNavigate(AlertDestination.HOME) }, CyanPrimary)
                AlertType.RULES_LOST ->
                    QuietButton(stringResource(R.string.alert_action_open_backup), { onNavigate(AlertDestination.BACKUP) }, CyanPrimary)
                AlertType.PRIVATE_DNS_CONFLICT ->
                    QuietButton(stringResource(R.string.private_dns_open), { onNavigate(AlertDestination.NETWORK_SETTINGS) }, CyanPrimary)
            }
        }
    }
}
