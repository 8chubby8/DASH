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
import com.dash.android.DashApplication
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
import com.dash.android.ui.common.TINY
import com.dash.android.ui.common.SETTING_SPACING
import com.dash.android.ui.common.CONTROL_WIDTH
import com.dash.android.ui.common.DashButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import com.dash.android.ui.common.controlWidth

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
    // Asked once for the whole process (roadmap 1.5.14) — About DASH reports the same answer, and a
    // privileged call is not something to make twice for a question whose answer cannot change.
    val capable = remember { (appContext as DashApplication).densityCapable }

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

    // Every control in the nook shares one width — steppers, segments and buttons alike — so the
    // right-hand column reads as a column (roadmap 1.5.15, Roger).
    val controlWidth = Modifier.width(controlWidth(LocalDensity.current.fontScale))

    Column(modifier = Modifier.fillMaxWidth()) {

        // The page is titled once, and its two sections sit a rank below it — DASH's own sizing, then
        // Android's. Before 1.5.15 both sections wore the page-title heading, so nothing said which
        // of them the page was actually about (Roger).
        SettingsContentHeader("Size & Scale")

        // ── DASH Scale ───────────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SETTING_SPACING),
        ) {
            SettingsSectionHeader("DASH Scale")

            SettingBlock(
                name = "System bar size",
                control = {
                    Stepper(
                        value = "${barConfig.heightDp} dp",
                        modifier = controlWidth,
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
                control = {
                    val elementMax = (barConfig.heightDp - SystemBarConfig.HEIGHT_STEP_DP)
                        .coerceAtLeast(SystemBarConfig.MIN_ELEMENT_HEIGHT_DP)
                    Stepper(
                        value = "${barConfig.elementHeightDp} dp",
                        modifier = controlWidth,
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
                tag = "Arrives with the App Launcher · 1.8.x",
                control = { Stepper(value = "—", enabled = false, modifier = controlWidth, onMinus = {}, onPlus = {}) },
            )

            SettingBlock(
                name = "DASH text size",
                control = {
                    Stepper(
                        value = "%.1f×".format(dashTextScale),
                        modifier = controlWidth,
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


        // ── Android Density ──────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SETTING_SPACING),
        ) {
            SettingsSectionHeader("Android")

            SettingBlock(
                name = "Viewport App Density",
                // The four-preset segment is too wide for the nook and stacks; the Bronze button is
                // not, so it sits on the right with every other control.
                fullWidthControl = capable,
                control = {
                    if (capable) {
                        // System privilege present: change density natively, the way Android's own
                        // display-size page does — DASH mirrors it so the user needn't leave.
                        PresetSegment(presets.map { it.label }, selectedIndex, Modifier.fillMaxWidth()) { i ->
                            val preset = presets[i]
                            scope.launch { prefs.saveDensityPreset(preset) }
                            densityManager.setDensity(preset)
                        }
                    } else {
                        // No privilege (Bronze): DASH can't set density, so there is no control to
                        // show — only the way out to Android's own page, sitting directly under the
                        // setting's name rather than off in the control nook. Boxed like every other
                        // action, at the shared control width (roadmap 1.5.15).
                        DashButton(
                            label = "Android text & display size",
                            modifier = controlWidth,
                            onClick = {
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
                            },
                        )
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
                    control = {
                        PresetSegment(ANDROID_FONT_LABELS, androidFontIndex, Modifier.fillMaxWidth()) { androidFontIndex = it }
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
        Text(label, color = theme.textColourSecondary.copy(alpha = 0.7f), fontSize = TINY, fontFamily = theme.font)
    }
}
