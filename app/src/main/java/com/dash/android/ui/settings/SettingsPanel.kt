package com.dash.android.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dash.android.ui.theme.LocalDashTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.MainActivity
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.systembar.SystemBarConfig
import kotlinx.coroutines.launch

private val LABEL_COLOR = Color(0xFF666666)
private val INACTIVE = Color(0xFF2A2A2A)
private val ACTIVE = Color(0xFF2E7D32)


/**
 * The DASH settings panel — opened from the system bar's settings button, which is the only route
 * into settings (interface.md). A full-screen scrollable overlay housing every configuration
 * control. Built incrementally; the full settings tree from interface.md is filled out over later
 * versions.
 */
@Composable
fun SettingsPanel(
    activity: MainActivity,
    prefs: DashPreferences,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val autoRotate by prefs.autoRotate.collectAsState(initial = true)
    val lockedOrientation by prefs.lockedOrientation.collectAsState(initial = "LANDSCAPE")
    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A12))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DASH SETTINGS", color = Color.White, fontSize = 16.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 3.sp)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("CLOSE ✕", fontSize = 12.sp, fontFamily = LocalDashTheme.current.font) }
            }

            // System Bar rehomed to Layout › System Bar (roadmap 1.5.7) — position, the EDIT BAR
            // LAYOUT entry point, and Reset all live there now. Removed from the legacy panel.

            // Transitions rehomed to Appearance › Transitions (roadmap 1.5.5) — every DASH transition
            // now breaks out to its own control there, under a master pace. Removed from the legacy panel.

            // App Density rehomed to Appearance › Size & Scale (roadmap 1.5.3) — the presets, the
            // capability-gated privileged path and the Android deep-link all live there now. The
            // "Open Display Size Settings →" button left behind here was a duplicate of that deep-link
            // and has been removed (roadmap 1.5.12).

            // Rotation
            Section("ROTATION") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AUTO", "PORTRAIT", "LANDSCAPE").forEach { mode ->
                        val active = if (mode == "AUTO") autoRotate else !autoRotate && lockedOrientation == mode
                        SettingButton(
                            label = mode,
                            active = active,
                            onClick = {
                                scope.launch {
                                    if (mode == "AUTO") prefs.saveAutoRotate(true)
                                    else { prefs.saveAutoRotate(false); prefs.saveLockedOrientation(mode) }
                                }
                            }
                        )
                    }
                }
            }

            // Splash Screen rehomed to Appearance › Splash Screen (roadmap 1.5.6) — type, theme-token
            // background, image (still or animated), dwell and a live preview all live there now.
            // Removed from the legacy panel.

            // Launcher
            Section("LAUNCHER") {
                Button(
                    onClick = { activity.openChangeLauncher() },
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) { Text("CHANGE LAUNCHER →", fontSize = 13.sp, fontFamily = LocalDashTheme.current.font) }
            }

            // Module Management rehomed to Modules › Module Management (roadmap 1.5.8) — the full 1.4.x
            // instrument now lives in the settings shell. Removed from the legacy panel.

            // Serial Monitor and Signal Monitor rehomed to Modules › Serial Monitor / Signal Monitor
            // (roadmap 1.5.12) — both rebuilt on the settings surface. Removed from the legacy panel.

            // Exit
            TextButton(onClick = onExit) {
                Text("EXIT DASH", color = Color(0xFF555555), fontSize = 11.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 2.sp)
            }
        }
    }

}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = LABEL_COLOR, fontSize = 11.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 2.sp)
        content()
    }
}

@Composable
private fun SettingButton(label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) ACTIVE else INACTIVE,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF1A1A1A),
            disabledContentColor = Color(0xFF444444)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(label, fontSize = 13.sp, fontFamily = LocalDashTheme.current.font) }
}
