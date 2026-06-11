package com.dash.android.ui.scale

import androidx.compose.runtime.compositionLocalOf

val LocalDashScale = compositionLocalOf { 1.0f }

const val DASH_SCALE_DEFAULT = 1.0f
const val DASH_SCALE_MIN = 0.5f
const val DASH_SCALE_MAX = 2.0f
const val DASH_SCALE_STEP = 0.1f