package com.dash.android.ui.screen

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dash.android.DashApplication
import com.dash.android.MainActivity
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.common.DashButton
import com.dash.android.ui.motion.DashTransitions
import com.dash.android.ui.motion.LocalDashTransitions
import com.dash.android.ui.motion.TransitionId
import com.dash.android.ui.weather.LocalWeatherSnapshot
import com.dash.android.weather.WeatherProvider
import com.dash.android.weather.WeatherSnapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.dash.android.ui.scale.DASH_TEXT_SCALE_DEFAULT
import com.dash.android.ui.modules.LocalModuleDesk
import com.dash.android.ui.modules.ModuleDesk
import com.dash.android.ui.transports.LocalTransportDesk
import com.dash.android.ui.transports.TransportDesk
import com.dash.android.ui.rotation.DashOrientation
import com.dash.android.ui.modulepanel.ModulePanel
import com.dash.android.ui.modulepanel.ModulePanelConfig
import com.dash.android.ui.modulepanel.ModulePanelSpec
import com.dash.android.ui.modulepanel.PanelEdge
import com.dash.android.ui.modulepanel.effectiveEdge
import com.dash.android.ui.modulepanel.rememberActivePanel
import com.dash.android.ui.modulepanel.slotFor
import com.dash.android.ui.modulepanel.valuesFor
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

