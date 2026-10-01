#include "DashAccessory.h"
#include <string.h>

void DashAccessory::setAssets(const DashAsset* assets, uint8_t count) {
  _assets = assets;
  _assetCount = count;
  _assetBytes = 0;
  for (uint8_t i = 0; i < count; i++) _assetBytes += assets[i].length;
}

// The install payload: MANIFEST as a table of contents so DASH can draw a real
// progress bar, then the blocks. The base sends INSTALL_END afterwards (§7, §8).
void DashAccessory::onInstall() {
  startMsg(F("MANIFEST"));
  fieldInt(_assetCount);
  fieldInt((long)_assetBytes);
  endMsg();
  for (uint8_t i = 0; i < _assetCount; i++) sendBlock(_assets[i]);
}

/* One asset: the header line, then exactly `length` raw bytes.

   THE BYTE COUNT IS THE FRAMING. Once the header is out, the next `length`
   bytes are payload and nothing else — a 0x0A inside a PNG is data, not a line
   ending.

   Deliberately NOT flush(): on the ESP32 core that drains the *receive* buffer
   and would quietly eat inbound DASH messages mid-install. */
void DashAccessory::sendBlock(const DashAsset& asset) {
  startMsg(F("BLOCK"));
  fieldRaw(asset.name);          // generated constants — no stripping needed
  fieldInt((long)asset.length);
  fieldRaw(asset.crc);
  endMsg();

  uint8_t chunk[DASH_BLOCK_CHUNK];
  uint32_t sent = 0;
  while (sent < asset.length) {
    uint16_t n = (asset.length - sent > DASH_BLOCK_CHUNK)
                     ? DASH_BLOCK_CHUNK : (uint16_t)(asset.length - sent);
    for (uint16_t i = 0; i < n; i++) chunk[i] = pgm_read_byte(asset.bytes + sent + i);

    // A Stream may accept fewer bytes than offered when its buffer is full, and
    // a block one byte short is a length mismatch at the far end after the whole
    // payload has gone. Push the remainder rather than assume it went.
    uint16_t written = 0;
    while (written < n) {
      size_t w = _io->write(chunk + written, n - written);
      if (w == 0) { yield(); continue; }
      written += w;
    }
    sent += n;
    yield();
  }
}

// Just went ACTIVE: report everything (§8) — the panel is drawn from DASH's
// store, so a module that waits for its next change leaves it blank until then.
void DashAccessory::onActivated() {
  reportAll();
  _lastHeartbeat = millis();
  if (_onActivate) _onActivate();
}

void DashAccessory::onDeactivated() {
  if (_onDeactivate) _onDeactivate();
}

void DashAccessory::onTick(unsigned long now) {
  if (_heartbeatMs == 0) return;
  if (now - _lastHeartbeat >= _heartbeatMs) {
    _lastHeartbeat = now;
    reportAll();
  }
}

// ACTION|id|control[|value] — the base has already checked the id.
void DashAccessory::onCommand(int argc, char** argv) {
  if (strcmp(argv[0], "ACTION") != 0) return;
  if (argc < 3) return;
  handleAction(argv[2], argc > 3 ? argv[3] : "");
}

void DashAccessory::report(const char* variable, const char* value) {
  if (!isActive()) return;
  startMsg(F("REPORT"));
  field(variable);
  field(value);
  endMsg();
}

void DashAccessory::report(const char* variable, long value) {
  if (!isActive()) return;
  startMsg(F("REPORT"));
  field(variable);
  fieldInt(value);
  endMsg();
}

void DashAccessory::report(const char* variable, double value, int decimals) {
  if (!isActive()) return;
  startMsg(F("REPORT"));
  field(variable);
  fieldFloat(value, decimals);
  endMsg();
}

void DashAccessory::trigger(const char* name) {
  if (!isActive()) return;
  startMsg(F("TRIGGER"));
  field(name);
  endMsg();
}
