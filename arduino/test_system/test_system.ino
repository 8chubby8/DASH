/* ===========================================================================
   DASH Module — Test System                   |  module type: SYSTEM
   ---------------------------------------------------------------------------
   This is a REFERENCE DASH module, written to be read — same discipline as
   the steering wheel, accessory and listener sketches. Nothing here uses
   access a community builder couldn't have (the DASH "SDKable" principle).

   WHAT THIS MODULE IS
     A bench-test SYSTEM module — the hardware twin of DASH's built-in "Sim
     Vehicle" virtual module (DASH 1.4.7). A SYSTEM module reports vehicle
     state into DASH's sourceless core as standard signals; this one exists
     to prove DASH's system-message routing (roadmap 1.4.7) end to end on
     real copper:

         BROADCAST|id|function|value   →  gatekeeper  →  state store / events

     It has no real car to sense, so it invents one — the same pretend
     drive the Sim Vehicle runs — exercising all three of DASH's §5a
     behaviours (arduino/arduino.md, system_commands.md):

       vehicle_speed / engine_rpm  continuous, store-only   every 500 ms
       door_driver_open            boolean,  store+event    random, 8–20 s
       gear_position               multi-state, store+event cycles every 12 s
       headlights_on               boolean,  store+event    toggles every 15 s
       media_next                  momentary, event-only    random, 10–25 s
                                   (NO value field — its absence is the
                                    signal, arduino.md §5a)

     The finish line is DASH's State Inspector: speed/rpm tick in the store
     pane with NO event lines (store-only proven); door, gear and lights land
     in both panes (store+event); media_next appears ONLY in the event pane
     (event-only); and the 5 s heartbeat streams on the Serial Monitor while
     the event pane stays silent — DASH's change detection, visible by eye.

   STATE REPORTING  (arduino/arduino.md §4b — the part 1.4.7 consumes)
     A SYSTEM module reports CURRENT STATE, not changes:
       - on ACTIVATE, a full dump of every stateful signal it manages
         (alongside its ROGER), so DASH's store is never guessing;
       - every 5 s, a heartbeat re-sending all stateful values, changed or
         not. Change detection is DASH's job, not this module's — the
         firmware never tracks what changed, it just reports what it sees.
     Momentary controls (media_next) have no state: never dumped, never
     heartbeated, fired only live.

   WHAT IT DELIBERATELY DOES NOT DO
     - No physical inputs — the pretend drive is self-generated so the test
       needs nothing wired to the board. (A real SYSTEM module reads pins;
       pin-driven signals can be added here later if the bench wants them.)
     - No install payload — a SYSTEM module declares its signals, one
       SYSTEM_SIGNAL line each, and that is the entire handshake (§7).

   TARGET BOARD
     Arduino Uno R4 WiFi (same bench board as the other test modules).
   ===========================================================================*/

#include <string.h>   // strcmp — plain C string compare, no library needed

/* ---------------------------------------------------------------------------
   TRANSPORT SELECTION
   One ordinary Arduino Serial port. To run this exact code where the link to
   DASH is a UART instead, change ONE line to Serial1 — nothing else moves.
   --------------------------------------------------------------------------- */
#define DASH_SERIAL  Serial
#define DASH_BAUD    115200

/* ---------------------------------------------------------------------------
   MODULE IDENTITY  (arduino/arduino.md §3)
   Assigned id (the §3 no-MAC fallback), unique on this bench: steering wheel
   ...EE01, accessory ...EE02, listener ...EE03 — so this one is ...EE04.
   --------------------------------------------------------------------------- */
const char* MODULE_ID = "0000DA58EE04";   // PLACEHOLDER assigned id — make it unique

/* ---------------------------------------------------------------------------
   IDENTITY TEXT  (sent in HELLO; field caps from arduino/arduino.md §10)
   --------------------------------------------------------------------------- */
const char* MODULE_TYPE = "SYSTEM";                  // exactly one type (§4a)
const char* MODULE_NAME = "Test System";             // <= 24 chars
const char* MODULE_DESC = "1.4.7 test: BROADCAST + dump/heartbeat"; // <= 64
const char* MODULE_VERS = "v1.0";                    // <= 12 chars

/* ---------------------------------------------------------------------------
   THE SIGNALS  (declared at install, one SYSTEM_SIGNAL line each — §7)
   --------------------------------------------------------------------------- */
