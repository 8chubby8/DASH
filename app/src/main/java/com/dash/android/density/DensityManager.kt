package com.dash.android.density

import android.content.Context
import android.os.Process

class DensityManager(private val context: Context) {

    private val userId: Int get() = Process.myUid() / 100000

    fun readCurrentSystemDpi(): Int =
        context.applicationContext.resources.displayMetrics.densityDpi

    fun formatDpi(dpi: Int): String {
        val match = DensityPreset.entries.firstOrNull { it.dpi == dpi }
        return if (match != null) "${match.label} (${dpi} dpi)" else "Custom (${dpi} dpi)"
    }

    /**
     * Whether this installation can drive Android's display density. The only honest way to know is
     * to try it (the Capability Detection Principle) — there is no permission to query — so this
     * exercises the whole privileged call path and reports whether it went through.
     *
     * **It sets the density to whatever it already is.** That matters. The original probe passed
     * `DENSITY_DEVICE_STABLE`, which is a no-op only on a device sitting at its stock density: on
     * Silver/Gold hardware where the call *succeeds* and the user had set a DASH density, probing
     * reset their screen. Passing the current value makes the probe a true no-op at every tier, so
     * any screen can ask the question in passing — About DASH does, for its device report.
     *
     * Prefer `DashApplication.densityCapable`, which asks this once and keeps the answer.
     */
    fun checkCapability(): Boolean = try {
        val wms = windowManagerService() ?: return false
        wms.javaClass
            .getMethod("setForcedDisplayDensityForUser", Int::class.java, Int::class.java, Int::class.java)
            .invoke(wms, 0, readCurrentSystemDpi(), userId)
        true
    } catch (_: Exception) {
        false
    }

    fun setDensity(preset: DensityPreset) {
        callSetForcedDisplayDensity(preset.dpi)
    }

    fun resetToDefault() {
        callClearForcedDisplayDensity()
    }

    private fun windowManagerService(): Any? = try {
        val wmGlobal = Class.forName("android.view.WindowManagerGlobal")
        val method = wmGlobal.getDeclaredMethod("getWindowManagerService")
        method.isAccessible = true
        method.invoke(null)
    } catch (e: Exception) {
        null
    }

    private fun callSetForcedDisplayDensity(dpi: Int) {
        try {
            val wms = windowManagerService() ?: return
            wms.javaClass
                .getMethod("setForcedDisplayDensityForUser", Int::class.java, Int::class.java, Int::class.java)
                .invoke(wms, 0, dpi, userId)
        } catch (_: Exception) {}
    }

    private fun callClearForcedDisplayDensity() {
        try {
            val wms = windowManagerService() ?: return
            wms.javaClass
                .getMethod("clearForcedDisplayDensityForUser", Int::class.java, Int::class.java)
                .invoke(wms, 0, userId)
        } catch (_: Exception) {}
    }
}