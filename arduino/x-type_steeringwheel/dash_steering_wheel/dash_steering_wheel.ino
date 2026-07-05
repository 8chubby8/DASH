/* ===========================================================================
   DASH Module — X-Type Steering Wheel        |  module type: SYSTEM
   ---------------------------------------------------------------------------
   This is a REFERENCE DASH module. It is written to be read. Every community
   module — built-in or third-party — talks to DASH exactly this way, and this
   file is meant to be copied, understood, and adapted. There is nothing here a
   community builder could not do (the DASH "SDKable" principle).

   WHAT THIS MODULE IS
     A SYSTEM module. SYSTEM modules speak DASH's *known vocabulary* (standard
     vehicle/head-unit signals) and are SEND-ONLY: they BROADCAST signals out
     and never receive live data. The steering-wheel buttons are the canonical
     example (see transport.md and arduino/arduino.md).

   WHAT THIS FILE DOES *SO FAR*
     Only the lifecycle handshake — the conversation that gets a module
     discovered, installed and activated by DASH:

         DISCOVER  -> HELLO
         INSTALL   -> (declare each signal) -> INSTALL_END
         ACTIVATE  -> ROGER
         DEACTIVATE-> ROGER

     The actual button reading (a resistor ladder on an analog pin) is NOT here
     yet — it is deliberately deferred. The handshake is the foundation; we
     prove it first, against DASH, before adding hardware.

   THE WIRE  (arduino/arduino.md §1, §2)
     Plain UTF-8 text, one message per line. Every message has the same shape:
         TYPE | id | field3 | field4 | ...
     Fields are pipe-separated and positional; trailing fields fall off the
     right. The only two characters forbidden inside a field are newline and
     the pipe itself, so parsing is a simple split — no escaping, ever.

   TARGET BOARD
     Arduino Uno R4 WiFi. Its USB Serial enumerates straight to the host (DASH)
     as a CDC serial port, so this behaves exactly like a classic Uno R3.
   ===========================================================================*/

#include <string.h>   // strcmp — plain C string compare, no library needed

/* ---------------------------------------------------------------------------
   TRANSPORT SELECTION
   The protocol talks over one ordinary Arduino Serial port. On this board that
   is the USB Serial. To run this exact code on a board where the link to DASH
   is a UART instead, change ONE line below to Serial1 — nothing else moves.
   --------------------------------------------------------------------------- */
#define DASH_SERIAL  Serial
#define DASH_BAUD    115200

/* ---------------------------------------------------------------------------
   MODULE IDENTITY  (arduino/arduino.md §3)
   The id is a 12-hex MAC address, uppercase, separators removed. It rides in
   every message so DASH always knows which module is speaking.

   This Uno R4 WiFi HAS a real MAC (via its WiFi module). Reading it is a
   deliberate LATER step — it pulls in the WiFi library and a little boot-time
   setup. For now we use an ASSIGNED id: the §3 fallback that any board without
   a MAC (e.g. a bare Uno R3) must use. The rule is simply: use the MAC where
   you have one; otherwise it is on you to guarantee uniqueness.
   --------------------------------------------------------------------------- */
const char* MODULE_ID = "0000DA58EE01";   // PLACEHOLDER assigned id — make it unique

/* ---------------------------------------------------------------------------
   IDENTITY TEXT  (sent in HELLO; field caps from arduino/arduino.md §10)
   --------------------------------------------------------------------------- */
const char* MODULE_TYPE = "SYSTEM";               // exactly one type (§4a)
const char* MODULE_NAME = "Steering Wheel";       // <= 24 chars
const char* MODULE_DESC = "X-Type wheel buttons"; // <= 64 chars
const char* MODULE_VERS = "v0.1";                 // <= 12 chars

/* ---------------------------------------------------------------------------
   STANDARD SIGNALS THIS MODULE BROADCASTS   (PROVISIONAL names)
   At install, a SYSTEM module declares each signal it will send — one per line
   (arduino/arduino.md §7). The function is the *name* (e.g. "volume"); the
   value (up / down / next ...) is sent later in a BROADCAST when a button is
   pressed. These names are provisional until the DASH signal vocabulary is
   ratified, and are trivial to change.
   --------------------------------------------------------------------------- */
const char* SYSTEM_SIGNALS[]  = { "volume", "track", "source" };
const int   SYSTEM_SIGNAL_NUM = 3;

