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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.MainActivity
import com.dash.android.density.DensityManager
import com.dash.android.density.DensityPreset
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.scale.DASH_SCALE_MAX
import com.dash.android.ui.scale.DASH_SCALE_MIN
import com.dash.android.ui.scale.DASH_SCALE_STEP
import com.dash.android.ui.systembar.BarPosition
import com.dash.android.ui.systembar.SystemBarConfig
import com.dash.android.ui.systembar.ZoneConfig
import java.util.UUID
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SPLASH_COLOURS = listOf(
    "Black" to 0xFF000000L,
    "DASH Navy" to 0xFF1A1A2EL,
    "Dark Slate" to 0xFF0D1117L
)

private val LABEL_COLOR = Color(0xFF666666)
private val INACTIVE = Color(0xFF2A2A2A)
private val ACTIVE = Color(0xFF2E7D32)

private data class ZoneDistribution(val label: String, val fractions: List<Float>)

private val ZONE_DISTRIBUTIONS_2 = listOf(
    ZoneDistribution("1:1", listOf(0.5f, 0.5f)),
    ZoneDistribution("1:2", listOf(1f / 3f, 2f / 3f)),
    ZoneDistribution("2:1", listOf(2f / 3f, 1f / 3f)),
)

private val ZONE_DISTRIBUTIONS_3 = listOf(
    ZoneDistribution("1:1:1", listOf(1f / 3f, 1f / 3f, 1f / 3f)),
    ZoneDistribution("1:2:1", listOf(0.25f, 0.5f, 0.25f)),
    ZoneDistribution("2:1:1", listOf(0.5f, 0.25f, 0.25f)),
    ZoneDistribution("1:1:2", listOf(0.25f, 0.25f, 0.5f)),
)

private fun distributionActive(config: SystemBarConfig, dist: ZoneDistribution): Boolean =
    config.zones.size == dist.fractions.size &&
        config.zones.zip(dist.fractions).all { (z, f) -> kotlin.math.abs(z.widthFraction - f) < 0.01f }

private fun SystemBarConfig.withZoneCount(count: Int): SystemBarConfig {
    val current = zones.size
    return when {
        count == current -> this
        count > current -> {
            val fractions = List(count) { 1f / count }
            val expanded = zones.mapIndexed { i, z -> z.copy(widthFraction = fractions[i]) } +
                (current until count).map { i ->
                    ZoneConfig(id = UUID.randomUUID().toString(), widthFraction = fractions[i])
                }
            copy(zones = expanded)
        }
        else -> {
            val kept = zones.take(count)
            val orphaned = zones.drop(count).flatMap { it.elements }
            val fractions = List(count) { 1f / count }
            copy(zones = kept.mapIndexed { i, z ->
                z.copy(
                    widthFraction = fractions[i],
                    elements = if (i == 0) z.elements + orphaned else z.elements
                )
            })
        }
    }
}

