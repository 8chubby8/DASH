#include "DashSystem.h"

void DashSystem::addSignal(const char* function) {
  if (_signalCount < DASH_MAX_SIGNALS) _signals[_signalCount++] = function;
}

// Install handshake for a SYSTEM module (§7): one SYSTEM_SIGNAL line per declared
// signal. The base appends INSTALL_END.
void DashSystem::onInstall() {
  for (int i = 0; i < _signalCount; i++) {
    startMsg(F("SYSTEM_SIGNAL"));
    fieldRaw(_signals[i]);
    endMsg();
  }
}

// On activation (§4b): the full state dump, then a fresh heartbeat clock, then the
// builder's optional activate hook. The base has already sent ROGER.
void DashSystem::onActivated() {
  if (_report) _report();
  _lastHeartbeat = millis();
  if (_onActivate) _onActivate();
}

// The §4b heartbeat: re-send current state every _heartbeatMs, changed or not.
void DashSystem::onTick(unsigned long now) {
  if (_report && now - _lastHeartbeat >= _heartbeatMs) {
    _lastHeartbeat = now;
    _report();
  }
}

void DashSystem::broadcast(const char* function, const char* value) {
  if (!isActive()) return;  // data only while active (§6)
  startMsg(F("BROADCAST"));
  field(function);
  field(value);
  endMsg();
}

void DashSystem::broadcast(const char* function, long value) {
  if (!isActive()) return;
  startMsg(F("BROADCAST"));
  field(function);
  fieldInt(value);
  endMsg();
}

void DashSystem::broadcast(const char* function, double value, int decimals) {
  if (!isActive()) return;
  startMsg(F("BROADCAST"));
  field(function);
  fieldFloat(value, decimals);
  endMsg();
}

// Event-only momentary control — no value field (§5a).
void DashSystem::event(const char* function) {
  if (!isActive()) return;
  startMsg(F("BROADCAST"));
  field(function);
  endMsg();
}
