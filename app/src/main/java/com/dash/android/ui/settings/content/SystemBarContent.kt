package com.dash.android.ui.settings.content

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.systembar.BarPosition
import com.dash.android.ui.systembar.LocalEnterBarEdit
import com.dash.android.ui.systembar.SystemBarConfig
import com.dash.android.ui.theme.LocalDashTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layout › System Bar (roadmap 1.5.7). Per interface.md this tab is the *door* to the bar, not a
 * duplicate of the in-edit sliders: **Position** (which edge it docks to, applied live), an **Edit bar
 * layout** entry into the edit-mode workspace where height, zones and element sizing are set on the
 * bar itself, and **Reset** to defaults.
 */
private const val WITHIN_SECTION = 28
private const val BETWEEN_SECTIONS = 44

@Composable
fun SystemBarContent() {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val prefs = remember { DashPreferences(appContext) }
    val scope = rememberCoroutineScope()
    val enterEdit = LocalEnterBarEdit.current

    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())
    var confirmReset by remember { mutableStateOf(false) }
    // The confirm state is a moment, not a mode — it lapses on its own if the user thinks better of it.
    LaunchedEffect(confirmReset) { if (confirmReset) { delay(3500); confirmReset = false } }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Position ─────────────────────────────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp)) {
            SettingsContentHeader(
                title = "System Bar",
                description = "Where the bar sits, and the way in to editing its layout.",
            )
            SettingBlock(
                name = "Position",
                help = "Which edge the system bar docks to.",
                control = {
                    PresetSegment(
                        labels = listOf("Bottom", "Top"),
                        selected = if (barConfig.position == BarPosition.TOP) 1 else 0,
                    ) { i ->
                        val pos = if (i == 1) BarPosition.TOP else BarPosition.BOTTOM
                        scope.launch { prefs.saveSystemBarConfig(barConfig.copy(position = pos)) }
                    }
                },
                preview = { BarPositionPreview(barConfig.position) },
            )
            SettingBlock(
                name = "Zones",
                help = "How many zones the bar is split into. Their boundaries are then positioned on " +
                    "the bar itself in edit mode.",
                control = {
                    PresetSegment(
                        labels = listOf("1", "2", "3"),
                        selected = (barConfig.zones.size - 1).coerceIn(0, 2),
                    ) { i -> scope.launch { prefs.saveSystemBarConfig(barConfig.withZoneCount(i + 1)) } }
                },
            )
        }

        Spacer(Modifier.height(BETWEEN_SECTIONS.dp))

        // ── Edit-mode entry ──────────────────────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp)) {
            SettingBlock(
                name = "Layout",
                help = "Height, zones and element sizing are set live on the bar itself, in edit mode.",
                control = { LinkButton("Edit bar layout →") { enterEdit() } },
            )
        }

        Spacer(Modifier.height(BETWEEN_SECTIONS.dp))

        // ── Reset ────────────────────────────────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp)) {
            SettingBlock(
                name = "Reset",
                help = "Restore the bar to its default position and layout. This clears any custom " +
                    "zones and element sizing.",
                tag = if (confirmReset) "Tap again to confirm" else null,
                control = {
                    LinkButton(if (confirmReset) "Confirm reset" else "Reset to defaults") {
                        if (confirmReset) {
                            scope.launch { prefs.saveSystemBarConfig(SystemBarConfig.default()) }
                            confirmReset = false
                        } else {
                            confirmReset = true
                        }
                    }
                },
            )
        }
    }
}

/** A small "screen" with the bar drawn on its chosen edge — it slides across when the toggle flips. */
@Composable
private fun BarPositionPreview(position: BarPosition) {
    val theme = LocalDashTheme.current
    val bias by animateFloatAsState(
        targetValue = if (position == BarPosition.TOP) -1f else 1f,
        label = "barPosition",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D0D14))
            .border(1.dp, theme.textColourSecondary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
    ) {
        Box(
            Modifier
                .align(BiasAlignment(0f, bias))
                .fillMaxWidth()
                .height(24.dp)
                .background(theme.backgroundColourPrimary),
        ) {
            // A few footprint boxes so the strip reads as the bar, not just a band.
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(width = 16.dp, height = 9.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.iconColourPrimary.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}
