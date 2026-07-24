package com.dash.android.ui.screen

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.dash.android.ui.motion.DashTransitions
import com.dash.android.ui.motion.LocalDashTransitions
import com.dash.android.ui.motion.TransitionId
import com.dash.android.ui.weather.LocalWeatherSnapshot
import com.dash.android.weather.WeatherProvider
import com.dash.android.weather.WeatherSnapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.dash.android.ui.scale.DASH_SCALE_DEFAULT
import com.dash.android.ui.scale.DASH_TEXT_SCALE_DEFAULT
import com.dash.android.ui.scale.LocalDashScale
import com.dash.android.ui.modules.LocalModuleDesk
import com.dash.android.ui.modules.ModuleDesk
import com.dash.android.ui.monitor.SerialMonitorScreen
import com.dash.android.ui.signal.SignalMonitorScreen
import com.dash.android.ui.modulepanel.MODULE_PANEL_EXPANDED_HEIGHT
import com.dash.android.ui.modulepanel.MODULE_PANEL_MINIMISED_HEIGHT
import com.dash.android.ui.modulepanel.ModulePanelPlaceholder
import com.dash.android.ui.settings.SettingsPanel
import com.dash.android.ui.settings.SettingsShell
import com.dash.android.ui.theme.DashTheme
import com.dash.android.ui.theme.LocalDashTheme
import com.dash.android.ui.theme.SPLASH_BACKGROUND_COLOUR_DEFAULT
import com.dash.android.ui.splash.LocalSplashPreview
import com.dash.android.ui.splash.SPLASH_CROP_DEFAULT
import com.dash.android.ui.splash.SPLASH_DWELL_DEFAULT_MS
import com.dash.android.ui.splash.SplashScreen
import com.dash.android.ui.systembar.LocalEnterBarEdit
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
    val controller = remember { DashController(transport, context) }
    DisposableEffect(transport, controller) {
        transport.start()
        controller.start()
        onDispose {
            controller.stop()
            transport.stop()
        }
    }

    // Bluetooth Classic (SPP) transport permission (1.4.12). On API 31+ BLUETOOTH_CONNECT is a runtime
    // grant; we ask once on start. This only *raises the dialog* — the transport does its own
    // capability detection every sweep, so if the grant is given the next sweep connects, and if it is
    // denied the transport simply reports unavailable and DASH carries on (CLAUDE.md graceful
    // degradation). Below API 31 the legacy BLUETOOTH permission is auto-granted, so there is nothing
    // to ask for.
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by the transport's next sweep — nothing to do here */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    // Weather for the settings landing scene, cached at the root so the panel opens on real weather
    // instead of the clock-only floor. Warmed once at start and refreshed on each open — always off
    // the main thread, so a slow network never blocks the panel opening (the fetch does up to two IP
    // lookups and a forecast call, each on a 5s socket timeout; gating the open on that could freeze
    // the button for seconds). See openSettings().
    val weatherProvider = remember { WeatherProvider(context) }
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
    LaunchedEffect(Unit) { weather = weatherProvider.current() }

    var isDefaultLauncher by remember { mutableStateOf(mainActivity.isDefaultLauncher()) }
    var showSplash by remember { mutableStateOf(isColdBoot) }
    var showSettings by remember { mutableStateOf(false) }
    // Where to reopen settings after a focused task (bar edit mode) takes over the screen — so Save/
    // Cancel returns to the tab the user left, not the home screen.
    var settingsReturnTarget by remember { mutableStateOf<String?>(null) }
    var showLegacySettings by remember { mutableStateOf(false) }
    var modulePanelExpanded by remember { mutableStateOf(false) }
    var showSerialMonitor by remember { mutableStateOf(false) }
    var showSignalMonitor by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var editConfig by remember { mutableStateOf<SystemBarConfig?>(null) }
    var elementWidths by remember { mutableStateOf(mapOf<String, Int>()) }

    // Opening settings refreshes the landing weather in the background (never gating the open); closing
    // is a plain toggle. Both settings-button sites route through this so the behaviour is identical.
    val toggleSettings: () -> Unit = {
        if (showSettings) {
            showSettings = false
        } else {
            showSettings = true
            scope.launch { weather = weatherProvider.current() }
        }
    }

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
    val dashTextScale by prefs.dashTextScale.collectAsState(initial = DASH_TEXT_SCALE_DEFAULT)
    val transitionMap by prefs.transitions.collectAsState(initial = emptyMap())
    val transitions = remember(transitionMap) { DashTransitions(transitionMap) }
    val selectedPreset by prefs.densityPreset.collectAsState(initial = null)
    val autoRotate by prefs.autoRotate.collectAsState(initial = true)
    val lockedOrientation by prefs.lockedOrientation.collectAsState(initial = "LANDSCAPE")
    val splashMode by prefs.splashMode.collectAsState(initial = "COLOUR")
    val splashColour by prefs.splashBackgroundColour.collectAsState(initial = SPLASH_BACKGROUND_COLOUR_DEFAULT)
    val splashImageUri by prefs.splashImageUri.collectAsState(initial = "")
    val splashAnimationUri by prefs.splashAnimationUri.collectAsState(initial = "")
    val splashCropPortrait by prefs.splashCropPortrait.collectAsState(initial = SPLASH_CROP_DEFAULT)
    val splashCropLandscape by prefs.splashCropLandscape.collectAsState(initial = SPLASH_CROP_DEFAULT)
    val splashDwell by prefs.splashDwellMillis.collectAsState(initial = SPLASH_DWELL_DEFAULT_MS)
    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())

    LaunchedEffect(autoRotate, lockedOrientation) {
        activity.requestedOrientation = if (autoRotate) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else when (lockedOrientation) {
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    // DASH text size — override the composition's fontScale with DASH's own value so every sp in
    // DASH chrome follows the DASH text-size control and ignores Android's font setting entirely
    // (Android's font size is left for the viewport apps). The device density (dp mapping) is kept
    // as-is; only text scaling is taken over here.
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, dashTextScale),
        LocalDashScale provides dashScale,
        LocalDashTransitions provides transitions,
        LocalWeatherSnapshot provides weather,
        LocalDashTheme provides DashTheme.default(),
        LocalSplashPreview provides { showSplash = true },
        LocalEnterBarEdit provides {
            editConfig = barConfig
            editMode = true
            showSettings = false
            settingsReturnTarget = "layout.systembar"
        },
        // The live module desk for the Modules › Module Management tab (1.5.8). Its four managers are
        // stateful and live on the controller for the app's life, so the tab reaches them here rather
        // than rebuilding them from context the way the stateless-prefs tabs do.
        LocalModuleDesk provides ModuleDesk(
            discovery = controller.discovery,
            install = controller.install,
            database = controller.database,
            reconciliation = controller.reconciliation,
            onUpdate = controller::updateModule,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // Not-default-launcher banner — anchored opposite the bar so the two never collide.
            // Hidden in edit mode: the workspace is a focused task and nothing else should compete.
            if (!isDefaultLauncher && !editMode) {
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
                        fontFamily = LocalDashTheme.current.font,
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

            // Settings shell (roadmap 1.5.2). Drawn *before* the bar so the bar stays on top and
            // reachable while the panel is open. It rolls out from under the bar like a blind: an
            // explicitly animated height from 0 to the region's measured full height, clipped so the
            // content is revealed rather than stretched. This is duration-accurate on purpose —
            // AnimatedVisibility's expandVertically does not animate when the container is forced to
            // fillMaxSize, which is why the transition-speed setting had no visible effect.
            // The bounds conform to the surrounding chrome — below the bar, above the module panel —
            // so it covers a minimised module panel but yields to an expanded one.
            val moduleHeight = if (modulePanelExpanded) MODULE_PANEL_EXPANDED_HEIGHT else MODULE_PANEL_MINIMISED_HEIGHT
            val barIsTop = barConfig.position == BarPosition.TOP
            val settingsTopInset = if (barIsTop) barConfig.heightDp.dp else 0.dp
            val settingsBottomInset = (if (!barIsTop) barConfig.heightDp.dp else 0.dp) + moduleHeight
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxSize()
                    .padding(top = settingsTopInset, bottom = settingsBottomInset)
            ) {
                val fullHeight = maxHeight
                // Open and close are two separate transitions with their own durations — the blind
                // can roll out slow and snap back fast, or any pairing the user picks. The direction
                // is read from the target: expanding uses OPEN, collapsing uses CLOSE.
                val revealed by animateDpAsState(
                    targetValue = if (showSettings) fullHeight else 0.dp,
                    animationSpec = tween(
                        transitions.millis(
                            if (showSettings) TransitionId.SETTINGS_PANEL_OPEN else TransitionId.SETTINGS_PANEL_CLOSE
                        )
                    ),
                    label = "settingsReveal"
                )
                if (revealed > 0.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(revealed)
                            .align(if (barIsTop) Alignment.TopStart else Alignment.BottomStart)
                            .clipToBounds(),
                        // Anchor the full-height content to the bar's edge so the blind uncovers it
                        // from that edge — content stays put, the clip window grows over it.
                        contentAlignment = if (barIsTop) Alignment.TopStart else Alignment.BottomStart
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(fullHeight)) {
                            SettingsShell(
                                initialSubId = settingsReturnTarget,
                                onClose = { showSettings = false; settingsReturnTarget = null },
                                onOpenLegacy = { showLegacySettings = true }
                            )
                        }
                    }

                    // A thin rule at the bar boundary sets the panel apart from the system bar.
                    // The panel region is inset by the bar height, so this rides the bar's inner
                    // edge — growing the bar with the size slider visibly moves it. Most of the
                    // width, centred, in the secondary surface colour; fades in with the blind.
                    Box(
                        modifier = Modifier
                            .align(if (barIsTop) Alignment.TopCenter else Alignment.BottomCenter)
                            .fillMaxWidth(0.86f)
                            .height(1.dp)
                            // Nearly imperceptible — just enough to catch the boundary. The reveal
                            // progress fades it in; the 0.16 ceiling keeps it a whisper at full open.
                            .alpha((revealed / fullHeight).coerceIn(0f, 1f) * 0.16f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(LocalDashTheme.current.backgroundColourSecondary)
                    )
                }
            }

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
                            // The settings button toggles the panel: open when closed, close when open.
                            if (!editMode && action is DashAction.OpenSettings) toggleSettings()
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
                            // The settings button toggles the panel: open when closed, close when open.
                            if (!editMode && action is DashAction.OpenSettings) toggleSettings()
                        },
                        onElementMeasured = { id, width -> elementWidths = elementWidths + (id to width) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Edit workspace — with the bar's configuration now living in settings (Position and Zones
            // in Layout › System Bar; Bar Height and Element Size in Appearance › Size & Scale), edit
            // mode is reduced to its one irreducible job: the ruler beside the bar, plus Save / Cancel.
            // SAVE commits the ruler's in-progress config to DataStore; CANCEL discards it (barConfig,
            // from DataStore, is the implicit snapshot). Colours read from the theme tokens.
            if (editMode) {
                val editTheme = LocalDashTheme.current
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Button(
                        onClick = {
                            editConfig = null
                            editMode = false
                            if (settingsReturnTarget != null) showSettings = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = editTheme.accentColourSecondary,
                            contentColor = editTheme.textColourSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("CANCEL", fontSize = 11.sp, fontFamily = editTheme.font, letterSpacing = 1.sp)
                    }
                    Button(
                        onClick = {
                            editConfig?.let { scope.launch { prefs.saveSystemBarConfig(it) } }
                            editConfig = null
                            editMode = false
                            if (settingsReturnTarget != null) showSettings = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = editTheme.backgroundColourPrimary,
                            contentColor = editTheme.textColourPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("SAVE", fontSize = 11.sp, fontFamily = editTheme.font, letterSpacing = 1.sp)
                    }
                }
            }

            // Module panel placeholder (throwaway scaffold, roadmap 1.5.2 → deleted at 1.6.x).
            // Present so the settings shell has a real surface to conform to — but hidden in edit
            // mode, which is a focused workspace with nothing but the bar, its ruler and Save/Cancel.
            if (!editMode) {
                ModulePanelPlaceholder(
                    expanded = modulePanelExpanded,
                    onToggle = { modulePanelExpanded = !modulePanelExpanded },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (barIsTop) 0.dp else barConfig.heightDp.dp)
                )
            }

            // Legacy flat settings — the pre-1.5.2 panel, reached from the shell's temporary
            // LEGACY SETTINGS button so nothing built in 1.1.x–1.4.x is unreachable while it waits
            // to be rehomed into the shell. Removed at 1.5.12.
            if (showLegacySettings) {
                SettingsPanel(
                    activity = mainActivity,
                    prefs = prefs,
                    densityManager = densityManager,
                    onOpenSerialMonitor = {
                        showSerialMonitor = true
                        showLegacySettings = false
                        showSettings = false
                    },
                    onOpenSignalMonitor = {
                        showSignalMonitor = true
                        showLegacySettings = false
                        showSettings = false
                    },
                    onExit = {
                        densityManager.resetToDefault()
                        activity.finish()
                        exitProcess(0)
                    },
                    onDismiss = { showLegacySettings = false }
                )
            }

            // Module Management is now a settings tab (Modules › Module Management, 1.5.8) rendered
            // inside the shell — no standalone route. It reaches the controller's managers through
            // LocalModuleDesk, provided above.

            // Serial Monitor overlay — full-screen dev instrument, reached from settings
            // (mirrors the edit-workspace route). Closing returns to the main screen.
            if (showSerialMonitor) {
                SerialMonitorScreen(
                    transport = transport,
                    onDismiss = { showSerialMonitor = false }
                )
            }

            // Signal Monitor overlay — the live board of system messages + their state in the
            // sourceless core (roadmap 1.4.10). Dev instrument, same shelf as the Serial Monitor.
            if (showSignalMonitor) {
                SignalMonitorScreen(
                    controller = controller,
                    onDismiss = { showSignalMonitor = false }
                )
            }

            // Splash overlay — sits above everything, including the settings panel
            if (showSplash) {
                SplashScreen(
                    mode = splashMode,
                    backgroundColour = splashColour,
                    imageUri = splashImageUri,
                    animationUri = splashAnimationUri,
                    imageCropPortrait = splashCropPortrait,
                    imageCropLandscape = splashCropLandscape,
                    dwellMillis = splashDwell,
                    fadeInMillis = transitions.millis(TransitionId.SPLASH_FADE_IN),
                    fadeOutMillis = transitions.millis(TransitionId.SPLASH_FADE_OUT),
                    onDismiss = { showSplash = false }
                )
            }
        }
    }
}
