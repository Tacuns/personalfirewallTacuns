package com.sentinel.ui.rules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import com.sentinel.core.rules.DomainCheck
import com.sentinel.core.schedule.ScheduleCategories
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sentinel.ui.components.HelpBottomSheet
import com.sentinel.ui.components.HelpContent
import com.sentinel.ui.components.HelpDialog
import com.sentinel.ui.components.HelpIcon
import com.sentinel.ui.components.MoreToolsToggle
import com.sentinel.core.settings.AppPreferencesRepository
import kotlinx.coroutines.launch
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.AppControlViewModel
import com.sentinel.ui.viewmodel.AppControlViewModelFactory
import com.sentinel.ui.viewmodel.AppAccessFilter
import com.sentinel.ui.viewmodel.matches
import com.sentinel.ui.viewmodel.sharedWithNames
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import com.tacu.nsfwzerotrust.R

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────

data class AppRule(
    val name: String,
    val packageName: String,
    val uid: Int,
    val isWifiBlocked: Boolean = false,
    val isDataBlocked: Boolean = false
)

data class GeoCountry(val name: String, val code: String, val tld: String)

val GEO_COUNTRIES = listOf(
    GeoCountry("China",       "CN", "cn"),
    GeoCountry("Russia",      "RU", "ru"),
    GeoCountry("Iran",        "IR", "ir"),
    GeoCountry("North Korea", "KP", "kp"),
    GeoCountry("Belarus",     "BY", "by"),
    GeoCountry("Nigeria",     "NG", "ng")
)

