/* ===========================================================================
   DASH Module — Test Listener                |  module type: LISTENER
   ---------------------------------------------------------------------------
   This is a REFERENCE DASH module, written to be read — same discipline as
   the steering wheel (SYSTEM) and test accessory (ACCESSORY) sketches.
   Nothing here uses access a community builder couldn't have (the DASH
   "SDKable" principle).

   WHAT THIS MODULE IS
     A bench-test LISTENER module. LISTENER modules subscribe to standard
     DASH signals at install time and act on their own hardware when those
     signals are delivered at runtime (arduino/arduino.md §4c, §9). This one
     exists to prove DASH's LISTENER install path (roadmap 1.4.4) — the
     SUBSCRIBE declaration parser — and to model the runtime LISTEN
     behaviour a real LISTENER library will provide.

     It declares THREE subscriptions, chosen to cover the spectrum:
       1. headlights_on          — plain on-change boolean: every trailing
                                   field left off, the simplest legal form.
       2. steering_angle|20hz|2  — continuous signal with an explicit rate
                                   cap and threshold (deadband). DASH 1.4.4
                                   captures and displays these; they stay
                                   inert until the stream engine (1.4.8).
       3. media_next             — event-only momentary control: arrives as
                                   a LISTEN with NO value field (§4c).

     Its "own hardware" is the built-in LED — real, observable behaviour:
       headlights_on true/false  →  LED on/off
       media_next                →  LED pulses briefly
       steering_angle            →  stored silently (a real module would
                                    move something; here it exists to
                                    exercise the throttled subscription)

   THE INSTALL FINISH LINE (DASH 1.4.4)
     DISCOVER shows a LISTENER chip; INSTALL runs the indeterminate pulse
     (no MANIFEST byte total on this path); the pane turns green; DETAILS
     lists all three subscriptions with every declared field captured.

   THE RUNTIME TEST (before DASH 1.4.8 exists)
     DASH does not deliver LISTEN messages until the stream engine lands
     (roadmap 1.4.8). Until then, drive this module by hand from the DASH
     Serial Monitor's send box (or any serial monitor):

         LISTEN|0000DA58EE03|headlights_on|true     → LED on
         LISTEN|0000DA58EE03|headlights_on|true     → nothing (no change!)
         LISTEN|0000DA58EE03|headlights_on|false    → LED off
         LISTEN|0000DA58EE03|media_next             → LED pulses once

     The second line is the point: change detection lives in the module
     (§4c), so a heartbeat re-delivering the same value causes no action.

   THE WIRE  (arduino/arduino.md §1, §2)
     Plain UTF-8 text, one message per line, pipe-separated positional
     fields, trailing fields fall off the right. The only two characters
     forbidden inside a field are newline and the pipe itself.

   TARGET BOARD
     Arduino Uno R4 WiFi (same bench board as the other two reference
     modules). Its USB Serial enumerates to the host as a CDC serial port,
     so this behaves exactly like a classic Uno R3.
   ===========================================================================*/

#include <string.h>   // strcmp / strncpy — plain C string handling

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
   is ...EE01, test accessory ...EE02, so this one is ...EE03.
   --------------------------------------------------------------------------- */
const char* MODULE_ID = "0000DA58EE03";   // PLACEHOLDER assigned id — make it unique

/* ---------------------------------------------------------------------------
   IDENTITY TEXT  (sent in HELLO; field caps from arduino/arduino.md §10)
   --------------------------------------------------------------------------- */
const char* MODULE_TYPE = "LISTENER";                // exactly one type (§4a)
const char* MODULE_NAME = "Test Listener";           // <= 24 chars
const char* MODULE_DESC = "Install path test: SUBSCRIBE + LISTEN to LED"; // <= 64
const char* MODULE_VERS = "v0.1";                    // <= 12 chars

/* ---------------------------------------------------------------------------
   THE SUBSCRIPTIONS   (arduino/arduino.md §4c, §9)
   Each entry is everything after the id in one SUBSCRIBE line:

       SUBSCRIBE | id | function | rate | threshold | gate | gate_value

   All fields after `function` are optional and fall off the right; blank
   middle fields are legal (`20hz||reverse|active` = rate + gate, no
   threshold). Signal names come from system_commands.md — the authoritative
   vocabulary. Defaults for omitted fields are defined there per signal, so
   the plain `headlights_on` form is a complete, correct subscription.
   --------------------------------------------------------------------------- */
const char* SUBSCRIPTIONS[] = {
  "headlights_on",          // boolean, on-change delivery (all defaults)
  "steering_angle|20hz|2",  // continuous, explicit rate cap + deadband
  "media_next",             // event-only: LISTEN arrives with no value
};
const int SUBSCRIPTION_NUM = 3;

