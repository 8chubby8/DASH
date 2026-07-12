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
- Mandatory alerts area element — placeholder initially, functional in 1.4.x when transport is live *(superseded 2026-07-08 — the alerts area going functional, together with TRIGGER routing, was moved to the new 1.9.x Elements version as a deliberately low-priority nice-to-have. The placeholder remains from this version; it goes live in 1.9.x, not 1.4.x.)*
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
- Edit mode — binding visual feedback **(Complete — 1.3.10)** — element boxes show a red 3dp tint on each bound edge (touching a zone boundary or adjacent element). Snap detents reintroduced at 1/4, 1/3, 1/2, 2/3, 3/4 with a 4dp threshold. Escape mechanic: picking up a divider already at a snap point enters free-move immediately; snap re-engages after 4dp of movement. Detent markers (tick marks) appear on the ruler during drag. Divider arrow turns red when settled at a snap point. Live edge highlight during element box drag previews the intended anchor in real time. Element box drop onto another element's footprint swaps their positions; drop into open space uses the existing thirds model
- Edit mode — drag gesture robustness, pick-up affordance, and ruler visual design **(Complete — 1.3.11)** — gesture events consumed unconditionally during drag rather than only on positionChanged, preventing cancellation when the touch point moves outside the original marker bounds. Divider arrow brightens to full opacity on press as immediate pick-up confirmation; element box stroke thickens from 1dp to 2dp and goes to full opacity on press — both before any drag movement. Ruler visual redesign: ruler is now transparent with a single 1dp centre track line in barAccent2; element boxes are stroke-only outlines (no fill); bound edge tints remain as coloured overlays on the outline. New barAccent2 theme token added for all ruler structural colours — sits between the invisible-dark barAccent and the content-bright barText, independently themeable in version 2
- Edit mode — element drag bounds crash **(Complete — 1.3.12)** — dragging an element box too far left (off the left screen edge) crashes the app. Root cause: `ElementBox` uses `Modifier.padding(start = xPx.toDp())` and `elementDragOffsetPx` is unconstrained, so a sufficiently large leftward drag produces a negative `xPx`, which Compose rejects as invalid padding. Fix: clamp `currentXPx` in `EditRuler` so the element box cannot be dragged to a position where its left edge would be off the ruler's left boundary. Apply the same clamp to the right edge for symmetry — an element box should not be draggable beyond the right edge of the ruler either
- Edit mode — height controls relocation **(Complete — 1.3.13)** — move the bar height stepper and the element height stepper out of the main Settings panel and into the edit-mode workspace, alongside the zone count control already relocated there in 1.3.9. Both remain single global values exactly as before — bar height applies to the one bar, element height applies uniformly to every element on the bar. No per-element variation. While inside edit mode, adjusting either stepper resizes the bar or its elements in real time, so the user sees the result immediately rather than leaving settings to check
- Element height and width contract **(→ 1.3.14)** — finalises the SDK sizing contract, superseding the earlier draft. Height is decided entirely by DASH from the global element height setting and is never exposed to an element's implementation in any form — no height parameter, no height property, nothing in the API surface an element author sees or touches. Width is not negotiated by the element either. Each element has its own natural aspect ratio, defined purely by how the author designed its appearance. Given a height, DASH computes the corresponding width automatically by scaling the whole design proportionally — the way enlarging or shrinking a photograph preserves its shape rather than stretching it unevenly. By default, elements are rigid: at any given height, an element has exactly one natural size. It either fits in the remaining zone space or it doesn't. If it doesn't fit, DASH refuses to place it and shows the zone-overflow soft warning already specified — it never compresses, distorts, clips, or overlaps elements to force a fit. Making room is the user's responsibility, achieved by resizing the bar, rearranging zones, or removing something else. Documented future direction, not part of this version: an author may later choose to make their element compressible, defining multiple presentations for different amounts of available space — deferred until a real element complex enough to warrant it is built, which aligns with the additional elements already planned for version 2. Refactor both existing elements (Settings Button and Alerts Area) to conform fully — neither should reference height anywhere in its implementation, and each should render at a width derived purely from its natural aspect ratio and the given height. Confirm the Zone Layout computes total required width from natural element sizes and correctly refuses placement with the existing soft warning when there isn't enough room
- Spacer architecture correction **(→ 1.3.15)** — corrects the design decision made in 1.3.5 to handle the Spacer outside the ElementRegistry, on the reasoning that it was structural rather than a content element. That reasoning is superseded. The Spacer is not structural — it is a rigid element with no visible content, going through the exact same DashElement contract as Settings Button or Alerts Area, with its own fixed natural size and no special handling anywhere in the layout system. Flexible gap sizing is achieved by placing multiple spacer instances next to each other, not by giving the spacer its own resizable width control. Remove the special-case Spacer handling added in 1.3.5 from the Zone Layout. Building the actual Spacer element is deferred to version 2 alongside the other planned additional elements (Clock, Volume, Now Playing) — it does not need to exist for the remainder of 1.3.x. Leave or remove the partial ElementType.SPACER scaffolding from 1.3.5 as judged appropriate given its current state. Reaffirm: only Settings Button and Alerts Area are native, mandatory DASH elements. Every other element, including the Spacer once built, is SDK-pathway from the start — built using the same contract any third-party developer would use, even when DASH ships it by default
- Theme token system **(Complete — 1.3.3, extended 1.3.11)** — named colour tokens (barBackground, barAccent, barAccent2, barText) exposed via a `CompositionLocal` rather than a plain singleton or object. Components read their theme from the current composition context; version 2 introduces full theming and presets by providing different token values at the top of the composition tree, with no rework to how `SystemBar` or any `DashElement` reads its colours. This matches interface.md's requirement that elements consume theme tokens rather than hardcode values. One default token set. No user-facing theme switching — that is version 2 scope. Foundational requirement so the settings panel in 1.5.x can inherit its visual identity from the active system bar theme. barAccent2 added in 1.3.11 for ruler structural colours — a mid-tone between the invisible-dark barAccent and the content-bright barText