// ─────────────────────────────────────────────────────────────────────────────
// Rules Screen — Geo Blocking + Domain Blocking + Smart Suggestions
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: AppControlViewModel = viewModel(
        factory = AppControlViewModelFactory(LocalContext.current)
    )
) {
    LaunchedEffect(Unit) {
        viewModel.onScreenShown()   // lists may have changed on the Activity tab
        viewModel.loadRuleHits()
        // Rules may have changed on another screen since the last check.
        viewModel.clearDomainTest()
    }

    var domainInput        by remember { mutableStateOf("") }
    val blockedDomains     = viewModel.blockedDomains
    val suggestions        = viewModel.suggestedBlocks
    val keyboard           = LocalSoftwareKeyboardController.current
    val userBlockedDomains = blockedDomains.filter { it.contains('.') }
    val activeGeoCount     = GEO_COUNTRIES.count { blockedDomains.contains(it.tld) }
    val exportMsg          by viewModel.exportMessage
    val importMsg          by viewModel.importMessage
    val rateLimit          by viewModel.rateLimitState
    val schedule           by viewModel.scheduleState
    val domainTest         by viewModel.domainTestResult
    val canUndoRestore     by viewModel.canUndoRestore

    var showSuggestionsHelp   by remember { mutableStateOf(false) }
    var showGeoHelp           by remember { mutableStateOf(false) }
    var showDomainHelp        by remember { mutableStateOf(false) }
    var showScheduleHelp      by remember { mutableStateOf(false) }
    var showImportExportHelp  by remember { mutableStateOf(false) }
    var renameTarget          by remember { mutableStateOf<String?>(null) }
    var selectedDomains       by remember { mutableStateOf(emptySet<String>()) }
    var confirmUndo           by remember { mutableStateOf(false) }
    var showBackupDialog      by remember { mutableStateOf(false) }
    // Country blocking, schedules and backup sit under "More tools" (progressive disclosure);
    // whether it is open is remembered.
    val context            = LocalContext.current
    val prefs              = remember { AppPreferencesRepository(context.applicationContext) }
    val moreToolsScope     = rememberCoroutineScope()
    val moreToolsOpen      by prefs.moreToolsOpen("protect").collectAsState(initial = false)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importRules(it) } }

    LaunchedEffect(exportMsg) {
        if (exportMsg != null) { delay(5_000); viewModel.clearExportMessage() }
    }
    LaunchedEffect(importMsg) {
        if (importMsg != null) { delay(5_000); viewModel.clearImportMessage() }
    }

    LazyColumn(
        modifier        = Modifier.fillMaxSize().background(BgDeep),
        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "rules_header") {
            RulesScreenHeader(
                geoActive    = activeGeoCount,
                domainActive = userBlockedDomains.size
            )
            Spacer(Modifier.height(20.dp))
        }

        item(key = "domain_header") {
            AcSectionHeader(stringResource(R.string.section_domain_blocking),
                if (userBlockedDomains.isNotEmpty()) stringResource(R.string.badge_x_active, userBlockedDomains.size) else null,
                RedCritical,
                onHelpClick = { showDomainHelp = true })
            Spacer(Modifier.height(8.dp))
            if (showDomainHelp) {
                HelpDialog(content = DomainBlockingHelp(), onDismiss = { showDomainHelp = false })
            }
        }
        item(key = "domain_card") {
            DomainBlockCard(
                domainInput   = domainInput,
                onInputChange = { domainInput = it },
                onBlock       = { viewModel.blockDomain(domainInput); domainInput = ""; keyboard?.hide() },
                onTest        = { viewModel.testDomain(domainInput); keyboard?.hide() },
                testResult    = domainTest?.takeIf { it.input == domainInput },
                blockedDomains = userBlockedDomains,
                hits          = viewModel.ruleHits,
                onUnblock     = { viewModel.unblockDomain(it) },
                onRename      = { renameTarget = it },
                selected      = selectedDomains,
                onToggleSelect = { domain ->
                    selectedDomains = if (domain in selectedDomains) selectedDomains - domain
                                      else selectedDomains + domain
                },
                onDeleteSelected = {
                    viewModel.unblockDomains(selectedDomains)
                    selectedDomains = emptySet()
                },
                onClearSelection = { selectedDomains = emptySet() }
            )
            Spacer(Modifier.height(20.dp))
        }

        item(key = "allow_card") {
            AllowListCard(
                allowed  = viewModel.allowedDomains,
                onAllow  = { viewModel.allowDomain(it) },
                onRemove = { viewModel.removeAllowed(it) }
            )
            Spacer(Modifier.height(20.dp))
        }

        if (suggestions.isNotEmpty()) {
            item(key = "suggestions_header") {
                AcSectionHeader(stringResource(R.string.section_smart_suggestions), stringResource(R.string.badge_x_new, suggestions.size), AmberHigh,
                    onHelpClick = { showSuggestionsHelp = true })
                Spacer(Modifier.height(8.dp))
                if (showSuggestionsHelp) {
                    HelpDialog(content = SmartSuggestionsHelp(), onDismiss = { showSuggestionsHelp = false })
                }
            }
            item(key = "suggestions_card") {
                SmartSuggestionsCard(
                    suggestions = suggestions,
                    onBlock     = { viewModel.blockDomainFromSuggestion(it) },
                    onDismiss   = { viewModel.dismissSuggestion(it) }
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        item(key = "more_tools_toggle") {
            val summary = listOf(
                if (activeGeoCount > 0) stringResource(R.string.more_tools_country_on, activeGeoCount)
                else stringResource(R.string.more_tools_country_off),
                stringResource(if (schedule.enabled) R.string.more_tools_schedule_on else R.string.more_tools_schedule_off),
                stringResource(R.string.more_tools_backup)
            ).joinToString(" · ")
            MoreToolsToggle(
                open     = moreToolsOpen,
                summary  = summary,
                onToggle = { moreToolsScope.launch { prefs.setMoreToolsOpen("protect", !moreToolsOpen) } }
            )
            Spacer(Modifier.height(20.dp))
        }

        if (moreToolsOpen) {
            item(key = "geo_header") {
                AcSectionHeader(stringResource(R.string.section_geo_blocking),
                    if (activeGeoCount > 0) stringResource(R.string.badge_x_active, activeGeoCount) else null, RedCritical,
                    onHelpClick = { showGeoHelp = true })
                Spacer(Modifier.height(8.dp))
                if (showGeoHelp) {
                    HelpDialog(content = GeoBlockingHelp(), onDismiss = { showGeoHelp = false })
                }
            }
            item(key = "geo_card") {
                GeoBlockingCard(blockedDomains,
                    onBlock   = { viewModel.blockGeoTld(it) },
                    onUnblock = { viewModel.unblockGeoTld(it) })
                Spacer(Modifier.height(20.dp))
            }

            item(key = "schedule_header") {
                AcSectionHeader(
                    title      = stringResource(R.string.section_scheduled_blocking),
                    badge      = stringResource(R.string.schedule_badge_free),
                    badgeColor = if (schedule.enabled) GreenSafe else TextMuted,
                    onHelpClick = { showScheduleHelp = true }
                )
                Spacer(Modifier.height(8.dp))
                if (showScheduleHelp) {
                    HelpDialog(content = ScheduleBlockingHelp(), onDismiss = { showScheduleHelp = false })
                }
            }
            item(key = "schedule_card") {
                ScheduleCard(
                    state    = schedule,
                    onChange = { viewModel.updateSchedule(it) }
                )
                Spacer(Modifier.height(20.dp))
            }

            item(key = "import_export_header") {
                AcSectionHeader(stringResource(R.string.section_import_export), null, CyanPrimary,
                    onHelpClick = { showImportExportHelp = true })
                Spacer(Modifier.height(8.dp))
                if (showImportExportHelp) {
                    HelpDialog(content = ImportExportHelp(), onDismiss = { showImportExportHelp = false })
                }
            }
            item(key = "import_export_card") {
                ImportExportCard(
                    onExport   = { showBackupDialog = true },
                    onImport   = { importLauncher.launch(arrayOf("*/*")) },
                    exportMsg  = exportMsg,
                    importMsg  = importMsg,
                    rateLimit  = rateLimit,
                    canUndo    = canUndoRestore,
                    onUndo     = { confirmUndo = true }
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }

    // Outside the LazyColumn on purpose: a dialog hosted inside a lazy item is
    // disposed when that item scrolls out of the viewport.
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            containerColor   = BgCard,
            title = {
                Text(stringResource(R.string.restore_undo_confirm_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(stringResource(R.string.restore_undo_confirm_text),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = { confirmUndo = false; viewModel.undoLastRestore() }) {
                    Text(stringResource(R.string.btn_undo), color = AmberMedium, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUndo = false }) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        )
    }

    if (showBackupDialog) {
        BackupDialog(
            onDismiss = { showBackupDialog = false },
            onBackup  = { withHistory -> showBackupDialog = false; viewModel.exportRules(withHistory) }
        )
    }

    renameTarget?.let { original ->
        RenameDomainDialog(
            original  = original,
            onDismiss = { renameTarget = null },
            onConfirm = { updated ->
                viewModel.renameDomain(original, updated)
                renameTarget = null
            }
        )
    }
}

@Composable
private fun RenameDomainDialog(
    original:  String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(original) { mutableStateOf(original) }
    val trimmed = text.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != original

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = {
            Text(
                stringResource(R.string.rename_domain_title),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
        },
        text = {
            TextField(
                value         = text,
                onValueChange = { text = it },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors        = TextFieldDefaults.colors(
                    focusedContainerColor   = BgCardAlt,
                    unfocusedContainerColor = BgCardAlt,
                    focusedIndicatorColor   = CyanPrimary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary,
                    cursorColor             = CyanPrimary
                ),
                textStyle       = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(trimmed) })
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = canSave) {
                Text(
                    stringResource(R.string.btn_save),
                    color = if (canSave) CyanPrimary else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun SmartSuggestionsHelp() = HelpContent(
    title = stringResource(R.string.suggestions_help_title),
    whatItIs = stringResource(R.string.suggestions_help_what_it_is),
    whatItDoes = stringResource(R.string.suggestions_help_what_it_does),
    why = stringResource(R.string.suggestions_help_why),
    benefit = stringResource(R.string.suggestions_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.suggestions_help_practice_1)
    )
)

@Composable
private fun GeoBlockingHelp() = HelpContent(
    title = stringResource(R.string.geo_help_title),
    whatItIs = stringResource(R.string.geo_help_what_it_is),
    whatItDoes = stringResource(R.string.geo_help_what_it_does),
    why = stringResource(R.string.geo_help_why),
    benefit = stringResource(R.string.geo_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.geo_help_practice_1)
    )
)

@Composable
private fun DomainBlockingHelp() = HelpContent(
    title = stringResource(R.string.domain_help_title),
    whatItIs = stringResource(R.string.domain_help_what_it_is),
    whatItDoes = stringResource(R.string.domain_help_what_it_does),
    why = stringResource(R.string.domain_help_why),
    benefit = stringResource(R.string.domain_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.domain_help_practice_1),
        stringResource(R.string.domain_help_practice_2)
    )
)

@Composable
private fun ScheduleBlockingHelp() = HelpContent(
    title = stringResource(R.string.schedule_help_title),
    whatItIs = stringResource(R.string.schedule_help_what_it_is),
    whatItDoes = stringResource(R.string.schedule_help_what_it_does),
    why = stringResource(R.string.schedule_help_why),
    benefit = stringResource(R.string.schedule_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.schedule_help_practice_1)
    )
)

@Composable
private fun ImportExportHelp() = HelpContent(
    title = stringResource(R.string.import_export_help_title),
    whatItIs = stringResource(R.string.import_export_help_what_it_is),
    whatItDoes = stringResource(R.string.import_export_help_what_it_does),
    why = stringResource(R.string.import_export_help_why),
    benefit = stringResource(R.string.import_export_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.import_export_help_practice_1)
    )
)

@Composable
private fun RulesScreenHeader(geoActive: Int, domainActive: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RedCritical.copy(alpha = 0.12f))
                    .border(1.dp, RedCritical.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, null, tint = RedCritical, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.rules_screen_title), fontSize = 18.sp, fontWeight = FontWeight.Black,
                    color = TextPrimary, letterSpacing = 0.5.sp)
                Text(stringResource(R.string.rules_subtitle),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${geoActive + domainActive}", fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = if (geoActive + domainActive > 0) RedCritical else GreenSafe)
                Text(stringResource(R.string.rules_active_label), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App Block Screen — per-app Wi-Fi & data blocking rules
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppControlScreen(
    viewModel: AppControlViewModel = viewModel(
        factory = AppControlViewModelFactory(LocalContext.current)
    )
) {
    LaunchedEffect(Unit) { viewModel.loadRiskScores() }

    var searchQuery  by remember { mutableStateOf("") }
    val apps         = viewModel.appList
    val showSystem   by viewModel.showSystemApps
    val blockedAppCount by viewModel.blockedAppCount
    val sharedUidApps   by viewModel.sharedUidApps
    var accessFilter by rememberSaveable { mutableStateOf(AppAccessFilter.ALL) }
    val selectedApp  by viewModel.selectedApp
    val appProfile   = viewModel.selectedProfile.value
    var showBadgesHelp by remember { mutableStateOf(false) }

    val filteredApps by remember {
        derivedStateOf {
            apps.filter {
                it.matches(accessFilter) &&
                    (searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true))
            }
        }
    }

    LazyColumn(
        modifier        = Modifier.fillMaxSize().background(BgDeep),
        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        // ── Header panel ──────────────────────────────────────────────────────
        item(key = "header") {
            AppControlHeader(
                blockedCount = blockedAppCount
            )
            Spacer(Modifier.height(20.dp))
        }

        // ── App Rules header ──────────────────────────────────────────────────
        item(key = "apps_header") {
            AcSectionHeader(
                title      = stringResource(R.string.section_app_firewall_rules),
                badge      = blockedAppCount
                    .takeIf { it > 0 }?.let { stringResource(R.string.badge_x_blocked, it) },
                badgeColor = RedCritical,
                onHelpClick = { showBadgesHelp = true }
            )
            Spacer(Modifier.height(8.dp))
            if (showBadgesHelp) {
                HelpBottomSheet(content = AppBadgesHelp(), onDismiss = { showBadgesHelp = false })
            }
        }

        // Controls: search + system toggle
        item(key = "controls") {
            AppControlsBar(
                searchQuery    = searchQuery,
                onSearchChange = { searchQuery = it },
                showSystem     = showSystem,
                onToggleSystem = { viewModel.toggleSystemApps(it) },   // ← unchanged
                accessFilter   = accessFilter,
                onAccessFilter = { accessFilter = it }
            )
            Spacer(Modifier.height(12.dp))
        }

        // Hint row
        item(key = "hint") {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape).background(CyanPrimary.copy(alpha = 0.5f))
                )
                Text(
                    text  = stringResource(R.string.hint_tap_wifi_data),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Honest disclosure of a real platform limitation, shown only when it applies.
        // Official Android VpnService docs: "If the list includes one or more apps, then
        // only the apps in the list use the VPN. All other apps (that aren't in the list)
        // use the system networks as if the VPN isn't running." So while any app is
        // blocked, domain rules cannot reach the remaining apps. Surfacing this stops the
        // reduction in protection from being silent.
        item(key = "tunnel_scope_notice") {
            if (blockedAppCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberHigh.copy(alpha = 0.08f))
                        .border(1.dp, AmberHigh.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        tint = AmberHigh,
                        modifier = Modifier.size(14.dp).padding(top = 1.dp)
                    )
                    Text(
                        text  = stringResource(R.string.app_block_domain_scope_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (filteredApps.isEmpty() && apps.isNotEmpty() && accessFilter != AppAccessFilter.ALL) {
            item(key = "filter_empty") {
                Text(
                    text      = stringResource(R.string.apps_filter_empty),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            }
        }

        // ── App list — LAZY (one item per app, not forEachIndexed) ────────────
        items(filteredApps, key = { it.packageName }) { app ->
            AppCard(
                app               = app,
                sharedWith        = sharedWithNames(app.uid, app.packageName, sharedUidApps),
                riskCount         = viewModel.appRiskMap[app.packageName] ?: 0,
                queryRate         = viewModel.appQueryRateMap[app.packageName] ?: 0,
                anomalyRatio      = viewModel.appAnomalyMap[app.packageName] ?: 0f,
                beaconInterval    = viewModel.appBeaconMap[app.packageName] ?: 0,
                updateChangeRatio = viewModel.appUpdateChangeMap[app.packageName] ?: 0f,
                onClick           = { viewModel.loadAppProfile(app) },
                onPurge           = { viewModel.purgeApp(it) },
                onWifiToggle      = { viewModel.updatePolicy(app, it, app.isDataBlocked) },
                onDataToggle      = { viewModel.updatePolicy(app, app.isWifiBlocked, it) }
            )
            Spacer(Modifier.height(8.dp))
        }

        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }

    if (selectedApp != null) {
        AppProfileSheet(
            app       = selectedApp!!,
            profile   = appProfile,
            onDismiss = { viewModel.clearAppProfile() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppBadgesHelp() = HelpContent(
    title = stringResource(R.string.app_badges_help_title),
    whatItIs = stringResource(R.string.app_badges_help_what_it_is),
    whatItDoes = stringResource(R.string.app_badges_help_what_it_does),
    why = stringResource(R.string.app_badges_help_why),
    benefit = stringResource(R.string.app_badges_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.app_badges_help_practice_1),
        stringResource(R.string.app_badges_help_practice_2),
        stringResource(R.string.app_badges_help_practice_3),
        stringResource(R.string.app_badges_help_practice_4),
        stringResource(R.string.app_badges_help_practice_5),
        stringResource(R.string.app_badges_help_practice_6)
    )
)

@Composable
private fun AppControlHeader(blockedCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyanPrimary.copy(alpha = 0.12f))
                    .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Security,
                    contentDescription = null,
                    tint               = CyanPrimary,
                    modifier           = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text          = stringResource(R.string.appblock_title),
                    fontSize      = 18.sp,
                    fontWeight    = FontWeight.Black,
                    color         = TextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text  = stringResource(R.string.appblock_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "$blockedCount",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Black,
                    color      = if (blockedCount > 0) RedCritical else GreenSafe
                )
                Text(
                    text  = stringResource(R.string.appblock_blocked_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Domain Block Card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DomainBlockCard(
    domainInput:      String,
    onInputChange:    (String) -> Unit,
    onBlock:          () -> Unit,
    onTest:           () -> Unit,
    testResult:       AppControlViewModel.DomainTestResult?,
    blockedDomains:   List<String>,
    hits:             Map<String, Int>,
    onUnblock:        (String) -> Unit,
    onRename:         (String) -> Unit,
    selected:         Set<String>,
    onToggleSelect:   (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit
) {
    val selectionMode = selected.isNotEmpty()
    val hasInput      = domainInput.trim().isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text  = stringResource(R.string.domain_block_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TextField(
                        value           = domainInput,
                        onValueChange   = onInputChange,
                        placeholder     = { Text(stringResource(R.string.domain_input_hint), fontSize = 12.sp, color = TextMuted) },
                        singleLine      = true,
                        modifier        = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                        colors          = TextFieldDefaults.colors(
                            focusedContainerColor   = BgCardAlt,
                            unfocusedContainerColor = BgCardAlt,
                            focusedIndicatorColor   = CyanPrimary,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary,
                            cursorColor             = CyanPrimary
                        ),
                        textStyle       = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (domainInput.trim().isNotEmpty()) onBlock() })
                    )
                    val canBlock = domainInput.trim().isNotEmpty()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canBlock) RedCritical.copy(alpha = 0.15f) else BgBorder.copy(alpha = 0.4f))
                            .border(1.dp, if (canBlock) RedCritical.copy(alpha = 0.5f) else BgBorder, RoundedCornerShape(8.dp))
                            .clickable(enabled = canBlock) { onBlock() }
                            .padding(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        Text(stringResource(R.string.btn_block), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (canBlock) RedCritical else TextMuted)
                    }
                }
                // Read-only check: says whether the typed website is blocked, and why.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = hasInput) { onTest() }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = if (hasInput) CyanPrimary else TextMuted,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.domain_test_action), fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = if (hasInput) CyanPrimary else TextMuted)
                }
                testResult?.let { DomainTestResultLine(it) }
                // The phone briefly remembers sites it opened, so say plainly that a new block can lag.
                Text(stringResource(R.string.domain_block_delay_note), fontSize = 11.sp, color = TextMuted,
                    lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
            }

            if (blockedDomains.isNotEmpty()) {
                HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                if (selectionMode) {
                    // Selection action bar — replaces the list header while selecting.
                    Row(
                        modifier = Modifier.fillMaxWidth().background(BgCardAlt)
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.domains_selected, selected.size),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = CyanPrimary, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDeleteSelected) {
                            Text(stringResource(R.string.btn_delete), fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, color = RedCritical)
                        }
                        TextButton(onClick = onClearSelection) {
                            Text(stringResource(R.string.btn_cancel), fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(BgCardAlt)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.label_blocked_domains), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = TextMuted, letterSpacing = 0.8.sp)
                        Text("${blockedDomains.size}", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = RedCritical)
                    }
                }
                blockedDomains.forEachIndexed { index, domain ->
                    if (index > 0) HorizontalDivider(color = BgBorder, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 14.dp))
                    val isSelected = domain in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) CyanPrimary.copy(alpha = 0.08f) else Color.Transparent)
                            .combinedClickable(
                                onClick     = { if (selectionMode) onToggleSelect(domain) },
                                onLongClick = { onToggleSelect(domain) }
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectionMode) {
                            Checkbox(
                                checked         = isSelected,
                                onCheckedChange = { onToggleSelect(domain) },
                                modifier        = Modifier.size(18.dp),
                                colors          = CheckboxDefaults.colors(
                                    checkedColor   = CyanPrimary,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = BgDeep
                                )
                            )
                        } else {
                            Icon(Icons.Default.Block, null, tint = RedCritical.copy(0.5f),
                                modifier = Modifier.size(13.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(domain, fontSize = 13.sp, color = TextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val count = hits[domain] ?: 0
                            if (count > 0) {
                                Text(pluralStringResource(R.plurals.rule_hits_recent, count, count),
                                    fontSize = 10.sp, color = TextMuted)
                            }
                        }
                        if (!selectionMode) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(3.dp))
                                    .background(RedCritical.copy(0.10f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.badge_blocked), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = RedCritical) }
                            IconButton(onClick = { onRename(domain) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Edit, stringResource(R.string.cd_rename, domain), tint = TextMuted,
                                    modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { onUnblock(domain) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.cd_unblock, domain), tint = TextMuted,
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One plain sentence saying whether the tested website is blocked, and why. */
@Composable
private fun DomainTestResultLine(result: AppControlViewModel.DomainTestResult) {
    val domain = result.domain
    val rule   = result.rule
    val text = when {
        domain == null                    -> stringResource(R.string.domain_test_invalid)
        rule == null                      -> stringResource(R.string.domain_test_not_blocked, domain)
        DomainCheck.isAllowRule(rule)     -> stringResource(R.string.domain_test_allowed, domain)
        DomainCheck.isBlocklist(rule)     -> stringResource(R.string.domain_test_blocked_list, domain)
        rule.source == "SCHEDULE"         -> stringResource(R.string.domain_test_blocked_schedule, domain)
        !rule.domain.contains('.')        -> stringResource(R.string.domain_test_blocked_country, domain, rule.domain)
        else                              -> stringResource(R.string.domain_test_blocked_rule, domain, rule.domain)
    }
    val color = when {
        domain == null -> AmberMedium
        rule == null || DomainCheck.isAllowRule(rule) -> GreenSafe
        else           -> RedCritical
    }
    Text(text, fontSize = 12.sp, color = color, modifier = Modifier.padding(top = 2.dp))
    if (domain != null && !result.firewallOn) {
        Text(stringResource(R.string.domain_test_firewall_off), fontSize = 11.sp, color = TextMuted,
            modifier = Modifier.padding(top = 2.dp))
    }
    if (domain != null && result.watchOnly && rule != null && !DomainCheck.isAllowRule(rule)) {
        Text(stringResource(R.string.domain_test_watch_only), fontSize = 11.sp, color = AmberMedium,
            modifier = Modifier.padding(top = 2.dp))
    }
    if (rule != null && !DomainCheck.isAllowRule(rule) && result.firewallOn && result.appBlockActive) {
        Text(stringResource(R.string.domain_test_app_block_note), fontSize = 11.sp, color = AmberMedium,
            modifier = Modifier.padding(top = 2.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scheduled Blocking Card — Feature #16
// Free: 1 schedule, all categories, any time range. PRO: unlimited schedules.
// Worker (ScheduleCheckWorker) runs every 15 min via WorkManager — no VPN restart.
// ─────────────────────────────────────────────────────────────────────────────

// Localized display labels for ScheduleCategories' constant keys — kept entirely in this
// UI layer; core/schedule/ScheduleCategories.kt (the actual stored/matched values and
// domainsFor() logic) is untouched.
@Composable
private fun scheduleCategoryLabel(cat: String): String = when (cat) {
    ScheduleCategories.SOCIAL    -> stringResource(R.string.schedule_cat_social)
    ScheduleCategories.STREAMING -> stringResource(R.string.schedule_cat_streaming)
    ScheduleCategories.GAMING    -> stringResource(R.string.schedule_cat_gaming)
    ScheduleCategories.NEWS      -> stringResource(R.string.schedule_cat_news)
    else                         -> cat
}

@Composable
private fun ScheduleCard(
    state:    AppControlViewModel.ScheduleState,
    onChange: (AppControlViewModel.ScheduleState) -> Unit
) {
    val categoryColor = when (state.category) {
        ScheduleCategories.SOCIAL    -> Color(0xFF3B82F6)
        ScheduleCategories.STREAMING -> RedCritical
        ScheduleCategories.GAMING    -> Color(0xFFCE93D8)
        else                         -> AmberHigh
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(
                1.dp,
                if (state.enabled && state.activeNow) GreenSafe.copy(0.4f) else BgBorder,
                RoundedCornerShape(12.dp)
            )
    ) {
        Column {

            // ── Header: icon + title + enable toggle ──────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (state.enabled && state.activeNow)
                            GreenSafe.copy(alpha = 0.06f) else BgCard
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, null,
                    tint     = if (state.enabled) categoryColor else TextMuted,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.schedule_toggle_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.schedule_toggle_subtitle),
                        fontSize = 10.sp, color = TextMuted)
                }
                Switch(
                    checked         = state.enabled,
                    onCheckedChange = { onChange(state.copy(enabled = it)) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = BgDeep,
                        checkedTrackColor   = GreenSafe,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = BgBorder
                    )
                )
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Category chips ────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.label_category), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = TextMuted, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScheduleCategories.all.forEach { cat ->
                            val selected = state.category == cat
                            val chipColor = when (cat) {
                                ScheduleCategories.SOCIAL    -> Color(0xFF3B82F6)
                                ScheduleCategories.STREAMING -> RedCritical
                                ScheduleCategories.GAMING    -> Color(0xFFCE93D8)
                                else                          -> AmberHigh
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (selected) chipColor.copy(alpha = 0.18f)
                                        else BgCardAlt
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) chipColor.copy(0.6f) else BgBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onChange(state.copy(category = cat)) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = scheduleCategoryLabel(cat),
                                    fontSize   = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (selected) chipColor else TextMuted
                                )
                            }
                        }
                    }
                }

                // ── Time window ───────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.label_time_window), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = TextMuted, letterSpacing = 1.sp)
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        TimeSelector(
                            label     = stringResource(R.string.time_from),
                            hour      = state.startHour,
                            min       = state.startMin,
                            onTimeChange = { h, m -> onChange(state.copy(startHour = h, startMin = m)) },
                            modifier  = Modifier.weight(1f)
                        )
                        Text("→", fontSize = 14.sp, color = TextMuted)
                        TimeSelector(
                            label     = stringResource(R.string.time_until),
                            hour      = state.endHour,
                            min       = state.endMin,
                            onTimeChange = { h, m -> onChange(state.copy(endHour = h, endMin = m)) },
                            modifier  = Modifier.weight(1f)
                        )
                    }
                }

                // ── Day chips ─────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.label_active_days), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = TextMuted, letterSpacing = 1.sp)
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dayLabels = listOf(
                            stringResource(R.string.schedule_day_mon),
                            stringResource(R.string.schedule_day_tue),
                            stringResource(R.string.schedule_day_wed),
                            stringResource(R.string.schedule_day_thu),
                            stringResource(R.string.schedule_day_fri),
                            stringResource(R.string.schedule_day_sat),
                            stringResource(R.string.schedule_day_sun)
                        )
                        dayLabels.forEachIndexed { i, label ->
                            val on = i < state.days.length && state.days[i] == '1'
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (on) CyanPrimary.copy(alpha = 0.18f) else BgCardAlt
                                    )
                                    .border(
                                        1.dp,
                                        if (on) CyanPrimary.copy(0.5f) else BgBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        val arr = state.days.toCharArray()
                                        if (i < arr.size) arr[i] = if (on) '0' else '1'
                                        onChange(state.copy(days = String(arr)))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 11.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (on) CyanPrimary else TextMuted)
                            }
                        }
                    }
                }
            }

            // ── Status row ────────────────────────────────────────────────────
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
            val (statusDot, statusText, statusColor) = when {
                !state.enabled         -> Triple("○", stringResource(R.string.schedule_status_disabled), TextMuted)
                state.activeNow        -> Triple("●", stringResource(R.string.schedule_status_active, scheduleCategoryLabel(state.category)), GreenSafe)
                else                   -> Triple("○", stringResource(R.string.schedule_status_inactive), TextSecondary)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (state.activeNow) GreenSafe.copy(alpha = 0.05f) else BgCardAlt)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(statusDot, fontSize = 10.sp, color = statusColor)
                Text(statusText, fontSize = 11.sp, color = statusColor,
                    fontWeight = if (state.activeNow) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/**
 * One time box (hour : minute). The small − / + buttons change the hour or minute by one,
 * wrapping around (23 → 00, 59 → 00). Tapping the time itself opens the standard Material 3
 * time picker, so any minute can be chosen quickly. Hour and minute are always set together.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSelector(
    label:        String,
    hour:         Int,
    min:          Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier:     Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 9.sp, color = TextMuted, letterSpacing = 0.5.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BgCardAlt)
                .border(1.dp, BgBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TimeStepper(
                value    = hour,
                onMinus  = { onTimeChange((hour + 23) % 24, min) },
                onPlus   = { onTimeChange((hour + 1) % 24, min) },
                onTapValue = { showPicker = true }
            )
            Text(":", fontSize = 13.sp, color = TextMuted)
            TimeStepper(
                value    = min,
                onMinus  = { onTimeChange(hour, (min + 59) % 60) },
                onPlus   = { onTimeChange(hour, (min + 1) % 60) },
                onTapValue = { showPicker = true }
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = min, is24Hour = true)
        var typeTime by remember { mutableStateOf(false) }
        // Sized to its own content (the Material 3 time-picker dialog pattern), so the clock
        // face is never squeezed on narrow phones.
        Dialog(
            onDismissRequest = { showPicker = false },
            properties       = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape    = RoundedCornerShape(20.dp),
                color    = BgCard,
                modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                    if (typeTime) TimeInput(state = pickerState) else TimePicker(state = pickerState)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // Switch between the clock face and typing the time.
                        IconButton(onClick = { typeTime = !typeTime }) {
                            Icon(if (typeTime) Icons.Default.Schedule else Icons.Default.Keyboard,
                                null, tint = TextSecondary)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showPicker = false }) {
                            Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                        }
                        TextButton(onClick = {
                            showPicker = false
                            onTimeChange(pickerState.hour, pickerState.minute)
                        }) { Text(stringResource(R.string.btn_save), color = CyanPrimary, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/** − value + ; tapping the value opens the time picker. */
@Composable
private fun TimeStepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit, onTapValue: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                .background(BgBorder.copy(0.6f)).clickable { onMinus() },
            contentAlignment = Alignment.Center
        ) { Text("−", fontSize = 12.sp, color = TextSecondary) }
        Text(
            text       = "%02d".format(value),
            fontSize   = 14.sp,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary,
            modifier   = Modifier.width(26.dp).clip(RoundedCornerShape(4.dp)).clickable { onTapValue() },
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center
        )
        Box(
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                .background(BgBorder.copy(0.6f)).clickable { onPlus() },
            contentAlignment = Alignment.Center
        ) { Text("+", fontSize = 12.sp, color = TextSecondary) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Import / Export Card
// Export saves USER rules to Downloads as a plain .txt file (one domain per line).
// Import reads any .txt file, skips comments (#) and duplicates, merges the rest.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ImportExportCard(
    onExport:  () -> Unit,
    onImport:  () -> Unit,
    exportMsg: String?,
    importMsg: String?,
    rateLimit: AppControlViewModel.RateLimitState,
    canUndo:   Boolean,
    onUndo:    () -> Unit
) {
    val activeMsg  = exportMsg ?: importMsg
    val isError    = activeMsg?.lowercase()?.contains("failed") == true
    val msgColor   = if (isError) RedCritical else GreenSafe
    val isBlocked  = rateLimit.usesRemaining == 0

    // Captured here (Composable context) since produceState's block is a suspend lambda,
    // not a Composable one — stringResource() cannot be called inside it directly.
    val hourSuffix = stringResource(R.string.time_hour_suffix)
    val minSuffix  = stringResource(R.string.time_min_suffix)

    // Live countdown — ticks every minute while the card is visible
    val countdown by produceState(initialValue = "", key1 = rateLimit.resetAtMs) {
        while (true) {
            val remaining = rateLimit.resetAtMs - System.currentTimeMillis()
            value = if (remaining > 0) {
                val h = remaining / 3_600_000
                val m = (remaining % 3_600_000) / 60_000
                if (h > 0) "${h}$hourSuffix ${m}$minSuffix" else "${m}$minSuffix"
            } else ""
            delay(60_000)
        }
    }

    // Usage badge colors
    val usageBadgeColor = when (rateLimit.usesRemaining) {
        2    -> GreenSafe
        1    -> AmberHigh
        else -> RedCritical
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(
                width = 1.dp,
                color = if (isBlocked) AmberHigh.copy(alpha = 0.35f) else BgBorder,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column {
            // ── Header row with usage badge ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isBlocked) AmberHigh.copy(alpha = 0.06f)
                        else CyanPrimary.copy(alpha = 0.05f)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Share, null,
                    tint     = if (isBlocked) AmberHigh.copy(0.7f) else CyanPrimary.copy(0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text     = stringResource(R.string.import_export_backup_desc),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                // Uses remaining badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(usageBadgeColor.copy(alpha = 0.14f))
                        .border(1.dp, usageBadgeColor.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text       = stringResource(R.string.uses_remaining_badge, rateLimit.usesRemaining),
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color      = usageBadgeColor
                    )
                }
            }
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            // ── Action buttons (dimmed when blocked) ──────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val exportBg     = if (isBlocked) BgCardAlt else CyanPrimary.copy(alpha = 0.10f)
                val exportBorder = if (isBlocked) BgBorder   else CyanPrimary.copy(alpha = 0.35f)
                val exportTint   = if (isBlocked) TextMuted   else CyanPrimary

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(exportBg)
                        .border(1.dp, exportBorder, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isBlocked) { onExport() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Share, null, tint = exportTint,
                            modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.btn_export_rules), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = exportTint
                        )
                    }
                }

                val importBg     = if (isBlocked) BgCardAlt else GreenSafe.copy(alpha = 0.10f)
                val importBorder = if (isBlocked) BgBorder   else GreenSafe.copy(alpha = 0.35f)
                val importTint   = if (isBlocked) TextMuted   else GreenSafe

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(importBg)
                        .border(1.dp, importBorder, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isBlocked) { onImport() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = importTint,
                            modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.btn_import_rules), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = importTint
                        )
                    }
                }
            }

            // ── Undo the last restore (only when there is one) ────────────────
            if (canUndo) {
                HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUndo() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, tint = AmberMedium,
                        modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.restore_undo), fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = AmberMedium)
                }
            }

            // ── Blocked warning with countdown ────────────────────────────────
            if (isBlocked) {
                HorizontalDivider(color = AmberHigh.copy(alpha = 0.2f), thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AmberHigh.copy(alpha = 0.07f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(AmberHigh))
                    Text(
                        text     = stringResource(R.string.daily_limit_reached),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color    = AmberHigh,
                        modifier = Modifier.weight(1f)
                    )
                    if (countdown.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(AmberHigh.copy(alpha = 0.14f))
                                .border(1.dp, AmberHigh.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text       = stringResource(R.string.resets_in, countdown),
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = AmberHigh
                            )
                        }
                    }
                }
            }

            // ── Operation result message (auto-clears after 5s) ──────────────
            if (activeMsg != null && !activeMsg.contains("limit reached", ignoreCase = true)) {
                HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(msgColor.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(msgColor))
                    Text(activeMsg, fontSize = 11.sp, color = msgColor,
                        modifier = Modifier.weight(1f))
                }
            }

            // ── Format hint ───────────────────────────────────────────────────
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCardAlt)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text     = stringResource(R.string.import_export_format_hint),
                    fontSize = 9.sp,
                    color    = TextMuted
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppControlsBar(
    searchQuery:    String,
    onSearchChange: (String) -> Unit,
    showSystem:     Boolean,
    onToggleSystem: (Boolean) -> Unit,
    accessFilter:   AppAccessFilter,
    onAccessFilter: (AppAccessFilter) -> Unit
) {
    var showHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(BgCard).border(1.dp, BgBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value         = searchQuery,
                onValueChange = onSearchChange,
                placeholder   = { Text(stringResource(R.string.search_apps_placeholder), fontSize = 13.sp, color = TextMuted) },
                leadingIcon   = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors        = TextFieldDefaults.colors(
                    focusedContainerColor   = BgCardAlt,
                    unfocusedContainerColor = BgCardAlt,
                    focusedIndicatorColor   = CyanPrimary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary,
                    cursorColor             = CyanPrimary
                )
            )
            // Two sides instead of a switch: tap the left for apps you installed,
            // the right for system apps. The lists are mutually exclusive.
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgCardAlt)
                        .border(1.dp, BgBorder, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    AppScopeTab(
                        label    = stringResource(R.string.apps_tab_installed),
                        selected = !showSystem,
                        modifier = Modifier.weight(1f)
                    ) { onToggleSystem(false) }
                    AppScopeTab(
                        label    = stringResource(R.string.apps_tab_system),
                        selected = showSystem,
                        modifier = Modifier.weight(1f)
                    ) { onToggleSystem(true) }
                }
                HelpIcon(onClick = { showHelp = true })
            }
            // Every app, or only the allowed or only the blocked ones (a partial block counts
            // as blocked). Works together with the tab and the search text.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCardAlt)
                    .border(1.dp, BgBorder, RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(
                    AppAccessFilter.ALL     to R.string.apps_filter_all,
                    AppAccessFilter.ALLOWED to R.string.apps_filter_allowed,
                    AppAccessFilter.BLOCKED to R.string.apps_filter_blocked
                ).forEach { (filter, label) ->
                    AppScopeTab(
                        label    = stringResource(label),
                        selected = accessFilter == filter,
                        modifier = Modifier.weight(1f)
                    ) { onAccessFilter(filter) }
                }
            }
        }
    }

    if (showHelp) {
        HelpDialog(content = ShowSystemAppsHelp(), onDismiss = { showHelp = false })
    }
}

