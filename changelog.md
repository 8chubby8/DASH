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

## Version 1.3.10

**Status:** Complete

**Implemented:**
- Snap detents reintroduced at 1/4, 1/3, 1/2, 2/3, and 3/4 of bar width with a 4dp pull threshold. At 4dp, snap only activates when the divider is genuinely close to a detent — the pull zone is tight enough to feel assistive rather than obstructive
- Escape mechanic: if the divider is already settled at a snap point when picked up, it enters free-move immediately. Snap re-engages once the divider has moved more than 4dp from the touch-down position. This eliminates the on-touch-down locking that caused snap to be removed in 1.3.7
- Detent position markers: short vertical tick marks appear at each snap fraction on the ruler while a divider is being dragged; they fade in on drag start and fade out on release. Gives the user a visible target to aim at
- Divider arrow turns red (`0xFFE53935`) when settled at a snap point, derived from config on every recomposition so it updates in real time during drag
- Element box bound edge tinting: a 3dp red strip is drawn on whichever edge of each element box is bound — touching a zone boundary or packed against an adjacent element. Bound state is computed from anchor group membership (LEFT group: left edges always bound; RIGHT group: right edges always bound; CENTRE group: inner edges bound, outer edges free). Strip is clipped by the box's rounded corners

**Regressions:**
- None

**Fixes:**
- N/A

**Outstanding:**
- None

**Notes:**
- The snap threshold of 4dp is deliberately tight and may need adjustment after on-device testing. The consensus before implementation was to start tight and expand if needed — 6dp is the next step if 4dp disappears into the noise
- The escape mechanic uses `totalDragPx` (signed net displacement from touch-down) rather than total distance traveled. If the user reverses direction, accumulated displacement decreases — snap stays disabled until they have committed to a clear move away from the starting point. This feels correct on the reasoning that small reversals near the touch-down point should not unlock snap prematurely
- `isSnapped` is derived from config via `remember(config, dividerIndex, rulerWidthPx)` rather than a separate state variable. Since config updates every drag frame via `onConfigChange`, the arrow colour updates in real time with no additional state management
- The 1.3.7 note about the 12dp threshold causing "on-touch-down locking" is the problem the escape mechanic solves. Both changes together should produce snap that is usable and unobtrusive. If the escape mechanic proves unnecessary at 4dp, it is still correct behaviour — picking up a free divider that happens to be exactly at a snap point gives immediate movement, which is always the right feel

---

## Version 1.3.9

**Status:** Complete

**Implemented:**
- Zone count control (1/2/3 buttons) moved from the Settings panel into the edit workspace. Zone count is now part of the edit session — buttons update `editConfig` in memory, committed on SAVE or discarded on CANCEL
- `withZoneCount()` moved from a private extension function in `SettingsPanel.kt` to a method on `SystemBarConfig` in `SystemBarModel.kt`, where it belongs as a pure model transformation
- Zone distribution preset buttons (`DISTRIBUTION` row) removed entirely from Settings. The `ZoneDistribution` data class, both preset lists (`ZONE_DISTRIBUTIONS_2`, `ZONE_DISTRIBUTIONS_3`), `distributionActive()`, and `withDistribution()` are all deleted
- Edit workspace is now a `Column` — ZONES label and 1/2/3 buttons above, SAVE/CANCEL row below
- Settings System Bar section now contains only: position toggle, bar height stepper, element height stepper, EDIT BAR LAYOUT button, RESET button

**Regressions:**
- None

**Fixes:**
- N/A

**Outstanding:**
- None

**Notes:**
- **Zone count moved to edit workspace — deliberate design decision.** Previously, changing zone count wrote directly to DataStore with no way to undo. Moving it into the edit session means the user can experiment with zone count, see the result on the bar in real time, and commit or discard it alongside any divider positions they have changed. The edit session is the correct scope for all layout decisions
- **Distribution presets removed — deliberate design decision.** The preset buttons (`1:1`, `1:2`, `2:1`, etc.) existed to give the user a way to set zone widths before divider dragging existed. They predate 1.3.6/1.3.7. Now that the user can drag a divider to any position they want, the presets are redundant. Removing them simplifies the Settings panel and eliminates a UI element that was already superseded by a better mechanism

---

## Version 1.3.8

