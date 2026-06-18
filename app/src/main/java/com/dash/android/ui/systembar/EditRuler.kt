package com.dash.android.ui.systembar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dash.android.ui.theme.LocalDashTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private val RULER_HEIGHT = 44.dp
private val ARROW_TOUCH_WIDTH = 80.dp
private val ARROW_SIZE_DP = 10.dp
private val ELEMENT_BOX_HEIGHT = 32.dp
private val ELEMENT_BOX_MIN_WIDTH_DP = 48.dp

/**
 * The edit mode ruler — a horizontal strip that appears adjacent to the system bar when edit mode
 * is active. The bar itself is never touched during editing; all interaction happens here.
 *
 * Zone dividers are represented as triangle arrow markers pointing back toward the bar, and are
 * fully draggable, clamped to a minimum zone width of 48dp.
 * Elements are represented as footprint-sized boxes matching their rendered width in the bar.
 * Dragging an element box across a zone boundary moves the element to that zone; the drop
 * position within the zone infers a new anchor (left/centre/right thirds of zone width).
 *
 * [elementWidths] is populated by [SystemBar] via [onSizeChanged] on each element's Box — it
 * carries each element's measured pixel width at the current [SystemBarConfig.elementHeightDp].
 * On first entry to edit mode this map is already populated from the bar's prior rendering.
 */