**Why third:** The system bar must exist before settings can live anywhere, before modules have a persistent UI reference, and before the viewport has a boundary to conform to.

---

#### 1.4.x — Transport Layer

**What it is:** The communication backbone that allows modules to connect, identify themselves, and send data to DASH.

**Wire format note:** This layer is built against the ratified module grammar in `arduino/arduino.md` (pipe-separated `TYPE|id|…`), which supersedes the older colon grammar (`DASH:DISCOVER`, `SYSTEM:function:value`, …) still shown in the body of `transport.md`. See the banner note at the top of `transport.md` (added 2026-07-01) for the full old→new mapping. The message names below have been updated to the current grammar.

**Build order — one piece per version (1.4.1 → 1.4.16; 1.4.12–1.4.13 added 2026-07-07; on 2026-07-12 Bluetooth was inserted at 1.4.12 — bumping the mismatch and failure versions to 1.4.13/1.4.14 — and the module library and cleanup pass were added as 1.4.15/1.4.16):**
- **1.4.1** — Transport interface (pluggable abstraction) **+** USB serial transport (usb-serial-for-android) **+** Serial Monitor. *(Combined: a transport foundation must be visible to be verifiable. The Serial Monitor — a persistent dev instrument reached from settings — is the diagnostic surface every later piece is verified through.)* **(Complete — 1.4.1, bench-verified on Arduino Uno R4: `DISCOVER` → `HELLO` round-trip confirmed.)**
- **1.4.2** — Discovery broadcast **+** response parsing **(Complete — 1.4.2, bench-verified on the Arduino Uno R4: press DISCOVER → the module appears in the Module Management list by name).** A user-driven DISCOVER button on a new Module Management screen (reached from settings, mirroring the Serial Monitor route) broadcasts `DISCOVER` on all active transports; the `HELLO|id|type|name|description|version` replies are parsed and populate the discovered-module list, each press rebuilding it from scratch. *(1.4.3 merged in — as 1.4.1 combined the transport and the monitor — because a discovery screen with a button but no populated list can't be verified as working. Two decisions taken here, ahead of the features that use them: (a) discovery is a user-driven **method of installation, not reconnection** — DASH broadcasts only on the button press, never on a timer; the automatic re-sweep of already-installed modules (arduino.md §6) belongs to reconnection/reconciliation, 1.4.6. (b) The message brain is a **configurable crossroads, not a funnel** — a `DashController` dispatcher (one inbox, sort by TYPE word, dispatch to a desk, one outbox) with only the discovery desk staffed today; the transport layer sends to and receives from **all** active transports. The single-device status prompt was removed from Module Management — that screen is about the whole bus of modules, not one device.)*
- **1.4.3** — *(merged into 1.4.2 above — the outbound broadcast and the HELLO parsing were built together.)*
- **1.4.4** — Installation handshake — `INSTALL|id` → type-specific declarations → `INSTALL_END|id` **(Complete — 1.4.4, bench-verified on the Arduino Uno R4: DISCOVER → INSTALL → the pane runs a progress bar, turns green, and DETAILS shows the captured declarations.)** All three declaration parsers built together — SYSTEM (`SYSTEM_SIGNAL`), LISTENER (`SUBSCRIBE`, all seven fields captured), ACCESSORY (`MANIFEST` + length-prefixed CRC32-checked asset `BLOCK`s) — with Roger writing LISTENER and ACCESSORY reference modules to exercise each path. The install desk (`Install`) is the controller's first *stateful, bidirectional* desk: opened by DASH sending `INSTALL`, fed by the declaration run, committed on `INSTALL_END` into a session-only `InstalledModule` (disk is 1.4.5). Block framing is owned by the transport (a new `FrameAssembler` switches to a byte-count read synchronously on the IO thread); CRC validation and record assembly by the desk. *(Deferred, with homes: install timeout + a designed fail-state visual to later 1.4.x failure work — only the unavoidable bad-CRC abort is handled now; ACCESSORY variables/controls declaration framing to be designed before the panel, 1.6.x, per arduino.md §10; ACTIVATE/live data/state store to 1.4.6+, so an installed module is dormant. arduino.md gained §4b state reporting and §5a controller architecture as groundwork for those, plus `system_commands.md` as the authoritative signal vocabulary.)*
- **1.4.5** — Module database — save installed modules to disk **(Complete — 1.4.5, verified: install a module, relaunch DASH, the green card is back from disk with DETAILS reading the saved record.)** New `ModuleDatabase` — one folder per module under `filesDir/modules/`, a `module.json` record (the serialised `InstalledModule`) beside an `assets/` folder holding each ACCESSORY block's raw bytes as plain files. The record is written *last*, so a folder interrupted mid-write has no record and is swept on the next load — never half-loaded. The split settled here: **the database holds *installed*; the install desk holds only *installing*** — a completed handshake commits into the database (payload bytes held in the session only for that hop, then written to disk, never kept in memory after). Module Management now merges two sources into **one card per physical module**: installed cards appear the moment the screen opens (no DISCOVER needed, plugged in or not), discovery adds INSTALL cards for the rest; DISCOVER can never remove an installed card. Uninstall is one recursive delete — no wire message, because the module persists nothing (arduino.md §6): DASH forgetting *is* the uninstall. *(A failed disk write logs and degrades to 1.4.4's session-only behaviour rather than erroring. Asset bytes on disk are read by nothing yet — the panel, 1.6.x, is their consumer.)*
- **1.4.6** — Startup reconciliation — boot broadcast, Active/Dormant management, `ACTIVATE`/`DEACTIVATE` + `ROGER` acknowledgement **(Complete — 1.4.6, bench-verified on the Arduino Uno R4: launch DASH with the module plugged in → the green card turns ACTIVE within a sweep and the Serial Monitor shows DISCOVER → HELLO → ACTIVATE → ROGER; uninstall while active → DEACTIVATE → ROGER and the module's heartbeat stops.)** The reconciliation desk (`Reconciliation`) is the controller's first desk with a *clock*: a persistent `DISCOVER` sweep — every 5 s for the first minute (the Uno Q ~20–30 s boot case), then every 30 s forever (crank-reset and hot-plug recovery) — matches `HELLO`s against the module database and `ACTIVATE`s the matches with ROGER-verified retries (3 tries, 2 s apart), pausing while an install handshake is in flight and sweeping immediately when a transport comes up. Installed cards wear an ACTIVE/DORMANT chip (absent after ~75 s unheard); discovered-but-not-installed cards now appear and age out on their own. Uninstall gained its wire half: an active module is sent `DEACTIVATE` (acked, retried), the record deleted immediately per arduino.md §6, with the disconnect-or-power-cycle warning raised if never confirmed. *(Two decisions taken with Roger, 2026-07-06: (a) **one DISCOVER, no separate reconnect message** — a broadcast "reconnect" is impossible when modules don't know their own install state (§6), so the install/reconnect distinction lives in DASH's handling of the replies, not on the wire; a per-id ping and an optional HELLO state field were considered and parked (arduino.md Considered & parked). (b) **The DISCOVER button became SYNC** — with the sweep persistent, a dedicated install-discovery button is redundant; SYNC is §6's manual "check now", serving install and reconnection with one press. This amends 1.4.2's decision (a): discovery remains a method of installation, but "broadcasts only on the button press, never on a timer" was the rule only until reconciliation existed — the sweep 1.4.2 explicitly reserved for 1.4.6 now runs it. (c) `ACTIVATE` is **re-asserted every sweep** rather than only when DASH thinks a module is silent — a rebooted module's HELLO is indistinguishable from a healthy one's, so a fresh ROGER is the only proof of life; measured at ~1% of a 115200 wire for ten modules even with future §4b dumps, Roger accepted the chatter over changing the HELLO contract.)*
- **1.4.7** — System message routing — `BROADCAST|id|function|value` parsed and dispatched into the sourceless core **(Complete — 1.4.7, verified on the tablet with the built-in simulator: SIMULATOR ON → install the two virtual modules → the store ticks, events fire, the pokes broadcast.)** The controller's fourth desk (`Broadcasts`) is the §5 gatekeeper: sender must be installed *and* ACTIVE, then the id is consumed and the signal routed into the new sourceless core (`SystemState`: state store + event bus) by its §5a behaviour, looked up in the in-code copy of `system_commands.md` (`SystemCommands` — the markdown stays authoritative). The DASH side of §4b came free by construction: the desk *compares before it acts*, so activation dumps and 5 s heartbeats are ordinary traffic — same value, nothing stirs; the store self-heals. Verification surface (agreed 2026-07-07): a **State Inspector** dev instrument (settings, beside the Serial Monitor) — store pane with value+age, timestamped event log — plus a **simulated transport** with two virtual modules kept to full firmware discipline (SILENT until ACTIVATE, honest path through the gatekeeper): a Sim Vehicle SYSTEM module (continuous speed/rpm stream, random door flips, §4b dump + heartbeat, poke buttons standing in for the pretend car's physical inputs) and a Sim Accessory (block-transfer install, REPORTs that correctly go unrouted until 1.4.9). *(Decisions: the machinery is permanent, the inspector screen deliberately quick-and-dirty; unknown signals drop-and-log — the custom fallthrough and patch-bay stay future crossroads stages; live broadcast traffic feeds the reconciliation liveness clock so a streaming module can't age DORMANT mid-stream; the sim's two-modules-one-pipe is a deliberate shared-bus preview, contention design still parked at 1.4.10.)*
- **1.4.8** — LISTENER streams — `SUBSCRIBE` at install, `LISTEN` delivery, with rate/threshold/gate evaluated in DASH (arduino.md §9) **(Complete — 1.4.8, verified on the tablet with the built-in simulator: install Sim Vehicle + the new Sim Relay LISTENER → the vehicle's signals come straight back out as `LISTEN` on the Serial Monitor, throttled at 1 Hz against the 2 Hz source, deadbanded, delivered on-change, on the 5 s heartbeat, dumped on activation, and valueless for `media_next`.)** The controller's fifth desk (`Streams`) — the *outbound* mirror of 1.4.7's broadcast desk. It watches the sourceless core rather than the wire (the crossroads' destination stays dumb; the desk holds the cleverness) and delivers subscribed signals back out, evaluating all four §9 controls in DASH: a **leading-and-trailing rate throttle** (the resting value of a stream converges within one window instead of waiting on the heartbeat), a numeric **deadband**, and a **gate** on another stored signal. *(Two decisions taken with Roger, 2026-07-07→08: (a) **defaults are the firmware library's job, not DASH's** — the earlier plan for DASH to hold per-signal default rate/threshold was dropped; a module declares exactly what it wants and DASH honours the delivered line literally, holding no defaults and recording nothing extra. This keeps DASH out of the way (ethos) and the built-in/community playing field level (SDKable): the default-provider moved from DASH to the shared library the sketch author still doesn't touch. A blank optional field is legal and total (no cap / any change / always); a present-but-malformed field is logged and ignored, never guessed at — DASH assumes modules are written perfectly and drops what isn't, protecting its own integrity without accommodating the error. arduino.md §4c/§9 and system_commands.md reworded to match. (b) **The vocabulary-as-editable-data idea was raised and parked indefinitely** — the built-in signal set stays curated in code (type-safe, no runtime parse or ship-a-typo surface); user-added custom signals are the future patch-bay/custom-fallthrough stage's job, where a user-editable file is the right tool.) The gate path is built but was the one path left unexercised by the sim (its subscriptions are ungated for a clean "all signals" read) — recorded honestly as built-but-not-yet-watched; it reuses the verified store-watch and lookup patterns.*
- **1.4.9** — Module message routing — `REPORT|id|variable|value` parsed and dispatched (renders in the module panel once that exists in 1.6.x; dispatches to the monitor until then) **(Complete — 1.4.9, verified on the tablet with the built-in simulator: install Sim Accessory → its `test_counter` REPORTs, unrouted since 1.4.7, now land in the State Inspector's MODULE REPORTS pane; press ACTION ▸ → `ACTION|…|sim_button` goes out and `button_presses` climbs back in, the full round trip.)** The controller's sixth desk (`ModuleReports`) is the specific column's inbound half — the private mirror of the 1.4.7 broadcast desk. The one architectural difference and the point of the version: `REPORT` is **sourceful** — the id is *kept* as the store key (`temperature` from module X is that module's panel data, never merged), where a broadcast's id is consumed. It fills `ModuleData`, the per-module twin of the sourceless `SystemState`. Built alongside it (Roger's call, so the 1.6.x panel front end is ready to just work): `ACTION|id|control|value` **out** via a thin `Actions` desk — a gatekept emit that stores nothing and deliberately does not validate the control id (the panel does that for free, only ever rendering real controls). With this the §4 2×2 is fully staffed: general column (BROADCAST/LISTEN) sourceless, specific column (REPORT/ACTION) sourceful. *(Scope decision: `TRIGGER`, the third ACCESSORY-out message, was ruled out and given a home at the new 1.9.x Elements version — it lands in a shared alert store read by the alerts element, so it is element work, not private-panel work.)*
- **1.4.10** — Multi-device support & Devices view — the transport layer sees and *addresses* more than one physical device (today the USB transport grabs only the first device it finds); a Devices view of what is physically connected on each transport; and device selection in the Serial Monitor (a dropdown to pick which device to talk to). **(Complete — 1.4.10, verified on the tablet with two real boards on a *powered* hub: an Arduino Uno R4 WiFi (CDC-ACM) and an ESP32 DevKitC (CP2102) held open at once — "2 devices @ 115200 8N1", the first run of the multi-device path.)** Per-device `DeviceConnection`s each with **their own `FrameAssembler`** (the hard requirement); inclusive per-device driver resolution so no ESP32 board is locked out; broadcast send / merged incoming / aggregated status + device list. **The Devices view was dropped** (Roger's call — Module Management already answers "what modules," a physical-device list is thin) and **replaced by a Signal Monitor**: a live board of every system message against its current value in the sourceless core — the surface the deleted State Inspector used to give, minus the sim. The Serial Monitor gained a "SEND TO" device dropdown (All devices = broadcast). The **USB one-time permission grant** was pulled forward from the pre-1.5 cleanup below (device_filter + `USB_DEVICE_ATTACHED` intent-filter → "use by default", so a board authorised once stops re-prompting). Two new reference SYSTEM sketches (`arduino.ino` "Body", `esp32.ino` "Powertrain") exercise it on real copper, sharing `ambient_temp` to prove the sourceless core takes redundant sources. Deferred: per-device labelling of inbound lines in the wire log (→ 1.5.x settings). *(The session's dominant symptom — "second board kills the first" — was diagnosed as a **power** problem, not code: an unpowered hub browning out the tablet's whole USB bus; a powered hub fixed it, the transport was correct throughout.)* The single-device prompt was already removed from Module Management in 1.4.2. Placed just before WiFi because WiFi is what really brings a crowd of devices — build the multi-device model and immediately stress it with WiFi's many clients. *(Roger's call: progress the module-lifecycle sequence first; multi-device is not blocking — install addresses a module by id, so it works with one device meanwhile.)* *(Added 2026-07-07, from the what-happens-to-live-data-during-an-install discussion: (a) **one FrameAssembler per device stream** is a hard requirement of this version — today's single global assembler is correct only while there is a single device; kept global, a block transfer on device A would put device B's innocent live-data bytes into byte-count mode and corrupt the frame. Per-device assembly is what guarantees install data and live data never share a stream on any point-to-point transport — USB per-device, TCP per-socket. (b) **Shared-bus contention** is the one place that guarantee can't hold and is parked here with the rest of the shared-bus design: on RS485 multi-drop a block transfer must own the bus — DASH quiesces the other modules for the duration (temporary DEACTIVATE or a lighter hush, design TBD) — and the simultaneous-HELLO reply-jitter question parked in the 1.4.6 changelog lands in the same conversation.)*
- **1.4.11** — WiFi TCP transport — DASH TCP server, module client connections (proves the 1.4.1 abstraction is genuinely transport-agnostic) **(Complete — 1.4.11, verified on real hardware: the Arduino Uno R4 WiFi running the new `arduino_wifi.ino` "Body WiFi" reference module connected to DASH's TCP server over WiFi and came up as a module over the air — the first time a module has reached DASH over anything but a cable.)** The second `DashTransport` behind the 1.4.1 contract, with the inversion that DASH is now the *server* and modules are the clients that connect in (vs USB, where DASH is the host that opens devices). A TCP server on **port 3274** (D-A-S-H on a phone keypad; unprivileged, so it keeps the no-root constraint), one `FrameAssembler` **per accepted socket** (the 1.4.10 per-stream rule carried from cables to sockets), and — the whole point of the version — the *only* change above the transport layer was one line registering it in `TransportManager`; controller, core, install, database and reconciliation untouched. Also folded in: the `INTERNET` permission; the tablet's LAN IP surfaced in the WiFi transport status; and the Serial Monitor switched to **one status line per transport** after the old merged line was found to mask the WiFi "Listening on <ip>" behind USB's. The **in-car networking model** was worked out and recorded (tablet-as-hotspot providing the module network while the SIM carries internet — one radio does both, and the tablet-as-AP gives a *fixed* `DASH_HOST`); the **MODULE SETUP provisioning button** was designed and deliberately parked to Version 2 (reading the device's own hotspot SSID/password needs system-app privilege Android denies a Bronze sideload — a natural capability-detection feature for the production hardware).
- **1.4.12** — Bluetooth Classic (SPP) transport — a third `DashTransport`, the wireless sibling of WiFi, further proving the 1.4.1 abstraction holds. *(Added 2026-07-12 — Roger can run SPP on the ESP32 while the Arduino stays on WiFi, giving a genuine three-transport bench: USB, WiFi and BT at once — the strongest proof yet that nothing above the transport layer cares which pipe carried a message.)* **Classic SPP, not BLE:** a byte-stream that drops straight onto the line grammar and the per-device `FrameAssembler` model (`BluetoothSerial` on the ESP32 side). The transport *shape* is the familiar one — per-device RFCOMM socket, its own assembler, fan-out send / merged incoming — but BT brings three things WiFi didn't. **Pairing:** an SPP device must be bonded first, so DASH works with already-paired devices via standard Android Bluetooth settings (transport.md), no programmatic pairing. **Runtime permissions:** `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` on API 31+ are dangerous permissions needing a user grant — a capability-detection / graceful-degradation path (request; if denied, the transport reports unavailable and DASH carries on). **Client model:** DASH connects *out* to bonded devices as the RFCOMM client — more like USB's DASH-initiates than WiFi's module-connects-in. **BLE is deliberately not this version** — its GATT / characteristic / MTU-chunk model doesn't fit the line grammar and is its own later, more complex transport. *(Placed right after WiFi because both are wireless hot-plug transports and doing them back-to-back keeps the machinery hot; it also gives 1.4.14's wired-vs-wireless fault visual a third transport type to test against.)*
- **1.4.13** — Firmware version mismatch — detect, surface, one-tap update. *(Added 2026-07-07, prompted by the Test Accessory v2 reflash: DASH's stored record was captured from the old firmware and DASH silently didn't notice; renumbered 1.4.12 → 1.4.13 on 2026-07-12 when Bluetooth took 1.4.12.)* The reconciliation desk already compares every `HELLO` against the module database by id; this adds the version field to that comparison. A mismatch means the install-time contract DASH holds — `SYSTEM_SIGNAL` declarations, `SUBSCRIBE` requests, MANIFEST assets — may be stale, so it is surfaced, never ignored: a version-mismatch chip on the installed card, DETAILS showing both stored and reporting versions, and a one-tap update that re-runs the install handshake (uninstall + install back to back — a firmware update *is* a reinstall, because the module persists nothing, §6). Detection is *difference only* — the version field is free text with no ordering contract, and "different" is exactly the condition under which the stored record is untrustworthy. **Deliberately excluded: auto-refresh.** DASH re-running the handshake unprompted is deferred until the install failure work exists (timeout + designed fail state, deferred since 1.4.4 — now 1.4.14) — auto-triggering installs at boot with nobody watching invites the wedged-install-pauses-the-sweep problem. Revisit after 1.4.14 lands.
- **1.4.14** — Designed install failure — timeout, fail state, recovery. *(Added 2026-07-07 — not a new idea but the home for the failure work parked since 1.4.4, when "install timeout + a designed fail-state visual" was deferred with only the unavoidable bad-CRC abort built; renumbered 1.4.13 → 1.4.14 on 2026-07-12 when Bluetooth took 1.4.12.)* Today an install that stalls mid-handshake — module unplugged mid-transfer, wedged firmware, a declaration run that never reaches `INSTALL_END` — leaves the install session open forever. This version closes that: an install timeout aborts a stalled session; the card gets a *designed* fail state (a visual with a reason and a retry, not a silent reset or an eternal progress bar); and the 1.4.6 outstanding item is fixed — a wedged install can no longer pause the reconciliation sweep indefinitely. Also collects the other parked §6 failure visual: **absent-wired = fault** (a wired module that stops answering should look broken) **vs absent-wireless = quiet dormant** (a WiFi or BT module out of range is normal), parked in 1.4.6 as "alongside the install timeout" — placed after the wireless transports (WiFi, then BT) so the wired/wireless distinction has real transport types to be tested against. Completing this version unlocks the 1.4.13 auto-refresh revisit.
- **1.4.15** — The module firmware library — reconcile `arduino/arduino.md`, lock the SDK, and write the shared Arduino library every module (built-in or community) is built from. *(Added 2026-07-12 — Roger's call: with every transport and the failure work in, the SDK the reference sketches have been proving by hand is ready to become a real library.)* Three acts, in order. **(1) Reconcile arduino.md** against everything actually built in 1.4.1–1.4.14 — walk each section against the changelog decisions, catch cheat-sheet-vs-body lag (e.g. the `SUBSCRIBE`-defaults cheat-sheet line still predates the 2026-07-08 defaults-in-the-library amendment in §4c), and add the module-facing wireless-connection facts a builder now needs (connect to port 3274 / bond over SPP, go SILENT on drop) — all as dated additive notes, never erasing the prior text. **(2) Lock the SDK** — with the spec current and true to the code, the rules are settled, which is the CLAUDE.md moment to consider promoting them out of the working record into `transport.md` or a dedicated module-rules document (only on Roger's express instruction). **(3) Write the library** — extract the discipline the reference sketches (`arduino.ino`, `esp32.ino`, `arduino_wifi.ino`, the LISTENER/ACCESSORY references) have carried by hand — framing, the HELLO / install handshake, the SILENT-until-`ACTIVATE` lifecycle, the §4b dump + heartbeat, `SUBSCRIBE`-default filling, change detection — into one library so a builder writes only their own sensors and controls. The concrete proof of the SDKable principle: the library a community builder uses is the same one the built-in modules use.
- **1.4.16** — Cleanup pass before 1.5.x — the short refinement pass that folds in the niceties deferred during the build. *(Added 2026-07-12 as an explicit numbered version — formalises the informal "Cleanup before 1.5.x" note that used to sit under this list.)* Known items: **USB hot-plug reconnection reliability** *(flagged 2026-07-12 — needs locking down tight)* — a USB device sometimes fails to come back after an unplug/replug (or a module reboot / brown-out), staying absent until DASH is restarted. Intermittent, so it needs reproducing and then hardening the whole reconnection path in `UsbSerialTransport`: the re-sweep's `connectAvailable()`, the ATTACH/DETACH broadcast handling, the `pendingPermission` linger/clear logic, and stale-`DeviceConnection` pruning — a replugged board must always return on its own, no restart. Likely suspects: a missed DETACH leaving a dead connection in the map (so `containsKey` skips reopening), a reused `deviceId` colliding with a lingering permission flag, or an IO thread not fully torn down before the reopen. **Per-device labelling of inbound lines in the Serial Monitor wire log** (the SEND-TO dropdown targets sends, but inbound lines still merge under one transport tag — deferred here since 1.4.10); and **greet-on-device-count-change** (a second module connecting while another is already up currently waits out the reconciliation sweep rather than being greeted at once — noted in 1.4.11; a transport-agnostic fix that helps USB, WiFi and BT alike). The USB one-time permission grant this pass originally owned was already pulled forward into 1.4.10.

*Cleanup before 1.5.x — **promoted 2026-07-12 to an explicit numbered version, 1.4.16 above** (alongside the module library at 1.4.15). Kept here for the record: the **USB one-time permission grant** this pass first listed was done early (pulled forward into 1.4.10 — a `USB_DEVICE_ATTACHED` intent-filter + `device_filter.xml` so a device is authorised once with "use by default"); the still-outstanding per-device wire-log labelling and greet-on-device-count-change items now live in 1.4.16.*

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

#### 1.9.x — Elements

**What it is:** Element work, gathered as the final major feature of version 1. *(Added 2026-07-08.)*

**What gets built (outline — detail to be filled when this era approaches):**
- **Agnostic alerts — TRIGGER.** `TRIGGER|id|name` routed into a shared, origin-aware alert store (a public blackboard, mirror of how `BROADCAST` fills the sourceless core — the alert *is* readable by anything; the alerts-area element just happens to be the thing that reads it), and the mandatory alerts-area element — a placeholder since 1.3.x — wired to read that store and render the module's shipped trigger icon. Origin-aware so an alert clears when its raising module goes DORMANT or is uninstalled. A deliberately low-priority nice-to-have, which is why it lands here at the end rather than in the transport era where TRIGGER's sibling messages were routed.
- Further element work to be scoped when this version is reached.

**Why last:** These are nice-to-have refinements, not foundations — nothing else in version 1 depends on them, so they sit at the very end of the era.

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

Module setup helper — a Developer/Serial Monitor button that emits a paste-ready `arduino_secrets.h` block (WiFi SSID/password, DASH host IP and port) so provisioning a WiFi module is copy-and-paste rather than hunting for the tablet's address. Capability-detected in the DASH way: the IP and port are always readable, but reading the device's *own* hotspot SSID/password needs a system-level permission Android denies a sideloaded (Bronze) app — so the credential auto-fill unlocks here on the system-app production hardware, degrading to placeholders where it can't. *(Parked here from 1.4.11, when the idea came up during the first on-hardware WiFi module setup.)*

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
