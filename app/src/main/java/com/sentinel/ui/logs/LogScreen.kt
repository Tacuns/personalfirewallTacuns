package com.sentinel.ui.logs

import com.sentinel.core.logs.LogLabels
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.core.rules.DgaDetector
import com.sentinel.core.rules.DnsTunnelDetector
import com.sentinel.core.rules.DomainCategorizer
import com.sentinel.core.rules.LookalikeDomainDetector
import com.sentinel.core.rules.RuleActions
import com.sentinel.ui.lock.AppLockSession
import kotlinx.coroutines.launch
import com.sentinel.ui.components.HelpBottomSheet
import com.sentinel.ui.components.HelpContent
import com.sentinel.ui.components.HelpDialog
import com.sentinel.ui.components.HelpIcon
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.LogViewModel
import com.tacu.nsfwzerotrust.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Data Model — all fields have safe defaults, no nulls
// ─────────────────────────────────────────────────────────────────────────────

data class PacketLog(
    val appName: String = "Unknown",
    val packageName: String = "",
    val destination: String = "No domain info",
    val status: String = "UNKNOWN",           // "BLOCKED" or "ALLOWED"
    val networkType: String = "Unknown",
    val timestampMs: Long = System.currentTimeMillis(),
    val threatLabel: String = ""              // "AD/TRACKER", "USER BLOCK", or ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun relativeTime(tsMs: Long): String {
    val diff = System.currentTimeMillis() - tsMs
    return when {
        diff < 60_000L     -> stringResource(R.string.time_just_now)
        diff < 3_600_000L  -> stringResource(R.string.time_min_ago, diff / 60_000)
        diff < 86_400_000L -> stringResource(R.string.time_hr_ago, diff / 3_600_000)
        else               -> try { timeFmt.format(Date(tsMs)) } catch (e: Exception) { "--:--" }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter enum
// ─────────────────────────────────────────────────────────────────────────────

enum class LogFilter { ALL, BLOCKED, ALLOWED }

/**
 * A website name that wraps only after a dot ("android.googleapis." / "com") instead of
 * mid-word. Display only: a zero-width space follows each dot, so never copy or store this.
 */
internal fun breakAtDots(domain: String): String = domain.replace(".", ".\u200B")

// ─────────────────────────────────────────────────────────────────────────────
// Incident grouping — same destination repeated N times collapses into one row
// ─────────────────────────────────────────────────────────────────────────────

data class DomainIncident(
    val destination: String,
    val count: Int,
    val latestMs: Long,
    val status: String,
    val threatLabel: String
)

data class AppIncidentGroup(
    val appName: String,
    val representativeLog: PacketLog,
    val totalCount: Int,
    val incidents: List<DomainIncident>
)

// ─────────────────────────────────────────────────────────────────────────────
// Section Header (matches Dashboard / AppControl pattern exactly)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogSectionHeader(
    title: String,
    badge: String? = null,
    badgeColor: Color = CyanPrimary,
    onHelpClick: (() -> Unit)? = null
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
// Header Card (top of screen — matches AppControlHeader style)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogsHeaderCard(totalCount: Int) {
    var showHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Default.Security,
                contentDescription = null,
                tint               = CyanPrimary,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text          = stringResource(R.string.logs_header_title),
                    style         = MaterialTheme.typography.labelSmall,
                    color         = CyanPrimary,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text  = stringResource(R.string.logs_header_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            if (totalCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyanPrimary.copy(alpha = 0.10f))
                        .border(1.dp, CyanPrimary.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = stringResource(R.string.logs_events_badge, totalCount),
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color      = CyanPrimary
                    )
                }
            }
            HelpIcon(onClick = { showHelp = true })
        }
    }

    if (showHelp) {
        HelpDialog(content = eventBasedLoggingHelp(), onDismiss = { showHelp = false })
    }
}