/* ---------------------------------------------------------------------------
   RUNTIME STATE
   A module persists NOTHING about install state — DASH is the single source
   of truth (§6). Boot SILENT; the only live-state is whether we are ACTIVE
   plus the last-known values below.

   THE CHANGE-DETECTION STORE (§4c). The controller re-delivers subscribed
   stateful signals on a 5 s heartbeat whether or not they changed; comparing
   and acting-only-on-a-difference is the MODULE's job, and in the real SDK
   it lives in the firmware library, not the builder's code. This sketch
   models that library: lastKnown[] is the store, onSignalChanged() is the
   one function a builder would actually write. Event-only signals have no
   entry — no state, nothing to compare (§4c).
   --------------------------------------------------------------------------- */
bool active = false;          // SILENT until DASH sends ACTIVATE

struct LastKnown {
  const char* function;       // which stateful signal
  char        value[16];      // last value seen ("" = never seen)
};
LastKnown lastKnown[] = {
  { "headlights_on",  "" },
  { "steering_angle", "" },
};
const int LAST_KNOWN_NUM = 2;

/* Inbound line assembly. 64 bytes is ample — the longest message DASH ever
   sends is a LISTEN (§10). */
char    rxLine[64];
uint8_t rxLen = 0;

/* Non-blocking LED pulse for event-only signals: while millis() is before
   pulseUntil the LED shows the inverse of its base state. */
unsigned long pulseUntil = 0;
const unsigned long PULSE_MS = 300;
bool ledBase = false;         // what the LED shows when not pulsing


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

// The install handshake for a LISTENER (§7): one SUBSCRIBE line per signal
// it wants delivered, then INSTALL_END. No assets, no signal declarations.
void doInstall() {
  for (int i = 0; i < SUBSCRIPTION_NUM; i++) {
    DASH_SERIAL.print(F("SUBSCRIBE|"));
    DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
    DASH_SERIAL.println(SUBSCRIPTIONS[i]);
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


/* ==================  ACTING ON DELIVERED SIGNALS (the job)  =============== */

// The builder's callback — the ONLY part of the runtime a real module author
// would write (§4c: the library does the comparing, you do the reacting).
// This module's hardware is one LED; a real one might switch a relay here.
void onSignalChanged(const char* function, const char* value) {
  if (strcmp(function, "headlights_on") == 0) {
    ledBase = (strcmp(value, "true") == 0);
    digitalWrite(LED_BUILTIN, ledBase ? HIGH : LOW);
  }
  // steering_angle: stored, no visible action — it exists to exercise the
  // throttled subscription form. A real module would move something here.
}

// The builder's event callback — event-only signals carry no value and have
// no state; they fire the moment they arrive, every time they arrive (§4c).
void onSignalEvent(const char* function) {
  if (strcmp(function, "media_next") == 0) {
    pulseUntil = millis() + PULSE_MS;
    digitalWrite(LED_BUILTIN, ledBase ? LOW : HIGH);   // visible blip
  }
}

// The "library" half: given a delivered LISTEN, do change detection against
// the store and invoke the right callback. Stateful signals (they carry a
// value) fire onSignalChanged only when the value differs from the last one
// seen; event-only signals (no value field) fire onSignalEvent every time.
void handleListen(const char* function, const char* value) {
  if (value == NULL) {                    // no value field → event-only (§4c)
    onSignalEvent(function);
    return;
  }
  for (int i = 0; i < LAST_KNOWN_NUM; i++) {
    if (strcmp(lastKnown[i].function, function) != 0) continue;
    if (strcmp(lastKnown[i].value, value) == 0) return;   // heartbeat, no change
    strncpy(lastKnown[i].value, value, sizeof(lastKnown[i].value) - 1);
    lastKnown[i].value[sizeof(lastKnown[i].value) - 1] = '\0';
    onSignalChanged(function, value);
    return;
  }
  // A signal we never subscribed to: ignore. DASH shouldn't send one, but
  // being well-mannered about the unexpected is the protocol's whole style.
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

  if      (strcmp(type, "INSTALL")    == 0) { doInstall(); }
  else if (strcmp(type, "ACTIVATE")   == 0) { active = true;  sendRoger("activate"); }
  else if (strcmp(type, "DEACTIVATE") == 0) {
    active = false;
    sendRoger("deactivate");
    // Leave the hardware in a safe state: a deactivated module should not
    // go on holding an output based on data it will no longer receive.
    ledBase = false;
    digitalWrite(LED_BUILTIN, LOW);
  }
  else if (strcmp(type, "LISTEN")     == 0) {
    // LISTEN|id|function|value — value absent for event-only signals. Only
    // act while active: DASH only delivers to active modules (§6), but a
    // hand-typed test line should obey the same rule the real wire does.
    if (active && n >= 3) handleListen(field[2], (n >= 4) ? field[3] : NULL);
  }
  // Unknown TYPE: ignore — forward compatibility (§2).
}


/* ===========================  ARDUINO ENTRY POINTS  ====================== */

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);
  DASH_SERIAL.begin(DASH_BAUD);
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

  // End a running pulse, restoring the LED to its base (stateful) meaning.
  if (pulseUntil != 0 && millis() >= pulseUntil) {
    pulseUntil = 0;
    digitalWrite(LED_BUILTIN, ledBase ? HIGH : LOW);
  }
}
