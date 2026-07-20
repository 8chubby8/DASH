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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.ui.motion.LocalTransitionMillis
import com.dash.android.ui.theme.LocalDashTheme

/**
 * The DASH settings shell (roadmap 1.5.2). A two-pane panel that grows out of the system bar
 * (MainScreen animates its appearance) and inherits its visual identity from the theme tokens.
 *
 * Navigation is Roger's two-pane model, not interface.md's original three-column one: the left
 * margin shows the **main tree** (the ten categories) until a category is tapped, at which point
 * its **subcategories** take the left margin's place and the content box appears on the right. The
 * back button at the bottom of the left margin walks back up one level — subtree to main tree — and
 * closes the panel when there is no level left to climb. The settings button on the bar closes it
 * from anywhere (handled in MainScreen).
 *
 * In 1.5.2 the content box only ever shows an honest placeholder — every subcategory names the
 * version its feature arrives at. A temporary LEGACY SETTINGS button reaches the old flat panel so
 * nothing built in 1.1.x–1.4.x is unreachable while it waits to be rehomed; it is removed at 1.5.12.
 */
@Composable
fun SettingsShell(
    onClose: () -> Unit,
    onOpenLegacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDashTheme.current
    val transitionMillis = LocalTransitionMillis.current
    // Android's master font scale — used to grow the row spacing with the font so the list doesn't
    // feel cramped as the text gets larger.
    val fontScale = LocalDensity.current.fontScale
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var selectedSubId by remember { mutableStateOf<String?>(null) }

    val inSubtree = selectedCategory != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColourPrimary)
    ) {
        // Slim breadcrumb header — inherits the primary surface, so it reads as one piece with the
        // bar above it.
        Text(
            text = selectedCategory?.label?.uppercase() ?: "SETTINGS",
            color = theme.textColourPrimary,
            fontSize = 13.sp,
            fontFamily = theme.font,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 14.dp)
        )

        Row(modifier = Modifier.fillMaxSize()) {

            // ── Left margin: main tree OR the selected category's subtree ──────────────────
            // Width scales with the font so labels keep the same fit as the text grows — a fixed
            // column ellipsised the longer labels ("Notificatio…") at large font sizes.
            Column(
                modifier = Modifier
                    .width((260 * fontScale).dp)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 12.dp, bottom = 16.dp)
            ) {
                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        if (targetState != null) {
                            // Descending into a subtree: it grows in from the right as the main
                            // tree drops away to the left.
                            (slideInHorizontally { it / 3 } + fadeIn(tween(transitionMillis))) togetherWith
                                (slideOutHorizontally { -it / 3 } + fadeOut(tween(transitionMillis * 2 / 3)))
                        } else {
                            (slideInHorizontally { -it / 3 } + fadeIn(tween(transitionMillis))) togetherWith
                                (slideOutHorizontally { it / 3 } + fadeOut(tween(transitionMillis * 2 / 3)))
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
                                NavRow(
                                    label = cat.label,
                                    trailing = "›",
                                    selected = false,
                                    onClick = {
                                        selectedCategory = cat
                                        selectedSubId = cat.subs.firstOrNull()?.id
                                    }
                                )
                            }
                        } else {
                            category.subs.forEach { sub ->
                                NavRow(
                                    label = sub.label,
                                    trailing = null,
                                    selected = sub.id == selectedSubId,
                                    onClick = { selectedSubId = sub.id }
                                )
                            }
                        }
                    }
                }

                // Temporary bridge to the pre-1.5.2 flat settings (removed at 1.5.12).
                NavRow(
                    label = "LEGACY SETTINGS →",
                    trailing = null,
                    selected = false,
                    dim = true,
                    onClick = onOpenLegacy
                )
                // Back button: up one level, or close at the top.
                NavRow(
                    label = if (inSubtree) "‹ BACK" else "‹ CLOSE",
                    trailing = null,
                    selected = false,
                    onClick = {
                        if (inSubtree) {
                            selectedCategory = null
                            selectedSubId = null
                        } else {
                            onClose()
                        }
                    }
                )
            }

            // ── Right: the content box (appears once a category is chosen) ─────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                if (selectedCategory != null) {
                    val sub = selectedCategory?.subs?.firstOrNull { it.id == selectedSubId }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.backgroundColourSecondary)
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = sub?.label?.uppercase() ?: "",
                            color = theme.textColourSecondary,
                            fontSize = 16.sp,
                            fontFamily = theme.font,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = sub?.wipVersion?.let { "Arrives with $it." }
                                ?: "Empty — wired in a later 1.5.x version.",
                            color = theme.textColourSecondary.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontFamily = theme.font
                        )
                    }
                }
            }
        }
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
            // Stay on one line so long labels (Notifications, Module Management) don't wrap; the
            // weight lets the label take the row's free width, keeping any trailing chevron in place.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (trailing != null) {
            Text(trailing, color = theme.textColourPrimary.copy(alpha = 0.5f), fontSize = 14.sp, fontFamily = theme.font)
        }
    }
}
