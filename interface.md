# DASH — Interface Architecture & Design

---

## The DASH Ethos

DASH gets out of the way.

The platform provides the rules, the tools, and the foundation. What gets built on top of that foundation belongs entirely to the user. Their car, their screen, their modules, their layout, their colours, their elements, their overlays, their experience. DASH has no opinion about what that should look like.

This principle governs every interface decision documented here. When a developer is deciding whether to constrain something or leave it open, this statement is the answer. The user is the master of their own system. Always.

---

## Architectural Overview

The DASH interface is built from three independent layers that work together without coupling to each other.

**The Transport Layer** moves data in and out of DASH. Defined in transport.md.

**The Signal and Trigger Layer** routes incoming data to the correct destination — standard signal slots, system message handlers, module panels, overlay triggers. It is the nervous system of DASH.

**The Rendering Layer** presents everything the user sees. Elements, overlays, module panels, the viewport. Defined in this document.

Nothing in the rendering layer needs to know how data arrived. Nothing in the transport layer needs to know how data is displayed. The layers are cleanly separated. This separation is what makes DASH genuinely hardware agnostic and infinitely extensible.

---

## DASH as a System Launcher

DASH declares itself as the Android system launcher. It registers as the default HOME application. From the moment the user sets DASH as their default launcher, every home action — home button press, back-to-home gesture, cold boot — brings the user to DASH.

Android protects the active launcher from memory management. DASH is always running. Module connections, transport listeners, signal monitors, and background services remain active continuously without risk of the system killing them.

**There is no traditional home screen.** The viewport always displays an application. The system never goes black. A default startup app — typically a navigation or dashboard app — is displayed when no other app is foregrounded. The user returns to that default by selecting it from the launcher, not by pressing a home button in the traditional sense.

---

## Power and Wake Behaviour

DASH is designed for continuous power from the vehicle battery. The normal operational cycle is screen sleep and screen wake — not system shutdown and cold boot.

**Normal cycle:**
- Ignition on → SYSTEM:ignition:on received → screen wakes → splash screen plays → DASH foregrounds
- Ignition off → SYSTEM:ignition:off received → screen sleeps → system continues running in background

**Cold boot** occurs only on first installation, system update, or manual reboot. On cold boot DASH is the first thing seen after Android initialises. The splash screen plays before the main interface appears.

**Splash screen** plays on both cold boot and screen wake from ignition. It is user-definable in Appearance settings — custom image, colour, or the default DASH branding. Duration is user-configurable.

---

## Display Density and UI Scale

Density and scale are two independent systems. They affect each other indirectly but are configured and applied separately.

### App Density

Android's system-wide display density controls how all applications render on screen. Increasing density makes everything larger — text, buttons, touch targets. Decreasing density fits more information on screen.

DASH presents density as named presets rather than a raw DPI value. The presets map to Android's standard density buckets:

| Preset | Density | Character |
|--------|---------|-----------|
| Compact | mdpi | Maximum information, smaller elements |
| Normal | hdpi | Balanced default |
| Comfortable | xhdpi | Larger elements, easier reading |
| Large | xxhdpi | Maximum size, closest viewing distance |

The user sets density once to make third party apps render correctly for their screen and viewing distance. This is the foundation. Everything else is built on top of it.

Density is configured in Appearance → Density.

### DASH UI Scale

DASH UI scale controls the size of DASH's own interface elements — the system bar, panels, zones, and elements — independently of system density. Changing density does not affect DASH chrome. Changing DASH scale does not affect third party apps.

DASH UI scale is a fluid, stepwise system. The user adjusts it with plus and minus controls in increments of 0.1x — for example 0.8x, 0.9x, 1.0x, 1.1x, 1.2x. There is no fixed set of named presets. The user dials the scale to exactly what works for their screen size and viewing distance. 1.0x is the default balanced starting point.

DASH overrides Android's density system for its own windows, ensuring the two controls remain cleanly separated and predictable. The user changes density — only apps respond. The user changes DASH scale — only DASH chrome responds.

UI scale is configured in Appearance → Density.

---

## The System Bar

The system bar is the only persistent interface element in DASH. It is always visible, always on screen, and cannot be hidden or dismissed under any circumstances.

**Position:** Top or bottom of the screen only. Never left or right edges — those are reserved for panels.

**Two elements are non-negotiable and must always be present on the system bar:**

1. **The Alerts Area** — vehicle and module alerts must always be visible. This is a safety consideration. The user must always be able to see what their vehicle and modules are reporting.

2. **The Settings Button** — the only way to reconfigure DASH. If it could be removed or hidden, the user could configure themselves into an unrecoverable state. The settings button has a hard minimum touch target of 48dp that is enforced silently regardless of bar scale. The visible icon may shrink with the bar but the interactive area never goes below 48dp.

Where these two mandatory elements are positioned within the bar is user-configurable. Their presence is not.

### System Bar Height

The system bar has a user-defined height. Height is the master measurement from which all element sizing within the bar is derived.

**Sizing rules:**
- Every element has a maximum size expressed as a percentage of bar height. As the bar grows, all element ceilings rise proportionally.
- Every element has a minimum size expressed as a percentage of bar height. Soft limits with amber warnings apply when elements approach the minimum.
- Element size is independent of bar scale. Making the bar taller does not automatically make elements bigger. The user sets element size separately within the new ceiling.
- Elements can never exceed the bar height vertically. This is a hard ceiling enforced silently.

