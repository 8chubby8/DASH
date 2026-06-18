# DASH — Development Roadmap

---

## Purpose of This Document

This document defines the development plan for DASH. It establishes the versioning convention, the development eras, the feature sequence, and the milestone definitions. It is the authoritative reference for what gets built, in what order, and why.

This document is read alongside changelog.md, which records what actually happened during development — regressions, fixes, refinements, and lessons learned. Roadmap.md is the plan. Changelog.md is the reality.

---

## Versioning Convention

Every DASH version is identified by three numbers in the format X.x.x.

### First Number — Development Era

The first number defines which deployment era DASH is in. Each era represents a fundamentally different target platform and capability level.

- **1.x.x** — Tablet era. Development and refinement toward a working tablet installation.
- **2.x.x** — Board era. SBC hardware with expanded features and refined experience.
- **3.x.x** — Platform era. Advanced features, community tools, and capability expansion.

The first number increments when DASH moves from one era to the next — from tablet to board, from board to platform.

### Second Number — Major Feature

The second number increments each time a new major feature implementation begins. Each increment represents a meaningful new capability being added to DASH. Features are implemented in a defined sequence — see the era sections below for the ordered feature list.

### Third Number — Implementation Stage

The third number always follows this convention regardless of era or feature:

- **.1** — Initial implementation. The feature exists for the first time. Core functionality is present and can be tested. May be rough or incomplete in edge cases.
- **.2 onwards** — Refinement. Each subsequent increment is a direct response to testing feedback — regressions fixed, behaviour improved, edge cases resolved. The number of refinement increments is not predefined. A feature advances when it is stable, not on a schedule.

**The third number always resets to .1 when the second number increments.** A new feature always starts at .1 regardless of how many refinements the previous feature required.

### Reading a Version Number

A version number tells a clear story at a glance.

- **1.3.1** — Tablet era, third major feature, initial implementation. Something new just arrived.
- **1.3.4** — Tablet era, third major feature, fourth refinement. Something has been worked on extensively.
- **2.1.1** — Board era just started, first major feature, initial implementation. A new era begins.

### The Changelog Relationship

Every version increment — including third number refinements — must have a corresponding changelog.md entry before the version is considered complete. The changelog records what was implemented, what broke, what was fixed, and what remains outstanding. A version number without a changelog entry is incomplete.

---

## Version 1.x.x — Tablet Era

**Goal:** A working DASH installation on a tablet that can be mounted in the X-Type and driven daily. Proves the core concept in real world conditions. Feature set is deliberately constrained — only what is needed for a meaningful and usable tablet head unit experience.

**Target hardware:** Any Android 7+ tablet with USB OTG host. Bronze tier.

**Milestone definition:** Version 1.x.x is complete when DASH can be mounted in a car on a tablet, display a running app in the viewport, show a functioning system bar with alerts and settings, connect and display at least one USB serial module in the module panel, and allow the user to launch apps from the launcher. It does not need to be beautiful. It needs to work.

---

### Feature Sequence

#### 1.1.x — Density and Scaling

**What it is:** The foundation of the entire DASH interface. Establishing the two-tier density and scale system before any UI is built on top of it.

**What gets built:**
- App density preset system — Compact, Normal, Comfortable, Large — applied to Android system density
- DASH UI scale preset system — Minimal, Compact, Balanced, Spacious — applied independently to DASH chrome
- DASH density override for its own windows, ensuring the two systems remain cleanly separated
- Basic Appearance settings page with density and scale selectors
- Verification that third party apps respond to density changes correctly
- Verification that DASH chrome responds to scale changes independently

**Why first:** Every subsequent feature is sized, positioned, and rendered using the density and scale system. Building anything before this is established means rebuilding it when this is added.

---

#### 1.2.x — DASH as System Launcher

**What it is:** Registering DASH as the Android default home application and establishing correct boot and wake behaviour.

**What gets built:**
- Launcher manifest declaration — HOME and DEFAULT_HOME intent filters
- Default launcher prompt handling — guiding the user to set DASH as default
- Boot behaviour — DASH is the first thing seen on cold boot
- Screen wake behaviour — DASH foregrounds on ignition on system message
- Screen sleep behaviour — screen sleeps on ignition off system message
- Splash screen — displays on cold boot and screen wake, user-definable image or colour
- Android navigation bar suppression on supported devices
- Transient navigation bar fallback for back access

