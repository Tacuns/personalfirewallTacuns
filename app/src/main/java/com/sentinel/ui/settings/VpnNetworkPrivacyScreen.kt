package com.sentinel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.BlocklistViewModel
import com.tacu.nsfwzerotrust.R

@Composable
private fun InfoSection(icon: ImageVector, title: String, body: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanPrimary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.labelSmall, color = TextSecondary, lineHeight = 17.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnNetworkPrivacyScreen(onBack: () -> Unit, viewModel: BlocklistViewModel = viewModel()) {
    val sources by viewModel.sources.collectAsState()
    val enabledSources = sources.filter { it.enabled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_vpn_privacy_title), style = MaterialTheme.typography.titleSmall,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                InfoSection(
                    icon = Icons.Default.VpnKey,
                    title = stringResource(R.string.vpn_conn_works_title),
                    body = stringResource(R.string.vpn_conn_works_body)
                )
            }
            item {
                InfoSection(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.vpn_data_leaves_title),
                    body = stringResource(R.string.vpn_data_leaves_body)
                )
            }
            item {
                InfoSection(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.vpn_blocked_never_title),
                    body = stringResource(R.string.vpn_blocked_never_body)
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.blocklist_downloads_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            if (enabledSources.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.blocklist_none_enabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            } else {
                items(enabledSources) { source ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgCardAlt)
                            .border(1.dp, BgBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(source.displayName, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(source.url, fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.blocklist_download_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