> **Design decision — v1.3.4:** The percentage model above was reconsidered during implementation and replaced with a direct dp control. The reason is that percentages structurally contradict the independence rule stated above. If an element is set to 80% of bar height and the bar grows from 56dp to 80dp, the element grows from 45dp to 64dp — even though the user only changed the bar. That is not independence. A dp value is genuinely independent: the bar changes, the element stays exactly where the user put it, the ceiling simply moves. The user's two decisions — how tall is the bar, how tall are the elements — remain cleanly separate. There is a second reason: SDK element authors need to know concretely what size they are drawing into. A dp value in `ElementScope` is unambiguous. A percentage relative to a runtime bar height is not something a developer can design against meaningfully. The dp model is honest about what the element is receiving. The ceiling rule remains intact: element height is capped at one dp step below bar height, enforced through the control itself.

### Zone System

The system bar is divided into between one and three user-defined zones. Zones are the structural containers within which elements are placed.

**Rules:**
- Default state is one zone spanning the full width of the bar
- The user may add a second or third zone by splitting the bar
- Three zones is the hard maximum
- Zones always span the full width of the system bar — they cannot be inset from the edges
- Zone boundaries are draggable dividers in edit mode
- Zone dividers snap to sensible positions — quarter bar, third bar, half bar
- Each zone has a minimum width sufficient to hold at least one element at minimum size

### Element Positioning Within Zones

Within each zone, elements snap to defined anchor points:
- Left edge of zone
- Centre of zone
- Right edge of zone
- Left edge of an existing element within the zone
- Right edge of an existing element within the zone

These are the only valid snap positions. No freeform pixel positioning.

**Multiple elements in one zone** pack against each other or against zone edges according to where they are snapped. Elements within a zone are aware of each other and never overlap.

**Overflow** is prevented — if elements exceed zone width DASH shows a soft warning in edit mode. Elements cannot spill into adjacent zones.

**Spacer element** — a special invisible element with a user-defined width that occupies space without displaying content. Used to create breathing room between elements or to pull elements away from screen edges. The spacer is the mechanism for all spacing control within zones.

**Vertical positioning** — elements are centred vertically within the bar by default. The user can offset the vertical anchor point of all elements up or down within the bar. Expressed as a percentage of bar height from the top edge. Useful for accommodating camera notches or personal preference. Soft limits prevent elements clipping the bar edges.

### Edit Mode

Element positioning only occurs in a dedicated edit mode accessed from the Appearance settings. During normal operation the bar is completely locked — nothing moves accidentally.

In edit mode:
- The bar displays a visual state change indicating it is editable
- Elements can be long pressed, picked up, and dragged
- Snap guidelines appear as elements approach snap points
- Zone dividers become draggable
- Elements animate to show the result of placement before the user commits

> **Design decision — v1.3.7:** The direct-bar-drag model described above was reconsidered in a design review before implementation and replaced with a ruler-based interaction model. The principles of the new approach are as follows.
>
> **The bar is never touched during editing.** All interaction is relocated to a ruler strip that appears adjacent to the bar when edit mode is active — below the bar if it is top-docked, above it if it is bottom-docked, always separated by a finger-width gap. The user's hand never occludes the thing they are adjusting. This is the core structural departure from the original model.
>
> **The ruler has a fixed, predictable position.** It is always on the inner side of the bar — between the bar and the main content area — at a consistent gap regardless of bar position or height. The user always knows where it is without looking for it.
>
> **The ruler has a consistent visual language.** Zone dividers are represented as arrow markers pointing back toward their position on the bar. Elements are represented as footprint-sized boxes matching each element's actual physical width in the bar — these boxes are visible only in edit mode and have no persistent visual boundary in normal operation. There is one colour convention throughout the ruler: red tint means bound or settled. An element box shows red on whichever edge is currently bound to a zone edge or an adjacent element's edge. A divider arrow turns red when settled exactly at a snap point — not merely near one. No text-based position labelling is used; the colour is the signal.
>
> **Edit-mode signalling is unambiguous but not duplicated.** The ruler's presence is itself a clear, unambiguous signal that edit mode is active. A second signal — the whole-bar border and tint introduced in 1.3.6 — would be repeating the same information across two different channels. It is removed. Colour in the ruler is reserved for meaningful per-marker state (bound, settled) rather than general mode indication.
>
> **The main content area becomes the edit workspace.** Save and Cancel actions occupy the main content area during editing — the same area normally occupied by the running app. This reuses the existing pattern where a focused task takes over the full screen, as already established by the settings panel. On entering edit mode, the current bar configuration is snapshotted. Save commits the edited state to DataStore. Cancel discards all changes and restores the snapshot exactly, with no write.

---

## Elements

Elements are self-contained UI components that live inside zones on the system bar. They are the building blocks of the DASH interface chrome.

### What an Element Is

An element has a single well-defined purpose. It may display information, accept user interaction, or both. It is self-contained — it knows what it does, it knows how to present itself at each of its size variants, and it handles its own logic internally.

### Element Size Variants

Every element defines multiple size variants. The user selects the variant that suits their available zone space and personal preference. Variants are typically Small, Medium, and Large, though individual elements may define their own variant names appropriate to their function.

**Example — Now Playing element:**
- Small: Album art thumbnail and play/pause button only
- Medium: Adds track name and artist
- Large: Adds progress bar and full transport controls

### Element Appearance

Elements receive their colours and fonts from the active DASH theme tokens. An element never defines its own independent colour scheme. This ensures visual consistency across all elements regardless of who built them. A community-built element automatically looks coherent within the user's chosen theme.

