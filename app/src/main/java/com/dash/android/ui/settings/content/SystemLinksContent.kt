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
        SettingsContentHeader(
            "Android Settings Links",
            "DASH replaces the home screen, so these are the way back to Android's own settings. " +
                "Each one opens the exact page, not the top of the menu.",
        )

        if (sections.isEmpty()) {
            Text(
                "This device exposes none of Android's settings screens to other apps. Nothing here " +
                    "would work, so nothing is listed.",
                color = theme.textColourSecondary.copy(alpha = 0.7f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = theme.font,
            )
            return@Column
        }

        sections.forEach { (title, links) ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsContentHeader(title)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    links.forEach { (link, intent) ->
                        LinkTile(link.label, link.help) {
                            runCatching { context.startActivity(intent) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A whole row that opens something — the item *is* the control.
 *
 * There is no OPEN button on the right because there is nothing else this row could do: every row
 * here has exactly one action, so a separate button would only be a smaller target for the same tap.
 * It also suits the car — a row spanning the box is hit reliably on a moving screen where a short
 * word at the far edge is not.
 *
 * The chevron is the only affordance, deliberately quiet: it says the row leads somewhere without
 * dressing itself up as a button.
 */
@Composable
private fun LinkTile(label: String, help: String, onClick: () -> Unit) {
    val theme = LocalDashTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(label, color = theme.textColourSecondary, fontSize = 14.sp, fontFamily = theme.font)
            Text(
                help,
                color = theme.textColourSecondary.copy(alpha = 0.68f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = theme.font,
            )
        }
        Chevron()
    }
}

/** A link out: what it is, why you would want it, and the intents that might open it — the first that
 *  this device can actually handle wins. */
private class SystemLink(
    val label: String,
    val help: String,
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
            "Join a network, or fix one that has dropped. DASH uses WiFi for weather, for its own " +
                "WiFi transport, and for anything running in the viewport.",
            action(Settings.ACTION_WIFI_SETTINGS),
        ),
        SystemLink(
            "Bluetooth",
            "Pair and manage devices — including any DASH module on the Bluetooth transport. " +
                "Pairing is Android's job, so DASH sends you here rather than rebuilding it.",
            action(Settings.ACTION_BLUETOOTH_SETTINGS),
        ),
        SystemLink(
            "Mobile network",
            "Data, roaming and carrier settings, for an installation with a SIM or a mobile dongle.",
            { listOf(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
        ),
    ),
    "Display and sound" to listOf(
        SystemLink(
            "Display",
            "Brightness, timeout and Android's own display options. DASH's own scale controls are " +
                "under Appearance › Size & Scale.",
            action(Settings.ACTION_DISPLAY_SETTINGS),
        ),
        SystemLink(
            "Sound",
            "Volumes and output behaviour. DASH's audio routing arrives in version 2.",
            action(Settings.ACTION_SOUND_SETTINGS),
        ),
    ),
    "Device" to listOf(
        SystemLink(
            "Apps",
            "Every app installed on this device, and their permissions.",
            { listOf(Intent(Settings.ACTION_APPLICATION_SETTINGS), Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) },
        ),
        SystemLink(
            "Storage",
            "What is using the space on this device.",
            action(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        ),
        SystemLink(
            "Date and time",
            "The clock DASH reads. Worth checking on a board with no SIM and no network time.",
            action(Settings.ACTION_DATE_SETTINGS),
        ),
        SystemLink(
            "Location",
            "Android's master location switch. DASH's own location sources are under System › Location.",
            action(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        ),
        SystemLink(
            "Accessibility",
            "Android's accessibility services and display adjustments.",
            action(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        ),
        SystemLink(
            "Keyboards",
            "The input methods available when DASH asks you to type.",
            action(Settings.ACTION_INPUT_METHOD_SETTINGS),
        ),
        SystemLink(
            "Developer options",
            "Android's developer settings, where they are enabled on this device.",
            action(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        ),
    ),
    "DASH on this device" to listOf(
        SystemLink(
            "Default home app",
            "Choose which app is the home screen. This is how you make DASH the launcher — and how " +
                "you hand it back.",
            { listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
        ),
        SystemLink(
            "DASH app info",
            "Android's own page for DASH — permissions, storage and notifications.",
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
            "The top of Android's own settings, for anything not listed here.",
            action(Settings.ACTION_SETTINGS),
        ),
    ),
)
