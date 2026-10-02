package com.sentinel.ui.logs

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.sp
import com.sentinel.core.logs.AppBlockNow
import com.sentinel.core.logs.CurrentRule
import com.sentinel.core.logs.LogWhy
import com.sentinel.core.logs.WhyNote
import com.sentinel.core.logs.LogReason
import com.sentinel.core.rules.DgaDetector
import com.sentinel.core.rules.DnsTunnelDetector
import com.sentinel.core.rules.DomainCategorizer
import com.sentinel.core.rules.LookalikeDomainDetector
import com.sentinel.core.rules.RuleActions
import com.sentinel.core.schedule.ScheduleCategories
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.theme.AmberMedium
import com.sentinel.ui.theme.GreenSafe
import com.sentinel.ui.theme.RedCritical
import com.sentinel.ui.viewmodel.LogViewModel
import com.tacu.nsfwzerotrust.R

/**
 * Details for one Activity entry: what happened, why, and how often the same website shows up
 * in the saved activity. Level 1 is the explanation; "See all entries" is level 2 — never more
 * (progressive disclosure). Read-only: it explains, it does not change any rule.
 *
 * [entry] is exactly what the tapped row showed, so the sheet always agrees with the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailSheet(
    entry: PacketLog,
    trustedDomains: Set<String>,
    viewModel: LogViewModel,
    logsVersion: Any?,
    onDismiss: () -> Unit,
    /** View-only App Lock account: no buttons (it can't change rules anywhere). */
    readOnly: Boolean = false,
    /** Always-allow [String] (the site as logged); the screen closes the sheet and offers Undo. */
    onAllow: (String) -> Unit = {},
    /** Block [String] (the site as logged); same flow. */
    onBlock: (String) -> Unit = {}
) {
    val context = LocalContext.current
    // The saved activity is read BEFORE the sheet opens, so it opens once with everything in it:
    // content that grows after opening is left below the screen edge. The read takes well under
    // a second, and NN/g's timing guidance is to show no loading indicator for that.
    // Re-read whenever the activity list changes, so the counts stay live while it is open.
    var loaded by remember(entry) { mutableStateOf(false) }
    var detail by remember(entry) { mutableStateOf<LogViewModel.LogDetail?>(null) }
    LaunchedEffect(entry, logsVersion) {
        detail = viewModel.loadDetail(entry.destination, entry.packageName)   // null only if the database can't be read
        loaded = true
    }
    var showAll by remember(entry) { mutableStateOf(false) }
    var showLookalikeWarning by remember(entry) { mutableStateOf(false) }
    if (!loaded) return

    val reason = remember(entry) { LogReason.of(entry.status, entry.threatLabel) }
    // Same checks, in the same order, as the badges on the row.
    val category = remember(entry) {
        if (entry.status == "ALLOWED") DomainCategorizer.categorize(entry.destination) else null
    }
    val lookalike = remember(entry, trustedDomains, category) {
        if (category == null) LookalikeDomainDetector.findLookalike(entry.destination, trustedDomains) else null
    }
    val isDga = remember(entry) { DgaDetector.isSuspect(entry.destination) }
    val isTunnel = remember(entry) { DnsTunnelDetector.isSuspect(entry.destination) }

    if (showLookalikeWarning) {
        // The one dangerous case gets a warning (NN/g error prevention): a fake site can take a
        // password before Undo would help. Everything else relies on Undo.
        AlertDialog(
            onDismissRequest = { showLookalikeWarning = false },
            containerColor = SecurePalette.GlassTop,
            title = { Text(stringResource(R.string.lookalike_confirm_title), color = SecurePalette.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    lookalike?.matchedTrustedDomain?.let { stringResource(R.string.lookalike_confirm_body, it) }
                        ?: stringResource(R.string.lookalike_confirm_body_generic),
                    color = SecurePalette.TextSoft, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showLookalikeWarning = false; onAllow(entry.destination) }) {
                    Text(stringResource(R.string.lookalike_confirm_allow), color = RedCritical, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLookalikeWarning = false }) {
                    Text(stringResource(R.string.btn_cancel), color = SecurePalette.TextMain)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Open fully: the explanation is short, and a half-open sheet hid "See all entries".
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The sheet is its own window and shows browsing history: block screenshots in it
        // explicitly instead of relying on inheriting the activity's flag.
        properties = ModalBottomSheetDefaults.properties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = SecurePalette.GlassTop,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SecurePalette.EdgeStrong)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── What happened ────────────────────────────────────────────────────
            item {
                val (statusText, statusColor, statusIcon) = when (reason) {
                    LogReason.WATCH_ONLY -> Triple(stringResource(R.string.log_detail_status_watch), AmberMedium, Icons.Default.Visibility)
                    LogReason.ALLOWED    -> Triple(stringResource(R.string.log_detail_status_allowed), GreenSafe, Icons.Default.Check)
                    else                 -> Triple(stringResource(R.string.log_detail_status_blocked), RedCritical, Icons.Default.Close)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                        Text(statusText, color = statusColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    // Selectable so a long name can be copied, e.g. to report a false block.
                    SelectionContainer {
                        Text(
                            entry.destination.ifBlank { stringResource(R.string.log_unknown_domain) },
                            color = SecurePalette.TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            // ── Why ──────────────────────────────────────────────────────────────
            item {
                // Today's rule names the exact list or rule — only when it's the same kind the
                // entry recorded; otherwise the general line plus an honest note (LogWhy.explain).
                val now = detail?.currentRule
                val why = now?.let { LogWhy.explain(reason, it) }
                val appBlock = detail?.appBlock
                DetailSection(stringResource(R.string.log_detail_why)) {
                    when {
                        reason == LogReason.APP_BLOCKED && appBlock != null ->
                            DetailText(appBlockText(entry, appBlock))
                        why?.specific == true && reason != LogReason.WATCH_ONLY ->
                            DetailText(ruleText(now, entry.destination))
                        else -> DetailText(reasonText(reason, entry, lookalike?.matchedTrustedDomain))
                    }
                    // Watch-only: also name what would have blocked it.
                    if (reason == LogReason.WATCH_ONLY && why?.specific == true) DetailText(ruleText(now, entry.destination))
                    // Allowed then, blocked today: say so and name the rule (the sheet offers no Block button then).
                    if (reason == LogReason.ALLOWED && (now is CurrentRule.OnList || now is CurrentRule.OwnRule ||
                            now is CurrentRule.Country || now is CurrentRule.Schedule)) {
                        DetailText(stringResource(R.string.log_why_blocked_now))
                        DetailText(ruleText(now, entry.destination))
                    }
                    when (val note = why?.note) {
                        is WhyNote.RuleGone     -> DetailText(stringResource(R.string.log_why_rule_gone))
                        is WhyNote.Changed      -> DetailText(stringResource(R.string.log_why_changed))
                        is WhyNote.AllowedSince -> DetailText(stringResource(R.string.log_why_allowed_since, note.ruleDomain))
                        else -> Unit
                    }
                }
            }

            // ── Hints the row shows as badges, in words ──────────────────────────
            val hints = buildList {
                if (lookalike != null && reason != LogReason.LOOKALIKE) add(HintKind.LOOKALIKE)
                if (isDga) add(HintKind.DGA)
                if (isTunnel) add(HintKind.TUNNEL)
                if (category != null) add(HintKind.CATEGORY)
            }
            if (hints.isNotEmpty()) {
                item {
                    DetailSection(stringResource(R.string.log_detail_good_to_know)) {
                        hints.forEach { hint ->
                            DetailText(
                                when (hint) {
                                    HintKind.LOOKALIKE -> stringResource(R.string.log_detail_hint_lookalike, lookalike!!.matchedTrustedDomain)
                                    HintKind.DGA       -> stringResource(R.string.log_detail_hint_dga)
                                    HintKind.TUNNEL    -> stringResource(R.string.log_detail_hint_tunnel)
                                    HintKind.CATEGORY  -> stringResource(R.string.log_detail_hint_category, categoryLabelText(category!!))
                                }
                            )
                        }
                    }
                }
            }

            // ── When, and which app (the tapped row) ─────────────────────────────
            item {
                DetailSection(stringResource(R.string.log_detail_when)) {
                    DetailText(
                        stringResource(
                            R.string.log_detail_app_and_time,
                            entry.appName.ifBlank { stringResource(R.string.log_unknown_app) },
                            formatDateTime(context, entry.timestampMs)
                        )
                    )
                }
            }

            // ── Everything saved about this website ──────────────────────────────
            val current = detail
            if (current != null) {
                val summary = current.summary
                val entries = current.entries
                item {
                    DetailSection(stringResource(R.string.log_detail_saved_activity)) {
                        if (summary.total == 0) {
                            DetailText(stringResource(R.string.log_detail_not_saved))
                        } else {
                            DetailText(stringResource(R.string.log_detail_counts, summary.blocked, summary.allowed))
                            DetailText(stringResource(R.string.log_detail_apps, summary.apps))
                            summary.firstMs?.let {
                                DetailText(stringResource(R.string.log_detail_first_seen, formatDateTime(context, it)))
                            }
                        }
                    }
                }
                if (entries.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = { showAll = !showAll },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text(
                                if (showAll) stringResource(R.string.log_detail_hide_all)
                                else stringResource(R.string.log_detail_see_all, summary.total),
                                color = SecurePalette.Orange, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                        }
                    }
                    if (showAll) {
                        items(entries.size) { i -> EntryLine(entries[i]) }
                        if (summary.total > entries.size) {
                            item { DetailText(stringResource(R.string.log_detail_showing_latest, entries.size)) }
                        }
                    }
                }
            }

            item {
                // One action at most (Hick's Law): allow what was blocked, block what was allowed.
                val now = detail?.currentRule
                val blocksNow = now is CurrentRule.OnList || now is CurrentRule.OwnRule ||
                    now is CurrentRule.Country || now is CurrentRule.Schedule
                val action = when {
                    readOnly -> SheetAction.NONE
                    reason == LogReason.APP_BLOCKED -> SheetAction.APPS_TAB_HINT
                    reason == LogReason.ALLOWED -> if (blocksNow) SheetAction.NONE else SheetAction.BLOCK
                    now is CurrentRule.Allowed -> SheetAction.NONE          // already always allowed
                    reason == LogReason.LOOKALIKE -> SheetAction.ALLOW_WITH_WARNING
                    else -> SheetAction.ALLOW
                }
                val blockName = remember(entry) { RuleActions.blockTarget(entry.destination) }
                val allowName = remember(entry) { RuleActions.allowTarget(entry.destination) }
                if (action == SheetAction.APPS_TAB_HINT) {
                    DetailText(stringResource(R.string.log_action_app_hint), color = SecurePalette.TextSoft)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var busy by remember(entry) { mutableStateOf(false) }   // no double taps
                    val primary: (() -> Unit)? = when (action) {
                        SheetAction.ALLOW -> allowName?.let { { busy = true; onAllow(entry.destination) } }
                        SheetAction.ALLOW_WITH_WARNING -> allowName?.let { { showLookalikeWarning = true } }
                        SheetAction.BLOCK -> blockName?.let { { busy = true; onBlock(entry.destination) } }
                        else -> null
                    }
                    if (primary != null) {
                        Button(
                            onClick = primary, enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp).weight(1f, fill = false),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SecurePalette.Orange, contentColor = Color.Black
                            )
                        ) {
                            Text(
                                when (action) {
                                    SheetAction.BLOCK -> stringResource(R.string.log_action_block, blockName ?: "")
                                    SheetAction.ALLOW_WITH_WARNING -> stringResource(R.string.log_action_allow_anyway)
                                    else -> stringResource(R.string.log_action_allow)
                                },
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.btn_close), color = SecurePalette.TextSoft, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private enum class HintKind { LOOKALIKE, DGA, TUNNEL, CATEGORY }

private enum class SheetAction { NONE, ALLOW, ALLOW_WITH_WARNING, BLOCK, APPS_TAB_HINT }

/** The sentence naming today's deciding rule for [site]. */
@Composable
private fun ruleText(now: CurrentRule, site: String): String = when (now) {
    is CurrentRule.OnList  -> stringResource(R.string.log_why_list_named, now.listName)
    is CurrentRule.OwnRule ->
        if (now.ruleDomain == site) stringResource(R.string.log_why_own_exact)
        else stringResource(R.string.log_why_own_parent, now.ruleDomain, site)
    is CurrentRule.Country -> stringResource(R.string.log_why_country, now.tld)
    is CurrentRule.Schedule -> stringResource(R.string.log_why_schedule_named, scheduleCategoryName(now.category))
    is CurrentRule.Allowed ->
        if (now.ruleDomain == site) stringResource(R.string.log_why_allowed_exact)
        else stringResource(R.string.log_why_allowed_parent, now.ruleDomain)
    CurrentRule.None -> stringResource(R.string.log_detail_why_allowed)
}

@Composable
private fun scheduleCategoryName(category: String?): String = when (category) {
    ScheduleCategories.SOCIAL    -> stringResource(R.string.schedule_cat_social)
    ScheduleCategories.STREAMING -> stringResource(R.string.schedule_cat_streaming)
    ScheduleCategories.GAMING    -> stringResource(R.string.schedule_cat_gaming)
    ScheduleCategories.NEWS      -> stringResource(R.string.schedule_cat_news)
    else -> category.orEmpty()
}

/** Whole-app block, as it is today, per network. */
@Composable
private fun appBlockText(entry: PacketLog, now: AppBlockNow): String {
    val app = entry.appName.ifBlank { stringResource(R.string.log_unknown_app) }
    return when {
        now.wifi && now.mobile -> stringResource(R.string.log_why_app_both, app)
        now.wifi               -> stringResource(R.string.log_why_app_wifi, app)
        now.mobile             -> stringResource(R.string.log_why_app_mobile, app)
        else                   -> stringResource(R.string.log_why_app_not_now, app)
    }
}

@Composable
private fun reasonText(reason: LogReason, entry: PacketLog, lookalikeOf: String?): String = when (reason) {
    LogReason.AD_LIST       -> stringResource(R.string.log_detail_why_ad_list)
    LogReason.USER_RULE     -> stringResource(R.string.log_detail_why_user_rule)
    LogReason.SCHEDULE      -> stringResource(R.string.log_detail_why_schedule)
    LogReason.LOOKALIKE     ->
        if (lookalikeOf != null) stringResource(R.string.log_detail_why_lookalike, lookalikeOf)
        else stringResource(R.string.log_detail_why_lookalike_generic)
    LogReason.APP_BLOCKED   -> stringResource(
        R.string.log_detail_why_app_blocked,
        entry.appName.ifBlank { stringResource(R.string.log_unknown_app) }
    )
    LogReason.WATCH_ONLY    -> stringResource(R.string.log_detail_why_watch_only)
    LogReason.ALLOWED       -> stringResource(R.string.log_detail_why_allowed)
    LogReason.BLOCKED_OTHER -> stringResource(R.string.log_detail_why_blocked_other)
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            color = SecurePalette.TextSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

@Composable
private fun DetailText(text: String, color: Color = SecurePalette.TextMain) {
    Text(text, color = color, fontSize = 14.sp, lineHeight = 20.sp)
}

/** One saved entry in the "See all" list: result, app, date and time. */
@Composable
private fun EntryLine(log: PacketLog) {
    val context = LocalContext.current
    val blocked = log.status == "BLOCKED"
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (blocked) Icons.Default.Close else Icons.Default.Check,
            contentDescription = stringResource(
                if (blocked) R.string.log_detail_status_blocked else R.string.log_detail_status_allowed
            ),
            tint = if (blocked) RedCritical else GreenSafe,
            modifier = Modifier.size(16.dp)
        )
        Text(
            log.appName.ifBlank { stringResource(R.string.log_unknown_app) },
            color = SecurePalette.TextMain, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(formatDateTime(context, log.timestampMs), color = SecurePalette.TextSoft, fontSize = 12.sp)
    }
}

/** Date and time in the app's language, e.g. "27 Sept, 10:42". */
private fun formatDateTime(context: android.content.Context, ms: Long): String =
    DateUtils.formatDateTime(
        context, ms,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    )
