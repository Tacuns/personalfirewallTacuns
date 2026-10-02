package com.sentinel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.ui.components.GradientButton
import com.sentinel.ui.components.InlineAlert
import com.sentinel.ui.components.QuietButton
import com.sentinel.ui.components.ScreenHeaderCard
import com.sentinel.ui.components.SecureBackground
import com.sentinel.ui.components.SecureCard
import com.sentinel.ui.components.SecureField
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.components.SecureTopBar
import com.sentinel.ui.lock.AppLockAuth
import com.sentinel.ui.lock.AppLockCrypto
import com.sentinel.ui.lock.AppLockThrottle
import com.sentinel.ui.lock.PasswordChecklist
import com.sentinel.ui.lock.rememberLockoutRemaining
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.SettingsViewModel
import com.tacu.nsfwzerotrust.R

/** Full access shows in the brand orange; view only in blue, so the two never look alike. */
private val RoleFullColor = SecurePalette.Orange
private val RoleViewColor = BlueAccent

/**
 * Manage App Lock accounts: add (up to [AppLockAuth.MAX_USERS]), remove, and change a
 * username or password.
 *
 * The screen is gated behind a sign-in first, so reaching Settings while the lock is
 * switched off is not enough to alter who can unlock the app. Changing a specific account
 * additionally requires THAT account's current password — verifying as one user must not
 * grant control over another user's credentials.
 */
