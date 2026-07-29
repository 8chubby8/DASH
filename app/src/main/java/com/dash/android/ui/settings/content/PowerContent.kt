package com.dash.android.ui.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dash.android.MainActivity
import com.dash.android.system.DevicePower
import kotlinx.coroutines.delay
import com.dash.android.ui.common.SETTING_SPACING
import com.dash.android.ui.common.CONTROL_WIDTH
import com.dash.android.ui.common.DashButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import com.dash.android.ui.common.controlWidth

/**
 * System › Power (roadmap 1.5.14) — the rehome of the legacy panel's EXIT DASH, plus the device
 * power actions on hardware that allows them.
 *
 * **Named Power, and living under System.** Exit DASH alone would have been an *Exit* tab, but on
 * Silver/Gold this surface also restarts and shuts down the whole device — and something that acts
 * on the device belongs with the rest of the system settings rather than as a peer of Appearance and
 * Layout.
 *
 * Exit ends the DASH process and hands the screen back to Android, resetting any forced display
 * density on the way out so nothing of DASH's is left behind on a device the user has walked away
 * from.
 *
 * **It is honest about doing nothing useful when DASH is the launcher.** A home app that finishes is
 * relaunched immediately, because there is nowhere else for the screen to go — so the help text says
 * so and points at the tab that can actually change it, rather than leaving the user tapping a
 * control that appears broken.
 *
 * Tap-to-confirm, on the 1.5.7 Reset idiom: the confirm is a moment, not a mode, and lapses on its
 * own after a few seconds if the user thinks better of it.
 */
@Composable
fun PowerContent() {
    val context = LocalContext.current
    val activity = remember(context) { context as? MainActivity }
    val isLauncher = remember(activity) { activity?.isDefaultLauncher() == true }
    val power = remember(context) { DevicePower(context) }

    val controlWidth = Modifier.width(controlWidth(LocalDensity.current.fontScale))

    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) { if (confirming) { delay(3500); confirming = false } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SETTING_SPACING),
    ) {
        SettingsContentHeader("Power")

        SettingBlock(
            name = "Return to homescreen",
            tag = when {
                confirming -> "Tap again to confirm"
                isLauncher -> "DASH is the home app — Android will restart it"
                else -> null
            },
            control = {
                DashButton(
                    label = if (confirming) "Confirm exit" else "Exit",
                    modifier = controlWidth,
                    onClick = { if (confirming) activity?.exitDash() else confirming = true },
                )
            },
        )

        // ── Device power ─────────────────────────────────────────────────────────────────────
        // Present only where Android grants DASH the privilege. On Bronze — every 1.x sideload —
        // this whole section is absent rather than disabled: a control that cannot work should not
        // be on the screen explaining that it cannot work.
        if (power.canRestart || power.canShutDown) {
            Column(verticalArrangement = Arrangement.spacedBy(SETTING_SPACING)) {
                SettingsSectionHeader("Device power")
                if (power.canRestart) {
                    ConfirmAction(
                        name = "Restart device",
                        idle = "Restart",
                        confirm = "Confirm restart",
                    ) { power.restart() }
                }
                if (power.canShutDown) {
                    ConfirmAction(
                        name = "Shut down device",
                        idle = "Shut down",
                        confirm = "Confirm shut down",
                    ) { power.shutDown() }
                }
            }
        }
    }
}

/** A destructive action on the 1.5.7 Reset idiom: the first tap arms it, the second does it, and the
 *  armed state lapses on its own so a stray tap in a moving car never leaves it primed. */
@Composable
private fun ConfirmAction(
    name: String,
    idle: String,
    confirm: String,
    onConfirmed: () -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3500); armed = false } }
    SettingBlock(
        name = name,
        tag = if (armed) "Tap again to confirm" else null,
        control = {
            DashButton(
                label = if (armed) confirm else idle,
                modifier = Modifier.width(controlWidth(LocalDensity.current.fontScale)),
                onClick = { if (armed) { armed = false; onConfirmed() } else armed = true },
            )
        },
    )
}
