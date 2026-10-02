package com.sentinel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = CyanPrimary,
    secondary        = BlueAccent,
    tertiary         = AmberMedium,
    background       = BgDeep,
    surface          = BgCard,
    surfaceVariant   = BgCardAlt,
    outline          = BgBorder,
    onPrimary        = BgDeep,
    onSecondary      = TextPrimary,
    onTertiary       = BgDeep,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error            = RedCritical,
    onError          = TextPrimary
)

@Composable
fun SentinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = SentinelTypography,
        content     = content
    )
}
