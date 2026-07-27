package com.dash.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dash.android.density.DensityManager
import com.dash.android.ui.screen.MainScreen
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    var pendingWakeSplash = false

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.densityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        val isColdBoot = savedInstanceState == null
        setContent {
            MainScreen(activity = this, isColdBoot = isColdBoot)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    fun openSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            startActivity(roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
        } else {
            openChangeLauncher()
        }
    }

    /**
     * Leave DASH — hand the screen back to Android and end the process (roadmap 1.5.14, rehomed from
     * the legacy panel's EXIT DASH).
     *
     * The density reset is the half that matters. DASH may have forced a display density on
     * privileged hardware, and walking away leaving the whole device at a density the user did not
     * choose would be DASH changing something and not putting it back.
     *
     * Note this only really lands where DASH is *not* the default launcher: a home app that finishes
     * is immediately relaunched by Android, because there is nowhere else for the screen to go. The
     * Exit tab says so, rather than leaving the user to work out why nothing happened.
     */
    fun exitDash() {
        DensityManager(this).resetToDefault()
        finish()
        exitProcess(0)
    }

    fun openChangeLauncher() {
        val intent = runCatching {
            Intent(Settings.ACTION_HOME_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }.getOrElse {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        startActivity(intent)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
