package com.dash.android.ui.settings.content

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.dash.android.ui.theme.LocalDashTheme

/**
 * Reusable building blocks for a settings content box (roadmap 1.5.3). Every subcategory that goes
 * live fills the same scaffold — a header, then a stack of setting blocks, each pairing a control
 * with an optional live preview — so the whole panel reads as one system wherever you are in it.
 *
 * Everything here lives on the *secondary* surface (the content box is [DashTheme.backgroundColourSecondary]),
 * so the pairing rule holds: text and structure are drawn in the secondary set. Selected chips flip
 * to the light secondary-text colour with the box colour as their ink, which is the one high-contrast
 * accent the muted default theme affords without crossing the pairing.
 */

/**
 * Header zone — identical shape on every subcategory: title, art-deco rule, and an *optional*
 * description. Leave the description off unless it tells the user something the controls below don't
 * already make plain; most tabs don't need one.
 */
@Composable
fun SettingsContentHeader(title: String, description: String? = null) {
    val theme = LocalDashTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = theme.textColourSecondary,
            fontSize = 20.sp,
            fontFamily = theme.font,
            letterSpacing = 0.5.sp,
        )
        if (!description.isNullOrBlank()) {
            Text(
                description,
                color = theme.textColourSecondary.copy(alpha = 0.72f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontFamily = theme.font,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp).fillMaxWidth(),
        ) {
            Box(
                Modifier.width(22.dp).height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(theme.textColourSecondary)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.weight(1f).height(2.dp).background(
                    Brush.horizontalGradient(
                        listOf(theme.textColourSecondary.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
            )
        }
    }
}

/**
 * One setting: name + plain-language help on one side, the control on the other, with an optional
 * full-width live preview beneath. Splits left/right when the box is wide enough, stacks when it
 * isn't — the responsive behaviour that keeps it readable from a phone to a head unit.
 */
@Composable
fun SettingBlock(
    name: String,
    help: String,
    tag: String? = null,
    fullWidthControl: Boolean = false,
    control: @Composable () -> Unit,
    preview: (@Composable () -> Unit)? = null,
) {
    val theme = LocalDashTheme.current
    val label: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(name, color = theme.textColourSecondary, fontSize = 14.sp, fontFamily = theme.font)
            Text(
                help,
                color = theme.textColourSecondary.copy(alpha = 0.68f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = theme.font,
            )
            if (tag != null) Tag(tag)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A control that wants the full width (a wide segmented selector) always stacks under its
        // label, even where the block would otherwise split label-left / control-right — a six-cell
        // segment has nowhere near enough room in the right-hand nook.
        val wide = maxWidth >= 520.dp && !fullWidthControl
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { label() }
                    control()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    label()
                    control()
                }
            }
            preview?.invoke()
        }
    }
}

@Composable
private fun Tag(text: String) {
    val theme = LocalDashTheme.current
    Text(
        text.uppercase(),
        color = theme.textColourSecondary.copy(alpha = 0.9f),
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        fontFamily = theme.font,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, theme.textColourSecondary.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A rounded segmented selector — the preset control (density, bar position, and so on). */
@Composable
fun PresetSegment(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val theme = LocalDashTheme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(theme.textColourSecondary.copy(alpha = 0.08f))
            .border(1.dp, theme.textColourSecondary.copy(alpha = 0.18f), RoundedCornerShape(11.dp))
            .horizontalScroll(rememberScrollState())
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, l ->
            val sel = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) theme.textColourSecondary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Text(
                    l,
                    color = if (sel) theme.backgroundColourSecondary else theme.textColourSecondary.copy(alpha = 0.75f),
                    fontSize = 12.5.sp,
                    fontFamily = theme.font,
                )
            }
        }
    }
}

/**
 * A segmented selector that **shrinks to fit** rather than scrolling. The cells share the width
 * equally, and the label font is measured down until the longest label fits its cell — so a control
 * with many long options (the six transition speeds) stays one tidy row on a phone in portrait
 * instead of truncating and side-scrolling. Give it the full width (it fills what it's handed).
 */