/* ---------------------------------------------------------------------------
   RUNTIME STATE
   A module persists NOTHING about install state — DASH is the single source of
   truth (§6). The module simply boots SILENT and does what it is told each
   session. Its only live-state is whether it is currently ACTIVE (allowed to
   send live data).
   --------------------------------------------------------------------------- */
bool active = false;          // SILENT until DASH sends ACTIVATE

/* Inbound line assembly. 64 bytes is ample: the longest message DASH ever
   sends a module is a LISTEN, which a SYSTEM module never even receives (§10). */
char    rxLine[64];
uint8_t rxLen = 0;

/* OPTIONAL: once ACTIVE, emit a stand-in BROADCAST on a timer so you can watch
   live data flow end-to-end before any buttons exist. Uncomment to enable. */
//#define SEND_TEST_HEARTBEAT
#ifdef SEND_TEST_HEARTBEAT
unsigned long lastBeat = 0;
#endif


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

// The install handshake. A SYSTEM module declares each signal it broadcasts,
// one per line, then ends the handshake. No assets, no subscriptions. (§7)
void doInstall() {
  for (int i = 0; i < SYSTEM_SIGNAL_NUM; i++) {
    DASH_SERIAL.print(F("SYSTEM_SIGNAL|"));
    DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
    DASH_SERIAL.println(SYSTEM_SIGNALS[i]);
  }
  // NOTE / OPEN QUESTION: arduino.md §2 says every message names its module,
  // so we send the id here. The §7 example writes "INSTALL_END" bare — a
  // conformance point to settle with the DASH side. We follow the §2 rule.
  DASH_SERIAL.print(F("INSTALL_END|"));
  DASH_SERIAL.println(MODULE_ID);
}

// ROGER|id|which — acknowledges a command actually happened (§6). ACTIVATE and
// DEACTIVATE are the acknowledged commands; live data is fire-and-forget.
void sendRoger(const char* which) {
  DASH_SERIAL.print(F("ROGER|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.println(which);
}

// BROADCAST|id|function|value — a standard signal going out (§4). Used by the
// button code later; exposed now so the optional heartbeat can exercise it.
void sendBroadcast(const char* function, const char* value) {
  DASH_SERIAL.print(F("BROADCAST|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.print(function);  DASH_SERIAL.print('|');
  DASH_SERIAL.println(value);
}


/* =====================  MESSAGES WE RECEIVE FROM DASH  ==================== */

// Handle one complete line already sitting in rxLine (null-terminated).
void handleLine() {
  if (rxLen == 0) return;                       // ignore blank lines

  // Split on '|' in place: walk the line, turn each pipe into a terminator,
  // and remember where each field starts. A plain split — exactly as §2 says.
  char* field[6];
  int   n = 0;
  field[n++] = rxLine;
  for (char* p = rxLine; *p && n < 6; p++) {
    if (*p == '|') { *p = '\0'; field[n++] = p + 1; }
  }
  const char* type = field[0];

  // DISCOVER is the one message with no id — it goes to everyone (§4). Anyone
  // who hears it answers with HELLO, regardless of state.
  if (strcmp(type, "DISCOVER") == 0) { sendHello(); return; }

  // Every other command names its target module in field 2. If it is not for
  // us, stay quiet — this is what keeps a shared bus orderly.
  if (n < 2 || strcmp(field[1], MODULE_ID) != 0) return;

  if      (strcmp(type, "INSTALL")    == 0) { doInstall(); }
  else if (strcmp(type, "ACTIVATE")   == 0) { active = true;  sendRoger("activate"); }
  else if (strcmp(type, "DEACTIVATE") == 0) { active = false; sendRoger("deactivate"); }
  // Unknown TYPE: ignore it. Ignoring what we don't recognise is how the
  // protocol stays forward-compatible (§2).
}


/* ===========================  ARDUINO ENTRY POINTS  ====================== */

void setup() {
  DASH_SERIAL.begin(DASH_BAUD);
  // Boot SILENT: we send NOTHING until spoken to. We answer DISCOVER, and we
  // act on commands addressed to us. That is the whole of startup.
}

void loop() {
  // 1) Read whatever DASH has sent, assembling complete lines.
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

  // 2) Live data flows ONLY while active (§6). Real button BROADCASTs will live
  //    here. For now, an optional heartbeat proves the loop end-to-end.
#ifdef SEND_TEST_HEARTBEAT
  if (active && millis() - lastBeat >= 2000) {
    lastBeat = millis();
    sendBroadcast("volume", "up");   // stand-in for a real button press
  }
#endif
}