**Status:** Complete

**Implemented:**
- DONE button replaced with separate SAVE and CANCEL actions, centred in the screen while edit mode is active
- CANCEL discards all in-progress edits and exits edit mode with no DataStore write. `barConfig` (the DataStore-backed flow) is the implicit snapshot — it is never written until SAVE is pressed, so nulling `editConfig` restores the bar exactly to its last saved state
- SAVE commits the in-memory `editConfig` to DataStore then exits edit mode, identical to what DONE did
- Buttons are side by side in a `Row` centred on screen: CANCEL (grey, `0xFF424242`) on the left, SAVE (green, `0xFF2E7D32`) on the right. Both use the same monospace style as all other DASH text controls

**Regressions:**
- None

**Fixes:**
- N/A

**Outstanding:**
- None

**Notes:**
- The snapshot mechanism requires no additional state variable. `barConfig` comes from DataStore and is only written on SAVE — it is naturally the restore point for CANCEL
- Buttons are in the main content area (the empty space between bar/ruler and the opposite screen edge), consistent with the roadmap intent that the content area becomes the edit workspace. Additional controls move into this workspace in 1.3.9 (zone count) and 1.3.12 (height steppers)

---

## Version 1.3.7

**Status:** Complete

**Implemented:**
- `EditRuler` composable — a 44dp horizontal strip that appears on the bar's inner side when edit mode is active, separated from the bar by an 8dp gap. For a bottom-docked bar it sits above; for a top-docked bar it sits below. The ruler is the complete interaction surface for edit mode; the bar itself is never touched during editing
- Ruler entry/exit transitions — `AnimatedVisibility` with `expandVertically` (spring, medium bouncy) and `fadeIn`/`fadeOut`. The bar stays pinned to the screen edge; the ruler grows inward from the bar. Exit is a quick tween shrink in the same direction
- Divider arrow markers — each zone boundary in the ruler is represented by a filled triangle pointing back toward the bar, centred on an 80dp touch target. Drag is free — position follows touch exactly, clamped only to maintain a 48dp minimum width on each adjacent zone. `rememberUpdatedState` for stable gesture capture. The arrow sits atop a subtle 1dp vertical line that marks the zone boundary in the ruler
- Element footprint boxes — one box per element per zone, positioned to mirror each element's rendered position in the bar. `SystemBar` now fires `onElementMeasured(id, widthPx)` via `Modifier.onSizeChanged` on each element wrapper; `MainScreen` collects these into `elementWidths: Map<String, Int>`. The ruler's `computeElementPositions()` uses those widths and mirrors the Zone Layout's anchor-group packing (padding-aware, LEFT/CENTRE/RIGHT groups). Boxes are 32dp tall, rounded, `barAccent` fill at 40% opacity; dragging brightens them to 75% with a spring-animated alpha transition
- Element repositioning — dragging a box left or right moves it continuously; on release `computeNewConfig()` determines the target zone (which zone range the drop centre fell in) and infers a new anchor from the drop position within that zone (left third → LEFT, middle → CENTRE, right → RIGHT). The element is added to the end of the target zone's element list. The config update is immediate; the box reappears at its new natural position in the recomposed ruler
- `DraggableDivider` removed — the bar now renders plain 1dp dividers in all modes. The bar no longer knows about edit mode at all: `editMode` and `onConfigChange` parameters removed from `SystemBar`. Edit state is held entirely in `MainScreen` and driven through `EditRuler`
- 1.3.6 bar border/tint removed — the ruler's presence is the sole unambiguous signal that edit mode is active. No duplicate mode indicator

**Regressions:**
- Divider arrow markers were completely unresponsive to touch on first build