### Built-in Elements

DASH ships with the following built-in elements:

| Element | Type | Description |
|---------|------|-------------|
| Alerts Area | Informational | Vehicle and module alerts. Mandatory. |
| Settings Button | Interactive | Opens the settings panel. Mandatory. |
| Now Playing | Both | Current media track, album art, transport controls |
| Clock and Date | Informational | Time display with optional date. Multiple format options. |
| Volume | Both | Current volume level with tap-to-adjust overlay trigger |
| Connectivity Status | Informational | WiFi, Bluetooth, cellular signal indicators |
| Back Button | Interactive | Android back navigation. Optional. |
| App Launcher | Interactive | Reveals the app launcher tray |
| Module Panel Reveal | Interactive | Reveals the floating module panel |
| Spacer | Layout | Invisible width element for spacing control |

### User-Defined Elements — The Element SDK

DASH provides an Element SDK allowing developers and users to build their own elements. A custom element is a self-contained component that follows the DASH element specification. When placed in the elements folder it appears in the element library automatically alongside built-in elements.

**An element package contains:**
- A declaration file stating name, description, version, and size variants
- Compose implementation for each size variant
- An icon for the element library
- A declaration of any DASH data sources or theme tokens it consumes

**The element specification requires:**
- Declaration of all supported size variants
- Consumption of theme tokens for all colours and fonts — no hardcoded values
- Respect for the bar height ceiling — no element may exceed bar height vertically
- Declaration of whether the element is informational, interactive, or both

Elements that do not conform to the specification are skipped on load with a log entry. They do not crash DASH or affect other elements.

**Architectural principle:** New DASH interface features do not require modifying the core platform. They are written as elements and dropped into the elements folder. The core platform remains stable while the interface evolves through the element system.

> **Design decision — v1.3.13:** The element sizing contract was finalised before more elements were built against an earlier, looser model. The settled contract works as follows.
>
> **Height is invisible to element authors.** It is not passed as a parameter, not queryable, not declarable, and not present anywhere in the API surface an element author interacts with. DASH determines height from the global element height setting and applies it by constraining the space given to the element before that element's own composable code runs. The element sees "here is a box" — the box happens to be a specific size, but the element neither knows nor cares what that size is.
>
> **Width derives from natural aspect ratio, not from negotiation.** Each element has a natural aspect ratio, defined purely by how its author designed its appearance — the same way a photograph has its own shape. Given the imposed height, DASH computes the corresponding width automatically by scaling the design proportionally. The element does not express a preferred width, request available space, or participate in the calculation. The width is a consequence of the height and the design, nothing more.
>
> **Elements are rigid by default.** At any given height an element has exactly one natural size. It either fits in the remaining zone space or it does not. If it does not fit, DASH refuses placement and shows the zone-overflow soft warning already specified in the Zone System section. DASH never compresses, distorts, clips, or overlaps elements to force a fit — that outcome is always a layout error, not an acceptable fallback. Making room is the user's responsibility, by resizing the bar, rearranging elements, or removing something. As a documented future direction, not part of the current contract: an element author may later opt into making their element compressible, defining multiple distinct presentations for different amounts of available space. This is deferred until a real element complex enough to warrant it is built, which aligns with the additional elements planned for version 2.
>
> **Bar height and element height are single global values.** Bar height is the height of the one bar. Element height applies uniformly to every element on the bar, regardless of which zone they occupy or who built them. There is no per-element height setting, by design. Visual consistency across the bar is structural and cannot be violated by individual elements, because height is not something an element can influence.
>
> **Only two elements are native to DASH.** The Alerts Area and the Settings Button are the only elements that exist outside the SDK pathway — they are mandatory, they are built into the platform, and they are the reason the system bar is useful as a safety and access mechanism. Every other element, including the Spacer when it is built, is an SDK element by design: it uses the same contract any third-party developer would use, it goes through the same ElementRegistry, and it receives no special access to DASH internals. DASH shipping an element by default does not make that element native. Only the mandatory two are native.

---

## Theme Tokens

DASH uses a named colour token system. Every component that renders visual DASH chrome — the system bar, the edit mode ruler, elements, overlays, and the settings panel — reads colours from the active token set rather than using hardcoded values. This is the mechanism that makes version 2 theming possible: swapping the token set at the top of the composition tree changes the appearance of everything at once, with no changes to any individual component.

The token set is defined in `DashColors` and provided via `LocalDashTheme`. One default set exists in version 1. Version 2 introduces user-selectable presets and a custom colour editor — both work by providing a different `DashColors` instance; the components themselves do not change.

### Token Reference

| Token | Default | Used For |
|-------|---------|----------|
| `barBackground` | `0xFF1A1A2E` | System bar fill. The foundation colour everything else sits on. Also the implied background for the settings panel and any DASH-native surfaces that inherit from the bar. |
| `barAccent` | `0xFF26263F` | Subtle structural separators within the bar — zone divider lines in the bar itself. Deliberately close to `barBackground` so it reads as a hint rather than a feature. |
| `barAccent2` | `0xFF7878A0` | Visible interactive structure — edit mode ruler track line, zone divider lines in the ruler, detent markers, element box outlines, and the divider arrow resting state. Sits between the invisible-dark `barAccent` and the content-bright `barText`. Used wherever a structural element needs to be clearly readable against the bar background without competing with content. |
| `barText` | `0xFFB0B0C8` | Foreground content — text, icons, and any element content that must be readable on `barBackground`. The brightest of the four tokens. |

