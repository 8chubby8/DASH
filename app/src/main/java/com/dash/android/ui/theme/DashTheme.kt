package com.dash.android.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The DASH colour token set. Every system bar component and element reads colours from here —
 * never from hardcoded values. Adding a token is one field with a default; nothing existing
 * breaks. Version 2 introduces user-facing presets by providing a different [DashColors] instance
 * at the top of the composition tree; no component needs to change.
 */
data class DashColors(
    val barBackground: Color,
    val barAccent: Color,
    val barAccent2: Color,
    val barText: Color,
) {
    companion object {
        // Neutral greys drawn from Apple's light-mode system palette: a light bar surface
        // (systemGray5) with slightly darker accent panels (systemGray4), a mid-grey accent line
        // (systemGray), and dark text — the token *roles* are unchanged, only the values flipped.
        fun dark() = DashColors(
            barBackground = Color(0xFFE5E5EA),
            barAccent     = Color(0xFFD1D1D6),
            barAccent2    = Color(0xFF8E8E93),
            barText       = Color(0xFF3A3A3C),
        )
    }
}

/**
 * The active DASH theme. Always provided by [MainScreen] before any bar or element renders.
 * The fallback here is a safety net only — in practice the provider is always present.
 */
val LocalDashTheme = compositionLocalOf { DashColors.dark() }
