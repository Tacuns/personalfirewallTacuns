package com.sentinel.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tacu.nsfwzerotrust.R
import com.sentinel.core.settings.AppPreferencesRepository
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.AlertsViewModel
import com.sentinel.ui.viewmodel.DashboardStatsViewModel
import com.sentinel.ui.viewmodel.FirewallViewModel
import kotlinx.coroutines.launch

/**
 * The Security tab: the same 5 real checks behind the Home score, each with a way to fix it where
 * one exists, plus unread alerts and the size of the threat database. Built with the main-tab theme
 * (same cards as Home). Nothing here is computed differently from Home or the Security Checklist.
 */
@Composable
fun SecurityScreen(
    onToggleVpn: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBlocklists: () -> Unit,
    onOpenAlerts: () -> Unit,
    firewallViewModel: FirewallViewModel = viewModel(),
    statsViewModel: DashboardStatsViewModel = viewModel(),
    alertsViewModel: AlertsViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferencesRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val noteSeen by prefs.mapMovedNoteSeen.collectAsState(initial = true)

    val isVpnRunning by firewallViewModel.isVpnRunning
    val stats by statsViewModel.stats.collectAsState()
    val alerts by alertsViewModel.alerts.collectAsState()
    val unread = alerts.count { !it.read }

    // Same five facts and thresholds as SecurityChecklistScreen and the Home score.
    val total = stats.blockedToday + stats.allowedToday
    val blockRate = if (total > 0) stats.blockedToday.toFloat() / total else 0f
    val checks = listOf(
        Check(R.string.checklist_item_vpn_label, R.string.checklist_item_vpn_detail, isVpnRunning,
            fix = onToggleVpn),
        Check(R.string.checklist_item_threat_db_label, R.string.checklist_item_threat_db_detail,
            stats.blocklistSize > 1_000, fix = onOpenBlocklists),
        Check(R.string.checklist_item_blocked_today_label, R.string.checklist_item_blocked_today_detail,
            stats.blockedToday > 0),
        Check(R.string.checklist_item_autostart_label, R.string.checklist_item_autostart_detail,
            stats.bootEnabled, fix = onOpenSettings),
        Check(R.string.checklist_item_blockrate_label, R.string.checklist_item_blockrate_detail,
            blockRate > 0f)
    )
    val passed = checks.count { it.passed }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        if (!noteSeen) {
            MapMovedNote(onDismiss = { scope.launch { prefs.setMapMovedNoteSeen() } })
            Spacer(Modifier.height(16.dp))
        }

        SecurityHeader()
        Spacer(Modifier.height(20.dp))

        ScoreCard(passed = passed, total = checks.size)
        Spacer(Modifier.height(20.dp))

        SecuritySectionHeader(stringResource(R.string.security_checks_title))
        Spacer(Modifier.height(8.dp))
        Card {
            checks.forEachIndexed { i, check ->
                CheckRow(check)
                if (i < checks.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = BgBorder.copy(alpha = 0.5f), thickness = 0.5.dp
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        SecuritySectionHeader(stringResource(R.string.security_alerts_title))
        Spacer(Modifier.height(8.dp))
        Card {
            ActionRow(
                text = if (unread > 0) stringResource(R.string.security_alerts_new, unread)
                       else stringResource(R.string.security_alerts_none),
                color = if (unread > 0) AmberMedium else TextSecondary,
                action = stringResource(R.string.security_see_all),
                onAction = onOpenAlerts
            )
        }
        Spacer(Modifier.height(20.dp))

        SecuritySectionHeader(stringResource(R.string.security_blocklists_title))
        Spacer(Modifier.height(8.dp))
        Card {
            ActionRow(
                text = stringResource(R.string.security_blocklists_line, "%,d".format(stats.blocklistSize)),
                color = TextSecondary,
                action = stringResource(R.string.security_manage),
                onAction = onOpenBlocklists
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class Check(val label: Int, val detail: Int, val passed: Boolean, val fix: (() -> Unit)? = null)

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp)),
        content = content
    )
}

@Composable
private fun SecurityHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(CyanPrimary, BlueAccent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.security_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = stringResource(R.string.security_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun SecuritySectionHeader(title: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CyanPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun ScoreCard(passed: Int, total: Int) {
    val color = when {
        passed == total -> GreenSafe
        passed >= 3     -> AmberMedium
        else            -> RedCritical
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (passed == total) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = stringResource(R.string.security_checks_passed, passed, total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun CheckRow(check: Check) {
    val color = if (check.passed) GreenSafe else AmberHigh
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(check.label),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(check.detail),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        if (!check.passed && check.fix != null) {
            TextButton(onClick = check.fix, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.security_fix), color = CyanPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ActionRow(text: String, color: Color, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(action, color = CyanPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MapMovedNote(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CyanPrimary.copy(alpha = 0.10f))
            .border(1.dp, CyanPrimary.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.security_map_moved),
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.security_got_it), color = CyanPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}
