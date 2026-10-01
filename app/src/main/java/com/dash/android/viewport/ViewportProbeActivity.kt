package com.dash.android.viewport

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import com.dash.android.DashApplication
import kotlin.math.abs

/**
 * **The windowed rung's test** (roadmap 1.7.1). A blank, transparent window DASH opens at the
 * viewport's bounds, which reads back where Android actually put it and closes.
 *
 * It exists because the platform's own answer cannot be trusted: a Pixel reports freeform window
 * support and then opens every bounded launch full screen. The only honest test of "will Android put
 * an app in this box" is to ask it to put something in the box and look. DASH's own window is used
 * rather than a real app so the test needs nothing installed and disturbs nothing.
 *
 * Its own task affinity (manifest) keeps it out of DASH's home task — a bounded launch into an
 * existing fullscreen task is ignored, which would fail the test for the wrong reason.
 */
class ViewportProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = View(this)
        setContentView(view)
        // Measured after the first layout pass — before it the window has no settled bounds.
        view.post { report() }
    }

    private fun report() {
        val host = (application as DashApplication).viewport
        @Suppress("DEPRECATION")
        val wanted = intent.getParcelableExtra<Rect>(EXTRA_RECT)
        val actual = actualBounds()
        val windowed = isInMultiWindowMode
        val ok = windowed && wanted != null && close(wanted, actual)
        val detail = when {
            !windowed -> "Android opened it full screen"
            ok -> "Placed exactly — ${actual.width()} × ${actual.height()} px"
            else -> "Placed, but not where asked (${actual.flattenToString()})"
        }
        host.onProbeResult(ok, detail)
        finishAndRemoveTask()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun actualBounds(): Rect =
        if (Build.VERSION.SDK_INT >= 30) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val loc = IntArray(2)
            window.decorView.getLocationOnScreen(loc)
            Rect(loc[0], loc[1], loc[0] + window.decorView.width, loc[1] + window.decorView.height)
        }

    /** Within a couple of percent on every edge — a window manager may nudge by a few pixels. */
    private fun close(a: Rect, b: Rect): Boolean {
        val tol = maxOf(8, (minOf(a.width(), a.height()) * 0.02f).toInt())
        return abs(a.left - b.left) <= tol && abs(a.top - b.top) <= tol &&
            abs(a.right - b.right) <= tol && abs(a.bottom - b.bottom) <= tol
    }

    companion object {
        const val EXTRA_RECT = "com.dash.android.viewport.RECT"
    }
}
