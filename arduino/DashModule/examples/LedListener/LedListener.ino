/* ===========================================================================
   DASH Module — LED Listener            |  module type: LISTENER
   Built on the DashModule library       |  transport: USB serial (CDC)
   ---------------------------------------------------------------------------
   The reference LISTENER module, on the DASH SDK library (roadmap 1.4.15).
   Compare it to arduino/old_test/test_listener.ino: the SUBSCRIBE line
   emission, the SUBSCRIBE default-filling, and the whole change-detection
   store are gone into the library. What remains is only the module's job —
   what to do when a signal changes. That is the SDKable principle: a builder
   writes exactly this much.

   It subscribes to three signals covering the spectrum, and its "hardware" is
   the built-in LED (a real module would switch a relay):
     - headlights_on  → LED follows (plain on-change boolean)
     - steering_angle → subscribed with the continuous default rate+deadband,
                        stored but not shown (a real module would move something)
     - media_next     → event-only: the LED blips once each time it fires

   END-TO-END BENCH TEST (DASH 1.4.8+ delivers real LISTEN messages): install
   this alongside the Body SYSTEM module — Body broadcasts headlights_on every
   15 s, DASH relays it here, and the LED follows the pretend headlights. Two
   modules, one signal, mediated by DASH's sourceless core. Or drive it by hand
   from the Serial Monitor:
       LISTEN|0000DA58EE0B|headlights_on|true     → LED on
       LISTEN|0000DA58EE0B|headlights_on|true     → nothing (no change!)
       LISTEN|0000DA58EE0B|media_next             → LED blips

   Bench id map: …EE0B (LED Listener). Others: Body …EE05, Powertrain …EE06,
   Body WiFi …EE07, Powertrain BT …EE08, Big Test Accessory …EE0A.
   =========================================================================== */
#include <Dash.h>

DashListener dash("0000DA58EE0B", "LED Listener",
                  "Follows headlights, blips on media_next", "v1.0");

unsigned long pulseUntil = 0;         // non-blocking blip for event-only signals
const unsigned long PULSE_MS = 300;
bool ledBase = false;                 // what the LED shows when not blipping

/* -------- the only runtime a builder writes: react to a changed signal ------ */
void onSignal(const char* function, const char* value) {
  if (strcmp(function, "headlights_on") == 0) {
    ledBase = (strcmp(value, "true") == 0);
    digitalWrite(LED_BUILTIN, ledBase ? HIGH : LOW);
  }
  // steering_angle arrives here too (throttled by its default rate); this module
  // just stores it by reacting to nothing — a real one would move a needle.
}

/* -------- event-only signals: fire every time, no state (§4c) --------------- */
void onEvent(const char* function) {
  if (strcmp(function, "media_next") == 0) {
    pulseUntil = millis() + PULSE_MS;
    digitalWrite(LED_BUILTIN, ledBase ? LOW : HIGH);   // visible blip
  }
}

/* -------- put hardware safe when DASH stops delivering ----------------------- */
void onDeactivate() {
  ledBase = false;
  digitalWrite(LED_BUILTIN, LOW);
}

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  Serial.begin(115200);
  dash.begin(Serial);

  dash.subscribe("headlights_on");            // boolean → on-change (all defaults)
  dash.subscribe("steering_angle");           // continuous → library fills 20hz|2
  dash.subscribe("media_next");               // event-only → LISTEN with no value

  dash.onSignal(onSignal);
  dash.onEvent(onEvent);
  dash.onDeactivate(onDeactivate);
}

void loop() {
  dash.loop();

  // End a running blip, restoring the LED to its base (stateful) meaning.
  if (pulseUntil != 0 && millis() >= pulseUntil) {
    pulseUntil = 0;
    digitalWrite(LED_BUILTIN, ledBase ? HIGH : LOW);
  }
}