@Composable
fun FitPresetSegment(
    labels: List<String>,
    selected: Int,
    maxFontSp: Float = 12.5f,
    minFontSp: Float = 8f,
    onSelect: (Int) -> Unit,
) {
    val theme = LocalDashTheme.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val outerPad = 3.dp
    val gap = 2.dp
    val chipHPad = 6.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = labels.size.coerceAtLeast(1)
        // Width left for a cell's *text* once the frame, gaps and per-chip padding are removed.
        val chrome = outerPad * 2 + gap * (n - 1) + chipHPad * 2 * n
        val perCellTextPx = with(density) { ((maxWidth - chrome) / n).coerceAtLeast(0.dp).toPx() }

        // Largest font (stepping down from max) at which the widest label still fits a cell.
        val fontSp = remember(labels, perCellTextPx, theme.font) {
            var fs = maxFontSp
            while (fs > minFontSp) {
                val widest = labels.maxOf { l ->
                    measurer.measure(
                        AnnotatedString(l),
                        style = TextStyle(fontSize = fs.sp, fontFamily = theme.font),
                    ).size.width
                }
                if (widest <= perCellTextPx) break
                fs -= 0.5f
            }
            fs
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(theme.textColourSecondary.copy(alpha = 0.08f))
                .border(1.dp, theme.textColourSecondary.copy(alpha = 0.18f), RoundedCornerShape(11.dp))
                .padding(outerPad),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { i, l ->
                val sel = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) theme.textColourSecondary else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = chipHPad, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        l,
                        color = if (sel) theme.backgroundColourSecondary else theme.textColourSecondary.copy(alpha = 0.75f),
                        fontSize = fontSp.sp,
                        fontFamily = theme.font,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A ± stepper for a fluid value — the size controls (bar, elements, DASH text) and anything else
 * dialled up and down. [sub] is an optional caption under the value; [enabled] false greys it and
 * stops the buttons, for a control whose feature has not landed yet.
 */
@Composable
fun Stepper(
    value: String,
    sub: String? = null,
    enabled: Boolean = true,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val theme = LocalDashTheme.current
    val ink = if (enabled) theme.textColourSecondary else theme.textColourSecondary.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(theme.textColourSecondary.copy(alpha = if (enabled) 0.08f else 0.04f))
            .border(1.dp, theme.textColourSecondary.copy(alpha = if (enabled) 0.18f else 0.1f), RoundedCornerShape(11.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepButton("−", ink, enabled, onMinus)
        Column(
            modifier = Modifier.width(66.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = ink, fontSize = 16.sp, fontFamily = theme.font)
            if (sub != null) {
                Text(
                    sub.uppercase(),
                    color = ink.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    fontFamily = theme.font,
                )
            }
        }
        StepButton("+", ink, enabled, onPlus)
    }
}

@Composable
private fun StepButton(sign: String, ink: Color, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalDashTheme.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, color = ink, fontSize = 20.sp, fontFamily = theme.font)
    }
}

/** A raised card that shows the effect of a setting live, so the change is seen and not just read. */
@Composable
fun LivePreviewCard(label: String, content: @Composable () -> Unit) {
    val theme = LocalDashTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(theme.textColourSecondary.copy(alpha = 0.06f))
            .border(1.dp, theme.textColourSecondary.copy(alpha = 0.14f), RoundedCornerShape(11.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                color = theme.textColourSecondary.copy(alpha = 0.55f),
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontFamily = theme.font,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(1.dp).background(theme.textColourSecondary.copy(alpha = 0.14f)))
        }
        content()
    }
}

/**
 * A pill toggle for an on/off setting, drawn in the secondary set like the rest of the scaffold. The
 * caller owns the state — [onToggle] fires on tap and the composable simply reflects [checked].
 */
@Composable
fun SettingToggle(checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    val theme = LocalDashTheme.current
    val trackWidth = 46.dp
    val trackHeight = 28.dp
    val thumb = 22.dp
    val offset by animateDpAsState(if (checked) trackWidth - trackHeight else 0.dp, label = "toggle")
    Box(
        modifier = Modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (checked) theme.textColourSecondary
                else theme.textColourSecondary.copy(alpha = if (enabled) 0.18f else 0.08f)
            )
            .clickable(enabled = enabled) { onToggle() }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(thumb)
                .clip(CircleShape)
                .background(
                    if (checked) theme.backgroundColourSecondary
                    else theme.textColourSecondary.copy(alpha = 0.55f)
                )
        )
    }
}

