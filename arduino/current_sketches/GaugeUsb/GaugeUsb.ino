/* ===========================================================================
   DASH Module — Tank Gauge (USB)         |  module type: ACCESSORY
   Board: Espressif ESP32 DevKitC (WROOM-32, classic)  |  transport: USB serial
          or Arduino Uno R4 WiFi / Minima — builds unchanged (2026-10-01)
   PREFER A NATIVE-USB BOARD (Uno R4, ESP32-S2/S3/C3/C6 native port) for a USB
   module — a classic ESP32's converter chip damages large installs; DASH repairs
   them, but slower. See hardware.md, "Module Boards — USB Compatibility".
   Built on the DashModule library.       |  roadmap 1.6.6
   ---------------------------------------------------------------------------
   THE THIRD PIPE. Same panel as GaugeWifi and GaugeBt, same artwork, same
   variable — down a cable this time. Between the three of them the question
   "does a panel care which transport carried it?" is answered on every transport
   DASH has.

   AND THIS ONE IS THE SIMPLEST SKETCH OF THE THREE, WHICH IS THE POINT.
     No radio to associate, no socket to dial, no RFCOMM client to watch, no
     link-lost bookkeeping — because on USB the cable *is* the link, and DASH is
     the host that opens it. When the cable goes, the port goes with it and there
     is nothing left running to notice. Everything the other two sketches spend
     their extra lines on is link management, not panel work.

   THE ARTWORK IS SHARED, NOT COPIED
     make_assets.py here reads ../GaugeWifi/assets, exactly as GaugeBt does. One
     source folder, three generated headers, identical CRCs — so a difference on
     screen can only be the pipe.

   NOTHING MAY PRINT TO Serial
     Serial *is* the wire. A stray debug line would be parsed as a DASH message
     and, worse, could land in the middle of a length-prefixed BLOCK and shred an
     asset. So there is no DBG here — unlike the WiFi and Bluetooth sketches,
     which have a spare UART to talk on. If you need to watch this one, watch it
     from DASH's Serial Monitor, which is showing you the same bytes anyway.

   OPENING THE PORT RESETS THE BOARD
     DTR pulses when DASH opens the device, so the ESP32 reboots at the moment it
     is connected to. That is normal and harmless — the module comes up, answers
     DISCOVER with HELLO, and the handshake proceeds. It is worth knowing before
     diagnosing a "reboot loop" that is really just a port being opened.

   A DISTINCT ID FROM THE OTHER TWO
     ...AC03, after ...AC01 (WiFi) and ...AC02 (Bluetooth), so all three can be
     installed at once and DASH treats them as three separate modules.
   =========================================================================== */
#include <Dash.h>
#include "gauge_assets.h"      // GENERATED — run make_assets.py after changing the artwork

/* The ACCESSORY face is the library's DashAccessory since 1.6.10 — extracted
   from the draft class this sketch, and its two twins, carried until then. */

/* -------- the module ---------------------------------------------------------- */
// **The version is bumped whenever the layout changes, and that is not bookkeeping.** DASH
// captured this string at install and compares it against every HELLO (roadmap 1.4.13), so a
// module whose artwork or bindings have moved on while its version stands still would keep
// being drawn from the layout already on the tablet's disk. Bumping it is what makes DASH
// quarantine the stale record and offer the update that re-runs the handshake.
DashAccessory dash("0000DA58AC03", "Tank Gauge USB",
                        "Air-ride tank pressure panel over USB serial", "v1.3");

/* -------- this board's own pretend tank --------------------------------------- */
// No sensor wired up, so the value is held rather than measured. It moves only when
// the panel asks it to — nothing here runs on a timer, so every movement on screen is
// attributable to the press that caused it.
float pressure = 0.0;
const float PRESSURE_MIN = 0.0, PRESSURE_MAX = 11.0, PRESSURE_STEP = 1.0;


// Everything this module knows, said out loud. One variable today; a real one
// would say all of them here, because the panel is drawn from what DASH has been
// told and nothing else.
void dumpState() {
  dash.report("tank_pressure", pressure, 1);
}

/* A press arrived. Move the tank, clamp it, and say where it ended up.

   THE CLAMP IS NOT A REFUSAL. Pressing PLUS at 11 bar reports 11 again — the
   module heard, and could not comply, and says so by stating the truth. There is
   no error message for it and there should not be: DASH draws facts (§8), and
   the fact is that the tank did not move.

   The report goes out unconditionally, including when nothing changed, because
   this is the acknowledgement as well as the value — §8 has no separate ROGER
   for actions, and a press that produced no change still needs answering or
   DASH is left waiting on a prediction nobody will ever confirm. */
void onPanelAction(const char* control, const char* value) {
  if      (strcmp(control, "pressure_up")   == 0) pressure += PRESSURE_STEP;
  else if (strcmp(control, "pressure_down") == 0) pressure -= PRESSURE_STEP;
  else if (strcmp(control, "tank_pressure") == 0) pressure = atof(value);
  else return;                       // not ours — stay quiet rather than answer

  if (pressure > PRESSURE_MAX) pressure = PRESSURE_MAX;
  if (pressure < PRESSURE_MIN) pressure = PRESSURE_MIN;

  // No logging: on this board the only stream available is the wire itself.
  dash.report("tank_pressure", pressure, 1);
}

void setup() {
  // 115200 — the project's one serial rate, matched by DASH's UsbSerialTransport and every other
  // sketch. Do not change it here alone; a mismatch is silent, and it presents as a board that
  // enumerates perfectly and then never answers DISCOVER, because both ends are talking noise at
  // each other. If USB ever goes quiet after a firmware experiment, check this line first.
  //
  // **57600 was tried and rejected** (2026-08-13, roadmap 1.6.6). An 88 KB panel payload arrives
  // corrupt over USB roughly two installs in five; halving the rate changed nothing measurable —
  // one failure in four against two in five, the same coin. So the cause is neither throughput nor
  // bit time, and the remedy is a per-block retry (RESEND, built at 1.6.12), not a slower wire. Recorded so nobody
  // spends the afternoon trying it again.
  Serial.begin(115200);
  dash.onAction(onPanelAction);  // a button on the panel was pressed
  dash.setAssets(DASH_ASSETS, DASH_ASSET_COUNT);  // the install payload, from flash
  dash.onReport(dumpState);      // §8: on activation, and every heartbeat after
  dash.setHeartbeat(2000);
  dash.begin(Serial);            // Serial IS the wire — nothing else may write to it
}

void loop() {
  dash.loop();
}
