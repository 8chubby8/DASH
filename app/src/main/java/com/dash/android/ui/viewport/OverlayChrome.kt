package com.dash.android.ui.viewport

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dash.android.MainActivity
import com.dash.android.ui.screen.MainScreen
import com.dash.android.viewport.ViewportHost

/**
 * **Draw on top** — the viewport ladder's fourth rung (roadmap 1.7.1, test build).
 *
 * The app runs full screen, and DASH's own chrome — bar, module panel, tab bar — floats over it in
 * "display over other apps" windows. It is the one ordinary-permission way to give a device that will
 * not window apps (the Pixel) a DASH around them, and the price is known before it is paid: **the app
 * does not know the chrome is there**, so whatever it draws under the bar or the panel is covered.
 *
 * **The windows are exactly the screen minus the viewport** — up to four strips, one per side DASH
 * holds. That is what keeps the viewport itself touchable: an overlay window takes every touch inside
 * its own rectangle, so one full-screen window with a transparent hole would have swallowed the app's
 * touches too. Each strip draws the same screen as DASH, shifted so its own slice lines up, which is
 * why the chrome looks identical whether DASH is in front or floating.
 *
 * **They exist only while an app DASH opened is in front of it** — added when DASH's screen stops,
 * removed when it starts again. Pressing Home, or settings, brings DASH back and the strips go,
 * because DASH is then drawing its chrome itself.
 *
 * Known limits of this test build: the strips are cut for the layout at the moment the app opened,
 * so rotating while an app is up leaves them where they were; and the panel cannot expand while
 * floating, since its strip is only as large as its resting state.
 */
class OverlayChrome(private val activity: MainActivity, private val host: ViewportHost) {

    private val windowManager = activity.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val views = mutableListOf<View>()
    private var owner: OverlayOwner? = null

    fun show() {
        if (views.isNotEmpty() || !host.overlaying) return
        val viewport = host.rect.value ?: return
        val screen = screenBounds()
        val strips = listOf(
            Rect(screen.left, screen.top, screen.right, viewport.top),
            Rect(screen.left, viewport.bottom, screen.right, screen.bottom),
            Rect(screen.left, viewport.top, viewport.left, viewport.bottom),
            Rect(viewport.right, viewport.top, screen.right, viewport.bottom),
        ).filter { it.width() > 0 && it.height() > 0 }

        val newOwner = OverlayOwner().also { owner = it }
        val density = activity.resources.displayMetrics.density
        val screenW = (screen.width() / density).dp
        val screenH = (screen.height() / density).dp

        strips.forEach { strip ->
            val view = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(newOwner)
                setViewTreeSavedStateRegistryOwner(newOwner)
                setContent {
                    // The whole screen, laid out at full size and shifted so this strip's slice of
                    // it sits in this window.
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .offset(x = (-strip.left / density).dp, y = (-strip.top / density).dp)
                            .requiredSize(screenW, screenH)
                    ) {
                        MainScreen(activity = activity, isColdBoot = false, floating = true)
                    }
                }
            }
            val lp = WindowManager.LayoutParams(
                strip.width(), strip.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = strip.left
                y = strip.top
                // Placed in raw screen coordinates. Without this Android shifts an overlay clear of
                // the status and navigation bars, and the strips would no longer meet the viewport.
                if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                title = "DASH chrome"
            }
            runCatching { windowManager.addView(view, lp); views += view }
                .onFailure { Log.w(ViewportHost.TAG, "overlay strip $strip refused", it) }
        }
        Log.i(ViewportHost.TAG, "floating chrome over ${host.currentApp}: ${views.size} strip(s)")
    }

    fun hide() {
        if (views.isEmpty()) return
        views.forEach { runCatching { windowManager.removeViewImmediate(it) } }
        views.clear()
        owner?.destroy()
        owner = null
        Log.i(ViewportHost.TAG, "floating chrome removed")
    }

    private fun screenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= 30) {
            windowManager.maximumWindowMetrics.bounds
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(m)
            Rect(0, 0, m.widthPixels, m.heightPixels)
        }
}

/** A window outside any activity has no lifecycle of its own, and Compose needs one to run. This is
 *  the smallest that will do: resumed while the strips are up, destroyed when they come down. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}
