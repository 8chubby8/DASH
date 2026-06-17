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
    val barText: Color,
) {
    companion object {
        fun dark() = DashColors(
            barBackground = Color(0xFF1A1A2E),
            barAccent    = Color(0xFF26263F),
            barText      = Color(0xFFB0B0C8),
        )
    }
}

/**
 * The active DASH theme. Always provided by [MainScreen] before any bar or element renders.
 * The fallback here is a safety net only — in practice the provider is always present.
 */
val LocalDashTheme = compositionLocalOf { DashColors.dark() }
