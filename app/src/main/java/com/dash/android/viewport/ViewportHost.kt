package com.dash.android.viewport

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **The viewport — test build** (roadmap 1.7.1).
 *
 * The question 1.7.x turns on is not where the viewport is (DASH has always known that — it is the
 * rectangle the settings blind rolls into) but whether DASH can make *another app* live inside it.
 * An ordinary launcher cannot simply tell Maps "you live in this box". Testing on 2026-10-01 found a
 * ladder of ways that work on some hardware and not on others:
 *
 * 1. **System app** — the ideal; full control of every window. Silver/Gold hardware.
 * 2. **Shizuku** — the same window control through shell powers. Worked on the Tab S9 Ultra and the
 *    Pixel 8 Pro when driven from adb.
 * 3. **Windowed** — an ordinary app asks Android to open the app at given bounds. Samsung honours it;
 *    the Pixel ignores it, even with freeform switched on. Placement at launch only — once open, an
 *    ordinary app cannot move it.
 * 4. **Draw on top** — the app runs full screen and DASH's chrome floats over it. Works almost
 *    everywhere, but the app does not know, so whatever sits under the bars is covered.
 * 5. **Full screen** — nothing works; the app takes the screen, as before 1.7.x.
 *
 * **Found while building it: an ordinary app cannot get back in front of a windowed one.** Samsung
 * keeps freeform windows above every other task — DASH reordering itself forward, a home intent, even
 * a full-screen app launch all leave Maps on top. Only the user's own Home key tucks them away. So
 * settings opened while an app is in the viewport rolls out *underneath* it. Recorded as an open
 * problem for this test build rather than worked around.
 *
 * **This build detects all five and drives rungs 3, 4 and 5.** Draw on top was added second (Roger,
 * 2026-10-01) because it is the one ordinary-permission rung that reaches the Pixel. Rungs 1 and 2 are
 * reported honestly as detected-but-not-built, so the panel shows the whole ladder rather than
 * pretending it is shorter. The drawing itself is [OverlayChrome]'s; this class only decides when.
 *
 * **A test-only method picker** lets the rung be forced, so the same device can show Windowed and
 * Draw on top side by side. Automatic — the best rung that works — is the default.
 *
 * Capability detection, per CLAUDE.md: **the windowed rung is tested, never assumed.** A Pixel says
 * it supports freeform windows and then ignores every bounds request an ordinary app makes, so the
 * feature flag is worthless as an answer. DASH launches a blank window of its own
 * ([ViewportProbeActivity]) at a known rectangle and reads back where it actually landed.
 */
class ViewportHost(private val context: Context) {

    enum class Method(val label: String) {
        SYSTEM("System app"),
        SHIZUKU("Shizuku"),
        WINDOWED("Windowed"),
        OVERLAY("Draw on top"),
        FULLSCREEN("Full screen"),
    }

    /** What the probe found for one rung. [works] is whether the capability is there; [built] is
     *  whether this build can actually drive it. Only a rung that is both is ever used. */
    data class MethodStatus(val method: Method, val works: Boolean, val built: Boolean, val detail: String)

    private val _rect = MutableStateFlow<Rect?>(null)
    /** The viewport in screen pixels, as the layout last measured it. Null until the first layout. */
    val rect: StateFlow<Rect?> = _rect.asStateFlow()

    private val _statuses = MutableStateFlow<List<MethodStatus>>(emptyList())
    val statuses: StateFlow<List<MethodStatus>> = _statuses.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val store = context.getSharedPreferences("viewport_test", Context.MODE_PRIVATE)

    private val _choice = MutableStateFlow(
        store.getString(KEY_CHOICE, null)?.let { runCatching { Method.valueOf(it) }.getOrNull() }
    )
    /** The test build's forced rung, or null for automatic. */
    val choice: StateFlow<Method?> = _choice.asStateFlow()

    fun choose(method: Method?) {
        _choice.value = method
        store.edit().putString(KEY_CHOICE, method?.name).apply()
        publish()
    }

    /**
     * The rung in use. A forced choice is honoured only if it works and is built — forcing Windowed on
     * a Pixel would otherwise "succeed" at opening every app full screen while claiming not to. Short
     * of that, the best rung that both works and is built. Full screen always qualifies.
     */
    val active: Method
        get() {
            val usable = _statuses.value.filter { it.works && it.built }
            return _choice.value?.takeIf { c -> usable.any { it.method == c } }
                ?: usable.firstOrNull()?.method ?: Method.FULLSCREEN
        }

    private val _activeFlow = MutableStateFlow(Method.FULLSCREEN)
    /** [active], observable — the stand-in launcher and the settings page both show it. */
    val activeFlow: StateFlow<Method> = _activeFlow.asStateFlow()

    /** The app DASH last opened. Draw on top floats DASH's chrome over it while it is in front. */
    var currentApp: String? = null
        private set

    /**
     * Set when DASH was brought forward *from* an app — its settings button pressed on the floating
     * bar — so closing settings hands the screen back to that app rather than stranding the user on
     * DASH. Cleared on use, and by the Home key.
     */
    private var returnTo: String? = null

    private val _settingsRequest = MutableStateFlow(false)
    /** The floating bar's settings button, reaching the real screen. Consumed by [MainScreen]. */
    val settingsRequest: StateFlow<Boolean> = _settingsRequest.asStateFlow()

    private var windowedResult: MethodStatus? = null

    private val main = Handler(Looper.getMainLooper())
    private var tested = false
    private val settledTest = Runnable { if (!tested) test() }