### Adding Tokens

When a new component requires a colour that does not fit an existing token semantically, add a new token to `DashColors` with a sensible default and a clear name. Do not reuse an existing token for a purpose it was not intended for. Do not hardcode a colour value in a component. The token set is the single source of truth for all DASH chrome colours — keeping it complete and accurate is what makes future theming viable.

> **Token set update — 2026-07-20 (roadmap 1.5.2).** Building the settings panel, the token set above was reconciled and extended, and `DashColors` renamed to **`DashTheme`** — it now carries a **font** as well as colours (all still provided via `LocalDashTheme`). The original four `bar*` colour tokens are **retired**; their roles are absorbed into a fuller, cleanly-named set with the same defaults-flipped-to-light values 1.3.11 introduced. Nothing about the mechanism changes: one default set, and version 2 presets still work by providing a different `DashTheme` at the top of the tree.
>
> **The 1.5.2 token set:**
>
> | Token | Default | Role |
> |-------|---------|------|
> | `backgroundColourPrimary` | `0xFFE5E5EA` | Primary surface — system bar fill **and** the settings-panel background (shared, so the panel grows seamlessly from the bar). Was `barBackground`. |
> | `backgroundColourSecondary` | `0xFF848482` | Raised surface on the primary — the settings content box. |
> | `textColourPrimary` | `0xFF000000` | Text on the primary surface. Absorbs the old `barText`. |
> | `textColourSecondary` | `0xFFE5E5EA` | Text on the secondary surface. |
> | `iconColourPrimary` | `0xFF000000` | Icons on the primary surface — e.g. the settings-button gear. |
> | `iconColourSecondary` | `0xFFE5E5EA` | Icons on the secondary surface. |
> | `accentColourPrimary` | `0xFFD1D1D6` | Subtle structure on the primary — bar zone dividers, nav-row selection. Was `barAccent`. |
> | `accentColourSecondary` | `0xFF8E8E93` | Visible interactive structure — edit-ruler track, detents, element outlines. Was `barAccent2`. |
> | `font` | Monospace | The one typeface for all DASH chrome text. A single setting (the v2 font picker) changes every label at once — no DASH text hardcodes a font. Font *size* is a separate axis and, for elements, belongs to the element author, not DASH. |
>
> **The pairing rule (must not be crossed):** the colours come in matched primary/secondary pairs. Primary text/icons sit on the primary background; secondary text/icons sit on the secondary background. Secondary text on the primary background — both currently the same light grey — would be invisible. Two full sets exist deliberately, so a surface and its content always have contrast and the user keeps control of both.

---

## The Viewport

The viewport is the dedicated display area for all Android applications. Every app that runs on DASH — Google Maps, Spotify, YouTube, any installed Android app — opens and displays fully within the viewport boundaries.

The viewport is the space remaining after all persistent bars and panels have claimed their portions of the screen. Its size is determined entirely by the user's layout configuration.

**Rules:**
- Apps always fill the viewport completely — DASH never letterboxes or pillarboxes
- The viewport is the app's entire world — everything outside it is DASH's domain
- Apps receive correct inset information so interactive content respects bar areas even in Passive mode
- The viewport corner style matches the active viewport mode

### Viewport Modes

Three viewport modes define the visual relationship between the viewport and the surrounding DASH interface. The mode is selected in Appearance → Viewport and forms part of the preset system.

**Flush**
The default. All bars, panels, and the viewport share square edges and sit flush against each other and the screen edges. No rounding, no overlap, no transparency. Everything occupies its own defined rectangle. Nothing bleeds into anything else. Clean, functional, immediately legible.

**Dominant**
The viewport appears to float above the surrounding interface. The viewport has rounded corners and a subtle elevation shadow. The interface chrome recedes visually as a background layer. The app content is the hero of the screen. Bold and modern in character.

**Passive**
The viewport extends underneath floating bars and panels which float above it as an overlay layer. Apps bleed edge to edge or close to it. Bars use semi-transparent or frosted glass backgrounds, hinting at the content beneath. The interface feels minimal and immersive. Maximum visual real estate for the app. Frosted glass requires Android 12 or above — older versions receive a semi-transparent colour approximation.

---

## The Module Panel

The module panel is the display area for installed accessory modules. DASH draws the container. The module fills it entirely. The module is king within its own domain — background colour, fonts, layout, controls, and all visual content within the panel boundary are defined by the module exclusively. DASH does not alter, override, or offer settings for module panel content.

### Docking

The module panel docks to any of the four screen edges — top, bottom, left, or right.

**Orientation is automatic based on docking edge:**
- Top or bottom edge → horizontal orientation → h_ layout slots used
- Left or right edge → vertical orientation → v_ layout slots used

The user can override the automatic orientation if preferred.

### Panel Sizes

Three sizes defined relative to system bar height:
- **Small** — 1× system bar height
- **Medium** — 2× system bar height
- **Large** — 4× system bar height

### System Bar Relationship

The module panel never overwrites or overruns the system bar. If the system bar is at the top and the module panel is docked to the right, the module panel's top edge begins where the system bar ends. The system bar is always the senior element.

### Persistent and Floating Modes

**Persistent** — the panel is always visible and always occupies its defined screen space.

**Floating** — the panel is hidden by default. A peek strip — a few dp of the panel edge — remains visible at the docking edge, acting as a visual handle and swipe target. The user swipes inward from the peek strip to reveal the full panel. The panel tracks the swipe gesture in real time, feeling physically pulled from behind the screen edge.

