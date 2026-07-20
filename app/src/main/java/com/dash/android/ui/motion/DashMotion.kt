package com.dash.android.ui.motion

import androidx.compose.runtime.compositionLocalOf

/**
 * User-configurable transition length (Appearance settings). One knob scales how long DASH's own
 * chrome transitions take — the settings panel grow/collapse today, more surfaces as they are
 * built. It is stored in DataStore and provided via [LocalTransitionMillis] at the top of the
 * composition tree, so any animation can read it without being plumbed the value.
 *
 * This is the DASH ethos as a setting: DASH has no opinion on how fast the user wants their
 * interface to move — from instant to cinematic is entirely their call.
 */
val LocalTransitionMillis = compositionLocalOf { TRANSITION_MILLIS_DEFAULT }

const val TRANSITION_MILLIS_DEFAULT = 450

/** The named speeds offered in settings; [millis] is the transition duration in milliseconds. */
enum class TransitionSpeed(val label: String, val millis: Int) {
    INSTANT("INSTANT", 0),
    FAST("FAST", 250),
    NORMAL("NORMAL", 450),
    SLOW("SLOW", 750),
    CINEMATIC("CINEMATIC", 1100),
}
