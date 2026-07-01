package com.dash.android.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dash.android.MainActivity
import com.dash.android.density.DensityManager
import com.dash.android.prefs.DashPreferences
import com.dash.android.transport.DashController
import com.dash.android.transport.TransportManager
import com.dash.android.ui.debug.DiagnosticOverlay
import com.dash.android.ui.scale.DASH_SCALE_DEFAULT
import com.dash.android.ui.scale.LocalDashScale
import com.dash.android.ui.modules.ModuleManagementScreen
import com.dash.android.ui.monitor.SerialMonitorScreen
import com.dash.android.ui.settings.SettingsPanel
import com.dash.android.ui.theme.DashColors
import com.dash.android.ui.theme.LocalDashTheme
import com.dash.android.ui.splash.SplashScreen
import com.dash.android.ui.systembar.BarPosition
import com.dash.android.ui.systembar.DashAction
import com.dash.android.ui.systembar.EditRuler
import com.dash.android.ui.systembar.SystemBar
import com.dash.android.ui.systembar.SystemBarConfig
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

@Composable
fun MainScreen(activity: ComponentActivity, isColdBoot: Boolean) {
    val context = LocalContext.current
    val mainActivity = activity as MainActivity
    val densityManager = remember { DensityManager(context) }
    val prefs = remember { DashPreferences(context) }
    val scope = rememberCoroutineScope()

    // Transport layer (1.4.1). Owned here so the connection persists for the life of the running
    // app, independent of whether the Serial Monitor is open — the monitor only observes the wire.
    // The controller/brain (1.4.2) sits above it: it consumes inbound messages, routes them by type,
    // and holds the discovery desk the Module Management screen drives. Both live for the app's life.
    val transport = remember { TransportManager(context) }
    val controller = remember { DashController(transport) }
    DisposableEffect(transport, controller) {
        transport.start()
        controller.start()
        onDispose {
            controller.stop()
            transport.stop()
        }
    }

    var isDefaultLauncher by remember { mutableStateOf(mainActivity.isDefaultLauncher()) }
    var showSplash by remember { mutableStateOf(isColdBoot) }
    var showSettings by remember { mutableStateOf(false) }
    var showModules by remember { mutableStateOf(false) }
    var showSerialMonitor by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var editConfig by remember { mutableStateOf<SystemBarConfig?>(null) }
    var elementWidths by remember { mutableStateOf(mapOf<String, Int>()) }

    DisposableEffect(activity.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultLauncher = mainActivity.isDefaultLauncher()
                if (mainActivity.pendingWakeSplash) {
                    mainActivity.pendingWakeSplash = false
                    showSplash = true
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON && mainActivity.isDefaultLauncher()) {
                    mainActivity.pendingWakeSplash = true
                }
            }
        }
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { activity.unregisterReceiver(receiver) }
    }

    val dashScale by prefs.dashScale.collectAsState(initial = DASH_SCALE_DEFAULT)
    val selectedPreset by prefs.densityPreset.collectAsState(initial = null)
    val autoRotate by prefs.autoRotate.collectAsState(initial = true)
    val lockedOrientation by prefs.lockedOrientation.collectAsState(initial = "LANDSCAPE")
    val splashMode by prefs.splashMode.collectAsState(initial = "COLOUR")
    val splashColour by prefs.splashColour.collectAsState(initial = 0xFF000000L)
    val splashImageUri by prefs.splashImageUri.collectAsState(initial = "")
    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())

    LaunchedEffect(autoRotate, lockedOrientation) {
        activity.requestedOrientation = if (autoRotate) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else when (lockedOrientation) {
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    CompositionLocalProvider(
        LocalDashScale provides dashScale,
        LocalDashTheme provides DashColors.dark(),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // Not-default-launcher banner — anchored opposite the bar so the two never collide
            if (!isDefaultLauncher) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(if (barConfig.position == BarPosition.TOP) Alignment.BottomCenter else Alignment.TopCenter)
                        .background(Color(0xFF7B1FA2))
                        .clickable { mainActivity.openSetDefaultLauncher() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "DASH IS NOT YOUR DEFAULT LAUNCHER  —  TAP TO SET AS DEFAULT",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            DiagnosticOverlay(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = if (!isDefaultLauncher && barConfig.position != BarPosition.TOP) 48.dp else 0.dp, start = 16.dp),
                dashScale = dashScale,
                selectedPreset = selectedPreset
            )

            // Bar + ruler column. Anchored to the same edge as the bar; ruler slides in adjacent
            // to the bar on its inner side (below if top-docked, above if bottom-docked).
            // The Column grows away from the screen edge as the ruler expands — the bar stays
            // fixed against the edge and the ruler grows inward.
            val activeConfig = editConfig ?: barConfig
            Column(
                modifier = Modifier
                    .align(if (activeConfig.position == BarPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                if (activeConfig.position == BarPosition.TOP) {
                    SystemBar(
                        config = activeConfig,
                        onAction = { action ->
                            if (!editMode && action is DashAction.OpenSettings) showSettings = true
                        },
                        onElementMeasured = { id, width -> elementWidths = elementWidths + (id to width) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(
                        visible = editMode,
                        enter = fadeIn(tween(200)) + expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                        ),
                        exit = fadeOut(tween(150)) + shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = tween(180)
                        )
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            EditRuler(
                                config = activeConfig,
                                elementWidths = elementWidths,
                                barPosition = activeConfig.position,
                                onConfigChange = { editConfig = it }
                            )
                        }
                    }
                } else {
                    AnimatedVisibility(
                        visible = editMode,
                        enter = fadeIn(tween(200)) + expandVertically(
                            expandFrom = Alignment.Bottom,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                        ),
                        exit = fadeOut(tween(150)) + shrinkVertically(
                            shrinkTowards = Alignment.Bottom,
                            animationSpec = tween(180)
                        )
                    ) {
                        Column {
                            EditRuler(
                                config = activeConfig,
                                elementWidths = elementWidths,
                                barPosition = activeConfig.position,
                                onConfigChange = { editConfig = it }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    SystemBar(
                        config = activeConfig,
                        onAction = { action ->
                            if (!editMode && action is DashAction.OpenSettings) showSettings = true
                        },
                        onElementMeasured = { id, width -> elementWidths = elementWidths + (id to width) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Edit workspace — centred in the screen while edit mode is active.
            // SAVE commits the in-progress config to DataStore.
            // CANCEL discards all changes; barConfig (from DataStore) is the implicit snapshot.
            if (editMode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        "POSITION",
                        color = Color(0xFF666666),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BarPosition.entries.forEach { pos ->
                            val active = editConfig?.position == pos
                            Button(
                                onClick = { editConfig = editConfig?.copy(position = pos) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) Color(0xFF2E7D32) else Color(0xFF2A2A2A),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                            ) {
                                Text(pos.name, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    Text(
                        "ZONES",
                        color = Color(0xFF666666),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { count ->
                            val active = (editConfig?.zones?.size ?: 0) == count
                            Button(
                                onClick = { editConfig = editConfig?.withZoneCount(count) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) Color(0xFF2E7D32) else Color(0xFF2A2A2A),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                            ) {
                                Text(count.toString(), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    Text(
                        "BAR HEIGHT",
                        color = Color(0xFF666666),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Button(
                            onClick = {
                                editConfig = editConfig?.let { c ->
                                    val newH = (c.heightDp - SystemBarConfig.HEIGHT_STEP_DP).coerceAtLeast(SystemBarConfig.MIN_HEIGHT_DP)
                                    val newE = c.elementHeightDp.coerceAtMost(newH - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP)
                                    c.copy(heightDp = newH, elementHeightDp = newE)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) { Text("−", fontSize = 16.sp, fontFamily = FontFamily.Monospace) }
                        Text(
                            "${editConfig?.heightDp ?: 0}dp",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                editConfig = editConfig?.let { c ->
                                    c.copy(heightDp = (c.heightDp + SystemBarConfig.HEIGHT_STEP_DP).coerceAtMost(SystemBarConfig.MAX_HEIGHT_DP))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) { Text("+", fontSize = 16.sp, fontFamily = FontFamily.Monospace) }
                    }
                    Text(
                        "ELEMENT SIZE",
                        color = Color(0xFF666666),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        val elementAtMin = (editConfig?.elementHeightDp ?: 0) <= SystemBarConfig.MIN_ELEMENT_HEIGHT_DP
                        val elementAtMax = (editConfig?.elementHeightDp ?: 0) >= (editConfig?.heightDp ?: 0) - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP
                        Button(
                            onClick = {
                                editConfig = editConfig?.let { c ->
                                    c.copy(elementHeightDp = (c.elementHeightDp - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP).coerceAtLeast(SystemBarConfig.MIN_ELEMENT_HEIGHT_DP))
                                }
                            },
                            enabled = !elementAtMin,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) { Text("−", fontSize = 16.sp, fontFamily = FontFamily.Monospace) }
                        Text(
                            text = when {
                                elementAtMin -> "min"
                                elementAtMax -> "max"
                                else -> "${editConfig?.elementHeightDp ?: 0}dp"
                            },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                editConfig = editConfig?.let { c ->
                                    val ceiling = c.heightDp - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP
                                    c.copy(elementHeightDp = (c.elementHeightDp + SystemBarConfig.ELEMENT_HEIGHT_STEP_DP).coerceAtMost(ceiling))
                                }
                            },
                            enabled = !elementAtMax,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) { Text("+", fontSize = 16.sp, fontFamily = FontFamily.Monospace) }
                    }
                    Button(
                        onClick = { editConfig = editConfig?.let { SystemBarConfig.default().copy(position = it.position) } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5D1A1A),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Text("RESET BAR LAYOUT", fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                editConfig = null
                                editMode = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF424242),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Text("CANCEL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                        }
                        Button(
                            onClick = {
                                editConfig?.let { scope.launch { prefs.saveSystemBarConfig(it) } }
                                editConfig = null
                                editMode = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Text("SAVE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            // Settings panel overlay
            if (showSettings) {
                SettingsPanel(
                    activity = mainActivity,
                    prefs = prefs,
                    densityManager = densityManager,
                    onPreviewSplash = { showSplash = true },
                    onEnterEditMode = {
                        editConfig = barConfig
                        editMode = true
                        showSettings = false
                    },
                    onOpenModules = {
                        showModules = true
                        showSettings = false
                    },
                    onOpenSerialMonitor = {
                        showSerialMonitor = true
                        showSettings = false
                    },
                    onExit = {
                        densityManager.resetToDefault()
                        activity.finish()
                        exitProcess(0)
                    },
                    onDismiss = { showSettings = false }
                )
            }

            // Module Management overlay — full-screen, reached from settings (mirrors the Serial
            // Monitor route). The DISCOVER button lives here; closing returns to the main screen.
            if (showModules) {
                ModuleManagementScreen(
                    discovery = controller.discovery,
                    onDismiss = { showModules = false }
                )
            }

            // Serial Monitor overlay — full-screen dev instrument, reached from settings
            // (mirrors the edit-workspace route). Closing returns to the main screen.
            if (showSerialMonitor) {
                SerialMonitorScreen(
                    transport = transport,
                    onDismiss = { showSerialMonitor = false }
                )
            }

            // Splash overlay — sits above everything, including the settings panel
            if (showSplash) {
                SplashScreen(
                    mode = splashMode,
                    colour = splashColour,
                    imageUri = splashImageUri,
                    onDismiss = { showSplash = false }
                )
            }
        }
    }
}