**Fixes:**
- **Hit area bug — `Modifier.offset` does not move the touch target.** `Modifier.offset { IntOffset(x, y) }` is a layout modifier that places the child at the offset position in the parent's coordinate system, but reports its own bounds as `(0, 0)` to `(childWidth, childHeight)` — the size of the child, not the size of the child plus the offset. Compose's hit testing descends the layout tree checking each node's reported bounds. When the touch point is at the drawn position (e.g. dividerX = 600dp) but the node's reported bounds only cover `(0, 0)` to `(40dp, 44dp)`, the hit test fails and the gesture handler is never reached. Fixed by replacing `Modifier.offset(x)` with `Modifier.padding(start = xDp)` throughout `EditRuler`. `padding` is also a layout modifier but it includes the padded space in the node's own reported bounds — a `padding(start = 580dp).width(40dp)` node correctly reports bounds of `(0, 0)` to `(620dp, 44dp)`, and the touch at 590dp passes the hit test and reaches the inner `pointerInput`
- Touch target enlarged from 40dp to 80dp after on-device testing confirmed that 40dp was registering but felt unreliable
- **Snapping removed.** Detent snap at 1/4 / 1/3 / 1/2 / 2/3 / 3/4 (carried over from 1.3.6's `DraggableDivider`) was rebuilt into the ruler but removed after on-device testing. With a 12dp snap threshold, the divider would lock to the nearest detent on touch-down, requiring the user to drag significantly before it registered any movement — indistinguishable in feel from the divider not responding. Snap may be reintroduced in a later version if there is a genuine case for it. For now drag is free

**Outstanding:**
- Element pick-up affordance (scale-up on press, before drag begins) → 1.3.11
- Drag gesture continuation if touch moves outside marker bounds → 1.3.11

**Notes:**
- `elementWidths` in `MainScreen` is populated by the bar's ongoing rendering — by the time the ruler first appears it is already fully populated. The `onSizeChanged` path fires only when measured widths change (element height change, config change), so there is no recomposition loop
- The `computeElementPositions()` function mirrors the Zone Layout's anchor-group math exactly, including the 8dp horizontal padding that the Layout applies to each zone. The ruler's footprint boxes will sit at the same X positions as the real elements in the bar
- `computeNewConfig()` infers anchor from drop position within the target zone (thirds model). This is intentionally simple for 1.3.7 — fine-grained reordering within an anchor group is a future refinement
- The `Column` wrapping bar + ruler in `MainScreen` is pinned to the same screen edge as the bar was previously. Because the Column is `BottomCenter`/`TopCenter`-aligned, the bar stays against the screen edge and the ruler expands inward as the Column grows — the DONE button's absolute position (against the same edge) continues to overlay the bar correctly
- The 1.3.10 roadmap entry references visual feedback when a divider is at a snap point. If snap is not reintroduced, that entry will need revising when 1.3.10 is reached

---

## Version 1.3.6

**Status:** Complete

**Implemented:**
- Edit mode state — `editMode: Boolean` and `editConfig: SystemBarConfig?` hoisted in `MainScreen`. Edit mode is runtime-only, never persisted. `editConfig` is a live shadow of the bar config during editing; it is initialised from prefs when edit mode is entered and written back to DataStore only when DONE is tapped
- Entry point — "EDIT BAR LAYOUT" button added to the System Bar section of the settings panel. Tapping it closes settings and activates edit mode immediately. Placed in the System Bar section for now; will relocate to a dedicated Appearance section when the full settings tree is built in a later version
- Visual state change — the bar gains a 2dp border (barText at 35% opacity) when edit mode is active. Zone dividers thicken to 2dp at 60% opacity — visually distinct from the normal 1dp at 30% accent
- Draggable zone dividers — in edit mode each divider is replaced by a 16dp touch target containing the 2dp visual line. `pointerInput(Unit)` with `detectDragGestures` drives the drag; `rememberUpdatedState` ensures the gesture handler always reads the latest config without restarting mid-drag. Drag delta is converted to a zone fraction delta and applied to the two adjacent zones, keeping their combined fraction constant
- Detent snap — snap points at 1/4, 1/3, 1/2, 2/3, 3/4 of bar width. Within 12dp of a snap point the divider pulls to it; drag past the threshold and it releases. All positions between snap points are valid — snap assists alignment, it does not constrain placement
- Minimum zone width — 48dp per zone. The clamp prevents either adjacent zone from collapsing below a usable size; fractions are adjusted conservatively so the combined fraction is always conserved
- DONE button — a green "DONE" button overlays the left end of the bar during edit mode (opposite the settings button, which is right-anchored by default). Tapping it saves `editConfig` to DataStore and exits edit mode. Config is written once on exit — not on every drag frame
- Settings button in edit mode — `DashAction.OpenSettings` is ignored while `editMode` is true. DONE is the only exit path

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- Element drag-and-drop repositioning → 1.3.7

**Notes:**
- Edit mode was originally planned as a single version. It was split into 1.3.6 (infrastructure + zone divider dragging) and 1.3.7 (element drag-and-drop) because Compose drag-and-drop for production-quality element repositioning — ghost element, anchor snapping, snap guidelines, per-element sizing — is a substantial undertaking on its own. The split keeps each version completable cleanly and allows divider dragging to be tested and confirmed before element repositioning is layered on top
- `BoxWithConstraints` wraps the `SystemBar` Row to provide `barWidthPx` synchronously for drag fraction calculations. `barWidthPx` is passed to `DraggableDivider` via `rememberUpdatedState` so the gesture handler stays consistent if the bar is ever resized while edit mode is active
- The "EDIT BAR LAYOUT" entry point will move to a dedicated Appearance section of settings when the full settings tree is built. It is deliberately a simple callback — relocation will be trivial
- **Design revision — 1.3.7 onward:** The original plan for 1.3.7 (long-press-drag directly on the bar with ghost element and floating offset preview) was superseded by a design review before implementation began. The new model relocates all editing interaction to an adjacent ruler strip — the bar itself is never touched during editing. This is a substantially different approach to edit mode and replaces the direct-bar-drag model described in the 1.3.6 Outstanding section. The ruler model is now the plan of record from 1.3.7 onwards; the original approach has been retired without being built
- **Roadmap additions — 1.3.12 and 1.3.13:** Following a further design review, two additional versions have been added to the 1.3.x sequence. 1.3.12 relocates the bar height and element height steppers from the main Settings panel into the edit-mode workspace (alongside the zone count control moved in 1.3.9), with real-time resize feedback while inside edit mode. 1.3.13 removes height entirely from the DashElement API surface — the Zone Layout will impose a fixed-height constraint on each element's box before the element's own composable runs, making height an invisible platform concern rather than something element authors need to handle. Both changes are captured in roadmap.md; implementation follows once the preceding edit mode versions are complete
- **Roadmap revision — 1.3.13 updated, 1.3.14 added:** Following a further design review, the 1.3.13 entry has been substantially revised and a new 1.3.14 added. 1.3.13 now covers the complete element sizing contract: height invisible to element authors (imposed by DASH before element code runs), width derived from natural aspect ratio rather than negotiated by the element (proportional scaling like a photograph), rigid-by-default fit-or-no-fit behaviour using the existing zone overflow warning, and compressible elements documented as a future opt-in rather than part of this version. 1.3.14 is a new Spacer architecture correction: the 1.3.5 decision to handle Spacer outside the ElementRegistry is superseded — the Spacer is a standard rigid DashElement with no special layout handling, flexible gaps come from placing multiple instances rather than a variable-width single instance, and building the actual Spacer element is deferred to version 2 alongside Clock, Volume, and Now Playing. This entry explicitly corrects and supersedes the Spacer reasoning recorded in 1.3.5, so that the project history does not carry two contradictory decisions forward unreconciled

---

## Version 1.3.5

**Status:** Complete

**Implemented:**
- Zone splitting: the user can split the system bar into 1, 2, or 3 zones via a zone count control in settings (ZONES: 1 / 2 / 3 buttons). Default state is one zone spanning the full width
- Zone width distribution: when 2 or 3 zones are active, preset distribution buttons appear (DISTRIBUTION). 2-zone presets: 1:1, 1:2, 2:1. 3-zone presets: 1:1:1, 1:2:1, 2:1:1, 1:1:2. The active preset is highlighted. Draggable dividers arrive with edit mode in 1.3.6
- Zone dividers: a 1dp vertical line is drawn between zones at 30% accent opacity. In 1.3.6 these become the draggable edit-mode handles
- Element packing layout: the `Zone` composable is rewritten as a custom Compose `Layout`. Elements are grouped by anchor (LEFT, CENTRE, RIGHT) and packed without overlap — LEFT group packs left-to-right from the left edge; RIGHT group packs left-to-right as a unit flush against the right edge; CENTRE group packs as a unit centred in the zone. All elements are centred vertically within the bar. This layout is the foundation 1.3.6 drag-and-drop builds on
- `ElementType.SPACER` added to the catalogue. `ElementPlacement` gains `spacerWidthDp: Int?` (null for all non-spacer types). Spacer constants added to `SystemBarConfig`: default 16dp, min 4dp, max 120dp, step 4dp. Spacer elements are rendered as invisible sized boxes by the Zone layout. No UI for placing spacers yet — that arrives with edit mode in 1.3.6
- Zone management on count reduction: elements in removed zones are migrated to zone 0 before the zone is dropped

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- Drag-and-drop edit mode (element placement, zone divider dragging, snap guidelines) → 1.3.6

**Notes:**
- `withZoneCount()` and `withDistribution()` are private extension functions on `SystemBarConfig` inside `SettingsPanel.kt` — they are settings-panel concerns and don't need to live in the model
- The packing layout assumes measurables correspond 1:1 with `zone.elements` in list order — this invariant must be maintained when adding new element types. Each element type in the `when` branch must emit exactly one composable node
- Spacer is handled directly in the Zone Layout rather than registered in `ElementRegistry`. The registry is for content elements. Spacer is structural and carries no content or SDK surface
- Zone distribution fractions use float comparison with 0.01f tolerance for preset active-state detection

---

## Version 1.3.4

**Status:** Complete

**Implemented:**
- Element height is now a user-controlled dp value stored in `SystemBarConfig.elementHeightDp` (default 36dp). Elements receive this as `scope.heightDp: Dp` in `ElementScope` and are responsible for rendering sensibly within it — the same contract an SDK element developer will receive
- `SizeVariant` enum removed entirely. It was a stepped abstraction over the thing users actually care about — a concrete size. The dp system is direct, consistent with the bar height control, and unambiguous for SDK element authors
- `ElementPlacement.variant` removed. Element height is global for now; per-element sizing arrives with edit mode in 1.3.6
- Settings panel System Bar section updated — bar height control labelled "SYSTEM BAR SIZE", element height control labelled "ELEMENT SIZE" below it, using the identical +/− stepper pattern
- Element size boundaries: minimum 24dp (label shows "min", − greyed), maximum one step below bar height (label shows "max", + greyed). When bar height is reduced below the element ceiling, element height is auto-clamped in the same save operation — config is always internally consistent
- `AlertsAreaElement` and `SettingsButtonElement` render proportionally to `scope.heightDp` — font and icon sizes scale as a fixed fraction of element height so the visual scales smoothly across the full range
- `SettingButton` composable gains an `enabled` parameter with distinct disabled colours — used for the element size steppers at their limits

**Regressions:**
- None

**Fixes:**
- None

**Outstanding:**
- Zone splitting (up to three), inter-element snap packing, Spacer element → 1.3.5
- Drag-and-drop edit mode → 1.3.6

**Notes:**
- Element height proportions: `AlertsAreaElement` font at 30% of height, padding at 28%/11% horizontal/vertical. `SettingsButtonElement` icon at 55% of height. These feel right at the 36dp default and at both limits — adjust per on-device testing
- The `enabled = false` greyed state on `SettingButton` uses `disabledContainerColor = Color(0xFF1A1A1A)` and `disabledContentColor = Color(0xFF444444)` — visually distinct from the active and inactive states without being harsh
- **Departure from interface.md — element sizing model:** interface.md specifies element sizing as a percentage of bar height. This version implements a direct dp control instead. The dp model keeps bar height and element height as fully independent decisions, which is cleaner for the user and unambiguous for SDK element authors. interface.md should be updated to reflect this decision
- **Departure from interface.md — soft limit behaviour:** interface.md specifies amber soft limit warnings at the lower size boundary. This version instead shows a "min" label and greys the − button at 24dp, and a "max" label with greyed + button at the ceiling. The boundary is communicated through the control itself rather than a separate warning indicator. interface.md should be updated to reflect this decision

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
- **How to add a token to the system:** (1) Add a field with a default value to `DashColors` in `ui/theme/DashTheme.kt` — e.g. `val barHighlight: Color = Color(0xFF4A4AFF)`. (2) Update `DashColors.dark()` (and any future preset factories) with a considered value. (3) Read it anywhere in the composition with `LocalDashTheme.current.barHighlight`. That is the entire change. No call sites break, no provider changes needed, no migration required for stored data

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