const char* SIGNALS[] = {
  "vehicle_speed", "engine_rpm", "door_driver_open",
  "gear_position", "headlights_on", "media_next"
};
const int SIGNAL_NUM = 6;

/* ---------------------------------------------------------------------------
   RUNTIME STATE
   A module persists NOTHING about install state — DASH is the single source
   of truth (§6). Boot SILENT; the only live-state is whether we are ACTIVE,
   plus the pretend car's current readings (what real firmware would read
   off its input pins).
   --------------------------------------------------------------------------- */
bool active = false;          // SILENT until DASH sends ACTIVATE

int         speedKmh   = 0;       // swept 0..90 on a slow sine
int         rpm        = 0;       // loosely follows speed
bool        doorOpen   = false;
bool        headlights = false;
int         gearIndex  = 0;
const char* GEARS[] = { "park", "reverse", "neutral", "drive" };

float speedPhase = 0.0;

/* Schedulers — everything is millis()-based, nothing blocks the loop. */
unsigned long lastStream     = 0;   // 500 ms: speed + rpm
unsigned long lastHeartbeat  = 0;   // 5 s: §4b full state report
unsigned long lastGear       = 0;   // 12 s: cycle the gear
unsigned long lastLights     = 0;   // 15 s: toggle the headlights
unsigned long nextDoorFlip   = 0;   // random 8–20 s
unsigned long nextMediaPress = 0;   // random 10–25 s

const unsigned long STREAM_MS    = 500;
const unsigned long HEARTBEAT_MS = 5000;
const unsigned long GEAR_MS      = 12000;
const unsigned long LIGHTS_MS    = 15000;

/* Inbound line assembly. 64 bytes is ample (§10). */
char    rxLine[64];
uint8_t rxLen = 0;


/* =====================  MESSAGES WE SEND TO DASH  ========================= */

// HELLO|id|type|name|description|version   — our reply to DISCOVER (§4, §7)
void sendHello() {
  DASH_SERIAL.print(F("HELLO|"));
  DASH_SERIAL.print(MODULE_ID);   DASH_SERIAL.print('|');
  DASH_SERIAL.print(MODULE_TYPE); DASH_SERIAL.print('|');
  DASH_SERIAL.print(MODULE_NAME); DASH_SERIAL.print('|');
  DASH_SERIAL.print(MODULE_DESC); DASH_SERIAL.print('|');
  DASH_SERIAL.println(MODULE_VERS);
}

// The install handshake for a SYSTEM module (§7): one SYSTEM_SIGNAL line per
// signal this module sends — including the momentary control — then the end.
void doInstall() {
  for (int i = 0; i < SIGNAL_NUM; i++) {
    DASH_SERIAL.print(F("SYSTEM_SIGNAL|"));
    DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
    DASH_SERIAL.println(SIGNALS[i]);
  }
  DASH_SERIAL.print(F("INSTALL_END|"));
  DASH_SERIAL.println(MODULE_ID);
}

