package com.sentinel.ui.dashboard

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.core.logs.StatRow
import com.sentinel.ui.components.HelpBottomSheet
import com.sentinel.ui.components.HelpContent
import com.sentinel.ui.components.HelpDialog
import com.sentinel.ui.components.HelpIcon
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.DashboardStatsViewModel
import com.sentinel.ui.viewmodel.FirewallViewModel
import com.tacu.nsfwzerotrust.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun fmt(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fK".format(n / 1_000.0)
    else           -> n.toString()
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    onToggleVpn: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    viewModel: FirewallViewModel = viewModel(),
    statsViewModel: DashboardStatsViewModel = viewModel()
) {
    val isVpnRunning by viewModel.isVpnRunning
    val stats        by statsViewModel.stats.collectAsState()
    // Blocklists can be turned on or off on another screen; read the current state each time
    // Home is shown, so the Ad & Tracker row never shows an old one.
    LaunchedEffect(Unit) { statsViewModel.refresh() }
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()

    val hasData = stats.blockedToday > 0 || stats.allowedToday > 0 || stats.blocklistSize > 0

    var showMetricsHelp          by remember { mutableStateOf(false) }
    var showSecurityScoreHelp    by remember { mutableStateOf(false) }
    var showWeeklyReportHelp     by remember { mutableStateOf(false) }
    var showTimelineHelp         by remember { mutableStateOf(false) }
    var showThreatPreventionHelp by remember { mutableStateOf(false) }
    var showRecentThreatsHelp    by remember { mutableStateOf(false) }
    var showActiveAppsHelp       by remember { mutableStateOf(false) }
    // The score, reports and charts sit under "More insights" (progressive disclosure);
    // whether it is open is remembered. The score also has its own Security tab.
    val prefs            = remember { com.sentinel.core.settings.AppPreferencesRepository(context.applicationContext) }
    val insightsOpen     by prefs.moreToolsOpen("home").collectAsState(initial = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ──────────────────────────────────────────────────────────
        DashboardHeader(isVpnRunning)

        Spacer(Modifier.height(16.dp))

        // ── VPN Status Card ──────────────────────────────────────────────────
        VpnStatusCard(isVpnRunning, onToggleVpn)

        Spacer(Modifier.height(12.dp))

        // ── Rules lost warning — only after a damaged rules file was reset ────
        var rulesLost by remember { mutableStateOf(com.sentinel.core.rules.RulesLossNotice.isRaised(context)) }
        if (rulesLost) {
            RulesLostCard(onDismiss = {
                com.sentinel.core.rules.RulesLossNotice.dismiss(context)
                rulesLost = false
            })
            Spacer(Modifier.height(12.dp))
        }

        // ── 2×2 Metric Grid ──────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text          = stringResource(R.string.snapshot_section_title),
                style         = MaterialTheme.typography.labelSmall,
                color         = TextMuted,
                letterSpacing = 1.sp,
                modifier      = Modifier.weight(1f)
            )
            HelpIcon(onClick = { showMetricsHelp = true })
        }
        Spacer(Modifier.height(8.dp))
        if (showMetricsHelp) {
            HelpDialog(content = MetricsHelp(), onDismiss = { showMetricsHelp = false })
        }
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier   = Modifier.weight(1f),
                label      = stringResource(R.string.metric_threats_blocked),
                value      = fmt(stats.blockedToday),
                sub        = stringResource(R.string.metric_today),
                subColor   = RedCritical,
                icon       = Icons.Default.Shield,
                iconColor  = RedCritical,
                iconBg     = BgCritical
            )
            MetricCard(
                modifier  = Modifier.weight(1f),
                label     = stringResource(R.string.metric_connections),
                value     = fmt(stats.allowedToday),
                sub       = stringResource(R.string.metric_allowed_today),
                subColor  = GreenSafe,
                icon      = Icons.Default.CheckCircle,
                iconColor = GreenSafe,
                iconBg    = BgActive
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier  = Modifier.weight(1f),
                label     = stringResource(R.string.metric_threat_db),
                value     = fmt(stats.blocklistSize),
                sub       = stringResource(R.string.metric_known_domains),
                subColor  = CyanPrimary,
                icon      = Icons.Default.Storage,
                iconColor = CyanPrimary,
                iconBg    = Color(0xFF062028)
            )
            MetricCard(
                modifier  = Modifier.weight(1f),
                label     = stringResource(R.string.metric_dns_requests),
                value     = fmt(stats.blockedToday + stats.allowedToday),
                sub       = stringResource(R.string.metric_total_today),
                subColor  = BlueAccent,
                icon      = Icons.Default.NetworkCheck,
                iconColor = BlueAccent,
                iconBg    = Color(0xFF06122A)
            )
        }

        // ── Recent Blocked Threats ─────────────────────────────────────────────
        if (stats.topBlockedDomains.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader(
                title = stringResource(R.string.section_recent_threats),
                badge = null,
                onHelpClick = { showRecentThreatsHelp = true }
            )
            Spacer(Modifier.height(8.dp))
            ThreatAlertsCard(stats.topBlockedDomains)
            if (showRecentThreatsHelp) {
                HelpDialog(content = RecentThreatsHelp(), onDismiss = { showRecentThreatsHelp = false })
            }
        }

        // ── Security tab link + More insights ─────────────────────────────────
        Spacer(Modifier.height(20.dp))
        SecurityLinkRow(onClick = onOpenSecurity)
        Spacer(Modifier.height(12.dp))
        com.sentinel.ui.components.MoreToolsToggle(
            open      = insightsOpen,
            summary   = listOf(
                stringResource(R.string.security_score_help_title),
                stringResource(R.string.weekly_help_title),
                stringResource(R.string.timeline_help_title),
                stringResource(R.string.threat_prevention_help_title),
                stringResource(R.string.active_apps_help_title)
            ).joinToString(" · "),
            onToggle  = { scope.launch { prefs.setMoreToolsOpen("home", !insightsOpen) } },
            title     = stringResource(R.string.more_insights_title),
            showLabel = stringResource(R.string.cd_more_insights_show),
            hideLabel = stringResource(R.string.cd_more_insights_hide)
        )

        if (insightsOpen) {
            // ── Security Posture Score ────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            SectionHeader(
                title = stringResource(R.string.section_security_posture),
                badge = null,
                onHelpClick = { showSecurityScoreHelp = true }
            )
            Spacer(Modifier.height(8.dp))
            SecurityScoreCard(
                score         = stats.securityScore,
                grade         = stats.scoreGrade,
                isVpnRunning  = isVpnRunning,
                blocklistSize = stats.blocklistSize,
                blockedToday  = stats.blockedToday,
                allowedToday  = stats.allowedToday,
                bootEnabled   = stats.bootEnabled,
                protectionLevel = stats.protectionLevel
            )
            if (showSecurityScoreHelp) {
                HelpBottomSheet(content = SecurityScoreHelp(), onDismiss = { showSecurityScoreHelp = false })
            }

            // ── Weekly Privacy Report ─────────────────────────────────────────────
            if (stats.weeklyBlocked > 0 || stats.weeklyAllowed > 0) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(
                    title = stringResource(R.string.section_weekly_report),
                    badge = null,
                    onHelpClick = { showWeeklyReportHelp = true }
                )
                Spacer(Modifier.height(8.dp))
                WeeklyReportCard(
                    stats    = stats,
                    onExport = {
                        scope.launch {
                            exportWeeklyReport(context, stats)
                        }
                    }
                )
                if (showWeeklyReportHelp) {
                    HelpDialog(content = WeeklyReportHelp(), onDismiss = { showWeeklyReportHelp = false })
                }
            }

            // ── 24h Activity Timeline ────────────────────────────────────────────
            if (hasData) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(
                    title = stringResource(R.string.section_timeline),
                    badge = null,
                    onHelpClick = { showTimelineHelp = true }
                )
                Spacer(Modifier.height(8.dp))
                ThreatTimelineCard(
                    hourlyBlocked = stats.hourlyBlocked,
                    hourlyAllowed = stats.hourlyAllowed
                )
                if (showTimelineHelp) {
                    HelpDialog(content = TimelineHelp(), onDismiss = { showTimelineHelp = false })
                }
            }

            // ── Threat Prevention ─────────────────────────────────────────────────
            if (hasData) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(
                    title = stringResource(R.string.section_threat_prevention),
                    badge = if (stats.blockedToday > 0) stringResource(R.string.badge_x_blocked, fmt(stats.blockedToday)) else null,
                    badgeColor = RedCritical,
                    onHelpClick = { showThreatPreventionHelp = true }
                )
                Spacer(Modifier.height(8.dp))
                ThreatPreventionCard(stats.blocklistSize, stats.enabledBlocklists, stats.blockedToday, isVpnRunning)
                if (showThreatPreventionHelp) {
                    HelpDialog(content = ThreatPreventionHelp(), onDismiss = { showThreatPreventionHelp = false })
                }
            }

            // ── Most Active Apps ───────────────────────────────────────────────────
            if (stats.topApps.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(
                    title = stringResource(R.string.section_active_apps),
                    badge = null,
                    onHelpClick = { showActiveAppsHelp = true }
                )
                Spacer(Modifier.height(8.dp))
                ActiveAppsCard(stats.topApps)
                if (showActiveAppsHelp) {
                    HelpDialog(content = ActiveAppsHelp(), onDismiss = { showActiveAppsHelp = false })
                }
            }

        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SecurityScoreHelp() = HelpContent(
    title = stringResource(R.string.security_score_help_title),
    whatItIs = stringResource(R.string.security_score_help_what_it_is),
    whatItDoes = stringResource(R.string.security_score_help_what_it_does),
    why = stringResource(R.string.security_score_help_why),
    benefit = stringResource(R.string.security_score_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.security_score_help_practice_1),
        stringResource(R.string.security_score_help_practice_2),
        stringResource(R.string.security_score_help_practice_3),
        stringResource(R.string.security_score_help_practice_4),
        stringResource(R.string.security_score_help_practice_5)
    )
)

