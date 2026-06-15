package com.dash.android.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.dash.android.ui.debug.DiagnosticOverlay
import com.dash.android.ui.scale.DASH_SCALE_DEFAULT
import com.dash.android.ui.scale.LocalDashScale
import com.dash.android.ui.settings.SettingsPanel
import com.dash.android.ui.splash.SplashScreen
import com.dash.android.ui.systembar.BarPosition
import com.dash.android.ui.systembar.DashAction
import com.dash.android.ui.systembar.SystemBar
import com.dash.android.ui.systembar.SystemBarConfig
import kotlin.system.exitProcess

@Composable
fun MainScreen(activity: ComponentActivity, isColdBoot: Boolean) {
    val context = LocalContext.current
    val mainActivity = activity as MainActivity
    val densityManager = remember { DensityManager(context) }
    val prefs = remember { DashPreferences(context) }

    var isDefaultLauncher by remember { mutableStateOf(mainActivity.isDefaultLauncher()) }
    var showSplash by remember { mutableStateOf(isColdBoot) }
    var showSettings by remember { mutableStateOf(false) }

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

    CompositionLocalProvider(LocalDashScale provides dashScale) {
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

            // The system bar — always present, top or bottom per config
            SystemBar(
                config = barConfig,
                onAction = { action -> if (action is DashAction.OpenSettings) showSettings = true },
                modifier = Modifier.align(
                    if (barConfig.position == BarPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter
                )
            )

            // Settings panel overlay
            if (showSettings) {
                SettingsPanel(
                    activity = mainActivity,
                    prefs = prefs,
                    densityManager = densityManager,
                    onPreviewSplash = { showSplash = true },
                    onExit = {
                        densityManager.resetToDefault()
                        activity.finish()
                        exitProcess(0)
                    },
                    onDismiss = { showSettings = false }
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
