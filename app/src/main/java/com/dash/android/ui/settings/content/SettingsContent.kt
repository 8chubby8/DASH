package com.dash.android.ui.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.ui.settings.SettingsSub
import com.dash.android.ui.theme.LocalDashTheme

/**
 * The content router for the settings box. Each subcategory that has gone live claims its id here;
 * everything else falls through to the honest WIP placeholder from 1.5.2. Adding a tab is one line —
 * the navigation shell never changes.
 */
@Composable
fun SettingsContent(sub: SettingsSub) {
    when (sub.id) {
        "appearance.density" -> SizeScaleContent()
        "appearance.transitions" -> MotionContent()
        "system.location" -> LocationContent()
        else -> WipPlaceholder(sub)
    }
}

@Composable
private fun WipPlaceholder(sub: SettingsSub) {
    val theme = LocalDashTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            sub.label.uppercase(),
            color = theme.textColourSecondary,
            fontSize = 16.sp,
            fontFamily = theme.font,
            letterSpacing = 2.sp,
        )
        Text(
            sub.wipVersion?.let { "Arrives with $it." } ?: "Empty — wired in a later 1.5.x version.",
            color = theme.textColourSecondary.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontFamily = theme.font,
        )
    }
}