@Composable
private fun MetricsHelp() = HelpContent(
    title = stringResource(R.string.metrics_help_title),
    whatItIs = stringResource(R.string.metrics_help_what_it_is),
    whatItDoes = stringResource(R.string.metrics_help_what_it_does),
    why = stringResource(R.string.metrics_help_why),
    benefit = stringResource(R.string.metrics_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.metrics_help_practice_1)
    )
)

@Composable
private fun WeeklyReportHelp() = HelpContent(
    title = stringResource(R.string.weekly_help_title),
    whatItIs = stringResource(R.string.weekly_help_what_it_is),
    whatItDoes = stringResource(R.string.weekly_help_what_it_does),
    why = stringResource(R.string.weekly_help_why),
    benefit = stringResource(R.string.weekly_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.weekly_help_practice_1)
    )
)

@Composable
private fun TimelineHelp() = HelpContent(
    title = stringResource(R.string.timeline_help_title),
    whatItIs = stringResource(R.string.timeline_help_what_it_is),
    whatItDoes = stringResource(R.string.timeline_help_what_it_does),
    why = stringResource(R.string.timeline_help_why),
    benefit = stringResource(R.string.timeline_help_benefit),
    bestPractices = emptyList()
)

@Composable
private fun ThreatPreventionHelp() = HelpContent(
    title = stringResource(R.string.threat_prevention_help_title),
    whatItIs = stringResource(R.string.threat_prevention_help_what_it_is),
    whatItDoes = stringResource(R.string.threat_prevention_help_what_it_does),
    why = stringResource(R.string.threat_prevention_help_why),
    benefit = stringResource(R.string.threat_prevention_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.threat_prevention_help_practice_1)
    )
)

