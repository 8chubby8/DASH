package com.dash.android.ui.modulepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * to a known shape; DASH scales it to whatever screen it lands on. The long edge is the screen edge
 * the panel docks to, and the thickness follows from the ratio.
 *
 * [LARGE_RATIO] is the first of the twelve layout slots to get a real number (Roger, 1.6.2), chosen
 * because it is the shape he wants in his own car. The remaining slots are designed at 1.6.4 as
 * real cases turn up, and the whole set locks into `module-sdk.md` at 1.6.10. Provisional until
 * then — this is one constant precisely so it stays cheap to change.
 */
object ModulePanelSpec {
    /**
     * Large horizontal, expressed as width ÷ height — **4×1.5** (Roger, 1.6.2). Written as the two
     * numbers rather than reduced, because the audience is a module author picturing a shape, not a
     * developer reading a coefficient. Arrived at by eye on real hardware: 3:1 was the first sketch,
     * 4×3 proved far bigger than expected, 4×2 closer, 4×1.5 shallower again.
     */
    const val LARGE_ASPECT = 4f / 1.5f

    /** Panel thickness for a given long-edge length, at the large slot. */
    fun largeHeightFor(width: Dp): Dp = width / LARGE_ASPECT
}

/**
 * The persistent module panel container.
 *
 * Docked full-width to one screen edge, its height derived from that width by the slot ratio. The
 * caller positions it and guarantees the edge is free — the panel and the system bar never share an
 * edge (Roger, 1.6.2), so the two can never stack or collide.
 *
 * The fill is `backgroundColourPrimary` — the same token the rest of DASH's chrome uses. It is a
 * default to be drawn over rather than a designed frame: when a module owns this box the fill is
 * simply never seen. With no module installed there is no king in the castle, so DASH occupying the
 * empty box is the one tenancy it is entitled to — and it ends the moment a module claims the space.
 */
@Composable
fun ModulePanel(modifier: Modifier = Modifier) {
    val theme = LocalDashTheme.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ModulePanelSpec.largeHeightFor(maxWidth))
                .background(theme.backgroundColourPrimary)
        )
    }
}