**Why second:** Every other feature assumes DASH is the launcher. Navigation, viewport control, persistent services — all depend on DASH owning the home position.

---

#### 1.3.x — System Bar

**What it is:** The only persistent interface element. The anchor of the entire DASH interface.

**What gets built:**
- System bar container — top or bottom position, user-defined height
- Single default zone spanning full bar width
- Mandatory alerts area element — placeholder initially, functional in 1.4.x when transport is live
- Mandatory settings button element — 48dp hard touch target floor enforced
- Basic element rendering within zones — left, centre, right snap positions
- Element vertical positioning within bar
- Bar height as master measurement — element sizing as a user-controlled dp value **(Complete — 1.3.4)**
- Element size control: 4dp steps, 24dp minimum ("min" label, − greyed), ceiling one step below bar height ("max" label, + greyed), auto-clamped when bar shrinks **(Complete — 1.3.4)**
- Zone splitting (up to three), zone width distribution presets, element packing layout, Spacer element type **(Complete — 1.3.5)**
- Edit mode scaffolding and zone divider dragging **(Complete — 1.3.6)** — edit mode entry from settings, bar visual state change, draggable zone dividers with detent snap at 1/4 / 1/3 / 1/2 / 2/3 / 3/4 of bar width. Edit mode originally planned as a single version; split into 1.3.6 (infrastructure + dividers) and 1.3.7 (element drag-and-drop) because drag-and-drop in Compose for a production-quality experience is a substantial undertaking on its own. Splitting keeps each version completable cleanly and ensures divider dragging can be tested before element repositioning is layered on top
- Edit mode — ruler and interaction model **(Complete — 1.3.7)** — the original plan for direct long-press-drag on the bar has been superseded by a design review. The new model: when edit mode is entered, a control strip appears a finger-width's gap from the bar on its inner side (below if top-docked, above if bottom-docked). The bar itself is never touched directly during editing — all interaction happens in the ruler. Zone dividers are represented in the ruler as arrow markers pointing back to their bar position. Elements are represented as footprint-sized boxes visible only in edit mode — no permanent boundary box exists in normal operation. The whole-bar border/tint treatment from 1.3.6 is removed — the ruler's presence alone is the unambiguous signal that edit mode is active; a second signal repeating the same information is redundant. Divider drag is free (no snapping) — snap detents from 1.3.6 were rebuilt into the ruler but removed after on-device testing; the snap threshold masked the gesture as non-response. Snap may return in a future version if there is genuine reason for it
- Edit mode — Save, Cancel, and edit workspace **(Complete — 1.3.8)** — DONE button replaced with separate SAVE and CANCEL actions centred in the main content area. SAVE commits the in-memory edited state to DataStore. CANCEL discards all changes and restores the bar to its last saved state with no write — `barConfig` from DataStore is the implicit snapshot; no additional snapshot variable required
- Edit mode — zone configuration relocation **(Complete — 1.3.9)** — zone count (1/2/3) moved from Settings into the edit workspace. Zone count is now part of the edit session — committed on SAVE, discarded on CANCEL. Distribution preset buttons removed entirely: they predate divider dragging and are redundant now that dragging achieves the same result directly. Settings System Bar section now contains only position toggle, bar height stepper, element height stepper, EDIT BAR LAYOUT, and RESET
- Edit mode — binding visual feedback **(Complete — 1.3.10)** — element boxes show a red 3dp tint on each bound edge (touching a zone boundary or adjacent element). Snap detents reintroduced at 1/4, 1/3, 1/2, 2/3, 3/4 with a 4dp threshold. Escape mechanic: picking up a divider already at a snap point enters free-move immediately; snap re-engages after 4dp of movement. Detent markers (tick marks) appear on the ruler during drag. Divider arrow turns red when settled at a snap point
- Edit mode — drag gesture robustness and pick-up affordance **(→ 1.3.11)** — fix gesture tracking so it continues correctly if the touch point moves outside the original bounds of the marker being dragged (identified as the likely cause of unpredictable drag cancellation during testing). Apply a subtle highlight or scale-up the instant a press registers on a ruler marker, before any drag movement, to confirm pick-up immediately
- Edit mode — element drag bounds crash **(→ 1.3.12)** — dragging an element box too far left (off the left screen edge) crashes the app. Root cause: `ElementBox` uses `Modifier.padding(start = xPx.toDp())` and `elementDragOffsetPx` is unconstrained, so a sufficiently large leftward drag produces a negative `xPx`, which Compose rejects as invalid padding. Fix: clamp `currentXPx` in `EditRuler` so the element box cannot be dragged to a position where its left edge would be off the ruler's left boundary. Apply the same clamp to the right edge for symmetry — an element box should not be draggable beyond the right edge of the ruler either
- Edit mode — height controls relocation **(→ 1.3.13)** — move the bar height stepper and the element height stepper out of the main Settings panel and into the edit-mode workspace, alongside the zone count control already relocated there in 1.3.9. Both remain single global values exactly as before — bar height applies to the one bar, element height applies uniformly to every element on the bar. No per-element variation. While inside edit mode, adjusting either stepper resizes the bar or its elements in real time, so the user sees the result immediately rather than leaving settings to check
- Element height and width contract **(→ 1.3.14)** — finalises the SDK sizing contract, superseding the earlier draft. Height is decided entirely by DASH from the global element height setting and is never exposed to an element's implementation in any form — no height parameter, no height property, nothing in the API surface an element author sees or touches. Width is not negotiated by the element either. Each element has its own natural aspect ratio, defined purely by how the author designed its appearance. Given a height, DASH computes the corresponding width automatically by scaling the whole design proportionally — the way enlarging or shrinking a photograph preserves its shape rather than stretching it unevenly. By default, elements are rigid: at any given height, an element has exactly one natural size. It either fits in the remaining zone space or it doesn't. If it doesn't fit, DASH refuses to place it and shows the zone-overflow soft warning already specified — it never compresses, distorts, clips, or overlaps elements to force a fit. Making room is the user's responsibility, achieved by resizing the bar, rearranging zones, or removing something else. Documented future direction, not part of this version: an author may later choose to make their element compressible, defining multiple presentations for different amounts of available space — deferred until a real element complex enough to warrant it is built, which aligns with the additional elements already planned for version 2. Refactor both existing elements (Settings Button and Alerts Area) to conform fully — neither should reference height anywhere in its implementation, and each should render at a width derived purely from its natural aspect ratio and the given height. Confirm the Zone Layout computes total required width from natural element sizes and correctly refuses placement with the existing soft warning when there isn't enough room
- Spacer architecture correction **(→ 1.3.15)** — corrects the design decision made in 1.3.5 to handle the Spacer outside the ElementRegistry, on the reasoning that it was structural rather than a content element. That reasoning is superseded. The Spacer is not structural — it is a rigid element with no visible content, going through the exact same DashElement contract as Settings Button or Alerts Area, with its own fixed natural size and no special handling anywhere in the layout system. Flexible gap sizing is achieved by placing multiple spacer instances next to each other, not by giving the spacer its own resizable width control. Remove the special-case Spacer handling added in 1.3.5 from the Zone Layout. Building the actual Spacer element is deferred to version 2 alongside the other planned additional elements (Clock, Volume, Now Playing) — it does not need to exist for the remainder of 1.3.x. Leave or remove the partial ElementType.SPACER scaffolding from 1.3.5 as judged appropriate given its current state. Reaffirm: only Settings Button and Alerts Area are native, mandatory DASH elements. Every other element, including the Spacer once built, is SDK-pathway from the start — built using the same contract any third-party developer would use, even when DASH ships it by default
- Theme token system **(Complete — 1.3.3)** — named colour tokens (barBackground, barAccent, barText) exposed via a `CompositionLocal` rather than a plain singleton or object. Components read their theme from the current composition context; version 2 introduces full theming and presets by providing different token values at the top of the composition tree, with no rework to how `SystemBar` or any `DashElement` reads its colours. This matches interface.md's requirement that elements consume theme tokens rather than hardcode values. One default token set. No user-facing theme switching — that is version 2 scope. Foundational requirement so the settings panel in 1.5.x can inherit its visual identity from the active system bar theme