@Composable
fun AppLockUsersScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val config by viewModel.appLockConfig.collectAsState()
    var verified by remember { mutableStateOf(false) }

    SecureBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            SecureTopBar(title = stringResource(R.string.app_lock_manage_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!verified) {
                    VerifyGate(
                        busy     = viewModel.appLockBusy,
                        lockDeadline = config.lockDeadline,
                        onVerify = { u, p, cb -> viewModel.verifyAppLock(u, p, cb) },
                        onPassed = { verified = true }
                    )
                } else {
                    UserList(
                        usernames = config.users.map { it.username },
                        readOnlyNames = config.users.filter { it.readOnly }.map { it.username }.toSet(),
                        busy      = viewModel.appLockBusy,
                        viewModel = viewModel
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun VerifyGate(
    busy: Boolean,
    lockDeadline: com.sentinel.ui.lock.AppLockThrottle.Deadline,
    onVerify: (String, String, (Boolean) -> Unit) -> Unit,
    onPassed: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var failed   by remember { mutableStateOf(false) }
    val waitMs = rememberLockoutRemaining(lockDeadline)
    val locked = waitMs > 0

    fun verify() {
        if (username.isBlank() || password.isEmpty() || busy || locked) return
        onVerify(username, password) { ok -> if (ok) onPassed() else failed = true }
    }

    ScreenHeaderCard(
        icon     = Icons.Default.VerifiedUser,
        tint     = SecurePalette.Orange,
        title    = stringResource(R.string.app_lock_verify_title),
        subtitle = stringResource(R.string.app_lock_verify_desc)
    )
    SecureCard {
        SecureField(username, { username = it; failed = false },
            stringResource(R.string.app_lock_username), Icons.Default.Person,
            isError = failed, enabled = !busy && !locked)
        SecureField(password, { password = it; failed = false },
            stringResource(R.string.app_lock_enter_password), Icons.Default.Key,
            isError = failed, enabled = !busy && !locked, isPassword = true,
            imeAction = ImeAction.Done, onDone = { verify() })
        if (locked) InlineAlert(stringResource(R.string.app_lock_error_locked, AppLockThrottle.format(waitMs)),
                                AmberMedium, Icons.Default.Timer)
        else if (failed) InlineAlert(stringResource(R.string.app_lock_error_credentials))
        GradientButton(
            label   = stringResource(R.string.app_lock_btn_verify),
            onClick = { verify() },
            enabled = username.isNotBlank() && password.isNotEmpty() && !busy && !locked,
            loading = busy,
            icon    = Icons.Default.VerifiedUser
        )
    }
}

@Composable
private fun UserList(
    usernames: List<String>,
    readOnlyNames: Set<String>,
    busy: Boolean,
    viewModel: SettingsViewModel
) {
    var showAdd    by remember { mutableStateOf(false) }
    var changeFor  by remember { mutableStateOf<String?>(null) }
    var message    by remember { mutableStateOf<String?>(null) }
    var showRoleTip by remember { mutableStateOf(false) }

    val errGeneric  = stringResource(R.string.app_lock_error_generic)
    val errLastUser = stringResource(R.string.app_lock_error_last_user)
    val errLastFull = stringResource(R.string.app_lock_error_last_full)

    ScreenHeaderCard(
        icon     = Icons.Default.ManageAccounts,
        tint     = SecurePalette.Orange,
        title    = stringResource(R.string.app_lock_manage_title),
        subtitle = stringResource(R.string.app_lock_manage_subtitle, usernames.size, AppLockAuth.MAX_USERS)
    )

    // What the two roles mean, always visible above the list.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, SecurePalette.Edge, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Info, null, tint = SecurePalette.Orange, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.app_lock_view_only_desc), fontSize = 12.sp,
            color = SecurePalette.TextSoft, lineHeight = 16.sp)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        usernames.forEach { name ->
            val viewOnly = name in readOnlyNames
            AccountCard(
                name       = name,
                viewOnly   = viewOnly,
                busy       = busy,
                canRemove  = usernames.size > 1 && !busy,
                onRoleTap  = {
                    viewModel.setAppLockUserReadOnly(name, !viewOnly) { ok ->
                        message = if (ok) null else errLastFull
                    }
                },
                onEdit     = { changeFor = name },
                onRemove   = {
                    viewModel.removeAppLockUser(name) { ok ->
                        if (!ok) message = if (usernames.size <= 1) errLastUser else errLastFull
                    }
                }
            )
        }
    }

    if (message != null) InlineAlert(message!!)

    if (usernames.size < AppLockAuth.MAX_USERS) {
        GradientButton(
            label   = stringResource(R.string.app_lock_add_title),
            onClick = { message = null; showAdd = true },
            enabled = !busy,
            loading = busy,
            icon    = Icons.Default.PersonAdd
        )
    } else {
        Text(
            stringResource(R.string.app_lock_max_users, AppLockAuth.MAX_USERS),
            fontSize = 12.sp,
            color = SecurePalette.TextFaint,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
    }

    if (showRoleTip) {
        AlertDialog(
            onDismissRequest = { showRoleTip = false },
            containerColor   = SecurePalette.GlassTop,
            shape            = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.ManageAccounts, null, tint = SecurePalette.Orange) },
            title = {
                Text(stringResource(R.string.app_lock_role_tip_title),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SecurePalette.TextMain)
            },
            text = {
                Text(stringResource(R.string.app_lock_role_tip_text),
                    fontSize = 12.5.sp, color = SecurePalette.TextSoft, lineHeight = 18.sp)
            },
            confirmButton = {
                TextButton(onClick = { showRoleTip = false }) {
                    Text(stringResource(R.string.app_lock_btn_done), color = SecurePalette.Orange, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showAdd) {
        CredentialDialog(
            titleRes        = R.string.app_lock_add_title,
            requireCurrent  = false,
            targetUsername  = "",
            onDismiss       = { showAdd = false },
            onSubmit        = { _, newUser, newPass ->
                showAdd = false
                viewModel.addAppLockUser(newUser, newPass) { ok ->
                    if (!ok) message = errGeneric else showRoleTip = true
                }
            }
        )
    }

    changeFor?.let { target ->
        CredentialDialog(
            titleRes       = R.string.app_lock_change_title,
            requireCurrent = true,
            targetUsername = target,
            onDismiss      = { changeFor = null },
            onSubmit       = { current, newUser, newPass ->
                changeFor = null
                viewModel.changeAppLockUser(target, current, newUser, newPass) { ok ->
                    if (!ok) message = errGeneric
                }
            }
        )
    }
}

/** One account: letter avatar, name, tappable role chip, edit and remove. */
@Composable
private fun AccountCard(
    name: String,
    viewOnly: Boolean,
    busy: Boolean,
    canRemove: Boolean,
    onRoleTap: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val roleColor = if (viewOnly) RoleViewColor else RoleFullColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SecurePalette.GlassBrush)
            .border(1.dp, SecurePalette.Edge, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (viewOnly) Brush.linearGradient(listOf(RoleViewColor.copy(alpha = 0.35f), RoleViewColor.copy(alpha = 0.15f)))
                    else SecurePalette.BrandGradient
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (viewOnly) RoleViewColor else Color.White
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (viewOnly) stringResource(R.string.app_lock_view_only_label)
                       else stringResource(R.string.app_lock_role_full),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = roleColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(roleColor.copy(alpha = 0.12f))
                    .border(1.dp, roleColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable(enabled = !busy) { onRoleTap() }
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        ActionTile(Icons.Default.Edit, stringResource(R.string.app_lock_change), SecurePalette.TextSoft, enabled = true, onClick = onEdit)
        ActionTile(Icons.Default.Delete, stringResource(R.string.app_lock_remove),
            if (canRemove) RedCritical else SecurePalette.TextFaint, enabled = canRemove, onClick = onRemove)
    }
}

@Composable
private fun ActionTile(icon: ImageVector, description: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, SecurePalette.Edge, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** Shared add/change dialog. Save stays disabled until every rule passes. */
@Composable
private fun CredentialDialog(
    titleRes: Int,
    requireCurrent: Boolean,
    targetUsername: String,
    onDismiss: () -> Unit,
    onSubmit: (current: String, username: String, password: String) -> Unit
) {
    var current  by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(targetUsername) }
    var password by remember { mutableStateOf("") }
    var confirm  by remember { mutableStateOf("") }

    val rulesOk = AppLockCrypto.isValid(password)
    val matches = password.isNotEmpty() && password == confirm
    val canSave = username.isNotBlank() && rulesOk && matches &&
                  (!requireCurrent || current.isNotEmpty())

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecurePalette.Orange.copy(alpha = 0.14f))
                        .border(1.dp, SecurePalette.Orange.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (requireCurrent) Icons.Default.Edit else Icons.Default.PersonAdd, null,
                        tint = SecurePalette.Orange, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(titleRes), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SecurePalette.TextMain)
            }

            if (requireCurrent) {
                SecureField(current, { current = it }, stringResource(R.string.app_lock_current_password),
                    Icons.Default.Key, isPassword = true)
            }
            SecureField(username, { username = it }, stringResource(R.string.app_lock_username), Icons.Default.Person)
            SecureField(password, { password = it }, stringResource(R.string.app_lock_new_password),
                Icons.Default.Key, isError = password.isNotEmpty() && !rulesOk, isPassword = true)
            PasswordChecklist(password)
            SecureField(confirm, { confirm = it }, stringResource(R.string.app_lock_confirm_password),
                Icons.Default.Key, isError = confirm.isNotEmpty() && !matches, isPassword = true,
                imeAction = ImeAction.Done)
            if (confirm.isNotEmpty() && !matches) InlineAlert(stringResource(R.string.app_lock_error_mismatch))

            Row(verticalAlignment = Alignment.CenterVertically) {
                QuietButton(stringResource(R.string.btn_cancel), onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                GradientButton(
                    label    = stringResource(R.string.btn_save),
                    onClick  = { onSubmit(current, username, password) },
                    modifier = Modifier.weight(1f),
                    enabled  = canSave
                )
            }
        }
    }
}
