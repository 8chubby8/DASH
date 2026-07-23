package com.dash.android.ui.settings.content

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.dash.android.prefs.DashPreferences
import com.dash.android.ui.theme.LocalDashTheme
import com.dash.android.weather.WeatherProvider
import kotlinx.coroutines.launch

/**
 * System › Location (roadmap 1.5.4). The two controls that let a user decide *how DASH knows where
 * they are* for the weather scene — and, later, anything else that wants a fix.
 *
 * - **Use device location** — the opt-in that gates the GPS rung of the cascade. DASH never asks for
 *   location on its own (the no-nag rule); this toggle *is* the ask, and only when the user reaches
 *   for it. Off, the scene falls back to keyless IP geolocation, which needs no permission.
 * - **Manual location** — a place the user types, resolved once via the geocoder and pinned at the
 *   very top of the cascade, overriding GPS and IP both.
 *
 * Nothing here is required: with everything untouched the scene still works from IP, and offline it
 * still works from the clock. These controls only ever sharpen it.
 */
@Composable
fun LocationContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { DashPreferences(context) }
    val provider = remember { WeatherProvider(context) }
    val manual by prefs.manualLocation.collectAsState(initial = null)

    var granted by remember { mutableStateOf(hasCoarseLocation(context)) }
    // The user may grant or revoke the permission in Android's own settings while this screen is
    // still open (the settings shell survives the trip), so re-read it whenever we resume.
    val owner = context as? LifecycleOwner
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasCoarseLocation(context)
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it || hasCoarseLocation(context) }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsContentHeader(
            "Location",
            "How DASH works out where you are, for the weather scene. DASH never asks for your " +
                "location on its own — everything here is yours to switch on.",
        )

        SettingBlock(
            name = "Use device location",
            help = if (granted) {
                "On. DASH reads your device's location for a precise, local forecast."
            } else {
                "Off. DASH uses an approximate location from your internet connection, which can be " +
                    "a town or two out. Turn on to use your device's GPS instead."
            },
            control = {
                SettingToggle(checked = granted) {
                    // Android alone can revoke a granted permission, so send the user there for that;
                    // otherwise raise the system grant dialog.
                    if (granted) openAppSettings(context)
                    else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
        )

        ManualLocationBlock(
            current = manual?.name,
            onResolve = { query, onResult ->
                scope.launch {
                    val found = provider.geocodeCity(query)
                    if (found != null) prefs.saveManualLocation(found)
                    onResult(found != null)
                }
            },
            onClear = { scope.launch { prefs.clearManualLocation() } },
        )
    }
}

@Composable
private fun ManualLocationBlock(
    current: String?,
    onResolve: (String, (Boolean) -> Unit) -> Unit,
    onClear: () -> Unit,
) {
    val theme = LocalDashTheme.current
    var text by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }

    SettingBlock(
        name = "Manual location",
        help = "Pin the weather to a place you choose. When set it overrides both device and " +
            "internet location. Leave it on automatic to let DASH decide.",
        control = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CityField(
                    value = text,
                    onValueChange = { text = it; notFound = false },
                    onSubmit = {
                        val query = text.trim()
                        if (query.isNotEmpty()) {
                            searching = true
                            notFound = false
                            onResolve(query) { ok ->
                                searching = false
                                notFound = !ok
                                if (ok) text = ""
                            }
                        }
                    },
                )
                val status = when {
                    searching -> "Searching…"
                    notFound -> "No place found by that name."
                    current != null -> "Set to $current"
                    else -> "Automatic (device or internet)"
                }
                Text(
                    status,
                    color = theme.textColourSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = theme.font,
                )
                if (current != null) LinkButton("Use automatic") { onClear() }
            }
        },
    )
}

@Composable
private fun CityField(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit) {
    val theme = LocalDashTheme.current
    Row(
        modifier = Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.textColourSecondary.copy(alpha = 0.08f))
            .border(1.dp, theme.textColourSecondary.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    "Town or city",
                    color = theme.textColourSecondary.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    fontFamily = theme.font,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = theme.textColourSecondary,
                    fontSize = 14.sp,
                    fontFamily = theme.font,
                ),
                cursorBrush = SolidColor(theme.textColourSecondary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Set",
            color = theme.textColourSecondary,
            fontSize = 13.sp,
            fontFamily = theme.font,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onSubmit() }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun hasCoarseLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
