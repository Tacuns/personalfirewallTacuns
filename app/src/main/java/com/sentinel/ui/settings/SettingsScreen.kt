package com.sentinel.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.sentinel.ui.lock.AppLockAuth
import com.sentinel.ui.lock.AppLockSetupContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tacu.nsfwzerotrust.BuildConfig
import com.tacu.nsfwzerotrust.R
import com.sentinel.ui.components.HelpContent
import com.sentinel.ui.components.HelpDialog
import com.sentinel.ui.components.HelpIcon
import com.sentinel.ui.theme.*
import com.sentinel.core.logs.LogRetention
import com.sentinel.ui.viewmodel.SettingsViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Screen root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToBlocklists: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToVpnNetworkPrivacy: () -> Unit = {},
    onNavigateToDataSafety: () -> Unit = {},
    onNavigateToPrivacyDashboard: () -> Unit = {},
    onNavigateToSecurityChecklist: () -> Unit = {},
    onNavigateToChangeHistory: () -> Unit = {},
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToDns: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToAppLockUsers: () -> Unit = {}
) {
    val bootOnStart by viewModel.bootOnStart.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appLockConfig by viewModel.appLockConfig.collectAsState()
    val firewallOptions by viewModel.firewallOptions.collectAsState()
    val logStats by viewModel.logStats.collectAsState()
    val adListEnabled by viewModel.adListEnabled.collectAsState()

    // Real row count and on-disk size, read when the screen opens rather than kept live,
    // so nothing polls the database in the background.
    LaunchedEffect(Unit) { viewModel.refreshLogStats() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        SettingsHeader()

        Spacer(Modifier.height(20.dp))

        SettingsSectionHeader(stringResource(R.string.section_protection))
        Spacer(Modifier.height(8.dp))
        ProtectionCard(bootOnStart = bootOnStart, onBootToggle = { viewModel.setBootOnStart(it) })
        Spacer(Modifier.height(8.dp))
        ProtectionLevelCard(
            current = com.sentinel.core.rules.detectProfile(firewallOptions, adListEnabled),
            onApply = { profile, done -> viewModel.applyProfile(profile, done) }
        )
        Spacer(Modifier.height(8.dp))
        AppLockCard(
            enabled        = appLockEnabled,
            hasCredentials = appLockConfig.hasCredentials,
            userCount      = appLockConfig.users.size,
            busy           = viewModel.appLockBusy,
            onToggle       = { viewModel.setAppLockEnabled(it) },
            onCreate       = { u, p, q, a, c -> viewModel.createAppLock(u, p, q, a, c) },
            onManage       = onNavigateToAppLockUsers
        )
        Spacer(Modifier.height(8.dp))
        AlwaysOnVpnCard()
        Spacer(Modifier.height(20.dp))
        SettingsSectionHeader(stringResource(R.string.extra_protection_title).uppercase())
        Spacer(Modifier.height(8.dp))
        ExtraProtectionCard(
            options      = firewallOptions,
            onWatchOnly  = { viewModel.setWatchOnly(it) },
            onLookalikes = { viewModel.setBlockLookalikes(it) },
            onSafeSearch = { viewModel.setSafeSearch(it) },
            onSafeSearchYouTube = { viewModel.setSafeSearchYouTube(it) }
        )
        Spacer(Modifier.height(8.dp))
        SettingsNavRow(
            icon = Icons.Default.Dns, iconTint = CyanPrimary,
            title = stringResource(R.string.dns_settings_title),
            subtitle = stringResource(R.string.dns_row_subtitle),
            onClick = onNavigateToDns
        )
        Spacer(Modifier.height(8.dp))
        SettingsNavRow(
            icon = Icons.Default.Language, iconTint = BlueAccent,
            title = stringResource(R.string.settings_language_title),
            subtitle = stringResource(R.string.settings_language_subtitle),
            onClick = onNavigateToLanguage
        )

        Spacer(Modifier.height(20.dp))

        SettingsSectionHeader(stringResource(R.string.section_blocklists))
        Spacer(Modifier.height(8.dp))
        BlocklistManagementRow(onClick = onNavigateToBlocklists)

        Spacer(Modifier.height(20.dp))

        SettingsSectionHeader(stringResource(R.string.section_privacy))
        Spacer(Modifier.height(8.dp))
        PrivacyCard()
        Spacer(Modifier.height(8.dp))
        ActivityHistoryCard(
            retentionHours = firewallOptions.logRetentionHours,
            stats          = logStats,
            onRefresh      = { viewModel.refreshLogStats() },
            onChoose       = { viewModel.setLogRetentionHours(it) }
        )

        Spacer(Modifier.height(20.dp))

        SettingsSectionHeader(stringResource(R.string.section_privacy_security))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsNavRow(
                icon = Icons.Default.Lock, iconTint = CyanPrimary,
                title = stringResource(R.string.settings_permissions_title),
                subtitle = stringResource(R.string.settings_permissions_subtitle),
                onClick = onNavigateToPermissions
            )
            SettingsNavRow(
                icon = Icons.Default.VpnKey, iconTint = BlueAccent,
                title = stringResource(R.string.settings_vpn_privacy_title),
                subtitle = stringResource(R.string.settings_vpn_privacy_subtitle),
                onClick = onNavigateToVpnNetworkPrivacy
            )
            SettingsNavRow(
                icon = Icons.Default.Storage, iconTint = GreenSafe,
                title = stringResource(R.string.settings_data_safety_title),
                subtitle = stringResource(R.string.settings_data_safety_subtitle),
                onClick = onNavigateToDataSafety
            )
            SettingsNavRow(
                icon = Icons.Default.Security, iconTint = AmberMedium,
                title = stringResource(R.string.settings_privacy_dashboard_title),
                subtitle = stringResource(R.string.settings_privacy_dashboard_subtitle),
                onClick = onNavigateToPrivacyDashboard
            )
            SettingsNavRow(
                icon = Icons.Default.CheckCircle, iconTint = GreenSafe,
                title = stringResource(R.string.settings_security_checklist_title),
                subtitle = stringResource(R.string.settings_security_checklist_subtitle),
                onClick = onNavigateToSecurityChecklist
            )
            SettingsNavRow(
                icon = Icons.Default.NotificationsActive, iconTint = AmberMedium,
                title = stringResource(R.string.alerts_title),
                subtitle = stringResource(R.string.settings_alerts_subtitle),
                onClick = onNavigateToAlerts
            )
            SettingsNavRow(
                icon = Icons.Default.History, iconTint = BlueAccent,
                title = stringResource(R.string.ch_title),
                subtitle = stringResource(R.string.ch_settings_desc),
                onClick = onNavigateToChangeHistory
            )
        }

        Spacer(Modifier.height(20.dp))

        SettingsSectionHeader(stringResource(R.string.section_about))
        Spacer(Modifier.height(8.dp))
        AboutCard()

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHeader() {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(CyanPrimary, BlueAccent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Settings,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text          = stringResource(R.string.settings_header_title),
                style         = MaterialTheme.typography.labelLarge,
                fontWeight    = FontWeight.Bold,
                color         = TextPrimary,
                letterSpacing = 0.5.sp
            )
            Text(
                text  = stringResource(R.string.settings_header_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header (same pattern as DashboardScreen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
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
            letterSpacing = 1.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Protection Card — boot toggle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProtectionCard(bootOnStart: Boolean, onBootToggle: (Boolean) -> Unit) {
    var showHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onBootToggle(!bootOnStart) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF062028)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint               = CyanPrimary,
                    modifier           = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = stringResource(R.string.boot_on_start_title),
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
                Text(
                    text  = stringResource(R.string.boot_on_start_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            HelpIcon(onClick = { showHelp = true })
            Switch(
                checked          = bootOnStart,
                onCheckedChange  = onBootToggle,
                colors           = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = CyanPrimary,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = BgBorder
                )
            )
        }
    }

    if (showHelp) {
        HelpDialog(content = bootOnStartHelp(), onDismiss = { showHelp = false })
    }
}

/**
 * App Lock toggle.
 *
 * Enabling and creating accounts are now SEPARATE. The setup form only appears the first
 * time, when no account exists yet; after that the switch just flips a flag and is
 * instant, because no key derivation is involved. Turning the lock off keeps the accounts,
 * so turning it back on never re-runs setup.
 *
 * Enabling is still impossible without an account, so the app can never be locked with no
 * way back in.
 */
@Composable
private fun AppLockCard(
    enabled: Boolean,
    hasCredentials: Boolean,
    userCount: Int,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onCreate: (String, String, String, String, String) -> Unit,
    onManage: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var showHelp   by remember { mutableStateOf(false) }

    fun attempt(next: Boolean) {
        if (busy) return
        // Only the very first enable needs the setup form.
        if (next && !hasCredentials) showDialog = true else onToggle(next)
    }

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
                    .clickable { attempt(!enabled) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF062028)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Lock,
                        contentDescription = null,
                        tint               = CyanPrimary,
                        modifier           = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = stringResource(R.string.app_lock_setting_title),
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = TextPrimary
                    )
                    Text(
                        text  = stringResource(R.string.app_lock_setting_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                HelpIcon(onClick = { showHelp = true })
                if (busy) {
                    // Setting up takes a few seconds (key derivation). Without this the
                    // switch looked broken, because nothing visibly happened.
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = CyanPrimary
                    )
                } else {
                    Switch(
                        checked         = enabled,
                        onCheckedChange = { attempt(it) },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = Color.White,
                            checkedTrackColor   = CyanPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BgBorder
                        )
                    )
                }
            }

            // Account management appears only once an account exists.
            if (hasCredentials) {
                HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onManage() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF062028)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.ManageAccounts,
                            contentDescription = null,
                            tint               = BlueAccent,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = stringResource(R.string.app_lock_manage_title),
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = TextPrimary
                        )
                        Text(
                            text  = stringResource(R.string.app_lock_manage_subtitle, userCount, AppLockAuth.MAX_USERS),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = TextMuted, modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 460.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(com.sentinel.ui.components.SecurePalette.Base)
                    .border(1.dp, com.sentinel.ui.components.SecurePalette.EdgeStrong, RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                AppLockSetupContent(
                    onCancel   = { showDialog = false },
                    onComplete = { u, p, q, a, c ->
                        showDialog = false
                        onCreate(u, p, q, a, c)
                    }
                )
            }
        }
    }

    if (showHelp) {
        HelpDialog(content = appLockHelp(), onDismiss = { showHelp = false })
    }
}

