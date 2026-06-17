package com.dash.android.ui.systembar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dash.android.ui.theme.LocalDashTheme
import kotlin.math.abs

/**
 * The DASH system bar — the one persistent interface element, always visible and never
 * dismissable. Renders the configured zones and the elements placed within them.
 *
 * In edit mode the bar shows a visible border, and zone dividers become draggable handles.
 * [onConfigChange] is called on every drag frame with the updated config — the caller is
 * responsible for holding this as transient state and persisting it only when edit mode exits.
 */
@Composable
fun SystemBar(
    config: SystemBarConfig,
    editMode: Boolean,
    onConfigChange: (SystemBarConfig) -> Unit,
    onAction: (DashAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalDashTheme.current
    val barHeight = config.heightDp.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val barWidthPx = constraints.maxWidth.toFloat()

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.barBackground)
                .then(if (editMode) Modifier.border(2.dp, theme.barText.copy(alpha = 0.35f)) else Modifier)
        ) {
            config.zones.forEachIndexed { index, zone ->
                if (index > 0) {
                    if (editMode) {
                        DraggableDivider(
                            config = config,
                            dividerIndex = index - 1,
                            barWidthPx = barWidthPx,
                            onConfigChange = onConfigChange
                        )
                    } else {
                        Spacer(
                            Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(theme.barAccent.copy(alpha = 0.3f))
                        )
                    }
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
}

/**
 * A draggable zone divider for edit mode. Renders as a 2dp visual line centred inside a 16dp
 * touch target. Drag gestures adjust the widthFraction of the two adjacent zones while keeping
 * their combined fraction constant. Detent snap points at 1/4, 1/3, 1/2, 2/3, 3/4 of bar width
 * pull the divider into alignment when within 12dp — the user can drag past them freely.
 */
@Composable
private fun DraggableDivider(
    config: SystemBarConfig,
    dividerIndex: Int,
    barWidthPx: Float,
    onConfigChange: (SystemBarConfig) -> Unit
) {
    val theme = LocalDashTheme.current
    val density = LocalDensity.current
    val currentConfig by rememberUpdatedState(config)
    val currentBarWidthPx by rememberUpdatedState(barWidthPx)

    val snapThresholdPx = remember(density) { with(density) { 12.dp.toPx() } }
    val minZonePx = remember(density) { with(density) { 48.dp.toPx() } }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .width(16.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val zones = currentConfig.zones.toMutableList()
                    val leftZone = zones[dividerIndex]
                    val rightZone = zones[dividerIndex + 1]
                    val combinedFraction = leftZone.widthFraction + rightZone.widthFraction
                    val minZoneFraction = minZonePx / currentBarWidthPx

                    // Apply raw drag delta and clamp so neither zone drops below minimum width
                    val rawLeft = leftZone.widthFraction + dragAmount.x / currentBarWidthPx
                    val clampedLeft = rawLeft.coerceIn(minZoneFraction, combinedFraction - minZoneFraction)

                    // Cumulative position of this divider from the left edge of the bar
                    val cumulativeBefore = zones.take(dividerIndex).sumOf { it.widthFraction.toDouble() }.toFloat()
                    val cumulativeAtDivider = cumulativeBefore + clampedLeft

                    // Detent snap: pull toward preset fractions when within threshold
                    val snapPoints = listOf(0.25f, 1f / 3f, 0.5f, 2f / 3f, 0.75f)
                    val nearest = snapPoints.minByOrNull { abs(it - cumulativeAtDivider) }!!
                    val distToSnapPx = abs(nearest - cumulativeAtDivider) * currentBarWidthPx
                    val snappedCumulative = if (distToSnapPx < snapThresholdPx) nearest else cumulativeAtDivider

                    val finalLeft = (snappedCumulative - cumulativeBefore)
                        .coerceIn(minZoneFraction, combinedFraction - minZoneFraction)
                    val finalRight = combinedFraction - finalLeft

                    zones[dividerIndex] = leftZone.copy(widthFraction = finalLeft)
                    zones[dividerIndex + 1] = rightZone.copy(widthFraction = finalRight)
                    onConfigChange(currentConfig.copy(zones = zones))
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(theme.barText.copy(alpha = 0.6f))
        )
    }
}

/**
 * A single zone. Elements are packed by anchor group using a custom Layout:
 * - LEFT anchored elements pack left-to-right from the left edge, in list order
 * - RIGHT anchored elements pack left-to-right as a group, flush against the right edge
 * - CENTRE anchored elements pack as a group centred within the zone
 *
 * This layout is the foundation 1.3.7 drag-and-drop element repositioning builds on.
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
