package com.sentinel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.ui.theme.*
import com.tacu.nsfwzerotrust.R

// ─────────────────────────────────────────────────────────────────────────────
// Look for the security-sensitive screens: App Lock (sign-in, recovery, setup, accounts)
// and Language. An original dark "glass" style in the TACUNS brand orange: near-black base
// with a warm glow at the top, dark glass cards with a thin light edge, pill-shaped actions.
// Text sizes follow the main tabs, so these screens do not look oversized.
// ─────────────────────────────────────────────────────────────────────────────

/** Palette for these screens only. The rest of the app keeps its own theme. */
object SecurePalette {
    val Base        = Color(0xFF09090B)                 // near-black page
    val Orange      = Color(0xFFF7931E)                 // TACUNS brand orange
    val OrangeDeep  = Color(0xFFE2541B)                 // deeper end of the brand gradient
    val GlassTop    = Color(0xFF1C1C21)                 // card gradient, top
    val GlassBottom = Color(0xFF131316)                 // card gradient, bottom
    val Edge        = Color.White.copy(alpha = 0.08f)   // thin light card edge
    val EdgeStrong  = Color.White.copy(alpha = 0.14f)
    val Field       = Color.White.copy(alpha = 0.04f)   // input fill
    val TextMain    = Color(0xFFF4F4F5)
    val TextSoft    = Color(0xFFA1A1AA)
    val TextFaint   = Color(0xFF71717A)
    val BrandGradient: Brush get() = Brush.horizontalGradient(listOf(Orange, OrangeDeep))
    val GlassBrush: Brush get() = Brush.verticalGradient(listOf(GlassTop, GlassBottom))
}

/** Page background: near-black with a warm brand glow at the top for depth. */
@Composable
fun SecureBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SecurePalette.Base)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(SecurePalette.OrangeDeep.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 1.05f
                    )
                )
            },
        content = content
    )
}

/** Centred title with a round glass back button — for the account and language pages. */
@Composable
fun SecureTopBar(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, SecurePalette.Edge, CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back),
                tint = SecurePalette.TextMain, modifier = Modifier.size(18.dp))
        }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 52.dp))
    }
}

/** Icon tile with a brand-gradient edge, used at the top of recovery and setup. */
@Composable
fun SecurityEmblem(icon: ImageVector, size: Dp = 64.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(SecurePalette.BrandGradient)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(18.5.dp))
            .background(SecurePalette.GlassBrush),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = SecurePalette.Orange, modifier = Modifier.size(size * 0.42f))
    }
}

/** Small pill with a coloured dot, e.g. "Firewall Active". */
@Composable
fun StatusChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** Dark glass container with a thin light edge. */
@Composable
fun SecureCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SecurePalette.GlassBrush)
            .border(1.dp, SecurePalette.Edge, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

/** Small section title. */
@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier.fillMaxWidth(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = SecurePalette.TextMain)
}

/** Header card: brand-tinted icon tile, title and a short explanation. */
@Composable
fun ScreenHeaderCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SecurePalette.GlassBrush)
            .border(1.dp, SecurePalette.Edge, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SecurePalette.Orange.copy(alpha = 0.14f))
                .border(1.dp, SecurePalette.Orange.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            // [tint] kept for callers; these screens use the brand colour for a single look.
            Icon(icon, contentDescription = null, tint = SecurePalette.Orange, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SecurePalette.TextMain)
            Text(subtitle, fontSize = 12.sp, color = SecurePalette.TextSoft, lineHeight = 16.sp)
        }
        trailing?.invoke()
    }
}

/**
 * Text field with its label always visible above it, a leading icon, clear focus / error
 * edges, and a show / hide button for passwords.
 */
@Composable
fun SecureField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = if (isError) RedCritical else SecurePalette.TextSoft)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            isError = isError,
            textStyle = TextStyle(fontSize = 14.sp, color = SecurePalette.TextMain),
            leadingIcon = {
                Icon(leadingIcon, contentDescription = null,
                    tint = if (isError) RedCritical else SecurePalette.TextFaint, modifier = Modifier.size(18.dp))
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (visible) R.string.cd_hide_password else R.string.cd_show_password
                            ),
                            tint = SecurePalette.TextFaint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !visible) PasswordVisualTransformation()
                                   else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = SecurePalette.Field,
                unfocusedContainerColor = SecurePalette.Field,
                disabledContainerColor  = SecurePalette.Field,
                errorContainerColor     = SecurePalette.Field,
                focusedBorderColor      = SecurePalette.Orange,
                unfocusedBorderColor    = SecurePalette.EdgeStrong,
                disabledBorderColor     = SecurePalette.Edge,
                errorBorderColor        = RedCritical,
                focusedTextColor        = SecurePalette.TextMain,
                unfocusedTextColor      = SecurePalette.TextMain,
                disabledTextColor       = SecurePalette.TextFaint,
                errorTextColor          = SecurePalette.TextMain,
                cursorColor             = SecurePalette.Orange,
                errorCursorColor        = RedCritical
            )
        )
    }
}

/** Main action: pill shape, brand gradient. */
@Composable
fun GradientButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val filled = enabled || loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) SecurePalette.BrandGradient else SolidColor(Color.White.copy(alpha = 0.06f)))
            .border(1.dp, if (filled) Color.White.copy(alpha = 0.12f) else SecurePalette.Edge, RoundedCornerShape(50))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null,
                        tint = if (enabled) Color.White else SecurePalette.TextFaint, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else SecurePalette.TextFaint)
            }
        }
    }
}

/** Secondary action: quiet text button. */
@Composable
fun QuietButton(label: String, onClick: () -> Unit, color: Color = SecurePalette.TextSoft) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

/** Tinted message box with an icon — errors in red, waits in amber. */
@Composable
fun InlineAlert(text: String, color: Color = RedCritical, icon: ImageVector = Icons.Default.ErrorOutline) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 12.sp, color = color, lineHeight = 16.sp)
    }
}
