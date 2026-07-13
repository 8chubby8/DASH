/* ===========================================================================
   DashModule — the DASH module SDK core (roadmap 1.4.15)
   ---------------------------------------------------------------------------
   The transport-agnostic base every DASH module is built on. It implements the
   parts of the locked SDK (module-sdk.md) that are identical for every module,
   so a builder never rewrites them:

     - line framing over any Arduino Stream (Serial, Serial1, WiFiClient,
       BluetoothSerial — all are Streams), tolerating CRLF, dropping over-long
       lines (§1);
     - the plain pipe split (§2);
     - HELLO on DISCOVER (§4, §7);
     - the install handshake wrapper — the subclass emits its own declarations,
       the base always closes with INSTALL_END (§7);
     - the SILENT -> ACTIVE -> SILENT lifecycle with ROGER acknowledgement (§6);
     - forbidden-character stripping on every sent field (§2).

   A module type (SYSTEM / LISTENER / ACCESSORY) subclasses this and fills in the
   small type-specific hooks. The builder then writes only their own hardware.

   Transport is the builder's job (module-sdk.md §12): USB is nothing but
   Serial.begin(); WiFi/BT connect-and-reconnect stay the builder's maintainLink
   before begin(). The library talks the protocol over whatever Stream it is given.
   =========================================================================== */
#ifndef DASH_MODULE_H
#define DASH_MODULE_H

#include <Arduino.h>

// Inbound receive buffer. 64 bytes covers the longest inbound line, a LISTEN
// (module-sdk.md §10). Override before including if a module ever needs more.
#ifndef DASH_RX_BUFFER
#define DASH_RX_BUFFER 64
#endif

// Max fields parsed from one inbound line. TYPE|id|f|v plus headroom.
#ifndef DASH_MAX_FIELDS
#define DASH_MAX_FIELDS 8
#endif

class DashModule {
 public:
  DashModule(const char* id, const char* type, const char* name,
             const char* description, const char* version);

  // Give the module its wire. The builder calls Serial.begin()/connects the
  // socket first; the library only reads and writes the Stream.
  void begin(Stream& io);

  // Pump the wire: read inbound lines and dispatch them, then service periodic
  // work (e.g. the SYSTEM heartbeat) while active. Call every loop().
  void loop();

  bool isActive() const { return _active; }
  const char* id() const { return _id; }

 protected:
  // ---- hooks a module type fills in --------------------------------------
  // Emit this module's install declarations (SYSTEM_SIGNAL / SUBSCRIBE / asset
  // BLOCKs). The base sends INSTALL_END afterwards, always.
  virtual void onInstall() {}
  // Just went ACTIVE (base has already sent ROGER). SYSTEM does its state dump here.
  virtual void onActivated() {}
  // Just went SILENT (base has already sent ROGER).
  virtual void onDeactivated() {}
  // Periodic service while active — called every loop with the current millis().
  virtual void onTick(unsigned long now) { (void)now; }
  // An inbound command the base didn't handle (LISTEN, ACTION…) — for subclasses.
  virtual void onCommand(int argc, char** argv) { (void)argc; (void)argv; }

  // ---- send primitives (id + framing handled) ----------------------------
  Stream* _io = nullptr;
  const char* _id;
  void startMsg(const __FlashStringHelper* type);  // "TYPE|id"
  void field(const char* s);                        // "|<s with | \n \r stripped>"
  void fieldRaw(const char* s);                     // "|<s>" (trusted constants)
  void fieldInt(long v);                             // "|<v>"
  void fieldFloat(double v, int decimals);           // "|<v>"
  void endMsg();                                      // newline
  void sendRoger(const char* which);

 private:
  const char *_type, *_name, *_desc, *_version;
  bool _active = false;
  char _rx[DASH_RX_BUFFER];
  uint8_t _rxLen = 0;

  void sendHello();
  void dispatch(char* line);
};

#endif  // DASH_MODULE_H
