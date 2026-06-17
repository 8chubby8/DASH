package com.dash.android.ui.systembar.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.dash.android.ui.theme.LocalDashTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.ui.systembar.DashAction
import com.dash.android.ui.systembar.DashElement
import com.dash.android.ui.systembar.ElementKind
import com.dash.android.ui.systembar.ElementScope
import com.dash.android.ui.systembar.ElementType

/**
 * The Settings Button — a mandatory, always-present element and the only route into DASH
 * settings. If it could be hidden the user could configure themselves into an unrecoverable
 * state, so it can never be removed.
 *
 * interface.md mandates a hard minimum touch target of 48dp that holds regardless of bar scale:
 * the visible glyph may shrink with the bar, but the interactive area never drops below 48dp.
 * That floor is enforced here with [Modifier.sizeIn], silently.
 */
object SettingsButtonElement : DashElement {
    override val type = ElementType.SETTINGS_BUTTON
    override val displayName = "Settings Button"
    override val kind = ElementKind.INTERACTIVE
    override val mandatory = true

    private const val MIN_TOUCH_TARGET_DP = 48

    @Composable
    override fun Content(scope: ElementScope) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = MIN_TOUCH_TARGET_DP.dp, minHeight = MIN_TOUCH_TARGET_DP.dp)
                .clickable { scope.onAction(DashAction.OpenSettings) },
            contentAlignment = Alignment.Center
        ) {
            // Gear glyph scales with element height; touch target floor is the 48dp sizeIn above.
            Text(
                text = "⚙",
                color = LocalDashTheme.current.barText,
                fontSize = (scope.heightDp.value * 0.55f).sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
