package com.dash.android.ui.settings

/**
 * The DASH settings tree, declared as data (roadmap 1.5.2). The navigation shell renders whatever
 * this describes; later 1.5.x versions fill a subcategory's content by wiring a real control to its
 * id — they never touch the shell or the navigation again. This is the reconciled structure from
 * interface.md's 2026-07-20 addendum: the ten top-level categories with Layout lifted out of
 * Appearance.
 *
 * Every subcategory carries a [SettingsStatus]. In 1.5.2 nothing is wired yet, so the shell shows
 * each one as an honest placeholder; [wipVersion] is the version its feature lights up at (the
 * WIP-placeholder convention). As features are rehomed, [SettingsStatus.LIVE] entries gain real
 * content.
 */
enum class SettingsStatus { LIVE, WIP }

data class SettingsSub(
    val id: String,
    val label: String,
    val status: SettingsStatus = SettingsStatus.WIP,
    val wipVersion: String? = null,
    // A tab that manages its own scrolling fills the whole content box instead of being wrapped in the
    // shell's outer vertical scroll — a pinned control region with a scrollable body beneath it (1.5.8:
    // Module Management pins its header + REFRESH and scrolls only the card list; the Developer
    // instruments at 1.5.11 will want the same shape).
    val fillsBox: Boolean = false,
)

data class SettingsCategory(
    val id: String,
    val label: String,
    val subs: List<SettingsSub>,
)

private fun wip(id: String, label: String, version: String) =
    SettingsSub(id, label, SettingsStatus.WIP, version)

/**
 * The reconciled tree. Kept deliberately representative rather than exhaustive at every leaf —
 * enough that the shell navigates the full shape. Version labels track roadmap.md's 1.5.x plan and
 * the v2/v3 eras.
 */
val DASH_SETTINGS_TREE: List<SettingsCategory> = listOf(
    SettingsCategory(
        "appearance", "Appearance", listOf(
            SettingsSub("appearance.density", "Size & Scale", SettingsStatus.LIVE),
            SettingsSub("appearance.transitions", "Transitions", SettingsStatus.LIVE),
            SettingsSub("appearance.splash", "Splash Screen", SettingsStatus.LIVE),
            wip("appearance.colours", "Colours", "v2"),
            wip("appearance.fonts", "Fonts", "v2"),
            wip("appearance.presets", "Presets", "v2"),
            wip("appearance.ambient", "Ambient Mode", "v2"),
        )
    ),
    SettingsCategory(
        "layout", "Layout", listOf(
            SettingsSub("layout.systembar", "System Bar", SettingsStatus.LIVE),
            wip("layout.modulepanel", "Module Panel", "1.6.x"),
            wip("layout.launcher", "App Launcher", "1.8.x"),
            wip("layout.elements", "Elements", "1.9.x"),
            wip("layout.overlays", "Overlays", "v2"),
        )
    ),
    SettingsCategory(
        "modules", "Modules", listOf(
            SettingsSub("modules.management", "Module Management", SettingsStatus.LIVE, fillsBox = true),
            wip("modules.enable", "Enable / Disable", "1.5.7"),
        )
    ),
    SettingsCategory(
        "transports", "Transports", listOf(
            wip("transports.list", "Transport List", "1.5.8"),
        )
    ),
    SettingsCategory(
        "vehicle", "Vehicle", listOf(
            wip("vehicle.patchbay", "CAN Patch Bay", "v3"),
            wip("vehicle.obd2", "OBD2", "v3"),
            wip("vehicle.slots", "Signal Slots", "v3"),
            wip("vehicle.dbc", "DBC Profiles", "v3"),
            wip("vehicle.profile", "Vehicle Profile", "v3"),
        )
    ),
    SettingsCategory(
        "audio", "Audio", listOf(
            wip("audio.output", "Output Selection", "v2"),
            wip("audio.routing", "Audio Routing", "v2"),
            wip("audio.volume", "Volume Behaviour", "v2"),
            wip("audio.perapp", "Per-app Audio", "v2"),
        )
    ),
    SettingsCategory(
        "notifications", "Notifications", listOf(
            wip("notifications.suppression", "Notification Suppression", "v2"),
            wip("notifications.overlays", "Overlay Trigger Mapping", "v2"),
            wip("notifications.perapp", "Per-app Management", "v2"),
            wip("notifications.durations", "Durations", "v2"),
            wip("notifications.driving", "Driving-mode Rules", "v2"),
            wip("notifications.history", "History", "v2"),
        )
    ),
    SettingsCategory(
        "apps", "Apps", listOf(
            wip("apps.installed", "Installed Apps", "v2"),
            wip("apps.defaults", "Default Assignments", "v2"),
            wip("apps.permissions", "Permissions", "v2"),
            wip("apps.storage", "Storage", "v3"),
        )
    ),
    SettingsCategory(
        "system", "System", listOf(
            SettingsSub("system.location", "Location", SettingsStatus.LIVE),
            wip("system.deeplinks", "Android Settings Links", "1.5.11"),
            wip("system.about", "About DASH", "1.5.11"),
        )
    ),
    SettingsCategory(
        "developer", "Developer", listOf(
            wip("developer.serial", "Serial Monitor", "1.5.9"),
            wip("developer.signal", "Signal Monitor", "1.5.9"),
            wip("developer.diagnostics", "Transport Diagnostics", "1.5.10"),
            wip("developer.logs", "Log Viewer", "1.5.10"),
        )
    ),
)
