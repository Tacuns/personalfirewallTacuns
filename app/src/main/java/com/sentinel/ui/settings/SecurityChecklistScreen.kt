package com.sentinel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.DashboardStatsViewModel
import com.sentinel.ui.viewmodel.FirewallViewModel
import com.tacu.nsfwzerotrust.R

private data class ChecklistItem(val label: String, val detail: String, val passed: Boolean)

@Composable
private fun ChecklistRow(item: ChecklistItem) {
    val color = if (item.passed) GreenSafe else AmberHigh
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (item.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(item.detail, style = MaterialTheme.typography.labelSmall, color = TextSecondary, lineHeight = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityChecklistScreen(
    onBack: () -> Unit,
    firewallViewModel: FirewallViewModel = viewModel(),
    statsViewModel: DashboardStatsViewModel = viewModel()
) {
    val isVpnRunning by firewallViewModel.isVpnRunning
    val stats by statsViewModel.stats.collectAsState()

    // Same thresholds as the Dashboard's Security Score — this screen presents the same
    // 5 real facts as a checklist instead of a gauge, it does not recompute the score itself.
    val total = stats.blockedToday + stats.allowedToday
    val blockRate = if (total > 0) (stats.blockedToday.toFloat() / total) else 0f

    val items = listOf(
        ChecklistItem(
            stringResource(R.string.checklist_item_vpn_label),
            stringResource(R.string.checklist_item_vpn_detail),
            isVpnRunning
        ),
        ChecklistItem(
            stringResource(R.string.checklist_item_threat_db_label),
            stringResource(R.string.checklist_item_threat_db_detail),
            stats.blocklistSize > 1_000
        ),
        ChecklistItem(
            stringResource(R.string.checklist_item_blocked_today_label),
            stringResource(R.string.checklist_item_blocked_today_detail),
            stats.blockedToday > 0
        ),
        ChecklistItem(
            stringResource(R.string.checklist_item_autostart_label),
            stringResource(R.string.checklist_item_autostart_detail),
            stats.bootEnabled
        ),
        ChecklistItem(
            stringResource(R.string.checklist_item_blockrate_label),
            stringResource(R.string.checklist_item_blockrate_detail),
            blockRate > 0f
        )
    )
    val passedCount = items.count { it.passed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.security_checklist_title), style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        containerColor = BgDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                stringResource(R.string.checklist_summary, passedCount, items.size),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp)
            ) {
                Column {
                    items.forEachIndexed { i, item ->
                        ChecklistRow(item)
                        if (i < items.lastIndex) {
                            HorizontalDivider(color = BgBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