@Composable
fun EditRuler(
    config: SystemBarConfig,
    elementWidths: Map<String, Int>,
    barPosition: BarPosition,
    onConfigChange: (SystemBarConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalDashTheme.current
    val density = LocalDensity.current
    val minZonePx = remember(density) { with(density) { 48.dp.toPx() } }
    val paddingPx = remember(density) { with(density) { 8.dp.roundToPx() } }
    val minBoxWidthPx = remember(density) { with(density) { ELEMENT_BOX_MIN_WIDTH_DP.roundToPx() } }

    var draggedElementId by remember { mutableStateOf<String?>(null) }
    var elementDragOffsetPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .background(theme.barBackground)
    ) {
        val rulerWidthPx = constraints.maxWidth.toFloat()
        val currentConfig by rememberUpdatedState(config)

        val naturalPositions = remember(config, elementWidths, rulerWidthPx, paddingPx) {
            computeElementPositions(config, elementWidths, rulerWidthPx.toInt(), paddingPx)
        }

        // Zone divider lines and arrow markers
        var cumulativeFraction = 0f
        config.zones.forEachIndexed { index, zone ->
            if (index > 0) {
                val dividerX = (cumulativeFraction * rulerWidthPx).roundToInt()

                // Subtle zone line — same visual weight as the bar divider
                key("line_${zone.id}") {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(dividerX, 0) }
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(theme.barAccent.copy(alpha = 0.25f))
                    )
                }

                // Draggable arrow marker
                key("arrow_${zone.id}") {
                    DividerArrow(
                        config = config,
                        dividerIndex = index - 1,
                        dividerXPx = dividerX,
                        barPosition = barPosition,
                        rulerWidthPx = rulerWidthPx,
                        minZonePx = minZonePx,
                        onConfigChange = onConfigChange,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }
            cumulativeFraction += zone.widthFraction
        }

        // Element footprint boxes
        config.zones.forEach { zone ->
            zone.elements.forEach { placement ->
                val naturalX = naturalPositions[placement.id] ?: 0
                val widthPx = (elementWidths[placement.id] ?: minBoxWidthPx).coerceAtLeast(minBoxWidthPx)
                val isDragging = draggedElementId == placement.id
                val currentXPx = naturalX + if (isDragging) elementDragOffsetPx.roundToInt() else 0

                key(placement.id) {
                    ElementBox(
                        widthPx = widthPx,
                        xPx = currentXPx,
                        isDragging = isDragging,
                        onDragStart = {
                            draggedElementId = placement.id
                            elementDragOffsetPx = 0f
                        },
                        onDrag = { delta -> elementDragOffsetPx += delta },
                        onDragEnd = {
                            val finalCenterX = naturalX + elementDragOffsetPx + widthPx / 2f
                            val newConfig = computeNewConfig(currentConfig, placement.id, finalCenterX, rulerWidthPx.toInt())
                            onConfigChange(newConfig)
                            draggedElementId = null
                            elementDragOffsetPx = 0f
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }
        }
    }
}

@Composable
private fun DividerArrow(
    config: SystemBarConfig,
    dividerIndex: Int,
    dividerXPx: Int,
    barPosition: BarPosition,
    rulerWidthPx: Float,
    minZonePx: Float,
    onConfigChange: (SystemBarConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalDashTheme.current
    val density = LocalDensity.current
    val currentConfig by rememberUpdatedState(config)
    val currentRulerWidthPx by rememberUpdatedState(rulerWidthPx)
    val touchWidthPx = remember(density) { with(density) { ARROW_TOUCH_WIDTH.roundToPx() } }
    val arrowColor = theme.barText.copy(alpha = 0.65f)

    val startDp = with(density) { (dividerXPx - touchWidthPx / 2).coerceAtLeast(0).toDp() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(start = startDp)
            .width(ARROW_TOUCH_WIDTH)
            .fillMaxHeight()
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Claim the gesture on first touch — no slop wait, no competition
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            val dx = change.positionChange().x
                            change.consume()
                            val zones = currentConfig.zones.toMutableList()
                            val leftZone = zones[dividerIndex]
                            val rightZone = zones[dividerIndex + 1]
                            val combinedFraction = leftZone.widthFraction + rightZone.widthFraction
                            val minFraction = minZonePx / currentRulerWidthPx

                            val rawLeft = leftZone.widthFraction + dx / currentRulerWidthPx
                            val finalLeft = rawLeft.coerceIn(minFraction, combinedFraction - minFraction)
                            val finalRight = combinedFraction - finalLeft

                            zones[dividerIndex] = leftZone.copy(widthFraction = finalLeft)
                            zones[dividerIndex + 1] = rightZone.copy(widthFraction = finalRight)
                            onConfigChange(currentConfig.copy(zones = zones))
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .width(ARROW_SIZE_DP)
                .height(ARROW_SIZE_DP)
        ) {
            val path = Path()
            if (barPosition == BarPosition.BOTTOM) {
                // Points down toward the bottom bar
                path.moveTo(size.width / 2f, size.height)
                path.lineTo(0f, 0f)
                path.lineTo(size.width, 0f)
            } else {
                // Points up toward the top bar
                path.moveTo(size.width / 2f, 0f)
                path.lineTo(0f, size.height)
                path.lineTo(size.width, size.height)
            }
            path.close()
            drawPath(path, arrowColor)
        }
    }
}

@Composable
private fun ElementBox(
    widthPx: Int,
    xPx: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalDashTheme.current
    val density = LocalDensity.current
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.75f else 0.4f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "element_box_alpha"
    )

    Box(
        modifier = modifier
            .padding(start = with(density) { xPx.toDp() })
            .width(with(density) { widthPx.toDp() })
            .height(ELEMENT_BOX_HEIGHT)
            .clip(RoundedCornerShape(4.dp))
            .background(theme.barAccent.copy(alpha = alpha))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    currentOnDragStart()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            currentOnDragEnd()
                            break
                        }
                        if (change.positionChanged()) {
                            val dx = change.positionChange().x
                            change.consume()
                            currentOnDrag(dx)
                        }
                    }
                }
            }
    )
}

/**
 * Computes the natural (undragged) ruler X position for every element in the config, mirroring
 * the Zone layout's anchor-group packing logic. [paddingPx] matches Zone's horizontal padding.
 */
