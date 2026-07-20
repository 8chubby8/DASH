package com.dash.android.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dash.android.ui.theme.LocalDashTheme
import androidx.compose.ui.unit.sp
import com.dash.android.density.DensityPreset

@Composable
fun DiagnosticOverlay(
    modifier: Modifier = Modifier,
    dashScale: Float,
    selectedPreset: DensityPreset?
) {
    val dm = LocalContext.current.resources.displayMetrics

    Column(modifier = modifier) {
        listOf(
            "${dm.widthPixels}×${dm.heightPixels}px",
            "native ${dm.densityDpi}dpi",
            "system → ${selectedPreset?.let { "${it.label} (${it.dpi}dpi)" } ?: "default"}",
            "scale ${"%.1f".format(dashScale)}x"
        ).forEach { line ->
            Text(
                text = line,
                color = Color(0xFF555555),
                fontSize = 10.sp,
                fontFamily = LocalDashTheme.current.font
            )
        }
    }
}
