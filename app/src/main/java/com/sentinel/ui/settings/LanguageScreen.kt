package com.sentinel.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.sentinel.ui.components.ScreenHeaderCard
import com.sentinel.ui.components.SecureBackground
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.components.SecureTopBar
import com.tacu.nsfwzerotrust.R
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Language list — native-name labels are intentionally plain literals, not
// stringResource(): a language's own name (e.g. "தமிழ்") must always render the
// same way regardless of which language is currently active, so a user browsing
// the list can recognize their language even if the app is currently showing a
// different one.
// ─────────────────────────────────────────────────────────────────────────────

private data class AppLanguage(val tag: String, val nativeName: String)

private val supportedLanguages = listOf(
    AppLanguage("en", "English"),
    AppLanguage("ta", "தமிழ்"),
    AppLanguage("hi", "हिन्दी"),
    AppLanguage("bn", "বাংলা"),
    AppLanguage("es", "Español"),
    AppLanguage("pt-BR", "Português (Brasil)"),
    AppLanguage("pt-PT", "Português (Portugal)"),
    AppLanguage("fr", "Français"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("it", "Italiano"),
    AppLanguage("ru", "Русский"),
    AppLanguage("ar", "العربية"),
    AppLanguage("fa", "فارسی"),
    AppLanguage("he", "עברית")
)

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    // Re-read on every recomposition of this screen so the checkmark reflects
    // the current selection immediately after a tap. Uses toLanguageTag() (not .language)
    // so region-qualified tags like "pt-BR" vs "pt-PT" are distinguished correctly.
    val currentTag = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "en"
    val currentName = supportedLanguages.firstOrNull { it.tag == currentTag }?.nativeName ?: "English"

    SecureBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            SecureTopBar(title = stringResource(R.string.language_screen_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScreenHeaderCard(
                    icon     = Icons.Default.Language,
                    tint     = SecurePalette.Orange,
                    title    = currentName,
                    subtitle = stringResource(R.string.settings_language_subtitle)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    supportedLanguages.forEach { lang ->
                        LanguageRow(
                            lang     = lang,
                            selected = lang.tag == currentTag,
                            onSelect = {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(lang.tag)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * One language: short code badge, the language's own name, and its English name underneath
 * (from the system, so it needs no translation). The selected row glows in the brand colour.
 */
@Composable
private fun LanguageRow(lang: AppLanguage, selected: Boolean, onSelect: () -> Unit) {
    val englishName = remember(lang.tag) {
        Locale.forLanguageTag(lang.tag).getDisplayName(Locale.ENGLISH)
    }
    val shape = RoundedCornerShape(18.dp)
    val orange = SecurePalette.Orange

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(listOf(orange.copy(alpha = 0.16f), orange.copy(alpha = 0.05f)))
                else SecurePalette.GlassBrush
            )
            .border(1.dp, if (selected) orange.copy(alpha = 0.55f) else SecurePalette.Edge, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) orange.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                .border(1.dp, if (selected) orange.copy(alpha = 0.40f) else SecurePalette.Edge, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lang.tag.uppercase(Locale.ROOT),
                fontSize = if (lang.tag.length > 2) 9.5.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) orange else SecurePalette.TextSoft
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(lang.nativeName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain)
            if (!englishName.equals(lang.nativeName, ignoreCase = true)) {
                Text(englishName, fontSize = 11.5.sp, color = SecurePalette.TextFaint)
            }
        }
        if (selected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = orange, modifier = Modifier.size(18.dp))
        }
    }
}
