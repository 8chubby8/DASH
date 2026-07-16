/* ===========================================================================
   DASH Module — Powertrain                    |  module type: SYSTEM
   Board: Espressif ESP32 DevKitC (WROOM-32)   |  transport: USB serial (CP210x)
   ---------------------------------------------------------------------------
   A REFERENCE DASH module, written to be read. Nothing here uses access a
   community builder couldn't have (the DASH "SDKable" principle).

   WHAT THIS MODULE IS
     The other half of a two-board agnosticism test. This board is a pretend
     POWERTRAIN controller — speed, revs, gear. Its twin, arduino.ino, is a
     pretend BODY controller — doors, lights, cabin sensing. They report
     DIFFERENT signals, on DIFFERENT boards, over DIFFERENT USB-serial chips
     (this one a Silicon Labs CP210x bridge, the R4 a native CDC-ACM device) —
     and DASH treats them identically, because a SYSTEM module is a SYSTEM
     module regardless of what silicon or cable it rode in on. That indifference
     is the whole point of the test.

   THE ONE DELIBERATE OVERLAP
     Both boards report ambient_temp. It is the same standard signal from two
     independent sources, and DASH's core is SOURCELESS (arduino.md §5): it
     stores "ambient_temp = <whatever arrived last>" without caring which module
     sent it. On the Serial Monitor you will see ambient_temp arrive under BOTH
     module ids — proof that redundant sources for one signal coexist by design.

   THE SIGNALS  (declared at install, one SYSTEM_SIGNAL line each — §7),
   exercising all three of DASH's §5a behaviours:
       gear_position   multi-state, store + event   cycles every 12 s
       vehicle_speed   continuous,  store only        every 500 ms (this board's own)
       engine_rpm      continuous,  store only        every 500 ms (this board's own)
       ambient_temp    continuous,  store only        every 1 s (SHARED with arduino)
       media_next      momentary,   event only        random, 10-25 s
                       (NO value field — its absence is the signal, §5a)

   STATE REPORTING  (arduino.md §4b)
     A SYSTEM module reports CURRENT STATE, not changes: a full dump of every
     stateful signal on ACTIVATE (alongside its ROGER), and a heartbeat every
     5 s re-sending them changed or not. Change detection is DASH's job, never
     the module's. Momentary controls have no state — never dumped, never
     heartbeated, fired only live.
   ===========================================================================*/

#include <string.h>        // strcmp — plain C string compare, no library needed
#include "esp_random.h"    // ESP32 hardware RNG — this board's seed source

/* ---------------------------------------------------------------------------
   TRANSPORT SELECTION
   The ESP32's USB port is Serial, bridged to USB by the on-board CP210x. The
   exact same code runs over a hardware UART by pointing this at Serial1/Serial2.
   --------------------------------------------------------------------------- */
#define DASH_SERIAL  Serial
#define DASH_BAUD    115200

/* ---------------------------------------------------------------------------
   MODULE IDENTITY  (arduino.md §3)
   A real module reads its own MAC at boot (the ESP32 has one, factory-unique).
   For a repeatable bench test we use an assigned id (the §3 no-MAC fallback) so
   DASH's saved record keys to a known value across reflashes. Bench sequence:
   the Body board is ...EE05, this Powertrain board ...EE06.
   --------------------------------------------------------------------------- */
const char* MODULE_ID = "0000DA58EE06";   // assigned bench id — keep unique

/* ---------------------------------------------------------------------------
   IDENTITY TEXT  (sent in HELLO; field caps from arduino.md §10)
   --------------------------------------------------------------------------- */
const char* MODULE_TYPE = "SYSTEM";                            // exactly one type (§4a)
const char* MODULE_NAME = "Powertrain";                        // <= 24 chars
const char* MODULE_DESC = "ESP32 powertrain: speed, rpm, gear"; // <= 64 chars
const char* MODULE_VERS = "v1.0";                              // <= 12 chars

/* ---------------------------------------------------------------------------
   THE SIGNALS  (one SYSTEM_SIGNAL line each at install — §7)
   --------------------------------------------------------------------------- */
const char* SIGNALS[] = {
  "gear_position", "vehicle_speed", "engine_rpm",
  "ambient_temp", "media_next"
};
const int SIGNAL_NUM = 5;

/* ---------------------------------------------------------------------------
   RUNTIME STATE
   A module persists NOTHING about install state — DASH is the single source of
   truth (§6). Boot SILENT; the only live state is whether we are ACTIVE plus
   the pretend car's current readings (what real firmware reads off its pins).
   --------------------------------------------------------------------------- */
bool active = false;          // SILENT until DASH sends ACTIVATE

int         speedKmh    = 0;      // swept 0..90 on a slow sine
int         rpm         = 0;      // loosely follows speed
int         gearIndex   = 0;
float       ambientTemp = 21.5;   // °C, engine-bay ambient (SHARED signal)
const char* GEARS[] = { "park", "reverse", "neutral", "drive" };

float speedPhase = 0.0;
float tempPhase  = 2.0;       // offset so it doesn't track speed

/* Schedulers — everything millis()-based, nothing blocks the loop. */
unsigned long lastStream    = 0;   // 500 ms: speed + rpm
unsigned long lastTemp      = 0;   // 1 s: ambient_temp (the shared signal)
unsigned long lastHeartbeat = 0;   // 5 s: §4b full state report
unsigned long lastGear      = 0;   // 12 s: cycle the gear
unsigned long nextMedia     = 0;   // random 10-25 s: media_next

const unsigned long STREAM_MS    = 500;
const unsigned long TEMP_MS      = 1000;
const unsigned long HEARTBEAT_MS = 5000;
const unsigned long GEAR_MS      = 12000;

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
// Sent on activation (the full dump) and on every heartbeat. media_next has
// no state — never reported here.
void reportState() {
  broadcast("gear_position", GEARS[gearIndex]);
  broadcastInt("vehicle_speed", speedKmh);
  broadcastInt("engine_rpm", rpm);
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
    lastStream = lastTemp = lastHeartbeat = lastGear = now;
    nextMedia = now + random(10000, 25000);
  }
  else if (strcmp(type, "DEACTIVATE") == 0) { active = false; sendRoger("deactivate"); }
  // Unknown TYPE: ignore — forward compatibility (§2).
}


/* ===========================  ARDUINO ENTRY POINTS  ====================== */

void setup() {
  DASH_SERIAL.begin(DASH_BAUD);
  randomSeed(esp_random());            // the ESP32's on-chip hardware RNG
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

  // ambient_temp — the SHARED signal. The R4 Body board reports its own
  // ambient_temp too; DASH's sourceless core keeps whichever arrived last (§5).
  if (now - lastTemp >= TEMP_MS) {
    lastTemp = now;
    tempPhase += 0.02;
    ambientTemp = 22.0 + 2.0 * sin(tempPhase);
    broadcastFloat("ambient_temp", ambientTemp, 1);
  }

  // The gear cycles park → reverse → neutral → drive (multi-state store+event).
  if (now - lastGear >= GEAR_MS) {
    lastGear = now;
    gearIndex = (gearIndex + 1) % 4;
    broadcast("gear_position", GEARS[gearIndex]);
  }

  // An occasional media_next press (event-only: fires and is forgotten).
  if (now >= nextMedia) {
    nextMedia = now + random(10000, 25000);
    broadcastEvent("media_next");
  }

  // §4b heartbeat: full state report, changed or not. DASH's change detection
  // (not ours) decides whether anything moved.
  if (now - lastHeartbeat >= HEARTBEAT_MS) {
    lastHeartbeat = now;
    reportState();
  }
}
