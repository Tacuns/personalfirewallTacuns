package com.sentinel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sentinel.core.rules.BlocklistSource
import com.sentinel.core.vpn.BlocklistSyncStatus
import com.sentinel.core.vpn.CustomBlocklist
import com.sentinel.ui.components.GradientButton
import com.sentinel.ui.components.InlineAlert
import com.sentinel.ui.components.QuietButton
import com.sentinel.ui.components.SecureCard
import com.sentinel.ui.components.SecureField
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.theme.RedCritical
import com.sentinel.ui.viewmodel.CustomListResult
import com.tacu.nsfwzerotrust.R

/**
 * "Your own lists": blocklists the user adds by link, up to [CustomBlocklist.MAX_LISTS].
 * Built from the shared glass components so it matches the app's newer screens.
 */
@Composable
fun CustomBlocklistsCard(
    lists:       List<BlocklistSource>,
    syncingKeys: Set<String>,
    errorFor:    (BlocklistSource) -> String?,
    intervalFor: (String) -> Int,
    onInterval:  (String, Int) -> Unit,
    onToggle:    (String, Boolean) -> Unit,
    onRefresh:   (String) -> Unit,
    onRemove:    (String) -> Unit,
    onAdd:       (name: String, link: String, onResult: (CustomListResult) -> Unit) -> Unit
) {
    var showAdd      by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<BlocklistSource?>(null) }

    SecureCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(SecurePalette.Orange.copy(alpha = 0.14f))
                    .border(1.dp, SecurePalette.Orange.copy(alpha = 0.28f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Link, null, tint = SecurePalette.Orange, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.blocklist_custom_title), fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain)
                Text(
                    stringResource(R.string.blocklist_custom_subtitle,
                        lists.size.toString(), CustomBlocklist.MAX_LISTS.toString()),
                    fontSize = 12.sp, color = SecurePalette.TextSoft, lineHeight = 16.sp
                )
            }
        }

        if (lists.isEmpty()) {
            Text(stringResource(R.string.blocklist_custom_empty), fontSize = 12.sp, color = SecurePalette.TextFaint)
        } else {
            lists.forEachIndexed { index, source ->
                if (index > 0) HorizontalDivider(color = SecurePalette.Edge, thickness = 1.dp)
                CustomListRow(
                    source    = source,
                    syncing   = source.sourceKey in syncingKeys,
                    error     = errorFor(source),
                    interval  = intervalFor(source.sourceKey),
                    onInterval = { onInterval(source.sourceKey, it) },
                    onToggle  = onToggle,
                    onRefresh = onRefresh,
                    onRemove  = { removeTarget = source }
                )
            }
        }

        if (lists.size < CustomBlocklist.MAX_LISTS) {
            GradientButton(
                label   = stringResource(R.string.blocklist_custom_add),
                onClick = { showAdd = true },
                icon    = Icons.Default.Add
            )
        } else {
            Text(stringResource(R.string.blocklist_custom_max, CustomBlocklist.MAX_LISTS.toString()),
                fontSize = 12.sp, color = SecurePalette.TextSoft)
        }
        Text(stringResource(R.string.blocklist_custom_tip), fontSize = 11.5.sp,
            color = SecurePalette.TextFaint, lineHeight = 16.sp)
    }

    if (showAdd) {
        AddCustomListDialog(onDismiss = { showAdd = false }, onAdd = onAdd)
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor   = SecurePalette.GlassTop,
            shape            = RoundedCornerShape(24.dp),
            title = {
                Text(stringResource(R.string.blocklist_custom_remove_title), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = SecurePalette.TextMain)
            },
            text = {
                Text(stringResource(R.string.blocklist_custom_remove_text), fontSize = 12.5.sp,
                    color = SecurePalette.TextSoft, lineHeight = 18.sp)
            },
            confirmButton = {
                TextButton(onClick = { onRemove(target.sourceKey); removeTarget = null }) {
                    Text(stringResource(R.string.btn_delete), color = RedCritical, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(stringResource(R.string.btn_cancel), color = SecurePalette.TextSoft)
                }
            }
        )
    }
}