private fun computeElementPositions(
    config: SystemBarConfig,
    elementWidths: Map<String, Int>,
    rulerWidthPx: Int,
    paddingPx: Int
): Map<String, Int> {
    val positions = mutableMapOf<String, Int>()
    var zoneStartPx = 0

    config.zones.forEach { zone ->
        val zoneWidthPx = (zone.widthFraction * rulerWidthPx).roundToInt()
        val availableWidth = zoneWidthPx - 2 * paddingPx

        val leftElements = zone.elements.filter { it.anchor == ElementAnchor.LEFT }
        val centreElements = zone.elements.filter { it.anchor == ElementAnchor.CENTRE }
        val rightElements = zone.elements.filter { it.anchor == ElementAnchor.RIGHT }

        // LEFT: pack from left edge of padded zone
        var x = zoneStartPx + paddingPx
        leftElements.forEach { placement ->
            positions[placement.id] = x
            x += elementWidths[placement.id] ?: 0
        }

        // RIGHT: pack from right edge of padded zone
        val rightTotal = rightElements.sumOf { elementWidths[it.id] ?: 0 }
        x = zoneStartPx + paddingPx + availableWidth - rightTotal
        rightElements.forEach { placement ->
            positions[placement.id] = x
            x += elementWidths[placement.id] ?: 0
        }

        // CENTRE: centre within padded zone
        val centreTotal = centreElements.sumOf { elementWidths[it.id] ?: 0 }
        x = zoneStartPx + paddingPx + (availableWidth - centreTotal) / 2
        centreElements.forEach { placement ->
            positions[placement.id] = x
            x += elementWidths[placement.id] ?: 0
        }

        zoneStartPx += zoneWidthPx
    }

    return positions
}

/**
 * Determines the updated [SystemBarConfig] after an element is dropped at [dropCenterX] in ruler
 * coordinates. The target zone is the zone whose pixel range contains the drop point. The new
 * anchor is inferred from the drop position within that zone: left third → LEFT, middle third →
 * CENTRE, right third → RIGHT. The element is appended to the end of the target zone's list.
 */
private fun computeNewConfig(
    config: SystemBarConfig,
    elementId: String,
    dropCenterX: Float,
    rulerWidthPx: Int
): SystemBarConfig {
    var movingPlacement: ElementPlacement? = null
    config.zones.forEach { zone ->
        zone.elements.forEach { if (it.id == elementId) movingPlacement = it }
    }
    val placement = movingPlacement ?: return config

    // Find target zone
    var cumulativePx = 0
    var targetZoneIdx = config.zones.lastIndex
    for ((idx, zone) in config.zones.withIndex()) {
        val zoneWidthPx = (zone.widthFraction * rulerWidthPx).roundToInt()
        if (dropCenterX < cumulativePx + zoneWidthPx) {
            targetZoneIdx = idx
            break
        }
        cumulativePx += zoneWidthPx
    }

    // Infer anchor from drop position within the target zone
    val targetZoneStartPx = config.zones.take(targetZoneIdx).sumOf { (it.widthFraction * rulerWidthPx).roundToInt() }
    val targetZoneWidthPx = (config.zones[targetZoneIdx].widthFraction * rulerWidthPx).roundToInt()
    val posInZone = dropCenterX - targetZoneStartPx
    val newAnchor = when {
        posInZone < targetZoneWidthPx / 3f -> ElementAnchor.LEFT
        posInZone < targetZoneWidthPx * 2f / 3f -> ElementAnchor.CENTRE
        else -> ElementAnchor.RIGHT
    }

    val newPlacement = placement.copy(anchor = newAnchor)

    val newZones = config.zones.mapIndexed { zIdx, zone ->
        val withoutElement = zone.elements.filter { it.id != elementId }
        if (zIdx == targetZoneIdx) zone.copy(elements = withoutElement + newPlacement)
        else zone.copy(elements = withoutElement)
    }

    return config.copy(zones = newZones)
}