@Composable
private fun appLockHelp() = HelpContent(
    title = stringResource(R.string.app_lock_help_title),
    whatItIs = stringResource(R.string.app_lock_help_what_it_is),
    whatItDoes = stringResource(R.string.app_lock_help_what_it_does),
    why = stringResource(R.string.app_lock_help_why),
    benefit = stringResource(R.string.app_lock_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.app_lock_help_practice_1),
        stringResource(R.string.app_lock_help_practice_2),
        stringResource(R.string.app_lock_help_practice_3)
    )
)

@Composable
private fun bootOnStartHelp() = HelpContent(
    title = stringResource(R.string.boot_on_start_help_title),
    whatItIs = stringResource(R.string.boot_on_start_help_what_it_is),
    whatItDoes = stringResource(R.string.boot_on_start_help_what_it_does),
    why = stringResource(R.string.boot_on_start_help_why),
    benefit = stringResource(R.string.boot_on_start_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.boot_on_start_help_practice_1)
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Privacy Card — on-device only + VPN disclosure
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrivacyCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            PrivacyRow(
                icon      = Icons.Default.Lock,
                iconColor = GreenSafe,
                iconBg    = BgActive,
                title     = stringResource(R.string.ondevice_only_title),
                body      = stringResource(R.string.ondevice_only_body),
                help      = onDeviceOnlyHelp()
            )
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 14.dp),
                color     = BgBorder.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )
            PrivacyRow(
                icon      = Icons.Default.VerifiedUser,
                iconColor = BlueAccent,
                iconBg    = Color(0xFF06122A),
                title     = stringResource(R.string.vpn_disclosure_title),
                body      = stringResource(R.string.vpn_disclosure_body),
                help      = vpnDisclosureHelp()
            )
        }
    }
}