    /**
     * The layout reports several rectangles while DASH starts — before its saved bar and panel
     * settings have loaded, the viewport is briefly the whole screen. The first test waits for the
     * rectangle to hold still, so it is not run against a layout that is about to change.
     */
    fun updateRect(r: Rect) {
        if (r == _rect.value || r.width() <= 0 || r.height() <= 0) return
        _rect.value = r
        Log.i(TAG, "viewport rect $r")
        main.removeCallbacks(settledTest)
        main.postDelayed(settledTest, SETTLE_MS)
    }

    /** Run the whole ladder again. The windowed rung answers asynchronously, from the probe window. */
    fun test() {
        val viewportRect = _rect.value ?: return
        tested = true
        // The probe asks for a box well inside the viewport rather than the viewport itself: the
        // question is whether Android places windows where asked at all, and a box hard against the
        // screen edge mixes that up with a second question — Samsung will not let a window cover its
        // status bar's strip, even with the bar hidden, and nudges it down (found 2026-10-01).
        val r = Rect(viewportRect).apply { inset(width() / 8, height() / 8) }
        windowedResult = null
        publish()
        if (!supportsFreeformFlag()) {
            windowedResult = MethodStatus(Method.WINDOWED, false, true, "Device has no windowed mode")
            publish()
            return
        }
        _testing.value = true
        val probe = Intent(context, ViewportProbeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            .putExtra(ViewportProbeActivity.EXTRA_RECT, r)
        val options = ActivityOptions.makeBasic().setLaunchBounds(r)
        val launched = runCatching { context.startActivity(probe, options.toBundle()) }
        if (launched.isFailure) {
            Log.w(TAG, "probe launch failed", launched.exceptionOrNull())
            onProbeResult(false, "Probe could not start")
        }
    }

    /** Called by [ViewportProbeActivity] with where it actually landed. */
    internal fun onProbeResult(ok: Boolean, detail: String) {
        Log.i(TAG, "windowed probe ok=$ok ($detail)")
        windowedResult = MethodStatus(Method.WINDOWED, ok, true, detail)
        _testing.value = false
        publish()
    }

    /** Re-read the permissions without re-running the windowed probe — DASH coming back from Android's
     *  "Display over other apps" screen is the case this is for. */
    fun refresh() = publish()

    private fun publish() {
        val system = hasPermission("android.permission.MANAGE_ACTIVITY_TASKS")
        val shizuku = isInstalled(SHIZUKU_PACKAGE)
        val overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
        _statuses.value = listOf(
            MethodStatus(Method.SYSTEM, system, false,
                if (system) "Available — not built in this test" else "Not a system app"),
            MethodStatus(Method.SHIZUKU, shizuku, false,
                if (shizuku) "Installed — not built in this test" else "Not installed"),
            windowedResult ?: MethodStatus(Method.WINDOWED, false, true, "Testing…"),
            MethodStatus(Method.OVERLAY, overlay, true,
                if (overlay) "Permission granted" else "Needs \"Display over other apps\""),
            MethodStatus(Method.FULLSCREEN, true, true, "Always available"),
        )
        _activeFlow.value = active
    }

    /** Android's own screen for the draw-on-top grant — the user switches it on; DASH never can. */
    fun openOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Whether DASH's chrome should float over the app in front. */
    val overlaying: Boolean
        get() = currentApp != null && active == Method.OVERLAY

    /**
     * The floating bar's settings button. DASH is brought forward — over a full-screen app that is
     * simply the launcher coming to the front — and asked to open settings; closing them later hands
     * the screen back to [currentApp].
     */
    fun settingsFromApp() {
        returnTo = currentApp
        _settingsRequest.value = true
        runCatching {
            context.startActivity(
                Intent(context, com.dash.android.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(EXTRA_FROM_APP, true)
            )
        }.onFailure { Log.w(TAG, "could not bring DASH forward", it) }
    }

    fun settingsRequestHandled() { _settingsRequest.value = false }

    /** Settings has closed. If it was opened from an app, that app gets the screen back. */
    fun settingsClosed() {
        val pkg = returnTo ?: return
        returnTo = null
        launch(pkg)
    }

    /** The user went home on purpose (the Home key reaches a launcher as a new intent): the app is
     *  no longer "in the viewport", so nothing floats over anything and nothing is handed back. */
    fun wentHome() {
        currentApp = null
        returnTo = null
    }

    /**
     * Open [pkg] in the viewport using the active rung. An app already open is only brought forward —
     * an ordinary app cannot move a window once Android has placed it, which is the windowed rung's
     * real limit and why Shizuku sits above it.
     */
    fun launch(pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val r = _rect.value
        val bundle = if (active == Method.WINDOWED && r != null) {
            ActivityOptions.makeBasic().setLaunchBounds(r).toBundle()
        } else null
        runCatching { context.startActivity(intent, bundle) }
            .onSuccess { currentApp = pkg; Log.i(TAG, "launched $pkg via $active into $r") }
            .onFailure { Log.w(TAG, "launch $pkg failed", it) }
    }

    private fun supportsFreeformFlag(): Boolean =
        Build.VERSION.SDK_INT >= 24 &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    private fun hasPermission(name: String) =
        context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED

    private fun isInstalled(pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    companion object {
        const val TAG = "DashViewport"
        private const val SETTLE_MS = 1500L
        private const val KEY_CHOICE = "choice"
        /** Marks DASH's own bring-forward, so [MainActivity] does not mistake it for the Home key. */
        const val EXTRA_FROM_APP = "com.dash.android.viewport.FROM_APP"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
