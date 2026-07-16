/* ===========================================================================
   DASH Module — Body (WiFi)                   |  module type: SYSTEM
   Board: Arduino Uno R4 WiFi                   |  transport: WiFi TCP (client)
   ---------------------------------------------------------------------------
   A REFERENCE DASH module, written to be read. Nothing here uses access a
   community builder couldn't have (the DASH "SDKable" principle).

   WHAT THIS MODULE IS
     The WiFi twin of arduino.ino ("Body"). SAME module, SAME signals, SAME
     firmware discipline — only the pipe is different. Where arduino.ino talks
     to DASH over USB-serial (CDC), this talks over a TCP socket to DASH's
     module server (roadmap 1.4.11). That is the whole point of the exercise:
     a SYSTEM module is a SYSTEM module regardless of what carried its bytes.
     DASH's controller, core, install, database and reconciliation are all
     transport-agnostic, so this board goes through the identical lifecycle —
     DISCOVER → HELLO → INSTALL → ACTIVATE → live data — over the air.

   THE INVERSION vs USB (transport.md)
     On USB, DASH is the host and opens the device. On WiFi, DASH runs a TCP
     SERVER and this module is the CLIENT that connects IN. So the firmware's
     one added job over the serial version is keeping the link up: associate to
     WiFi, connect to DASH's server, and — the important bit — go SILENT again
     on any drop, waiting for DASH to re-DISCOVER and re-ACTIVATE us. That is
     the same self-heal a brown-out reboot gets; the module lifecycle (§6)
     already demands it, so a dropped socket needs no special handling beyond
     "forget we were active."

   THE MESSAGE LAYER IS UNCHANGED
     WiFiClient is an Arduino Stream, exactly like Serial — .print/.println/
     .available/.read all behave identically. So every send/receive function
     below is byte-for-byte what arduino.ino does; only DASH_LINK changed from
     `Serial` to `client`. Serial is now free, so we use it for human-readable
     bench debug ("WiFi up", "link up") — it is NOT the DASH pipe here.

   THE SIGNALS  (declared at install, one SYSTEM_SIGNAL line each — §7),
   exercising all three of DASH's §5a behaviours:
       door_driver_open   boolean,   store + event   random, 8-20 s
       headlights_on      boolean,   store + event   toggles every 15 s
       ambient_light      continuous store only       every 1 s (this board's own)
       ambient_temp       continuous store only       every 1 s (SHARED with esp32)
       button_home_pressed  momentary event only      random, 12-30 s
                            (NO value field — its absence is the signal, §5a)

   STATE REPORTING  (arduino.md §4b)
     Full dump of every stateful signal on ACTIVATE (with the ROGER), and a
     heartbeat every 5 s re-sending them changed or not. Change detection is
     DASH's job. Momentary controls have no state — fired only live.
   ===========================================================================*/

#include <string.h>       // strcmp — plain C string compare, no library needed
#include <WiFiS3.h>       // Uno R4 WiFi radio (onboard ESP32-S3)
#include "arduino_secrets.h"   // SECRET_SSID / SECRET_PASS / DASH_HOST / DASH_PORT

/* ---------------------------------------------------------------------------
   TRANSPORT
   The DASH pipe is a TCP client to DASH's server (DASH_HOST:DASH_PORT). Every
   message function writes to DASH_LINK — swapping this one alias back to Serial
   would give you the exact USB sketch. Serial stays for debug only.
   --------------------------------------------------------------------------- */
WiFiClient client;
#define DASH_LINK  client
#define DBG        Serial          // human-readable bench log, not the DASH pipe

/* ---------------------------------------------------------------------------
   MODULE IDENTITY  (arduino.md §3)
   The R4 WiFi has a real MAC (WiFi.macAddress()) and production firmware would
   use it — the §3 recommended path. For a repeatable bench test we use an
   assigned id (the §3 no-MAC fallback) so DASH's saved record keys to a known
   value across reflashes. Bench sequence: ...EE05 = Body (serial), ...EE06 =
   Powertrain (esp32), ...EE07 = this Body-over-WiFi — a DISTINCT id so it can
   coexist with the serial Body without an id clash (§3).
   --------------------------------------------------------------------------- */
