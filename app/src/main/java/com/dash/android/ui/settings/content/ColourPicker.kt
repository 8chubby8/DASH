package com.dash.android.ui.settings.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * A colour as hue (0–360), saturation and value (0–1). Held as HSV, not ARGB, so the picker is its own
 * stable source of truth — an RGB round-trip loses the hue of a grey, which would make the hue slider
 * jump around under the user's finger. Converted to a real colour only at the edges.
 */
data class Hsv(val h: Float, val s: Float, val v: Float)

fun colourToHsv(argb: Long): Hsv {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV((argb and 0xFFFFFFFF).toInt(), out)
    return Hsv(out[0], out[1], out[2])
}

fun Hsv.toColour(): Color = Color.hsv(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

/** Store a Color as an unsigned 32-bit ARGB Long — the form DASH preferences keep splash colour in. */
fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

private val HUE_STOPS = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/**
 * A compact HSV picker: a saturation/value square over a hue bar. [onChange] fires continuously as the
 * user drags (drive the live preview from it); [onCommit] fires when a gesture ends (persist there, so
 * a drag is one write, not hundreds).
 */
@Composable
fun ColourPicker(hsv: Hsv, onChange: (Hsv) -> Unit, onCommit: () -> Unit) {
    val hueColour = Color.hsv(hsv.h.coerceIn(0f, 360f), 1f, 1f)

    // A pointerInput(Unit) block captures its lambdas once. Route the current hsv and callbacks
    // through updated state so a gesture always acts on the latest — otherwise adjusting hue then S/V
    // would snap the hue back, and commit would persist a stale colour.
    val latestHsv by rememberUpdatedState(hsv)
    val latestChange by rememberUpdatedState(onChange)
    val latestCommit by rememberUpdatedState(onCommit)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Saturation (x) × Value (y) ───────────────────────────────────────────────────────────
        var svSize by remember { mutableStateOf(IntSize.Zero) }
        fun setSv(pos: Offset) {
            if (svSize.width == 0 || svSize.height == 0) return
            latestChange(
                latestHsv.copy(
                    s = (pos.x / svSize.width).coerceIn(0f, 1f),
                    v = (1f - pos.y / svSize.height).coerceIn(0f, 1f),
                )
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
                .onSizeChanged { svSize = it }
                .pointerInput(Unit) { detectTapGestures { setSv(it); latestCommit() } }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { latestCommit() }) { change, _ ->
                        change.consume(); setSv(change.position)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColour)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val cx = hsv.s.coerceIn(0f, 1f) * size.width
                val cy = (1f - hsv.v.coerceIn(0f, 1f)) * size.height
                drawCircle(Color.Black, radius = 11f, center = Offset(cx, cy), style = Stroke(width = 5f))
                drawCircle(Color.White, radius = 11f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
            }
        }

        // ── Hue ──────────────────────────────────────────────────────────────────────────────────
        var hueSize by remember { mutableStateOf(IntSize.Zero) }
        fun setHue(pos: Offset) {
            if (hueSize.width == 0) return
            latestChange(latestHsv.copy(h = (pos.x / hueSize.width).coerceIn(0f, 1f) * 360f))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .onSizeChanged { hueSize = it }
                .pointerInput(Unit) { detectTapGestures { setHue(it); latestCommit() } }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { latestCommit() }) { change, _ ->
                        change.consume(); setHue(change.position)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(HUE_STOPS))
                val r = size.height / 2f
                val x = ((hsv.h / 360f) * size.width).coerceIn(r, size.width - r)
                drawCircle(Color.Black, radius = r - 1.5f, center = Offset(x, r), style = Stroke(width = 4f))
                drawCircle(Color.White, radius = r - 1.5f, center = Offset(x, r), style = Stroke(width = 2f))
            }
        }
    }
}