**Why third:** The system bar must exist before settings can live anywhere, before modules have a persistent UI reference, and before the viewport has a boundary to conform to.

---

#### 1.4.x — Transport Layer

**What it is:** The communication backbone that allows modules to connect, identify themselves, and send data to DASH.

**What gets built:**
- Transport interface — the pluggable transport abstraction
- USB serial transport using usb-serial-for-android library
- Discovery broadcast — DASH:DISCOVER sent on all active transports
- Module discovery response parsing — six field format
- Installation handshake — DASH:INSTALL:moduleID and module response handling
- Module database — save installed modules to disk
- Startup reconciliation — broadcast on boot, Active and Dormant state management
- System message routing — SYSTEM:function:value parsed and dispatched
- Module message routing — MODULE:moduleID:variable:value parsed and dispatched
- WiFi TCP transport — DASH TCP server, module client connections

**Why fourth:** The module panel in 1.5.x needs live data to be useful. Building transport first means module panel testing is immediately meaningful rather than working with dummy data.

---

#### 1.5.x — Settings Panel

**What it is:** The user-facing configuration interface. Minimal at this stage — only settings that support features already implemented. Grows as each subsequent feature is added.

**What gets built:**
- Full screen settings panel layout — system bar remains visible above
- Three level progressive navigation — major categories, subcategories, content area
- Animated category slide transition — major column slides left on subcategory selection
- Settings visual identity — inherits system bar theme tokens
- Appearance tab — density presets, scale presets (from 1.1.x), system bar height and position
- Transports tab — USB serial enable/disable, WiFi TCP enable/disable and port
- Modules tab — installed module list, enable/disable per module
- Developer tab — safety acknowledgement, basic transport diagnostics, log viewer
- Settings button on system bar wired to open settings panel

