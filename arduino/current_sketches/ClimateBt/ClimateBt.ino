/* ===========================================================================
   DASH Module — Climate (Bluetooth)      |  module type: ACCESSORY
   Board: Espressif ESP32 DevKitC (WROOM-32, classic)  |  transport: BT SPP
   Built on the DashModule library.       |  roadmap 1.6.8
   ---------------------------------------------------------------------------
   THE BLUETOOTH TWIN OF ClimateWifi. Same panel, same firmware, same nine
   variables — only the pipe differs. It exists to answer one question: does
   a panel care which transport carried it?

   IT MUST NOT, AND THAT IS THE POINT.
     A layout is installed once and read from disk from then on. Nothing in a
     layout names a transport, nothing in the render path knows one exists,
     and the module is never consulted again after the handshake. So this
     board's panel should be indistinguishable from ClimateWifi's — and if it
     is not, something is wrong in the transport layer, not in the panel.

   ARTWORK AND LOGIC ARE SHARED, NOT COPIED
     make_assets.py here reads ../ClimateWifi/assets and copies its
     ClimateModule.h. One source, three sketches. If each owned its own copy
     they would drift, and then a difference on screen could be the pipe or
     could be the firmware and nobody could say which.

   HOW DASH FINDS THIS MODULE — THE NAME MARKER
     Classic BT has no BLE-style service advertisement, so DASH identifies
     its modules by their Bluetooth NAME containing the token `D.A.S.H`. The
     adapter is named `D.A.S.H-Climate` below. That is separate from the
     module's HELLO name, and anything may follow the token.
   =========================================================================== */
#include <Dash.h>
#include <BluetoothSerial.h>
#include "climate_assets.h"    // GENERATED — run make_assets.py after changing assets/

#define DASH_BT_NAME "D.A.S.H-Climate"

BluetoothSerial SerialBT;
#define DBG Serial

#include "ClimateModule.h"

ClimateModule dash("0000DA58AC05", "Climate BT",
                   "Single-zone cabin climate over Bluetooth", "v1.6");

bool linkUp = false;           // RFCOMM client currently connected?

void setup() {
  DBG.begin(115200);
  delay(200);
  DBG.println();
  DBG.println(F("DASH Climate (ACCESSORY over Bluetooth SPP)"));
  DBG.print(F("payload: ")); DBG.print(DASH_ASSET_COUNT);
  DBG.print(F(" blocks, ")); DBG.print(DASH_ASSET_TOTAL_BYTES); DBG.println(F(" bytes"));
  DBG.print(F("pair with: ")); DBG.println(F(DASH_BT_NAME));

  // The name MUST contain `D.A.S.H` — that is how DASH recognises this module.
  SerialBT.begin(DASH_BT_NAME);
  dash.begin(SerialBT);
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
  dash.service();
}
