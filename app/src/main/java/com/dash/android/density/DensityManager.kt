package com.dash.android.density

import android.content.Context
import android.os.Process

class DensityManager(private val context: Context) {

    private val userId: Int get() = Process.myUid() / 100000

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