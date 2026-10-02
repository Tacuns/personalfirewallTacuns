package com.sentinel.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R

/**
 * Rules › Always allow: sites that no blocklist, schedule or look-alike check will block.
 * Uses the same card colours and field style as the Domain Blocking card above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowListCard(allowed: List<String>, onAllow: (String) -> Unit, onRemove: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val canAllow = input.trim().isNotEmpty()
    val submit = {
        if (canAllow) {
            onAllow(input)
            input = ""
            keyboard?.hide()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = GreenSafe, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.allow_title), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.allow_subtitle), style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary, lineHeight = 16.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value           = input,
                        onValueChange   = { input = it },
                        placeholder     = { Text(stringResource(R.string.domain_input_hint), fontSize = 12.sp, color = TextMuted) },
                        singleLine      = true,
                        modifier        = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                        colors          = TextFieldDefaults.colors(
                            focusedContainerColor   = BgCardAlt,
                            unfocusedContainerColor = BgCardAlt,
                            focusedIndicatorColor   = CyanPrimary,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary,
                            cursorColor             = CyanPrimary
                        ),
                        textStyle       = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canAllow) GreenSafe.copy(alpha = 0.15f) else BgBorder.copy(alpha = 0.4f))
                            .border(1.dp, if (canAllow) GreenSafe.copy(alpha = 0.5f) else BgBorder, RoundedCornerShape(8.dp))
                            .clickable(enabled = canAllow) { submit() }
                            .padding(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        Text(stringResource(R.string.allow_button), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (canAllow) GreenSafe else TextMuted)
                    }
                }
                if (allowed.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.allow_empty), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }

            if (allowed.isNotEmpty()) {
                HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
                allowed.forEachIndexed { index, domain ->
                    if (index > 0) HorizontalDivider(color = BgBorder, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = GreenSafe.copy(0.7f), modifier = Modifier.size(13.dp))
                        Text(domain, fontSize = 13.sp, color = TextPrimary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(domain) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.cd_remove_list, domain), tint = TextMuted,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
