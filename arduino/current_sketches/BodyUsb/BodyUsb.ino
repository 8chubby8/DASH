/* ===========================================================================
   DASH Module — Body                    |  module type: SYSTEM
   Board: Arduino Uno R4 (WiFi or Minima) |  transport: USB serial (CDC)
   Built on the DashModule library.
   ---------------------------------------------------------------------------
   The reference SYSTEM module: a pretend body controller (doors, lights, cabin)
   talking to DASH over the USB cable. The wire protocol — framing, HELLO on
   DISCOVER, the install handshake, the SILENT/ACTIVE lifecycle with ROGER, the
   §4b activation dump and 5 s heartbeat — all lives in the library. What remains
   here is only this board's own job: its sensors and when to send them. That is
   the SDKable principle made concrete — a community builder writes exactly this
   much and no more.

   THE SIGNALS  (one SYSTEM_SIGNAL line each at install), exercising all three of
   DASH's §5a behaviours:
       door_driver_open   boolean,   store + event   random, 8-20 s
       headlights_on      boolean,   store + event   toggles every 15 s
       ambient_light      continuous store only       every 1 s
       ambient_temp       continuous store only       every 1 s (SHARED with the ESP32)
       button_home_pressed  momentary event only      random, 12-30 s
   =========================================================================== */
#include <Dash.h>

// Identity + type in one line. USB transport: DASH is the host, we are Serial.
DashSystem dash("0000DA58EE05", "Body", "R4 body: doors, lights, cabin", "v1.0");

/* -------- this board's own pretend car (what real firmware reads off pins) --- */
bool  doorOpen    = false;
bool  headlights  = false;
int   ambientLux  = 0;       // 0..1000, swept on a slow sine
float ambientTemp = 19.5;    // °C, drifts slowly (SHARED with the ESP32 board)
float luxPhase = 0.0, tempPhase = 1.0;

/* -------- the builder's own schedulers (millis-based; nothing blocks) -------- */
unsigned long lastStream   = 0;   // 1 s: ambient_light + ambient_temp
unsigned long lastLights   = 0;   // 15 s: toggle headlights
unsigned long nextDoorFlip = 0;   // random 8-20 s
unsigned long nextButton   = 0;   // random 12-30 s: button_home_pressed
const unsigned long STREAM_MS = 1000, LIGHTS_MS = 15000;

/* -------- state report: current value of every STATEFUL signal (§4b) ---------
   The library calls this on the activation dump and on every 5 s heartbeat. We
   never track change or manage the heartbeat clock — DASH owns change detection,
   the library owns the clock. The momentary button has no state (fired live only). */
void report() {
  dash.broadcast("door_driver_open", doorOpen ? "true" : "false");
  dash.broadcast("headlights_on",    headlights ? "true" : "false");
  dash.broadcast("ambient_light",    (long)ambientLux);
  dash.broadcast("ambient_temp",     ambientTemp, 1);
}

/* -------- runs once on the SILENT->ACTIVE transition: start schedulers fresh -- */
void onActivate() {
  unsigned long now = millis();
  lastStream = lastLights = now;
  nextDoorFlip = now + random(8000, 20000);
  nextButton   = now + random(12000, 30000);
}

void setup() {
  Serial.begin(115200);        // USB transport: DASH is the host
  dash.begin(Serial);

  // Declare our signals — one SYSTEM_SIGNAL line each at install.
  dash.addSignal("door_driver_open");
  dash.addSignal("headlights_on");
  dash.addSignal("ambient_light");
  dash.addSignal("ambient_temp");
  dash.addSignal("button_home_pressed");   // momentary event-only control

  dash.onReport(report);
  dash.onActivate(onActivate);

  randomSeed(analogRead(A0));
}

void loop() {
  dash.loop();                 // pump the wire + run the heartbeat/dump for us
  if (!dash.isActive()) return;  // our own logic runs only while active

  unsigned long now = millis();

  // Continuous sensing — DASH stores these silently (§5a). We broadcast them
  // immediately here for a live feed; the heartbeat covers them regardless.
  if (now - lastStream >= STREAM_MS) {
    lastStream = now;
    luxPhase  += 0.06;
    tempPhase += 0.02;
    ambientLux  = (int)(((sin(luxPhase) + 1.0) / 2.0) * 1000.0 + 0.5);
    ambientTemp = 20.0 + 2.0 * sin(tempPhase);
    dash.broadcast("ambient_light", (long)ambientLux);
    dash.broadcast("ambient_temp", ambientTemp, 1);
  }

  // The random driver's door — broadcast immediately on change for low latency.
  if (now >= nextDoorFlip) {
    nextDoorFlip = now + random(8000, 20000);
    doorOpen = !doorOpen;
    dash.broadcast("door_driver_open", doorOpen ? "true" : "false");
  }

  // Headlights toggle.
  if (now - lastLights >= LIGHTS_MS) {
    lastLights = now;
    headlights = !headlights;
    dash.broadcast("headlights_on", headlights ? "true" : "false");
  }

  // An occasional home-button press — event-only: fires and is forgotten (§5a).
  if (now >= nextButton) {
    nextButton = now + random(12000, 30000);
    dash.event("button_home_pressed");
  }
}
