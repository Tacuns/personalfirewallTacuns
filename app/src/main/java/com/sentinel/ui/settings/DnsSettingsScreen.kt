package com.sentinel.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.core.vpn.DnsResolverChoice
import com.sentinel.ui.components.GradientButton
import com.sentinel.ui.components.InlineAlert
import com.sentinel.ui.components.ScreenHeaderCard
import com.sentinel.ui.components.SecureBackground
import com.sentinel.ui.components.SecureCard
import com.sentinel.ui.components.SecureField
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.components.SecureTopBar
import com.sentinel.ui.components.SectionLabel
import com.sentinel.ui.theme.AmberMedium
import com.sentinel.ui.theme.CyanPrimary
import com.sentinel.ui.theme.GreenSafe
import com.sentinel.ui.theme.RedCritical
import com.sentinel.ui.viewmodel.SettingsViewModel
import com.tacu.nsfwzerotrust.R
import kotlinx.coroutines.delay

/**
 * Settings › DNS: where the firewall sends the lookups it allows. A main server and an
 * optional backup, both chosen by the user; the backup is only used when one is chosen.
 * The status line is read from the running firewall, never guessed.
 */
@Composable
fun DnsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val options by viewModel.firewallOptions.collectAsState()
    val info by viewModel.dnsInfo.collectAsState()

    // Live status while the screen is open; stops when it closes.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshDnsInfo()
            delay(3_000)
        }
    }

    SecureBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            SecureTopBar(stringResource(R.string.dns_settings_title), onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ScreenHeaderCard(
                    icon = Icons.Default.Dns, tint = CyanPrimary,
                    title = stringResource(R.string.dns_settings_title),
                    subtitle = stringResource(R.string.dns_subtitle)
                )

                DnsStatusCard(options.dnsBackup, info)

                if (info.privateDnsActive) {
                    InlineAlert(stringResource(R.string.dns_private_dns_note), color = AmberMedium,
                        icon = Icons.Default.Info)
                }

                ServerPicker(
                    title = stringResource(R.string.dns_main_title),
                    choices = DnsResolverChoice.MAIN_CHOICES,
                    selected = options.dnsMain,
                    customText = options.dnsMainCustom,
                    info = info,
                    onPick = { choice, custom -> viewModel.setDnsServer(false, choice, custom) }
                )
                ServerPicker(
                    title = stringResource(R.string.dns_backup_title),
                    subtitle = stringResource(R.string.dns_backup_subtitle),
                    choices = DnsResolverChoice.BACKUP_CHOICES,
                    selected = options.dnsBackup,
                    customText = options.dnsBackupCustom,
                    info = info,
                    onPick = { choice, custom -> viewModel.setDnsServer(true, choice, custom) }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DnsStatusCard(backupChoice: String, info: SettingsViewModel.DnsInfo) {
    val status = info.status
    val (color, text) = when {
        !info.vpnRunning -> SecurePalette.TextSoft to stringResource(R.string.dns_status_vpn_off)
        status == null -> SecurePalette.TextSoft to stringResource(R.string.dns_status_waiting)
        status.state == DnsResolverChoice.STATE_MAIN ->
            GreenSafe to stringResource(R.string.dns_status_main, status.server)
        status.state == DnsResolverChoice.STATE_BACKUP ->
            AmberMedium to stringResource(R.string.dns_status_backup, status.server)
        backupChoice == DnsResolverChoice.NONE ->
            RedCritical to stringResource(R.string.dns_status_failing_no_backup)
        else -> RedCritical to stringResource(R.string.dns_status_failing)
    }
    SecureCard {
        SectionLabel(stringResource(R.string.dns_status_title))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (color == RedCritical || color == AmberMedium) {
                Icon(Icons.Default.WarningAmber, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(text, fontSize = 13.sp, color = color, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ServerPicker(
    title: String,
    subtitle: String? = null,
    choices: List<String>,
    selected: String,
    customText: String,
    info: SettingsViewModel.DnsInfo,
    onPick: (choice: String, custom: String) -> Unit
) {
    var editing by remember(selected, customText) { mutableStateOf(selected == DnsResolverChoice.CUSTOM) }
    var draft by remember(customText) { mutableStateOf(customText) }
    val draftValid = DnsResolverChoice.isValidCustom(draft)

    SecureCard {
        SectionLabel(title)
        if (subtitle != null) {
            Text(subtitle, fontSize = 12.sp, color = SecurePalette.TextSoft, lineHeight = 16.sp)
        }
        choices.forEach { choice ->
            val isSelected = if (choice == DnsResolverChoice.CUSTOM) editing || selected == choice
                             else selected == choice && !editing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) {
                        if (choice == DnsResolverChoice.CUSTOM) editing = true
                        else { editing = false; onPick(choice, "") }
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadioButton(
                    selected = isSelected, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = CyanPrimary,
                        unselectedColor = SecurePalette.TextSoft)
                )
                Column(Modifier.weight(1f)) {
                    Text(dnsChoiceName(choice), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = SecurePalette.TextMain)
                    dnsChoiceDetail(choice, info)?.let {
                        Text(it, fontSize = 11.sp, color = SecurePalette.TextSoft, lineHeight = 15.sp)
                    }
                }
            }
        }
        if (editing) {
            SecureField(
                value = draft,
                onValueChange = { draft = it.trim() },
                label = stringResource(R.string.dns_custom_label),
                leadingIcon = Icons.Default.Router,
                isError = draft.isNotEmpty() && !draftValid,
                imeAction = ImeAction.Done,
                onDone = { if (draftValid) onPick(DnsResolverChoice.CUSTOM, draft) }
            )
            if (draft.isNotEmpty() && !draftValid) {
                Text(stringResource(R.string.dns_custom_invalid), fontSize = 12.sp, color = RedCritical)
            }
            GradientButton(
                label = stringResource(R.string.dns_custom_save),
                onClick = { onPick(DnsResolverChoice.CUSTOM, draft) },
                enabled = draftValid && !(selected == DnsResolverChoice.CUSTOM && draft == customText)
            )
        }
    }
}

/** The user's name for a DNS choice, shared with Change history. */
@Composable
fun dnsChoiceName(choice: String): String = when (choice) {
    DnsResolverChoice.SYSTEM -> stringResource(R.string.dns_choice_system)
    DnsResolverChoice.GOOGLE -> stringResource(R.string.dns_choice_google)
    DnsResolverChoice.CLOUDFLARE -> stringResource(R.string.dns_choice_cloudflare)
    DnsResolverChoice.CUSTOM -> stringResource(R.string.dns_choice_custom)
    DnsResolverChoice.NONE -> stringResource(R.string.dns_choice_none)
    else -> choice
}

@Composable
private fun dnsChoiceDetail(choice: String, info: SettingsViewModel.DnsInfo): String? = when (choice) {
    DnsResolverChoice.SYSTEM -> {
        val where = when (info.onWifi) {
            true -> stringResource(R.string.dns_system_wifi)
            false -> stringResource(R.string.dns_system_mobile)
            null -> stringResource(R.string.dns_system_unknown)
        }
        if (info.systemServers.isEmpty()) where
        else where + " · " + info.systemServers.joinToString(", ")
    }
    DnsResolverChoice.GOOGLE -> "8.8.8.8"
    DnsResolverChoice.CLOUDFLARE -> "1.1.1.1"
    DnsResolverChoice.CUSTOM -> stringResource(R.string.dns_custom_detail)
    DnsResolverChoice.NONE -> stringResource(R.string.dns_none_detail)
    else -> null
}
