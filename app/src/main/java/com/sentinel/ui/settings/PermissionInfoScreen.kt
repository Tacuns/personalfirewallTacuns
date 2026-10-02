package com.sentinel.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R

private data class PermissionInfo(
    val name: String,
    val icon: ImageVector,
    val why: String
)

// Plain-English explanation for every permission this app declares in AndroidManifest.xml.
// Keyed by the real Android permission constant so the list can never drift from the manifest.
@Composable
private fun permissionExplanations(): Map<String, PermissionInfo> = mapOf(
    "android.permission.FOREGROUND_SERVICE" to PermissionInfo(
        stringResource(R.string.perm_foreground_service_name), Icons.Default.Security,
        stringResource(R.string.perm_foreground_service_why)
    ),
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" to PermissionInfo(
        stringResource(R.string.perm_foreground_service_special_name), Icons.Default.VpnKey,
        stringResource(R.string.perm_foreground_service_special_why)
    ),
    "android.permission.POST_NOTIFICATIONS" to PermissionInfo(
        stringResource(R.string.perm_post_notifications_name), Icons.Default.Notifications,
        stringResource(R.string.perm_post_notifications_why)
    ),
    "android.permission.INTERNET" to PermissionInfo(
        stringResource(R.string.perm_internet_name), Icons.Default.Dns,
        stringResource(R.string.perm_internet_why)
    ),
    "android.permission.ACCESS_NETWORK_STATE" to PermissionInfo(
        stringResource(R.string.perm_network_state_name), Icons.Default.NetworkCheck,
        stringResource(R.string.perm_network_state_why)
    ),
    "android.permission.RECEIVE_BOOT_COMPLETED" to PermissionInfo(
        stringResource(R.string.perm_boot_completed_name), Icons.Default.Autorenew,
        stringResource(R.string.perm_boot_completed_why)
    ),
    "android.permission.QUERY_ALL_PACKAGES" to PermissionInfo(
        stringResource(R.string.perm_query_all_packages_name), Icons.Default.PhoneAndroid,
        stringResource(R.string.perm_query_all_packages_why)
    )
)

private fun friendlyLabel(permission: String, explanations: Map<String, PermissionInfo>): String =
    explanations[permission]?.name ?: permission.substringAfterLast('.')
        .lowercase().replace('_', ' ')
        .replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val explanations = permissionExplanations()

    // Reads the REAL permission list from the manifest at runtime — this list can never
    // go out of sync with what the app actually declares, because it is not hand-copied.
    val declaredPermissions = remember {
        try {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_permissions_title), style = MaterialTheme.typography.titleSmall,
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
                Text(
                    text = stringResource(R.string.perm_intro, declaredPermissions.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            items(declaredPermissions) { permission ->
                val info = explanations[permission]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanPrimary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = info?.icon ?: Icons.Default.Security,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friendlyLabel(permission, explanations),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = info?.why ?: stringResource(R.string.perm_default_reason),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = permission,
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.perm_manage_system_settings),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
