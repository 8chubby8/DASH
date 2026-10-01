/* ===========================================================================
   DashAccessory — an ACCESSORY module (module-sdk.md §4a, §8; module-layout.md)
   ---------------------------------------------------------------------------
   An ACCESSORY ships a panel and talks about it: REPORT out, ACTION in, TRIGGER
   for alarms. The library handles everything on the wire; the builder writes:

     1. setAssets(DASH_ASSETS, DASH_ASSET_COUNT) — the install payload, as
        generated into flash by make_assets.py. The library sends the MANIFEST
        and streams every BLOCK straight out of PROGMEM a chunk at a time, never
        holding an asset whole in RAM (§8). The layout IS the declaration
        (module-layout.md §9) — there is nothing else to declare.
     2. onReport(fn) — a function that reports the CURRENT value of every panel
        variable. The library calls it on activation (so the panel is right from
        its first frame) and on every heartbeat. DASH drops unchanged values, so
        the heartbeat is free.
     3. onAction(fn) — called with (control, value) when a control on the panel
        is operated; value is "" for a momentary control. Move your own state,
        then report() where it ended up — ALWAYS, even if nothing changed: the
        report is the acknowledgement, and DASH is holding a prediction until it
        arrives (module-layout.md §8).

   report() may also be called whenever the module's own logic changes a value.
   trigger("name") raises an agnostic alarm. Every send is gated on ACTIVE (§6).

   A control this module does not recognise should be ignored in silence: the
   layout on the tablet may be newer than the firmware on the board.

   For a module with more state than a pair of free functions suits, subclass
   DashAccessory and override handleAction() / reportAll() instead of passing
   callbacks — the Climate sketches do exactly that.

   A MODULE NEVER PARSES ITS OWN LAYOUT. It copies bytes from flash to a Stream
   and understands none of them; making sense of the payload is DASH's job.
   =========================================================================== */
#ifndef DASH_ACCESSORY_H
#define DASH_ACCESSORY_H

#include "DashModule.h"

// One install asset, as make_assets.py generates it into flash.
struct DashAsset {
  const char*    name;    // the BLOCK name — a slot name means it is a layout
  const uint8_t* bytes;   // in PROGMEM: read with pgm_read_byte
  uint32_t       length;  // exact byte count, sent in the BLOCK header
  const char*    crc;     // CRC32, lowercase hex, unpadded (§8)
};

// The one working buffer every asset is streamed through.
#ifndef DASH_BLOCK_CHUNK
#define DASH_BLOCK_CHUNK 512
#endif

class DashAccessory : public DashModule {
 public:
  DashAccessory(const char* id, const char* name, const char* description,
                const char* version)
      : DashModule(id, "ACCESSORY", name, description, version) {}

  // The install payload (§8). Total bytes for the MANIFEST are summed here.
  void setAssets(const DashAsset* assets, uint8_t count);

  void onAction(void (*cb)(const char* control, const char* value)) { _onAction = cb; }
  void onReport(void (*report)()) { _report = report; }
  // Optional: once on each SILENT -> ACTIVE, after the activation report.
  void onActivate(void (*cb)()) { _onActivate = cb; }
  // Optional: on going SILENT — put hardware in a safe state.
  void onDeactivate(void (*cb)()) { _onDeactivate = cb; }

  // Heartbeat interval (default 5000 ms, the §4b recommendation). 0 turns it off.
  void setHeartbeat(unsigned long ms) { _heartbeatMs = ms; }

  // ---- REPORT|id|variable|value — gated on ACTIVE (§6) --------------------
  void report(const char* variable, const char* value);
  void report(const char* variable, long value);
  void report(const char* variable, int value) { report(variable, (long)value); }
  void report(const char* variable, double value, int decimals = 1);

  // TRIGGER|id|name — an agnostic alarm for DASH's system bar. Gated on ACTIVE.
  void trigger(const char* name);

 protected:
  // Override these in a subclass instead of passing callbacks, if preferred.
  virtual void handleAction(const char* control, const char* value) {
    if (_onAction) _onAction(control, value);
  }
  virtual void reportAll() {
    if (_report) _report();
  }

  void onInstall() override;
  void onActivated() override;
  void onDeactivated() override;
  void onTick(unsigned long now) override;
  void onCommand(int argc, char** argv) override;

 private:
  const DashAsset* _assets = nullptr;
  uint8_t _assetCount = 0;
  uint32_t _assetBytes = 0;

  void (*_onAction)(const char*, const char*) = nullptr;
  void (*_report)() = nullptr;
  void (*_onActivate)() = nullptr;
  void (*_onDeactivate)() = nullptr;
  unsigned long _heartbeatMs = 5000;
  unsigned long _lastHeartbeat = 0;

  void sendBlock(const DashAsset& asset);
};

#endif  // DASH_ACCESSORY_H
