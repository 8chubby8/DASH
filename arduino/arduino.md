# DASH Module SDK — Working Record

This document is the working record for the DASH module firmware / SDK design.
It captures the agreed **Module Definition** — the rules any module (built-in or
community) follows to talk to DASH — plus the items still open.

It is the **living working record** for the module SDK, and is part of the DASH
document set (see CLAUDE.md). It is *not* a locked Bible document — it is updated
freely as the SDK design progresses, and **nothing here changes transport.md**.
Once the SDK is locked, the agreed rules will be promoted — either into
transport.md (on Roger's express instruction) or into a dedicated module-rules
document.

---

## Purpose

We are writing reference firmware against the DASH protocol in order to
pressure-test it and settle every ambiguity a real module exposes. The end
product is a **locked module SDK**, with the firmware doubling as:

1. The conformance test rig for the DASH-side transport layer (roadmap 1.4.x,
   not yet built).
2. The reference implementation community module builders will copy.

This honours the **SDKable principle**: built-in modules get no special access a
community builder couldn't have. The reference firmware *is* that level playing
field, made concrete.

---

## The Plan

Two reference modules, on hardware Roger already has:

| Order | Board         | Module type | Why |
|-------|---------------|-------------|-----|
| 1st   | Arduino Uno Q | SYSTEM      | Exercises the full core loop (discover → install → live data → relay) with **no** heavy payload (no icon, no layouts). Cleanest start. |
| 2nd   | ESP32         | ACCESSORY   | Adds the hard problems — shipping an icon and layout panels out of flash. Built once the core is proven. |

---

# Module Definition

The agreed, current design. This is the concept laid down for building the SDK.

## Cheat sheet (quick reference)

Every message: `TYPE | id | function | value…` — pipe-separated, positional,
trailing fields optional. `id` = the 12-hex MAC.

**Module types** (a module is exactly one):

| Type | Role |
|---|---|
| `SYSTEM` | Produces standard DASH signals. Send-only (`BROADCAST`). |
| `ACCESSORY` | Owns an agnostic interactive panel. Two-way (`REPORT` out, `ACTION` in), raises `TRIGGER` alarms, ships visual assets. |
| `LISTENER` | Consumes standard signals (`SUBSCRIBE` → `LISTEN`) and acts on its own hardware. |

**Lifecycle / commands:**

| Message | Dir | Purpose |
|---|---|---|
| `DISCOVER` | DASH→all | Announce yourselves (the one message with no id). |
| `HELLO\|id\|type\|name\|description\|version` | mod→DASH | Identity reply to `DISCOVER`. |
| `INSTALL\|id` | DASH→mod | Begin the install handshake. |
| `ACTIVATE\|id` / `DEACTIVATE\|id` | DASH→mod | Start / stop sending live data (acknowledged). |
| `ROGER\|id\|which` | mod→DASH | Confirms a command was carried out. |

**Live data (the 2×2):**

| Message | Dir | Purpose |
|---|---|---|
| `BROADCAST\|id\|function\|value` | mod→DASH | Standard signal out — general / pub-sub. |
| `LISTEN\|id\|function\|value` | DASH→mod | Standard signal delivered to a subscriber. |
| `REPORT\|id\|variable\|value` | mod→DASH | Module's own panel data — specific / private. |
| `ACTION\|id\|control\|value` | DASH→mod | User operated one of the module's panel controls. |
| `TRIGGER\|id\|name` | mod→DASH | Agnostic alarm → system bar (icon; ACCESSORY only). |

**Install-time declarations** (sent once, during the handshake):

| Message | Dir | Purpose |
|---|---|---|
| `SYSTEM_SIGNAL\|id\|function` | mod→DASH | SYSTEM declares a signal it will broadcast — one per line. |
| `SUBSCRIBE\|id\|function\|rate\|threshold\|gate\|gate_value` | mod→DASH | LISTENER subscribes to a signal with optional throttle/gate — one per line. |
| `MANIFEST\|id\|blocks\|bytes` | mod→DASH | ACCESSORY asset table-of-contents + total size. |
| `BLOCK\|id\|name\|length\|crc` + raw bytes | mod→DASH | ACCESSORY one asset (icon/panel), length-prefixed, CRC-checked. |
| `INSTALL_END\|id` | mod→DASH | Ends the handshake. |

Pairings to remember: `SYSTEM_SIGNAL`→`BROADCAST` and `SUBSCRIBE`→`LISTEN` are
each "declare at install → message at runtime"; `REPORT`↔`ACTION` are the two
halves of one private panel wire.

## 1. The wire — framing

- A module and DASH talk in **plain UTF-8 text, one message per line**.
- Each line ends with a newline. DASH **emits** `\n`; DASH **accepts** both `\n`
  and `\r\n` (it trims a trailing `\r`). So a standard `Serial.println()` works.
- Text, not binary, so the conversation is readable in any serial monitor —
  debuggability is a first-class goal.

## 2. The message structure (one grammar for everything)

Every message has the same shape, fields separated by the pipe `|`:

```
TYPE | id | function | value1 | value2 | value3
```

- **Positional.** The meaning of each field is fixed by position.
- **Fields fall off from the right.** A message uses as many fields as it needs;
  unused trailing fields are simply absent. You may **not** skip a field in the
  middle — if a later field is present, all earlier ones must be too. Legal
  shapes: `TYPE`, `TYPE|id`, `TYPE|id|function`, `TYPE|id|function|value1`, …
- **Field 1 (TYPE)** declares what the message is and therefore how to read the
  rest.
- **Field 2 (id)** is the module ID. **Every message names its module**, in both
  directions (see §4). The one bootstrap exception is `DISCOVER`, which has no
  target (it goes to everyone).
- **Fields 3+** are payload; their meaning is defined per TYPE.

### Delimiter and content rules

- The delimiter is the pipe **`|`** — chosen because it essentially never appears
  in real names, descriptions or data, so forbidding it costs nothing.
- **Exactly two characters are forbidden in any field content:** the newline
  (it ends a message) and the pipe `|` (it separates fields). **Everything else
  is allowed** — including colons, so a value like `12:30` or `21.5` is fine.
- Because the delimiter never appears in content, **parsing is a plain split on
  `|`** — no escaping, no clever "split only the first N" logic. The SDK strips
  the two forbidden characters from any field before sending.
- **Forward compatibility:** a receiver **ignores extra trailing fields it does
  not recognise.** This lets the structure grow (a future `value2`/`value3`)
  without breaking older firmware.
- **One value per field — no embedded lists.** A field never holds a
  comma-separated (or otherwise sub-delimited) list. To declare several things,
  send several messages — one item per line. (This is why a module declares each
  signal it sends/subscribes to on its own `SYSTEM_SIGNAL` / `SUBSCRIBE` line,
  rather than a single comma-joined line.) Keeps parsing a single plain split with
  no nested grammar anywhere.

## 3. Module identity (the id)

- The id is the full **12-hex-character MAC address**, separators removed,
  uppercase — e.g. `A4CF12B8D9E0`. (A bare MAC is written `A4:CF:12:B8:D9:E0`;
  the colons are dropped.)
- The MAC is factory-assigned and globally unique, so there is zero risk of two
  modules clashing and zero management for the builder — firmware just reads its
  own MAC at boot.
- **MAC-less fallback:** a board with no networking (e.g. a classic Arduino Uno
  R3) has no MAC. There the builder must assign a unique id themselves. Rule:
  *use the MAC where you have one; otherwise it is on you to guarantee
  uniqueness.*
- The id is load-bearing in three places — message routing, install/
  reconciliation records, and addressing a specific module on a shared bus — so
  its uniqueness matters.

## 4. The message vocabulary

**DASH → module** (commands). No `DASH:` prefix — the TYPE word already says it
is a command:

| Message | Meaning |
|---|---|
| `DISCOVER` | Broadcast to everyone: announce yourself. |
| `INSTALL\|id` | Set this module up (begins the install handshake). |
| `ACTIVATE\|id` | You are installed — start sending live data. |
| `DEACTIVATE\|id` | Stop sending. |
| `LISTEN\|id\|function\|value` | Here is a **standard** signal you subscribed to. |
| `ACTION\|id\|control\|value` | A user operated one of **your panel's** controls — **agnostic** input. |

**module → DASH:**

| Message | Meaning |
|---|---|
| `HELLO\|id\|type\|name\|description\|version` | Discovery response (identity). `type` is the module's single type (see §4a) — one word, never a combination. Transport is **not** included — DASH knows it from the connection. |
| `BROADCAST\|id\|function\|value` | Standard DASH signal (headlights, reverse…). |
| `REPORT\|id\|variable\|value` | The module's own agnostic data. |
| `TRIGGER\|id\|name` | An agnostic alarm → system-bar alert area (carries an icon; ACCESSORY only). |
| `ROGER\|id\|which` | Thumbs-up confirming a command was carried out. |

**Examples** (full MAC shown; shortened elsewhere for readability):

```
DISCOVER
HELLO|A4CF12B8D9E0|ACCESSORY|Climate Control|Dual zone cabin temp & fan|v1.2
INSTALL|A4CF12B8D9E0
BROADCAST|A4CF12B8D9E0|reverse|active
REPORT|A4CF12B8D9E0|temperature|21.5
LISTEN|A4CF12B8D9E0|headlights|on
ACTION|A4CF12B8D9E0|fan_up|on
```

**Symmetry to note.** The four data messages form a 2×2 — *general vs specific*
(does it go to everyone, or to one place?) × *send vs receive* (from the module's
point of view):

| | **send** (module→DASH) | **receive** (DASH→module) |
|---|---|---|
| **general** (standard signals — pub/sub) | `BROADCAST\|id\|function\|value` | `LISTEN\|id\|function\|value` |
| **specific** (agnostic — the module's own panel) | `REPORT\|id\|variable\|value` | `ACTION\|id\|control\|value` |

The naming *style* matches the channel's *shape*. Standard signals are a shared
bus, so they get pub/sub verbs — you `BROADCAST` to all, you `LISTEN` for what you
want. The agnostic panel is a private one-to-one wire, so it gets the directed
pair — you `REPORT` your data out, and an `ACTION` comes back in. Same id slot,
same payload shape across all four.

## 4a. Module types

A module is exactly **one** type. The `type` word in `HELLO` is a single word —
**never** a combination. The three types are mutually exclusive:

| Type | What it does |
|---|---|
| `SYSTEM` | Sends standard DASH signals — DASH's known vocabulary (reverse, headlights, ignition). Send-only. |
| `ACCESSORY` | Owns an agnostic interactive panel — things DASH knows nothing about; the module is king inside its box. **Two-way:** ships the panel + its data (`REPORT`) and triggers, **and receives interactions with it** (`ACTION`). |
| `LISTENER` | Receives subscribed **standard** signals from DASH (relays) and acts on them — e.g. switches its own hardware. |

**A module does one job.** To do several jobs, a board presents **several
modules** — one per job, each with its own id and its own single type. To DASH
they are fundamentally separate modules that happen to share a board.

Example — one board, three ids, one of each type:
- a `SYSTEM` module reporting the vehicle's exterior lights,
- an `ACCESSORY` module drawing a climate panel,
- a `LISTENER` module switching an aux relay on a subscribed signal.

DASH sees **three modules**. It never knows (or cares) they share silicon — what
happens inside the board is the module's business (module is king). The firmware
just honours **each module's lifecycle independently**: discover, install,
activate and uninstall happen **per id**. (Unique id per face is easy — an ESP32
has its base MAC plus derived per-interface MACs; otherwise the builder assigns
per the §3 fallback.)

**Why these three, and why no HYBRID:**
- The real fault line is *language*: **SYSTEM speaks DASH's known vocabulary;
  ACCESSORY speaks its own and DASH doesn't care.** `LISTENER` is the inbound
  counterpart on the **standard** side — standard signals flowing *to* a module so
  it can act.
- **Two kinds of "inbound" — keep them apart.** A `LISTENER` module takes in
  **standard** signals (relays). An `ACCESSORY` takes in **its own panel's**
  interactions (`ACTION`) — fully agnostic, intrinsic to having an interactive
  panel. Different mechanisms, different domains; that's why ACCESSORY is two-way
  but isn't a `LISTENER`. (A climate panel that *also* wants standard signals like
  headlights relayed is still an `ACCESSORY` face **plus** a `LISTENER` face.)
- **HYBRID is retired.** It only ever meant "two jobs in one," which is now
  simply two modules on one board.
- **Triggers belong to ACCESSORY only.** A trigger carries an **icon**, and only
  an ACCESSORY ships assets — so SYSTEM and LISTENER have nothing to draw, and no
  agnostic alarm to raise. Anything that must raise an alarm is an ACCESSORY.
- **Gap 8 resolved:** a `SYSTEM` module is send-only and cannot subscribe to
  relay. To receive a *standard* signal you add a separate `LISTENER` module.

**On the wire,** the `type` word in `HELLO` names the one type, and what the
module declares at install matches it — `SYSTEM_SIGNAL` lines (SYSTEM), panel/icon
`BLOCK`s (ACCESSORY), `SUBSCRIBE` lines (LISTENER).

## 5. The two layers inside DASH (source-awareness)

- **Receiving layer (gatekeeper)** — what the connections plug into. It is
  inherently source-aware: it knows which module/connection a message came from,
  and it blocks data from any module that is not currently installed and active.
- **Main system (core)** — acts on *meaning*, not on *who sent it*. It is
  sourceless: told "headlights on", it dims the screen regardless of source
  (this allows redundant sources for the same signal).

The id rides in every message for the **gatekeeper's** benefit. For `BROADCAST`
messages the gatekeeper consumes the id and hands the core a clean
source-free message, so the core stays sourceless (recommended; passing the id
through for the core to ignore is an equally valid alternative — it is a
DASH-internal detail, invisible to the module).

This is why there is no RS485 special case: because **every** message carries the
id, a shared bus is handled exactly like any other transport. The earlier
"add an address prefix only on RS485" idea is **superseded** by uniform ids.

## 6. Module lifecycle

**Rules:**
- Every module — SYSTEM included — must be **installed** before it sends any data.
  A module can exist and be discoverable without being installed.
- A module sends data **only** while active. This keeps the bus clear.
- On uninstall, DASH deletes the module's record **and** the module stops sending.

**Single source of truth is DASH.** The module persists *nothing* about its
install state. It boots **SILENT** (answers `DISCOVER`, sends no data). DASH holds
the installed list on disk and tells the module what to do each session. This
makes the rules self-enforcing: a module uninstalled while powered off simply
never gets activated next boot, so it stays silent — no desync, no "zombie".

**States:** `SILENT` (boot; identity only) → `ACTIVE` (after `ACTIVATE`; sends
data) → back to `SILENT` (after `DEACTIVATE`).

**INSTALL ≠ ACTIVATE:**
- `INSTALL` happens **once** — the full handshake; assets/declarations saved to
  disk.
- `ACTIVATE` happens **every boot** for an already-installed module —
  lightweight, no re-transfer.

**Boot reconciliation:** `DISCOVER` → match replies against the installed list →
`ACTIVATE` the matches. DASH keeps a **low-rate re-sweep** running so a slow
booter is picked up whenever it appears (the Arduino Uno Q's full Linux boot of
~20–30 s is the worst case — a fixed "3 tries then give up" would miss it).

**Absent = DORMANT, not an error, and transport-aware:**
- A **wired** module (UART/RS485) expected but absent → **fault** indication.
- A **wireless** module (WiFi/BT) absent → normal dormant, no alarm; auto-
  reconnects when it returns.
- A **SYNC** button offers a manual "check now" (e.g. after fixing wiring).

**Acknowledgements:**
- Live data (`BROADCAST`/`REPORT`) is **fire-and-forget** — stateless and
  self-healing; a lost value is overwritten by the next.
- `ACTIVATE`/`DEACTIVATE` are **acknowledged and retried** (the module sends
  `ROGER`), so DASH can be sure a module actually started/stopped.
- The install handshake is self-acknowledging — it ends with `INSTALL_END`.

**Uninstall robustness:** because the module remembers nothing, it cannot return
as a zombie after a reboot. A lost `DEACTIVATE` ack is at worst transient; DASH
deletes the record immediately and retries. If still unconfirmed, a **FORCE
UNINSTALL** is offered, with the warning: *"If you do this the module could still
send misleading data, so either disconnect the module or power-cycle the module
after force uninstall."* (Power-cycle the **module**, not DASH — DASH cannot stop
a powered module that's already running.)

## 7. Discovery & install handshake (the flow)

```
DASH   → DISCOVER                                  (to everyone)
module → HELLO|id|type|name|description|version    (each module that hears it)

— user picks a module and installs —

DASH   → INSTALL|id                                (only the matching module replies)

  — an ACCESSORY sends its assets (see §8) —
module → MANIFEST|id|blocks|bytes                  (table of contents + total size)
module → BLOCK|id|icon|1342|<crc>
         <1342 raw bytes>
module → BLOCK|id|h_small_light|980|<crc>
         <980 raw bytes>
module → ... (further BLOCKs)

  — a SYSTEM module declares each signal it sends, one per line —
module → SYSTEM_SIGNAL|id|reverse
module → SYSTEM_SIGNAL|id|headlights

  — a LISTENER module declares each signal it wants, one per line (see §9) —
module → SUBSCRIBE|id|headlights                            (plain on-change)
module → SUBSCRIBE|id|steering_angle|20hz||reverse|active   (throttled + gated)

module → INSTALL_END|id
DASH   → (validate, save the complete record to disk)
DASH   → ACTIVATE|id
module → ROGER|id|activate
         (live data now flows)
```

A given module sends **only its own type's** declarations — a SYSTEM module sends
`SYSTEM_SIGNAL` lines, a LISTENER module `SUBSCRIBE` lines, an ACCESSORY its
`BLOCK`s. The flow above stacks all three only to show them in one place.

What an install transfers depends on the module's **type** (§4a):
- **`SYSTEM`** → one `SYSTEM_SIGNAL` line per standard signal it **sends**. No visuals.
- **`ACCESSORY`** → its **visuals** (icon + layout panels, as `BLOCK`s), its
  trigger icons, its display **variables** (the `REPORT` data it pushes), **and its
  interactive controls** (each with an id, so DASH can route a `ACTION` event back
  when the user operates it). Variables out and control ids in are the two halves of
  the panel contract.
- **`LISTENER`** → one `SUBSCRIBE` line per signal it wants **delivered** (§9).

(A board doing several jobs installs several modules — one per type — each with
its own handshake.)

## 8. Asset transfer (the install payload)

ACCESSORY visuals (an SVG icon and up to twelve layout panels) are multi-line and
KB-scale, so they don't fit the one-line message rule. They are carried inside
the bounded **install handshake** as length-prefixed, checksummed blocks:

- `BLOCK|id|name|length|crc`, followed by exactly `length` raw bytes.
- The byte count makes the block **collision-proof** (content is read verbatim,
  newlines and all) and **bounded** (DASH can reject an oversize block before
  reading it). Begin/end sentinels were rejected — an SVG could legitimately
  contain the sentinel text.
- **CRC** (default **CRC32**) catches corruption. It is applied to the install
  payload **only**, because that data is **saved to disk** and rendered every
  boot — silent corruption there would persist. Live data needs no checksum (it
  self-heals). The firmware can precompute each asset's CRC at build time, so even
  a weak board pays nothing at install time.
- Assets live in **flash** (`PROGMEM`, or LittleFS/SPIFFS on ESP32) and are
  **streamed** out a chunk at a time — never held whole in RAM. A modest board
  can send a 5 KB icon with a tiny working buffer.
- On a CRC/length mismatch DASH aborts the bounded handshake cleanly; nothing
  half-corrupted reaches disk.

**Capability tiering (not a flaw):** a rich ACCESSORY is realistic on ESP32+
(megabytes of flash). A classic Uno R3 (32 KB) builds SYSTEM modules (no payload).

## 9. Streams — standard signals delivered to a LISTENER module

A `LISTENER` module subscribes, at install, to the signals it wants delivered. DASH
stores the subscriptions and delivers values at runtime. This lets a module react
to vehicle state without its own sensor — and because system signals are
sourceless, the state may originate from a *different* module entirely, mediated
by DASH, with neither aware of the other.

### One subscription message, one delivery message

```
subscribe (once, at install):  SUBSCRIBE | id | function | rate | threshold | gate | gate_value
deliver   (running):           LISTEN | id | function | value
```

- **Delivery is always plain `LISTEN`** — `LISTEN|id|headlights|on`,
  `LISTEN|id|steering_angle|360`. The module receiving a value never needs to know
  whether it came throttled or on-change; the cleverness all lives in the
  subscription.
- **There is no separate "boolean" vs "stream" subscription.** To DASH every
  signal is the same — it just relays values. A discrete on/off signal is simply a
  stream with no rate cap and no threshold. `SUBSCRIBE|id|headlights` (everything
  trailing left off) **is** an on-change subscription. *Everything a module
  receives is a stream; a boolean is just a slow one.*

### The four optional controls

All evaluated **inside DASH** (the gatekeeper/core), so the wire only ever carries
useful data — filter as far upstream as possible. All optional; blank or absent =
default:

| Field | Default | Meaning |
|---|---|---|
| `rate` | on-change (no cap) | Max delivery frequency, e.g. `20hz`. A time throttle. |
| `threshold` | any change | Minimum change before re-sending (deadband), in the signal's own units, e.g. `2`. A value throttle. |
| `gate` + `gate_value` | always active | A condition the stream runs **only** under, written as the usual `function value` pair — `reverse active`. While false, nothing is sent at all. |

```
SUBSCRIBE|id|steering_angle|20hz                     just rate-capped
SUBSCRIBE|id|steering_angle|20hz|2                   + deadband
SUBSCRIBE|id|steering_angle|20hz||reverse|active     + gate (blank threshold)
SUBSCRIBE|id|steering_angle|20hz|2|reverse|active    the lot
SUBSCRIBE|id|headlights                              plain on-change (a "boolean")
```

- **Gate is implicit equality.** `reverse active` means "while signal `reverse`
  equals `active`." (`<` / `>` and AND/OR are deliberately deferred — add only if a
  real need appears.) The gate references a standard signal DASH already holds in
  its sourceless core, so DASH can evaluate it with no module involvement.
