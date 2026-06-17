package com.dash.android.ui.systembar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dash.android.ui.theme.LocalDashTheme

/**
 * The DASH system bar — the one persistent interface element, always visible and never
 * dismissable. Renders the configured zones and the elements placed within them.
 *
 * Zone dividers are drawn between zones as a 1dp vertical line. In 1.3.6 edit mode these
 * dividers become draggable handles; for now they are purely visual.
 */
@Composable
fun SystemBar(
    config: SystemBarConfig,
    onAction: (DashAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalDashTheme.current
    val barHeight = config.heightDp.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(theme.barBackground)
    ) {
        config.zones.forEachIndexed { index, zone ->
            if (index > 0) {
                Spacer(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(theme.barAccent.copy(alpha = 0.3f))
                )
            }
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
 * A single zone. Elements are packed by anchor group using a custom Layout:
 * - LEFT anchored elements pack left-to-right from the left edge, in list order
 * - RIGHT anchored elements pack left-to-right as a group, flush against the right edge
 * - CENTRE anchored elements pack as a group centred within the zone
 *
 * This layout is the foundation 1.3.6 drag-and-drop edit mode builds on — dragging offsets
 * positions from this base rather than replacing the layout entirely.
 */
@Composable
private fun Zone(
    zone: ZoneConfig,
    barHeight: Dp,
    elementHeightDp: Dp,
    onAction: (DashAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier.padding(horizontal = 8.dp),
        content = {
            zone.elements.forEach { placement ->
                when (placement.type) {
                    ElementType.SPACER -> Box(
                        Modifier
                            .width((placement.spacerWidthDp ?: SystemBarConfig.DEFAULT_SPACER_WIDTH_DP).dp)
                            .height(elementHeightDp)
                    ) {}
                    else -> {
                        val element = ElementRegistry.get(placement.type)
                        if (element != null) {
                            Box { element.Content(ElementScope(elementHeightDp, barHeight, onAction)) }
                        } else {
                            Box {}
                        }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val elementConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxHeight = elementHeightDp.roundToPx()
        )
        val placeables = measurables.map { it.measure(elementConstraints) }

        val leftIndices = zone.elements.indices.filter { zone.elements[it].anchor == ElementAnchor.LEFT }
        val centreIndices = zone.elements.indices.filter { zone.elements[it].anchor == ElementAnchor.CENTRE }
        val rightIndices = zone.elements.indices.filter { zone.elements[it].anchor == ElementAnchor.RIGHT }

        val zoneWidth = constraints.maxWidth
        val zoneHeight = constraints.maxHeight
        val xPositions = IntArray(zone.elements.size)

        var x = 0
        leftIndices.forEach { i ->
            xPositions[i] = x
            x += placeables[i].width
        }

        val rightTotal = rightIndices.sumOf { placeables[it].width }
        x = zoneWidth - rightTotal
        rightIndices.forEach { i ->
            xPositions[i] = x
            x += placeables[i].width
        }

        val centreTotal = centreIndices.sumOf { placeables[it].width }
        x = (zoneWidth - centreTotal) / 2
        centreIndices.forEach { i ->
            xPositions[i] = x
            x += placeables[i].width
        }

        layout(zoneWidth, zoneHeight) {
            placeables.forEachIndexed { i, placeable ->
                val y = (zoneHeight - placeable.height) / 2
                placeable.placeRelative(xPositions[i], y)
            }
        }
    }
}
