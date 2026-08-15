/* ===========================================================================
   DASH Module — Climate (USB serial)     |  module type: ACCESSORY
   Board: Espressif ESP32 DevKitC (WROOM-32, classic)  |  transport: USB CDC
   Built on the DashModule library.       |  roadmap 1.6.7
   ---------------------------------------------------------------------------
   THE USB TWIN OF ClimateWifi. Same panel, same firmware, different pipe —
   see ClimateBt for why the three exist and what they prove.

   NOTHING MAY PRINT TO Serial. On this board Serial IS the wire. The other
   two sketches log their lifecycle to a spare UART; here there isn't one, so
   ClimateModule's Serial.print calls are compiled out by DASH_SILENT below.

   USB DELIVERS LARGE PAYLOADS UNRELIABLY — see roadmap 1.6.10. An 88 KB Tank
   Gauge payload arrived corrupt roughly two installs in five; this panel is a
   fraction of that size, being one SVG and no PNG, so it should fare better.
   The per-block CRC catches it either way and nothing corrupt ever reaches
   the renderer.
   =========================================================================== */
#include <Dash.h>
#include "climate_assets.h"    // GENERATED — run make_assets.py after changing assets/

// Silences ClimateModule's lifecycle logging: on this board the only stream
// available is the wire itself, and a stray print would corrupt the protocol.
#define DASH_SILENT 1

#include "ClimateModule.h"

ClimateModule dash("0000DA58AC06", "Climate USB",
                   "Single-zone cabin climate over USB serial", "v1.6");

void setup() {
  // 115200 — the project's one serial rate, matched by DASH's UsbSerialTransport and every
  // other sketch. Do not change it here alone; a mismatch is silent, and it presents as a
  // board that enumerates perfectly and then never answers DISCOVER.
  //
  // **57600 was tried and rejected** (2026-08-13, roadmap 1.6.6). Halving the rate changed
  // nothing measurable — one failure in four against two in five, the same coin. The cause is
  // neither throughput nor bit time, and the remedy is a per-block retry (1.6.10).
  Serial.begin(115200);
  dash.begin(Serial);            // Serial IS the wire — nothing else may write to it
}

void loop() {
  dash.loop();
  dash.service();
}
