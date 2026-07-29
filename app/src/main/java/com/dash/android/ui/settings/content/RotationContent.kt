package com.dash.android.ui.settings.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.rotation.DashOrientation
import com.dash.android.ui.systembar.BarPosition
import com.dash.android.ui.systembar.SystemBarConfig
import com.dash.android.ui.theme.LocalDashTheme
import kotlinx.coroutines.launch
import com.dash.android.ui.common.TINY
import com.dash.android.ui.common.TINY_LINE
import com.dash.android.ui.common.SETTING_SPACING

/**
 * Layout › Rotation (roadmap 1.5.15) — the rehome of the legacy panel's ROTATION control, rebuilt.
 *
 * **Why Layout rather than Appearance.** Layout owns the structural decisions — where the bar sits,
 * how zones divide, where panels dock — and which way the whole screen faces is the most structural
 * of them. Appearance stays about how things look rather than how they are arranged. Roger's call.
 *
 * **The glyph is a miniature of the user's real layout, and that is the point.** A plain rectangle
 * cannot show a 180° rotation: portrait and portrait-reversed are the same shape, as are the two
 * landscapes. So each tile draws the screen at its true aspect ratio *with the user's own system bar
 * on the edge it will actually appear on* — read live from [SystemBarConfig]. Reversed options put
 * that bar at the opposite edge, which is exactly what the eye needs and exactly what will happen.
 * Move the bar to the top in Layout › System Bar and every tile here follows.
 *
 * **Five choices in one flat group**, Auto first. Picking a fixed orientation turns Auto off by
 * implication rather than greying the others out, so there is never a dead control on screen.
 *
 * **It applies live, with no confirmation.** Choosing Landscape while holding the device in portrait
 * will spin the interface under your hand — correct on a fixed car screen, dramatic on a handheld,
 * and undone by tapping another tile. Every other control in DASH shows its effect immediately and
 * this one is no different.
 */
@Composable
fun RotationContent() {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val prefs = remember { DashPreferences(appContext) }
    val scope = rememberCoroutineScope()

    val autoRotate by prefs.autoRotate.collectAsState(initial = true)
    val lockedOrientation by prefs.lockedOrientation.collectAsState(initial = null)
    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())

    // The device's own proportions, so the tiles are a picture of this screen and not a generic one.
    val metrics = remember { appContext.resources.displayMetrics }
    val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    val longSide = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    val ratio = if (longSide > 0f) shortSide / longSide else 0.6f

    val selected = DashOrientation.from(lockedOrientation)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SETTING_SPACING),
    ) {
        SettingsContentHeader("Rotation")

        val barAtTopNormally = barConfig.position == BarPosition.TOP

        // Auto stands apart from the four fixed orientations (roadmap 1.5.15, Roger). It is a
        // different kind of answer — "whichever way you are holding it" rather than one of four
        // positions — so it gets its own row at its own width rather than being a fifth of a set.
        RotationTile(
            label = "Auto",
            selected = autoRotate,
            ratio = ratio,
            barAtTop = false,
            portrait = false,
            auto = true,
            modifier = Modifier.width(TILE_WIDTH),
        ) { scope.launch { prefs.saveAutoRotate(true) } }

        // The four share the box's width equally, so they read as one set of alternatives and stay
        // the same size as each other whatever the box is doing.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            DashOrientation.entries.forEach { orientation ->
                RotationTile(
                    label = orientation.label,
                    selected = !autoRotate && orientation == selected,
                    ratio = ratio,
                    // The bar sits on its configured edge normally, and on the opposite edge when
                    // DASH is upside down — the whole difference between the pairs.
                    barAtTop = barAtTopNormally != orientation.reversed,
                    portrait = orientation.portrait,
                    auto = false,
                    modifier = Modifier.weight(1f),
                ) {
                    scope.launch {
                        prefs.saveAutoRotate(false)
                        prefs.saveLockedOrientation(orientation.stored)
                    }
                }
            }
        }
    }
}

@Composable
private fun RotationTile(
    label: String,
    selected: Boolean,
    ratio: Float,
    barAtTop: Boolean,
    portrait: Boolean,
    auto: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalDashTheme.current
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) theme.textColourSecondary.copy(alpha = 0.14f) else Color.Transparent
            )
            .border(
                if (selected) 2.dp else 1.dp,
                theme.textColourSecondary.copy(alpha = if (selected) 0.75f else 0.18f),
                shape,
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.height(GLYPH_BOX).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScreenGlyph(ratio, barAtTop, portrait, auto, selected)
        }
        Text(
            label,
            color = theme.textColourSecondary.copy(alpha = if (selected) 1f else 0.72f),
            fontSize = TINY,
            lineHeight = TINY_LINE,
            textAlign = TextAlign.Center,
            fontFamily = theme.font,
        )
    }
}

/**
 * The screen, at its real proportions, with the DASH system bar drawn on the edge it will occupy.
 * For Auto the bar is left off and a rotation mark stands in its place — Auto has no one answer,
 * and drawing a bar somewhere would claim it did.
 */
@Composable
private fun ScreenGlyph(
    ratio: Float,
    barAtTop: Boolean,
    portrait: Boolean,
    auto: Boolean,
    selected: Boolean,
) {
    val theme = LocalDashTheme.current
    val ink = theme.textColourSecondary.copy(alpha = if (selected) 0.95f else 0.55f)
    val shape = RoundedCornerShape(3.dp)

    val w: Dp
    val h: Dp
    if (auto) {
        // Auto shows the device's natural proportions — long side across.
        w = GLYPH_LONG
        h = GLYPH_LONG * ratio
    } else if (portrait) {
        w = GLYPH_LONG * ratio
        h = GLYPH_LONG
    } else {
        w = GLYPH_LONG
        h = GLYPH_LONG * ratio
    }

    Box(
        modifier = Modifier
            .size(w, h)
            .clip(shape)
            .border(1.5.dp, ink, shape),
        contentAlignment = when {
            auto -> Alignment.Center
            barAtTop -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        },
    ) {
        if (auto) {
            Text("⟳", color = ink, fontSize = 17.sp, fontFamily = theme.font)
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BAR_THICKNESS)
                    .padding(horizontal = 2.dp)
                    .background(ink)
            )
        }
    }
}

/** Auto's width. The other four take their width from the box, so only this one is fixed. */
private val TILE_WIDTH = 96.dp
private val TILE_GAP = 10.dp
private val GLYPH_BOX = 58.dp
private val GLYPH_LONG = 52.dp
private val BAR_THICKNESS = 5.dp
