package com.dash.android.ui.settings.content

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.density.DensityManager
import com.dash.android.density.DensityPreset
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.scale.DASH_TEXT_SCALE_MAX
import com.dash.android.ui.scale.DASH_TEXT_SCALE_MIN
import com.dash.android.ui.scale.DASH_TEXT_SCALE_STEP
import com.dash.android.ui.systembar.SystemBarConfig
import com.dash.android.ui.theme.LocalDashTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Appearance › Size & Scale (roadmap 1.5.3). Two clearly separated sections, each under its own
 * heading so DASH's settings never blur into Android's:
 *
 *  **DASH Scale** — DASH's own chrome, each surface on its own ± stepper: system bar size, element
 *  size, the (not-yet-built) app-favourites bar, and DASH text size (applied at the composition
 *  root in MainScreen, so DASH text follows this and ignores Android's font setting).
 *
 *  **Android Density** — Android's system density, which only ever touches the viewport apps.
 *
 * Each stepper persists on the tap, so the bar and the panel's own text resize immediately.
 */
private const val WITHIN_SECTION = 34
private const val BETWEEN_SECTIONS = 56

// Android's own font-size buckets, mirrored by the privileged "mimic Android" control.
private val ANDROID_FONT_LABELS = listOf("Small", "Default", "Large", "Larger")
private val ANDROID_FONT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f)

private fun snapTenth(v: Float): Float = (v * 10).roundToInt() / 10f

