package com.dash.android.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.ui.motion.LocalDashTransitions
import com.dash.android.ui.motion.TransitionId
import com.dash.android.ui.settings.content.SettingsContent
import com.dash.android.ui.theme.LocalDashTheme
import com.dash.android.ui.weather.LocalWeatherSnapshot
import com.dash.android.ui.weather.WeatherScene
import com.dash.android.weather.WeatherArt
import com.dash.android.weather.WeatherSnapshot

/**
 * The DASH settings shell. **Adaptive** (roadmap 1.5.x): it lays itself out from the space actually
 * available, not a fixed shape.
 *
 * - **Wide** (tablet, landscape, head unit): the two-pane model — the category/subcategory tree in
 *   the left margin, the content box beside it. Picking a category only reveals its subtree; the
 *   content box stays on its empty landing until a *subcategory* is chosen (category → sub → content).
 * - **Narrow** (phone portrait): the progressive drill-down from interface.md's original three-level
 *   model — the tree fills the screen; tapping a category replaces it with that category's subtree;
 *   tapping a subcategory replaces that with the content. A back control pinned to the bottom walks
 *   one level down and closes at the top. No landing pane on a narrow screen — there's nothing to
 *   land on until you pick something.
 *
 * The two share one navigation state, so rotating the device (the Activity is not recreated — see
 * the manifest) simply reflows between the two.
 */
private const val WIDE_BREAKPOINT_DP = 600

private data class NarrowScreen(val category: SettingsCategory?, val subId: String?) {
    val depth: Int get() = if (subId != null) 2 else if (category != null) 1 else 0
}

@Composable
fun SettingsShell(
    onClose: () -> Unit,
    onOpenLegacy: () -> Unit,
    modifier: Modifier = Modifier,
    initialSubId: String? = null,
) {
    val theme = LocalDashTheme.current
    // Seed the selection from [initialSubId] when opening — the shell is dropped from composition when
    // the blind closes, so a fresh open lands here. Used to return the user to the tab they left when
    // a focused task (bar edit mode) took over the screen, rather than dumping them at the top.
    var selectedCategory by remember {
        mutableStateOf(initialSubId?.let { id -> DASH_SETTINGS_TREE.firstOrNull { cat -> cat.subs.any { it.id == id } } })
    }
    var selectedSubId by remember { mutableStateOf(initialSubId) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColourPrimary)
    ) {
        val wide = maxWidth >= WIDE_BREAKPOINT_DP.dp

        if (wide) {
            WideSettings(
                selectedCategory = selectedCategory,
                selectedSubId = selectedSubId,
                onSelectCategory = { cat ->
                    selectedCategory = cat
                    selectedSubId = null
                },
                onSelectSub = { selectedSubId = it },
                onOpenLegacy = onOpenLegacy,
                onBack = {
                    if (selectedCategory != null) {
                        selectedCategory = null
                        selectedSubId = null
                    } else {
                        onClose()
                    }
                },
            )
        } else {
            NarrowSettings(
                selectedCategory = selectedCategory,
                selectedSubId = selectedSubId,
                onSelectCategory = { cat ->
                    selectedCategory = cat
                    selectedSubId = null
                },
                onSelectSub = { selectedSubId = it },
                onOpenLegacy = onOpenLegacy,
                onBack = {
                    when {
                        selectedSubId != null -> selectedSubId = null
                        selectedCategory != null -> selectedCategory = null
                        else -> onClose()
                    }
                },
            )
        }
    }
}

