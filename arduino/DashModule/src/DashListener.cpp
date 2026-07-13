#include "DashListener.h"
#include "dash_signals.h"
#include <string.h>

void DashListener::subscribe(const char* function) {
  add(function, "", "", "", "");
}

void DashListener::subscribe(const char* function, const char* rate,
                             const char* threshold) {
  add(function, rate, threshold, "", "");
}

void DashListener::subscribe(const char* function, const char* rate,
                             const char* threshold, const char* gate,
                             const char* gateValue) {
  add(function, rate, threshold, gate, gateValue);
}

void DashListener::add(const char* fn, const char* rate, const char* thr,
                       const char* gate, const char* gateVal) {
  if (_subCount >= DASH_MAX_SUBSCRIPTIONS) return;
  Sub& s = _subs[_subCount++];
  s.function = fn;
  s.rate = rate;
  s.threshold = thr;
  s.gate = gate;
  s.gateValue = gateVal;
  s.last[0] = '\0';
}

// Install handshake for a LISTENER (§7): one SUBSCRIBE line per subscription.
// A blank rate/threshold on a continuous signal is filled from the signal table
// before the line is sent (§4c); everything else stays blank (event-driven).
// Fields fall off the right, but a blank middle field is preserved when a later
// field is present (e.g. rate + gate with no threshold).
void DashListener::onInstall() {
  for (int i = 0; i < _subCount; i++) {
    Sub& s = _subs[i];
    const char* rate = s.rate;
    const char* thr = s.threshold;
    const DashContinuousDefault* def = dashContinuous(s.function);
    if (def) {
      if (rate[0] == '\0') rate = def->rate;
      if (thr[0] == '\0') thr = def->threshold;
    }
    const char* fields[4] = {rate, thr, s.gate, s.gateValue};
    int last = -1;
    for (int f = 0; f < 4; f++)
      if (fields[f][0] != '\0') last = f;

    startMsg(F("SUBSCRIBE"));
    fieldRaw(s.function);
    for (int f = 0; f <= last; f++) fieldRaw(fields[f]);  // empty ⇒ bare "|"
    endMsg();
  }
}

void DashListener::onDeactivated() {
  if (_onDeactivate) _onDeactivate();
}

void DashListener::onCommand(int argc, char** argv) {
  if (strcmp(argv[0], "LISTEN") == 0) handleListen(argc, argv);
}

// LISTEN|id|function|value — value absent for event-only signals (§4c). Change
// detection lives here: a stateful signal fires onSignal only when its value
// differs from the last one seen; an event-only signal (no value) fires onEvent
// every time. DASH only delivers to active modules, but gate defensively.
void DashListener::handleListen(int argc, char** argv) {
  if (!isActive() || argc < 3) return;
  const char* function = argv[2];

  if (argc < 4) {  // no value field ⇒ event-only
    if (_onEvent) _onEvent(function);
    return;
  }
  const char* value = argv[3];
  for (int i = 0; i < _subCount; i++) {
    if (strcmp(_subs[i].function, function) != 0) continue;
    if (strcmp(_subs[i].last, value) == 0) return;  // heartbeat, no change
    strncpy(_subs[i].last, value, DASH_LISTEN_VALUE_MAX - 1);
    _subs[i].last[DASH_LISTEN_VALUE_MAX - 1] = '\0';
    if (_onSignal) _onSignal(function, value);
    return;
  }
  // A signal we never subscribed to: ignore (well-mannered, §2).
}