- **Gate-open fires immediately.** When a gate flips false→true (you select
  reverse), DASH sends the current value at once, ignoring the threshold, so the
  module isn't stale on activation. Normal throttling then resumes; gate-close just
  stops.

### Shared-bus bandwidth (a DASH-side concern)

The gate does the heavy lifting — most streams are gated off most of the time, so
aggregate load stays low. The rate cap bounds the worst case, so DASH can sum
subscribed rates and know the ceiling. If a slow shared bus can't carry it, DASH
**clamps the effective rate and lets the module degrade gracefully** (fewer
updates) — never a hard refusal. The module declares what it would *like*; DASH
delivers what the bus can *bear*. (Detailed bus management is DASH 1.4.x, not
module-facing protocol.)

On a shared bus a delivered `LISTEN` carries the **target's** id, so the
addressed module acts and the rest ignore it — the mirror of upstream messages
carrying the **sender's** id.

## 10. Buffer & line sizes

- **Firmware receive buffer:** 64 bytes is enough — the longest inbound message
  is a `LISTEN`. Exposed as a tunable `#define`; lean default, raise if wanted.
- **Protocol max line ≈ 160 bytes**, a DASH-side concern only (Android has the
  RAM). It derives from the longest line — the `HELLO` response — via the field
  caps below.

