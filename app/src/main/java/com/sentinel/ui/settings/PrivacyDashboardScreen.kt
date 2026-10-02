package com.sentinel.ui.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.core.logs.LogDatabase
import com.sentinel.core.rules.RuleEngine
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DataFootprint(
    val customRules: Int = 0,
    val blocklistDomains: Int = 0,
    val loggedEventsAllTime: Int = 0,
    val blockedToday: Int = 0,
    val allowedToday: Int = 0
)

// Reads real, existing rows from the same Room databases the rest of the app already uses.
// No new queries were added — countByStatusSince(status, 0) is the existing query, called
// with since=0 to mean "all time" instead of "since a specific hour".
private suspend fun loadFootprint(context: Context): DataFootprint = withContext(Dispatchers.IO) {
    val ruleEngine = RuleEngine.getInstance(context)
    val logDao     = LogDatabase.getInstance(context).packetLogDao()
    val startOfDay = run {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    DataFootprint(
        customRules          = ruleEngine.db.ruleDao().getUserBlockedDomains().size,
        blocklistDomains     = ruleEngine.getBlocklistCount(),
        loggedEventsAllTime  = logDao.countByStatusSince("BLOCKED", 0) + logDao.countByStatusSince("ALLOWED", 0),
        blockedToday         = logDao.countByStatusSince("BLOCKED", startOfDay),
        allowedToday         = logDao.countByStatusSince("ALLOWED", startOfDay)
    )
}

private suspend fun exportFootprint(context: Context, footprint: DataFootprint) {
    val dateFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val fileName = "privacy-dashboard-${dateFmt.format(Date())}.txt"
    val report = buildString {
        appendLine(context.getString(R.string.export_report_header))
        appendLine(context.getString(R.string.export_report_generated, SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())))
        appendLine()
        appendLine(context.getString(R.string.export_report_custom_rules, footprint.customRules))
        appendLine(context.getString(R.string.export_report_blocklist_domains, footprint.blocklistDomains))
        appendLine(context.getString(R.string.export_report_total_logged, footprint.loggedEventsAllTime))
        appendLine(context.getString(R.string.export_report_blocked_today, footprint.blockedToday))
        appendLine(context.getString(R.string.export_report_allowed_today, footprint.allowedToday))
        appendLine()
        appendLine(context.getString(R.string.export_report_footer))
    }
    var savedOk = false
    withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(report.toByteArray()) }
                savedOk = true
            }
        } catch (e: Exception) {
            android.util.Log.e("PrivacyDashboard", "Save failed: ${e.message}")
        }
    }
    if (savedOk) Toast.makeText(context, context.getString(R.string.privacy_snapshot_toast_saved, fileName), Toast.LENGTH_LONG).show()
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.privacy_snapshot_share_subject))
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_share_chooser_title)))
}

@Composable
private fun FootprintStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 9.5.sp, color = color.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var footprint by remember { mutableStateOf<DataFootprint?>(null) }

    LaunchedEffect(Unit) {
        footprint = loadFootprint(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_privacy_dashboard_title), style = MaterialTheme.typography.titleSmall,
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
                stringResource(R.string.privacy_dashboard_intro),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(18.dp))

            if (footprint == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyanPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                val f = footprint!!
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { FootprintStat(stringResource(R.string.stat_custom_rules), "${f.customRules}", CyanPrimary) }
                    Box(Modifier.weight(1f)) { FootprintStat(stringResource(R.string.stat_blocklist_domains), "${f.blocklistDomains}", BlueAccent) }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { FootprintStat(stringResource(R.string.stat_blocked_today), "${f.blockedToday}", RedCritical) }
                    Box(Modifier.weight(1f)) { FootprintStat(stringResource(R.string.stat_allowed_today), "${f.allowedToday}", GreenSafe) }
                }
                Spacer(Modifier.height(10.dp))
                FootprintStat(stringResource(R.string.stat_total_logged_events), "${f.loggedEventsAllTime}", TextSecondary)

                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(1.dp, CyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { exportFootprint(context, f) } }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.btn_export_snapshot), style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold, color = CyanPrimary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
