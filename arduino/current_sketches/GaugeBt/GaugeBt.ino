/* ===========================================================================
   DASH Module — Tank Gauge (Bluetooth)   |  module type: ACCESSORY
   Board: Espressif ESP32 DevKitC (WROOM-32, classic)  |  transport: BT Classic SPP
   Built on the DashModule library.       |  roadmap 1.6.6
   ---------------------------------------------------------------------------
   THE BLUETOOTH TWIN OF GaugeWifi. Same panel, same artwork, same variable —
   only the pipe differs. It exists to answer one question: does a panel care
   which transport carried it?

   IT MUST NOT, AND THAT IS THE POINT.
     A layout is installed once and read from disk from then on (module-layout.md
     §1). Nothing in a layout document names a transport, nothing in the render
     path knows one exists, and the module is never consulted again after the
     handshake. So the panel this board draws should be indistinguishable from the
     one GaugeWifi draws — and if it is not, something is wrong in the transport
     layer rather than in the panel.

   THE ARTWORK IS SHARED, NOT COPIED
     make_assets.py here reads ../GaugeWifi/assets. One source folder, two
     generated headers, so the bytes that go down Bluetooth are byte-for-byte the
     bytes that go down WiFi. If each sketch owned its own copy they would drift,
     and then a difference on screen could be the pipe or could be the artwork
     and nobody could say which.

   HOW DASH FINDS THIS MODULE — THE NAME MARKER
     Classic BT has no BLE-style service advertisement, so DASH identifies its
     modules by their Bluetooth NAME containing the token `D.A.S.H`. The adapter
     is named `D.A.S.H-TankGauge` below. That is separate from the module's HELLO
     name, and anything may follow the token.

   PAIRING
     SPP needs the device BONDED first. Pair this board once in Android's own
     Bluetooth settings — DASH never pairs programmatically. After that DASH
     connects out to it on every sweep.

   A DISTINCT ID FROM THE WiFi GAUGE
     ...AC02 rather than ...AC01, following PowertrainUsb/PowertrainBt. The two
     are different physical modules as far as DASH is concerned, so both can be
     installed at once — which is what 1.6.8 will want when it swipes between
     panels, and it costs nothing to be ready for.

   LINK LOSS
     A dropped RFCOMM client sends no DEACTIVATE, so on the client-gone transition
     we call dash.linkLost() — the module forgets it was active and goes SILENT
     until DASH reconnects and re-ACTIVATEs it (§6).

   BOARD NOTE — CLASSIC ESP32 ONLY
     BluetoothSerial (Classic/SPP) exists only on the original ESP32 (WROOM-32).
     The S3/C3/C6 are BLE-only and cannot run this. Use a classic ESP32 DevKitC.

   FLASH NOTE — THIS NEEDS A BIGGER APP PARTITION
     The Bluetooth Classic stack plus 88 KB of baked artwork does not fit the
     default 1.3 MB app partition. Build with the `huge_app` partition scheme:
       arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app .
     There is nothing to change in the code — the payload is streamed from flash
     either way; it simply has to fit in flash first.
   =========================================================================== */
#include <Dash.h>
#include "BluetoothSerial.h"   // Bluetooth Classic SPP — the only change vs GaugeWifi
#include "gauge_assets.h"      // GENERATED — run make_assets.py after changing the artwork

BluetoothSerial SerialBT;
#define DASH_BT_NAME "D.A.S.H-TankGauge"   // MUST contain the token `D.A.S.H`
#define DBG Serial

/* The ACCESSORY face is the library's DashAccessory since 1.6.10 — extracted
   from the draft class this sketch, and its two twins, carried until then. */

/* -------- the module ---------------------------------------------------------- */
// **The version is bumped whenever the layout changes, and that is not bookkeeping.** DASH
// captured this string at install and compares it against every HELLO (roadmap 1.4.13), so a
// module whose artwork or bindings have moved on while its version stands still would keep
// being drawn from the layout already on the tablet's disk. Bumping it is what makes DASH
// quarantine the stale record and offer the update that re-runs the handshake.
DashAccessory dash("0000DA58AC02", "Tank Gauge BT",
                        "Air-ride tank pressure panel over Bluetooth", "v1.2");

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

  DBG.print(F("[dash] ")); DBG.print(control);
  DBG.print(F(" -> ")); DBG.println(pressure);
  dash.report("tank_pressure", pressure, 1);
}

bool linkUp = false;         // RFCOMM client currently connected?

void setup() {
  DBG.begin(115200);
  delay(200);
  DBG.println();
  DBG.println(F("DASH Tank Gauge (ACCESSORY over Bluetooth SPP)"));
  DBG.print(F("payload: ")); DBG.print(DASH_ASSET_COUNT);
  DBG.print(F(" blocks, ")); DBG.print(DASH_ASSET_TOTAL_BYTES); DBG.println(F(" bytes"));
  DBG.print(F("pair with: ")); DBG.println(F(DASH_BT_NAME));

  // The name MUST contain `D.A.S.H` — that is how DASH recognises this module.
  SerialBT.begin(DASH_BT_NAME);
  dash.onAction(onPanelAction);  // a button on the panel was pressed
  dash.setAssets(DASH_ASSETS, DASH_ASSET_COUNT);  // the install payload, from flash
  dash.onReport(dumpState);      // §8: on activation, and every heartbeat after
  dash.setHeartbeat(2000);
  dash.begin(SerialBT);        // BluetoothSerial is a Stream; the library drives it
}

void loop() {
  // Watch the RFCOMM client: on the up->down transition, go SILENT (§6).
  bool clientNow = SerialBT.hasClient();
  if (linkUp && !clientNow) {
    DBG.println(F("[bt] client gone — going SILENT"));
    dash.linkLost();
  }
  if (!linkUp && clientNow) DBG.println(F("[bt] client connected"));
  linkUp = clientNow;

  dash.loop();
}
