/* ===========================================================================
   DASH Module — Climate (WiFi)           |  module type: ACCESSORY
   Board: Espressif ESP32 DevKitC (WROOM-32, classic)  |  transport: WiFi TCP
   Built on the DashModule library.       |  roadmap 1.6.7
   ---------------------------------------------------------------------------
   THE SECOND ACCESSORY, AND THE FIRST THAT IS ALL CONTROLS. The Tank Gauge
   was one variable and three buttons — this is nine variables and thirteen
   controls, which is what a real panel looks like.

   WHAT IT SENDS, AND WHEN
     On INSTALL it streams two blocks out of flash:

       h_large_day   the panel description for the large horizontal slot
       climate.svg   the artwork — one vector, no raster

     Then SILENT until ACTIVATE, then a full state dump, then a report
     whenever anything changes.

   IT HOLDS STATE AND NOTHING ELSE
     There is no climate hardware behind this. Every variable is a number in
     RAM that moves when DASH tells it to — which is the whole module as far
     as DASH is concerned, because DASH never learns what a module does with
     an ACTION. Wiring this to real hardware changes apply() and nothing
     else: not the layout, not the wire, not a line of DASH.

   THE BOARD IS DECLARED IN THE LAYOUT (module-layout.md §8a)
     The layout lists every value each variable takes, in order. That is what
     lets DASH work out where a press lands *before* it sends, so the panel
     draws under the finger instead of a round trip later. This firmware must
     therefore report values that are ON those lists, spelled identically —
     `lvl1` not `level1`, `off` not `0`. The strings below are the contract.

   NO up/down MESSAGES. DASH sends the destination, never a direction:

       ACTION|id|fan|4          not  ACTION|id|fan_up
       ACTION|id|seat_left|lvl2 not  ACTION|id|seat_left_cycle

     So onAction() below is one setter. There is no stepping logic in this
     firmware at all, because the stepping happened on the tablet.

   FIRST BUILD: copy arduino_secrets.h.example to arduino_secrets.h in this
   folder and fill it in — SECRET_SSID, SECRET_PASS, DASH_HOST (the DASH
   device's IP), DASH_PORT (3274). Your copy is gitignored.
   =========================================================================== */
#include <Dash.h>
#include <WiFi.h>
#include "arduino_secrets.h"
#include "climate_assets.h"    // GENERATED — run make_assets.py after changing assets/

WiFiClient client;
#define DBG Serial

#include "ClimateModule.h"

/* -------- the module ----------------------------------------------------------
   A distinct id from every other module on the bench so they can coexist.
   **Bump the version whenever the layout or the artwork changes.** DASH captured
   this string at install and compares it against every HELLO (roadmap 1.4.13), so
   a module whose panel has moved on while its version stands still keeps being
   drawn from the copy already on the tablet's disk. */
ClimateModule dash("0000DA58AC04", "Climate",
                   "Single-zone cabin climate", "v1.6");

unsigned long lastLinkTry = 0;
const unsigned long LINK_RETRY_MS = 3000;
bool linkUp = false;

// Association is a separate, slower business from opening the socket: the radio
// needs to be left alone to finish rather than prodded every retry. See below.
bool associating = false;
unsigned long associatingSince = 0;
const unsigned long ASSOCIATE_TIMEOUT_MS = 20000;

/* -------- WiFi: associate, connect, and go SILENT on any drop ------------------ */
// On USB, DASH opens the device; on WiFi, DASH runs the server and this module is
// the client that dials in. So the firmware's extra job is keeping that socket up
// — and calling linkLost() the moment it is not, because a dead socket sends no
// DEACTIVATE and the module would otherwise believe it was still active.
void maintainLink() {
  if (client.connected()) { linkUp = true; return; }

  if (linkUp) {
    DBG.println(F("[wifi] link lost — going SILENT"));
    dash.linkLost();
    linkUp = false;
  }

  unsigned long now = millis();
  if (now - lastLinkTry < LINK_RETRY_MS) return;
  lastLinkTry = now;

  /* ASSOCIATE ONCE, THEN LET IT FINISH.

     This retry loop used to call WiFi.begin() every pass while the radio was
     still associating, which the ESP32 core refuses outright:

         E (39194) wifi:sta is connecting, cannot set config

     Worse than refusing, it restarts the attempt — so on any network that
     takes longer than the retry interval to associate, the module never
     connects at all and simply prints that line for ever. It is invisible on
     a fast AP and total on a slow one, which is why it survived in GaugeWifi
     (roadmap 1.6.5) until a slower association exposed it here.

     So: begin once, wait, and only start over if it has clearly given up. */
  if (WiFi.status() != WL_CONNECTED) {
    if (!associating) {
      DBG.print(F("[wifi] associating to ")); DBG.println(SECRET_SSID);
      WiFi.begin(SECRET_SSID, SECRET_PASS);
      associating = true;
      associatingSince = now;
    } else if (now - associatingSince > ASSOCIATE_TIMEOUT_MS) {
      DBG.println(F("[wifi] association timed out — starting over"));
      WiFi.disconnect();   // radio stays on; true would power it down
      associating = false;
    }
    return;
  }
  associating = false;

  DBG.print(F("[wifi] connecting to DASH at "));
  DBG.print(DASH_HOST); DBG.print(':'); DBG.println(DASH_PORT);
  if (client.connect(DASH_HOST, DASH_PORT)) {
    DBG.println(F("[wifi] connected"));
    linkUp = true;
  }
}

void setup() {
  DBG.begin(115200);
  delay(200);
  DBG.println();
  DBG.println(F("DASH Climate (ACCESSORY over WiFi)"));
  DBG.print(F("payload: ")); DBG.print(DASH_ASSET_COUNT);
  DBG.print(F(" blocks, ")); DBG.print(DASH_ASSET_TOTAL_BYTES); DBG.println(F(" bytes"));

  WiFi.mode(WIFI_STA);
  dash.begin(client);
}

void loop() {
  maintainLink();
  dash.loop();
  dash.service();
}
