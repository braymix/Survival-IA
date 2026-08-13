package com.survivalwiki.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette ad alto contrasto: l'app si usa in condizioni di luce difficili e con batteria scarsa.
private val SurvivalDark = darkColorScheme(
    primary = Color(0xFF8BD450),        // verde segnale
    onPrimary = Color(0xFF0A1400),
    secondary = Color(0xFFE0A85C),      // ambra "attenzione"
    background = Color(0xFF0B0F0A),
    surface = Color(0xFF12180F),
    onBackground = Color(0xFFE7EDE3),
    onSurface = Color(0xFFE7EDE3),
    error = Color(0xFFFF6B6B),
)

private val SurvivalLight = lightColorScheme(
    primary = Color(0xFF356A00),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7A5200),
    error = Color(0xFFB3261E),
)

/**
 * Tema di default: scuro. Chi vuole può seguire il sistema; la dark è la modalità primaria
 * anche per la "modalità basso consumo" (meno pixel accesi su OLED).
 */
@Composable
fun SurvivalWikiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme || forceDark) SurvivalDark else SurvivalLight
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