const char* MODULE_ID = "0000DA58EE07";   // assigned bench id — keep unique

/* ---------------------------------------------------------------------------
   IDENTITY TEXT  (sent in HELLO; field caps from arduino.md §10)
   --------------------------------------------------------------------------- */
const char* MODULE_TYPE = "SYSTEM";                        // exactly one type (§4a)
const char* MODULE_NAME = "Body WiFi";                     // <= 24 chars
const char* MODULE_DESC = "R4 body over WiFi: doors, lights, cabin"; // <= 64 chars
const char* MODULE_VERS = "v1.1";                          // <= 12 chars  (bumped for the 1.4.13 mismatch bench)

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
   the pretend car's current readings.
   --------------------------------------------------------------------------- */
bool active = false;          // SILENT until DASH sends ACTIVATE
bool linkUp = false;          // TCP socket to DASH currently connected?

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
unsigned long lastLinkTry   = 0;   // rate-limit reconnection attempts

const unsigned long STREAM_MS    = 1000;
const unsigned long HEARTBEAT_MS = 5000;
const unsigned long LIGHTS_MS    = 15000;
const unsigned long LINK_RETRY_MS = 3000;   // don't hammer WiFi/TCP reconnects

/* Inbound line assembly. 64 bytes is ample (§10). */
char    rxLine[64];
uint8_t rxLen = 0;


/* =====================  MESSAGES WE SEND TO DASH  ========================= */
/* Identical to the serial sketch — only DASH_LINK differs (it is `client`). */

// HELLO|id|type|name|description|version — our reply to DISCOVER (§4, §7)
void sendHello() {
  DASH_LINK.print(F("HELLO|"));
  DASH_LINK.print(MODULE_ID);   DASH_LINK.print('|');
  DASH_LINK.print(MODULE_TYPE); DASH_LINK.print('|');
  DASH_LINK.print(MODULE_NAME); DASH_LINK.print('|');
  DASH_LINK.print(MODULE_DESC); DASH_LINK.print('|');
  DASH_LINK.println(MODULE_VERS);
}

// The install handshake for a SYSTEM module (§7): one SYSTEM_SIGNAL line per
// signal this module sends — including the momentary control — then the end.
void doInstall() {
  for (int i = 0; i < SIGNAL_NUM; i++) {
    DASH_LINK.print(F("SYSTEM_SIGNAL|"));
    DASH_LINK.print(MODULE_ID); DASH_LINK.print('|');
    DASH_LINK.println(SIGNALS[i]);
  }
  DASH_LINK.print(F("INSTALL_END|"));
  DASH_LINK.println(MODULE_ID);
}

// ROGER|id|which — acknowledges a command actually happened (§6).
void sendRoger(const char* which) {
  DASH_LINK.print(F("ROGER|"));
  DASH_LINK.print(MODULE_ID); DASH_LINK.print('|');
  DASH_LINK.println(which);
}

// BROADCAST|id|function|value — a standard signal's current value (§5).
void broadcast(const char* function, const char* value) {
  DASH_LINK.print(F("BROADCAST|"));
  DASH_LINK.print(MODULE_ID); DASH_LINK.print('|');
  DASH_LINK.print(function);  DASH_LINK.print('|');
  DASH_LINK.println(value);
}

void broadcastInt(const char* function, long value) {
  DASH_LINK.print(F("BROADCAST|"));
  DASH_LINK.print(MODULE_ID); DASH_LINK.print('|');
  DASH_LINK.print(function);  DASH_LINK.print('|');
  DASH_LINK.println(value);
}