/** A quiet inline action — deep-links out, "reset", and the like. [colour] overrides the default ink
 *  for the few actions that carry a meaning of their own (a PAUSE that is green, a CLEAR that is red). */
@Composable
fun LinkButton(text: String, colour: Color? = null, onClick: () -> Unit) {
    val theme = LocalDashTheme.current
    Text(
        text,
        color = colour ?: theme.textColourSecondary,
        fontSize = 12.5.sp,
        fontFamily = theme.font,
        textAlign = TextAlign.End,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
    )
}

/** One row of a [DashMenu] — what the user reads, and the value it stands for. */
data class MenuOption(val label: String, val value: String)

/**
 * A DASH dropdown menu (roadmap 1.5.12).
 *
 * Replaces Material's `DropdownMenu`, which was the one thing on a DASH surface that could not be
 * styled from its call site: a Material component reads its colours from `MaterialTheme`, and DASH
 * has never provided one — deliberately, since it owns its own token system. Every other Material
 * component DASH uses (`Text`, above all) takes an explicit colour at every call site and so never
 * consults it, which is why the gap stayed invisible until a menu appeared.
 *
 * Built on `Popup` from compose-ui rather than anything in material3, so it inherits nothing from
 * Google's design language: DASH's surface, DASH's border, DASH's font, DASH's ink. Anchor it by
 * placing it inside the `Box` that holds the control it belongs to — it positions itself directly
 * beneath.
 *
 * The rows are set at 13.5sp rather than the 12sp Material used, which was small enough to be worth
 * complaining about.
 */
@Composable
fun DashMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<MenuOption>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    if (!expanded) return
    val theme = LocalDashTheme.current
    val ink = theme.textColourSecondary
    val shape = RoundedCornerShape(8.dp)
    val rowStyle = TextStyle(fontSize = 13.5.sp, fontFamily = theme.font)

    // Width is measured from the widest label rather than left to the layout. A Popup is given the
    // whole window as its maximum, so a child calling fillMaxWidth expands to the full screen — which
    // is exactly what the first cut did. Intrinsic sizing is not an option either, because it cannot
    // be measured through a scrolling container. So: measure the text, add the padding, clamp.
    val measurer = rememberTextMeasurer()
    val widest = options.maxOfOrNull { measurer.measure(AnnotatedString(it.label), rowStyle).size.width } ?: 0
    val menuWidth = with(LocalDensity.current) { widest.toDp() + ROW_PAD * 2 }
        .coerceIn(MENU_MIN_WIDTH, MENU_MAX_WIDTH)

    Popup(
        popupPositionProvider = BelowAnchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .shadow(10.dp, shape)
                .clip(shape)
                .background(theme.backgroundColourSecondary)
                .border(1.dp, ink.copy(alpha = 0.35f), shape)
                .width(menuWidth)
                // Capped so a filter over a chatty bus — every module id it has ever seen — scrolls
                // rather than running off the screen.
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                Text(
                    option.label,
                    color = if (isSelected) ink else ink.copy(alpha = 0.78f),
                    fontSize = 13.5.sp,
                    fontFamily = theme.font,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) ink.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(option.value); onDismiss() }
                        .padding(horizontal = ROW_PAD, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Drops the menu *below* its anchor, and keeps it on screen.
 *
 * `Popup`'s `alignment` parameter positions the popup **within** the anchor's bounds, so `BottomStart`
 * aligns its bottom edge with the anchor's bottom and it grows *upwards* — which is what the first cut
 * did. Dropping downwards means offsetting by the anchor's height, and only a position provider is
 * given that: it receives the anchor's bounds and the measured popup size, so it can also nudge the
 * menu left when it would overflow the right edge, and flip it above the anchor when there genuinely
 * is not room below.
 */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left
            .coerceAtMost(windowSize.width - popupContentSize.width)
            .coerceAtLeast(0)
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

private val ROW_PAD = 14.dp
private val MENU_MIN_WIDTH = 110.dp
private val MENU_MAX_WIDTH = 320.dp
