package com.dash.android.ui.modulepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.transport.InstalledModule
import com.dash.android.ui.theme.LocalDashTheme

/**
 * The module tab bar — DASH's switch between installed module panels (roadmap 1.6.8).
 *
 * **This exists because the swipe belongs to the module.** *(Roger, 2026-08-19.)* interface.md had
 * modules cycled by swiping inside the panel; they are not. Everything inside the panel boundary is
 * the module's box under the Module Mantra, and a single-finger gesture inside it is the module's
 * input to claim — hold-to-repeat on a stepper, a drag slider, a swipe through presets. **Once DASH
 * takes that gesture it can never give it back** without breaking every module built in the
 * meantime, so it is not taken at all. DASH's control therefore lives *outside* the box: DASH owns
 * the walls, and this makes one wall thick enough to touch.
 *
 * **Tapping a tab goes straight to that module** rather than cycling — six modules are one tap
 * apart, not five. That is also why the switch is a cross-fade rather than a slide: with direct
 * selection you can jump from the first tab to the fourth, so there is nothing meaningful to slide
 * past, and sliding would tell a story about modules living side by side that the bar does not
 * support.
 *
 * **This is the one place DASH may name a module.** The label is the module's own `HELLO` name, and
 * DASH does not decorate it, disambiguate two modules that chose the same one, or annotate it with
 * the state of its board. Two boards running the same firmware are two modules and get two identical
 * tabs *(Roger)*.
 *
 * **Style is deliberately plain here.** Thickness, tab style, alignment, colour and whether the bar
 * shows at all become the user's at **1.6.9**, where the bar also picks up its second job as the
 * floating panel's peek strip. What is built here is only enough to switch panels.
 */
object ModuleTabsSpec {
    /**
     * The bar's thickness — **the user's, since 2026-08-26** *(Roger)*. The value itself lives in
     * [com.dash.android.ui.modulepanel.ModulePanelConfig.tabThicknessDp], stored with the panel's
     * edge and size because it belongs to that assembly; what lives here is the shape of the
     * control — where it starts and how far it goes.
     *
     * **The bar sits outside the panel and cuts into the viewport** *(Roger)*. Not out of the
     * panel's own footprint: the module's box would then be thinner than the slot ratio it was
     * authored to, and a module drawn for 8 × 3 must be drawn into 8 × 3. The panel keeps its exact
     * shape and the cost lands on content area instead. **That is what makes this a setting rather
     * than a number DASH picks:** the same thickness is nearly free beside a large 8 × 3 panel and a
     * real bite out of a 16 × 1 one, so no single value can serve both, and the person looking at
     * the screen is the one who can see which case they are in.
     *
     * [DEFAULT_DP] is 36 — where the constant stood from 1.6.8, a comfortable target for a gloved
     * hand without spending more viewport than the job needs. It is only a starting point now.
     *
     * **The range is deliberately wide and has no hard floor.** [MIN_DP] is 24 because that is
     * already the smallest DASH will let anything be — `SystemBarConfig.MIN_ELEMENT_HEIGHT_DP` — so
     * it is a floor the system has agreed to elsewhere rather than a new opinion invented here. 24dp
     * *is* small for a gloved hand, and that is the user's call to make: interface.md reserves hard
     * floors for safety-critical targets, and the reachability question this bar really raises —
     * what happens when the only way off a module is made too small to hit, or hidden altogether —
     * is answered properly at 1.6.9 rather than pre-empted with a floor here.
     *
     * [STEP_DP] is 4, matching every other size stepper in DASH. A different feel under the thumb on
     * one control and not the others would read as a fault rather than a choice.
     *
     * *There is no global UI scale to bind any of this to: the old fluid `LocalDashScale` was removed
     * at 1.5.15, and per-surface sizing — each surface on its own stepper — is the established
     * pattern this now joins.*
     */
    const val DEFAULT_DP = 36
    const val MIN_DP = 24
    const val MAX_DP = 96
    const val STEP_DP = 4
}

/**
 * The tab bar, laid out along the panel's inboard edge.
 *
 * [horizontal] follows the *panel's* orientation rather than the bar's own reading direction: a
 * panel docked top or bottom gives a wide bar with tabs side by side, and a panel docked left or
 * right gives a tall one with tabs stacked and their labels turned to read bottom-to-top.
 */
@Composable
fun ModuleTabs(
    modules: List<InstalledModule>,
    selectedId: String?,
    horizontal: Boolean,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = {},
) {
    val theme = LocalDashTheme.current
    Box(
        modifier = modifier
            .size(width.coerceAtLeast(0.dp), height.coerceAtLeast(0.dp))
            // The bar takes the *primary* surface, the light one (Roger, 2026-08-26 — the pairing
            // was the other way round when the bar was first drawn at 1.6.8). It matches the panel's
            // own floor, which is the same token: where a module leaves its floor bare the bar and
            // the panel read as one surface, and where a module fills its box — Climate does — the
            // join never shows. The selection is what carries the contrast now, not the bar.
            .background(theme.backgroundColourPrimary)
            .clipToBounds()
            .padding(TAB_INSET)
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                modules.forEach { module ->
                    ModuleTab(
                        module = module,
                        selected = module.id == selectedId,
                        horizontal = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onSelect(module.id) },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(TAB_GAP),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                modules.forEach { module ->
                    ModuleTab(
                        module = module,
                        selected = module.id == selectedId,
                        horizontal = false,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onClick = { onSelect(module.id) },
                    )
                }
            }
        }
    }
}

/**
 * One tab.
 *
 * Selected is a filled *dark* pill carrying light text; unselected is the bar's own light surface
 * carrying black. Both are the theme's own documented pairings rather than a choice made by eye —
 * `DashTheme` states the rule plainly: the primary surface carries black ink, the secondary surface
 * carries light-grey ink. Selected runs 11.1:1 and unselected 13.9:1 on the default palette.
 * The mid-grey accent is deliberately not used for either: it was the ink here until 2026-08-26,
 * and on the light surface it is far too close to the background to be read at a glance.
 *
 * **A tab is a full-height target, not just its text.** The whole cell is clickable, so a tab is as
 * easy to hit as the bar is thick — which is the entire reason for spending viewport on a bar rather
 * than hiding the switch in a gesture.
 */
@Composable
private fun ModuleTab(
    module: InstalledModule,
    selected: Boolean,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalDashTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) theme.backgroundColourSecondary else theme.backgroundColourPrimary)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = module.name,
            color = if (selected) theme.textColourSecondary else theme.textColourPrimary,
            fontFamily = theme.font,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = if (horizontal) Modifier.padding(horizontal = 6.dp) else Modifier.readingUpwards(),
        )
    }
}

/**
 * Turn a label a quarter turn so it reads bottom-to-top, for the bar on a vertical panel.
 *
 * A vertical bar is as thin as a horizontal one is shallow, and a module name will not fit across
 * 36dp. Rotating the *drawing* alone is not enough — the text would still be measured against the
 * bar's narrow width and ellipsise before it was ever turned — so the constraints are swapped
 * before measuring and the placement is offset to re-centre what is now a taller-than-wide box.
 *
 * The alternative was to show dots on a vertical bar and names on a horizontal one, which would have
 * been DASH deciding that a vertical panel's user needs less information than a horizontal panel's.
 */
private fun Modifier.readingUpwards(): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        )
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2),
        ) { rotationZ = -90f }
    }
}

private val TAB_INSET = 3.dp
private val TAB_GAP = 3.dp