@Composable
fun MainScreen(activity: ComponentActivity, isColdBoot: Boolean) {
    val context = LocalContext.current
    val mainActivity = activity as MainActivity
    val prefs = remember { DashPreferences(context) }
    val scope = rememberCoroutineScope()

    // Transport layer (1.4.1) + the controller/brain (1.4.2) above it. *Reached*, never created here
    // (roadmap 1.5.11): both are owned by [DashApplication] and live for the life of the process, so
    // an activity recreation reflows the UI without touching the bus. They used to be `remember`ed
    // right here, which tied DASH's whole module conversation to a composable — see DashApplication
    // for what that cost and how it was caught. There is deliberately no DisposableEffect: this screen
    // does not start the stack and must never stop it.
    val dashApp = remember(context) { context.applicationContext as DashApplication }
    val transport = dashApp.transport
    val controller = dashApp.controller

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

    var showSplash by remember { mutableStateOf(isColdBoot) }
    var showSettings by remember { mutableStateOf(false) }
    // Where to reopen settings after a focused task (bar edit mode) takes over the screen — so Save/
    // Cancel returns to the tab the user left, not the home screen.
    var settingsReturnTarget by remember { mutableStateOf<String?>(null) }
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

    val dashTextScale by prefs.dashTextScale.collectAsState(initial = DASH_TEXT_SCALE_DEFAULT)
    val transitionMap by prefs.transitions.collectAsState(initial = emptyMap())
    val transitions = remember(transitionMap) { DashTransitions(transitionMap) }
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
    val modulePanelConfig by prefs.modulePanelConfig.collectAsState(initial = ModulePanelConfig.default())

    LaunchedEffect(autoRotate, lockedOrientation) {
        activity.requestedOrientation = if (autoRotate) {
            // FULL_SENSOR, not SENSOR (roadmap 1.5.15). Plain SENSOR deliberately refuses to rotate
            // into reverse portrait on most devices — so with Layout › Rotation offering all four
            // fixed orientations, SENSOR would leave Auto unable to reach one of them. A tab that
            // will hold an orientation on demand but never rotate into it on its own contradicts
            // itself; FULL_SENSOR gives Auto the same four positions the tab offers.
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            DashOrientation.from(lockedOrientation).activityInfo
        }
    }

    // DASH text size — override the composition's fontScale with DASH's own value so every sp in
    // DASH chrome follows the DASH text-size control and ignores Android's font setting entirely
    // (Android's font size is left for the viewport apps). The device density (dp mapping) is kept
    // as-is; only text scaling is taken over here.
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, dashTextScale),
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
            systemState = controller.systemState,
        ),
        // The live transport desk for Modules › Transport Manager (1.5.10). Live flows off the
        // TransportManager (held for the app's life), plus the check-now sweep and the deep-links out
        // to Android's own Wi-Fi / Bluetooth settings for radio-level and pairing controls.
        LocalTransportDesk provides TransportDesk(
            transportStatuses = transport.transportStatuses,
            devices = transport.devices,
            lastInboundAt = transport.lastInboundAt,
            lastDashAt = controller.lastDashAt,
            wire = transport.wire,
            send = transport::send,
            onOpenWifiSettings = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onOpenBluetoothSettings = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // Captured here so the whole screen can measure against the window, not against whatever
            // nested BoxWithConstraints happens to be in scope further down.
            val screenWidth = maxWidth

            // The not-default-launcher banner is gone (roadmap 1.6.2). A purple full-width strip
            // anchored opposite the bar, it wanted exactly the edge the module panel now takes, and
            // Roger's call was that he would not miss it. Nobody is stranded: System › Android
            // Settings Links offers Android's own home-settings and default-apps screens, which is
            // where a "make me the launcher" action belongs anyway.

            // The module panel measures the screen for every other DASH surface — **when there is
            // one to measure.**
            //
            // **The long edge is the docked edge minus what the bar has already taken from it**
            // (Roger, 1.6.3). Docked top or bottom the bar is on the opposite horizontal edge and
            // takes nothing, so the panel runs the full screen width. Docked left or right the bar
            // spans the full width at top or bottom and eats into the vertical run, so the panel
            // runs the screen height *less the bar* — which also means changing the bar height
            // resizes a vertical panel.
            //
            // **No layout, no panel** (module-layout.md §6, ruled 2026-08-12, built at 1.6.6). With
            // no installed module able to fill the selected slot there is no panel at all, and the
            // screen reclaims the space rather than reserving a strip to display nothing. That is
            // why the thickness below is zero rather than the panel merely being skipped at the
            // point it would be drawn: every inset on this screen is measured from it, so an absent
            // panel has to be absent from the arithmetic too, or settings would still roll out into
            // a band that had been set aside for a king who never arrived.
            val barThickness = barConfig.heightDp.dp
            val panelEdge = effectiveEdge(modulePanelConfig.edge, barConfig.position)
            val panelDocument = rememberActivePanel(
                database = controller.database,
                slot = slotFor(modulePanelConfig.size, panelEdge),
            )
            val panelValues = controller.moduleData.valuesFor(panelDocument?.moduleId)
            val panelLongEdge = if (panelEdge.horizontal) screenWidth else (maxHeight - barThickness)
            val panelThickness = if (panelDocument == null) 0.dp else
                ModulePanelSpec.thicknessFor(modulePanelConfig.size, panelLongEdge)
            val panelWidth = if (panelEdge.horizontal) screenWidth else panelThickness
            val panelHeight = if (panelEdge.horizontal) panelThickness else panelLongEdge

            // The diagnostic overlay is gone (roadmap 1.5.15). Four lines of grey 10sp — pixel size,
            // native dpi, the applied density preset and the old dashScale — pinned permanently over
            // the top-left of the user's home screen since 1.1.x, when reading those numbers while
            // changing density was the whole job. It never got a gate. System › About DASH now
            // reports all of it and more, on demand, which is where a device readout belongs.
            // Settings shell (roadmap 1.5.2). Drawn *before* the bar so the bar stays on top and
            // reachable while the panel is open. It rolls out from under the bar like a blind: an
            // explicitly animated height from 0 to the region's measured full height, clipped so the
            // content is revealed rather than stretched. This is duration-accurate on purpose —
            // AnimatedVisibility's expandVertically does not animate when the container is forced to
            // fillMaxSize, which is why the transition-speed setting had no visible effect.
            // The bounds conform to the surrounding chrome on both edges — the bar on one, the module
            // panel on the other. The panel is persistent, so settings always yields to it and can
            // never cover it (Roger, 1.6.2): DASH's own chrome does not sit on top of the king's
            // castle. That leaves the blind rolling out into the band between the two.
            val barIsTop = barConfig.position == BarPosition.TOP
            // Settings conforms to both surfaces on whichever edges they hold — the bar on its one,
            // the panel on its own, and they can never be the same edge. A fixed-ratio panel derives
            // its thickness from the length of its edge, so on a wide screen it can ask for more
            // than the screen has; the vertical insets are clamped so the blind is never handed
            // negative space to lay out in. That guards the arithmetic only — the panel still draws
            // at its true ratio, so an overflowing shape stays visibly wrong rather than trimmed.
            val panelVerticalInset =
                if (panelEdge.horizontal) (panelThickness).coerceAtMost(maxHeight - barThickness) else 0.dp
            val settingsTopInset =
                (if (barIsTop) barThickness else 0.dp) + (if (panelEdge == PanelEdge.TOP) panelVerticalInset else 0.dp)
            val settingsBottomInset =
                (if (!barIsTop) barThickness else 0.dp) + (if (panelEdge == PanelEdge.BOTTOM) panelVerticalInset else 0.dp)
            val settingsStartInset = if (panelEdge == PanelEdge.LEFT) panelThickness else 0.dp
            val settingsEndInset = if (panelEdge == PanelEdge.RIGHT) panelThickness else 0.dp
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxSize()
                    .padding(
                        top = settingsTopInset,
                        bottom = settingsBottomInset,
                        start = settingsStartInset,
                        end = settingsEndInset,
                    )
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
                    // On the DASH button idiom since 1.5.15. These two were the last controls in
                    // DASH still wearing Material's shape, elevation, ripple and padding — everything
                    // else moved across at 1.5.8 and these were missed, which is why edit mode was
                    // the one workspace that didn't look like the rest of the system.
                    DashButton(
                        label = "CANCEL",
                        fill = editTheme.accentColourSecondary,
                        ink = editTheme.textColourSecondary,
                        onClick = {
                            editConfig = null
                            editMode = false
                            if (settingsReturnTarget != null) showSettings = true
                        },
                    )
                    DashButton(
                        label = "SAVE",
                        fill = editTheme.backgroundColourPrimary,
                        ink = editTheme.textColourPrimary,
                        onClick = {
                            editConfig?.let { scope.launch { prefs.saveSystemBarConfig(it) } }
                            editConfig = null
                            editMode = false
                            if (settingsReturnTarget != null) showSettings = true
                        },
                    )
                }
            }

            // The module panel (roadmap 1.6.2) — DASH's defining surface, on screen for the first
            // time. Large slot, persistent, one panel; docking is 1.6.3, the other sizes 1.6.4.
            //
            // **It never shares an edge with the bar** (Roger, 1.6.2) — where interface.md previously
            // had the panel beginning where the bar ends, the two now cannot meet at all. The user's
            // chosen edge is honoured unless the bar is already there, in which case the panel is
            // displaced to the opposite edge for as long as the collision lasts; see [effectiveEdge]
            // for why the stored preference is never rewritten.
            //
            // Positioned from the top-left rather than by Alignment so the move between edges can be
            // animated: an edge change moves the panel in two dimensions and resizes it (the long
            // edge changes from screen width to screen height less the bar), which four animated
            // values express directly and an Alignment switch cannot express at all. Registered as
            // MODULE_PANEL_MOVE, so its speed is the user's like every other transition.
            //
            // Hidden in edit mode, carrying forward the placeholder's rule: edit mode is a focused
            // workspace holding nothing but the bar, its ruler and Save/Cancel, and a panel this
            // size would crowd the ruler being dragged. Permanent means permanent on the home
            // screen, not inside a temporary task.
            if (!editMode && panelDocument != null) {
                // A vertical panel starts below a top bar and ends above a bottom one, so it always
                // clears the bar rather than running under it.
                val panelTargetX = if (panelEdge == PanelEdge.RIGHT) screenWidth - panelWidth else 0.dp
                val panelTargetY = when {
                    panelEdge == PanelEdge.TOP -> 0.dp
                    panelEdge == PanelEdge.BOTTOM -> maxHeight - panelHeight
                    else -> if (barIsTop) barThickness else 0.dp
                }
                val moveSpec = tween<Dp>(transitions.millis(TransitionId.MODULE_PANEL_MOVE))
                val panelX by animateDpAsState(panelTargetX, moveSpec, label = "modulePanelX")
                val panelY by animateDpAsState(panelTargetY, moveSpec, label = "modulePanelY")
                val panelW by animateDpAsState(panelWidth, moveSpec, label = "modulePanelW")
                val panelH by animateDpAsState(panelHeight, moveSpec, label = "modulePanelH")

                ModulePanel(
                    document = panelDocument,
                    values = panelValues,
                    width = panelW,
                    height = panelH,
                    modifier = Modifier.align(Alignment.TopStart).offset(x = panelX, y = panelY),
                )
            }

            // The legacy flat settings panel is gone (roadmap 1.5.15). It existed from 1.5.2 as a
            // temporary bridge so nothing built in 1.1.x–1.4.x was unreachable while the settings
            // shell was filled in one tab at a time. Every control it held has a real home now —
            // the last one, ROTATION, went to Layout › Rotation in this same version.

            // Module Management is now a settings tab (Modules › Module Management, 1.5.8) rendered
            // inside the shell — no standalone route. It reaches the controller's managers through
            // LocalModuleDesk, provided above.

            // The Serial Monitor and Signal Monitor are settings tabs since 1.5.12 (Modules › Serial
            // Monitor / Signal Monitor), rendered inside the shell — no standalone routes any more.
            // They reach the transport layer and the sourceless core through the two desks above.

            // Splash overlay — sits above everything, including the settings panel. "None" is a real
            // choice (roadmap 1.5.15, Roger): DASH has no opinion on whether you want a splash, so
            // that mode skips it entirely rather than showing an empty one for zero milliseconds.
            if (showSplash && splashMode != "NONE") {
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
