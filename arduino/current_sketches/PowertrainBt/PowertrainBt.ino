/* ===========================================================================
   DASH Module — Powertrain (Bluetooth)   |  module type: SYSTEM
   Board: Espressif ESP32 DevKitC (WROOM-32, classic)  |  transport: BT Classic SPP
   Built on the DashModule library.
   ---------------------------------------------------------------------------
   The Bluetooth twin of PowertrainUsb: SAME signals — only the pipe differs. It
   reaches DASH over Bluetooth Classic (SPP) instead of a cable. BluetoothSerial
   is an Arduino Stream, so the library drives it exactly like Serial; the only
   extra job is the link handling.

   HOW DASH FINDS THIS MODULE — THE NAME MARKER
     Classic BT has no BLE-style service advertisement, so DASH identifies its
     modules by their Bluetooth NAME containing the token `D.A.S.H`. We name the
     adapter `D.A.S.H-Powertrain` in SerialBT.begin() below. (This is separate
     from the module's HELLO name.) Append anything after the token you like.

   PAIRING
     SPP needs the device BONDED first. Pair this board once in Android's own
     Bluetooth settings — DASH never pairs programmatically. After that DASH
     connects out to it on every sweep.

   LINK LOSS
     A dropped RFCOMM client sends no DEACTIVATE, so on the client-gone transition
     we call dash.linkLost() — the module forgets it was active and goes SILENT
     until DASH reconnects and re-ACTIVATEs it (§6).

   BOARD NOTE — CLASSIC ESP32 ONLY
     BluetoothSerial (Classic/SPP) exists only on the original ESP32 (WROOM-32).
     The S3/C3/C6 are BLE-only and cannot run this. Use a classic ESP32 DevKitC.
   =========================================================================== */
#include <Dash.h>
#include "BluetoothSerial.h"   // Bluetooth Classic SPP — the only change vs PowertrainUsb
#include "esp_random.h"        // ESP32 hardware RNG — this board's seed source

BluetoothSerial SerialBT;
#define DASH_BT_NAME "D.A.S.H-Powertrain"   // MUST contain the token `D.A.S.H`

// A DISTINCT id from the serial Powertrain (...EE06) so both can coexist.
DashSystem dash("0000DA58EE08", "Powertrain BT", "ESP32 powertrain over BT: speed, rpm, gear", "v1.0");

/* -------- this board's own pretend drivetrain --------------------------------- */
const char* GEARS[] = { "park", "reverse", "neutral", "drive" };
int   gearIndex   = 0;
int   speedKmh    = 0;
int   rpm         = 750;
float ambientTemp = 22.0;    // SHARED with the Body board
float speedPhase = 0.0, tempPhase = 0.0;

/* -------- the builder's own schedulers ---------------------------------------- */
unsigned long lastStream = 0;
unsigned long lastTemp   = 0;
unsigned long lastGear   = 0;
unsigned long nextMedia  = 0;
const unsigned long STREAM_MS = 500, TEMP_MS = 1000, GEAR_MS = 12000;

bool linkUp = false;         // RFCOMM client currently connected?

/* -------- state report (§4b): dump + heartbeat, driven by the library --------- */
void report() {
  dash.broadcast("gear_position", GEARS[gearIndex]);
  dash.broadcast("vehicle_speed", (long)speedKmh);
  dash.broadcast("engine_rpm",    (long)rpm);
  dash.broadcast("ambient_temp",  ambientTemp, 1);
}

/* -------- runs once on the SILENT->ACTIVE transition -------------------------- */
void onActivate() {
  unsigned long now = millis();
  lastStream = lastTemp = lastGear = now;
  nextMedia = now + random(10000, 25000);
}

void setup() {
  // The name MUST contain `D.A.S.H` — that is how DASH recognises this module.
  SerialBT.begin(DASH_BT_NAME);
  dash.begin(SerialBT);        // the library reads/writes the RFCOMM stream

  dash.addSignal("gear_position");
  dash.addSignal("vehicle_speed");
  dash.addSignal("engine_rpm");
  dash.addSignal("ambient_temp");
  dash.addSignal("media_next");

  dash.onReport(report);
  dash.onActivate(onActivate);

  randomSeed(esp_random());
}

void loop() {
  // Watch the RFCOMM client: on the up->down transition, go SILENT (§6).
  bool clientNow = SerialBT.hasClient();
  if (linkUp && !clientNow) dash.linkLost();
  linkUp = clientNow;

  dash.loop();                 // pump the wire + heartbeat/dump
  if (!dash.isActive()) return;

  unsigned long now = millis();

  if (now - lastStream >= STREAM_MS) {
    lastStream = now;
    speedPhase += 0.05;
    speedKmh = (int)(((sin(speedPhase) + 1.0) / 2.0) * 90.0 + 0.5);
    rpm      = 750 + speedKmh * 47;
    dash.broadcast("vehicle_speed", (long)speedKmh);
    dash.broadcast("engine_rpm", (long)rpm);
  }

  if (now - lastTemp >= TEMP_MS) {
    lastTemp = now;
    tempPhase += 0.02;
    ambientTemp = 22.0 + 2.0 * sin(tempPhase);
    dash.broadcast("ambient_temp", ambientTemp, 1);
  }

  if (now - lastGear >= GEAR_MS) {
    lastGear = now;
    gearIndex = (gearIndex + 1) % 4;
    dash.broadcast("gear_position", GEARS[gearIndex]);
  }

  if (now >= nextMedia) {
    nextMedia = now + random(10000, 25000);
    dash.event("media_next");
  }
}
