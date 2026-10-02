package com.sentinel.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.sentinel.core.settings.AppPreferencesRepository.AppLockConfig
import com.sentinel.core.vpn.vpnRunning
import com.sentinel.core.vpn.vpnRunningFlow
import com.sentinel.ui.components.GradientButton
import com.sentinel.ui.components.InlineAlert
import com.sentinel.ui.components.QuietButton
import com.sentinel.ui.components.SecureBackground
import com.sentinel.ui.components.SecureCard
import com.sentinel.ui.components.SecureField
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.components.TacunsLogoMark
import com.sentinel.ui.components.SecurityEmblem
import com.sentinel.ui.components.StatusChip
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the "already unlocked" flag.
 *
 * It lives in a ViewModel so it survives configuration changes — the Activity has no
 * android:configChanges, so rotating the device recreates it, and an Activity field
 * would re-lock the app on every rotation. The ViewModel is cleared when the Activity
 * genuinely finishes, so a cold start still locks.
 */
class AppLockViewModel : ViewModel() {
    var unlocked by mutableStateOf(false)
}

/** What the lock screen is currently asking for. */
private enum class Phase { UNLOCK, RECOVER, SET_NEW }

/**
 * The App Lock gate: unlock, recover, and set-new-credentials in one self-contained tree.
 *
 * Deliberately free of fragments, system activities and lifecycle callbacks. The earlier
 * BiometricPrompt version failed intermittently because it coupled a fragment-based API to
 * Compose state across an activity boundary; a dismissed prompt could skip its error
 * callback and leave this screen unresponsive.
 *
 * Gates the UI process ONLY — SentinelVpnService runs in the :vpn process and BootReceiver
 * starts independently, so the firewall keeps protecting the device, and still auto-starts
 * after a reboot, while this screen is showing.
 */
