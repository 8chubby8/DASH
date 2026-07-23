package com.dash.android.ui.settings.content

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.splash.CroppedImage
import com.dash.android.ui.splash.LocalSplashPreview
import com.dash.android.ui.splash.SPLASH_CROP_DEFAULT
import com.dash.android.ui.splash.SPLASH_DWELL_DEFAULT_MS
import com.dash.android.ui.splash.SplashCrop
import com.dash.android.ui.splash.SplashImage
import com.dash.android.ui.splash.rememberSplashBitmap
import com.dash.android.ui.theme.LocalDashTheme
import kotlin.math.max
import kotlin.math.min
import com.dash.android.ui.theme.SPLASH_BACKGROUND_COLOUR_DEFAULT
import com.dash.android.ui.theme.splashColourPresets
import kotlinx.coroutines.launch

/**
 * Appearance › Splash Screen (roadmap 1.5.6). Exposes the 1.2.x splash feature: pick a **background
 * colour** (BackgroundColourSplash — the splash's own independent colour, seedable from a theme token
 * but free to be anything), a **still image**, or an **animation** (a GIF / animated WebP that plays);
 * and set how long a colour/image splash holds. The two fades either side are transitions and live in
 * Appearance › Transitions.
 *
 * Layout: type at the top, then a preview in the **real shape of the screen** so what you see is what
 * boots, then the control for the chosen type, then — for colour and image only — the timer. Pick
 * Animation and the timer disappears: the animation's own length is its duration.
 */
private const val WITHIN_SECTION = 28
private const val BETWEEN_SECTIONS = 44

// Dwell is dialled in half-second steps, from an instant flash to a long hold. The floor is 0 — DASH
// has no opinion on whether you want a splash at all — and the ceiling is generous.
private const val DWELL_STEP_MS = 500
private const val DWELL_MIN_MS = 0
private const val DWELL_MAX_MS = 10_000

