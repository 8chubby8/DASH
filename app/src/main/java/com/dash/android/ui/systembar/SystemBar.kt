package com.dash.android.ui.systembar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dash.android.ui.theme.LocalDashTheme

/**
 * The DASH system bar — the one persistent interface element, always visible and never
 * dismissable. Renders the configured zones and the elements placed within them.
 *
 * The rendered height is the user-defined base height ([SystemBarConfig.heightDp]) expressed
 * directly in dp. The DASH UI scale multiplier is intentionally not applied here — bar height
 * is set by the dp control alone. Scale will be reintroduced as a multiplier across all chrome
 * elements once there is more than one element for it to scale uniformly (1.3.x later).
 *
 * 1.3.1 lays out a single full-width zone with anchor-based placement (left / centre / right).
 * Multi-zone splitting and inter-element snap packing arrive in 1.3.5; the structure here is
 * built to extend to them without rework.
 */
@Composable
fun SystemBar(
    config: SystemBarConfig,
    onAction: (DashAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight = config.heightDp.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(LocalDashTheme.current.barBackground)
    ) {
        config.zones.forEach { zone ->
            Zone(
                zone = zone,
                barHeight = barHeight,
                elementHeightDp = config.elementHeightDp.dp,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(zone.widthFraction.coerceAtLeast(0.01f))
            )
        }
    }
}

/**
 * A single zone. Each placed element is positioned by its [ElementAnchor] within the zone. With
 * two mandatory elements (alerts left, settings right) this resolves to a clean left/right split;
 * the same Box-per-anchor approach generalises to packed multi-element zones later.
 */
@Composable
private fun Zone(
    zone: ZoneConfig,
    barHeight: Dp,
    elementHeightDp: Dp,
    onAction: (DashAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(horizontal = 8.dp)) {
        zone.elements.forEach { placement ->
            val element = ElementRegistry.get(placement.type) ?: return@forEach
            val alignment = when (placement.anchor) {
                ElementAnchor.LEFT -> Alignment.CenterStart
                ElementAnchor.CENTRE -> Alignment.Center
                ElementAnchor.RIGHT -> Alignment.CenterEnd
            }
            Box(
                modifier = Modifier.fillMaxHeight().align(alignment),
                contentAlignment = Alignment.Center
            ) {
                val scope = ElementScope(
                    heightDp = elementHeightDp,
                    barHeight = barHeight,
                    onAction = onAction
                )
                element.Content(scope)
            }
        }
    }
}