@Composable
private fun RecentThreatsHelp() = HelpContent(
    title = stringResource(R.string.recent_threats_help_title),
    whatItIs = stringResource(R.string.recent_threats_help_what_it_is),
    whatItDoes = stringResource(R.string.recent_threats_help_what_it_does),
    why = stringResource(R.string.recent_threats_help_why),
    benefit = stringResource(R.string.recent_threats_help_benefit),
    bestPractices = emptyList()
)

@Composable
private fun ActiveAppsHelp() = HelpContent(
    title = stringResource(R.string.active_apps_help_title),
    whatItIs = stringResource(R.string.active_apps_help_what_it_is),
    whatItDoes = stringResource(R.string.active_apps_help_what_it_does),
    why = stringResource(R.string.active_apps_help_why),
    benefit = stringResource(R.string.active_apps_help_benefit),
    bestPractices = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Header row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(isVpnRunning: Boolean) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon placeholder
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(listOf(CyanPrimary, BlueAccent))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Security,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = stringResource(R.string.dash_brand_title),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                letterSpacing = 0.5.sp
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isVpnRunning) GreenSafe else RedCritical)
                )
                Text(
                    text  = if (isVpnRunning) stringResource(R.string.dash_status_active) else stringResource(R.string.dash_status_offline),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isVpnRunning) GreenSafe else RedCritical
                )
                Text(
                    text  = stringResource(R.string.dash_subtitle_dns),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VPN Status Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RulesLostCard(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgHigh)
            .border(1.dp, AmberHigh.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = AmberHigh, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.rules_lost_title), fontSize = 15.sp,
                 fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.rules_lost_body, stringResource(R.string.nav_rules)),
             fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.rules_lost_dismiss), color = AmberHigh, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VpnStatusCard(isVpnRunning: Boolean, onToggle: () -> Unit) {
    val borderColor = if (isVpnRunning) GreenSafe.copy(alpha = 0.35f)
                      else RedCritical.copy(alpha = 0.3f)
    val statusBg    = if (isVpnRunning) BgActive else BgCritical
    var showHelp    by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Top row: status + toggle button
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status dot + text
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusBg),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (isVpnRunning) GreenSafe else RedCritical)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = if (isVpnRunning) stringResource(R.string.vpn_active_title) else stringResource(R.string.vpn_offline_title),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (isVpnRunning) GreenSafe else RedCritical
                    )
                    Text(
                        text  = if (isVpnRunning)
                            stringResource(R.string.vpn_active_desc)
                        else
                            stringResource(R.string.vpn_offline_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Toggle button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isVpnRunning) GreenSafe.copy(alpha = 0.15f)
                            else RedCritical.copy(alpha = 0.15f)
                        )
                        .border(
                            1.dp,
                            if (isVpnRunning) GreenSafe.copy(alpha = 0.4f)
                            else RedCritical.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggle() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text       = if (isVpnRunning) stringResource(R.string.vpn_toggle_on) else stringResource(R.string.vpn_toggle_off),
                        fontWeight = FontWeight.Black,
                        fontSize   = 14.sp,
                        color      = if (isVpnRunning) GreenSafe else RedCritical
                    )
                }
            }

            // Divider
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            // Bottom row: mode info badges
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoBadge(icon = Icons.Default.VpnKey, text = stringResource(R.string.badge_vpn_firewall))
                InfoBadge(icon = Icons.Default.Dns,    text = stringResource(R.string.badge_dns_filter))
                InfoBadge(icon = Icons.Default.PhoneAndroid, text = stringResource(R.string.badge_no_root))
                Spacer(Modifier.weight(1f))
                HelpIcon(onClick = { showHelp = true })
            }
        }
    }

    if (showHelp) {
        HelpDialog(content = VpnStatusHelp(), onDismiss = { showHelp = false })
    }
}

