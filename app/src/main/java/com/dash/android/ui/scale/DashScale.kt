package com.dash.android.ui.scale

import androidx.compose.runtime.compositionLocalOf

val LocalDashScale = compositionLocalOf { 1.0f }

const val DASH_SCALE_DEFAULT = 1.0f
const val DASH_SCALE_MIN = 0.5f
const val DASH_SCALE_MAX = 2.0f
const val DASH_SCALE_STEP = 0.1f

// DASH text size — DASH's own font scaling, independent of Android's font setting. Provided at the
// composition root as a LocalDensity fontScale override (see MainScreen), so all DASH chrome text
// follows this value and nothing else. 1.0 is the default.
const val DASH_TEXT_SCALE_DEFAULT = 1.0f
const val DASH_TEXT_SCALE_MIN = 0.8f
const val DASH_TEXT_SCALE_MAX = 1.4f
const val DASH_TEXT_SCALE_STEP = 0.1f