**Why fifth:** Users need to be able to configure what has been built before building more on top of it. The settings panel also needs to exist before module panel and launcher settings can be added in subsequent features.

---

#### 1.6.x — Module Panel

**What it is:** The display area for installed accessory modules. The core DASH differentiator.

**What gets built:**
- Module panel container — docks to any of four edges
- Automatic orientation — horizontal for top/bottom, vertical for left/right
- Three sizes — Small one times, Medium two times, Large four times system bar height
- System bar relationship — module panel never overwrites system bar space
- Persistent mode — always visible
- Floating mode — hidden with peek strip, swipe to reveal, tracks gesture in real time
- Module layout slot rendering — h underscore and v underscore variants, light and dark
- Fallback behaviour — cascade to nearest available slot if requested slot undefined
- Swipe to cycle between installed modules — horizontal swipe for horizontal panel, vertical for vertical
- Module Panel Reveal element added to element library
- Stacking rule enforcement — floating module panel and floating app launcher cannot share same edge
- Module Panel settings added to Settings — Appearance tab, Panels section

**Why sixth:** Transport is already live so modules can actually send data. The module panel can be tested with real hardware immediately.

---

#### 1.7.x — Viewport

**What it is:** Formal definition and control of the application display area.

**What gets built:**
- Viewport boundary calculation — remaining space after system bar and persistent panels
- App window constrained to viewport bounds
- Correct inset reporting to apps — apps know where interactive safe zones are
- Flush viewport mode — square edges, no overlap, default
- Dominant viewport mode — rounded corners, elevation shadow, viewport appears to float
- Passive viewport mode — viewport extends under floating bars, bars render as overlay layer
- Frosted glass effect for Passive mode — Android 12 and above, semi-transparent fallback for older versions
- Corner radius setting for Dominant mode
- Viewport mode selector added to Settings — Appearance tab, Viewport section

**Why seventh:** Viewport boundaries depend on knowing where all bars and panels are. Implementing this after the system bar and module panel ensures the calculation is complete and correct.

---

#### 1.8.x — App Launcher Tray

**What it is:** The full app library. Tray only at this stage — favourites bar deferred to version 2.

**What gets built:**
- Full screen launcher tray — app grid showing all installed apps
- Recently used apps surfaced at top of grid
- Search bar — filters apps and DASH functions simultaneously
- App launch on tap — app opens in viewport
- Long press app to pin — placeholder for favourites bar, functional in version 2
- App Launcher element added to element library — grid icon, taps to open tray
- Edge swipe to reveal tray — swipe from assigned edge
- Launcher settings added to Settings — Appearance tab, Panels section

