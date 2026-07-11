/* ===========================================================================
   DASH Module — Body                          |  module type: SYSTEM
   Board: Arduino Uno R4 WiFi                   |  transport: USB serial (CDC)
   ---------------------------------------------------------------------------
   A REFERENCE DASH module, written to be read. Nothing here uses access a
   community builder couldn't have (the DASH "SDKable" principle).

   WHAT THIS MODULE IS
     One half of a two-board agnosticism test. This board is a pretend BODY
     controller — doors and lights and cabin sensing. Its twin, esp32.ino, is
     a pretend POWERTRAIN controller — speed, revs, gear. They report
     DIFFERENT signals, on DIFFERENT boards, over DIFFERENT USB-serial chips
     (this one a CDC-ACM device, the ESP32 a CP210x bridge) — and DASH treats
     them identically, because a SYSTEM module is a SYSTEM module regardless of
     what silicon or cable it rode in on. That indifference is the whole point.

   THE ONE DELIBERATE OVERLAP
     Both boards report ambient_temp. It is the same standard signal from two
     independent sources, and DASH's core is SOURCELESS (arduino.md §5): it
     stores "ambient_temp = <whatever arrived last>" without caring which module
     sent it. On the Serial Monitor you will see ambient_temp arrive under BOTH
     module ids — proof that redundant sources for one signal coexist by design.

   THE SIGNALS  (declared at install, one SYSTEM_SIGNAL line each — §7),
   exercising all three of DASH's §5a behaviours:
       door_driver_open   boolean,   store + event   random, 8-20 s
       headlights_on      boolean,   store + event   toggles every 15 s
       ambient_light      continuous store only       every 1 s (this board's own)
       ambient_temp       continuous store only       every 1 s (SHARED with esp32)
       button_home_pressed  momentary event only      random, 12-30 s
                            (NO value field — its absence is the signal, §5a)

   STATE REPORTING  (arduino.md §4b)
     A SYSTEM module reports CURRENT STATE, not changes: a full dump of every
     stateful signal on ACTIVATE (alongside its ROGER), and a heartbeat every
     5 s re-sending them changed or not. Change detection is DASH's job, never
     the module's. Momentary controls have no state — never dumped, never
     heartbeated, fired only live.
   ===========================================================================*/

#include <string.h>   // strcmp — plain C string compare, no library needed

/* ---------------------------------------------------------------------------
   TRANSPORT SELECTION
   One ordinary Arduino Serial port over USB. Change this one line to Serial1
   to run the exact same code over a hardware UART — nothing else moves.
   --------------------------------------------------------------------------- */
#define DASH_SERIAL  Serial
#define DASH_BAUD    115200

/* ---------------------------------------------------------------------------
   MODULE IDENTITY  (arduino.md §3)
   A real module reads its own MAC at boot (the R4 WiFi has one). For a
   repeatable bench test we use an assigned id (the §3 no-MAC fallback) so
   DASH's saved record keys to a known value across reflashes. Bench sequence:
   ...EE04 was test_system; this Body board is ...EE05, the ESP32 ...EE06.
   --------------------------------------------------------------------------- */
const char* MODULE_ID = "0000DA58EE05";   // assigned bench id — keep unique

/* ---------------------------------------------------------------------------
   IDENTITY TEXT  (sent in HELLO; field caps from arduino.md §10)
   --------------------------------------------------------------------------- */
const char* MODULE_TYPE = "SYSTEM";                        // exactly one type (§4a)
const char* MODULE_NAME = "Body";                          // <= 24 chars
const char* MODULE_DESC = "R4 body: doors, lights, cabin"; // <= 64 chars
const char* MODULE_VERS = "v1.0";                          // <= 12 chars

/* ---------------------------------------------------------------------------
   THE SIGNALS  (one SYSTEM_SIGNAL line each at install — §7)
   --------------------------------------------------------------------------- */
const char* SIGNALS[] = {
  "door_driver_open", "headlights_on",
  "ambient_light", "ambient_temp",
  "button_home_pressed"
};
const int SIGNAL_NUM = 5;

/* ---------------------------------------------------------------------------
   RUNTIME STATE
   A module persists NOTHING about install state — DASH is the single source of
   truth (§6). Boot SILENT; the only live state is whether we are ACTIVE plus
   the pretend car's current readings (what real firmware reads off its pins).
   --------------------------------------------------------------------------- */
