/* ===========================================================================
   DashSystem — a SYSTEM module (module-sdk.md §4a, §4b)
   ---------------------------------------------------------------------------
   A SYSTEM module produces DASH's standard signals. The library handles the
   whole lifecycle and the §4b state-reporting discipline; the builder writes
   only two things:

     1. addSignal("name") for each standard signal the module sends — this is
        the install declaration (one SYSTEM_SIGNAL line each, §7).
     2. onReport(fn) — a function that broadcasts the CURRENT value of every
        stateful signal. The library calls it automatically on the activation
        dump and on every 5 s heartbeat (§4b). The builder never tracks change
        or manages heartbeat timing — DASH owns change detection, the library
        owns the clock.

   For low latency the builder may also broadcast() the instant its own logic
   changes something (e.g. a door interrupt), on top of the heartbeat — DASH
   dedupes. event() sends a momentary event-only control (no value field, §5a).
   Every send is gated on ACTIVE, so the "data only while active" rule (§6) is
   automatic — a broadcast while SILENT is simply dropped.
   =========================================================================== */
#ifndef DASH_SYSTEM_H
#define DASH_SYSTEM_H

#include "DashModule.h"

#ifndef DASH_MAX_SIGNALS
#define DASH_MAX_SIGNALS 16
#endif

class DashSystem : public DashModule {
 public:
  DashSystem(const char* id, const char* name, const char* description,
             const char* version)
      : DashModule(id, "SYSTEM", name, description, version) {}

  // Declare a standard signal this module sends (stateful or event-only). Emitted
  // as a SYSTEM_SIGNAL line during the install handshake.
  void addSignal(const char* function);

  // The builder's state-report function: broadcast every stateful signal's
  // current value. Called by the library on the activation dump and each heartbeat.
  void onReport(void (*report)()) { _report = report; }

  // Optional: run once each time the module goes ACTIVE (after ROGER + dump) —
  // e.g. reset the builder's own schedulers off a fresh clock.
  void onActivate(void (*cb)()) { _onActivate = cb; }

  // Optional heartbeat override (default 5000 ms, the §4b recommendation).
  void setHeartbeat(unsigned long ms) { _heartbeatMs = ms; }

  // ---- send a standard signal (BROADCAST) — gated on ACTIVE (§6) ----------
  void broadcast(const char* function, const char* value);
  void broadcast(const char* function, long value);
  void broadcast(const char* function, int value) { broadcast(function, (long)value); }
  void broadcast(const char* function, double value, int decimals = 1);
  // A momentary event-only control — no value field; its absence is the signal (§5a).
  void event(const char* function);

 protected:
  void onInstall() override;               // emit one SYSTEM_SIGNAL per addSignal
  void onActivated() override;             // dump + reset heartbeat clock + builder hook
  void onTick(unsigned long now) override; // the 5 s heartbeat

 private:
  const char* _signals[DASH_MAX_SIGNALS];
  int _signalCount = 0;
  void (*_report)() = nullptr;
  void (*_onActivate)() = nullptr;
  unsigned long _heartbeatMs = 5000;
  unsigned long _lastHeartbeat = 0;
};

#endif  // DASH_SYSTEM_H
