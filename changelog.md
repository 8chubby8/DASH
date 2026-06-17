# DASH — Changelog

---

## Purpose of This Document

This document records the development history of DASH version by version. For every version increment it captures what was implemented, what broke as a result, what was done to fix it, and what remains outstanding. It is the honest record of how DASH was actually built — including the mistakes, regressions, and lessons learned.

This document is read alongside roadmap.md. Roadmap.md is the plan. Changelog.md is the reality.

**A version number is not complete until a changelog entry exists for it.**

---

## Entry Format

Each version entry follows this structure:

```
## Version X.x.x

**Status:** In Progress / Complete

**Implemented:**
- What was built in this version

**Regressions:**
- What broke as a result of this version's changes
- Which previously working feature was affected and how

**Fixes:**
- What was done to resolve each regression
- Whether the fix is complete or still being worked on

**Outstanding:**
- Known issues not yet resolved
- Carries forward to next version if unresolved

**Notes:**
- Observations, lessons learned, decisions made during this version
- Anything useful for future development sessions to know
```

---

## Version History

---

## Version 1.3.3

**Status:** Complete

**Implemented:**
- Theme token system — `DashColors` data class carrying three named colour tokens: `barBackground`, `barAccent`, `barText`. Exposed via `LocalDashTheme`, a `compositionLocalOf` that any composable in the DASH tree can read without being wired through function parameters
- `DashColors.dark()` factory provides the default token set, carrying the colour values previously hardcoded across three files. Visually identical to 1.3.2 — the change is architectural only
- `MainScreen` provides `LocalDashTheme` at the top of the composition tree alongside the existing `LocalDashScale` provider. Version 2 introduces user-facing presets and theme switching by providing a different `DashColors` instance here — no component below changes
- `SystemBar` — hardcoded `BAR_COLOR` private val removed; bar background now reads `LocalDashTheme.current.barBackground`
- `AlertsAreaElement` — pill background reads `LocalDashTheme.current.barAccent`; ALERTS label reads `LocalDashTheme.current.barText.copy(alpha = 0.55f)`. Subdued text is a semantic derivation of `barText`, not a separate token
- `SettingsButtonElement` — gear icon reads `LocalDashTheme.current.barText`
- New file `ui/theme/DashTheme.kt` — `DashColors`, `LocalDashTheme`. Clean home for all theme infrastructure as the token set grows

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- Element percentage-of-bar-height sizing, size variants (S/M/L), soft-limit amber warnings → 1.3.4
- Zone splitting (up to three), inter-element snap packing, Spacer element → 1.3.5
- Drag-and-drop edit mode → 1.3.6

**Notes:**
- `compositionLocalOf` chosen over `staticCompositionLocalOf` — the former only recomposes readers when the value changes, which is the correct behaviour for version 2 live theme switching. Static would recompose the entire subtree
- Adding a token in future versions is one field with a default value on `DashColors`. All existing call sites continue to compile unchanged. Stored presets that predate a new token will decode fine via the existing `ignoreUnknownKeys = true` JSON config

---

## Version 1.3.2

**Status:** Complete

**Implemented:**
- Bar height is now controlled by the dp setting alone. `SystemBar` previously computed `barHeight = config.heightDp.dp * LocalDashScale.current`, meaning both the dp height control in System Bar settings and the DASH Scale +/- control affected bar size simultaneously. The scale multiplier has been removed — `barHeight = config.heightDp.dp`. The DASH Scale setting remains in the settings panel and its value is still persisted; it simply has no wired consumer at this stage
- Unused `LocalDashScale` import removed from `SystemBar.kt`

**Regressions:**
- None

**Fixes:**
- Two controls affecting bar height — the dp height setting and the DASH Scale multiplier were both acting on bar size. Removing the multiplier from bar height calculation resolves the conflict

**Outstanding:**
- Theme token system → 1.3.3
- Element percentage-of-bar-height sizing, size variants (S/M/L), soft-limit amber warnings → 1.3.4
- Zone splitting (up to three), inter-element snap packing, Spacer element → 1.3.5
- Drag-and-drop edit mode → 1.3.6