@Composable
private fun eventBasedLoggingHelp() = HelpContent(
    title = stringResource(R.string.logs_help_title),
    whatItIs = stringResource(R.string.logs_help_what_it_is),
    whatItDoes = stringResource(R.string.logs_help_what_it_does),
    why = stringResource(R.string.logs_help_why),
    benefit = stringResource(R.string.logs_help_benefit),
    bestPractices = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen root — switches between grouped list and app detail view
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LogScreen(viewModel: LogViewModel = viewModel()) {
    // Result messages with Undo sit at the bottom of the Activity screen (Android snackbar pattern).
    val snackbarHost = remember { SnackbarHostState() }
    // Logs or map of the same activity; the map used to be its own tab.
    var showMap by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(BgDeep)) {
            ActivityViewSwitch(showMap = showMap, onShowMap = { showMap = it })
            Box(Modifier.weight(1f)) {
                if (showMap) com.sentinel.ui.map.MapScreen()
                else LogScreenBody(viewModel, snackbarHost)
            }
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

/** The Logs | Map choice at the top of Activity, styled like the filter chips below it. */
@Composable
private fun ActivityViewSwitch(showMap: Boolean, onShowMap: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(false to R.string.activity_view_list, true to R.string.nav_map).forEach { (isMap, label) ->
            FilterChip(
                selected = showMap == isMap,
                onClick  = { onShowMap(isMap) },
                label    = { Text(stringResource(label), fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = if (isMap) Icons.Default.Place else Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor   = CyanPrimary.copy(alpha = 0.2f),
                    selectedLabelColor       = CyanPrimary,
                    selectedLeadingIconColor = CyanPrimary,
                    containerColor           = Color.Transparent,
                    labelColor               = CyanPrimary.copy(alpha = 0.5f),
                    iconColor                = CyanPrimary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun LogScreenBody(viewModel: LogViewModel, snackbarHost: SnackbarHostState) {
    // The logs are the user's visited-domain history — keep this screen (and its
    // app-detail view) out of screenshots and the recents thumbnail.
    com.sentinel.ui.components.SecureScreen()

    val logs by viewModel.logsFlow.collectAsState()
    val loggingOff by viewModel.loggingOff.collectAsState()
    val trustedDomains by viewModel.trustedDomainsFlow.collectAsState()
    var activeFilter by remember { mutableStateOf(LogFilter.ALL) }
    var selectedApp  by remember { mutableStateOf<String?>(null) }
    var showBadgesHelp by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // The entry whose details sheet is open (main list or one app's list), or null.
    var detailOf by remember { mutableStateOf<PacketLog?>(null) }

    // Hardware back button exits detail view without leaving the Logs tab
    BackHandler(enabled = selectedApp != null) { selectedApp = null }

    val filtered = remember(logs, activeFilter) {
        try {
            when (activeFilter) {
                LogFilter.BLOCKED -> logs.filter { it.status == "BLOCKED" }
                LogFilter.ALLOWED -> logs.filter { it.status == "ALLOWED" }
                LogFilter.ALL     -> logs
            }
        } catch (e: Exception) { emptyList() }
    }

    // Block / allow from the details sheet: close the sheet, save, then offer Undo.
    val context = LocalContext.current
    val actionScope = rememberCoroutineScope()
    val onSheetAction: (Boolean, String) -> Unit = { allow, site ->
        if (!AppLockSession.blockChange(context)) {
            detailOf = null
            actionScope.launch {
                when (val result = if (allow) viewModel.allowSite(site) else viewModel.blockSite(site)) {
                    is RuleActions.Result.Done -> {
                        val shown = snackbarHost.showSnackbar(
                            message = context.getString(
                                if (allow) R.string.log_action_allowed_done else R.string.log_action_blocked_done,
                                result.undo.domain
                            ),
                            actionLabel = context.getString(R.string.btn_undo),
                            duration = SnackbarDuration.Long
                        )
                        if (shown == SnackbarResult.ActionPerformed) {
                            val undone = viewModel.undo(result.undo)
                            snackbarHost.showSnackbar(context.getString(
                                if (undone) R.string.log_action_undone else R.string.log_action_failed))
                        }
                    }
                    is RuleActions.Result.AllowListFull ->
                        snackbarHost.showSnackbar(context.getString(R.string.log_action_allow_full, result.max))
                    else -> snackbarHost.showSnackbar(context.getString(R.string.log_action_failed))
                }
            }
        }
    }

    detailOf?.let { entry ->
        LogDetailSheet(
            entry          = entry,
            trustedDomains = trustedDomains,
            viewModel      = viewModel,
            logsVersion    = logs,
            onDismiss      = { detailOf = null },
            readOnly       = AppLockSession.readOnly,
            onAllow        = { onSheetAction(true, it) },
            onBlock        = { onSheetAction(false, it) }
        )
    }

    if (selectedApp != null) {
        AppDetailScreen(
            appName = selectedApp!!,
            allLogs = logs,
            trustedDomains = trustedDomains,
            loggingOff = loggingOff,
            onBack  = { selectedApp = null },
            onOpenEntry = { detailOf = it }
        )
        return
    }

    val incidentGroups = remember(filtered) {
        try {
            filtered
                .groupBy { it.appName }
                .entries
                .sortedByDescending { (_, v) -> v.maxOf { it.timestampMs } }
                .map { (appName, appLogs) ->
                    val incidents = appLogs
                        .groupBy { it.destination }
                        .entries
                        .sortedByDescending { (_, v) -> v.size }
                        .map { (dest, destLogs) ->
                            DomainIncident(
                                destination = dest,
                                count       = destLogs.size,
                                latestMs    = destLogs.maxOf { it.timestampMs },
                                status      = destLogs.first().status,
                                threatLabel = destLogs.firstOrNull { it.threatLabel.isNotEmpty() }?.threatLabel ?: ""
                            )
                        }
                    AppIncidentGroup(
                        appName           = appName,
                        representativeLog = appLogs.maxByOrNull { it.timestampMs } ?: appLogs.first(),
                        totalCount        = appLogs.size,
                        incidents         = incidents
                    )
                }
        } catch (e: Exception) { emptyList() }
    }

    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .background(BgDeep),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Header card
        item {
            LogsHeaderCard(totalCount = logs.size)
            Spacer(Modifier.height(16.dp))
        }

        // Filter chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick  = { activeFilter = filter },
                        label    = {
                            Text(
                                text = when (filter) {
                                    LogFilter.ALL     -> stringResource(R.string.logs_filter_all)
                                    LogFilter.BLOCKED -> stringResource(R.string.logs_filter_blocked)
                                    LogFilter.ALLOWED -> stringResource(R.string.logs_filter_allowed)
                                },
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                            selectedLabelColor     = CyanPrimary,
                            containerColor         = Color.Transparent,
                            labelColor             = CyanPrimary.copy(alpha = 0.5f)
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                if (logs.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text(
                            text = stringResource(R.string.logs_clear_action),
                            color = AmberMedium,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (filtered.isEmpty()) {
            item { EmptyLogState(loggingOff = loggingOff) }
        } else {
            // Section header with live count badge
            item {
                LogSectionHeader(
                    title      = stringResource(R.string.section_recent_activity),
                    badge      = "${filtered.size}",
                    badgeColor = CyanPrimary,
                    onHelpClick = { showBadgesHelp = true }
                )
                Spacer(Modifier.height(10.dp))
                if (showBadgesHelp) {
                    HelpBottomSheet(content = logBadgesHelp(), onDismiss = { showBadgesHelp = false })
                }
            }

            // Grouped app headers + incident rows (same domain repeated N times → 1 row with ×N badge)
            incidentGroups.forEach { group ->
                item(key = "hdr_${group.appName}") {
                    AppGroupHeader(
                        log     = group.representativeLog,
                        count   = group.totalCount,
                        onClick = { selectedApp = group.appName }
                    )
                }
                itemsIndexed(
                    items = group.incidents,
                    key   = { index, incident -> "${group.appName}_${incident.destination}_$index" }
                ) { _, incident ->
                    IncidentRow(incident, trustedDomains) {
                        // Exactly what this row shows, so the sheet always agrees with it.
                        detailOf = PacketLog(
                            appName     = group.appName,
                            packageName = group.representativeLog.packageName,
                            destination = incident.destination,
                            status      = incident.status,
                            timestampMs = incident.latestMs,
                            threatLabel = incident.threatLabel
                        )
                    }
                }
                item(key = "gap_${group.appName}") { Spacer(Modifier.height(6.dp)) }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // Outside the LazyColumn on purpose: a dialog hosted inside a lazy item is
    // disposed when that item scrolls out of the viewport.
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor   = BgCard,
            title = {
                Text(stringResource(R.string.clear_logs_confirm_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(stringResource(R.string.clear_logs_confirm_text),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; viewModel.clearLogs() }) {
                    Text(stringResource(R.string.logs_clear_action), color = AmberMedium, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App Group Header — icon + name + count badge + chevron, fully clickable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppGroupHeader(log: PacketLog, count: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val iconBitmap = remember(log.packageName) {
        if (log.packageName.isBlank()) return@remember null
        try {
            val drawable: Drawable = context.packageManager.getApplicationIcon(log.packageName)
            drawable.toBitmap().asImageBitmap()
        } catch (e: Exception) { null }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App icon
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanPrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap             = iconBitmap,
                        contentDescription = log.appName,
                        modifier           = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector        = Icons.Default.Info,
                        contentDescription = null,
                        tint               = CyanPrimary.copy(alpha = 0.4f),
                        modifier           = Modifier.size(22.dp)
                    )
                }
            }

            // App name
            Text(
                text       = log.appName.ifBlank { stringResource(R.string.log_unknown_app) },
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                style      = MaterialTheme.typography.bodyMedium,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )

            // Domain count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyanPrimary.copy(alpha = 0.12f))
                    .border(0.5.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text       = "$count",
                    color      = CyanPrimary,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Chevron
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_view_details),
                tint               = CyanPrimary.copy(alpha = 0.5f),
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Domain Row — compact indented row shown under each app group header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DomainRow(log: PacketLog) {
    val isBlocked   = log.status == "BLOCKED"
    val statusColor = if (isBlocked) RedCritical else GreenSafe

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp, end = 4.dp, bottom = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text     = log.destination.ifBlank { stringResource(R.string.log_unknown_domain) },
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val shownLabel = LogLabels.displayLabel(log.status, log.threatLabel)
        if ((isBlocked || shownLabel == "WOULD BLOCK") && shownLabel.isNotEmpty()) {
            ThreatBadge(shownLabel)
        }
        Icon(
            imageVector        = if (isBlocked) Icons.Default.Close else Icons.Default.Check,
            contentDescription = null,
            tint               = statusColor,
            modifier           = Modifier.size(12.dp)
        )
        Text(
            text  = relativeTime(log.timestampMs),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Incident Row — compact indented row; shows ×N count badge when count > 1
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun IncidentRow(incident: DomainIncident, trustedDomains: Set<String>, onClick: () -> Unit) {
    val isBlocked   = incident.status == "BLOCKED"
    val statusColor = if (isBlocked) RedCritical else GreenSafe
    val isDga    = remember(incident.destination) { DgaDetector.isSuspect(incident.destination) }
    val isTunnel = remember(incident.destination) { DnsTunnelDetector.isSuspect(incident.destination) }
    val category = remember(incident.destination, incident.status) {
        if (incident.status == "ALLOWED") DomainCategorizer.categorize(incident.destination) else null
    }
    val lookalike = remember(incident.destination, trustedDomains, category) {
        if (category == null) LookalikeDomainDetector.findLookalike(incident.destination, trustedDomains) else null
    }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)   // Android's minimum touch target
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.log_detail_open)) { onClick() }
            .padding(start = 52.dp, end = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text     = breakAtDots(incident.destination.ifBlank { stringResource(R.string.log_unknown_domain) }),
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            maxLines = 2,   // the name matters most; with several badges one line cut it to "beacons.…"
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val shownLabel = LogLabels.displayLabel(incident.status, incident.threatLabel)
        if ((isBlocked || shownLabel == "WOULD BLOCK") && shownLabel.isNotEmpty()) {
            ThreatBadge(shownLabel)
        }
        if (isDga)       { DgaBadge()              }
        if (isTunnel)    { TunnelBadge()           }
        if (category != null) { CategoryBadge(category) }
        if (lookalike != null) { LookalikeBadge(lookalike.matchedTrustedDomain) }
        if (incident.count > 1) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(0.5.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text       = "×${incident.count}",
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color      = statusColor
                )
            }
        }
        Icon(
            imageVector        = if (isBlocked) Icons.Default.Close else Icons.Default.Check,
            contentDescription = stringResource(
                if (isBlocked) R.string.log_detail_status_blocked else R.string.log_detail_status_allowed
            ),
            tint               = statusColor,
            modifier           = Modifier.size(12.dp)
        )
        Text(
            text  = relativeTime(incident.latestMs),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint               = CyanPrimary.copy(alpha = 0.5f),
            modifier           = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun logBadgesHelp() = HelpContent(
    title = stringResource(R.string.log_badges_help_title),
    whatItIs = stringResource(R.string.log_badges_help_what_it_is),
    whatItDoes = stringResource(R.string.log_badges_help_what_it_does),
    why = stringResource(R.string.log_badges_help_why),
    benefit = stringResource(R.string.log_badges_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.log_badges_help_practice_1),
        stringResource(R.string.log_badges_help_practice_2),
        stringResource(R.string.log_badges_help_practice_3),
        stringResource(R.string.log_badges_help_practice_4),
        stringResource(R.string.log_badges_help_practice_5)
    )
)

@Composable
private fun DgaBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(AmberHigh.copy(alpha = 0.15f))
            .border(0.5.dp, AmberHigh.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text       = stringResource(R.string.badge_dga),
            fontSize   = 7.sp,
            fontWeight = FontWeight.Bold,
            color      = AmberHigh,
            maxLines   = 1
        )
    }
}

@Composable
private fun TunnelBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(RedCritical.copy(alpha = 0.15f))
            .border(0.5.dp, RedCritical.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text       = stringResource(R.string.badge_tunnel),
            fontSize   = 7.sp,
            fontWeight = FontWeight.Bold,
            color      = RedCritical,
            maxLines   = 1
        )
    }
}

@Composable
private fun LookalikeBadge(matchedDomain: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(RedThreat.copy(alpha = 0.15f))
            .border(0.5.dp, RedThreat.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text       = stringResource(R.string.badge_lookalike_prefix, matchedDomain),
            fontSize   = 7.sp,
            fontWeight = FontWeight.Bold,
            color      = RedThreat,
            maxLines   = 1
        )
    }
}

@Composable
internal fun categoryLabelText(category: DomainCategorizer.Category): String = when (category) {
    DomainCategorizer.Category.CRASH_RPT -> stringResource(R.string.category_crash_rpt)
    DomainCategorizer.Category.ANALYTICS -> stringResource(R.string.category_analytics)
    DomainCategorizer.Category.SOCIAL    -> stringResource(R.string.category_social)
    DomainCategorizer.Category.CDN       -> stringResource(R.string.category_cdn)
    DomainCategorizer.Category.TELEMETRY -> stringResource(R.string.category_telemetry)
    DomainCategorizer.Category.GOOGLE    -> stringResource(R.string.category_google)
}

@Composable
private fun CategoryBadge(category: DomainCategorizer.Category) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(category.color.copy(alpha = 0.13f))
            .border(0.5.dp, category.color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text       = categoryLabelText(category),
            fontSize   = 7.sp,
            fontWeight = FontWeight.Bold,
            color      = category.color,
            maxLines   = 1
        )
    }
}

@Composable
private fun threatLabelText(label: String): String = when (label) {
    "AD/TRACKER" -> stringResource(R.string.threat_ad_tracker)
    "USER BLOCK" -> stringResource(R.string.threat_user_block)
    "SCHEDULE"   -> stringResource(R.string.threat_schedule)
    "LOOK-ALIKE" -> stringResource(R.string.threat_lookalike)
    "WOULD BLOCK" -> stringResource(R.string.threat_would_block)
    LogLabels.APP_BLOCKED -> stringResource(R.string.threat_app_blocked)
    else         -> label
}

@Composable
private fun ThreatBadge(label: String) {
    val color = when (label) {
        "AD/TRACKER"  -> AmberHigh
        "WOULD BLOCK" -> AmberMedium
        else          -> RedCritical
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.18f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text       = threatLabelText(label),
            fontSize   = 7.sp,
            fontWeight = FontWeight.Bold,
            color      = color,
            maxLines   = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App Detail Screen — all domains for one app
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppDetailScreen(
    appName: String,
    allLogs: List<PacketLog>,
    trustedDomains: Set<String>,
    loggingOff: Boolean = false,
    onBack: () -> Unit,
    onOpenEntry: (PacketLog) -> Unit
) {
    val appLogs = remember(allLogs, appName) {
        allLogs.filter { it.appName == appName }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Back header card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
                .clickable { onBack() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint               = CyanPrimary,
                    modifier           = Modifier.size(22.dp)
                )
                Text(
                    text       = appName,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    style      = MaterialTheme.typography.titleMedium,
                    modifier   = Modifier.weight(1f),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text  = stringResource(R.string.logs_connections_count, appLogs.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (appLogs.isEmpty()) {
            EmptyLogState(loggingOff = loggingOff)
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = appLogs,
                    key   = { index, log -> "${log.timestampMs}_${log.destination}_$index" }
                ) { _, log ->
                    DetailDomainRow(log, trustedDomains) { onOpenEntry(log) }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail Domain Row — full-width card used inside AppDetailScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailDomainRow(log: PacketLog, trustedDomains: Set<String>, onClick: () -> Unit) {
    val isBlocked   = log.status == "BLOCKED"
    val statusColor = if (isBlocked) RedCritical else GreenSafe
    val cardBg      = if (isBlocked) BgCritical else BgCard
    val cardBorder  = if (isBlocked) RedCritical.copy(alpha = 0.18f) else BgBorder
    val isDga       = remember(log.destination) { DgaDetector.isSuspect(log.destination) }
    val isTunnel    = remember(log.destination) { DnsTunnelDetector.isSuspect(log.destination) }
    val category    = remember(log.destination, log.status) {
        if (log.status == "ALLOWED") DomainCategorizer.categorize(log.destination) else null
    }
    val lookalike = remember(log.destination, trustedDomains, category) {
        if (category == null) LookalikeDomainDetector.findLookalike(log.destination, trustedDomains) else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)   // Android's minimum touch target
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = stringResource(R.string.log_detail_open)) { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector        = if (isBlocked) Icons.Default.Close else Icons.Default.Check,
                contentDescription = stringResource(
                    if (isBlocked) R.string.log_detail_status_blocked else R.string.log_detail_status_allowed
                ),
                tint               = statusColor,
                modifier           = Modifier.size(14.dp)
            )
            Text(
                text     = breakAtDots(log.destination.ifBlank { stringResource(R.string.log_unknown_domain) }),
                style    = MaterialTheme.typography.bodySmall,
                color    = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val shownLabel = LogLabels.displayLabel(log.status, log.threatLabel)
            if ((isBlocked || shownLabel == "WOULD BLOCK") && shownLabel.isNotEmpty()) {
                ThreatBadge(shownLabel)
            }
            if (isDga)       { DgaBadge()              }
            if (isTunnel)    { TunnelBadge()           }
            if (category != null) { CategoryBadge(category) }
            if (lookalike != null) { LookalikeBadge(lookalike.matchedTrustedDomain) }
            Text(
                text  = relativeTime(log.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint               = CyanPrimary.copy(alpha = 0.5f),
                modifier           = Modifier.size(16.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyLogState(loggingOff: Boolean) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgCard)
                    .border(1.dp, BgBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Info,
                    contentDescription = null,
                    tint               = TextMuted,
                    modifier           = Modifier.size(32.dp)
                )
            }
            Text(
                text       = stringResource(
                    if (loggingOff) R.string.logs_empty_off_title else R.string.logs_empty_title
                ),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color      = TextSecondary
            )
            Text(
                text  = stringResource(
                    if (loggingOff) R.string.logs_empty_off_subtitle else R.string.logs_empty_subtitle
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
