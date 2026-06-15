package com.dash.android.ui.systembar.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.ui.systembar.DashElement
import com.dash.android.ui.systembar.ElementKind
import com.dash.android.ui.systembar.ElementScope
import com.dash.android.ui.systembar.ElementType
import com.dash.android.ui.systembar.SizeVariant

/**
 * The Alerts Area — a mandatory, always-present element. Vehicle and module alerts must always be
 * visible; this is a safety consideration and the reason the element can never be removed.
 *
 * In 1.3.1 this is a placeholder visual only. It becomes functional in 1.4.x once the transport
 * layer is live and there are real alerts to display.
 */
object AlertsAreaElement : DashElement {
    override val type = ElementType.ALERTS_AREA
    override val displayName = "Alerts Area"
    override val kind = ElementKind.INFORMATIONAL
    override val mandatory = true
    override val sizeVariants = listOf(SizeVariant.SMALL, SizeVariant.MEDIUM, SizeVariant.LARGE)

    @Composable
    override fun Content(scope: ElementScope) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF26263F))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "ALERTS",
                color = Color(0xFF6E6E8A),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
    }
}
