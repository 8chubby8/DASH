package com.dash.android.ui.modulepanel

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A throwaway stand-in for the real module panel (roadmap 1.6.x). It exists only so the settings
 * shell has something to conform to: the settings panel must yield to a module panel that is
 * *expanded* (a persistent, always-visible surface — the king's castle DASH's own chrome may not
 * sit on top of) and may cover it when *minimised* (retracted). The expand/minimise button flips
 * that state so the behaviour can be seen live on the tablet.
 *
 * This deliberately looks nothing like the settings surface — a foreign dark panel — because the
 * module is king within its own domain. 1.6.x deletes this file wholesale and replaces it with the
 * real panel (docking edges, peek strips, actual module content).
 */
val MODULE_PANEL_EXPANDED_HEIGHT = 220.dp
val MODULE_PANEL_MINIMISED_HEIGHT = 40.dp

private val PANEL_FILL = Color(0xFF1B1B22)
private val PANEL_EDGE = Color(0xFF3A3A46)
private val PANEL_TEXT = Color(0xFFBFBFC9)

@Composable
fun ModulePanelPlaceholder(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(if (expanded) MODULE_PANEL_EXPANDED_HEIGHT else MODULE_PANEL_MINIMISED_HEIGHT)
            .animateContentSize()
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .background(PANEL_FILL)
    ) {
        // Handle row — label plus the expand/minimise toggle. Always present, so the panel can
        // always be brought back even when settings covers the minimised strip.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MODULE_PANEL_MINIMISED_HEIGHT)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MODULE PANEL — PLACEHOLDER",
                color = PANEL_TEXT,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(PANEL_EDGE)
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    if (expanded) "MINIMISE ▾" else "EXPAND ▴",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
        if (expanded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "the module is king within its own domain",
                    color = PANEL_TEXT.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