**Why last in version 1:** The launcher is needed for the car test but is the least foundational of the version 1 features. Everything else must work before the launcher adds meaningful value.

---

### Version 1 Milestone

**1.x.x is complete when:**
- DASH boots as the system launcher on a tablet
- Density and scale are configurable and work independently
- The system bar is present with alerts area and settings button always visible
- At least one USB serial module connects, installs, and displays in the module panel
- The viewport displays a running app correctly
- Apps can be launched from the launcher tray
- Basic settings are accessible and functional
- The whole system survives daily use in a moving vehicle without critical failures

---

## Version 2.x.x — Board Era

**Goal:** DASH running on dedicated SBC hardware as Roger's personal daily driver. Refined, polished, and genuinely pleasurable to use every day. Everything from version 1 working correctly on proper hardware, plus the features that elevate it from prototype to finished personal system.

**Target hardware:** Orange Pi 5 or equivalent RK3588 board. Silver tier minimum.

**Feature areas planned for version 2:**

Theming — MaterialTheme token system, colour and font customisation, preset system with export and import, the three viewport mode presets as complete aesthetic packages.

Advanced UI scaling — per-element size controls, zone resizing, spacer element, element vertical positioning fine-tuning.

Fully integrated settings — all settings tabs complete and functional, three column navigation fully polished, Developer tab with all tools operational.

App favourites bar — pinned app slots, icon count drives bar dimensions, empty slot placeholders, swipe from favourites to full tray.

Viewport Dominant and Passive modes — polished implementation, frosted glass effects, corner radius controls.

Advanced transparency effects — glass system bar, semi-transparent panels, the full Passive aesthetic.

Overlay system — transient and notification overlays, overlay SDK, notification interception via NotificationListenerService, overlay trigger mapping.

Now Playing element — MediaSession integration, full transport controls, album art.

Additional elements — clock and date variants, volume overlay trigger, connectivity status.

System message relay — modules subscribing to system messages, DASH relaying to subscribed modules.

---

## Version 3.x.x — Platform Era

**Goal:** Expanding DASH toward advanced automotive integration and community platform capability. Features that add depth and power over time rather than features needed for a working system. Polish and capability rather than foundation.

**Feature areas planned for version 3:**

Camera integration — reverse camera overlay, MIPI CSI pipeline on supported boards, surround view groundwork.

CAN integration — CAN sniffing service, OBD2 polling, standard signal slot population from vehicle data.

CAN learning tool — guided in-car signal identification, rolling CAN logger, community vehicle profile sharing.

CAN patch bay — user-configurable mapping of CAN signals to DASH system calls.

Element SDK — formal SDK extraction from built-in element codebase, documentation, examples, community element support.

Overlay SDK — formal SDK extraction, documentation, examples, community overlay support.

Advanced module features — HYBRID module type full implementation, source dominance for system messages, module version management.

Community infrastructure — vehicle profile database, theme sharing, element and overlay community library.

Advanced settings — ambient mode, granular notification control, driving mode rules, per-app audio permissions.

---

## Supporting Documents

| Document | Purpose |
|----------|---------|
| hardware.md | Board selection, hardware tiers, peripheral requirements |
| transport.md | Module protocol, transport types, message definitions |
| interface.md | Interface architecture, elements, overlays, settings |
| changelog.md | Version-by-version record of what was built, what broke, what was fixed |

---

## Development Discipline

**Every version increment requires a changelog entry.** No version number advances without the corresponding changelog.md entry being written. This applies to third number refinements as much as major feature implementations.

**Every built-in element and overlay must be built as if it were a community SDK component.** No built-in component gets special internal access that a community developer could not have. If a built-in component needs internal DASH state that the SDK does not expose, that is a signal to extend the SDK interface — not a reason to make an exception. This discipline ensures that when the Element SDK and Overlay SDK are extracted in version 3, they are complete and genuinely capable rather than second class.

**Features are considered complete when they are stable under real world use — not when they initially work on a bench.** The third number refinement cycle exists precisely to close the gap between initial implementation and genuine stability. A feature is not done until daily driving confirms it.

---

*This document is the authoritative development plan for DASH. Read alongside changelog.md for a complete picture of planned and actual progress.*