@Composable
private fun CustomListRow(
    source:    BlocklistSource,
    syncing:   Boolean,
    error:     String?,
    interval:  Int,
    onInterval: (Int) -> Unit,
    onToggle:  (String, Boolean) -> Unit,
    onRefresh: (String) -> Unit,
    onRemove:  () -> Unit
) {
    var showInterval by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(source.displayName, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                    color = SecurePalette.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(source.url, fontSize = 11.sp, color = SecurePalette.TextFaint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val stats = when {
                    source.domainCount > 0 -> stringResource(R.string.blocklist_stats_domains_age,
                        listCount(source.domainCount), listAge(source.lastUpdatedMs))
                    source.enabled -> stringResource(R.string.blocklist_stats_pending)
                    else           -> stringResource(R.string.blocklist_stats_not_downloaded)
                }
                Text(stats, fontSize = 11.5.sp, color = SecurePalette.TextSoft)
                if (source.enabled) {
                    BlocklistUpdateLine(source, interval, SecurePalette.Orange) { showInterval = true }
                }
            }
            when {
                syncing -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SecurePalette.Orange)
                source.enabled -> IconButton(onClick = { onRefresh(source.sourceKey) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.cd_refresh),
                        tint = SecurePalette.Orange, modifier = Modifier.size(17.dp))
                }
            }
            Switch(
                checked         = source.enabled,
                onCheckedChange = { onToggle(source.sourceKey, it) },
                enabled         = !syncing,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor    = Color.White,
                    checkedTrackColor    = SecurePalette.Orange,
                    uncheckedThumbColor  = SecurePalette.TextFaint,
                    uncheckedTrackColor  = Color.White.copy(alpha = 0.06f),
                    uncheckedBorderColor = SecurePalette.EdgeStrong
                )
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.DeleteOutline, stringResource(R.string.cd_remove_list, source.displayName),
                    tint = RedCritical.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
        }
        error?.let { BlocklistErrorLine(it, hasStoredCopy = source.domainCount > 0) }
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

@Composable
private fun AddCustomListDialog(
    onDismiss: () -> Unit,
    onAdd:     (name: String, link: String, onResult: (CustomListResult) -> Unit) -> Unit
) {
    var name  by remember { mutableStateOf("") }
    var link  by remember { mutableStateOf("") }
    var busy  by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun submit() {
        if (link.isBlank() || busy) return
        busy = true
        error = null
        onAdd(name, link) { result ->
            busy = false
            when (result) {
                CustomListResult.ADDED, CustomListResult.NOT_ALLOWED -> onDismiss()
                CustomListResult.INVALID_LINK -> error = R.string.blocklist_custom_err_link
                CustomListResult.DUPLICATE    -> error = R.string.blocklist_custom_err_duplicate
                CustomListResult.LIMIT        -> error = R.string.blocklist_custom_max
                CustomListResult.FAILED       -> error = R.string.app_lock_error_generic
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SecurePalette.GlassBrush)
                .border(1.dp, SecurePalette.EdgeStrong, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.blocklist_custom_dialog_title), fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = SecurePalette.TextMain)
            SecureField(name, { name = it }, stringResource(R.string.blocklist_custom_name), Icons.AutoMirrored.Filled.Label,
                enabled = !busy)
            SecureField(link, { link = it; error = null }, stringResource(R.string.blocklist_custom_url), Icons.Default.Link,
                isError = error != null, enabled = !busy, imeAction = ImeAction.Done, onDone = { submit() })
            error?.let { res ->
                InlineAlert(
                    if (res == R.string.blocklist_custom_max)
                        stringResource(res, CustomBlocklist.MAX_LISTS.toString())
                    else stringResource(res)
                )
            }
            Text(stringResource(R.string.blocklist_custom_tip), fontSize = 11.5.sp,
                color = SecurePalette.TextFaint, lineHeight = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuietButton(stringResource(R.string.btn_cancel), onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                GradientButton(
                    label    = stringResource(R.string.blocklist_custom_add),
                    onClick  = { submit() },
                    modifier = Modifier.weight(1f),
                    enabled  = link.isNotBlank() && !busy,
                    loading  = busy
                )
            }
        }
    }
}

private fun listCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000     -> "${count / 1_000}k"
    else               -> count.toString()
}

@Composable
private fun listAge(ms: Long): String {
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