private fun SystemBarConfig.withDistribution(dist: ZoneDistribution): SystemBarConfig =
    copy(zones = zones.mapIndexed { i, z -> z.copy(widthFraction = dist.fractions[i]) })

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
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val densityAvailable by remember { mutableStateOf(runCatching { densityManager.checkCapability() }.getOrDefault(false)) }
    val currentDpi = remember { densityManager.readCurrentSystemDpi() }

    val selectedPreset by prefs.densityPreset.collectAsState(initial = null)
    val dashScale by prefs.dashScale.collectAsState(initial = 1.0f)
    val autoRotate by prefs.autoRotate.collectAsState(initial = true)
    val lockedOrientation by prefs.lockedOrientation.collectAsState(initial = "LANDSCAPE")
    val splashMode by prefs.splashMode.collectAsState(initial = "COLOUR")
    val splashColour by prefs.splashColour.collectAsState(initial = 0xFF000000L)
    val splashImageUri by prefs.splashImageUri.collectAsState(initial = "")
    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())

    var showResetConfirm by remember { mutableStateOf(false) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarPosition.entries.forEach { pos ->
                        SettingButton(
                            label = pos.name,
                            active = barConfig.position == pos,
                            onClick = { scope.launch { prefs.saveSystemBarConfig(barConfig.copy(position = pos)) } }
                        )
                    }
                }
                Text("SYSTEM BAR SIZE", color = LABEL_COLOR, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingButton(label = "−", onClick = {
                        val newBarHeight = (barConfig.heightDp - SystemBarConfig.HEIGHT_STEP_DP).coerceAtLeast(SystemBarConfig.MIN_HEIGHT_DP)
                        val elementCeiling = newBarHeight - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP
                        val newElementHeight = barConfig.elementHeightDp.coerceAtMost(elementCeiling)
                        scope.launch { prefs.saveSystemBarConfig(barConfig.copy(heightDp = newBarHeight, elementHeightDp = newElementHeight)) }
                    })
                    Text(
                        text = "${barConfig.heightDp}dp",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(88.dp),
                        textAlign = TextAlign.Center
                    )
                    SettingButton(label = "+", onClick = {
                        val next = (barConfig.heightDp + SystemBarConfig.HEIGHT_STEP_DP).coerceAtMost(SystemBarConfig.MAX_HEIGHT_DP)
                        scope.launch { prefs.saveSystemBarConfig(barConfig.copy(heightDp = next)) }
                    })
                }
                Text("ELEMENT SIZE", color = LABEL_COLOR, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                val elementAtMin = barConfig.elementHeightDp <= SystemBarConfig.MIN_ELEMENT_HEIGHT_DP
                val elementAtMax = barConfig.elementHeightDp >= barConfig.heightDp - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP
                val elementLabel = when {
                    elementAtMin -> "min"
                    elementAtMax -> "max"
                    else -> "${barConfig.elementHeightDp}dp"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingButton(label = "−", enabled = !elementAtMin, onClick = {
                        val next = (barConfig.elementHeightDp - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP).coerceAtLeast(SystemBarConfig.MIN_ELEMENT_HEIGHT_DP)
                        scope.launch { prefs.saveSystemBarConfig(barConfig.copy(elementHeightDp = next)) }
                    })
                    Text(
                        text = elementLabel,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(88.dp),
                        textAlign = TextAlign.Center
                    )
                    SettingButton(label = "+", enabled = !elementAtMax, onClick = {
                        val ceiling = barConfig.heightDp - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP
                        val next = (barConfig.elementHeightDp + SystemBarConfig.ELEMENT_HEIGHT_STEP_DP).coerceAtMost(ceiling)
                        scope.launch { prefs.saveSystemBarConfig(barConfig.copy(elementHeightDp = next)) }
                    })
                }
                Text("ZONES", color = LABEL_COLOR, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { count ->
                        SettingButton(
                            label = count.toString(),
                            active = barConfig.zones.size == count,
                            onClick = { scope.launch { prefs.saveSystemBarConfig(barConfig.withZoneCount(count)) } }
                        )
                    }
                }
                if (barConfig.zones.size >= 2) {
                    Text("DISTRIBUTION", color = LABEL_COLOR, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    val distributions = if (barConfig.zones.size == 2) ZONE_DISTRIBUTIONS_2 else ZONE_DISTRIBUTIONS_3
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        distributions.forEach { dist ->
                            SettingButton(
                                label = dist.label,
                                active = distributionActive(barConfig, dist),
                                onClick = { scope.launch { prefs.saveSystemBarConfig(barConfig.withDistribution(dist)) } }
                            )
                        }
                    }
                }
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D1A1A), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("RESET BAR LAYOUT", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
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

            // DASH Scale
            Section("DASH SCALE") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingButton(label = "−", onClick = {
                        val next = ((dashScale - DASH_SCALE_STEP).coerceAtLeast(DASH_SCALE_MIN) * 10).roundToInt() / 10f
                        scope.launch { prefs.saveDashScale(next) }
                    })
                    Text(
                        text = "${"%.1f".format(dashScale)}x",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.Center
                    )
                    SettingButton(label = "+", onClick = {
                        val next = ((dashScale + DASH_SCALE_STEP).coerceAtMost(DASH_SCALE_MAX) * 10).roundToInt() / 10f
                        scope.launch { prefs.saveDashScale(next) }
                    })
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

            // Exit
            TextButton(onClick = onExit) {
                Text("EXIT DASH", color = Color(0xFF555555), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Color(0xFF1A1A2E),
            title = { Text("Reset bar layout?", color = Color.White, fontFamily = FontFamily.Monospace) },
            text = {
                Text(
                    "The system bar returns to its default — bottom position, 56dp tall, elements at 36dp, with the alerts area and settings button. This cannot be undone.",
                    color = Color(0xFFAAAAAA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.resetSystemBar() }
                    showResetConfirm = false
                }) { Text("RESET", color = Color(0xFFE57373), fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("CANCEL", color = Color(0xFF888888), fontFamily = FontFamily.Monospace)
                }
            }
        )
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
