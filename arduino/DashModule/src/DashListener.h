/* ===========================================================================
   DashListener — a LISTENER module (module-sdk.md §4c, §9)
   ---------------------------------------------------------------------------
   A LISTENER subscribes to standard DASH signals at install and acts on its own
   hardware when they are delivered. The library handles everything on the wire;
   the builder writes only:

     1. subscribe("signal") for each signal wanted — the library emits the
        SUBSCRIBE line at install, filling a blank rate/threshold with the
        signal's default from dash_signals.h (a continuous signal gets its rate
        + deadband; a boolean/event signal stays blank = event-driven, §4c).
        Override the defaults by passing rate/threshold (and optionally a gate).
     2. onSignal(fn) — called with (function, value) ONLY when a subscribed
        stateful signal's value has actually changed. The library does the
        change detection against a per-signal store (§4c), so a 5 s heartbeat
        re-delivering the same value never calls you.
     3. onEvent(fn) — called with (function) each time an event-only signal
        fires (a LISTEN with no value field, §4c). No state, no change detection.

   Optionally onDeactivate(fn) to put hardware in a safe state when DASH stops
   delivering. All inbound is gated on ACTIVE (§6).
   =========================================================================== */
#ifndef DASH_LISTENER_H
#define DASH_LISTENER_H

#include "DashModule.h"

#ifndef DASH_MAX_SUBSCRIPTIONS
#define DASH_MAX_SUBSCRIPTIONS 12
#endif

// Longest LISTEN value the change-detection store keeps per subscription.
#ifndef DASH_LISTEN_VALUE_MAX
#define DASH_LISTEN_VALUE_MAX 16
#endif

class DashListener : public DashModule {
 public:
  DashListener(const char* id, const char* name, const char* description,
               const char* version)
      : DashModule(id, "LISTENER", name, description, version) {}

  // Subscribe to a signal. Blank trailing fields are filled with the signal's
  // default at install (§4c). The three forms cover all-defaults, an explicit
  // rate+threshold, and the full form with a gate.
  void subscribe(const char* function);
  void subscribe(const char* function, const char* rate, const char* threshold);
  void subscribe(const char* function, const char* rate, const char* threshold,
                 const char* gate, const char* gateValue);

  // Called only when a subscribed stateful signal's value has changed.
  void onSignal(void (*cb)(const char* function, const char* value)) { _onSignal = cb; }
  // Called each time an event-only signal fires (no value).
  void onEvent(void (*cb)(const char* function)) { _onEvent = cb; }
  // Optional: called when the module goes SILENT — put hardware in a safe state.
  void onDeactivate(void (*cb)()) { _onDeactivate = cb; }

 protected:
  void onInstall() override;                        // emit one SUBSCRIBE per subscription
  void onDeactivated() override;                    // builder safe-state hook
  void onCommand(int argc, char** argv) override;   // handle inbound LISTEN

 private:
  struct Sub {
    const char* function;
    const char* rate;
    const char* threshold;
    const char* gate;
    const char* gateValue;
    char last[DASH_LISTEN_VALUE_MAX];  // last value seen ("" = never)
  };
  Sub _subs[DASH_MAX_SUBSCRIPTIONS];
  int _subCount = 0;

  void (*_onSignal)(const char*, const char*) = nullptr;
  void (*_onEvent)(const char*) = nullptr;
  void (*_onDeactivate)() = nullptr;

  void add(const char* fn, const char* rate, const char* thr,
           const char* gate, const char* gateVal);
  void handleListen(int argc, char** argv);
};

#endif  // DASH_LISTENER_H
