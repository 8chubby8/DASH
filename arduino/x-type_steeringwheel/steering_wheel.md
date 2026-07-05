# X-Type Steering Wheel — DASH Module (working writeup)

This document is the session writeup for the **X-Type steering wheel** DASH
module. It captures what the module is, the decisions made, the hardware, the
firmware written so far, and what is still open — enough to pick the work back
up cold (e.g. in a fresh VS Code session) without re-deriving anything.

It sits alongside the DASH document set in the parent folder. The authoritative
protocol contract is `../arduino/arduino.md` (the Module SDK working record).
This file is module-specific working notes, not a Bible document.

---

## 1. What this module is

A **SYSTEM** module that reads the resistor-ladder buttons on a Jaguar X-Type
steering wheel and broadcasts them to DASH as standard signals (volume, track,
source, …).

Module type is **SYSTEM** because:

- It speaks DASH's *known vocabulary* — standard head-unit/vehicle signals DASH
  already understands — rather than its own private data.
- It is **send-only**: it `BROADCAST`s signals out and never receives live data.
- The three DASH module types are mutually exclusive (`arduino.md §4a`):
  `SYSTEM` (sends standard signals), `ACCESSORY` (owns an interactive panel,
  two-way, ships assets), `LISTENER` (receives subscribed standard signals).
  A steering wheel is the textbook SYSTEM module — `transport.md` even names it
  as the example.

### Why this module exists (beyond the car)

It is the **first SYSTEM reference module** the SDK plan called for
(`arduino.md` "The Plan"): the cleanest possible exercise of the full core loop
(discover → install → activate → live data) with **no heavy payload** — no
icons, no panels, no asset transfer. It does double duty:

1. **Conformance test rig** for the DASH-side transport layer (roadmap 1.4.x,
   not yet built) — it pressure-tests the protocol on real hardware.
2. **Reference example** community builders copy. So it honours the **SDKable
   principle**: nothing here uses access a third-party builder couldn't have.
   The firmware is written to be *read*, heavily commented.

---

## 2. Hardware — Arduino Uno R4 WiFi

The board lined up for this build. (Originally an Arduino Uno Q was pencilled
in; that was changed to the R4 WiFi this session — see §3.)

- **Renesas RA4M1** (48 MHz Arm Cortex-M4) — the main MCU. Our sketch runs here.
  Operating voltage **5 V** (matters for the analog ladder later, not for the
  handshake).
- **ESP32-S3-MINI-1** — does **two jobs**: the WiFi/Bluetooth radio *and* the
  USB-serial bridge. The USB-C data lines route through switches that default to
  the ESP32, so the path is **USB-C → ESP32-S3 → RA4M1**.
- Consequence that matters: the sketch's `Serial` enumerates straight to the
  host (the DASH Android device) as an ordinary **USB CDC serial port** — the
  ESP32 bridge is transparent to us. So the board genuinely "acts like a classic
  Uno R3": one normal Arduino sketch, `Serial` over USB.
- It has a real **MAC address** (the ESP32-S3's, readable via the `WiFiS3`
  library) available for the module id when we want it — see §4.

---

## 3. Decisions made this session

### Protocol logic lives on the MCU, in plain Arduino C++ ("Option B")