void broadcastFloat(const char* function, float value, int decimals) {
  DASH_LINK.print(F("BROADCAST|"));
  DASH_LINK.print(MODULE_ID); DASH_LINK.print('|');
  DASH_LINK.print(function);  DASH_LINK.print('|');
  DASH_LINK.println(value, decimals);
}

// BROADCAST|id|function — an event-only momentary control. NO value field:
// its absence is how DASH knows there is nothing to store (§5a).
void broadcastEvent(const char* function) {
  DASH_LINK.print(F("BROADCAST|"));
  DASH_LINK.print(MODULE_ID); DASH_LINK.print('|');
  DASH_LINK.println(function);
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


/* ==========================  KEEPING THE LINK UP  ======================== */

// Called when the socket goes away (drop, reset, or never yet up). We forget we
// were active and reset the inbound assembler — on reconnect we are SILENT
// again until DASH re-DISCOVERs and re-ACTIVATEs us (§6). No state desync is
// possible because the module remembers nothing across the gap.
void onLinkLost() {
  if (linkUp) DBG.println(F("[link] lost — going SILENT, will reconnect"));
  linkUp = false;
  active = false;
  rxLen  = 0;
}

// Ensure WiFi is associated and the TCP socket to DASH is open. Reconnect
// attempts are rate-limited (LINK_RETRY_MS) so a missing AP or a down DASH
// doesn't spin. WiFi.begin() and client.connect() block briefly, which is fine:
// it only happens while we are SILENT (never mid-stream), and DASH's fast-phase
// sweep catches us the moment the socket comes up (the slow-booter case, §6).
// Returns true when the DASH socket is connected and ready.
bool maintainLink() {
  if (client.connected()) { linkUp = true; return true; }

  onLinkLost();
  unsigned long now = millis();
  if (now - lastLinkTry < LINK_RETRY_MS) return false;
  lastLinkTry = now;

  if (WiFi.status() != WL_CONNECTED) {
    DBG.print(F("[wifi] connecting to ")); DBG.println(SECRET_SSID);
    WiFi.begin(SECRET_SSID, SECRET_PASS);
    if (WiFi.status() != WL_CONNECTED) return false;   // try again next window
    DBG.print(F("[wifi] up, IP ")); DBG.println(WiFi.localIP());
    // DASH_HOST is the DASH *device's* IP on this network, NOT the gateway. When DASH and this module
    // both join a shared AP (a phone hotspot, a router), DASH is another client — read its IP off the
    // DASH WiFi status line ("Listening on <ip>:3274"). The gateway below is the AP (phone/router),
    // shown only to help sanity-check you're on the network you expect.
    DBG.print(F("[wifi] gateway (the AP, not DASH) ")); DBG.println(WiFi.gatewayIP());
  }

  DBG.print(F("[link] connecting to DASH ")); DBG.print(DASH_HOST);
  DBG.print(':'); DBG.println(DASH_PORT);
  if (client.connect(DASH_HOST, DASH_PORT)) {
    linkUp = true;
    DBG.println(F("[link] up — SILENT, waiting for DISCOVER"));
    return true;
  }
  return false;   // DASH not reachable yet — retry next window
}


/* ===========================  ARDUINO ENTRY POINTS  ====================== */

void setup() {
  DBG.begin(115200);                   // debug console only — NOT the DASH pipe
  randomSeed(analogRead(A0));          // a floating pin's noise is seed enough
  // Boot SILENT: send NOTHING until spoken to, and nothing at all until the
  // DASH socket is up (§6). maintainLink() in loop() brings the link up.
}

void loop() {
  // No socket, no conversation. maintainLink() keeps WiFi + TCP alive and holds
  // us SILENT across any drop.
  if (!maintainLink()) return;

  // Read whatever DASH has sent, assembling complete lines. WiFiClient reads
  // exactly like Serial (both are Streams).
  while (DASH_LINK.available()) {
    char c = DASH_LINK.read();
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
  // ambient_temp is the SHARED signal — the ESP32 reports its own too, and
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
