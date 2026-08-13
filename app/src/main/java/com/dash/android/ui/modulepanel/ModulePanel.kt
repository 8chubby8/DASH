package com.dash.android.ui.modulepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dash.android.panel.PanelDocument
import com.dash.android.ui.theme.LocalDashTheme

/**
 * The module panel — the display area an installed ACCESSORY module owns (roadmap 1.6.2).
 *
 * **DASH draws the container. The module fills it.** This file is the container and stops at its
 * boundary: a plain filled box, no border, no radius, no content of its own. Everything *inside*
 * the boundary belongs to the module — background, art, fonts, controls — and DASH never reaches
 * in. Since 1.6.6 a real module fills it, drawn from the layout document it shipped at install.
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
 * The module panel — the container, and the module's panel drawn inside it.
 *
 * Sized and positioned entirely by the caller, which owns the screen geometry. **DASH draws the
 * container and stops at its boundary**: no border, no radius, no padding, nothing of DASH's own
 * inside the walls. [PanelContent] fills it with what the module described and nothing else.
 *
 * The fill beneath is `backgroundColourPrimary` — a floor to draw on rather than a designed frame,
 * and one a full-panel layer covers completely. It is visible only where a module's own layout
 * leaves it visible, which is the module's decision to have made.
 *
 * **This composable is never reached with no module to draw.** §6's *no layout, no panel* rule is
 * settled a level up, in [rememberActivePanel] and the screen geometry that reads it, because the
 * absence has to change the *layout of the screen* rather than merely what this box contains.
 */
@Composable
fun ModulePanel(
    document: PanelDocument,
    values: Map<String, String>,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDashTheme.current
    Box(
        modifier = modifier
            .size(width.coerceAtLeast(0.dp), height.coerceAtLeast(0.dp))
            .background(theme.backgroundColourPrimary)
            // The king's walls are the edge of his domain in both directions: nothing DASH draws
            // reaches in, and nothing the module draws spills out over DASH's own surfaces.
            .clipToBounds()
    ) {
        PanelContent(document = document, values = values, modifier = Modifier.fillMaxSize())
    }
}
