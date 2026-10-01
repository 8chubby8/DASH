/* ===========================================================================
   ClimateModule — the Climate ACCESSORY, shared by all three transports
   ===========================================================================
   ClimateWifi owns this file. ClimateBt and ClimateUsb copy it, along with
   assets/, when their make_assets.py runs — one source, three sketches, so
   the firmware behind the three panels cannot drift apart any more than the
   artwork can.

   THIS FIRMWARE HOLDS STRINGS AND NOTHING ELSE, AND THAT IS THE POINT.
     DASH sends the destination value, never a direction (module-layout.md
     §8a) — `ACTION|id|fan|4`, not `ACTION|id|fan_up`. So this module never
     steps anything, never counts, and **never needs its own copy of the
     value lists**. It is told a string, it keeps the string, it reports the
     string. The lists live in one place, the layout, and cannot drift.

     What it must do is spell them the same way the layout does: `lvl1` not
     `level1`, `off` not `0`. Those strings are the contract on both sides.

   IT REPORTS FACTS AND NEVER ISSUES INSTRUCTIONS (§8)
     Nothing here mentions a button, a colour or a panel. apply() moves this
     module's state; the layout decides what that looks like. Which is why
     the side effects below need no cooperation from DASH at all: pressing
     SCREEN turns recirculation off in here, this module reports both, and
     the panel redraws both. DASH is never told the rule and never needs it.

   NO HARDWARE BEHIND IT YET
     Every variable is a string in RAM. Wiring this to a real climate system
     means filling in apply() and nothing else — not the layout, not the
     wire, not a line of DASH.
   =========================================================================== */
#ifndef CLIMATE_MODULE_H
#define CLIMATE_MODULE_H

#include <Dash.h>

/* On the USB sketch `Serial` IS the wire and a stray print would corrupt the
   protocol, so that sketch defines DASH_SILENT before including this file.
   The other two log to their spare UART. */
#ifdef DASH_SILENT
#define CLIMATE_LOG(x)
#define CLIMATE_LOGLN(x)
#else
#define CLIMATE_LOG(x)   Serial.print(x)
#define CLIMATE_LOGLN(x) Serial.println(x)
#endif

#ifndef CLIMATE_VALUE_MAX
#define CLIMATE_VALUE_MAX 12      // longest value on any list, plus room ("16.5", "lvl1")
#endif

class ClimateModule : public DashAccessory {
 public:
  /* The library (DashAccessory, 1.6.10) streams the install payload, runs the
     heartbeat (§4b) and routes ACTIONs here. A 2 s heartbeat means a DASH that
     somehow missed a report is corrected within a couple of seconds rather
     than for ever; DASH drops an unchanged value before it reaches its store,
     so it is free and disturbs no outstanding prediction. */
  ClimateModule(const char* id, const char* name, const char* description,
                const char* version)
      : DashAccessory(id, name, description, version) {
    setAssets(DASH_ASSETS, DASH_ASSET_COUNT);
    setHeartbeat(2000);
  }

 protected:
  // Activation and every heartbeat: everything this module knows, said out loud.
  void reportAll() override { dump(); }

  /* ACTION|id|control|value — a control on the panel was operated.

     A control this firmware does not recognise is ignored in silence: the
     layout on the tablet may be newer than the firmware on the board, and
     answering an unknown control with an error would turn ordinary version
     skew into a fault. Otherwise everything that changed — including the side
     effects — is reported straight away. */
  void handleAction(const char* control, const char* value) override {
    if (!*value) return;                     // every climate control carries a value
    if (slotFor(control) < 0) return;        // not ours — stay quiet
    apply(control, value);
    for (uint8_t i = 0; i < COUNT; i++) {
      if (strcmp(_state[i], _sent[i]) != 0) reportOne(i);
    }
  }

  void onDeactivated() override {
    DashAccessory::onDeactivated();
    CLIMATE_LOGLN(F("[dash] SILENT"));
  }

 private:
  // The order here is the order of the dump. Nothing depends on it but a
  // human reading the Serial Monitor, which is reason enough to group it.
  // Everything before COUNT is a panel variable and is reported. LAST_VENT sits
  // *after* it deliberately: it is this module's own memory of which vent the
  // driver last chose by hand, it is nobody else's business, and the dump loop
  // stops at COUNT so it never goes on the wire.
  enum { FAN, TEMP, MODE, AUTO, RECIRC, AC, SCREEN, SEAT_L, SEAT_R, COUNT,
         LAST_VENT, TOTAL };

