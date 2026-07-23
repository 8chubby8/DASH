package com.dash.android.ui.splash

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The default splash dwell — how long a colour/image splash holds between its two fades, in ms. */
const val SPLASH_DWELL_DEFAULT_MS = 2500

// Even in animation mode, never let the splash outstay this. If the play-once end callback never
// arrives (a decode oddity, a device that won't fire it), this releases the splash regardless.
private const val ANIMATION_SAFETY_CAP_MS = 20_000L

/**
 * A request to play the splash full-screen as a preview. Provided at the composition root by
 * [com.dash.android.ui.screen.MainScreen]; the Splash settings tab calls it so "Preview full screen"
 * plays the real splash — real dwell, real fades — over the top of the settings panel. A
 * CompositionLocal keeps the settings-content dispatcher signature untouched (one line per tab).
 */
val LocalSplashPreview = compositionLocalOf<() -> Unit> { {} }

@Composable
fun SplashScreen(
    mode: String,
    backgroundColour: Long,
    imageUri: String,
    animationUri: String,
    imageCropPortrait: SplashCrop,
    imageCropLandscape: SplashCrop,
    dwellMillis: Int,
    fadeInMillis: Int,
    fadeOutMillis: Int,
    onDismiss: () -> Unit
) {
    val colour = Color(backgroundColour)
    val scope = rememberCoroutineScope()

    // Start hidden so the first frame fades *in* rather than snapping on.
    var visible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }

    // Animation mode plays through once then clears — but only where it can actually play (API 28+
    // with a chosen file). Everywhere else (colour, image, or animation on old hardware showing a
    // static first frame) the dwell timer governs, so the splash always releases.
    val canPlay = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val playOnceAnimation = mode == "ANIMATION" && canPlay && animationUri.isNotEmpty()

    fun beginDismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            visible = false
            delay(fadeOutMillis.toLong())
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
        if (playOnceAnimation) {
            // Play-once dismissal comes from the animation-end callback; this is only the safety net.
            delay(ANIMATION_SAFETY_CAP_MS)
            beginDismiss()
        } else {
            delay(dwellMillis.toLong())
            beginDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(fadeInMillis)),
        exit = fadeOut(animationSpec = tween(fadeOutMillis)),
        modifier = Modifier.zIndex(100f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colour)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { beginDismiss() }
        ) {
            // The background colour sits behind the artwork as the matte, so a letterboxed image (or a
            // decode failure) lands on the user's chosen colour rather than void.
            when {
                mode == "IMAGE" && imageUri.isNotEmpty() -> {
                    // A still image honours the per-orientation crop, chosen by the real screen shape.
                    val bitmap = rememberSplashBitmap(imageUri)
                    if (bitmap != null) {
                        CroppedImage(bitmap, Modifier.fillMaxSize()) { landscape ->
                            if (landscape) imageCropLandscape else imageCropPortrait
                        }
                    }
                }

                mode == "ANIMATION" && animationUri.isNotEmpty() -> {
                    // Animation centre-crops for now (crop editing is still-images first).
                    SplashImage(
                        uriString = animationUri,
                        animate = true,
                        modifier = Modifier.fillMaxSize(),
                        playThroughOnce = playOnceAnimation,
                        onFinished = { beginDismiss() },
                    )
                }
            }
        }
    }
}

/**
 * Renders a user-chosen splash image, filling its box. When [animate] is true an animated image (GIF
 * or animated WebP) **plays**; when false it is frozen to its first frame (the still-image path). On
 * API 28+ the platform [ImageDecoder] decodes an [AnimatedImageDrawable]; on API 24–27 there is no
 * content-URI decode path, so the image is always a static first frame via [BitmapFactory] — the
 * graceful degradation the no-special-cases rule wants. (True video is out of scope for 1.5.6 — it
 * needs a media player, not a drawable.)
 *
 * [playThroughOnce] makes an animation play a single pass and then call [onFinished] — how the boot
 * splash times itself off its own animation. Left false (the preview) it loops.
 */
@Composable
fun SplashImage(
    uriString: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    playThroughOnce: Boolean = false,
    onFinished: (() -> Unit)? = null,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        AnimatedSplashImage(uriString, animate, modifier, playThroughOnce, onFinished)
    } else {
        val context = LocalContext.current
        val bitmap = remember(uriString) { loadBitmap(context, uriString) }
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun AnimatedSplashImage(
    uriString: String,
    animate: Boolean,
    modifier: Modifier,
    playThroughOnce: Boolean,
    onFinished: (() -> Unit)?,
) {
    val context = LocalContext.current
    val drawable = remember(uriString) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
            ImageDecoder.decodeDrawable(source)
        }.getOrNull()
    }
    val finished = rememberUpdatedState(onFinished)

    // Own the playback in an effect (not the view's update pass, which reruns on every recomposition)
    // so start/stop and the end callback register exactly once per drawable.
    DisposableEffect(drawable, animate, playThroughOnce) {
        val anim = drawable as? AnimatedImageDrawable
        var callback: Animatable2.AnimationCallback? = null
        if (anim != null && animate) {
            if (playThroughOnce) {
                anim.repeatCount = 0
                callback = object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(d: Drawable?) { finished.value?.invoke() }
                }
                anim.registerAnimationCallback(callback)
            } else {
                anim.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            }
            anim.start()
        }
        onDispose {
            if (anim != null) {
                anim.stop()
                callback?.let { anim.unregisterAnimationCallback(it) }
            }
        }
    }

    if (drawable != null) {
        AndroidView(
            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
            modifier = modifier,
            update = { view -> view.setImageDrawable(drawable) }
        )
    }
}

private fun loadBitmap(context: Context, uriString: String): ImageBitmap? {
    return runCatching {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()
}
