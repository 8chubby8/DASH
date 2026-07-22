package com.dash.android.ui.settings

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.ui.motion.LocalTransitionMillis
import com.dash.android.ui.settings.content.SettingsContent
import com.dash.android.ui.theme.LocalDashTheme

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
) {
    val theme = LocalDashTheme.current
    val transitionMillis = LocalTransitionMillis.current
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var selectedSubId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColourPrimary)
    ) {
        val wide = maxWidth >= WIDE_BREAKPOINT_DP.dp

        if (wide) {
            WideSettings(
                transitionMillis = transitionMillis,
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
                transitionMillis = transitionMillis,
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
    transitionMillis: Int,
    selectedCategory: SettingsCategory?,
    selectedSubId: String?,
    onSelectCategory: (SettingsCategory) -> Unit,
    onSelectSub: (String) -> Unit,
    onOpenLegacy: () -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalDashTheme.current
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
                        val slide = tween<IntOffset>(transitionMillis)
                        if (targetState != null) {
                            (slideInHorizontally(slide) { it / 3 } + fadeIn(tween(transitionMillis))) togetherWith
                                (slideOutHorizontally(slide) { -it / 3 } + fadeOut(tween(transitionMillis * 2 / 3)))
                        } else {
                            (slideInHorizontally(slide) { -it / 3 } + fadeIn(tween(transitionMillis))) togetherWith
                                (slideOutHorizontally(slide) { it / 3 } + fadeOut(tween(transitionMillis * 2 / 3)))
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

            // Right: the content box (appears once a category is chosen).
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp)) {
                val sub = selectedCategory?.subs?.firstOrNull { it.id == selectedSubId }
                if (sub != null) {
                    SettingsContentBox(sub, Modifier.fillMaxSize())
                } else {
                    // Landing: draw the empty content box straight away so the pane never opens blank
                    // (its contents arrive in a later version).
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.backgroundColourSecondary)
                    )
                }
            }
        }
    }
}

// ── Narrow: single-pane drill-down ─────────────────────────────────────────────────────────────
@Composable
private fun NarrowSettings(
    transitionMillis: Int,
    selectedCategory: SettingsCategory?,
    selectedSubId: String?,
    onSelectCategory: (SettingsCategory) -> Unit,
    onSelectSub: (String) -> Unit,
    onOpenLegacy: () -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalDashTheme.current
    val screen = NarrowScreen(selectedCategory, selectedSubId)

    Column(Modifier.fillMaxSize()) {
        Breadcrumb(selectedCategory?.label?.uppercase() ?: "SETTINGS")

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val slide = tween<IntOffset>(transitionMillis)
                val forward = targetState.depth > initialState.depth
                if (forward) {
                    (slideInHorizontally(slide) { it } + fadeIn(tween(transitionMillis))) togetherWith
                        (slideOutHorizontally(slide) { -it / 3 } + fadeOut(tween(transitionMillis * 2 / 3)))
                } else {
                    (slideInHorizontally(slide) { -it / 3 } + fadeIn(tween(transitionMillis))) togetherWith
                        (slideOutHorizontally(slide) { it } + fadeOut(tween(transitionMillis * 2 / 3)))
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
