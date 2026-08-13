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

/* ---------------------------------------------------------------------------
   The ACCESSORY face — the 1.6.10 helper in draft.

   Character-for-character the class in GaugeWifi.ino. It is duplicated rather
   than shared on purpose: this is the draft `DashAccessory` gets extracted from
   at 1.6.10, and having written it twice against two transports is exactly the
   evidence that it belongs in the library rather than in a sketch. The base class
   already handles framing, HELLO, INSTALL_END and the SILENT -> ACTIVE -> SILENT
   lifecycle with its ROGERs.
   --------------------------------------------------------------------------- */
class DashAccessoryDraft : public DashModule {
 public:
  DashAccessoryDraft(const char* id, const char* name, const char* description,
                     const char* version)
      : DashModule(id, "ACCESSORY", name, description, version) {}

  // REPORT|id|variable|value — this panel's own data, addressed to this module.
  // Sourceful: DASH keeps the id as the store key, because `tank_pressure` from
  // this module is this panel's data and is never merged with anybody else's.
  void report(const char* variable, float value, int decimals) {
    startMsg(F("REPORT"));
    field(variable);
    fieldFloat(value, decimals);
    endMsg();
  }

 protected:
  void onActivated() override {
    DBG.println(F("[dash] ACTIVE — reporting tank_pressure"));
  }

  void onDeactivated() override {
    DBG.println(F("[dash] SILENT"));
  }

  /* The install payload. MANIFEST first as a table of contents, so DASH can show
     a real progress bar rather than an indeterminate one, then the blocks. The
     base sends INSTALL_END after this returns. */
  void onInstall() override {
    startMsg(F("MANIFEST"));
    fieldInt(DASH_ASSET_COUNT);
    fieldInt((long)DASH_ASSET_TOTAL_BYTES);
    endMsg();

    for (uint8_t i = 0; i < DASH_ASSET_COUNT; i++) sendBlock(DASH_ASSETS[i]);
  }

 private:
  /* One asset: the header line, then exactly `length` raw bytes.

     THE BYTE COUNT IS THE FRAMING. Once the header is out, the next `length`
     bytes are payload and nothing else — a 0x0A inside a PNG is data, not a line
     ending. DASH switches to a counted read on seeing the header and switches
     back when the count is met, which is what lets binary travel down a wire that
     is otherwise line-based.

     THE PUSH-TO-COMPLETION LOOP EARNS ITS KEEP HERE. A Stream may accept fewer
     bytes than offered when its buffer is full, and RFCOMM's is far smaller than
     a TCP socket's — so where the WiFi build rarely short-writes, this one will,
     constantly. A block even one byte short is a length mismatch discovered at
     the far end after the whole payload has gone, so the remainder is pushed
     rather than assumed.

     Deliberately NOT flush(). */
  void sendBlock(const DashAsset& asset) {
    startMsg(F("BLOCK"));
    fieldRaw(asset.name);        // generated constants — no stripping needed
    fieldInt((long)asset.length);
    fieldRaw(asset.crc);
    endMsg();

    uint8_t chunk[CHUNK];
    uint32_t sent = 0;
    while (sent < asset.length) {
      uint16_t n = (asset.length - sent > CHUNK) ? CHUNK : (uint16_t)(asset.length - sent);
      for (uint16_t i = 0; i < n; i++) chunk[i] = pgm_read_byte(asset.bytes + sent + i);

      uint16_t written = 0;
      while (written < n) {
        size_t w = _io->write(chunk + written, n - written);
        if (w == 0) { yield(); continue; }   // radio busy — let the stack breathe
        written += w;
      }
      sent += n;
      yield();
    }
  }

  static const uint16_t CHUNK = 512;   // one working buffer, reused for every asset
};

/* -------- the module ---------------------------------------------------------- */
// **The version is bumped whenever the layout changes, and that is not bookkeeping.** DASH
// captured this string at install and compares it against every HELLO (roadmap 1.4.13), so a
// module whose artwork or bindings have moved on while its version stands still would keep
// being drawn from the layout already on the tablet's disk. Bumping it is what makes DASH
// quarantine the stale record and offer the update that re-runs the handshake.
DashAccessoryDraft dash("0000DA58AC02", "Tank Gauge BT",
                        "Air-ride tank pressure panel over Bluetooth", "v1.0");

/* -------- this board's own pretend tank --------------------------------------- */
// No sensor wired up, so the value is generated. A slow sweep across the full range
// makes the whole scale visible without anyone having to turn anything.
float pressure = 0.0;
bool  rising   = true;
const float PRESSURE_MIN = 0.0, PRESSURE_MAX = 11.0, PRESSURE_STEP = 0.1;

unsigned long lastReport = 0;
const unsigned long REPORT_MS = 250;

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
  if (!dash.isActive()) return;  // SILENT until DASH says otherwise (§6)

  unsigned long now = millis();
  if (now - lastReport >= REPORT_MS) {
    lastReport = now;
    pressure += rising ? PRESSURE_STEP : -PRESSURE_STEP;
    if (pressure >= PRESSURE_MAX) { pressure = PRESSURE_MAX; rising = false; }
    if (pressure <= PRESSURE_MIN) { pressure = PRESSURE_MIN; rising = true;  }
    dash.report("tank_pressure", pressure, 1);
  }
}
