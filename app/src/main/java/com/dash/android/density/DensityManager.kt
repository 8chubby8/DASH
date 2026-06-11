package com.dash.android.density

import android.content.Context
import android.os.Process
import android.util.DisplayMetrics

class DensityManager(private val context: Context) {

    private val userId: Int get() = Process.myUid() / 100000

    fun readCurrentSystemDpi(): Int =
        context.applicationContext.resources.displayMetrics.densityDpi

    fun formatDpi(dpi: Int): String {
        val match = DensityPreset.entries.firstOrNull { it.dpi == dpi }
        return if (match != null) "${match.label} (${dpi} dpi)" else "Custom (${dpi} dpi)"
    }

    fun checkCapability(): Boolean = try {
        val wms = windowManagerService() ?: return false
        // Probe by setting native density — visually a no-op, but exercises the full call path.
        wms.javaClass
            .getMethod("setForcedDisplayDensityForUser", Int::class.java, Int::class.java, Int::class.java)
            .invoke(wms, 0, DisplayMetrics.DENSITY_DEVICE_STABLE, userId)
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