@Composable
private fun PrivacyRow(
    icon:      ImageVector,
    iconColor: Color,
    iconBg:    Color,
    title:     String,
    body:      String,
    help:      HelpContent? = null
) {
    var showHelp by remember { mutableStateOf(false) }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconColor,
                modifier           = Modifier.size(17.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = body,
                style      = MaterialTheme.typography.labelSmall,
                color      = TextSecondary,
                lineHeight = 16.sp
            )
        }
        if (help != null) {
            HelpIcon(onClick = { showHelp = true })
        }
    }

    if (help != null && showHelp) {
        HelpDialog(content = help, onDismiss = { showHelp = false })
    }
}

@Composable
private fun vpnDisclosureHelp() = HelpContent(
    title = stringResource(R.string.vpn_disclosure_help_title),
    whatItIs = stringResource(R.string.vpn_disclosure_help_what_it_is),
    whatItDoes = stringResource(R.string.vpn_disclosure_help_what_it_does),
    why = stringResource(R.string.vpn_disclosure_help_why),
    benefit = stringResource(R.string.vpn_disclosure_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.vpn_disclosure_help_practice_1)
    )
)

@Composable
private fun onDeviceOnlyHelp() = HelpContent(
    title = stringResource(R.string.ondevice_only_help_title),
    whatItIs = stringResource(R.string.ondevice_only_help_what_it_is),
    whatItDoes = stringResource(R.string.ondevice_only_help_what_it_does),
    why = stringResource(R.string.ondevice_only_help_why),
    benefit = stringResource(R.string.ondevice_only_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.ondevice_only_help_practice_1),
        stringResource(R.string.ondevice_only_help_practice_2)
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// About Card — version + badges
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(CyanPrimary, BlueAccent))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Security,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = stringResource(R.string.app_name),
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary
                    )
                    Text(
                        text  = stringResource(R.string.about_version_prefix, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AboutBadge(stringResource(R.string.about_badge_no_root))
                AboutBadge(stringResource(R.string.about_badge_local_only))
                AboutBadge(stringResource(R.string.about_badge_dns_firewall))
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)

            val context = LocalContext.current
            var showLicenses by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tacuns.net/apps/tacuns-firewall/privacy-policy"))
                        )
                    }
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgBorder.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Policy, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                }
                Text(
                    stringResource(R.string.about_privacy_policy),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp, modifier = Modifier.padding(top = 12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLicenses = true }
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgBorder.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Description, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                }
                Text(
                    stringResource(R.string.about_open_source_notices),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }

            if (showLicenses) {
                AlertDialog(
                    onDismissRequest  = { showLicenses = false },
                    containerColor    = BgCard,
                    title = {
                        Text(
                            stringResource(R.string.about_open_source_notices),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = TextPrimary
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Text("StevenBlack Unified Hosts", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(stringResource(R.string.license_mit), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("github.com/StevenBlack/hosts", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                            Column {
                                Text("ip-location-db · server-country", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(stringResource(R.string.license_pddl_country), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("github.com/sapics/ip-location-db", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            // Software inside the app (licences as declared in each library's
                            // published Maven metadata, checked 2026-10-02).
                            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                            Column {
                                Text("AndroidX · Jetpack Compose · Material Components · Kotlin · kotlinx.coroutines · Coil · MaxMind DB Reader",
                                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(stringResource(R.string.license_apache2), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("apache.org/licenses/LICENSE-2.0", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLicenses = false }) {
                            Text(stringResource(R.string.btn_close), color = CyanPrimary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AboutBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(BgBorder.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text       = text,
            fontSize   = 10.sp,
            color      = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Generic Settings nav row — used by the PRIVACY & SECURITY section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Blocklist Management Row — navigates to BlocklistScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlocklistManagementRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF062028)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.blocklist_manage_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    stringResource(R.string.blocklist_manage_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── Activity history retention (F1) ──────────────────────────────────────────
// The window the user picks decides how much of their own history the phone keeps.
// Every number shown here is read from the database, never estimated.

@Composable
private fun ActivityHistoryCard(
    retentionHours: Int,
    stats: SettingsViewModel.LogStats,
    onRefresh: () -> Unit,
    onChoose: (Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var pendingOff by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val loggingOff = retentionHours == LogRetention.OFF

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
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (loggingOff) BgActive else Color(0xFF1A1206)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = if (loggingOff) GreenSafe else AmberMedium,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.log_retention_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = retentionLabel(retentionHours),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (loggingOff) GreenSafe else AmberMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.log_retention_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
                HelpIcon(onClick = { showHelp = true })
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = BgBorder.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // Honest state: nothing kept means nothing to describe, and we say so
            // instead of printing a zero that looks like a measurement.
            Text(
                text = when {
                    !stats.loaded -> stringResource(R.string.log_retention_reading)
                    loggingOff    -> stringResource(R.string.log_retention_stats_off)
                    stats.rows == 0 -> stringResource(R.string.log_retention_stats_empty)
                    else -> stringResource(
                        R.string.log_retention_stats,
                        stats.rows,
                        formatBytes(stats.bytesOnDisk)
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }

    if (showHelp) {
        HelpDialog(content = logRetentionHelp(), onDismiss = { showHelp = false })
    }

    if (showPicker) {
        RetentionPickerDialog(
            current = retentionHours,
            onDismiss = { showPicker = false },
            onPick = { picked ->
                showPicker = false
                // Turning logging off deletes what is already stored, so it is confirmed first.
                if (picked == LogRetention.OFF && retentionHours != LogRetention.OFF) {
                    pendingOff = true
                } else if (picked != retentionHours) {
                    onChoose(picked)
                } else {
                    onRefresh()
                }
            }
        )
    }

    if (pendingOff) {
        AlertDialog(
            onDismissRequest = { pendingOff = false },
            containerColor = BgCard,
            title = {
                Text(
                    stringResource(R.string.log_retention_off_confirm_title),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    stringResource(R.string.log_retention_off_confirm_body),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingOff = false
                    onChoose(LogRetention.OFF)
                }) {
                    Text(stringResource(R.string.log_retention_off_confirm_yes), color = RedCritical)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOff = false }) {
                    Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun RetentionPickerDialog(
    current: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text(
                stringResource(R.string.log_retention_title),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                LogRetention.CHOICES.forEach { hours ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(hours) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = hours == current,
                            onClick = { onPick(hours) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AmberMedium,
                                unselectedColor = TextSecondary
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = retentionLabel(hours),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            if (hours == LogRetention.OFF) {
                                Text(
                                    text = stringResource(R.string.log_retention_off_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun retentionLabel(hours: Int): String = when (hours) {
    LogRetention.OFF      -> stringResource(R.string.log_retention_off)
    LogRetention.HOURS_6  -> stringResource(R.string.log_retention_6h)
    LogRetention.HOURS_24 -> stringResource(R.string.log_retention_24h)
    LogRetention.DAYS_7   -> stringResource(R.string.log_retention_7d)
    LogRetention.DAYS_30  -> stringResource(R.string.log_retention_30d)
    else                  -> stringResource(R.string.log_retention_7d)
}

/** Plain size text. Uses the same 1024 steps the platform uses for storage figures. */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L         -> String.format(java.util.Locale.US, "%d KB", bytes / 1024L)
    else                   -> String.format(java.util.Locale.US, "%d B", bytes)
}

@Composable
private fun logRetentionHelp() = HelpContent(
    title = stringResource(R.string.log_retention_help_title),
    whatItIs = stringResource(R.string.log_retention_help_what_it_is),
    whatItDoes = stringResource(R.string.log_retention_help_what_it_does),
    why = stringResource(R.string.log_retention_help_why),
    benefit = stringResource(R.string.log_retention_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.log_retention_help_practice_1),
        stringResource(R.string.log_retention_help_practice_2)
    )
)
