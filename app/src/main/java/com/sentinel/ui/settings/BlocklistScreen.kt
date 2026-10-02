package com.sentinel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.core.rules.BlocklistSource
import com.sentinel.core.vpn.CustomBlocklist
import com.sentinel.ui.components.HelpContent
import com.sentinel.ui.components.HelpDialog
import com.sentinel.ui.components.HelpIcon
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.BlocklistViewModel
import com.tacu.nsfwzerotrust.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(onBack: () -> Unit) {
    val vm: BlocklistViewModel = viewModel()
    val sources by vm.sources.collectAsState()
    val snackState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val proSnackbarText = stringResource(R.string.blocklist_pro_snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.blocklist_screen_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        snackbarHost = { SnackbarHost(snackState) },
        containerColor = BgDeep
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                BlocklistInfoBanner()
            }
            item {
                // Built-in free lists only. The user's own lists have their own card below.
                // The four extra lists from early development (OISD, HaGeZi, AdGuard, Dan
                // Pollock) were never usable: their rows stay switched off in the database
                // but are not shown.
                BlocklistCard(
                    sources     = sources.filter { it.isDefault && !CustomBlocklist.isCustom(it.sourceKey) },
                    syncingKeys = vm.syncingKeys,
                    onRefreshAll = { vm.refreshAll() },
                    onRefresh    = { vm.refreshSource(it) },
                    onToggle     = { key, enabled -> vm.setSourceEnabled(key, enabled) },
                    errorFor     = { vm.errorFor(it) },
                    intervalFor  = { vm.intervalFor(it) },
                    onInterval   = { key, hours -> vm.setInterval(key, hours) },
                    onProTap     = {
                        scope.launch {
                            snackState.showSnackbar(proSnackbarText)
                        }
                    }
                )
            }
            item {
                CustomBlocklistsCard(
                    lists       = sources.filter { CustomBlocklist.isCustom(it.sourceKey) },
                    syncingKeys = vm.syncingKeys,
                    errorFor    = { vm.errorFor(it) },
                    intervalFor = { vm.intervalFor(it) },
                    onInterval  = { key, hours -> vm.setInterval(key, hours) },
                    onToggle    = { key, enabled -> vm.setSourceEnabled(key, enabled) },
                    onRefresh   = { vm.refreshSource(it) },
                    onRemove    = { vm.removeCustomList(it) },
                    onAdd       = { name, link, done -> vm.addCustomList(name, link, done) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.blocklist_attribution),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BlocklistInfoBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF062028))
            .border(1.dp, CyanPrimary.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(16.dp).padding(top = 1.dp)
            )
            Text(
                text = stringResource(R.string.blocklist_info_banner),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun BlocklistCard(
    sources:      List<BlocklistSource>,
    syncingKeys:  Set<String>,
    onRefreshAll: () -> Unit,
    onRefresh:    (String) -> Unit,
    onToggle:     (String, Boolean) -> Unit,
    errorFor:     (BlocklistSource) -> String?,
    intervalFor:  (String) -> Int,
    onInterval:   (String, Int) -> Unit,
    onProTap:     () -> Unit
) {
    val enabledCount = sources.count { it.enabled }
    var showHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            // ── Card header ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF062028)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.section_community_blocklists),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        stringResource(R.string.blocklist_active_count, enabledCount, sources.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                // Tier badge
                val badgeBg    = if (enabledCount > 0) GreenSafe.copy(alpha = 0.14f) else BgBorder.copy(alpha = 0.5f)
                val badgeColor = if (enabledCount > 0) GreenSafe else TextMuted
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        stringResource(R.string.badge_free),
                        fontSize = 10.sp,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                HelpIcon(onClick = { showHelp = true })
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            // ── Source rows ───────────────────────────────────────────────────
            sources.forEachIndexed { index, source ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(horizontal = 14.dp),
                        color     = BgBorder.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                }
                BlocklistSourceRow(
                    source     = source,
                    isSyncing  = source.sourceKey in syncingKeys,
                    onRefresh  = onRefresh,
                    onToggle   = onToggle,
                    error      = errorFor(source),
                    interval   = intervalFor(source.sourceKey),
                    onInterval = { onInterval(source.sourceKey, it) },
                    onProTap   = onProTap
                )
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            // ── Refresh all button ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRefreshAll() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.btn_refresh_active_blocklists),
                    style = MaterialTheme.typography.bodySmall,
                    color = CyanPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showHelp) {
        HelpDialog(content = blocklistCardHelp(), onDismiss = { showHelp = false })
    }
}

@Composable
private fun blocklistCardHelp() = HelpContent(
    title = stringResource(R.string.blocklist_card_help_title),
    whatItIs = stringResource(R.string.blocklist_card_help_what_it_is),
    whatItDoes = stringResource(R.string.blocklist_card_help_what_it_does),
    why = stringResource(R.string.blocklist_card_help_why),
    benefit = stringResource(R.string.blocklist_card_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.blocklist_card_help_practice_1),
        stringResource(R.string.blocklist_card_help_practice_2),
        stringResource(R.string.blocklist_card_help_practice_3)
    )
)

