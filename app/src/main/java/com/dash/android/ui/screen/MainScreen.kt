package com.dash.android.ui.screen

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dash.android.density.DensityManager
import com.dash.android.density.DensityPreset
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.debug.DiagnosticOverlay
import com.dash.android.ui.scale.DASH_SCALE_DEFAULT
import com.dash.android.ui.scale.DASH_SCALE_MAX
import com.dash.android.ui.scale.DASH_SCALE_MIN
import com.dash.android.ui.scale.DASH_SCALE_STEP
import com.dash.android.ui.scale.LocalDashScale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.system.exitProcess

private val BAR_BASE_HEIGHT = 56.dp
private val BAR_COLOR = Color(0xFF1A1A2E)

@Composable
fun MainScreen(activity: ComponentActivity) {
    val context = LocalContext.current
    val densityManager = remember { DensityManager(context) }
    val prefs = remember { DashPreferences(context) }
    val scope = rememberCoroutineScope()

    var densityAvailable by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { densityAvailable = densityManager.checkCapability() }

    var currentDpi by remember { mutableStateOf(densityManager.readCurrentSystemDpi()) }

    DisposableEffect(activity.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentDpi = densityManager.readCurrentSystemDpi()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    val selectedPreset by prefs.densityPreset.collectAsState(initial = null)
    val dashScale by prefs.dashScale.collectAsState(initial = DASH_SCALE_DEFAULT)
    val autoRotate by prefs.autoRotate.collectAsState(initial = true)
    val lockedOrientation by prefs.lockedOrientation.collectAsState(initial = "LANDSCAPE")

    LaunchedEffect(autoRotate, lockedOrientation) {
        activity.requestedOrientation = if (autoRotate) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else when (lockedOrientation) {
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    val barHeight = BAR_BASE_HEIGHT * dashScale

    CompositionLocalProvider(LocalDashScale provides dashScale) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            DiagnosticOverlay(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                dashScale = dashScale,
                selectedPreset = selectedPreset
            )

            Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Density
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "APP DENSITY",
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    if (densityAvailable == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DensityPreset.entries.forEach { preset ->
                                Button(
                                    onClick = {
                                        densityManager.setDensity(preset)
                                        scope.launch { prefs.saveDensityPreset(preset) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedPreset == preset) Color(0xFF2E7D32) else Color(0xFF2A2A2A),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(preset.label, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    } else if (densityAvailable == false) {
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
                                if (direct.isFailure) {
                                    context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A2A2A),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text("Open Display Size Settings →", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Rotation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ROTATION",
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("AUTO", "PORTRAIT", "LANDSCAPE").forEach { mode ->
                            val active = when (mode) {
                                "AUTO" -> autoRotate
                                else -> !autoRotate && lockedOrientation == mode
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (mode == "AUTO") {
                                            prefs.saveAutoRotate(true)
                                        } else {
                                            prefs.saveAutoRotate(false)
                                            prefs.saveLockedOrientation(mode)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) Color(0xFF2E7D32) else Color(0xFF2A2A2A),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(mode, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // DASH Scale
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DASH SCALE",
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Button(
                            onClick = {
                                val next = ((dashScale - DASH_SCALE_STEP).coerceAtLeast(DASH_SCALE_MIN) * 10).roundToInt() / 10f
                                scope.launch { prefs.saveDashScale(next) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A2A2A),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text("−", fontSize = 20.sp)
                        }
                        Text(
                            text = "${"%.1f".format(dashScale)}x",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(72.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = {
                                val next = ((dashScale + DASH_SCALE_STEP).coerceAtMost(DASH_SCALE_MAX) * 10).roundToInt() / 10f
                                scope.launch { prefs.saveDashScale(next) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A2A2A),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text("+", fontSize = 20.sp)
                        }
                    }
                }
            }

            // Exit
            TextButton(
                onClick = {
                    densityManager.resetToDefault()
                    activity.finish()
                    exitProcess(0)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = barHeight + 8.dp, end = 16.dp)
            ) {
                Text("EXIT", color = Color(0xFF555555), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            }

            // Placeholder system bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .align(Alignment.BottomCenter)
                    .background(BAR_COLOR)
            )
        }
    }
}