The reference module's job is to be the thing community builders copy, and they
are overwhelmingly on bare microcontrollers — so the protocol belongs in MCU
C++ (`Serial.println`, fixed buffers), not on a host/Linux side. On the R4 WiFi
this is trivially the case (it's a normal single-MCU Arduino).

*(Context: this decision was first weighed against the Uno Q, whose dual-brain
Linux+MCU design would have forced the protocol onto a Linux side or behind a
byte-bridge. Switching to the R4 WiFi made all of that moot — there is no Linux
side, no bridge, no USB-gadget question. The principle stands regardless: the
copyable protocol code is MCU C++.)*

### One-line transport indirection

The sketch talks over `#define DASH_SERIAL Serial`. That single line is the only
board-specific tie-in. To run the same code where the link to DASH is a UART,
change it to `Serial1` — nothing else moves. Keeps "acts like an Uno R3" honest
and the code portable.

### Module id: assigned now, real MAC later

The id is the 12-hex MAC, uppercase, no separators (`arduino.md §3`), and rides
in every message. The R4 WiFi *has* a MAC, but reading it pulls in the WiFi
library and boot-time setup. So the first sketch uses an **assigned** id (the §3
"no-MAC" fallback a bare Uno would use) to prove the handshake with zero WiFi
dependency. Current placeholder: `0000DA58EE01`. Swapping to the real MAC via
`WiFiS3` is a deliberate later step.

### `INSTALL_END` carries the id → `INSTALL_END|id`

`arduino.md §2` invariant is that **every** message names its module (only
`DISCOVER` is exempt), but the `§7` flow example had written `INSTALL_END` bare.
Resolved this session in favour of §2. `arduino.md` updated in both the cheat
sheet and the §7 example, with a dated session note. *(Roger's decision.)*

---

## 4. The protocol / handshake

Plain UTF-8 text, one message per line, pipe-separated positional fields:
`TYPE | id | field3 | field4 | …` (`arduino.md §1, §2`). The only two forbidden
characters in any field are newline and `|`, so parsing is a simple split — no
escaping ever.

The lifecycle the module implements (`arduino.md §6, §7`):

- Boot **SILENT** — answer identity only, send no data.
- The module persists **nothing** about install state; **DASH is the single
  source of truth**. This is what makes it self-healing — no "zombie" modules
  after a reboot.
- States: `SILENT` (boot) → `ACTIVE` (after `ACTIVATE`) → `SILENT` (after
  `DEACTIVATE`). Live data flows only while `ACTIVE`.
- `ACTIVATE`/`DEACTIVATE` are **acknowledged** (the module replies `ROGER`);
  live data is fire-and-forget.

### The handshake transcript (the contract between both ends)

This is exactly what the firmware produces and what the DASH side must
produce/expect:

```
DASH → DISCOVER
mod  → HELLO|0000DA58EE01|SYSTEM|Steering Wheel|X-Type wheel buttons|v0.1
DASH → INSTALL|0000DA58EE01
mod  → SYSTEM_SIGNAL|0000DA58EE01|volume
mod  → SYSTEM_SIGNAL|0000DA58EE01|track
mod  → SYSTEM_SIGNAL|0000DA58EE01|source
mod  → INSTALL_END|0000DA58EE01
DASH → ACTIVATE|0000DA58EE01
mod  → ROGER|0000DA58EE01|activate
DASH → DEACTIVATE|0000DA58EE01
mod  → ROGER|0000DA58EE01|deactivate
```

When a button is eventually pressed (active), the runtime message is:

```
mod  → BROADCAST|0000DA58EE01|volume|up
```

Note: a `SYSTEM_SIGNAL` line declares the signal **name** (`volume`); the
**value** (`up`/`down`/`next`…) is sent later in the `BROADCAST`. One signal per
line — no comma-lists (`§2`).

---

## 5. The firmware written so far

**File:** `x-type_steeringwheel/dash_steering_wheel/dash_steering_wheel.ino`
(Arduino requires the `.ino` to live in a folder of the same name.)

**Scope so far: the lifecycle handshake only.** The actual button reading (the
resistor ladder on an analog pin) is deliberately **not** written yet — the
handshake is the foundation and is proven first.

What it does:

- Boots SILENT, opens `DASH_SERIAL` at 115200 baud.
- Assembles incoming serial into complete lines (handles `\n`, tolerates and
  strips `\r` per `§1`, drops over-long lines safely). 64-byte receive buffer
  (`§10`).
- Parses each line by an **in-place split on `|`** — overwrites each pipe with a
  string terminator and bookmarks each field. Possible only because `|` is
  forbidden in content; that is what keeps the parser tiny.
- Dispatch:
  - `DISCOVER` (no id) → reply `HELLO`.
  - Otherwise, **ignore anything not addressed to our id** (on-module half of
    DASH's source-aware gatekeeper, `§5`).
  - `INSTALL` → send each `SYSTEM_SIGNAL` line, then `INSTALL_END|id`.
  - `ACTIVATE` → `active = true`, reply `ROGER|id|activate`.
  - `DEACTIVATE` → `active = false`, reply `ROGER|id|deactivate`.
  - Unknown TYPE → **ignored** (forward compatibility, `§2`).
- Optional `SEND_TEST_HEARTBEAT` (off by default): once active, sends a stand-in
  `BROADCAST` every 2 s (using `millis()`, never `delay()`, so the module stays
  responsive) to watch live data flow end-to-end before buttons exist.

Deliberate style choices (all to keep the reference small and robust):

- Plain `char[]` and `strcmp`, **not** the Arduino `String` class (avoids heap
  fragmentation on a small MCU).
- `F("…")` wrappers keep fixed strings in flash, not RAM.
- One `println` per message, always last — that newline is the protocol's whole
  framing.
- Builder-editable values (`MODULE_ID`, name/desc/version, signal names) are
  constants grouped at the top.

---

## 6. Changes made to the shared docs

- `../arduino/arduino.md`:
  - Cheat sheet: `INSTALL_END` → `INSTALL_END|id`.
  - §7 flow example: `module → INSTALL_END` → `module → INSTALL_END|id`.
  - Added a dated **Session — 2026-06-30** note recording the R4 WiFi hardware
    choice, MCU-C++/`DASH_SERIAL` approach, assigned-id-first, and the
    `INSTALL_END|id` resolution.

No other Bible documents were changed.

---

## 7. DASH-side status (important context)

DASH **cannot receive a module yet.** The whole transport layer is roadmap
**1.4.x** and DASH is currently mid-**1.3.x** (system bar / edit mode). So
`DISCOVER`/`INSTALL`/`ACTIVATE` — DASH's side of this conversation — does not
exist in the app yet.

Near-term, therefore, the firmware is proven **on the bench**: the wire is plain
text (debuggability is a first-class protocol goal), so the handshake can be
exercised in any serial monitor by typing DASH's lines at it, or against a small
mock-DASH script. Real in-app integration lands when 1.4.x is built — which is
the parallel track Roger intends to build alongside this firmware.

---

## 8. Open items / next steps

In no fixed order — pick per what's being worked on:

- **Build the DASH receiving side** against the §4 transcript (the concurrent
  track — verify both ends meet).
- **Add the real MAC** to the sketch: read the ESP32-S3 MAC via `WiFiS3`, format
  to 12-hex uppercase, replace the assigned id. (Adds a WiFi dependency + a
  little boot setup.)
- **The resistor ladder / buttons** (deferred, considered the trivial part):
  - Determine the X-Type wheel's ladder(s) — how many buttons, one ladder or
    two, resistance/voltage per button. Measure with a multimeter or read raw
    ADC values.
  - Remember the RA4M1 is **5 V** — keep the ladder within the 0–5 V analog
    range (add a divider if needed).
  - Firmware: read ADC, debounce, classify button, handle press / hold-repeat
    (volume) vs single-shot (seek/source) semantics, emit `BROADCAST`.
- **Signal vocabulary ratification.** The standard signal names are provisional
  (`volume`, `track`, `source`, possibly `phone`/`voice`). `transport.md`
  Appendix A is an explicit placeholder; defining these is a small act of
  writing DASH's standard language and wants agreeing/recording when settled.
- **Hardware unknown to confirm on the real connection:** that the R4 WiFi's CDC
  serial enumerates cleanly through DASH's `usb-serial-for-android`. High
  confidence (standard CDC-ACM), but only the real link proves it.

---

## 9. Quick reference

| Thing | Value |
|---|---|
| Module type | `SYSTEM` (send-only) |
| Board | Arduino Uno R4 WiFi (RA4M1 5 V + ESP32-S3 bridge/WiFi) |
| Transport | USB CDC `Serial` → DASH (`#define DASH_SERIAL Serial`) |
| Baud | 115200 |
| Module id | `0000DA58EE01` (assigned placeholder; real MAC later) |
| Provisional signals | `volume`, `track`, `source` |
| Sketch | `dash_steering_wheel/dash_steering_wheel.ino` |
| Protocol spec | `../arduino/arduino.md` |
| Status | Handshake firmware written; buttons + DASH side outstanding |