// ROGER|id|which — acknowledges a command actually happened (§6).
void sendRoger(const char* which) {
  DASH_SERIAL.print(F("ROGER|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.println(which);
}

// BROADCAST|id|function|value — a standard signal's current value (§5).
void broadcast(const char* function, const char* value) {
  DASH_SERIAL.print(F("BROADCAST|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.print(function);  DASH_SERIAL.print('|');
  DASH_SERIAL.println(value);
}

void broadcastInt(const char* function, long value) {
  DASH_SERIAL.print(F("BROADCAST|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.print(function);  DASH_SERIAL.print('|');
  DASH_SERIAL.println(value);
}

// BROADCAST|id|function — an event-only momentary control. NO value field:
// its absence is how DASH knows there is nothing to store (§5a).
void broadcastEvent(const char* function) {
  DASH_SERIAL.print(F("BROADCAST|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.println(function);
}

// §4b state report: every STATEFUL signal's current value, changed or not.
// Sent on activation (the full dump) and on every heartbeat. media_next has
// no state — never reported here.
void reportState() {
  broadcastInt("vehicle_speed", speedKmh);
  broadcastInt("engine_rpm", rpm);
  broadcast("door_driver_open", doorOpen ? "true" : "false");
  broadcast("gear_position", GEARS[gearIndex]);
  broadcast("headlights_on", headlights ? "true" : "false");
}


/* =====================  MESSAGES WE RECEIVE FROM DASH  ==================== */

// Handle one complete line already sitting in rxLine (null-terminated).
void handleLine() {
  if (rxLen == 0) return;                       // ignore blank lines

  // Split on '|' in place — a plain split, exactly as §2 says.
  char* field[6];
  int   n = 0;
  field[n++] = rxLine;
  for (char* p = rxLine; *p && n < 6; p++) {
    if (*p == '|') { *p = '\0'; field[n++] = p + 1; }
  }
  const char* type = field[0];

  // DISCOVER is the one message with no id — everyone answers (§4).
  if (strcmp(type, "DISCOVER") == 0) { sendHello(); return; }

  // Every other command names its target. Not for us? Stay quiet (§5).
  if (n < 2 || strcmp(field[1], MODULE_ID) != 0) return;

  if      (strcmp(type, "INSTALL") == 0) { doInstall(); }
  else if (strcmp(type, "ACTIVATE") == 0) {
    active = true;
    sendRoger("activate");
    reportState();                     // §4b: the on-activation full dump
    // Restart the schedulers so nothing fires from a stale clock.
    unsigned long now = millis();
    lastStream = lastHeartbeat = lastGear = lastLights = now;
    nextDoorFlip   = now + random(8000, 20000);
    nextMediaPress = now + random(10000, 25000);
  }
  else if (strcmp(type, "DEACTIVATE") == 0) { active = false; sendRoger("deactivate"); }
  // Unknown TYPE: ignore — forward compatibility (§2).
}


/* ===========================  ARDUINO ENTRY POINTS  ====================== */

void setup() {
  DASH_SERIAL.begin(DASH_BAUD);
  randomSeed(analogRead(A0));          // a floating pin's noise is seed enough
  // Boot SILENT: send NOTHING until spoken to (§6).
}

void loop() {
  // Read whatever DASH has sent, assembling complete lines.
  while (DASH_SERIAL.available()) {
    char c = DASH_SERIAL.read();
    if (c == '\n') {                 // end of message
      rxLine[rxLen] = '\0';
      handleLine();
      rxLen = 0;
    } else if (c == '\r') {          // tolerate CRLF: ignore the CR (§1)
      /* skip */
    } else if (rxLen < sizeof(rxLine) - 1) {
      rxLine[rxLen++] = c;
    } else {
      rxLen = 0;                     // over-long line: drop it safely
    }
  }

  // Live data flows ONLY while active (§6).
  if (!active) return;
  unsigned long now = millis();

  // The pretend drive: speed sweeps 0..90 km/h on a slow sine, rpm follows.
  // Continuous signals — DASH stores them silently, fires no events.
  if (now - lastStream >= STREAM_MS) {
    lastStream = now;
    speedPhase += 0.05;
    speedKmh = (int)(((sin(speedPhase) + 1.0) / 2.0) * 90.0 + 0.5);
    rpm      = 750 + speedKmh * 47;
    broadcastInt("vehicle_speed", speedKmh);
    broadcastInt("engine_rpm", rpm);
  }

  // The random door — nobody pressed anything (store+event: both panes).
  if (now >= nextDoorFlip) {
    nextDoorFlip = now + random(8000, 20000);
    doorOpen = !doorOpen;
    broadcast("door_driver_open", doorOpen ? "true" : "false");
  }

  // The gear cycles park → reverse → neutral → drive (multi-state).
  if (now - lastGear >= GEAR_MS) {
    lastGear = now;
    gearIndex = (gearIndex + 1) % 4;
    broadcast("gear_position", GEARS[gearIndex]);
  }

  // Headlights toggle (boolean store+event).
  if (now - lastLights >= LIGHTS_MS) {
    lastLights = now;
    headlights = !headlights;
    broadcast("headlights_on", headlights ? "true" : "false");
  }

  // An occasional media_next press (event-only: event pane only, no store row).
  if (now >= nextMediaPress) {
    nextMediaPress = now + random(10000, 25000);
    broadcastEvent("media_next");
  }

  // §4b heartbeat: full state report, changed or not. DASH's change detection
  // (not ours) decides nothing has moved — heartbeats on the wire tap with a
  // silent event pane is that decision made visible.
  if (now - lastHeartbeat >= HEARTBEAT_MS) {
    lastHeartbeat = now;
    reportState();
  }
}