@Composable
private fun VpnStatusHelp() = HelpContent(
    title = stringResource(R.string.badge_vpn_firewall),
    whatItIs = stringResource(R.string.vpn_help_what_it_is),
    whatItDoes = stringResource(R.string.vpn_help_what_it_does),
    why = stringResource(R.string.vpn_help_why),
    benefit = stringResource(R.string.vpn_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.vpn_help_practice_1)
    )
)

@Composable
private fun InfoBadge(icon: ImageVector, text: String) {
    Row(
        modifier          = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BgBorder.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = TextMuted,
            modifier           = Modifier.size(11.dp)
        )
        Text(
            text      = text,
            fontSize  = 10.sp,
            color     = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Metric Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricCard(
    modifier:  Modifier,
    label:     String,
    value:     String,
    sub:       String,
    subColor:  Color,
    icon:      ImageVector,
    iconColor: Color,
    iconBg:    Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            // Label + icon row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Text(
                    text     = label,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = iconColor,
                        modifier           = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Value
            Text(
                text       = value,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Black,
                color      = TextPrimary,
                lineHeight = 30.sp
            )

            Spacer(Modifier.height(4.dp))

            // Sub label
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(subColor)
                )
                Text(
                    text  = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = subColor
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    badge: String?,
    badgeColor: Color = RedCritical,
    onHelpClick: (() -> Unit)? = null
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent line
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CyanPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text          = title,
            style         = MaterialTheme.typography.labelSmall,
            color         = TextSecondary,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier      = Modifier.weight(1f)
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(0.5.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text       = badge,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = badgeColor
                )
            }
        }
        if (onHelpClick != null) {
            HelpIcon(onClick = onHelpClick)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Threat Prevention Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThreatPreventionCard(blocklistSize: Int, enabledBlocklists: Int, blockedToday: Int, isVpnRunning: Boolean) {
    val adState = adTrackerRowState(blocklistSize, enabledBlocklists, isVpnRunning)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            // Card header
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF062028)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Security,
                        contentDescription = null,
                        tint               = CyanPrimary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = stringResource(R.string.threat_prevention_title),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary,
                    modifier   = Modifier.weight(1f)
                )
                if (blockedToday > 0) {
                    SeverityBadge(text = stringResource(R.string.badge_x_blocked, fmt(blockedToday)), color = RedCritical)
                }
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            // Row 1 — Ad & Tracker Blocking
            ThreatPreventionRow(
                icon      = Icons.Default.Block,
                iconColor = AmberMedium,
                iconBg    = BgMedium,
                title     = stringResource(R.string.row_ad_tracker_title),
                badge     = stringResource(when (adState) {
                    AdTrackerRowState.ACTIVE         -> R.string.badge_active
                    AdTrackerRowState.NOT_DOWNLOADED -> R.string.badge_not_ready
                    else                             -> R.string.badge_off
                }),
                badgeColor = when (adState) {
                    AdTrackerRowState.ACTIVE         -> CyanPrimary
                    AdTrackerRowState.NOT_DOWNLOADED -> AmberMedium
                    else                             -> TextMuted
                },
                engine    = stringResource(R.string.row_ad_tracker_engine),
                count     = when (adState) {
                    AdTrackerRowState.LISTS_OFF      -> stringResource(R.string.row_ad_tracker_off)
                    AdTrackerRowState.NOT_DOWNLOADED -> stringResource(R.string.row_ad_tracker_empty)
                    else                             -> stringResource(R.string.row_ad_tracker_count, fmt(blocklistSize))
                },
                countColor = if (adState == AdTrackerRowState.ACTIVE) CyanPrimary else TextMuted,
                bg        = Color.Transparent
            )

            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 14.dp),
                color     = BgBorder.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // Row 2 — DNS Firewall
            ThreatPreventionRow(
                icon       = Icons.Default.Dns,
                iconColor  = BlueAccent,
                iconBg     = Color(0xFF06122A),
                title      = stringResource(R.string.row_dns_firewall_title),
                badge      = if (isVpnRunning) stringResource(R.string.badge_active) else stringResource(R.string.badge_off),
                badgeColor = if (isVpnRunning) GreenSafe else TextMuted,
                engine     = stringResource(R.string.row_dns_firewall_engine),
                count      = if (blockedToday > 0) stringResource(R.string.row_dns_firewall_count, fmt(blockedToday))
                             else stringResource(R.string.row_dns_monitoring),
                countColor = if (blockedToday > 0) RedCritical else TextMuted,
                bg         = Color.Transparent
            )

            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 14.dp),
                color     = BgBorder.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // Row 3 — Custom Rules
            ThreatPreventionRow(
                icon       = Icons.AutoMirrored.Filled.Rule,
                iconColor  = AmberHigh,
                iconBg     = BgHigh,
                title      = stringResource(R.string.row_custom_rules_title),
                badge      = stringResource(R.string.badge_user),
                badgeColor = AmberHigh,
                engine     = stringResource(R.string.row_custom_rules_engine),
                count      = stringResource(R.string.row_custom_rules_count),
                countColor = TextMuted,
                bg         = Color.Transparent
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ThreatPreventionRow(
    icon:       ImageVector,
    iconColor:  Color,
    iconBg:     Color,
    title:      String,
    badge:      String,
    badgeColor: Color,
    engine:     String,
    count:      String,
    countColor: Color,
    bg:         Color
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconColor,
                modifier           = Modifier.size(17.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
                SeverityBadge(text = badge, color = badgeColor)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text  = engine,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text       = count,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = countColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Threat Alerts Card (like Figma "Threat Alerts" panel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThreatAlertsCard(threats: List<StatRow>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            threats.forEachIndexed { i, row ->
                ThreatAlertRow(row)
                if (i < threats.lastIndex) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(horizontal = 14.dp),
                        color     = BgBorder.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreatAlertRow(row: StatRow) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(BgCritical.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Red X icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(RedCritical.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Block,
                contentDescription = null,
                tint               = RedCritical,
                modifier           = Modifier.size(16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = row.name,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text  = stringResource(R.string.threat_alert_source),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text       = "${row.count}×",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = RedCritical
            )
            SeverityBadge(text = stringResource(R.string.badge_blocked), color = RedCritical)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active Apps Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveAppsCard(apps: List<StatRow>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCardAlt)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text          = stringResource(R.string.table_header_application),
                    style         = MaterialTheme.typography.labelSmall,
                    color         = TextMuted,
                    letterSpacing = 1.sp,
                    modifier      = Modifier.weight(1f)
                )
                Text(
                    text          = stringResource(R.string.table_header_requests),
                    style         = MaterialTheme.typography.labelSmall,
                    color         = TextMuted,
                    letterSpacing = 1.sp
                )
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            apps.forEachIndexed { i, row ->
                AppActivityRow(row, i)
                if (i < apps.lastIndex) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(horizontal = 14.dp),
                        color     = BgBorder.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun AppActivityRow(row: StatRow, index: Int) {
    val dotColors = listOf(GreenSafe, CyanPrimary, BlueAccent)
    val dot       = dotColors.getOrElse(index) { TextMuted }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(
            text     = row.name,
            style    = MaterialTheme.typography.bodySmall,
            color    = TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text       = stringResource(R.string.app_activity_req, fmt(row.count)),
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color      = CyanPrimary
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Security Posture Score Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecurityScoreCard(
    score: Int,
    grade: DashboardStatsViewModel.ScoreGrade,
    isVpnRunning: Boolean,
    blocklistSize: Int,
    blockedToday: Int,
    allowedToday: Int,
    bootEnabled: Boolean,
    protectionLevel: String = ""
) {
    val gradeColor = when (grade) {
        DashboardStatsViewModel.ScoreGrade.OPTIMAL    -> GreenSafe
        DashboardStatsViewModel.ScoreGrade.PROTECTED  -> CyanPrimary
        DashboardStatsViewModel.ScoreGrade.MONITORING -> BlueAccent
        DashboardStatsViewModel.ScoreGrade.AT_RISK    -> AmberHigh
        DashboardStatsViewModel.ScoreGrade.OFFLINE    -> RedCritical
    }
    // Localized display text for the ScoreGrade enum — kept entirely in this UI layer;
    // DashboardStatsViewModel.kt's ScoreGrade.label (English, used nowhere else) is untouched.
    val gradeLabel = when (grade) {
        DashboardStatsViewModel.ScoreGrade.OPTIMAL    -> stringResource(R.string.grade_optimal)
        DashboardStatsViewModel.ScoreGrade.PROTECTED  -> stringResource(R.string.grade_protected)
        DashboardStatsViewModel.ScoreGrade.MONITORING -> stringResource(R.string.grade_monitoring)
        DashboardStatsViewModel.ScoreGrade.AT_RISK    -> stringResource(R.string.grade_at_risk)
        DashboardStatsViewModel.ScoreGrade.OFFLINE    -> stringResource(R.string.grade_offline)
    }
    val gradeDesc = when (grade) {
        DashboardStatsViewModel.ScoreGrade.OPTIMAL    -> stringResource(R.string.grade_desc_optimal)
        DashboardStatsViewModel.ScoreGrade.PROTECTED  -> stringResource(R.string.grade_desc_protected)
        DashboardStatsViewModel.ScoreGrade.MONITORING -> stringResource(R.string.grade_desc_monitoring)
        DashboardStatsViewModel.ScoreGrade.AT_RISK    -> stringResource(R.string.grade_desc_at_risk)
        DashboardStatsViewModel.ScoreGrade.OFFLINE    -> stringResource(R.string.grade_desc_offline)
    }

    val animatedScore by animateFloatAsState(
        targetValue    = score.toFloat(),
        animationSpec  = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label          = "securityScore"
    )

    val total           = blockedToday + allowedToday
    val blockRatePoints = if (total > 0)
        ((blockedToday.toFloat() / total) * 20).toInt().coerceAtMost(20) else 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circular arc gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.size(80.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW  = 7.dp.toPx()
                        val pad      = strokeW / 2f
                        val arcTL    = Offset(pad, pad)
                        val arcSz    = Size(size.width - strokeW, size.height - strokeW)
                        val startAng = 135f
                        val sweepAll = 270f

                        drawArc(
                            color      = BgBorder,
                            startAngle = startAng,
                            sweepAngle = sweepAll,
                            useCenter  = false,
                            topLeft    = arcTL,
                            size       = arcSz,
                            style      = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color      = gradeColor,
                            startAngle = startAng,
                            sweepAngle = sweepAll * (animatedScore / 100f),
                            useCenter  = false,
                            topLeft    = arcTL,
                            size       = arcSz,
                            style      = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = score.toString(),
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Black,
                            color      = gradeColor,
                            lineHeight = 22.sp
                        )
                        Text(
                            text       = stringResource(R.string.score_suffix),
                            fontSize   = 9.sp,
                            color      = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Grade info column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text          = gradeLabel,
                        fontSize      = 18.sp,
                        fontWeight    = FontWeight.Black,
                        color         = gradeColor,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = gradeDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(gradeColor)
                        )
                        Text(
                            text  = stringResource(R.string.score_protection_line, score),
                            style = MaterialTheme.typography.labelSmall,
                            color = gradeColor
                        )
                    }
                    // The protection level the current settings match (Settings › Protection).
                    if (protectionLevel.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = stringResource(R.string.protection_level_applied,
                                com.sentinel.ui.settings.profileName(
                                    com.sentinel.core.rules.ProtectionProfile.fromKey(protectionLevel))),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            Column(
                modifier            = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                ScoreFactorRow(
                    label    = stringResource(R.string.score_factor_vpn),
                    earned   = if (isVpnRunning) 30 else 0,
                    max      = 30,
                    achieved = isVpnRunning
                )
                ScoreFactorRow(
                    label    = stringResource(R.string.score_factor_threat_db),
                    earned   = if (blocklistSize > 1_000) 25 else 0,
                    max      = 25,
                    achieved = blocklistSize > 1_000
                )
                ScoreFactorRow(
                    label    = stringResource(R.string.score_factor_blocked_today),
                    earned   = if (blockedToday > 0) 15 else 0,
                    max      = 15,
                    achieved = blockedToday > 0
                )
                ScoreFactorRow(
                    label    = stringResource(R.string.score_factor_autostart),
                    earned   = if (bootEnabled) 10 else 0,
                    max      = 10,
                    achieved = bootEnabled
                )
                ScoreFactorRow(
                    label    = stringResource(R.string.score_factor_block_rate),
                    earned   = blockRatePoints,
                    max      = 20,
                    achieved = blockRatePoints > 0
                )
            }
        }
    }
}

@Composable
private fun ScoreFactorRow(label: String, earned: Int, max: Int, achieved: Boolean) {
    val dotColor   = if (achieved) GreenSafe else TextMuted
    val labelColor = if (achieved) TextPrimary else TextMuted
    val pointColor = if (achieved) GreenSafe else TextMuted

    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodySmall,
            color    = labelColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text       = "+$earned",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color      = pointColor
        )
        Text(
            text  = "/ $max",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Weekly Privacy Report Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeeklyReportCard(
    stats:    DashboardStatsViewModel.DashboardStats,
    onExport: () -> Unit
) {
    val total     = stats.weeklyBlocked + stats.weeklyAllowed
    val blockRate = if (total > 0) (stats.weeklyBlocked * 100 / total) else 0
    val amLabel = stringResource(R.string.time_am)
    val pmLabel = stringResource(R.string.time_pm)
    val hourLabel = if (stats.busiestHour >= 0) {
        val h = stats.busiestHour
        val amPm = if (h < 12) amLabel else pmLabel
        val h12  = if (h % 12 == 0) 12 else h % 12
        "${h12}:00 $amPm"
    } else "—"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            // Export button row
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = stringResource(R.string.weekly_last_7_days),
                    style      = MaterialTheme.typography.labelSmall,
                    color      = TextMuted,
                    modifier   = Modifier.weight(1f)
                )
                IconButton(
                    onClick  = onExport,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.FileDownload,
                        contentDescription = stringResource(R.string.cd_export_report),
                        tint               = CyanPrimary,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }

            // Stat chips row
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeeklyStatChip(
                    modifier = Modifier.weight(1f),
                    label    = stringResource(R.string.weekly_chip_blocked),
                    value    = fmt(stats.weeklyBlocked),
                    color    = RedCritical
                )
                WeeklyStatChip(
                    modifier = Modifier.weight(1f),
                    label    = stringResource(R.string.weekly_chip_allowed),
                    value    = fmt(stats.weeklyAllowed),
                    color    = GreenSafe
                )
                WeeklyStatChip(
                    modifier = Modifier.weight(1f),
                    label    = stringResource(R.string.weekly_chip_block_rate),
                    value    = "$blockRate%",
                    color    = if (blockRate >= 10) CyanPrimary else TextMuted
                )
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            // Detail rows
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stats.topDomain7d.isNotEmpty()) {
                    WeeklyDetailRow(
                        icon       = Icons.Default.Block,
                        iconColor  = RedCritical,
                        label      = stringResource(R.string.weekly_detail_top_domain),
                        value      = stats.topDomain7d,
                        badge      = "${stats.topDomain7dCount}×"
                    )
                }
                if (stats.mostTargetedApp.isNotEmpty()) {
                    WeeklyDetailRow(
                        icon       = Icons.Default.PhoneAndroid,
                        iconColor  = AmberHigh,
                        label      = stringResource(R.string.weekly_detail_most_targeted),
                        value      = stats.mostTargetedApp,
                        badge      = stringResource(R.string.weekly_badge_blocks_count, stats.mostTargetedAppCount)
                    )
                }
                if (stats.busiestHour >= 0) {
                    WeeklyDetailRow(
                        icon       = Icons.Default.Schedule,
                        iconColor  = CyanPrimary,
                        label      = stringResource(R.string.weekly_detail_peak_hour),
                        value      = hourLabel,
                        badge      = null
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyStatChip(modifier: Modifier, label: String, value: String, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = value,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Black,
                color      = color
            )
            Text(
                text          = label,
                fontSize      = 8.sp,
                fontWeight    = FontWeight.Bold,
                color         = color.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun WeeklyDetailRow(
    icon:      ImageVector,
    iconColor: Color,
    label:     String,
    value:     String,
    badge:     String?
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 9.sp, color = TextMuted)
            Text(value, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(iconColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(badge, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = iconColor)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Threat Timeline Card — 24 hourly bars, blocked=red (bottom), allowed=cyan (top)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThreatTimelineCard(hourlyBlocked: List<Int>, hourlyAllowed: List<Int>) {
    val maxVal = remember(hourlyBlocked, hourlyAllowed) {
        (0..23).maxOf { i ->
            (hourlyBlocked.getOrElse(i) { 0 } + hourlyAllowed.getOrElse(i) { 0 })
        }.coerceAtLeast(1)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF06122A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint               = BlueAccent,
                        modifier           = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = stringResource(R.string.timeline_title),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary,
                    modifier   = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendDot(color = RedCritical, label = stringResource(R.string.legend_blocked))
                    LegendDot(color = CyanPrimary, label = stringResource(R.string.legend_allowed))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bar chart — 24 slots
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                val slotW = size.width / 24f
                val barW  = slotW * 0.72f
                val gap   = (slotW - barW) / 2f
                val maxH  = size.height

                for (i in 0..23) {
                    val blocked = hourlyBlocked.getOrElse(i) { 0 }
                    val allowed = hourlyAllowed.getOrElse(i) { 0 }
                    val total   = blocked + allowed
                    val x       = i * slotW + gap

                    if (total == 0) {
                        // Empty hour — draw a faint stub at the baseline
                        drawRect(
                            color   = BgBorder,
                            topLeft = Offset(x, maxH - 3f),
                            size    = Size(barW, 3f)
                        )
                    } else {
                        val totalH   = (total.toFloat() / maxVal) * maxH
                        val blockedH = (blocked.toFloat() / total) * totalH
                        val allowedH = totalH - blockedH

                        // Allowed portion (cyan, upper section)
                        if (allowedH > 0f) {
                            drawRect(
                                color   = CyanPrimary.copy(alpha = 0.45f),
                                topLeft = Offset(x, maxH - totalH),
                                size    = Size(barW, allowedH)
                            )
                        }
                        // Blocked portion (red, lower section)
                        if (blockedH > 0f) {
                            drawRect(
                                color   = RedCritical.copy(alpha = 0.85f),
                                topLeft = Offset(x, maxH - blockedH),
                                size    = Size(barW, blockedH)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            // Axis labels
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.timeline_24h_ago), fontSize = 9.sp, color = TextMuted)
                Spacer(Modifier.weight(1f))
                Text(text = stringResource(R.string.timeline_12h_ago), fontSize = 9.sp, color = TextMuted)
                Spacer(Modifier.weight(1f))
                Text(
                    text       = stringResource(R.string.timeline_now),
                    fontSize   = 9.sp,
                    color      = CyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 9.sp, color = TextMuted)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared: Severity Badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SeverityBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text          = text,
            fontSize      = 8.sp,
            fontWeight    = FontWeight.Black,
            color         = color,
            letterSpacing = 0.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Weekly report export — saves to Downloads + shows share sheet
// ─────────────────────────────────────────────────────────────────────────────

private suspend fun exportWeeklyReport(
    context: Context,
    stats:   DashboardStatsViewModel.DashboardStats
) {
    val dateFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val labelFmt  = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val today     = Date()
    val weekAgo   = Date(System.currentTimeMillis() - 7L * 24 * 3_600_000)
    val fileName  = "firewall-report-${dateFmt.format(today)}.txt"

    val total     = stats.weeklyBlocked + stats.weeklyAllowed
    val blockRate = if (total > 0) (stats.weeklyBlocked * 100 / total) else 0
    val hourLabel = if (stats.busiestHour >= 0) {
        val h    = stats.busiestHour
        val h12  = if (h % 12 == 0) 12 else h % 12
        val amPm = if (h < 12) context.getString(R.string.time_am) else context.getString(R.string.time_pm)
        "${h12}:00 $amPm"
    } else context.getString(R.string.report_no_data)

    val report = buildString {
        appendLine(context.getString(R.string.report_title_line))
        appendLine(context.getString(R.string.report_period, labelFmt.format(weekAgo), labelFmt.format(today)))
        appendLine()
        appendLine(context.getString(R.string.report_summary_header))
        appendLine("  " + context.getString(R.string.report_threats_blocked, stats.weeklyBlocked))
        appendLine("  " + context.getString(R.string.report_connections, stats.weeklyAllowed))
        appendLine("  " + context.getString(R.string.report_block_rate, blockRate))
        appendLine()
        appendLine(context.getString(R.string.report_top_threats_header))
        if (stats.topDomain7d.isNotEmpty())
            appendLine("  " + context.getString(R.string.report_most_blocked_domain, stats.topDomain7d, stats.topDomain7dCount))
        if (stats.mostTargetedApp.isNotEmpty())
            appendLine("  " + context.getString(R.string.report_most_targeted_app, stats.mostTargetedApp, stats.mostTargetedAppCount))
        appendLine("  " + context.getString(R.string.report_peak_hour, hourLabel))
        appendLine()
        appendLine(context.getString(R.string.report_footer))
    }

    // 1. Save to Downloads folder (no permission needed on API 29+)
    var savedOk = false
    withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            )
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(report.toByteArray())
                }
                savedOk = true
            }
        } catch (e: Exception) {
            android.util.Log.e("WeeklyReport", "Save failed: ${e.message}")
        }
    }

    // 2. Toast + share sheet on main thread (already on Main after withContext)
    if (savedOk) {
        Toast.makeText(context, context.getString(R.string.export_toast_saved, fileName), Toast.LENGTH_LONG).show()
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_subject))
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_share_chooser)))
}

/** One row on Home that opens the Security tab (the full score and checks live there). */
@Composable
private fun SecurityLinkRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.nav_security), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(stringResource(R.string.security_subtitle), style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CyanPrimary)
    }
}