/** One side of the Installed / System selector. */
@Composable
private fun AppScopeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) CyanPrimary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color      = if (selected) CyanPrimary else TextMuted
        )
    }
}

@Composable
private fun ShowSystemAppsHelp() = HelpContent(
    title = stringResource(R.string.show_system_apps_help_title),
    whatItIs = stringResource(R.string.show_system_apps_help_what_it_is),
    whatItDoes = stringResource(R.string.show_system_apps_help_what_it_does),
    why = stringResource(R.string.show_system_apps_help_why),
    benefit = stringResource(R.string.show_system_apps_help_benefit),
    bestPractices = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// App card — clear block/allow UX with labeled buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppCard(
    app:               AppRule,
    sharedWith:        List<String> = emptyList(),
    riskCount:         Int        = 0,
    queryRate:         Int        = 0,
    anomalyRatio:      Float      = 0f,
    beaconInterval:    Int        = 0,
    updateChangeRatio: Float      = 0f,
    onClick:           () -> Unit = {},
    onPurge:           (AppRule) -> Unit,
    onWifiToggle:      (Boolean) -> Unit,
    onDataToggle:      (Boolean) -> Unit
) {
    val context      = LocalContext.current
    // The firewall's own row: it can never be blocked (see SelfProtection), so it never
    // shows as blocked either, even for the moment before an old saved rule is cleared.
    val isSelf       = app.packageName == context.packageName
    val isFullBlock  = !isSelf && app.isWifiBlocked && app.isDataBlocked
    val isPartial    = !isSelf && (app.isWifiBlocked xor app.isDataBlocked)
    val isAny        = !isSelf && (app.isWifiBlocked || app.isDataBlocked)
    val cardBorder   = if (isAny) RedCritical.copy(alpha = 0.25f) else BgBorder

    // Load icon via PackageManager — avoids Coil's failure on bare package-name strings
    val iconDrawable = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Top row: icon + name + status + delete ──────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // App icon (PackageManager drawable — no black box fallback)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(BgCardAlt),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconDrawable != null) {
                        AsyncImage(
                            model              = ImageRequest.Builder(context).data(iconDrawable).crossfade(true).build(),
                            contentDescription = app.name,
                            modifier           = Modifier.size(36.dp)
                        )
                    } else {
                        // Letter avatar when icon is unavailable
                        Text(
                            text       = app.name.firstOrNull()?.uppercase() ?: "?",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = CyanPrimary
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, fontSize = 10.sp, color = TextMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (riskCount > 0) {
                        val riskColor = when {
                            riskCount >= 101 -> RedCritical
                            riskCount >= 21  -> AmberHigh
                            else             -> GreenSafe
                        }
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(riskColor)
                            )
                            Text(
                                text     = pluralStringResource(R.plurals.threats_blocked_7d, riskCount, riskCount),
                                fontSize = 9.sp,
                                color    = riskColor
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Status badge + query rate stacked vertically
                val (statusText, statusColor) = when {
                    isFullBlock -> stringResource(R.string.badge_blocked) to RedCritical
                    isPartial   -> stringResource(R.string.status_partial) to AmberMedium
                    else        -> stringResource(R.string.weekly_chip_allowed) to GreenSafe
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(statusText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                    if (queryRate > 0) {
                        val rateColor = when {
                            queryRate >= 61 -> RedCritical
                            queryRate >= 21 -> AmberHigh
                            else            -> CyanPrimary
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(rateColor.copy(alpha = 0.10f))
                                .border(0.5.dp, rateColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text       = stringResource(R.string.query_rate_per_min, queryRate),
                                fontSize   = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color      = rateColor
                            )
                        }
                    }
                    if (anomalyRatio >= 3f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(RedCritical.copy(alpha = 0.14f))
                                .border(0.5.dp, RedCritical.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text       = stringResource(R.string.spike_badge, anomalyRatio.toInt()),
                                fontSize   = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color      = RedCritical
                            )
                        }
                    }
                    if (beaconInterval > 0) {
                        val intervalLabel = if (beaconInterval >= 60)
                            stringResource(R.string.beacon_minutes, beaconInterval / 60)
                            else stringResource(R.string.beacon_seconds, beaconInterval)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberHigh.copy(alpha = 0.13f))
                                .border(0.5.dp, AmberHigh.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text       = stringResource(R.string.beacon_badge, intervalLabel),
                                fontSize   = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color      = AmberHigh
                            )
                        }
                    }
                    if (updateChangeRatio >= 2f) {
                        val updColor = Color(0xFFCE93D8)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(updColor.copy(alpha = 0.13f))
                                .border(0.5.dp, updColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text       = stringResource(R.string.upd_badge, updateChangeRatio.toInt()),
                                fontSize   = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color      = updColor
                            )
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Purge button
                IconButton(onClick = { onPurge(app) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.cd_purge, app.name), tint = TextMuted.copy(0.45f),
                        modifier = Modifier.size(15.dp))
                }
            }

            // ── Shared internet access (apps under one UID) ─────────────────
            // Android applies the rule to the UID, so tapping this card's switches also
            // changes the apps named here.
            if (sharedWith.isNotEmpty()) {
                Row(
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = AmberMedium,
                        modifier = Modifier.size(13.dp).padding(top = 1.dp))
                    Text(
                        text       = stringResource(R.string.apps_shared_uid_note,
                            sharedWith.joinToString(", ")),
                        fontSize   = 11.sp,
                        lineHeight = 15.sp,
                        color      = TextSecondary
                    )
                }
            }

            // ── Divider ──────────────────────────────────────────────────────
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            // ── Block controls row ───────────────────────────────────────────
            if (isSelf) Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = GreenSafe,
                    modifier = Modifier.size(16.dp))
                Text(
                    text       = stringResource(R.string.app_self_protected),
                    fontSize   = 11.sp,
                    color      = TextSecondary,
                    lineHeight = 15.sp
                )
            } else Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BlockToggleButton(
                    modifier  = Modifier.weight(1f),
                    icon      = Icons.Default.Wifi,
                    label     = stringResource(R.string.toggle_wifi),
                    blocked   = app.isWifiBlocked,
                    onToggle  = { onWifiToggle(!app.isWifiBlocked) }
                )
                BlockToggleButton(
                    modifier  = Modifier.weight(1f),
                    icon      = Icons.Default.PhoneAndroid,
                    label     = stringResource(R.string.toggle_mobile_data),
                    blocked   = app.isDataBlocked,
                    onToggle  = { onDataToggle(!app.isDataBlocked) }
                )
            }

            // ── Profile hint — teaches new users the card is tappable ────────
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text     = if (riskCount > 0) stringResource(R.string.hint_threats_blocked_7d, riskCount) else stringResource(R.string.hint_tap_to_inspect),
                    fontSize = 10.sp,
                    color    = if (riskCount > 0) RedCritical.copy(alpha = 0.6f) else TextMuted
                )
                Text(
                    text     = stringResource(R.string.hint_view_dns_profile),
                    fontSize = 10.sp,
                    color    = CyanPrimary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Block toggle button — clearly labeled so new users know what to tap
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlockToggleButton(
    modifier:  Modifier,
    icon:      ImageVector,
    label:     String,
    blocked:   Boolean,
    onToggle:  () -> Unit
) {
    val bg     = if (blocked) RedCritical.copy(alpha = 0.10f) else BgCardAlt
    val border = if (blocked) RedCritical.copy(alpha = 0.35f) else BgBorder
    val tint   = if (blocked) RedCritical else TextMuted

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Column {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (blocked) RedCritical else TextSecondary)
                Text(
                    text      = if (blocked) stringResource(R.string.status_blocked_dot) else stringResource(R.string.status_allowed_dot),
                    fontSize  = 10.sp,
                    color     = if (blocked) RedCritical.copy(0.7f) else GreenSafe.copy(0.7f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AcSectionHeader(
    title: String,
    badge: String?,
    badgeColor: Color = CyanPrimary,
    onHelpClick: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(3.dp).height(14.dp)
                .clip(RoundedCornerShape(2.dp)).background(CyanPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = TextSecondary, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        if (badge != null) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.14f))
                    .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) { Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeColor) }
        }
        if (onHelpClick != null) {
            HelpIcon(onClick = onHelpClick)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Backup dialog — the activity history is private browsing data, so it is opt-in
// (off by default) and the dialog says plainly who could read it.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackupDialog(onDismiss: () -> Unit, onBackup: (includeHistory: Boolean) -> Unit) {
    var includeHistory by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = {
            Text(stringResource(R.string.backup_dialog_title),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_dialog_body),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .toggleable(value = includeHistory, role = Role.Checkbox,
                            onValueChange = { includeHistory = it })
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = includeHistory, onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = CyanPrimary, uncheckedColor = TextMuted)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.backup_include_history),
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(stringResource(R.string.backup_include_history_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (includeHistory) AmberMedium else TextSecondary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onBackup(includeHistory) }) {
                Text(stringResource(R.string.backup_dialog_confirm), color = CyanPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = TextSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Smart Suggestions Card
// ─────────────────────────────────────────────────────────────────────────────

private const val SUGGESTIONS_VISIBLE_DEFAULT = 5

@Composable
private fun SmartSuggestionsCard(
    suggestions: List<String>,
    onBlock:     (String) -> Unit,
    onDismiss:   (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val showToggle = suggestions.size > SUGGESTIONS_VISIBLE_DEFAULT
    val visible = if (expanded || !showToggle) suggestions
                  else suggestions.take(SUGGESTIONS_VISIBLE_DEFAULT)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, AmberHigh.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(AmberHigh.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Security,
                    contentDescription = null,
                    tint               = AmberHigh,
                    modifier           = Modifier.size(14.dp)
                )
                Text(
                    text       = stringResource(R.string.suggestions_card_title),
                    style      = MaterialTheme.typography.labelSmall,
                    color      = AmberHigh,
                    modifier   = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = AmberHigh.copy(alpha = 0.15f), thickness = 0.5.dp)

            visible.forEachIndexed { index, domain ->
                if (index > 0) HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 14.dp),
                    color     = BgBorder.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AmberHigh.copy(alpha = 0.7f))
                    )
                    Text(
                        text     = domain,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Dismiss
                    IconButton(
                        onClick  = { onDismiss(domain) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_dismiss),
                            tint               = TextMuted,
                            modifier           = Modifier.size(13.dp)
                        )
                    }
                    // Block
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RedCritical.copy(alpha = 0.12f))
                            .border(1.dp, RedCritical.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onBlock(domain) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text       = stringResource(R.string.btn_block),
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color      = RedCritical
                        )
                    }
                }
            }

            // Show more / Show less toggle — only when list exceeds default count
            if (showToggle) {
                HorizontalDivider(color = AmberHigh.copy(alpha = 0.10f), thickness = 0.5.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = if (expanded) stringResource(R.string.show_less)
                                else stringResource(R.string.show_x_more, suggestions.size - SUGGESTIONS_VISIBLE_DEFAULT),
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberHigh
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App Connection Profile Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppProfileSheet(
    app:      AppRule,
    profile:  AppControlViewModel.AppProfile,
    onDismiss: () -> Unit
) {
    val context      = LocalContext.current
    val iconDrawable = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
    }
    var showHelp     by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BgBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── App header ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgCardAlt),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconDrawable != null) {
                        AsyncImage(
                            model              = ImageRequest.Builder(context).data(iconDrawable).crossfade(true).build(),
                            contentDescription = app.name,
                            modifier           = Modifier.size(42.dp)
                        )
                    } else {
                        Text(
                            text       = app.name.firstOrNull()?.uppercase() ?: "?",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = CyanPrimary
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(app.packageName, fontSize = 10.sp, color = TextMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HelpIcon(onClick = { showHelp = true })
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            // ── 7-day stats row ───────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileStatChip(stringResource(R.string.profile_chip_queries),   "${profile.totalQueries}", CyanPrimary,  Modifier.weight(1f))
                ProfileStatChip(stringResource(R.string.badge_blocked),   "${profile.blockedCount}", RedCritical,  Modifier.weight(1f))
                ProfileStatChip(stringResource(R.string.weekly_chip_allowed),   "${profile.allowedCount}", GreenSafe,    Modifier.weight(1f))
                val rate = if (profile.totalQueries > 0)
                    "${profile.blockedCount * 100 / profile.totalQueries}%" else "—"
                ProfileStatChip(stringResource(R.string.weekly_chip_block_rate), rate,                    AmberHigh,    Modifier.weight(1f))
            }

            // ── Top Blocked domains ───────────────────────────────────────────
            if (profile.topBlocked.isNotEmpty()) {
                ProfileDomainSection(
                    title   = stringResource(R.string.profile_top_blocked),
                    rows    = profile.topBlocked,
                    color   = RedCritical,
                    label   = stringResource(R.string.badge_blocked)
                )
            }

            // ── Top Allowed domains ───────────────────────────────────────────
            if (profile.topAllowed.isNotEmpty()) {
                ProfileDomainSection(
                    title   = stringResource(R.string.profile_top_allowed),
                    rows    = profile.topAllowed,
                    color   = GreenSafe,
                    label   = stringResource(R.string.weekly_chip_allowed)
                )
            }

            if (profile.totalQueries == 0) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = stringResource(R.string.profile_no_activity),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }
    }

    if (showHelp) {
        HelpDialog(content = AppProfileHelp(), onDismiss = { showHelp = false })
    }
}