@Composable
fun SplashContent() {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val prefs = remember { DashPreferences(appContext) }
    val scope = rememberCoroutineScope()
    val theme = LocalDashTheme.current
    val previewFullScreen = LocalSplashPreview.current

    val mode by prefs.splashMode.collectAsState(initial = "COLOUR")
    val storedColour by prefs.splashBackgroundColour.collectAsState(initial = SPLASH_BACKGROUND_COLOUR_DEFAULT)
    val imageUri by prefs.splashImageUri.collectAsState(initial = "")
    val animationUri by prefs.splashAnimationUri.collectAsState(initial = "")
    val dwell by prefs.splashDwellMillis.collectAsState(initial = SPLASH_DWELL_DEFAULT_MS)

    // Colour edit state is held as HSV (the picker's stable source of truth), seeded from the stored
    // colour once it has loaded. After that the user's edits own it; preset taps re-seed it.
    var hsv by remember { mutableStateOf<Hsv?>(null) }
    LaunchedEffect(storedColour) { if (hsv == null) hsv = colourToHsv(storedColour) }
    val currentColour = hsv?.toColour() ?: Color(storedColour)

    // Per-orientation crop (Phase 2). The preview's shape follows this toggle, *not* the device's
    // current orientation — which is what made a landscape phone show a portrait preview. Default to
    // landscape, DASH's home turf. The screen's long/short dimensions come from the config either way
    // round, so max/min gives the true aspect regardless of how the device is held.
    val cropPortrait by prefs.splashCropPortrait.collectAsState(initial = SPLASH_CROP_DEFAULT)
    val cropLandscape by prefs.splashCropLandscape.collectAsState(initial = SPLASH_CROP_DEFAULT)
    var showLandscape by remember { mutableStateOf(true) }
    var editingCrop by remember { mutableStateOf(false) }
    var liveCrop by remember { mutableStateOf<SplashCrop?>(null) }
    val imageBitmap = rememberSplashBitmap(if (mode == "IMAGE") imageUri else "")
    LaunchedEffect(mode) { if (mode != "IMAGE") { editingCrop = false; liveCrop = null } }

    val config = LocalConfiguration.current
    val longDim = max(config.screenWidthDp, config.screenHeightDp).toFloat()
    val shortDim = min(config.screenWidthDp, config.screenHeightDp).toFloat()
    val previewAspect = if (showLandscape) longDim / shortDim else shortDim / longDim
    val activeCrop = if (showLandscape) cropLandscape else cropPortrait
    val displayCrop = liveCrop ?: activeCrop

    // One SAF picker per file kind, each with a persistable grant so the choice survives a reboot.
    // "image/*" covers stills and animated images (GIF / animated WebP); animation plays on API 28+.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch { prefs.saveSplashImageUri(it.toString()); prefs.saveSplashMode("IMAGE") }
        }
    }
    val animationPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch { prefs.saveSplashAnimationUri(it.toString()); prefs.saveSplashMode("ANIMATION") }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Type ───────────────────────────────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp)) {
            SettingsContentHeader(
                title = "Splash Screen",
                description = "The screen DASH shows as it comes up. A colour of your own, a still image, " +
                    "or an animation that plays.",
            )
            SettingBlock(
                name = "Type",
                help = "Colour and image hold for a set time; an animation plays through once.",
                fullWidthControl = true,
                control = {
                    FitPresetSegment(
                        labels = listOf("Colour", "Image", "Animation"),
                        selected = when (mode) { "IMAGE" -> 1; "ANIMATION" -> 2; else -> 0 },
                    ) { i ->
                        val next = when (i) { 1 -> "IMAGE"; 2 -> "ANIMATION"; else -> "COLOUR" }
                        scope.launch { prefs.saveSplashMode(next) }
                    }
                },
            )
        }

        Spacer(Modifier.height(BETWEEN_SECTIONS.dp))

        // ── Background — the splash's own colour, kept for all three types. For Colour it is the
        //    whole screen; for Image / Animation it is the backdrop and the matte behind letterboxing.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp)) {
            SettingsContentHeader(
                title = "Background",
                description = when (mode) {
                    "COLOUR" -> null
                    else -> "Sits behind your ${if (mode == "ANIMATION") "animation" else "image"}, and " +
                        "fills any gaps when it doesn't cover the screen."
                },
            )
            ColourSwatches(
                presets = splashColourPresets(theme),
                selected = currentColour,
            ) { picked ->
                hsv = colourToHsv(picked.toArgbLong())
                scope.launch { prefs.saveSplashBackgroundColour(picked.toArgbLong()) }
            }
            hsv?.let { current ->
                ColourPicker(
                    hsv = current,
                    onChange = { hsv = it },
                    onCommit = { scope.launch { prefs.saveSplashBackgroundColour(current.toColour().toArgbLong()) } },
                )
            }
        }

        Spacer(Modifier.height(BETWEEN_SECTIONS.dp))

        // ── Preview — actual screen shape, its orientation chosen by the toggle. A still image can be
        //    cropped in place (tap it); each orientation keeps its own crop.
        LivePreviewCard(label = "Preview") {
            PresetSegment(
                labels = listOf("Landscape", "Portrait"),
                selected = if (showLandscape) 0 else 1,
            ) { i -> showLandscape = (i == 0); liveCrop = null }

            val canCrop = mode == "IMAGE" && imageBitmap != null
            val enterEdit: () -> Unit = { editingCrop = true }

            if (canCrop && editingCrop) {
                SplashCropEditor(
                    bitmap = imageBitmap!!,
                    aspect = previewAspect,
                    colour = currentColour,
                    crop = displayCrop,
                    maxHeight = 380.dp,
                    onChange = { liveCrop = it },
                    onCommit = { c -> scope.launch { prefs.saveSplashCrop(showLandscape, c) } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LinkButton("Reset to standard") {
                        liveCrop = SPLASH_CROP_DEFAULT
                        scope.launch { prefs.saveSplashCrop(showLandscape, SPLASH_CROP_DEFAULT) }
                    }
                    LinkButton("Done") { editingCrop = false; liveCrop = null }
                }
            } else {
                SplashAspectPreview(
                    aspect = previewAspect,
                    colour = currentColour,
                    mode = mode,
                    imageBitmap = imageBitmap,
                    animationUri = animationUri,
                    crop = displayCrop,
                    onTap = if (canCrop) enterEdit else null,
                )
                if (canCrop) LinkButton("Adjust crop →") { enterEdit() }
                LinkButton("Preview full screen →") { previewFullScreen() }
            }
        }

        Spacer(Modifier.height(BETWEEN_SECTIONS.dp))

        // ── The image / animation to show. Colour needs nothing here — its background *is* the splash.
        if (mode == "IMAGE" || mode == "ANIMATION") {
            Spacer(Modifier.height(BETWEEN_SECTIONS.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp)) {
                if (mode == "IMAGE") {
                    SettingBlock(
                        name = "Image",
                        help = if (imageUri.isNotEmpty()) "Tap to choose a different image." else "No image chosen yet.",
                        control = {
                            LinkButton(if (imageUri.isNotEmpty()) "Change image →" else "Choose image →") {
                                imagePicker.launch("image/*")
                            }
                        },
                    )
                } else {
                    SettingBlock(
                        name = "Animation",
                        help = if (animationUri.isNotEmpty()) "Tap to choose a different animation (GIF or animated WebP)."
                        else "No animation chosen yet — pick a GIF or animated WebP.",
                        control = {
                            LinkButton(if (animationUri.isNotEmpty()) "Change animation →" else "Choose animation →") {
                                animationPicker.launch("image/*")
                            }
                        },
                    )
                }
            }
        }

        // ── Timing (colour + image only; an animation has no dwell) ───────────────────────────────
        if (mode != "ANIMATION") {
            Spacer(Modifier.height(BETWEEN_SECTIONS.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WITHIN_SECTION.dp)) {
                SettingsContentHeader(title = "Timing")
                SettingBlock(
                    name = "Display time",
                    help = "How long the splash holds before it clears. The fades are set in Transitions.",
                    control = {
                        Stepper(
                            value = formatSeconds(dwell),
                            sub = "hold",
                            onMinus = {
                                scope.launch { prefs.saveSplashDwellMillis((dwell - DWELL_STEP_MS).coerceAtLeast(DWELL_MIN_MS)) }
                            },
                            onPlus = {
                                scope.launch { prefs.saveSplashDwellMillis((dwell + DWELL_STEP_MS).coerceAtMost(DWELL_MAX_MS)) }
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * The preview, drawn in the **current screen's aspect ratio** so it honestly represents what boots —
 * a tall box in portrait, a wide one in landscape (it reflows on rotation). Fitted within a sensible
 * height so a portrait phone doesn't hand back a full-screen-tall preview.
 */
@Composable
private fun SplashAspectPreview(
    aspect: Float,
    colour: Color,
    mode: String,
    imageBitmap: ImageBitmap?,
    animationUri: String,
    crop: SplashCrop,
    maxHeight: Dp = 240.dp,
    onTap: (() -> Unit)? = null,
) {
    val theme = LocalDashTheme.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val byHeight = maxHeight * aspect
        val w = if (byHeight <= maxWidth) byHeight else maxWidth
        val h = w / aspect
        Box(
            Modifier
                .width(w)
                .height(h)
                .clip(RoundedCornerShape(8.dp))
                .background(colour)
                .border(1.dp, theme.textColourSecondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier),
        ) {
            when {
                mode == "IMAGE" && imageBitmap != null ->
                    CroppedImage(imageBitmap, Modifier.fillMaxSize()) { crop }

                mode == "ANIMATION" && animationUri.isNotEmpty() ->
                    SplashImage(animationUri, animate = true, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** The preset swatches under the colour picker — theme tokens plus black and white, tap to seed. */
@Composable
private fun ColourSwatches(
    presets: List<Pair<String, Color>>,
    selected: Color,
    onPick: (Color) -> Unit,
) {
    val theme = LocalDashTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.forEach { (_, colour) ->
            val isSelected = colour.toArgbLong() == selected.toArgbLong()
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colour)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) theme.textColourSecondary else theme.textColourSecondary.copy(alpha = 0.3f),
                        shape = CircleShape,
                    )
                    .clickable { onPick(colour) },
            )
        }
    }
}

/** "0.0s", "2.5s" — dwell shown in seconds to one decimal, from a millisecond store. */
private fun formatSeconds(millis: Int): String {
    val whole = millis / 1000
    val tenths = (millis % 1000) / 100
    return "$whole.${tenths}s"
}