bool active = false;          // SILENT until DASH sends ACTIVATE

bool  doorOpen    = false;
bool  headlights  = false;
int   ambientLux  = 0;        // 0..1000, swept on a slow sine
float ambientTemp = 19.5;     // °C, drifts slowly around ~20 (SHARED signal)

float luxPhase  = 0.0;
float tempPhase = 1.0;        // offset so it doesn't track the lux exactly

/* Schedulers — everything millis()-based, nothing blocks the loop. */
unsigned long lastStream    = 0;   // 1 s: ambient_light + ambient_temp
unsigned long lastHeartbeat = 0;   // 5 s: §4b full state report
unsigned long lastLights    = 0;   // 15 s: toggle headlights
unsigned long nextDoorFlip  = 0;   // random 8-20 s
unsigned long nextButton    = 0;   // random 12-30 s: button_home_pressed

const unsigned long STREAM_MS    = 1000;
const unsigned long HEARTBEAT_MS = 5000;
const unsigned long LIGHTS_MS    = 15000;

/* Inbound line assembly. 64 bytes is ample (§10). */
char    rxLine[64];
uint8_t rxLen = 0;


/* =====================  MESSAGES WE SEND TO DASH  ========================= */

// HELLO|id|type|name|description|version — our reply to DISCOVER (§4, §7)
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

void broadcastFloat(const char* function, float value, int decimals) {
  DASH_SERIAL.print(F("BROADCAST|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.print(function);  DASH_SERIAL.print('|');
  DASH_SERIAL.println(value, decimals);
}

// BROADCAST|id|function — an event-only momentary control. NO value field:
// its absence is how DASH knows there is nothing to store (§5a).
void broadcastEvent(const char* function) {
  DASH_SERIAL.print(F("BROADCAST|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.println(function);
}

// §4b state report: every STATEFUL signal's current value, changed or not.
// Sent on activation (the full dump) and on every heartbeat. The momentary
// button has no state — never reported here.
void reportState() {
  broadcast("door_driver_open", doorOpen ? "true" : "false");
  broadcast("headlights_on", headlights ? "true" : "false");
  broadcastInt("ambient_light", ambientLux);
  broadcastFloat("ambient_temp", ambientTemp, 1);
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
    lastStream = lastHeartbeat = lastLights = now;
    nextDoorFlip = now + random(8000, 20000);
    nextButton   = now + random(12000, 30000);
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

  // Continuous sensing — DASH stores these silently, fires no events.
  // ambient_light sweeps 0..1000 lux; ambient_temp drifts ~18..22 °C. The temp
  // is the SHARED signal — the ESP32 reports its own ambient_temp too, and
  // DASH's sourceless core keeps whichever arrived last (§5).
  if (now - lastStream >= STREAM_MS) {
    lastStream = now;
    luxPhase  += 0.06;
    tempPhase += 0.02;
    ambientLux  = (int)(((sin(luxPhase) + 1.0) / 2.0) * 1000.0 + 0.5);
    ambientTemp = 20.0 + 2.0 * sin(tempPhase);
    broadcastInt("ambient_light", ambientLux);
    broadcastFloat("ambient_temp", ambientTemp, 1);
  }

  // The random driver's door — store+event, so it lands in both DASH panes.
  if (now >= nextDoorFlip) {
    nextDoorFlip = now + random(8000, 20000);
    doorOpen = !doorOpen;
    broadcast("door_driver_open", doorOpen ? "true" : "false");
  }

  // Headlights toggle (boolean store+event).
  if (now - lastLights >= LIGHTS_MS) {
    lastLights = now;
    headlights = !headlights;
    broadcast("headlights_on", headlights ? "true" : "false");
  }

  // An occasional home-button press (event-only: fires and is forgotten).
  if (now >= nextButton) {
    nextButton = now + random(12000, 30000);
    broadcastEvent("button_home_pressed");
  }

  // §4b heartbeat: full state report, changed or not. DASH's change detection
  // (not ours) decides whether anything moved.
  if (now - lastHeartbeat >= HEARTBEAT_MS) {
    lastHeartbeat = now;
    reportState();
  }
}