**Field caps** (longest `HELLO` line derives from these):

| Field | Cap | Notes |
|---|---|---|
| id | 12 | MAC |
| type | 9 | single type word, e.g. `ACCESSORY` (§4a) |
| name | 24 | builder text |
| description | 64 | builder text (also a UI tidiness cap) |
| version | 12 | builder text |

---

## Open Items

Still to settle before the SDK is fully locked:

- **Asset sizing** — max icon size and max layout-panel size (bounds DASH's
  pre-flight check); final CRC width (CRC32 default vs CRC16 for very weak
  boards); mismatch recovery (abort whole install vs re-request one block);
  framing for VARIABLES and per-trigger icons (likely the same `BLOCK`
  mechanism). *(was Gap 7 sub-decisions)*

### Future additions (DASH-side — roadmap 1.4.x, not the module SDK)

- **Transport log / serial monitor.** Because every message is pipe-separated
  positional text, the wire is already a table (PSV). DASH can render a live,
  scrolling log with the pipes turned into aligned monospace columns: direction
  arrow (→ out / ← in), TYPE colour-coded, id, source/transport tag, timestamp,
  with filter / pause / clear and a raw↔table toggle (raw stays grep/copy-friendly).
  - **Two homes:** a **Transport Monitor** in the settings panel (the serious
    diagnostic surface), and a **live logger element/overlay** on the main
    viewscreen so you can *watch* a message go out the instant you press a panel
    control. Serves the brief's debuggability-first and openness principles.
  - **Architectural hook:** to build it the SDKable way, the **1.4.x transport
    layer must expose a read-only "tap"** — an observable message stream an
    element/overlay can subscribe to. Bake it in from the start. It doesn't dent
    module-is-king: a logger observes **the wire** (DASH's domain), never reaches
    inside a panel.

### Considered & parked

- **Module grouping** (`GROUP|groupid|id`, per-face, `groupid` = device base MAC).
  Lets DASH link several faces on one board so it can group them in the UI, fault
  them together, and offer "remove all." Sound design — per-face declaration (no
  privileged parent), globally-unique groupid (no collision). **Parked** as
  over-engineered: faces work fine fully independent, and we have no evidence yet
  of boards routinely presenting grouped faces. Revisit if a real need appears.
  An earlier variant — encoding the group into a short numeric id (3-digit subnet
  + 3-digit module) — was rejected: it throws away the MAC's global uniqueness,
  reintroduces assignment/collisions, and bakes topology into identity.

**Next:** begin the Arduino SDKable firmware work against this definition,
starting with the SYSTEM module on the Arduino Uno Q.

---

## Discussion Notes

### Session — 2026-06-22
First design session. Worked the protocol end to end and consolidated it into the
Module Definition above. Key arrivals:
- **Unified message structure** `TYPE|id|function|value1[|value2]`, positional,
  trailing-optional, single `|` delimiter, two forbidden characters (newline and
  `|`), simple split parsing, ignore-unknown-trailing-fields for growth.
- **Every message names its module** (id in field 2), both directions — fixing an
  inconsistency where SYSTEM messages had no id and RS485 needed a special prefix.
- **`HELLO`** replaces the old six-field discovery line; transport dropped (DASH
  infers it from the connection).
- **No `DASH:` prefix** on commands; the TYPE word carries the meaning.
- **Lifecycle = Model B** (DASH single source of truth), INSTALL ≠ ACTIVATE,
  transport-aware dormancy, acked stream control, force-uninstall safety.
- **Source-aware gatekeeper vs sourceless core** as an explicit layering.

**Superseded / removed this session:** the `:` delimiter; the Gap 3 limit-split
and escaping schemes (no longer needed once the delimiter is banned from
content); the RS485 address-prefix special case (replaced by uniform ids); the
`DASH:` command prefix; the `TRANSPORT|...`-first discovery line.

### Session — 2026-06-22 (second sitting)
Worked Gap 8 and it grew into a rework of the type model. Arrivals:
- **Three mutually-exclusive types** — `SYSTEM` / `ACCESSORY` / `RECEIVE` (§4a).
  A module is exactly **one** type; the `HELLO` `type` field is a single word,
  never a combination. **HYBRID retired.**
- **Combinations come from multiple modules, not from one module.** A board doing
  several jobs presents several modules — one per type, one id each — and to DASH
  they are fundamentally separate modules that share a board.
  - *(En route we briefly modelled these as composable flags on one module, then
    Roger corrected it: one module = one thing. Composable-flags model dropped;
    `RECEIVE` is a full type, not a flag.)*
- **The real fault line** is *language*: SYSTEM speaks DASH's known vocabulary;
  ACCESSORY speaks its own and DASH doesn't care. `RECEIVE` is the inbound side.
- **Triggers belong to ACCESSORY only**, because a trigger carries an icon and
  only an accessory ships assets. Anything that must raise an alarm is an ACCESSORY.
- **Gap 8 resolved:** a `SYSTEM` module is send-only and can't subscribe to relay;
  to receive a signal you add a separate `RECEIVE` module.
- **One board, several modules** confirmed (shared-bus-of-N, unique id per face,
  independent lifecycles).
- **Grouping** (`GROUP|groupid|id`) designed then **parked** (see Considered &
  parked above).

### Session — 2026-06-22 (third sitting)
Settled Gap 9 (relay/streams) and a related cleanup. Arrivals:
- **One subscription, one delivery.** Subscribe at install with
  `RELAY_STREAM|id|function|rate|threshold|gate|gate_value`; DASH delivers with
  plain `RELAY|id|function|value` for everything. The module never needs to know a
  value was throttled.
- **No boolean-vs-stream distinction.** Every received signal is a stream; a
  discrete on/off is just a stream with no rate cap and no threshold. So
  `RELAY_SIGNALS` is **retired** — folded into `RELAY_STREAM` with trailing fields
  left off. *(Gap 9 resolved.)*
- **Four optional controls**, all evaluated in DASH: `rate` (Hz cap), `threshold`
  (deadband, signal's own units), and `gate` + `gate_value` (run only while a
  standard signal equals a value). Gate is implicit equality; `<`/`>`/AND/OR
  deferred. Gate-open fires an immediate value; gate-close stops. Shared-bus
  overload → DASH clamps the rate and degrades gracefully, never hard-refuses.
- **Gate uses the universal `function value` pair** (`reverse active`), not a
  `reverse=active` mini-syntax — consistent with how signals appear everywhere else.
- **Comma-lists eliminated.** New §2 rule: *one value per field, no embedded
  lists* — declare several things with several one-per-line messages. This retired
  the comma-joined `RELAY_SIGNALS` **and** turned the send-side declaration into
  one `SYSTEM_SIGNAL` line per signal (was `SYSTEM_SIGNALS|id|sig1,sig2`). The
  protocol now has **no sub-delimiter anywhere** — parsing is one plain split.

### Session — 2026-06-22 (fourth sitting)
Closed a real hole and captured a spin-off idea.
- **ACCESSORY is two-way.** It had been defined send-only, but an interactive
  panel must receive its own control presses or it's just a picture. Added the
  agnostic inbound message `CONTROL|id|control|value` *(name provisional)* — the
  mirror of outbound `MODULE|id|variable|value`. Full symmetry now: standard
  `SYSTEM`↔`RELAY`, agnostic `MODULE`↔`CONTROL`.
- **Two distinct inbounds, kept apart:** `RECEIVE` takes in **standard** relays;
  `ACCESSORY` takes in **its own panel's** interactions. Different domains, so
  ACCESSORY is two-way without being a `RECEIVE`.
- **Panel contract** = display **variables** (out) + interactive **control ids**
  (in), both declared at install. Folds into the open asset/panel item.
- *(Open: final name for `CONTROL` — Roger to decide. Possible "RECEIVE" rename
  later, since two types now receive something.)*
- **Spin-off captured:** a **transport log / serial monitor** (the wire is already
  a PSV table) as a DASH-side 1.4.x addition, needing a read-only transport "tap"
  for the SDKable element/overlay version. Filed under Future additions.

### Session — 2026-06-22 (fifth sitting)
Renamed the four data-plane messages to a readable 2×2 (domain × direction,
direction from the **module's** point of view). Old → new:
- `SYSTEM` → **`SYSTEM_TX`** (module sends a standard signal)
- `RELAY` → **`SYSTEM_RX`** (DASH delivers a standard signal)
- `MODULE` → **`MODULE_TX`** (module sends its own agnostic data)
- `CONTROL` → **`MODULE_RX`** (DASH delivers a panel interaction) — settles the
  provisional `CONTROL` name.
- Cascade: the subscribe message `RELAY_STREAM` → **`STREAM`** (the word `RELAY`
  is retired). `SYSTEM_SIGNAL` (send-side declaration) unchanged.
- Evocative names outside the data plane keep their words (`HELLO`, `DISCOVER`,
  `INSTALL`, `ACTIVATE`/`DEACTIVATE`, `ACK`, `MANIFEST`, `BLOCK`, `TRIGGER`,
  `SYSTEM_SIGNAL`, `STREAM`).
- Nice consequence: types now map onto messages — SYSTEM does `SYSTEM_TX`, RECEIVE
  does `SYSTEM_RX`, ACCESSORY does `MODULE_TX` + `MODULE_RX`.

### Session — 2026-06-25 (sixth sitting)
Final pass on the message and type names — chasing human-readable words that also
tell the truth about each channel's shape. Old → new:
- `SYSTEM_TX` → **`BROADCAST`** (module sends a standard signal — to all)
- `SYSTEM_RX` → **`LISTEN`** (DASH delivers a standard signal you subscribed to)
- `MODULE_TX` → **`REPORT`** (module sends its own data to its panel — to one place)
- `MODULE_RX` → **`ACTION`** (user operated a panel control) — settles the
  long-provisional `CONTROL`/`MODULE_RX` name.
- Type **`RECEIVE` → `LISTENER`** (it does the `LISTEN` message).
- Reasoning: the **standard** channel is pub/sub (one-to-many) → role verbs
  `BROADCAST`/`LISTEN`; the **agnostic** channel is a private one-to-one wire →
  the directed pair `REPORT`/`ACTION`. Naming style mirrors channel topology, and
  all four are distinct words (best at-a-glance scannability in the serial monitor).
  Rejected en route: `MOD_DASH/DASH_MOD` (swapped-token blur), `COMMAND` (overloads
  "lifecycle command", a touch cold). `REPORT`/`ACTION` reads as the dashboard
  metaphor: the panel reports, the operator acts.
- Subscribe message `STREAM` → **`SUBSCRIBE`** — completes the pub/sub triad
  (`BROADCAST` publish / `SUBSCRIBE` / `LISTEN` receive), names the action not the
  byproduct, and reads cleanly for booleans too (`SUBSCRIBE|id|headlights`). The
  "everything you receive is a stream" idea stays as a concept; only the message
  word changed. `SYSTEM_SIGNAL` and the lifecycle/handshake words unchanged.
- Acknowledgement `ACK` → **`ROGER`** — it was the only abbreviation left in an
  otherwise plain-English set. "Roger" is a real radio acknowledgement (so it reads
  intuitively to any community builder) *and* a cheeky nod (Roger's name, his dad's
  suggestion). The `which` field still says what was actioned (`ROGER|id|activate`).

**For eventual promotion to transport.md / a module-rules doc (on instruction):**
the unified message structure and delimiter, `HELLO`, the three module types
(`SYSTEM`/`ACCESSORY`/`LISTENER`, one per module), the ACTIVATE/DEACTIVATE
vocabulary, the gatekeeper-vs-core layering, the subscribe/deliver model
(`SUBSCRIBE` subscribe, `LISTEN` deliver), and the agnostic panel round-trip
(`REPORT` out / `ACTION` in).

### Session — 2026-06-30
Began the first reference module — the **X-Type steering wheel** SYSTEM module —
on real hardware, and started writing the lifecycle handshake firmware. This is
the canonical SYSTEM build the plan called for: full core loop, no payload.
- **Hardware settled: Arduino Uno R4 WiFi** (not the Uno Q originally pencilled
  in). A normal single-MCU Arduino — the RA4M1 runs the sketch; the on-board
  ESP32-S3-MINI-1 is the WiFi/BT radio *and* the USB-serial bridge (USB-C →
  ESP32-S3 → RA4M1). So `Serial` enumerates straight to the host (DASH) as a CDC
  port — it "acts like an Uno R3," and none of the Uno Q's dual-brain/bridge
  complications apply. The protocol logic lives on the MCU in plain Arduino C++
  (most SDKable: it's what a community builder on a bare MCU copies), behind a
  one-line `#define DASH_SERIAL Serial` so the same code ports by changing only
  the transport object.
- **id:** the R4 WiFi has a real MAC (the ESP32-S3's, read via `WiFiS3`), but the
  first sketch uses an *assigned* id — the §3 "no-MAC" fallback — to prove the
  handshake with zero WiFi dependency. Real-MAC reading is a deliberate later
  step.
- **`INSTALL_END` now carries the id → `INSTALL_END|id`.** The §7 flow example
  had written it bare, which contradicted the §2 invariant that every message
  names its module (only `DISCOVER` is exempt). Resolved in favour of §2;
  cheat-sheet and §7 example updated to match. *(Roger's decision.)*
- **DASH-side reality noted:** the transport layer is roadmap 1.4.x and not yet
  built, so near-term the firmware is proven on the bench (serial monitor / a
  mock-DASH transcript). Real in-app integration lands with 1.4.x.