The Module Panel Reveal element on the system bar provides an alternative reveal mechanism. Tapping it reveals the panel. Tapping again cycles through installed modules. This is optional — the user places it on the system bar if they want it.

### Stacking and Floating Rules

**If both the module panel and app launcher are persistent** they may share the same edge and stack. On a bottom edge the app launcher sits above the module panel, closer to the content area.

**If either the module panel or app launcher is floating** they must occupy different edges. Two floating panels on the same edge creates an ambiguous swipe interaction that cannot be resolved reliably.

### Switching Between Modules

Installed modules are cycled by swiping within the panel:
- Horizontal panel → swipe left and right
- Vertical panel → swipe up and down

---

## The App Launcher

The app launcher is a two-tier system providing quick access to pinned favourite apps and a full library of all installed apps.

### Tier One — Favourites Bar

A persistent or floating strip of pinned app icon slots. The user defines the number of slots — this number drives the bar dimensions. DASH calculates icon size from the available bar length divided by the slot count, respecting minimum and maximum icon size limits. The user never sets bar width or icon size directly.

Empty slots display as visible placeholders — a subtle outlined space indicating room is available. If the user wants no empty spaces they set the slot count to match the number of apps they intend to pin.

The Favourites Bar may be:
- Absent entirely — the user uses only the full launcher tray
- Persistent — always visible as a strip
- Floating — hidden with a peek strip, revealed by swipe or system bar element

### Tier Two — Launcher Tray

The full app library. Opens from the favourites bar by swiping outward, or directly from a system bar element if no favourites bar is configured.

**Contents:**
- App grid showing all installed apps
- Recently used apps surfaced at the top of the grid
- Search bar at the top filtering both apps and DASH functions
- Long press any app to pin it directly to the favourites bar

### App Launcher Reveal Element

An optional system bar element — displaying a grid icon — that opens the launcher tray directly. If a favourites bar is configured, tapping the element reveals it first. If no favourites bar is configured, tapping goes straight to the full tray.

---

## Overlays

Overlays are transient UI layers that appear above the entire interface — above the viewport, above all bars and panels — to deliver contextual information without interrupting the user's primary task. They appear, communicate, and dismiss without requiring user action unless configured to persist.

### Two Overlay Categories

**Transient Overlays**
System state notifications. Volume changes, input selection, brightness adjustments. Consumed in the moment, no value in reviewing later. Auto-dismiss after 3 seconds by default. Not logged, not counted, not badged.

**Notification Overlays**
Communication and event notifications. Messages, missed calls, app alerts. Logged to notification history. A count badge on a system bar element shows unread notifications. Auto-dismiss after 5 seconds by default, or persistent until tapped — user configurable. When persistent and the vehicle is moving, auto-dismiss after 5 seconds overrides the persistent setting if vehicle speed data is available.

### Overlay Appearance

Each overlay defines:
- **Shape** — Rectangle, Rounded Rectangle, Pill, or Circle
- **Size** — Small, Medium, or Large within soft limits
- **Position** — Top centre, Bottom centre, Left centre, Right centre, or four corners
- **Opacity** — 0–100%
- **Background colour** — from theme tokens
- **Duration** — per overlay instance

### Overlay Content

An overlay contains any combination of icon, label, value, and progress or level indicator. Content is readable at a glance. Overlays are not designed for detailed reading — they deliver a single piece of information quickly.

### Multiple Overlays

When two overlays fire in rapid succession the second replaces the first immediately. For automotive use the current state is always more relevant than the previous state.

### Overlay Triggers

Any system message, module trigger, or Android notification can be mapped to an overlay in Notification settings. The user maps the event to an overlay definition. DASH fires the overlay when the event occurs. The mapping is user-configurable — no event is hardcoded to any specific overlay.

**Example mappings:**
- SYSTEM:volume:changed → Volume transient overlay
- MODULE:A4CF12:TRIGGER:filter_warning → Filter warning notification overlay
- SYSTEM:reverse:active → Reverse camera overlay (full screen or partial)
- Android message notification → Message notification overlay

### Overlay SDK

DASH provides an Overlay SDK allowing developers and users to build custom overlays. A custom overlay package placed in the overlays folder appears in the overlay library automatically. Custom overlays are mapped to triggers in the same settings panel as built-in overlays.

**An overlay package contains:**
- A declaration file stating name, description, version, and category
- Compose implementation for each supported shape variant
- Default duration and dismiss behaviour
- Theme token consumption declarations

---

## Navigation Model

DASH implements a simplified automotive navigation model. Traditional Android navigation concepts are replaced with more appropriate automotive equivalents.

**No recents panel.** The launcher tray provides instant access to any installed app. An app switcher adds unnecessary complexity. If the user wants Maps they tap Maps. If they want Spotify they tap Spotify.

**Back is optional.** A Back Button element can be placed on the system bar by the user if desired. When the Android navigation bar is hidden, swiping up from the very bottom of the screen temporarily reveals the transient navigation bar — a native Android behaviour that cannot be suppressed — providing back access when needed. As a future enhancement, DASH may detect when an app has a back stack and display the back element contextually only when it is useful.

**Home is an optional element.** The settings button is the only mandatory system bar element. Home behaviour — returning to the default startup app — is provided by the optional Home element or by selecting the default app from the launcher.

