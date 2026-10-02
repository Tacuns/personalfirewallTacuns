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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R

@Composable
private fun SafetyRow(icon: ImageVector, iconColor: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.labelSmall, color = TextSecondary, lineHeight = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSafetyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_data_safety_title), style = MaterialTheme.typography.titleSmall,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgActive)
                    .border(1.dp, GreenSafe.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenSafe, modifier = Modifier.size(20.dp))
                    Column {
                        Text(stringResource(R.string.data_safety_banner_title), style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold, color = GreenSafe)
                        Text(stringResource(R.string.data_safety_banner_subtitle),
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.data_safety_stored_header), style = MaterialTheme.typography.labelSmall,
                color = TextMuted, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp)
            ) {
                Column {
                    SafetyRow(Icons.Default.Block, CyanPrimary, stringResource(R.string.data_safety_rules_title),
                        stringResource(R.string.data_safety_rules_body))
                    HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                    SafetyRow(Icons.Default.Dns, BlueAccent, stringResource(R.string.data_safety_logs_title),
                        stringResource(R.string.data_safety_logs_body))
                    HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                    SafetyRow(Icons.Default.Settings, AmberMedium, stringResource(R.string.data_safety_prefs_title),
                        stringResource(R.string.data_safety_prefs_body))
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.data_safety_never_header), style = MaterialTheme.typography.labelSmall,
                color = TextMuted, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp)
            ) {
                Column {
                    SafetyRow(Icons.Default.Lock, GreenSafe, stringResource(R.string.data_safety_noaccount_title),
                        stringResource(R.string.data_safety_noaccount_body))
                    HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                    SafetyRow(Icons.Default.VerifiedUser, GreenSafe, stringResource(R.string.data_safety_noanalytics_title),
                        stringResource(R.string.data_safety_noanalytics_body))
                    HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                    SafetyRow(Icons.Default.Security, GreenSafe, stringResource(R.string.data_safety_background_title),
                        stringResource(R.string.data_safety_background_body))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