@Composable
private fun AppProfileHelp() = HelpContent(
    title = stringResource(R.string.profile_help_title),
    whatItIs = stringResource(R.string.profile_help_what_it_is),
    whatItDoes = stringResource(R.string.profile_help_what_it_does),
    why = stringResource(R.string.profile_help_why),
    benefit = stringResource(R.string.profile_help_benefit),
    bestPractices = emptyList()
)

@Composable
private fun ProfileStatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 8.sp, color = TextMuted, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun ProfileDomainSection(
    title: String,
    rows:  List<com.sentinel.core.logs.StatRow>,
    color: Color,
    label: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text          = title,
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            color         = TextMuted,
            letterSpacing = 1.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BgCardAlt)
                .border(1.dp, BgBorder, RoundedCornerShape(10.dp))
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = BgBorder, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 12.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(color.copy(alpha = 0.7f)))
                        Text(row.name, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(color.copy(alpha = 0.10f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("×${row.count}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(color.copy(alpha = 0.10f))
                                .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = color)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Geo Blocking Card
// ─────────────────────────────────────────────────────────────────────────────

// Localized display name for a GeoCountry, keyed by its (untranslated, functional) code —
// GEO_COUNTRIES' code/tld fields are never touched; only this UI-layer display mapping changes.
@Composable
private fun geoCountryName(code: String): String = when (code) {
    "CN" -> stringResource(R.string.geo_country_china)
    "RU" -> stringResource(R.string.geo_country_russia)
    "IR" -> stringResource(R.string.geo_country_iran)
    "KP" -> stringResource(R.string.geo_country_north_korea)
    "BY" -> stringResource(R.string.geo_country_belarus)
    "NG" -> stringResource(R.string.geo_country_nigeria)
    else -> code
}

@Composable
private fun GeoBlockingCard(
    blockedDomains: List<String>,
    onBlock:        (String) -> Unit,
    onUnblock:      (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RedCritical.copy(alpha = 0.05f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = Icons.Default.Block,
                    contentDescription = null,
                    tint               = RedCritical.copy(alpha = 0.7f),
                    modifier           = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text     = stringResource(R.string.geo_card_instruction),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            GEO_COUNTRIES.forEachIndexed { index, country ->
                if (index > 0) HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 14.dp),
                    color     = BgBorder.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                val isBlocked = blockedDomains.contains(country.tld)
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .background(if (isBlocked) RedCritical.copy(alpha = 0.04f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Country code chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(BgCardAlt)
                            .border(1.dp, BgBorder, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = country.code,
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Black,
                            color      = if (isBlocked) RedCritical else TextMuted,
                            letterSpacing = 0.5.sp
                        )
                    }
                    // Country name
                    Text(
                        text     = geoCountryName(country.code),
                        fontSize = 13.sp,
                        color    = if (isBlocked) TextPrimary else TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    // TLD badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgCardAlt)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text      = ".${country.tld}",
                            fontSize  = 10.sp,
                            color     = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Toggle
                    Switch(
                        checked         = isBlocked,
                        onCheckedChange = { on -> if (on) onBlock(country.tld) else onUnblock(country.tld) },
                        modifier        = Modifier.height(24.dp),
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = BgDeep,
                            checkedTrackColor   = RedCritical,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BgBorder
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy public alias — keeps any external callers working without changes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppRuleItem(
    app:            AppRule,
    onPurge:        (AppRule) -> Unit,
    onPolicyChange: (Boolean, Boolean) -> Unit
) {
    AppCard(
        app          = app,
        onPurge      = onPurge,
        onWifiToggle = { wifi -> onPolicyChange(wifi, app.isDataBlocked) },
        onDataToggle = { data -> onPolicyChange(app.isWifiBlocked, data) }
    )
}