**Android navigation bar** is hidden by default on SBC installations where DASH has system-level access. On tablet proof-of-concept installations, three-button navigation is the fallback — it has no conflicts with DASH gesture interactions.

**Gesture conflict management.** DASH uses edge swipes for floating panel reveals. Android's full gesture navigation also uses edge swipes for back. DASH addresses this by hiding Android navigation entirely on supported installations. On installations where this is not possible, gesture exclusion rectangles are declared for DASH panel areas. The absolute bottom edge is avoided for all DASH swipe interactions to prevent conflict with Android's persistent home gesture zone.

---

## Settings Panel

The settings panel is accessed via the Settings Button element on the system bar. It occupies the full screen with the exception of the system bar, which remains visible and accessible at all times.

### Visual Identity

The settings panel is not user-definable in its visual appearance. It inherits its visual identity from the active system bar theme — accent colours, fonts, font colours, and scale are all derived from the system bar's current configuration. The design language is consistent and coherent. If the system bar is flush, the settings panel is flush against it. If the system bar is floating glass, the settings panel fills the full screen with the system bar floating above it.

### Navigation Structure

Three-level progressive navigation with animated transitions.

**Level One** — Major category column on the far left. Always visible. Appearance, Modules, Transports, Vehicle, Audio, Notifications, Apps, System, Developer.

**Level Two** — Subcategory column appears to the right when a major category is selected. The major category column remains visible.

**Level Three** — When a subcategory is selected, the major category column slides off to the left. The subcategory column shifts left into the vacated space. The content area expands to fill the majority of the screen. A back affordance — the subcategory column or a subtle arrow — slides the major column back in when the user wants to navigate elsewhere.

### Settings Tree

```
DASH Settings
│
├── Appearance
│   ├── Presets
│   │   ├── Built-in presets (Flush, Dominant, Passive)
│   │   ├── User saved presets
│   │   └── Import / Export preset
│   ├── Viewport
│   │   ├── Viewport mode (Flush / Dominant / Passive)
│   │   ├── Corner radius
│   │   └── Shadow settings
│   ├── System Bar
│   │   ├── Position (top / bottom)
│   │   ├── Height
│   │   ├── Zone configuration
│   │   │   ├── Number of zones (1–3)
│   │   │   └── Zone divider positions
│   │   ├── Element layout editor
│   │   └── Element vertical positioning
│   ├── Panels
│   │   ├── Module Panel
│   │   │   ├── Docking edge
│   │   │   ├── Persistent or floating
│   │   │   ├── Peek strip size
│   │   │   └── Default size (small / medium / large)
│   │   └── App Launcher
│   │       ├── Persistent or floating
│   │       ├── Docking edge
│   │       ├── Favourite slot count
│   │       └── Reveal method (swipe / element / both)
│   ├── Colours
│   │   ├── System bar colour
│   │   ├── Panel colours
│   │   ├── Accent colours
│   │   ├── Font colours
│   │   └── Window border colours
│   ├── Fonts
│   │   ├── System font selector
│   │   └── Font size per element type
│   ├── Spacing
│   │   ├── Element vertical position within bar
│   │   └── Zone padding
│   ├── Overlays
│   │   ├── Shape preset
│   │   ├── Size
│   │   ├── Position on screen
│   │   ├── Opacity
│   │   ├── Transient duration
│   │   ├── Notification duration
│   │   └── Dismiss behaviour (auto / tap to dismiss)
│   ├── Density
│   │   ├── App density preset
│   │   └── DASH UI scale preset
│   ├── Splash Screen
│   │   ├── Image or colour selection
│   │   └── Display duration
│   └── Ambient Mode
│       └── (to be defined)
│
├── Modules
│   ├── Discovery
│   │   └── Search for modules
│   └── Installed Modules
│       └── Per module
│           ├── Enable / disable
│           ├── Transport assignment
│           ├── Relay signal subscriptions
│           └── Remove / uninstall
│
├── Transports
│   ├── USB Serial
│   ├── WiFi
│   ├── Bluetooth Classic (SPP)
│   ├── Bluetooth LE
│   ├── UART
│   ├── RS485
│   ├── CAN Bus
│   └── Ethernet
│
├── Vehicle
│   ├── CAN Patch Bay
│   ├── OBD2
│   ├── Signal Slots
│   ├── DBC Profiles
│   └── Vehicle Profile
│
├── Audio
│   ├── Output selection (digital / analogue)
│   ├── Audio routing
│   ├── Volume behaviour
│   └── Per app audio permissions
│
├── Notifications
│   ├── Per app notification management
│   ├── Duration settings
│   ├── Driving mode rules
│   └── Notification history settings
│
├── Apps
│   ├── Installed apps
│   ├── Default app assignments
│   ├── App permissions
│   └── Storage management
│
├── System
│   ├── WiFi → (Android deep link)
│   ├── Bluetooth → (Android deep link)
│   ├── Display → (Android deep link)
│   ├── Storage → (Android deep link)
│   ├── Accessibility → (Android deep link)
│   ├── Date and Time → (Android deep link)
│   └── About DASH
│
└── Developer
    ├── (Safety acknowledgement — shown once)
    ├── CAN Logger
    ├── Transport Diagnostics
    ├── Element SDK Tools
    ├── Overlay SDK Tools
    ├── Signal Monitor
    ├── Log Viewer
    └── Version and build info
```

