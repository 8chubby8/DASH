package com.dash.android.ui.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.dash.android.DashApplication
import com.dash.android.ui.common.DashButton
import com.dash.android.ui.common.SETTING_SPACING
import com.dash.android.ui.common.controlWidth
import com.dash.android.viewport.ViewportHost.Method

/**
 * Layout › Viewport — **test build** (roadmap 1.7.1).
 *
 * Shows the whole ladder of ways DASH can put an app in the viewport, what this hardware said to
 * each, and which one is in use. Roger's call was to see a viewport working before deciding what the
 * controls should be, so the one control here — the method picker — is a test instrument for
 * comparing rungs on one device, not a decided setting.
 *
 * Under Layout rather than Appearance for now — the viewport as a *surface* (where it is, how apps
 * get into it). Where its look (Flush / Dominant / Passive) lives is still the open question 1.5.2
 * left for 1.7.x.
 */
@Composable
fun ViewportContent() {
    val context = LocalContext.current
    val host = remember(context) { (context.applicationContext as DashApplication).viewport }
    val rect by host.rect.collectAsState()
    val statuses by host.statuses.collectAsState()
    val testing by host.testing.collectAsState()
    val choice by host.choice.collectAsState()
    val active by host.activeFlow.collectAsState()
    val density = LocalDensity.current.density

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SETTING_SPACING),
    ) {
        SettingsContentHeader("Viewport")

        SettingBlock(
            name = "Test build",
            help = "This version tests which ways of putting an app in the viewport work on this " +
                "device. Apps open from the test grid in the viewport.",
            control = {},
        )

        // Test-only: lets one device show each rung side by side. A forced rung that does not work
        // here is not honoured — the page says so rather than pretending.
        val options = listOf<Pair<String, Method?>>(
            "Automatic" to null,
            "Windowed" to Method.WINDOWED,
            "Draw on top" to Method.OVERLAY,
            "Full screen" to Method.FULLSCREEN,
        )
        SettingBlock(
            name = "Method",
            help = "Automatic uses the best method that works on this device.",
            tag = choice?.takeIf { it != active }?.let { "${it.label} doesn't work here — using ${active.label}" },
            fullWidthControl = true,
            control = {
                FitPresetSegment(
                    labels = options.map { it.first },
                    selected = options.indexOfFirst { it.second == choice }.coerceAtLeast(0),
                    onSelect = { host.choose(options[it].second) },
                )
            },
        )

        SettingsSectionHeader("Viewport")
        InfoRows(
            listOf(
                "In use" to if (testing) "Testing…" else active.label,
                "Size" to (rect?.let {
                    "${(it.width() / density).toInt()} × ${(it.height() / density).toInt()} dp " +
                        "(${it.width()} × ${it.height()} px)"
                } ?: "Not measured yet"),
                "Position" to (rect?.let { "${it.left}, ${it.top} px from top-left" } ?: "—"),
            )
        )

        SettingsSectionHeader("Methods, best first")
        InfoRows(
            statuses.map { s ->
                val mark = when {
                    s.works && s.built -> "✓ "
                    s.works -> "◦ "
                    else -> "✗ "
                }
                s.method.label to mark + s.detail
            }
        )

        if (statuses.any { it.method == Method.OVERLAY && !it.works }) {
            SettingBlock(
                name = "Draw on top",
                help = "Needs Android's \"Display over other apps\" permission for DASH. Switch it on in " +
                    "the screen this opens, then come back.",
                control = {
                    DashButton(
                        label = "Allow",
                        modifier = Modifier.width(controlWidth(LocalDensity.current.fontScale)),
                        onClick = { host.openOverlayPermission() },
                    )
                },
            )
        }

        SettingBlock(
            name = "Test again",
            help = "Re-runs every check. Useful after installing Shizuku or granting a permission.",
            control = {
                DashButton(
                    label = if (testing) "Testing…" else "Test",
                    modifier = Modifier.width(controlWidth(LocalDensity.current.fontScale)),
                    onClick = { if (!testing) host.test() },
                )
            },
        )
    }
}
