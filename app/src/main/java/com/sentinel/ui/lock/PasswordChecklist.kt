package com.sentinel.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.ui.theme.GreenSafe
import com.sentinel.ui.theme.TextMuted
import com.sentinel.ui.theme.TextPrimary
import com.tacu.nsfwzerotrust.R

/**
 * Each App Lock password rule on its own, for the live checklist. [all] is exactly
 * [AppLockCrypto.isValid] — a unit test keeps the two in agreement.
 */
data class PasswordRules(
    val length: Boolean,
    val upper: Boolean,
    val lower: Boolean,
    val digit: Boolean,
    val symbol: Boolean
) {
    val all: Boolean get() = length && upper && lower && digit && symbol
}

fun passwordRules(password: String) = PasswordRules(
    length = password.length in AppLockCrypto.MIN_LENGTH..AppLockCrypto.MAX_LENGTH,
    upper  = password.any { it.isUpperCase() },
    lower  = password.any { it.isLowerCase() },
    digit  = password.any { it.isDigit() },
    symbol = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
)

/** Shows every password rule and ticks each one green as it is met. */
@Composable
fun PasswordChecklist(password: String) {
    val r = passwordRules(password)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RuleRow(r.length, stringResource(R.string.app_lock_rule_length,
            AppLockCrypto.MIN_LENGTH.toString(), AppLockCrypto.MAX_LENGTH.toString()))
        RuleRow(r.upper,  stringResource(R.string.app_lock_rule_upper))
        RuleRow(r.lower,  stringResource(R.string.app_lock_rule_lower))
        RuleRow(r.digit,  stringResource(R.string.app_lock_rule_number))
        RuleRow(r.symbol, stringResource(R.string.app_lock_rule_symbol))
    }
}

@Composable
private fun RuleRow(met: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) GreenSafe else TextMuted,
            modifier = Modifier.size(15.dp)
        )
        Text(text, fontSize = 12.sp, color = if (met) TextPrimary else TextMuted)
    }
}