> **Reconciliation addendum — 2026-07-20 (roadmap 1.5.1).** The tree above is the original settings structure. Ahead of building the settings panel (1.5.x) it was walked against everything actually built in 1.1.x–1.4.x and against the 1.5.x implementation plan, and reconciled. The tree above is kept for the record; **the reconciled structure below is the one 1.5.x implements.** Nothing here changes a *feature* — only where its settings live in the tree, and how the tree maps to the three-level navigation.
>
> **What changed, and why:**
>
> 1. **New top-level category: Layout.** The five *placeable surfaces* — System Bar, Module Panel, App Launcher, Elements, Overlays — are lifted out of Appearance into their own top-level **Layout** category. *Why:* these are the discrete things DASH draws and positions (where they dock, how big, what reveals them), which is a different concern from the cross-cutting visual skin (colours, fonts, density). This is the Module Mantra as a settings tree — the panel is a *wall* DASH owns (Layout), the module is the *king* inside it (Modules). It also keeps the three-level navigation honest: nesting these under Appearance would have added a fourth tier the major→subcategory→content model can't show, and it de-loads Appearance, which was carrying eleven subcategories. Appearance keeps only the visual skin: Density, Splash Screen, Colours, Fonts, Presets, Ambient Mode.
>
> 2. **Panels dissolved into Layout.** The old `Appearance → Panels → {Module Panel, App Launcher}` grouping is flattened: Module Panel and App Launcher become direct Layout subcategories, siblings of System Bar. *Why:* with Layout as the surfaces category, the intermediate "Panels" node is redundant, and it keeps every surface at the same navigable depth. The stacking rule (a floating module panel and a floating launcher cannot share an edge) is still configured in one place — both now sit in Layout.
>
> 3. **Overlays split by concern.** Overlay *appearance* (shape, size, position, opacity, durations, dismiss behaviour) lives in **Layout → Overlays**. Overlay *trigger mapping* (what system signal, module trigger, or Android notification fires an overlay) lives in **Notifications → Overlay trigger mapping**. *Why:* these are two genuinely different jobs — styling a surface vs. wiring an event to it — and the trigger side was always described as living in Notification settings (see the Overlays section of this document). One feature, two correct homes.
>
> 4. **System Bar entry corrected.** In settings, System Bar is **Position + an Edit Bar Layout entry point + Reset** — not flat height/zone/element sliders. *Why:* 1.3.9 and 1.3.13 deliberately moved bar height, zone count, and element sizing *inside* edit mode so the user sees the result live while editing. The settings subcategory is the door to that workspace, not a duplicate set of controls.
>
> 5. **Spacing subcategory dropped.** The old `Appearance → Spacing` (element vertical position within bar, zone padding) is removed as redundant — its contents duplicate the System Bar element-positioning that now lives in edit mode. If a genuine cross-cutting spacing control emerges later it can return as its own thing.
>
> 6. **The work-in-progress convention.** The tree is complete and navigable from the shell version (1.5.2), but most tabs are placeholders for features not yet built. A placeholder is honestly labelled ("arrives with vX.x") and navigable, not a dead control. Each lights up at its own feature's version — Module Panel at 1.6.x, Viewport at 1.7.x, App Launcher at 1.8.x, Elements at 1.9.x, the theming/overlays/audio/notifications/apps tabs across version 2, vehicle/CAN in version 3. Viewport currently has **no** settings subcategory at all (removed here); its mode/corner-radius/shadow controls arrive with 1.7.x and a Viewport home is decided then (Appearance-as-look vs Layout-as-surface).
>
> **The reconciled top-level structure:**
>
> ```
> DASH Settings
> ├── Appearance    — the visual skin: Density, Splash Screen, Colours, Fonts, Presets, Ambient Mode
> ├── Layout        — the placeable surfaces: System Bar, Module Panel, App Launcher, Elements, Overlays
> ├── Modules       — Discovery; Installed Modules (per module: enable/disable, transport assignment,
> │                   relay subscriptions, uninstall)
> ├── Transports    — a generic list driven off TransportManager (USB, WiFi, Bluetooth today; future
> │                   transports auto-listed), each with enable/disable + its own configuration
> ├── Vehicle       — CAN Patch Bay, OBD2, Signal Slots, DBC Profiles, Vehicle Profile   (v3)
> ├── Audio         — output selection, routing, volume behaviour, per-app audio permissions   (v2)
> ├── Notifications — notification suppression (capability-detected), overlay trigger mapping,
> │                   per-app management, durations, driving-mode rules, history   (v2)
> ├── Apps          — installed apps, default assignments, permissions, storage   (v2/v3)
> ├── System        — Android deep-links (WiFi/Bluetooth/Display/Storage/Accessibility/Date&Time)
> │                   + About DASH
> └── Developer     — safety acknowledgement, Serial Monitor, Signal Monitor, transport diagnostics,
>                     log viewer, CAN Logger (v3), SDK tools (v3)
> ```
>
> **Notification suppression** (a master toggle making DASH the sole notification surface) is filed under Notifications, not Appearance, and is a capability-detected feature — it needs elevated access a Bronze sideload lacks, so it unlocks on Silver/Gold system-app hardware and degrades silently on Bronze. It is not a mere preference toggle; it gets its own designed capability path when built.