@Composable
fun AppLockScreen(
    config: AppLockConfig,
    onUnlocked: (matchedUsername: String) -> Unit,
    onAttempt: (success: Boolean) -> Unit,
    onSaveCredentials: (username: String, password: String, question: String, answer: String, recoveryCode: String) -> Unit
) {
    // Keep credential entry out of screenshots and the recents thumbnail.
    com.sentinel.ui.components.SecureScreen()

    var phase by remember { mutableStateOf(Phase.UNLOCK) }

    when (phase) {
        Phase.UNLOCK -> UnlockPhase(
            config      = config,
            onUnlocked  = onUnlocked,
            onAttempt   = onAttempt,
            onForgot    = { phase = Phase.RECOVER }
        )
        Phase.RECOVER -> RecoverPhase(
            config     = config,
            onVerified = { phase = Phase.SET_NEW },
            onAttempt  = onAttempt,
            onCancel   = { phase = Phase.UNLOCK }
        )
        Phase.SET_NEW -> LockScaffold {
            AppLockSetupContent(
                onCancel   = { phase = Phase.UNLOCK },
                onComplete = { u, p, q, a, code ->
                    onSaveCredentials(u, p, q, a, code)
                    // New credentials are now the active ones; let the user straight in.
                    onUnlocked(u.trim())
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 1 — unlock
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnlockPhase(
    config: AppLockConfig,
    onUnlocked: (String) -> Unit,
    onAttempt: (Boolean) -> Unit,
    onForgot: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var wrong    by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val waitMs = rememberLockoutRemaining(config.lockDeadline)
    val locked = waitMs > 0

    fun attempt() {
        if (checking || locked || username.isBlank() || password.isEmpty()) return
        scope.launch {
            checking = true; wrong = false
            // Exactly one PBKDF2 derivation, and never on the main thread.
            val matched = withContext(Dispatchers.Default) {
                AppLockAuth.authenticate(config, username, password)
            }
            checking = false
            onAttempt(matched != null)
            if (matched != null) { password = ""; onUnlocked(matched) }
            else { wrong = true; password = "" }
        }
    }

    LockScaffold {
        LockHeader(
            icon     = Icons.Default.Lock,
            title    = stringResource(R.string.app_lock_title),
            subtitle = stringResource(R.string.app_lock_subtitle),
            brand    = true
        )
        SecureCard {
            SecureField(
                value = username,
                onValueChange = { username = it; wrong = false },
                label = stringResource(R.string.app_lock_enter_username),
                leadingIcon = Icons.Default.Person,
                isError = wrong,
                enabled = !checking && !locked,
                imeAction = ImeAction.Next
            )
            SecureField(
                value = password,
                onValueChange = { password = it; wrong = false },
                label = stringResource(R.string.app_lock_enter_password),
                leadingIcon = Icons.Default.Key,
                isError = wrong,
                enabled = !checking && !locked,
                isPassword = true,
                imeAction = ImeAction.Done,
                onDone = { attempt() }
            )
            if (locked) InlineAlert(stringResource(R.string.app_lock_error_locked, AppLockThrottle.format(waitMs)),
                                    AmberMedium, Icons.Default.Timer)
            else if (wrong) InlineAlert(stringResource(R.string.app_lock_error_credentials))

            GradientButton(
                label   = stringResource(R.string.app_lock_unlock),
                onClick = { attempt() },
                enabled = username.isNotBlank() && password.isNotEmpty() && !checking && !locked,
                loading = checking,
                icon    = Icons.Default.LockOpen
            )
        }
        QuietButton(stringResource(R.string.app_lock_forgot), onClick = onForgot, color = SecurePalette.Orange)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 2 — recovery: security answer OR one-time code
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecoverPhase(
    config: AppLockConfig,
    onVerified: () -> Unit,
    onAttempt: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var answer   by remember { mutableStateOf("") }
    var code     by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var failed   by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val waitMs = rememberLockoutRemaining(config.lockDeadline)
    val locked = waitMs > 0

    fun attempt() {
        if (checking || locked || (answer.isBlank() && code.isBlank())) return
        scope.launch {
            checking = true; failed = false
            val ok = withContext(Dispatchers.Default) {
                val answerOk = answer.isNotBlank() && config.answerHash != null &&
                    AppLockCrypto.verify(AppLockCrypto.normalizeAnswer(answer), config.answerHash)
                val codeOk = code.isNotBlank() && config.recoveryHash != null &&
                    AppLockCrypto.verify(AppLockCrypto.normalizeCode(code), config.recoveryHash)
                answerOk || codeOk
            }
            checking = false
            onAttempt(ok)
            if (ok) onVerified() else { failed = true }
        }
    }

    LockScaffold {
        LockHeader(
            icon     = Icons.Default.LockReset,
            title    = stringResource(R.string.app_lock_recovery_title),
            subtitle = stringResource(R.string.app_lock_recovery_desc)
        )
        SecureCard {
            // Route 1 — the user's own security question
            if (!config.question.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SecurePalette.Field)
                        .border(1.dp, SecurePalette.Edge, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.app_lock_security_question), fontSize = 11.sp,
                        fontWeight = FontWeight.Medium, color = SecurePalette.TextFaint)
                    Text(config.question, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain)
                }
                SecureField(
                    value = answer,
                    onValueChange = { answer = it; failed = false },
                    label = stringResource(R.string.app_lock_security_answer),
                    leadingIcon = Icons.Default.QuestionAnswer,
                    isError = failed,
                    enabled = !checking && !locked,
                    imeAction = ImeAction.Next
                )
                OrDivider(stringResource(R.string.app_lock_recovery_or))
            }

            // Route 2 — the one-time recovery code
            SecureField(
                value = code,
                onValueChange = { code = it; failed = false },
                label = stringResource(R.string.app_lock_recovery_code_label),
                leadingIcon = Icons.Default.VpnKey,
                isError = failed,
                enabled = !checking && !locked,
                imeAction = ImeAction.Done,
                onDone = { attempt() }
            )
            if (locked) InlineAlert(stringResource(R.string.app_lock_error_locked, AppLockThrottle.format(waitMs)),
                                    AmberMedium, Icons.Default.Timer)
            else if (failed) InlineAlert(stringResource(R.string.app_lock_error_recovery))

            GradientButton(
                label   = stringResource(R.string.app_lock_btn_verify),
                onClick = { attempt() },
                enabled = (answer.isNotBlank() || code.isNotBlank()) && !checking && !locked,
                loading = checking,
                icon    = Icons.Default.VerifiedUser
            )
        }
        QuietButton(stringResource(R.string.btn_cancel), onClick = onCancel)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared setup form — used by the reset flow above AND by Settings
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Collects username, password, security question and answer, then shows the generated
 * recovery code once. [onComplete] fires only after the user confirms they saved it,
 * so nothing is persisted until they have seen the code.
 */
@Composable
fun AppLockSetupContent(
    onCancel: () -> Unit,
    onComplete: (username: String, password: String, question: String, answer: String, recoveryCode: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm  by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer   by remember { mutableStateOf("") }
    var code     by remember { mutableStateOf<String?>(null) }

    val rulesOk = AppLockCrypto.isValid(password)
    val matches = password.isNotEmpty() && password == confirm
    val canSave = username.isNotBlank() && rulesOk && matches &&
                  question.isNotBlank() && answer.isNotBlank()

    val generated = code
    if (generated != null) {
        RecoveryCodeCard(
            code   = generated,
            onDone = { onComplete(username, password, question, answer, generated) }
        )
        return
    }

    LockHeader(
        icon     = Icons.Default.Shield,
        title    = stringResource(R.string.app_lock_set_title),
        subtitle = null
    )
    SecureCard {
        SecureField(username, { username = it }, stringResource(R.string.app_lock_username),
            Icons.Default.Person, imeAction = ImeAction.Next)
        SecureField(password, { password = it }, stringResource(R.string.app_lock_new_password),
            Icons.Default.Key, isError = password.isNotEmpty() && !rulesOk, isPassword = true,
            imeAction = ImeAction.Next)
        PasswordChecklist(password)
        SecureField(confirm, { confirm = it }, stringResource(R.string.app_lock_confirm_password),
            Icons.Default.Key, isError = confirm.isNotEmpty() && !matches, isPassword = true,
            imeAction = ImeAction.Next)
        if (confirm.isNotEmpty() && !matches) InlineAlert(stringResource(R.string.app_lock_error_mismatch))
    }
    SecureCard {
        Text(
            text  = stringResource(R.string.app_lock_security_question_desc),
            fontSize = 12.sp,
            color = SecurePalette.TextSoft,
            lineHeight = 17.sp
        )
        SecureField(question, { question = it }, stringResource(R.string.app_lock_security_question),
            Icons.Default.Quiz, imeAction = ImeAction.Next)
        SecureField(answer, { answer = it }, stringResource(R.string.app_lock_security_answer),
            Icons.Default.QuestionAnswer, imeAction = ImeAction.Done)
    }
    GradientButton(
        label   = stringResource(R.string.btn_save),
        onClick = { code = AppLockCrypto.generateRecoveryCode() },
        enabled = canSave,
        icon    = Icons.Default.Shield
    )
    QuietButton(stringResource(R.string.btn_cancel), onClick = onCancel)
}

/** Shows the one-time recovery code. Deliberately blocking — it is never shown again. */
@Composable
private fun RecoveryCodeCard(code: String, onDone: () -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }

    LockHeader(
        icon     = Icons.Default.VpnKey,
        title    = stringResource(R.string.app_lock_code_title),
        subtitle = stringResource(R.string.app_lock_code_desc)
    )
    SecureCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SecurePalette.BrandGradient)
                .padding(1.5.dp)
                .clip(RoundedCornerShape(10.5.dp))
                .background(SecurePalette.Base)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text          = code,
                fontSize      = 17.sp,
                fontWeight    = FontWeight.Bold,
                fontFamily    = FontFamily.Monospace,
                color         = SecurePalette.Orange,
                letterSpacing = 2.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable { acknowledged = !acknowledged }
        ) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { acknowledged = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = SecurePalette.Orange, uncheckedColor = SecurePalette.TextFaint, checkmarkColor = Color.White
                )
            )
            Text(
                text  = stringResource(R.string.app_lock_code_confirm),
                fontSize = 12.5.sp,
                color = SecurePalette.TextSoft
            )
        }
        GradientButton(
            label   = stringResource(R.string.app_lock_btn_done),
            onClick = onDone,
            enabled = acknowledged
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared visual pieces
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Milliseconds left before App Lock accepts another try, refreshed every second while a
 * wait is running. Shared with the accounts screen so every sign-in box behaves the same.
 */
@Composable
fun rememberLockoutRemaining(deadline: AppLockThrottle.Deadline): Long {
    val context = LocalContext.current
    var now by remember { mutableStateOf(AppLockClock.now(context)) }
    LaunchedEffect(deadline) {
        now = AppLockClock.now(context)
        while (AppLockThrottle.remainingMs(deadline, now) > 0) {
            delay(1_000)
            now = AppLockClock.now(context)
        }
    }
    return AppLockThrottle.remainingMs(deadline, now)
}

/**
 * Full-screen frame for every lock phase. The chip at the top shows the REAL firewall state,
 * so a user knows whether protection is running while the app itself is locked.
 */
@Composable
private fun LockScaffold(content: @Composable ColumnScope.() -> Unit) {
    val context = LocalContext.current
    val firewallOn by remember(context) { context.vpnRunningFlow() }
        .collectAsState(initial = context.vpnRunning())

    SecureBackground {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 460.dp)
                    .padding(horizontal = 20.dp, vertical = 36.dp)
            ) {
                StatusChip(
                    text  = stringResource(if (firewallOn) R.string.vpn_active_title else R.string.vpn_offline_title),
                    color = if (firewallOn) GreenSafe else AmberMedium
                )
                content()
            }
        }
    }
}

@Composable
private fun LockHeader(icon: ImageVector, title: String, subtitle: String?, brand: Boolean = false) {
    if (brand) TacunsLogoMark(size = 84.dp) else SecurityEmblem(icon)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = SecurePalette.TextMain,
            textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, fontSize = 12.5.sp, color = SecurePalette.TextSoft, textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun OrDivider(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = SecurePalette.Edge, thickness = 1.dp)
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SecurePalette.TextFaint, letterSpacing = 1.sp)
        HorizontalDivider(modifier = Modifier.weight(1f), color = SecurePalette.Edge, thickness = 1.dp)
    }
}
