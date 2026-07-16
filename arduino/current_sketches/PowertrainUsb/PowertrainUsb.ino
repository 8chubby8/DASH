/* ===========================================================================
   DASH Module — Powertrain               |  module type: SYSTEM
   Board: Espressif ESP32 (any)           |  transport: USB serial (CDC)
   Built on the DashModule library.
   ---------------------------------------------------------------------------
   The powertrain twin of BodyUsb: a pretend drivetrain controller — speed, revs,
   gear — over the USB cable. The library owns the whole protocol; this sketch is
   only the board's own sensors and their timing. Same signal set as the classic
   esp32.ino reference, refactored onto the SDK.

   THE SIGNALS  (one SYSTEM_SIGNAL line each at install):
       gear_position   multi-state, store + event   cycles every 12 s
       vehicle_speed   continuous,  store only        every 500 ms
       engine_rpm      continuous,  store only        every 500 ms
       ambient_temp    continuous,  store only        every 1 s (SHARED with the Body)
       media_next      momentary,   event only        random, 10-25 s
   =========================================================================== */
#include <Dash.h>

// Identity + type in one line. USB transport: DASH is the host, we are Serial.
DashSystem dash("0000DA58EE06", "Powertrain", "ESP32 powertrain: speed, rpm, gear", "v1.0");

/* -------- this board's own pretend drivetrain --------------------------------- */
const char* GEARS[] = { "park", "reverse", "neutral", "drive" };
int   gearIndex   = 0;
int   speedKmh    = 0;       // 0..90, swept on a slow sine
int   rpm         = 750;
float ambientTemp = 22.0;    // °C (SHARED with the Body board)
float speedPhase = 0.0, tempPhase = 0.0;

/* -------- the builder's own schedulers (millis-based; nothing blocks) -------- */
unsigned long lastStream = 0;   // 500 ms: vehicle_speed + engine_rpm
unsigned long lastTemp   = 0;   // 1 s: ambient_temp
unsigned long lastGear   = 0;   // 12 s: cycle the gear
unsigned long nextMedia  = 0;   // random 10-25 s: media_next
const unsigned long STREAM_MS = 500, TEMP_MS = 1000, GEAR_MS = 12000;

/* -------- state report: current value of every STATEFUL signal (§4b) ---------
   Called on the activation dump and every 5 s heartbeat. media_next has no state
   (momentary), so it is fired live only, never reported here. */
void report() {
  dash.broadcast("gear_position", GEARS[gearIndex]);
  dash.broadcast("vehicle_speed", (long)speedKmh);
  dash.broadcast("engine_rpm",    (long)rpm);
  dash.broadcast("ambient_temp",  ambientTemp, 1);
}

/* -------- runs once on the SILENT->ACTIVE transition: start schedulers fresh -- */
void onActivate() {
  unsigned long now = millis();
  lastStream = lastTemp = lastGear = now;
  nextMedia = now + random(10000, 25000);
}

void setup() {
  Serial.begin(115200);        // USB transport: DASH is the host
  dash.begin(Serial);

  dash.addSignal("gear_position");
  dash.addSignal("vehicle_speed");
  dash.addSignal("engine_rpm");
  dash.addSignal("ambient_temp");
  dash.addSignal("media_next");      // momentary event-only control

  dash.onReport(report);
  dash.onActivate(onActivate);

  randomSeed(esp_random());
}

void loop() {
  dash.loop();                 // pump the wire + run the heartbeat/dump for us
  if (!dash.isActive()) return;  // our own logic runs only while active

  unsigned long now = millis();

  // The pretend drive: speed sweeps 0..90 km/h on a slow sine, rpm follows.
  if (now - lastStream >= STREAM_MS) {
    lastStream = now;
    speedPhase += 0.05;
    speedKmh = (int)(((sin(speedPhase) + 1.0) / 2.0) * 90.0 + 0.5);
    rpm      = 750 + speedKmh * 47;
    dash.broadcast("vehicle_speed", (long)speedKmh);
    dash.broadcast("engine_rpm", (long)rpm);
  }

  // ambient_temp — the SHARED signal; DASH's sourceless core keeps whichever
  // source arrived last (§5).
  if (now - lastTemp >= TEMP_MS) {
    lastTemp = now;
    tempPhase += 0.02;
    ambientTemp = 22.0 + 2.0 * sin(tempPhase);
    dash.broadcast("ambient_temp", ambientTemp, 1);
  }

  // The gear cycles park -> reverse -> neutral -> drive (multi-state store+event).
  if (now - lastGear >= GEAR_MS) {
    lastGear = now;
    gearIndex = (gearIndex + 1) % 4;
    dash.broadcast("gear_position", GEARS[gearIndex]);
  }

  // An occasional media_next press — event-only: fires and is forgotten (§5a).
  if (now >= nextMedia) {
    nextMedia = now + random(10000, 25000);
    dash.event("media_next");
  }
}