@Composable
private fun BlocklistSourceRow(
    source:    BlocklistSource,
    isSyncing: Boolean,
    onRefresh: (String) -> Unit,
    onToggle:  (String, Boolean) -> Unit,
    error:     String?,
    interval:  Int,
    onInterval: (Int) -> Unit,
    onProTap:  () -> Unit
) {
    val isPro = !source.isDefault
    var showInterval by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isPro) Modifier.clickable { onProTap() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Source icon
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (source.enabled) BgActive else BgBorder.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = if (source.enabled) CyanPrimary else TextMuted,
                modifier = Modifier.size(15.dp)
            )
        }

        // Main content
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    source.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPro) TextSecondary else TextPrimary
                )
                val badgeColor = if (isPro) Color(0xFFFFA726) else GreenSafe
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(badgeColor.copy(alpha = 0.14f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isPro) stringResource(R.string.badge_pro) else stringResource(R.string.badge_free),
                        fontSize = 9.sp,
                        color = badgeColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                source.description,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            // Stats
            val statsText = when {
                source.domainCount > 0 ->
                    stringResource(R.string.blocklist_stats_domains_age, formatCount(source.domainCount), formatAge(source.lastUpdatedMs))
                source.enabled         -> stringResource(R.string.blocklist_stats_pending)
                else                   -> stringResource(R.string.blocklist_stats_not_downloaded)
            }
            Text(
                statsText,
                style = MaterialTheme.typography.labelSmall,
                color = if (source.domainCount > 0) TextSecondary else TextMuted
            )
            // Health of a list that is on: how often it updates, when next, and why the
            // last download failed (the stored copy keeps blocking meanwhile).
            if (!isPro && source.enabled) {
                BlocklistUpdateLine(source, interval, CyanPrimary) { showInterval = true }
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    BlocklistErrorLine(it, hasStoredCopy = source.domainCount > 0)
                }
            }
        }

        // Right control — PRO stays locked; FREE sources get an on/off switch,
        // plus a refresh action while the list is on.
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                isPro     -> Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.cd_pro_locked),
                    tint = Color(0xFFFFA726).copy(alpha = 0.65f),
                    modifier = Modifier.size(18.dp)
                )
                isSyncing -> CircularProgressIndicator(
                    modifier    = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color       = CyanPrimary
                )
                else -> {
                    if (source.enabled) {
                        IconButton(
                            onClick  = { onRefresh(source.sourceKey) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh),
                                tint = CyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Switch(
                        checked         = source.enabled,
                        onCheckedChange = { onToggle(source.sourceKey, it) },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = BgDeep,
                            checkedTrackColor   = CyanPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BgBorder
                        )
                    )
                }
            }
        }
    }
    if (showInterval) {
        BlocklistIntervalDialog(
            listName  = source.displayName,
            current   = interval,
            onPick    = { onInterval(it); showInterval = false },
            onDismiss = { showInterval = false }
        )
    }
}

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000     -> "${count / 1_000}k"
    else               -> count.toString()
}

@Composable
private fun formatAge(ms: Long): String {
    if (ms == 0L) return stringResource(R.string.age_never)
    val diff  = System.currentTimeMillis() - ms
    val mins  = diff / 60_000
    val hours = diff / 3_600_000
    val days  = diff / 86_400_000
    return when {
        mins  < 5  -> stringResource(R.string.age_updated_just_now)
        hours < 1  -> stringResource(R.string.age_updated_min_ago, mins)
        hours < 24 -> stringResource(R.string.age_updated_hr_ago, hours)
        days  < 7  -> stringResource(R.string.age_updated_day_ago, days)
        else       -> stringResource(R.string.age_updated_week_ago, days / 7)
    }
}