**Notes:**
- DASH Scale is intentionally parked rather than removed. It does not have a clear job while the bar is the only chrome element. It will be reintroduced as a multiplier across all chrome elements once there is more than one thing for it to scale uniformly
- The DASH Scale section remains visible in settings — it is not hidden or greyed out. Its persistence and the +/- controls still work; the value simply has no active consumer until it is properly wired back in a later version

---

## Version 1.3.1

**Status:** Complete

**Implemented:**
- System bar reworked from a placeholder coloured `Box` into a real, configurable, persistent interface element — `ui/systembar/SystemBar.kt`. Renders at top or bottom per config; height is the user-defined base (`SystemBarConfig.heightDp`) multiplied by the live DASH UI scale, so it stays consistent with the rest of DASH chrome
- Persisted data model — `ui/systembar/SystemBarModel.kt`. `@Serializable` `SystemBarConfig` → `ZoneConfig` → `ElementPlacement`, with `BarPosition`, `ElementType`, `ElementAnchor`, `SizeVariant`, `ElementKind` enums. Deliberately shaped now to hold the full 1.3.x feature set (multiple zones, per-element anchors and variants) so the stored format never needs migrating as later increments land. `SystemBarConfig.default()` guarantees the two mandatory elements are always present
- Persistence from the first commit — bar config stored as JSON in the existing DataStore via `kotlinx.serialization` (`Json { ignoreUnknownKeys = true; encodeDefaults = true }`). `DashPreferences` gains `systemBarConfig` flow, `saveSystemBarConfig()`, and `resetSystemBar()`. Decode is wrapped in `runCatching` with a fallback to `default()`, so an absent, corrupt, or schema-changed config never crashes — it cleanly returns to default
- Two mandatory elements built — `AlertsAreaElement` (informational, placeholder visual until transport lands in 1.4.x) and `SettingsButtonElement` (interactive). The settings button enforces a hard 48dp minimum touch target via `Modifier.sizeIn`, regardless of bar height, exactly as interface.md mandates — the visible glyph may shrink, the touch area never does
- SDKable element framework — `DashElement` interface, `ElementScope`, `DashAction`, and `ElementRegistry`. Built-in elements receive nothing a community element could not; this is the seed of the v3 Element SDK. Interactive elements reach the platform only through the narrow `DashAction` channel (currently just `OpenSettings`)
- Settings relocated behind the settings button — `ui/settings/SettingsPanel.kt`. The debug controls that previously occupied the centre of the main screen (App Density, Rotation, DASH Scale, Splash Screen, Launcher, Exit) now live in a full-screen scrollable panel opened only from the bar's settings button. This is the intended model: the settings button is the one route into settings
- New System Bar settings section — position toggle (TOP/BOTTOM), height −/+ (40–120dp in 4dp steps), and a "Reset bar layout" button guarded by a confirmation dialog that returns the bar to its default (bottom, 56dp, alerts + settings)
- Not-default-launcher banner and diagnostic overlay now position themselves opposite the bar so they never collide with it when the bar is moved to the top

**Regressions:**
- None observed in build. On-device verification pending (see Outstanding)

**Fixes:**
- None

**Outstanding:**
- Scale/height conflict in the bar — DASH Scale multiplier applied to bar height alongside the dp control, giving two controls that both affect bar size → 1.3.2
- Element percentage-of-bar-height sizing, size variants (S/M/L), and soft-limit amber warnings → 1.3.4
- Zone splitting (up to three), inter-element snap packing, and the Spacer element → 1.3.5
- Drag-and-drop edit mode (long-press pickup, snap guidelines, draggable zone dividers) → 1.3.6
- Theme token system — named colour tokens (barBackground, barAccent, barText) as the single source of truth for system bar and element colours; prerequisite for settings panel visual identity inheritance in 1.5.x → 1.3.3
- On-device confirmation on the Pixel 8 / tablet: bar renders, settings button opens panel, position and height persist across an app kill, reset works

