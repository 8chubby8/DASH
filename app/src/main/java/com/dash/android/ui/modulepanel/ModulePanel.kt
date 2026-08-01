package com.dash.android.ui.modulepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dash.android.ui.theme.LocalDashTheme

/**
 * The module panel — the display area an installed ACCESSORY module owns (roadmap 1.6.2).
 *
 * **DASH draws the container. The module fills it.** This file is the container and stops at its
 * boundary: a plain filled box, no border, no radius, no content. Everything *inside* the boundary
 * belongs to the module — background, art, fonts, controls — and DASH never reaches in. Nothing
 * fills it yet; the first real module arrives at 1.6.5 and its layout is rendered at 1.6.6.
 *
 * **Shape, not size.** The panel is a fixed aspect ratio, not a measurement — that is what lets a
 * module drawn once render correctly on a phone, a tablet and a head unit alike. The author draws
 * to a known shape; DASH scales it to whatever screen it lands on.
 */
object ModulePanelSpec {
    /**
     * Panel thickness for a given long-edge length, at the requested size.
     *
     * **The long edge is the docked edge minus whatever the system bar has already taken from it**
     * (Roger, 1.6.3). Docked top or bottom, the bar is on the opposite horizontal edge and takes
     * nothing, so the long edge is the full screen width. Docked left or right, the bar spans the
     * full width at top or bottom and eats into the vertical run, so the long edge is the screen
     * height *less the bar*. A consequence worth knowing: changing the bar height resizes a
     * vertical panel.
     *
     * One number per size serves both orientations, because [PanelSize.aspect] is expressed against
     * the long edge rather than against width — a vertical panel is the same shape stood on its end.
     */
    fun thicknessFor(size: PanelSize, longEdge: Dp): Dp = longEdge / size.aspect
}

/**
 * The persistent module panel container.
 *
 * Sized and positioned entirely by the caller, which owns the screen geometry — this draws the box
 * and nothing else. The fill is `backgroundColourPrimary`, the same token the rest of DASH's chrome
 * uses. It is a default to be drawn over rather than a designed frame: when a module owns this box
 * the fill is simply never seen. With no module installed there is no king in the castle, so DASH
 * occupying the empty box is the one tenancy it is entitled to — and it ends the moment a module
 * claims the space.
 */
@Composable
fun ModulePanel(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val theme = LocalDashTheme.current
    Box(
        modifier = modifier
            .size(width.coerceAtLeast(0.dp), height.coerceAtLeast(0.dp))
            .background(theme.backgroundColourPrimary)
    )
}