// ── Wide: two-pane ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WideSettings(
    selectedCategory: SettingsCategory?,
    selectedSubId: String?,
    onSelectCategory: (SettingsCategory) -> Unit,
    onSelectSub: (String) -> Unit,
    onOpenLegacy: () -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalDashTheme.current
    val transitions = LocalDashTransitions.current
    val fontScale = LocalDensity.current.fontScale
    val inSubtree = selectedCategory != null

    Column(Modifier.fillMaxSize()) {
        Breadcrumb(selectedCategory?.label?.uppercase() ?: "SETTINGS")

        Row(modifier = Modifier.fillMaxSize().padding(top = 10.dp, bottom = 28.dp)) {
            // Left margin: tree, or the selected category's subtree. Width scales with the font so
            // long labels don't ellipsise as the text grows.
            Column(
                modifier = Modifier
                    .width((260 * fontScale).dp)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 12.dp)
            ) {
                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        // Into a subtree is DRILL IN; back to the tree is BACK OUT — each its own speed.
                        if (targetState != null) {
                            val d = transitions.millis(TransitionId.SETTINGS_NAV_DRILL_IN)
                            val slide = tween<IntOffset>(d)
                            (slideInHorizontally(slide) { it / 3 } + fadeIn(tween(d))) togetherWith
                                (slideOutHorizontally(slide) { -it / 3 } + fadeOut(tween(d * 2 / 3)))
                        } else {
                            val d = transitions.millis(TransitionId.SETTINGS_NAV_BACK_OUT)
                            val slide = tween<IntOffset>(d)
                            (slideInHorizontally(slide) { -it / 3 } + fadeIn(tween(d))) togetherWith
                                (slideOutHorizontally(slide) { it / 3 } + fadeOut(tween(d * 2 / 3)))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = "tree"
                ) { category ->
                    val scroll = rememberScrollState()
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy((4 * fontScale).dp)
                    ) {
                        if (category == null) {
                            DASH_SETTINGS_TREE.forEach { cat ->
                                NavRow(cat.label, trailing = null, selected = false) { onSelectCategory(cat) }
                            }
                        } else {
                            category.subs.forEach { sub ->
                                NavRow(sub.label, trailing = null, selected = sub.id == selectedSubId) { onSelectSub(sub.id) }
                            }
                        }
                    }
                }

                NavRow("LEGACY SETTINGS →", trailing = null, selected = false, dim = true) { onOpenLegacy() }
                NavRow(if (inSubtree) "‹ BACK" else "‹ CLOSE", trailing = null, selected = false) { onBack() }
            }

            // Right: the content box. A crossfade carries the eye between the weather landing and a
            // chosen subcategory's content (and between subcategories) rather than a hard cut.
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp)) {
                val sub = selectedCategory?.subs?.firstOrNull { it.id == selectedSubId }
                // Crossfade, not AnimatedContent, for the content swap: it re-targets each state's
                // alpha over the full duration when interrupted, so tapping back to a tab whose fade
                // hasn't finished animates home at the chosen speed rather than snapping. At LABORIOUS
                // the interruption window is seconds long, which is where the snap showed up.
                Crossfade(
                    targetState = sub,
                    animationSpec = tween(transitions.millis(TransitionId.SETTINGS_CONTENT_SWAP)),
                    label = "content"
                ) { target ->
                    if (target != null) {
                        SettingsContentBox(target, Modifier.fillMaxSize())
                    } else {
                        // Landing: the layered weather scene (roadmap 1.5.4). Renders offline from the
                        // device clock; the live weather layer upgrades it when a source is available.
                        WeatherLanding(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// ── Narrow: single-pane drill-down ─────────────────────────────────────────────────────────────
@Composable
private fun NarrowSettings(
    selectedCategory: SettingsCategory?,
    selectedSubId: String?,
    onSelectCategory: (SettingsCategory) -> Unit,
    onSelectSub: (String) -> Unit,
    onOpenLegacy: () -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalDashTheme.current
    val transitions = LocalDashTransitions.current
    val screen = NarrowScreen(selectedCategory, selectedSubId)

    Column(Modifier.fillMaxSize()) {
        Breadcrumb(selectedCategory?.label?.uppercase() ?: "SETTINGS")

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                // The same DRILL IN / BACK OUT pair as the wide layout — one control governs the
                // navigation slide in both, so the user isn't asked to set "the same slide" twice.
                val forward = targetState.depth > initialState.depth
                val d = transitions.millis(
                    if (forward) TransitionId.SETTINGS_NAV_DRILL_IN else TransitionId.SETTINGS_NAV_BACK_OUT
                )
                val slide = tween<IntOffset>(d)
                if (forward) {
                    (slideInHorizontally(slide) { it } + fadeIn(tween(d))) togetherWith
                        (slideOutHorizontally(slide) { -it / 3 } + fadeOut(tween(d * 2 / 3)))
                } else {
                    (slideInHorizontally(slide) { -it / 3 } + fadeIn(tween(d))) togetherWith
                        (slideOutHorizontally(slide) { it } + fadeOut(tween(d * 2 / 3)))
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "drill"
        ) { s ->
            when {
                s.subId != null -> {
                    val sub = s.category?.subs?.firstOrNull { it.id == s.subId }
                    if (sub != null) {
                        SettingsContentBox(sub, Modifier.fillMaxSize().padding(horizontal = 16.dp))
                    }
                }
                s.category != null -> {
                    val scroll = rememberScrollState()
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        s.category.subs.forEach { sub ->
                            NavRow(sub.label, trailing = null, selected = false) { onSelectSub(sub.id) }
                        }
                    }
                }
                else -> {
                    val scroll = rememberScrollState()
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DASH_SETTINGS_TREE.forEach { cat ->
                            NavRow(cat.label, trailing = null, selected = false) { onSelectCategory(cat) }
                        }
                        NavRow("LEGACY SETTINGS →", trailing = null, selected = false, dim = true) { onOpenLegacy() }
                    }
                }
            }
        }

        // Back control pinned to the bottom — one level down, or close at the top.
        Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
            NavRow(
                label = if (screen.depth > 0) "‹ BACK" else "‹ CLOSE",
                trailing = null,
                selected = false,
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun Breadcrumb(text: String) {
    val theme = LocalDashTheme.current
    Text(
        text = text,
        color = theme.textColourPrimary,
        fontSize = 13.sp,
        fontFamily = theme.font,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 14.dp)
    )
}

@Composable
private fun WeatherLanding(modifier: Modifier = Modifier) {
    val theme = LocalDashTheme.current
    val context = LocalContext.current
    val art = remember { WeatherArt(context) }
    // The snapshot is warmed and cached at the composition root (see MainScreen) and provided here, so
    // the scene opens on real weather rather than flashing through the clock-only floor. It falls back
    // to the offline floor only for the first moment after a cold boot, before the warm-up returns.
    val snapshot = LocalWeatherSnapshot.current ?: WeatherSnapshot.clockOnly()
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(theme.backgroundColourSecondary)
    ) {
        WeatherScene(snapshot, art, theme.font, Modifier.fillMaxSize())
    }
}

@Composable
private fun SettingsContentBox(sub: SettingsSub, modifier: Modifier = Modifier) {
    val theme = LocalDashTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(theme.backgroundColourSecondary)
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {
        SettingsContent(sub)
    }
}

@Composable
private fun NavRow(
    label: String,
    trailing: String?,
    selected: Boolean,
    dim: Boolean = false,
    onClick: () -> Unit,
) {
    val theme = LocalDashTheme.current
    val fontScale = LocalDensity.current.fontScale
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.accentColourPrimary else theme.backgroundColourPrimary)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = (12 * fontScale).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (dim) theme.textColourPrimary.copy(alpha = 0.45f) else theme.textColourPrimary,
            fontSize = 13.sp,
            fontFamily = theme.font,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (trailing != null) {
            Text(trailing, color = theme.textColourPrimary.copy(alpha = 0.5f), fontSize = 14.sp, fontFamily = theme.font)
        }
    }
}
