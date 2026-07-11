package com.dash.android.ui.settings

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.MainActivity
import com.dash.android.density.DensityManager
import com.dash.android.density.DensityPreset
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.systembar.SystemBarConfig
import kotlinx.coroutines.launch

private val SPLASH_COLOURS = listOf(
    "Black" to 0xFF000000L,
    "DASH Navy" to 0xFF1A1A2EL,
    "Dark Slate" to 0xFF0D1117L
)

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
    densityManager: DensityManager,
    onPreviewSplash: () -> Unit,
    onEnterEditMode: () -> Unit,
    onOpenModules: () -> Unit,
    onOpenSerialMonitor: () -> Unit,
    onOpenSignalMonitor: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val densityAvailable by remember { mutableStateOf(runCatching { densityManager.checkCapability() }.getOrDefault(false)) }
    val currentDpi = remember { densityManager.readCurrentSystemDpi() }

    val selectedPreset by prefs.densityPreset.collectAsState(initial = null)
    val autoRotate by prefs.autoRotate.collectAsState(initial = true)
    val lockedOrientation by prefs.lockedOrientation.collectAsState(initial = "LANDSCAPE")
    val splashMode by prefs.splashMode.collectAsState(initial = "COLOUR")
    val splashColour by prefs.splashColour.collectAsState(initial = 0xFF000000L)
    val splashImageUri by prefs.splashImageUri.collectAsState(initial = "")
    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                prefs.saveSplashImageUri(it.toString())
                prefs.saveSplashMode("IMAGE")
            }
        }
    }

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
                Text("DASH SETTINGS", color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("CLOSE ✕", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }

            // System Bar
            Section("SYSTEM BAR") {
                Button(
                    onClick = { onEnterEditMode() },
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("EDIT BAR LAYOUT", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }

            // App Density
            Section("APP DENSITY") {
                if (densityAvailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DensityPreset.entries.forEach { preset ->
                            SettingButton(
                                label = preset.label,
                                active = selectedPreset == preset,
                                onClick = {
                                    densityManager.setDensity(preset)
                                    scope.launch { prefs.saveDensityPreset(preset) }
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Current: ${densityManager.formatDpi(currentDpi)}",
                        color = Color(0xFF888888),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Button(
                        onClick = {
                            val direct = runCatching {
                                context.startActivity(Intent().apply {
                                    component = ComponentName(
                                        "com.android.settings",
                                        "com.android.settings.Settings\$TextReadingPreferenceActivity"
                                    )
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                            if (direct.isFailure) context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) { Text("Open Display Size Settings →", fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
                }
            }

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

            // Splash Screen
            Section("SPLASH SCREEN") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("COLOUR", "IMAGE").forEach { mode ->
                        SettingButton(
                            label = mode,
                            active = splashMode == mode,
                            onClick = { scope.launch { prefs.saveSplashMode(mode) } }
                        )
                    }
                }
                if (splashMode == "COLOUR") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SPLASH_COLOURS.forEach { (label, argb) ->
                            SettingButton(
                                label = label,
                                active = splashColour == argb,
                                onClick = { scope.launch { prefs.saveSplashColour(argb) } }
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { imagePicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (splashImageUri.isNotEmpty()) "Change Image →" else "Choose Image →",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Button(
                    onClick = onPreviewSplash,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("PREVIEW SPLASH", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }

            // Launcher
            Section("LAUNCHER") {
                Button(
                    onClick = { activity.openChangeLauncher() },
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) { Text("CHANGE LAUNCHER →", fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
            }

            // Modules — discovery and (later) install/enable management. Becomes the Modules tab
            // when the full settings tree is built in 1.5.x.
            Section("MODULES") {
                Button(
                    onClick = onOpenModules,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) { Text("MANAGE MODULES →", fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
            }

            // Serial Monitor — persistent transport diagnostic (1.4.x). Migrates into the
            // Transports/Developer tab when the full settings panel is built in 1.5.x.
            Section("SERIAL MONITOR") {
                Button(
                    onClick = onOpenSerialMonitor,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) { Text("OPEN SERIAL MONITOR →", fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
            }

            // Signal Monitor — live board of system messages + state in the sourceless core (1.4.10).
            // Migrates into the Developer tab (Signal Monitor) when the full settings panel is built in 1.5.x.
            Section("SIGNAL MONITOR") {
                Button(
                    onClick = onOpenSignalMonitor,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) { Text("OPEN SIGNAL MONITOR →", fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
            }

            // Exit
            TextButton(onClick = onExit) {
                Text("EXIT DASH", color = Color(0xFF555555), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
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
        Text(title, color = LABEL_COLOR, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
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
    ) { Text(label, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
}
