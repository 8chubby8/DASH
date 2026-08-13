package com.dash.android.ui.modulepanel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dash.android.panel.BoxedLayer
import com.dash.android.panel.PanelDocument
import com.dash.android.panel.RasterLayer
import com.dash.android.panel.TextAlign
import com.dash.android.panel.TextLayer
import com.dash.android.panel.VectorLayer
import com.dash.android.ui.theme.LocalDashTheme

/**
 * A module's panel, drawn (roadmap 1.6.6).
 *
 * **This is the inside of the castle, and DASH is only the mason.** Everything on screen here was
 * described by the module: its layers, its artwork, its colours, its arrangement. Nothing in this
 * file adds a border, a background, a padding, a radius or a style of DASH's own — the container is
 * [ModulePanel]'s business and it stops at the boundary. The one thing DASH contributes inside the
 * panel is a colour the author *asked* it to choose, through a theme token, which is obeying rather
 * than overriding (§7).
 *
 * Layers are drawn in list order, first at the bottom. Nothing is privileged as "the background":
 * the bottom layer is at the bottom, and that is its only distinction.
 */
@Composable
fun PanelContent(
    document: PanelDocument,
    values: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDashTheme.current
    val dynamics = rememberDynamics(document.layout, values, theme)
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier) {
        val panelWidth = maxWidth
        val panelHeight = maxHeight

        for (layer in document.layout.layers) {
            val dynamic = dynamics[layer.id]

            when (layer) {
                is RasterLayer -> {
                    val bitmap = document.raster[layer.asset] ?: continue
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        // Fit, not FillBounds. A layout is drawn for a known shape (§6), so this is
                        // an exact fit in practice — and where it is not, letterboxing shows the
                        // author their mismatch instead of quietly distorting their artwork to hide
                        // it. The same choice the vector layer makes, because raster and vector are
                        // equal citizens.
                        contentScale = ContentScale.Fit,
                        // `colour` on a raster is a tint (§4.3) — ship the artwork once in greyscale
                        // and colour it per state, rather than shipping an "on" and an "off" image.
                        colorFilter = dynamic?.colour?.let { ColorFilter.tint(it) },
                        modifier = Modifier.layerBox(layer, panelWidth, panelHeight, dynamic),
                    )
                }

                is VectorLayer -> {
                    val art = document.vector[layer.asset] ?: continue
                    VectorArtLayer(
                        art = art,
                        dynamics = dynamics.elementsOf(layer.id),
                        modifier = Modifier.layerBox(layer, panelWidth, panelHeight, dynamic),
                    )
                }

                is TextLayer -> {
                    val text = layer.format(values[layer.value])
                    val colour = layer.colour.resolve(theme) ?: theme.textColourPrimary
                    val family = layer.font.resolve(theme)
                    Canvas(Modifier.fillMaxSize()) {
                        val style = TextStyle(
                            // A fraction of the panel's height, taken straight to pixels. The module
                            // owns this number, so it deliberately does not track DASH's text-size
                            // control or Android's font setting — `toSp` here cancels the scale the
                            // renderer will reapply, leaving exactly the size the author asked for.
                            fontSize = (layer.fontSize * size.height).toSp(),
                            fontFamily = family,
                            fontWeight = if (layer.bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (layer.italic) FontStyle.Italic else FontStyle.Normal,
                        )
                        val measured = measurer.measure(AnnotatedString(text), style)
                        val anchorX = layer.at.x * size.width
                        val anchorY = layer.at.y * size.height
                        val x = when (layer.align) {
                            TextAlign.LEFT -> anchorX
                            TextAlign.CENTER -> anchorX - measured.size.width / 2f
                            TextAlign.RIGHT -> anchorX - measured.size.width
                        }
                        drawText(
                            textLayoutResult = measured,
                            color = dynamic?.colour ?: colour,
                            topLeft = Offset(x, anchorY),
                            alpha = layer.opacity * (dynamic?.opacity ?: 1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Place and transform a layer within the panel.
 *
 * A layer-level `rotate` turns about the layer's own `pivot`, which is right for a needle shipped as
 * its own small raster: `[0.5, 1.0]` is the middle of its bottom edge, and that is where a needle
 * turns. A binding may carry its own pivot, which wins.
 */
private fun Modifier.layerBox(
    layer: BoxedLayer,
    panelWidth: Dp,
    panelHeight: Dp,
    dynamic: Dynamic?,
): Modifier {
    val box = layer.box()
    val pivot = dynamic?.pivot ?: layer.pivot
    return this
        .offset(
            x = panelWidth * box.left + panelWidth * (dynamic?.translate?.x ?: 0f),
            y = panelHeight * box.top + panelHeight * (dynamic?.translate?.y ?: 0f),
        )
        .size(panelWidth * box.width, panelHeight * box.height)
        .graphicsLayer {
            rotationZ = dynamic?.rotate ?: 0f
            transformOrigin = TransformOrigin(pivot.x, pivot.y)
            alpha = layer.opacity * (dynamic?.opacity ?: 1f)
        }
        .revealClip(dynamic)
}

/**
 * A whole-layer `reveal` — a clip, never a scale (§4.4).
 *
 * Scaling a layer to show a proportion distorts it: a bar with rounded ends or a gradient deforms
 * visibly as it shrinks. Revealing draws it at true size and uncovers it.
 */
private fun Modifier.revealClip(dynamic: Dynamic?): Modifier {
    if (dynamic == null || dynamic.reveal >= 1f) return this
    return drawWithContent {
        val visible = visibleRect(dynamic.reveal, dynamic.revealDirection)
        clipRect(visible.left, visible.top, visible.right, visible.bottom) { this@drawWithContent.drawContent() }
    }
}

private fun DrawScope.visibleRect(reveal: Float, direction: String): Rect = when (direction) {
    "down" -> Rect(0f, 0f, size.width, size.height * reveal)
    "left" -> Rect(size.width - size.width * reveal, 0f, size.width, size.height)
    "right" -> Rect(0f, 0f, size.width * reveal, size.height)
    else -> Rect(0f, size.height - size.height * reveal, size.width, size.height)
}

/**
 * The dynamics belonging to elements *inside* one vector layer, re-keyed to the bare element name.
 *
 * `layer#element` is what closes the name-scoping problem — two vector layers may each contain an
 * element called `needle` without ambiguity, because the layer always qualifies the element. The
 * renderer inside a layer only ever sees its own.
 */
private fun Map<String, Dynamic>.elementsOf(layerId: String): Map<String, Dynamic> {
    val prefix = "$layerId#"
    return entries
        .filter { it.key.startsWith(prefix) }
        .associate { it.key.removePrefix(prefix) to it.value }
}
