package com.sentinel.ui.theme

import androidx.compose.ui.graphics.Color

// ── Background layers (Figma: ~#060912 → #111827) ─────────────────────────
val BgDeep       = Color(0xFF060912)   // deepest — page bg
val BgPrimary    = Color(0xFF0A0E1A)   // main surface
val BgCard       = Color(0xFF111827)   // card surface
val BgCardAlt    = Color(0xFF0F1620)   // slightly darker card
val BgBorder     = Color(0xFF1E293B)   // card/row borders

// ── Brand / accent ─────────────────────────────────────────────────────────
val CyanPrimary  = Color(0xFF06B6D4)   // primary cyan
val BlueAccent   = Color(0xFF3B82F6)   // blue (charts, VPN)
val GreenSafe    = Color(0xFF22C55E)   // allow / active / safe
val GreenLow     = Color(0xFF16A34A)   // low severity bg tint

// ── Severity ───────────────────────────────────────────────────────────────
val RedCritical  = Color(0xFFEF4444)   // CRITICAL badges + blocked
val RedThreat    = Color(0xFFDC2626)   // threat row tint
val AmberHigh    = Color(0xFFF97316)   // HIGH
val AmberMedium  = Color(0xFFF59E0B)   // MEDIUM
val AmberAccent  = Color(0xFFF59E0B)   // legacy compat alias

// ── Text ───────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted     = Color(0xFF64748B)

// ── Severity card tint backgrounds ────────────────────────────────────────
val BgCritical   = Color(0xFF2D0F0F)   // critical card bg
val BgHigh       = Color(0xFF2D1A06)   // high card bg
val BgMedium     = Color(0xFF2A200A)   // medium card bg
val BgActive     = Color(0xFF0A1F14)   // active/allow card bg

// ── Legacy aliases (referenced by LogScreen, AppControlScreen, MapScreen) ──
val BlueDeep     = BgPrimary
val BlueDarker   = BgDeep
val GlassWhite   = Color(0x14FFFFFF)
val GlassBlue    = Color(0x1406B6D4)
