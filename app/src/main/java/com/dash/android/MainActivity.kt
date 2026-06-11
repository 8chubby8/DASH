package com.dash.android

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dash.android.ui.screen.MainScreen

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // DASH always renders its own chrome at the device's native density,
        // independent of whatever system density the user has selected.
        val config = Configuration(newBase.resources.configuration)
        config.densityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainScreen(activity = this)
        }
    }
}
