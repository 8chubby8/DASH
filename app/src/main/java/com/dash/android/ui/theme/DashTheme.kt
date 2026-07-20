package com.dash.android.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * The DASH theme token set — colours and font. Every DASH chrome surface (system bar, elements,
 * edit ruler, settings panel, overlays) reads from here, never from hardcoded values. Adding a
 * token is one field with a default; nothing existing breaks. Version 2 introduces user-facing
 * presets by providing a different [DashTheme] instance at the top of the composition tree; no
 * component needs to change.
 *
 * The colours come in matched primary/secondary pairs so a surface and the text/icons on it stay
 * legible. **The pairing must not be crossed:** primary text/icons sit on the primary background,
 * secondary text/icons sit on the secondary background. Secondary text on the primary background
 * (both currently the same light grey) would be invisible.
 */
data class DashTheme(
    val backgroundColourPrimary: Color,
    val backgroundColourSecondary: Color,
    val textColourPrimary: Color,
    val textColourSecondary: Color,
    val iconColourPrimary: Color,
    val iconColourSecondary: Color,
    val accentColourPrimary: Color,
    val accentColourSecondary: Color,
    val font: FontFamily,
) {
    companion object {
        // One default set. The light-grey primary surface carries black text/icons; the raised
        // battleship-grey secondary surface carries light-grey text/icons. Accents are the mid
        // greys the system bar and edit ruler use for structure.
        fun default() = DashTheme(
            backgroundColourPrimary   = Color(0xFFE5E5EA),
            backgroundColourSecondary = Color(0xFF848482),
            textColourPrimary         = Color(0xFF000000),
            textColourSecondary       = Color(0xFFE5E5EA),
            iconColourPrimary         = Color(0xFF000000),
            iconColourSecondary       = Color(0xFFE5E5EA),
            accentColourPrimary       = Color(0xFFD1D1D6),
            accentColourSecondary     = Color(0xFF8E8E93),
            font                      = FontFamily.Monospace,
        )
    }
}

/**
 * The active DASH theme. Always provided by [com.dash.android.ui.screen.MainScreen] before any
 * chrome renders. The fallback here is a safety net only — in practice the provider is present.
 */
val LocalDashTheme = compositionLocalOf { DashTheme.default() }