  static const char* nameOf(uint8_t i) {
    switch (i) {
      case FAN:    return "fan";
      case TEMP:   return "temp";
      case MODE:   return "mode";
      case AUTO:   return "auto";
      case RECIRC: return "recirc";
      case AC:     return "ac";
      case SCREEN: return "screen";
      case SEAT_L: return "seat_left";
      case SEAT_R: return "seat_right";
      default:     return "";        // never reached: the dump stops at COUNT
    }
  }

  /* Move this module's state.

     THE SIDE EFFECTS ARE THE INTERESTING PART. A real climate system drops
     out of AUTO the moment you touch the fan, and kills recirculation when
     you demist — so this one does too. DASH is told none of that: it sends
     one ACTION, this module changes two things, reports both, and the panel
     redraws both. The rule lives here, where the hardware is, and nowhere
     else. That is what makes a module's logic genuinely its own. */
  void apply(const char* control, const char* value) {
    int8_t slot = slotFor(control);
    if (slot < 0) return;                    // not ours — stay quiet
    set(slot, value);

    /* Leaving AUTO — but not on temperature.

       AUTO is *targeting* a temperature, so changing the target is working the
       system rather than overriding it, and the setpoint stays under automatic
       control. Fan speed and vent direction are the things AUTO was deciding
       for you, so touching either of those takes it back off it. */
    if (slot == FAN || slot == MODE) set(AUTO, "off");
    // Demist wants outside air.
    if (slot == SCREEN && strcmp(value, "on") == 0) set(RECIRC, "off");

    /* AUTO OWNS THE VENTS, AND SAYS SO BY TAKING THEM.

       Going automatic sets `mode` to a value none of the three vent zones
       match, so all three go unlit together — the panel needs no concept of
       "nothing selected" and the layout needs no fourth binding, because a
       variable that holds `auto` simply satisfies no case.

       Coming back out restores whichever vent was last chosen by hand, which
       is why _lastVent exists. Leaving `mode` on `auto` with AUTO switched off
       would show three dark zones and no way to tell which was live.

       DASH is told none of this. It sends one ACTION for the button that was
       pressed; this module changes two variables and reports both, and the
       panel redraws both. The rule lives here, with the hardware. */
    if (slot == MODE && strcmp(value, "auto") != 0) set(LAST_VENT, value);
    if (slot == AUTO) {
      if (strcmp(value, "on") == 0) { set(SCREEN, "off"); set(MODE, "auto"); }
      else                          { set(MODE, _state[LAST_VENT]); }
    }

    CLIMATE_LOG(F("[dash] ")); CLIMATE_LOG(control);
    CLIMATE_LOG(F(" = ")); CLIMATE_LOGLN(value);
  }

  int8_t slotFor(const char* control) {
    for (uint8_t i = 0; i < COUNT; i++) {
      if (strcmp(control, nameOf(i)) == 0) return (int8_t)i;
    }
    return -1;
  }

  void set(uint8_t slot, const char* value) {
    strncpy(_state[slot], value, CLIMATE_VALUE_MAX - 1);
    _state[slot][CLIMATE_VALUE_MAX - 1] = '\0';
  }

  /* Everything this module knows, said out loud (§8). The panel is drawn from
     DASH's store, so until something arrives there is nothing in it — a module
     that waits for its next change leaves the panel blank until then. */
  void dump() {
    for (uint8_t i = 0; i < COUNT; i++) reportOne(i);
  }

  void reportOne(uint8_t i) {
    report(nameOf(i), _state[i]);
    strncpy(_sent[i], _state[i], CLIMATE_VALUE_MAX);
  }

  // Startup state. Every one of these must appear on its variable's list in
  // the layout, spelled identically, or DASH has no index to step from and
  // the first press on that control cannot be drawn until the module answers.
  char _state[TOTAL][CLIMATE_VALUE_MAX] = {
    "2", "21", "feet", "off", "off", "off", "off", "off", "off",
    "feet"                          // LAST_VENT — internal, never reported
  };
  // What DASH has been told. The change check compares against this rather
  // than tracking a dirty flag, so a report that never went out is retried.
  char _sent[TOTAL][CLIMATE_VALUE_MAX] = { "", "", "", "", "", "", "", "", "", "" };

};

#endif  // CLIMATE_MODULE_H
