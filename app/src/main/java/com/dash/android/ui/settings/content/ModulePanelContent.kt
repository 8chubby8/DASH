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
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.common.SETTING_SPACING
import com.dash.android.ui.common.TINY
import com.dash.android.ui.common.TINY_LINE
import com.dash.android.ui.modulepanel.ModulePanelConfig
import com.dash.android.ui.modulepanel.ModulePanelSpec
import com.dash.android.ui.modulepanel.PanelSize
import com.dash.android.ui.modulepanel.PanelEdge
import com.dash.android.ui.modulepanel.effectiveEdge
import com.dash.android.ui.systembar.BarPosition
import com.dash.android.ui.systembar.SystemBarConfig
import com.dash.android.ui.theme.LocalDashTheme
import kotlinx.coroutines.launch

/**
 * Layout › Module Panel — how big the panel is (roadmap 1.6.4) and where it docks (1.6.3).
 *
 * **Why Layout.** The panel is a *wall* DASH owns, so its size and placement are structural
 * decisions and belong beside the System Bar and Rotation. What happens *inside* the panel belongs
 * to the module and is never configured here — that is the Module Mantra as a settings tree.
 *
 * **Size first, position second** (Roger, 1.6.4). Size is the larger decision, and the position
 * tiles draw the panel at whatever size is chosen above them, so the page reads top to bottom as one
 * continuous answer rather than two unrelated controls.
 *
 * **Every tile is a miniature of the real screen**, following the Rotation tab's precedent: the
 * device at its true proportions, the user's own bar on the edge it actually occupies, and the panel
 * drawn from the same ratio the real one uses — so a Small tile really is a sixteenth of its edge
 * and the three sizes are honestly to scale against each other. That matters most on the position
 * row, where two tiles show something the user did not literally ask for: the panel and the bar can
 * never share an edge, so the bar's edge is greyed and shows the bar alone.
 *
 * The stored preference is still the edge they picked. Move the bar away and the panel returns
 * there on its own; nothing the user chose is ever quietly rewritten.
 */
@Composable
fun ModulePanelContent() {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val prefs = remember { DashPreferences(appContext) }
    val scope = rememberCoroutineScope()

    val config by prefs.modulePanelConfig.collectAsState(initial = ModulePanelConfig.default())
    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())

    // The device's own proportions, so the tiles picture this screen rather than a generic one.
    val metrics = remember { appContext.resources.displayMetrics }
    val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    val longSide = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    val ratio = if (longSide > 0f) shortSide / longSide else 0.6f

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SETTING_SPACING),
    ) {
        SettingsContentHeader("Module Panel")

        val barAtTop = barConfig.position == BarPosition.TOP
        val barEdge = if (barAtTop) PanelEdge.TOP else PanelEdge.BOTTOM
        val drawnNow = effectiveEdge(config.edge, barConfig.position)

        // Size before position (Roger, 1.6.4). Size is the larger decision, and the position tiles
        // below draw the panel at whatever size is chosen here — so the page reads top to bottom as
        // one continuous answer rather than two unrelated controls.
        SettingsSectionHeader("Size")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            PanelSize.entries.forEach { size ->
                PanelTile(
                    label = size.label,
                    selected = config.size == size,
                    unavailable = false,
                    ratio = ratio,
                    barAtTop = barAtTop,
                    // Drawn on the edge the panel is actually on, at this tile's own size, so each
                    // tile previews the real result rather than a generic diagram.
                    drawnEdge = drawnNow,
                    panelSize = size,
                    modifier = Modifier.weight(1f),
                ) {
                    scope.launch { prefs.saveModulePanelConfig(config.copy(size = size)) }
                }
            }
        }

        SettingsSectionHeader("Position")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            PanelEdge.entries.forEach { edge ->
                // The edge the bar holds cannot be chosen (Roger, 1.6.3) — the two never share an
                // edge, so offering it would be offering something DASH would immediately undo.
                val unavailable = edge == barEdge
                PanelTile(
                    label = edge.label,
                    // Selection tracks the stored *preference*, even when that edge is currently
                    // unavailable — a tile that is both selected and greyed says "this is still your
                    // choice, it just cannot apply while the bar is there", which is the truth. The
                    // panel meanwhile sits on its displaced edge. Reachable by choosing an edge and
                    // then moving the bar onto it; greying only prevents choosing it anew.
                    selected = config.edge == edge,
                    unavailable = unavailable,
                    ratio = ratio,
                    barAtTop = barAtTop,
                    drawnEdge = effectiveEdge(edge, barConfig.position),
                    panelSize = config.size,
                    modifier = Modifier.weight(1f),
                ) {
                    scope.launch { prefs.saveModulePanelConfig(config.copy(edge = edge)) }
                }
            }
        }
    }
}