**Notes:**
- `kotlinx.serialization` added (plugin + `kotlinx-serialization-json` 1.7.3) — first use of JSON persistence in DASH. Flat preference keys don't map cleanly to a nested bar config, so JSON-in-DataStore is the right tool here; the existing flat keys (density, scale, splash, etc.) are untouched
- Version code bumped to 3, versionName to 1.3.1. The build file had been sitting at 1.2.0/2, one behind the 1.2.1 changelog entry — now reconciled
- Bar element rendering currently uses a single full-width zone with anchor-based placement (left/centre/right). The `SystemBar` composable already iterates zones with weighted widths, so multi-zone in 1.3.3 is an extension rather than a rewrite

---

## Version 1.2.1

**Status:** Complete

**Implemented:**
- Splash screen now triggers on screen wake as well as cold boot — when the tablet wakes from sleep and DASH is the default launcher, the splash appears on top of DASH after the lock screen is dismissed
- `pendingWakeSplash` flag added to MainActivity — a dynamic `BroadcastReceiver` for `ACTION_SCREEN_ON` sets the flag when the screen wakes (conditional on DASH being the default launcher). The flag is consumed in `ON_RESUME`, which triggers the splash at the point DASH actually becomes visible rather than when the screen first turns on. This ensures the 2.5s timer starts after the lock screen is dismissed, not before
- Receiver registered with `ContextCompat.registerReceiver` and `RECEIVER_NOT_EXPORTED` — correct API 33+ handling. `ACTION_SCREEN_ON` cannot be registered in the manifest; dynamic registration is required

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- None

**Notes:**
- The flag approach (rather than directly setting `showSplash = true` from the receiver) is necessary because the lock screen sits between `ACTION_SCREEN_ON` and DASH becoming visible. Triggering the splash directly from the receiver starts the 2.5s timer while the lock screen is still showing — by the time the user unlocks, the splash has already auto-dismissed

---

## Version 1.2.0

**Status:** Complete

**Implemented:**
- Launcher manifest declaration — HOME and DEFAULT_HOME intent filters added to MainActivity. `android:launchMode="singleTask"` and `android:stateNotNeeded="true"` added — standard launcher attributes preventing multiple instances and allowing Android to safely kill launcher state
- BootReceiver — `RECEIVE_BOOT_COMPLETED` permission and `launcher/BootReceiver.kt` registered in manifest. Receives `BOOT_COMPLETED` and launches MainActivity. Foundation for 1.4.x startup reconciliation
- Default launcher prompt — on every `ON_RESUME`, checks whether DASH is the default home app. If not, a purple banner is shown across the top of the screen; tapping it opens the system launcher-selection dialog (API 29+: `RoleManager.createRequestRoleIntent(ROLE_HOME)` — shows a proper "make DASH your home app?" system prompt; API 24–28: opens `Settings.ACTION_HOME_SETTINGS`). Banner disappears automatically once DASH is set as default
- Change launcher escape button — "CHANGE LAUNCHER →" button permanently available in debug settings. Opens `Settings.ACTION_HOME_SETTINGS` (falls back to `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`). Allows switching away from DASH on development hardware without needing ADB
- Splash screen — full-screen overlay shown on cold boot (`savedInstanceState == null`). Two modes: COLOUR (solid fill, three presets: Black, DASH Navy, Dark Slate) and IMAGE (user-selected via system photo picker, URI persisted with `takePersistableUriPermission`). Auto-dismisses after 2.5 seconds with 400ms fade-out; tap also dismisses. "PREVIEW SPLASH" button in debug UI allows testing without rebooting. Image loading uses `ContentResolver` + `BitmapFactory` — no additional dependencies required
- Navigation bar suppression — carried forward unchanged from 1.1.4. `hideSystemBars()` hides both status bar and navigation bar via `WindowInsetsControllerCompat`; `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` provides the transient fallback. Reapplied on every `onWindowFocusChanged`. Confirmed correct behaviour as a launcher

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- None

**Notes:**
- Ignition-driven screen wake/sleep behaviour deliberately deferred — depends on 1.4.x transport layer's system message parsing (`SYSTEM:ignition:on` / `SYSTEM:ignition:off`). Standard Android screen behaviour is fully adequate at this stage: DASH is the home app, so it is what the user sees when the screen wakes. No additional work is needed or appropriate here
- The `CHANGE LAUNCHER →` button is for development use on the Pixel 8. It will remain in whatever settings panel replaces the current debug UI in 1.6.x
- Version code bumped to 2 alongside 1.2.0