> **Settings panel build — 2026-07-20 (roadmap 1.5.2).** The shell was built and two things in the sections above settled differently in practice; recorded here per the additive-docs rule (the originals are kept).
>
> **Navigation is two-pane, not three-column.** The "Navigation Structure" above describes three columns (major stays visible, subcategory to its right, content on a third). In build it became a cleaner two-pane model, agreed with Roger: the left margin shows the **main tree** (the ten categories); tapping a category makes its **subcategories grow in and the main tree drop out of view** — never three columns at once, only the main tree *or* one subtree. The **content box** appears on the right when a category is chosen (soft-radius, `backgroundColourSecondary` fill), auto-opening the first subcategory. A **back button pinned to the bottom of the left margin** walks up one level — subtree → main tree — and closes the panel when there is no level left to climb. This supersedes the three-column description for version 1. The left column's width and row spacing scale with Android's font-size setting so labels never wrap or crowd.
>
> **The panel rolls out from the bar.** It grows from the bar's edge like a blind — an explicitly animated height, not a fade — on the same `backgroundColourPrimary` fill so there is no seam, with the bar floating above it and staying reachable. Open and close both take the **user-configurable transition length**: a new Appearance setting (`LocalTransitionMillis`, presets INSTANT → CINEMATIC), because DASH has no opinion on how fast the user's interface should move. The **settings button toggles** the panel — a press opens it, a second press closes it — and is now a real vector icon that fills its cell, tinted from `iconColourPrimary`.
>
> **Panel bounds — the panel is not always full screen.** It covers the launcher and the viewport, sits **below** the bar (never over it), and **conforms to the module panel**: it yields to a persistent (non-retractable) module panel, keeping it fully visible, and covers a retracted one. This is the Module Mantra as layout — DASH's own settings chrome never sits on top of the king's castle.

### Developer Tab

The Developer tab is openly accessible — no passcode, no gesture, no hidden unlock sequence. DASH trusts its users with all of its tools.

On first entry the user sees a single acknowledgement screen. Plain language, honest tone. Something like: these tools give you direct access to DASH internals — changes here can affect system stability, proceed thoughtfully. A single confirm button. Remembered after first acknowledgement. The warning can be shown again from within the tab if the user wants a reminder.

This is a safety notice, not a locked door.

---

## The Three Extension SDKs

DASH provides three extension SDKs that together cover every layer of the interface. They share the same underlying philosophy — DASH defines the rules and the container, the developer defines the content.

**Module Protocol** — for external hardware modules describing their UI panels and data. Defined in transport.md.

**Element SDK** — for bar components. Elements that live inside zones on the system bar. Built-in elements and community elements use identical specifications.

**Overlay SDK** — for notification and state overlays. Built-in overlays and community overlays use identical specifications.

A motivated developer could rebuild the entire DASH visual experience using only community components without touching the core platform. The core is stable. The surface is infinitely extensible.

---

## Soft Limits and Hard Floors

DASH enforces sizing constraints through two mechanisms.

**Soft limits** apply to most user-configurable sizes. When an element or panel approaches a threshold below which it becomes difficult to use or read at dashboard viewing distance, DASH shows a subtle amber indicator in the settings editor. The user can acknowledge and proceed. The soft limit is education, not restriction.

**Hard floors** apply only to safety-critical elements. The settings button touch target has a hard minimum of 48dp. It cannot be configured below this value. DASH enforces it silently — the user never needs to know it exists. The visible icon may scale with the bar but the interactive area never goes below 48dp.

Soft limits appear contextually in real time during adjustment — as the user drags a size control toward the threshold the indicator appears immediately. Not as a post-save validation, not as a modal warning. Just a gentle signal visible while the decision is being made.

> **Design decision — v1.3.4:** The amber soft limit model above was reconsidered during implementation for element sizing and replaced with boundary labels integrated directly into the control. When element height reaches its minimum, the value display changes to "min" and the decrease button greys out. When it reaches the ceiling, the display changes to "max" and the increase button greys out. The reason for this change is that a separate amber indicator is a more complex mechanism than the situation requires. The original model describes a warning that appears *near* a threshold, implying the user can proceed past it — soft limit as advisory. But the element size floor and ceiling are not advisories; they are the actual boundaries of the control. Communicating the boundary through the control itself — changing the label, disabling the button — is simpler, more honest, and requires no separate warning infrastructure. The information the user needs is in the thing they are interacting with, at the moment they need it. The hard floor on the settings button touch target (48dp) is unchanged and continues to be enforced silently.

---

## Design Principles Summary

These principles govern all interface decisions in DASH. Any feature that touches the interface must respect them.

**1. The user is master**
DASH has no opinion about what the interface should look like. Every visual and layout decision belongs to the user.

**2. Get out of the way**
The interface exists to serve the user's goals. It never imposes, never restricts, never decides for the user what they should see or how they should interact.

**3. The module is king within its domain**
DASH draws the module panel container. The module fills it. DASH does not alter, style, or override any content within the module panel boundary.

**4. Everything is optional except the mandatory two**
The alerts area and the settings button are the only non-negotiable elements. Everything else — panels, elements, overlays, the launcher, even home and back — is the user's choice to include or exclude.

**5. Progressive disclosure**
Features are available when the user goes looking for them. The default experience is clean and uncluttered. Complexity is opt-in.

**6. Consistent design language**
The settings panel, overlays, and all DASH chrome inherit their visual identity from the user's chosen theme. Nothing feels like a different application.

**7. The viewport belongs to the app**
When an app is running, the viewport is its domain. DASH does not interfere with what an app renders within the viewport boundary.

**8. Safety through design**
The alerts area is always visible. The settings button is always reachable. The back mechanism is always available through the transient navigation bar. The user can never be stranded.

---

*This document is the authoritative reference for DASH interface architecture and design. All interface development must conform to the definitions and principles contained here.*