@Composable
private fun PanelTile(
    label: String,
    selected: Boolean,
    unavailable: Boolean,
    ratio: Float,
    barAtTop: Boolean,
    drawnEdge: PanelEdge,
    panelSize: PanelSize,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalDashTheme.current
    val shape = RoundedCornerShape(11.dp)
    // Greyed out rather than removed: the set of edges is fixed at four, and dropping one would
    // reshuffle the row every time the bar moved. A dimmed tile keeps the four in place and says
    // why this one cannot be had.
    val dim = if (unavailable) 0.34f else 1f
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) theme.textColourSecondary.copy(alpha = 0.14f * dim) else Color.Transparent
            )
            .border(
                if (selected) 2.dp else 1.dp,
                theme.textColourSecondary.copy(alpha = (if (selected) 0.75f else 0.18f) * dim),
                shape,
            )
            .then(if (unavailable) Modifier else Modifier.clickable { onClick() })
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.height(GLYPH_BOX).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScreenGlyph(ratio, barAtTop, drawnEdge, panelSize, selected, unavailable)
        }
        Text(
            if (unavailable) "$label — bar" else label,
            color = theme.textColourSecondary.copy(alpha = (if (selected) 1f else 0.72f) * dim),
            fontSize = TINY,
            lineHeight = TINY_LINE,
            textAlign = TextAlign.Center,
            fontFamily = theme.font,
        )
    }
}

/**
 * The screen at its real proportions, with the bar on its edge and the panel on the edge it will
 * actually be drawn on. The panel block is drawn at roughly its true share of the screen so the
 * tiles read as a picture of the result rather than as four identical diagrams.
 */
@Composable
private fun ScreenGlyph(
    ratio: Float,
    barAtTop: Boolean,
    drawnEdge: PanelEdge,
    panelSize: PanelSize,
    selected: Boolean,
    unavailable: Boolean,
) {
    val theme = LocalDashTheme.current
    val dim = if (unavailable) 0.34f else 1f
    val ink = theme.textColourSecondary.copy(alpha = (if (selected) 0.95f else 0.55f) * dim)
    val panelInk = theme.textColourSecondary.copy(alpha = (if (selected) 0.55f else 0.3f) * dim)
    val shape = RoundedCornerShape(3.dp)

    // Landscape proportions, matching how a head unit is usually mounted.
    val w = GLYPH_LONG
    val h = GLYPH_LONG * ratio

    Box(
        modifier = Modifier.size(w, h).clip(shape).border(1.5.dp, ink, shape),
    ) {
        // The bar — full width on its own edge, always senior.
        Box(
            Modifier
                .align(if (barAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BAR_THICKNESS)
                .padding(horizontal = 2.dp)
                .background(ink)
        )
        // An unavailable tile draws the bar alone — no panel. Showing the displaced panel here would
        // contradict the label: the tile says this edge belongs to the bar, so the picture should
        // say the same rather than illustrating a fallback the user cannot currently choose.
        if (unavailable) return@Box

        // The panel. On a vertical edge it runs the screen height *less the bar* and butts against
        // the end away from the bar — the same geometry as the real layout, where the bar's edge is
        // the one the panel has to clear.
        // Thickness is derived from the same ratio the real panel uses, against the same long edge,
        // so a Small tile really is a sixteenth of its edge and the three sizes are honestly to
        // scale against each other rather than three arbitrary bands.
        when (drawnEdge) {
            PanelEdge.TOP, PanelEdge.BOTTOM -> Box(
                Modifier
                    .align(if (drawnEdge == PanelEdge.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(ModulePanelSpec.thicknessFor(panelSize, w).coerceAtLeast(GLYPH_MIN_THICK))
                    .background(panelInk)
            )
            PanelEdge.LEFT, PanelEdge.RIGHT -> Box(
                Modifier
                    .align(
                        when {
                            drawnEdge == PanelEdge.LEFT && barAtTop -> Alignment.BottomStart
                            drawnEdge == PanelEdge.LEFT -> Alignment.TopStart
                            barAtTop -> Alignment.BottomEnd
                            else -> Alignment.TopEnd
                        }
                    )
                    .width(
                        ModulePanelSpec.thicknessFor(panelSize, h - BAR_THICKNESS)
                            .coerceAtLeast(GLYPH_MIN_THICK)
                    )
                    .height(h - BAR_THICKNESS)
                    .background(panelInk)
            )
        }
    }
}

private val TILE_GAP = 10.dp
private val GLYPH_BOX = 58.dp
private val GLYPH_LONG = 52.dp
private val BAR_THICKNESS = 5.dp

/** A floor so Small stays visible in a 52dp glyph, where a true sixteenth would be barely a hair. */
private val GLYPH_MIN_THICK: Dp = 3.dp