---

## Version 1.1.1

**Status:** Complete

**Implemented:**
- Full Android project created from scratch — Kotlin, Jetpack Compose, minSdk 24, package com.dash.android
- App density preset system — Compact (160dpi), Normal (240dpi), Comfortable (320dpi), Large (480dpi) — wired to Android system density via IWindowManager reflection. Requires WRITE_SECURE_SETTINGS permission granted once via ADB during development; production system app gets it natively
- DASH UI scale — fluid +/- system in 0.1x increments (0.5x–2.0x, default 1.0x) — drives DASH chrome size independently of system density
- Placeholder system bar — a horizontal strip at the bottom of the screen whose height responds to DASH scale, proving the scale system works
- Diagnostic overlay — top-left corner readout showing screen resolution, native DPI, selected density preset, and current scale value
- Exit button — resets system density to device default before closing, returning the phone to its normal state
- DASH density isolation — attachBaseContext override using DENSITY_DEVICE_STABLE ensures DASH chrome is immune to system density changes
- DataStore preference persistence — density preset and scale value survive app restarts
- interface.md updated — DASH UI scale changed from named presets to fluid stepwise system to match the actual design intent

**Regressions:**
- None at this stage — first implementation

**Fixes:**
- None required

**Outstanding:**
- Density preset system not functioning on physical hardware (Pixel 8) — buttons have no visible effect despite WRITE_SECURE_SETTINGS being granted via ADB. Root cause unknown at close of this version — carries forward to 1.1.2
- Scale independence verification pending — confirming that DASH scale changes do not affect third party apps

**Notes:**
- The ADB permission grant (`adb shell pm grant com.dash.android android.permission.WRITE_SECURE_SETTINGS`) is a one-time development setup step. Without it the density buttons have no effect — no crash, just silently no-ops
- System density persists while DASH is in the background by design — this is what makes the Spotify test possible
- DASH UI scale uses stepwise +/- rather than named presets — this was clarified with Roger during this session and interface.md was updated accordingly
- The gradlew wrapper was borrowed from a sibling project (AE2_infinitequantumstorage) rather than generated by Android Studio, since no Android Studio new-project wizard was used

---

## Version 1.1.2

**Status:** Complete

**Implemented:**
- Rotation controls added to test environment — AUTO, PORTRAIT, and LANDSCAPE buttons
- AUTO mode uses sensor-driven rotation (default on first launch)
- PORTRAIT and LANDSCAPE buttons lock orientation and disable auto-rotate
- Selection persists via DataStore — rotation state survives app restarts
- Manifest screen orientation constraint removed — orientation now controlled entirely in code via `requestedOrientation`

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- Density preset system still not functioning on physical hardware — carries forward to 1.1.3

**Notes:**
- `LaunchedEffect(autoRotate, lockedOrientation)` in MainScreen applies orientation changes reactively as prefs change — no activity restart required

---

## Version 1.1.3

**Status:** Complete

**Implemented:**
- App Density capability check — on startup, DASH attempts the density-change operation and catches any failure (reflection failure, security exception, permission denial, or anything else). If the probe fails for any reason, the App Density section is greyed out with the message: "App density requires elevated system permissions not available on this installation."
- If the probe succeeds the setting works as originally designed — buttons are shown and functional

**Regressions:**
- None

**Fixes:**
- App Density previously swallowed all exceptions silently, giving no feedback when the operation failed. The capability check surfaces this correctly

**Outstanding:**
- None for 1.1.x — density and scale foundation is complete. App Density confirmed unavailable on Pixel 8 (capability check working correctly). Full verification of App Density deferred to a board where DASH runs as a system app and the call path is natively available

