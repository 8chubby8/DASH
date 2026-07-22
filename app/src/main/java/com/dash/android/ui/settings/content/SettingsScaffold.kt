package com.dash.android.ui.settings.content

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val wide = maxWidth >= 520.dp
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

/** A quiet inline action — deep-links out, "reset", and the like. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit) {
    val theme = LocalDashTheme.current
    Text(
        text,
        color = theme.textColourSecondary,
        fontSize = 12.5.sp,
        fontFamily = theme.font,
        textAlign = TextAlign.End,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
    )
}
