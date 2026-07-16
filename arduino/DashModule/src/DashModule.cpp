#include "DashModule.h"
#include <string.h>

DashModule::DashModule(const char* id, const char* type, const char* name,
                       const char* description, const char* version)
    : _id(id), _type(type), _name(name), _desc(description), _version(version) {}

void DashModule::begin(Stream& io) {
  _io = &io;
  // Boot SILENT (§6): nothing is sent until DASH speaks first.
}

void DashModule::loop() {
  if (!_io) return;

  // Read whatever DASH has sent, assembling complete lines (§1). A stray '\r'
  // is dropped (CRLF tolerated); an over-long line is discarded safely.
  while (_io->available()) {
    char c = (char)_io->read();
    if (c == '\n') {
      _rx[_rxLen] = '\0';
      if (_rxLen > 0) dispatch(_rx);
      _rxLen = 0;
    } else if (c == '\r') {
      /* skip */
    } else if (_rxLen < DASH_RX_BUFFER - 1) {
      _rx[_rxLen++] = c;
    } else {
      _rxLen = 0;  // over-long line: drop it
    }
  }

  // Periodic service while active (e.g. the SYSTEM heartbeat).
  if (_active) onTick(millis());
}

void DashModule::linkLost() {
  if (_active) { _active = false; onDeactivated(); }  // go SILENT, run safe-state hook
  _rxLen = 0;                                          // drop any half-read line
}

// Split one line on '|' in place and dispatch by TYPE (§2, §4).
void DashModule::dispatch(char* line) {
  char* argv[DASH_MAX_FIELDS];
  int argc = 0;
  argv[argc++] = line;
  for (char* p = line; *p && argc < DASH_MAX_FIELDS; p++) {
    if (*p == '|') {
      *p = '\0';
      argv[argc++] = p + 1;
    }
  }
  const char* type = argv[0];

  // DISCOVER is the one message with no id — everyone answers (§4).
  if (strcmp(type, "DISCOVER") == 0) {
    sendHello();
    return;
  }

  // Every other command names its target. Not for us? Stay quiet (§5).
  if (argc < 2 || strcmp(argv[1], _id) != 0) return;

  if (strcmp(type, "INSTALL") == 0) {
    // The subclass emits its declarations; the base always closes the handshake.
    onInstall();
    startMsg(F("INSTALL_END"));
    endMsg();
  } else if (strcmp(type, "ACTIVATE") == 0) {
    // ACTIVATE is idempotent (Reconciliation re-asserts it every sweep as a
    // liveness ping — 1.4.6). Always ROGER (that IS the proof of life DASH wants),
    // but run the activation logic — the §4b dump, the heartbeat-clock reset, the
    // builder's onActivate hook — ONLY on the real SILENT->ACTIVE transition. Doing
    // it on every re-assertion resets the builder's own send-timers every sweep, so
    // during the fast-sweep phase (every 5 s for the first 60 s) any signal whose
    // change interval is longer than a sweep never advances — it appears frozen for
    // ~a minute after every connect. Guarding on the transition fixes that (1.4.16).
    bool wasActive = _active;
    _active = true;
    sendRoger("activate");
    if (!wasActive) onActivated();
  } else if (strcmp(type, "DEACTIVATE") == 0) {
    _active = false;
    sendRoger("deactivate");
    onDeactivated();
  } else {
    // LISTEN, ACTION, … — a subclass's concern.
    onCommand(argc, argv);
  }
}

void DashModule::sendHello() {
  startMsg(F("HELLO"));
  field(_type);
  field(_name);
  field(_desc);
  field(_version);
  endMsg();
}

void DashModule::sendRoger(const char* which) {
  startMsg(F("ROGER"));
  fieldRaw(which);
  endMsg();
}

// ---- framing primitives ----------------------------------------------------

void DashModule::startMsg(const __FlashStringHelper* type) {
  _io->print(type);
  _io->print('|');
  _io->print(_id);
}

void DashModule::field(const char* s) {
  _io->print('|');
  // Strip the two forbidden characters (§2), plus '\r' as a courtesy so a value
  // can never break framing on the receiver.
  for (; *s; ++s) {
    char c = *s;
    if (c != '|' && c != '\n' && c != '\r') _io->write((uint8_t)c);
  }
}

void DashModule::fieldRaw(const char* s) {
  _io->print('|');
  _io->print(s);
}

void DashModule::fieldInt(long v) {
  _io->print('|');
  _io->print(v);
}

void DashModule::fieldFloat(double v, int decimals) {
  _io->print('|');
  _io->print(v, decimals);
}

void DashModule::endMsg() { _io->print('\n'); }