**Notes:**
- DASH UI Scale (the +/− buttons) works correctly on the Pixel 8 — this is DASH's own internal scaling and requires no system permissions
- App Density (system-wide DPI) requires IWindowManager access that Android's hidden API restrictions block for non-system apps on recent Android versions. The capability check correctly identifies this regardless of the specific restriction — the same check will pass on the Orange Pi when DASH is installed as a system app
- The probe sets density to `DENSITY_DEVICE_STABLE` (the device's native DPI), which is visually a no-op but exercises the full reflection call path

---

## Version 1.1.4

**Status:** Complete

**Implemented:**
- System bars (status bar and navigation bar) hidden on launch — DASH occupies the full screen
- `onWindowFocusChanged` override reapplies the hide whenever the window regains focus — notifications and dialogs can temporarily restore bars, but DASH reclaims full screen when it returns to the foreground
- Swipe from edge temporarily reveals bars then auto-hides (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) — keeps back gesture accessible during development without permanently surfacing the bars

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- None

**Notes:**
- Implemented via `WindowInsetsControllerCompat` — handles API differences between Android 7 (minSdk 24) and current versions transparently

---

## Version 1.1.5

**Status:** Complete

**Implemented:**
- App Density UI is capability-conditional. On startup, DASH probes `setForcedDisplayDensityForUser()` via `checkCapability()`. Two exclusive branches result:
  - **System app path** (probe succeeds — Orange Pi production): DASH shows its own native preset buttons (Compact / Normal / Comfortable / Large) that apply density directly via IWindowManager. No external navigation required
  - **Consumer device path** (probe fails — Pixel 8, any sideloaded build): DASH reads the current system density freely from `Settings.Global display_density_forced` (no permissions needed) and shows a button that opens Android's Display size and text settings screen directly. The current density label refreshes on every `ON_RESUME` so it updates immediately when the user returns from Android settings
- Deep link targets `Settings$TextReadingPreferenceActivity` (the Display size and text screen) directly on AOSP/Pixel devices, with a silent fallback to `Settings.ACTION_DISPLAY_SETTINGS` on devices where that component doesn't exist
- DensityManager gains `readCurrentSystemDpi()`, `tryWriteSystemDpi()`, and `formatDpi()` — IWindowManager reflection methods retained intact for the system app path

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- None

**Notes:**
- The two paths are mutually exclusive — system privileges mean DASH controls density natively; no privileges mean DASH defers to Android settings. No hybrid or intermediate state is shown
- `display_density_forced` in Settings.Global is the exact key Android's own Display Size setting writes — reading it requires no permissions and is always accurate
- Shizuku is the next step for consumer-device density control — it grants IWindowManager access via ADB without root, enabling the native preset path on sideloaded builds. Carries forward to v1.1.6
- The `tryWriteSystemDpi()` method (Settings.Global.putInt path) exists in DensityManager but is not exposed in the UI — it was a secondary investigation path that was not pursued. Can be removed in a future cleanup if Shizuku proves to be the right next step

---

## Version 1.1.6

**Status:** Complete

**Implemented:**
- `readCurrentSystemDpi()` rewritten to read from `context.applicationContext.resources.displayMetrics.densityDpi` instead of `Settings.Global.display_density_forced`
- `tryWriteSystemDpi()` removed — it was an unexposed investigation path from v1.1.5 with no UI entry point

**Regressions:**
- None

**Fixes:**
- Consumer path density label ("Current: X dpi") was stuck showing "Compact (160 dpi)" regardless of actual system density. Root cause: `display_density_forced` in Settings.Global had been written directly to 160 by the ADB test in v1.1.5, and Android's Display Size setting does not reliably update this key. Reading from `applicationContext.resources.displayMetrics.densityDpi` bypasses Settings.Global entirely and returns the actual live system density. The ON_RESUME observer was working correctly throughout — only the read source was wrong

**Outstanding:**
- None

**Notes:**
- `applicationContext` is not affected by DASH's activity-level `attachBaseContext` density isolation — it reflects real system density
- Shizuku is parked indefinitely. The consumer deep-link path is adequate for now. Shizuku may be revisited in version 2 if consumer density control becomes a priority
- 1.1.x is now complete

---

*This document is maintained throughout development. Every version increment — including third number refinements — requires an entry here before the version is considered complete.*
