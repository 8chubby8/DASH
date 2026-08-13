/* ===========================================================================
   DASH Module — Tank Gauge (USB)         |  module type: ACCESSORY
   Board: Espressif ESP32 DevKitC (WROOM-32, classic)  |  transport: USB serial
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

/* ---------------------------------------------------------------------------
   The ACCESSORY face — the 1.6.10 helper in draft.

   The same class as GaugeWifi and GaugeBt carry. Written out a third time on
   purpose: three transports, one unchanged class, which is about as clear a
   statement as there can be that it belongs in the library rather than in a
   sketch. Extracting it is 1.6.10's job.
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
  // Deliberately empty. The other two sketches log the lifecycle to a spare
  // UART; here the only stream available is the wire itself, and nothing may
  // print to it.
  void onActivated() override {}
  void onDeactivated() override {}

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

     The push-to-completion write loop matters here for a third reason again: a
     UART's buffer is small and drains at the baud rate, so this short-writes
     constantly and simply waits for the wire. A block even one byte short is a
     length mismatch discovered at the far end after the whole payload has gone.

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
        if (w == 0) { yield(); continue; }   // UART busy — let it drain
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
DashAccessoryDraft dash("0000DA58AC03", "Tank Gauge USB",
                        "Air-ride tank pressure panel over USB serial", "v1.0");

/* -------- this board's own pretend tank --------------------------------------- */
// No sensor wired up, so the value is generated. A slow sweep across the full range
// makes the whole scale visible without anyone having to turn anything.
float pressure = 0.0;
bool  rising   = true;
const float PRESSURE_MIN = 0.0, PRESSURE_MAX = 11.0, PRESSURE_STEP = 0.1;

unsigned long lastReport = 0;
const unsigned long REPORT_MS = 250;

void setup() {
  // 115200 — the project's one serial rate, matched by DASH's UsbSerialTransport and every other
  // sketch. Do not change it here alone; a mismatch is silent, and it presents as a board that
  // enumerates perfectly and then never answers DISCOVER, because both ends are talking noise at
  // each other. If USB ever goes quiet after a firmware experiment, check this line first.
  //
  // **57600 was tried and rejected** (2026-08-13, roadmap 1.6.6). An 88 KB panel payload arrives
  // corrupt over USB roughly two installs in five; halving the rate changed nothing measurable —
  // one failure in four against two in five, the same coin. So the cause is neither throughput nor
  // bit time, and the remedy is a per-block retry (1.6.10), not a slower wire. Recorded so nobody
  // spends the afternoon trying it again.
  Serial.begin(115200);
  dash.begin(Serial);            // Serial IS the wire — nothing else may write to it
}

void loop() {
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
