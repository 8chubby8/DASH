package com.dash.android.ui.splash

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

// How long the splash holds fully visible between the fade in and the fade out. This dwell is a
// Splash-screen concern (its own control arrives with the Splash tab, roadmap 1.5.6) — the two fades
// either side of it are transitions and are controlled from Appearance › Transitions.
private const val SPLASH_DURATION_MS = 2500L

@Composable
fun SplashScreen(
    mode: String,
    colour: Long,
    imageUri: String,
    fadeInMillis: Int,
    fadeOutMillis: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Start hidden so the first frame fades *in* rather than snapping on. A tap short-circuits to the
    // fade out at any point.
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(SPLASH_DURATION_MS)
        visible = false
        delay(fadeOutMillis.toLong())
        onDismiss()
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    visible = false
                }
        ) {
            if (mode == "IMAGE" && imageUri.isNotEmpty()) {
                val bitmap = remember(imageUri) { loadBitmap(context, imageUri) }
                if (bitmap != null) {
                    Image(
                        painter = BitmapPainter(bitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(colour)))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(colour)))
            }
        }
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