@Composable
fun SizeScaleContent() {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val densityManager = remember { DensityManager(appContext) }
    val prefs = remember { DashPreferences(appContext) }
    val scope = rememberCoroutineScope()
    val capable = remember { densityManager.checkCapability() }

    val barConfig by prefs.systemBarConfig.collectAsState(initial = SystemBarConfig.default())
    val dashTextScale by prefs.dashTextScale.collectAsState(initial = 1.0f)

    val presets = DensityPreset.entries
    val savedPreset by prefs.densityPreset.collectAsState(initial = null)
    val selectedIndex = savedPreset?.let { presets.indexOf(it) }?.takeIf { it >= 0 }
        ?: presets.indexOf(DensityPreset.NORMAL)

    // Visual for now: the privileged Android font-size control. Selecting a bucket moves the
    // preview; writing Android's real font scale is the functional piece still to build (the font
    // analogue of DensityManager), so it changes nothing yet.
    var androidFontIndex by remember { mutableIntStateOf(1) }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── DASH Scale ───────────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp),
        ) {
            SettingsContentHeader(title = "DASH Scale")

            SettingBlock(
                name = "System bar size",
                help = "The height of the DASH system bar.",
                control = {
                    Stepper(
                        value = "${barConfig.heightDp} dp",
                        onMinus = {
                            val h = (barConfig.heightDp - SystemBarConfig.HEIGHT_STEP_DP)
                                .coerceAtLeast(SystemBarConfig.MIN_HEIGHT_DP)
                            val element = barConfig.elementHeightDp
                                .coerceIn(SystemBarConfig.MIN_ELEMENT_HEIGHT_DP, h - SystemBarConfig.HEIGHT_STEP_DP)
                            scope.launch { prefs.saveSystemBarConfig(barConfig.copy(heightDp = h, elementHeightDp = element)) }
                        },
                        onPlus = {
                            val h = (barConfig.heightDp + SystemBarConfig.HEIGHT_STEP_DP)
                                .coerceAtMost(SystemBarConfig.MAX_HEIGHT_DP)
                            scope.launch { prefs.saveSystemBarConfig(barConfig.copy(heightDp = h)) }
                        },
                    )
                },
            )

            SettingBlock(
                name = "Element size",
                help = "How large the elements inside the bar render. Capped so they always fit the bar.",
                control = {
                    val elementMax = (barConfig.heightDp - SystemBarConfig.HEIGHT_STEP_DP)
                        .coerceAtLeast(SystemBarConfig.MIN_ELEMENT_HEIGHT_DP)
                    Stepper(
                        value = "${barConfig.elementHeightDp} dp",
                        onMinus = {
                            val e = (barConfig.elementHeightDp - SystemBarConfig.ELEMENT_HEIGHT_STEP_DP)
                                .coerceAtLeast(SystemBarConfig.MIN_ELEMENT_HEIGHT_DP)
                            scope.launch { prefs.saveSystemBarConfig(barConfig.copy(elementHeightDp = e)) }
                        },
                        onPlus = {
                            val e = (barConfig.elementHeightDp + SystemBarConfig.ELEMENT_HEIGHT_STEP_DP)
                                .coerceAtMost(elementMax)
                            scope.launch { prefs.saveSystemBarConfig(barConfig.copy(elementHeightDp = e)) }
                        },
                    )
                },
            )

            SettingBlock(
                name = "App favourites bar size",
                help = "The height of the app favourites bar.",
                tag = "Arrives with the App Launcher · 1.8.x",
                control = { Stepper(value = "—", enabled = false, onMinus = {}, onPlus = {}) },
            )

            SettingBlock(
                name = "DASH text size",
                help = "DASH's own text no longer follows Android's font setting — this is the only control.",
                control = {
                    Stepper(
                        value = "%.1f×".format(dashTextScale),
                        onMinus = {
                            val v = snapTenth(dashTextScale - DASH_TEXT_SCALE_STEP)
                                .coerceIn(DASH_TEXT_SCALE_MIN, DASH_TEXT_SCALE_MAX)
                            scope.launch { prefs.saveDashTextScale(v) }
                        },
                        onPlus = {
                            val v = snapTenth(dashTextScale + DASH_TEXT_SCALE_STEP)
                                .coerceIn(DASH_TEXT_SCALE_MIN, DASH_TEXT_SCALE_MAX)
                            scope.launch { prefs.saveDashTextScale(v) }
                        },
                    )
                },
            )
        }

        Spacer(Modifier.height(BETWEEN_SECTIONS.dp))

        // ── Android Density ──────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp),
        ) {
            SettingsContentHeader(title = "Android")

            SettingBlock(
                name = "App density",
                help = "Android's system density — it sizes the apps in your viewport, never DASH.",
                tag = "Viewport apps",
                control = {
                    if (capable) {
                        // System privilege present: change density natively, the way Android's own
                        // display-size page does — DASH mirrors it so the user needn't leave.
                        PresetSegment(presets.map { it.label }, selectedIndex) { i ->
                            val preset = presets[i]
                            scope.launch { prefs.saveDensityPreset(preset) }
                            densityManager.setDensity(preset)
                        }
                    } else {
                        // No privilege (Bronze): DASH can't set density, so there's no control to
                        // show — just the link out to Android's own page, beside the text.
                        LinkButton("Android text & display size →") {
                            val direct = runCatching {
                                context.startActivity(
                                    Intent("android.settings.TEXT_READING_SETTINGS")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            if (direct.isFailure) {
                                context.startActivity(
                                    Intent(Settings.ACTION_DISPLAY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                },
                // The apps preview only means anything when DASH can actually change density.
                preview = if (capable) {
                    {
                        LivePreviewCard("Your apps") {
                            val scale = when (presets[selectedIndex]) {
                                DensityPreset.COMPACT -> 0.8f
                                DensityPreset.NORMAL -> 1.0f
                                DensityPreset.COMFORTABLE -> 1.2f
                                DensityPreset.LARGE -> 1.45f
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("Maps", "Music", "Phone", "Radio").forEach { AppTile(it, scale) }
                            }
                        }
                    }
                } else {
                    null
                },
            )

            // Font size — privileged path only. On Bronze the single link in App density already
            // covers it, so no separate control appears there.
            if (capable) {
                SettingBlock(
                    name = "Font size",
                    help = "Android's font size for your apps' text — DASH's own text is set above.",
                    control = {
                        PresetSegment(ANDROID_FONT_LABELS, androidFontIndex) { androidFontIndex = it }
                    },
                    preview = {
                        LivePreviewCard("Your apps' text") {
                            FontSample(ANDROID_FONT_SCALES[androidFontIndex])
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FontSample(scale: Float) {
    val theme = LocalDashTheme.current
    Text(
        "Aa  Sample app text",
        color = theme.textColourSecondary,
        fontSize = (16 * scale).sp,
        fontFamily = theme.font,
    )
}

@Composable
private fun AppTile(label: String, scale: Float) {
    val theme = LocalDashTheme.current
    val size by animateDpAsState((36 * scale).dp, tween(280), label = "tile")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.textColourSecondary.copy(alpha = 0.5f))
        )
        Text(label, color = theme.textColourSecondary.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = theme.font)
    }
}
