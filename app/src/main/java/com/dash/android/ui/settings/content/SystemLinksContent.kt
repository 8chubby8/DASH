package com.dash.android.ui.settings.content

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.ui.theme.LocalDashTheme
import com.dash.android.ui.common.MAINBODY
import com.dash.android.ui.common.BODY
import com.dash.android.ui.common.MAINBODY_LINE
import com.dash.android.ui.common.BODY_LINE
import com.dash.android.ui.common.DashButton

/**
 * System › Android Settings Links (roadmap 1.5.14).
 *
 * **Why this tab exists.** DASH is the home screen. Once it is the default launcher there is no app
 * drawer, no notification shade worth using at the wheel, and on a dedicated head unit quite possibly
 * no status bar at all — so Android's own Settings app is installed, running, and unreachable. If the
 * WiFi drops in a car running DASH, without this tab there is no way to fix it from inside DASH.
 *
 * **DASH links out, it does not reimplement.** Changing a WiFi network, pairing a device or setting
 * the clock needs privileges a Bronze sideload does not have, and rebuilding Android's screens would
 * be scope creep of the worst kind — a worse copy of something already on the device. The pattern was
 * already here in three places before this tab collected it: the display-size link behind App Density
 * (1.5.3), the permission link on Location (1.5.4), and Bluetooth pairing, which 1.5.10 decided is
 * Android's screen to own.
 *
 * **Every row is capability-detected.** Android settings screens are not uniform across builds — a
 * stripped board image may have no accessibility page, no developer options, no storage screen. Each
 * link resolves its intent first, falls back to an alternate action where one exists, and is simply
 * **not listed** when nothing on the device can handle it. A row that is present always works; there
 * are no buttons here that do nothing.
 */
@Composable
fun SystemLinksContent() {
    val theme = LocalDashTheme.current
    val context = LocalContext.current
    val sections = remember(context) { resolveSections(context) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        SettingsContentHeader("Android Settings Links")

        if (sections.isEmpty()) {
            Text(
                "This device exposes none of Android's settings screens to other apps. Nothing here " +
                    "would work, so nothing is listed.",
                color = theme.textColourSecondary.copy(alpha = 0.7f),
                fontSize = MAINBODY,
                lineHeight = MAINBODY_LINE,
                fontFamily = theme.font,
            )
            return@Column
        }

        sections.forEach { (title, links) ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsSectionHeader(title)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    links.forEach { (link, intent) ->
                        DashButton(
                            label = link.label,
                            modifier = Modifier.fillMaxWidth(),
                            alignStart = true,
                            onClick = { runCatching { context.startActivity(intent) } },
                        )
                    }
                }
            }
        }
    }
}


/** A link out: what it is, why you would want it, and the intents that might open it — the first that
 *  this device can actually handle wins. */
private class SystemLink(
    val label: String,
    val intents: (Context) -> List<Intent>,
)

private fun action(name: String): (Context) -> List<Intent> = { listOf(Intent(name)) }

/** Resolves every link against this device, dropping the ones nothing can open and then the sections
 *  left empty by that. Done once per visit — the set of installed settings screens does not change
 *  while DASH is open. */
private fun resolveSections(context: Context): List<Pair<String, List<Pair<SystemLink, Intent>>>> =
    SECTIONS.mapNotNull { (title, links) ->
        val resolved = links.mapNotNull { link ->
            link.intents(context)
                .asSequence()
                .map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                .firstOrNull {
                    context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null
                }
                ?.let { link to it }
        }
        if (resolved.isEmpty()) null else title to resolved
    }

private val SECTIONS: List<Pair<String, List<SystemLink>>> = listOf(
    "Connections" to listOf(
        SystemLink(
            "WiFi",
            action(Settings.ACTION_WIFI_SETTINGS),
        ),
        SystemLink(
            "Bluetooth",
            action(Settings.ACTION_BLUETOOTH_SETTINGS),
        ),
        SystemLink(
            "Mobile network",
            { listOf(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
        ),
    ),
    "Display and sound" to listOf(
        SystemLink(
            "Display",
            action(Settings.ACTION_DISPLAY_SETTINGS),
        ),
        SystemLink(
            "Sound",
            action(Settings.ACTION_SOUND_SETTINGS),
        ),
    ),
    "Device" to listOf(
        SystemLink(
            "Apps",
            { listOf(Intent(Settings.ACTION_APPLICATION_SETTINGS), Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) },
        ),
        SystemLink(
            "Storage",
            action(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        ),
        SystemLink(
            "Date and time",
            action(Settings.ACTION_DATE_SETTINGS),
        ),
        SystemLink(
            "Location",
            action(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        ),
        SystemLink(
            "Accessibility",
            action(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        ),
        SystemLink(
            "Keyboards",
            action(Settings.ACTION_INPUT_METHOD_SETTINGS),
        ),
        SystemLink(
            "Developer options",
            action(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        ),
    ),
    "DASH on this device" to listOf(
        SystemLink(
            "Default home app",
            { listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
        ),
        SystemLink(
            "DASH app info",
            { context ->
                listOf(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            },
        ),
        SystemLink(
            "All Android settings",
            action(Settings.ACTION_SETTINGS),
        ),
    ),
)
