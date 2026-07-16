/* ===========================================================================
   DASH Module — Powertrain (Bluetooth)         |  module type: SYSTEM
   Board: Espressif ESP32 DevKitC (WROOM-32)    |  transport: Bluetooth Classic SPP
   ---------------------------------------------------------------------------
   A REFERENCE DASH module, written to be read. Nothing here uses access a
   community builder couldn't have (the DASH "SDKable" principle).

   WHAT THIS MODULE IS
     The Bluetooth twin of esp32.ino. Same pretend POWERTRAIN controller —
     speed, revs, gear, ambient temp, a media button — but it reaches DASH over
     Bluetooth Classic (SPP) instead of a USB cable. It exists to prove the one
     thing roadmap 1.4.12 sets out to prove: that NOTHING above the DASH
     transport layer cares which pipe carried a message. Flash this, pair it,
     and it comes up as a module exactly as the USB and WiFi versions do — the
     DASH side runs the identical discovery / install / activate handshake.

     Run this alongside a WiFi module (arduino_wifi.ino) and a USB module and
     you have the three-transport bench: USB, WiFi and Bluetooth live at once,
     all indistinguishable to DASH once the bytes arrive.

   THE ONLY DIFFERENCE FROM esp32.ino
     The transport object. There it was `Serial` (USB-CDC). Here it is a
     `BluetoothSerial` running the Serial Port Profile. Every message function,
     every signal, the whole lifecycle — byte-for-byte the same, because SPP is
     just another byte stream and the DASH line grammar (arduino.md §1-2) does
     not know or care what carried the bytes. That is the point.

   HOW DASH FINDS THIS MODULE — THE NAME MARKER  (arduino.md §11, transport.md)
     Bluetooth Classic has no friendly service-UUID advertisement like BLE, so
     DASH identifies its modules by their Bluetooth NAME: a DASH module's name
     must contain the token `D.A.S.H`. That token is the product name and is
     effectively unique — no phone, headset or dashcam carries it — so DASH can
     dial its own modules and leave everything else you've paired alone. This
     module names itself `D.A.S.H-Powertrain` in SerialBT.begin() below. Append
     whatever you like after the token; the token is the part that matters.

   PAIRING  (transport.md)
     SPP requires the device to be BONDED first. You pair this board once, in
     Android's own Bluetooth settings, the normal way — DASH never pairs
     programmatically. After that DASH connects out to it on every sweep.

   BOARD NOTE — CLASSIC ESP32 ONLY
     BluetoothSerial (Bluetooth Classic / SPP) exists only on the original ESP32
     (WROOM-32 and friends). The newer ESP32-S3 / C3 / C6 are BLE-only and have
     NO Classic radio — they cannot run this sketch. Use a classic ESP32 DevKitC
     for the SPP module. (WiFi modules can be any of them.)

   THE SIGNALS  (declared at install, one SYSTEM_SIGNAL line each — §7),
   exercising all three of DASH's §5a behaviours:
       gear_position   multi-state, store + event   cycles every 12 s
       vehicle_speed   continuous,  store only        every 500 ms
       engine_rpm      continuous,  store only        every 500 ms
       ambient_temp    continuous,  store only        every 1 s
       media_next      momentary,   event only        random, 10-25 s
                       (NO value field — its absence is the signal, §5a)

   STATE REPORTING  (arduino.md §4b)
     A SYSTEM module reports CURRENT STATE, not changes: a full dump of every
     stateful signal on ACTIVATE (alongside its ROGER), and a heartbeat every
     5 s re-sending them changed or not. Change detection is DASH's job.
   ===========================================================================*/

#include <string.h>          // strcmp — plain C string compare, no library needed
#include "esp_random.h"      // ESP32 hardware RNG — this board's seed source
#include "BluetoothSerial.h" // Bluetooth Classic SPP — the ONLY change vs esp32.ino

/* ---------------------------------------------------------------------------
   TRANSPORT SELECTION
   The whole point of the sketch: swap the transport object, change nothing else.
   BluetoothSerial presents the same print()/read()/available() interface as
   Serial, so DASH_SERIAL below drives it identically to the USB version.
   --------------------------------------------------------------------------- */
BluetoothSerial SerialBT;
#define DASH_SERIAL  SerialBT

// The Bluetooth name DASH matches on. MUST contain the token `D.A.S.H`.
#define DASH_BT_NAME "D.A.S.H-Powertrain"

/* ---------------------------------------------------------------------------
   MODULE IDENTITY  (arduino.md §3)
   Its own assigned bench id, unique across the whole bench so every board can
   run at once. Bench map: Body USB ...EE05, Powertrain USB ...EE06, Body WiFi
   ...EE07, Powertrain BT (this board) ...EE08. A real module reads its
   factory-unique MAC instead, so ids never collide by construction — an
   assigned id is a bench convenience that must be kept unique by hand.
   --------------------------------------------------------------------------- */
const char* MODULE_ID = "0000DA58EE08";   // assigned bench id — keep unique

/* ---------------------------------------------------------------------------
   IDENTITY TEXT  (sent in HELLO; field caps from arduino.md §10)
   --------------------------------------------------------------------------- */
const char* MODULE_TYPE = "SYSTEM";                             // exactly one type (§4a)
const char* MODULE_NAME = "Powertrain BT";                      // <= 24 chars
const char* MODULE_DESC = "ESP32 powertrain over Bluetooth SPP"; // <= 64 chars
const char* MODULE_VERS = "v1.0";                               // <= 12 chars

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
   the pretend car's current readings.
   --------------------------------------------------------------------------- */
bool active    = false;       // SILENT until DASH sends ACTIVATE
bool linkUp    = false;       // tracks the RFCOMM link so we can go SILENT on drop

int         speedKmh    = 0;
int         rpm         = 0;
int         gearIndex   = 0;
float       ambientTemp = 21.5;
const char* GEARS[] = { "park", "reverse", "neutral", "drive" };

float speedPhase = 0.0;
float tempPhase  = 2.0;

unsigned long lastStream    = 0;
unsigned long lastTemp      = 0;
unsigned long lastHeartbeat = 0;
unsigned long lastGear      = 0;
unsigned long nextMedia     = 0;

const unsigned long STREAM_MS    = 500;
const unsigned long TEMP_MS      = 1000;
const unsigned long HEARTBEAT_MS = 5000;
const unsigned long GEAR_MS      = 12000;

char    rxLine[64];
uint8_t rxLen = 0;


/* =====================  MESSAGES WE SEND TO DASH  ========================= */

void sendHello() {
  DASH_SERIAL.print(F("HELLO|"));
  DASH_SERIAL.print(MODULE_ID);   DASH_SERIAL.print('|');
  DASH_SERIAL.print(MODULE_TYPE); DASH_SERIAL.print('|');
  DASH_SERIAL.print(MODULE_NAME); DASH_SERIAL.print('|');
  DASH_SERIAL.print(MODULE_DESC); DASH_SERIAL.print('|');
  DASH_SERIAL.println(MODULE_VERS);
}

void doInstall() {
  for (int i = 0; i < SIGNAL_NUM; i++) {
    DASH_SERIAL.print(F("SYSTEM_SIGNAL|"));
    DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
    DASH_SERIAL.println(SIGNALS[i]);
  }
  DASH_SERIAL.print(F("INSTALL_END|"));
  DASH_SERIAL.println(MODULE_ID);
}

void sendRoger(const char* which) {
  DASH_SERIAL.print(F("ROGER|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.println(which);
}

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

void broadcastEvent(const char* function) {
  DASH_SERIAL.print(F("BROADCAST|"));
  DASH_SERIAL.print(MODULE_ID); DASH_SERIAL.print('|');
  DASH_SERIAL.println(function);
}

void reportState() {
  broadcast("gear_position", GEARS[gearIndex]);
  broadcastInt("vehicle_speed", speedKmh);
  broadcastInt("engine_rpm", rpm);
  broadcastFloat("ambient_temp", ambientTemp, 1);
}


/* =====================  MESSAGES WE RECEIVE FROM DASH  ==================== */

void handleLine() {
  if (rxLen == 0) return;

  char* field[6];
  int   n = 0;
  field[n++] = rxLine;
  for (char* p = rxLine; *p && n < 6; p++) {
    if (*p == '|') { *p = '\0'; field[n++] = p + 1; }
  }
  const char* type = field[0];

  if (strcmp(type, "DISCOVER") == 0) { sendHello(); return; }

  if (n < 2 || strcmp(field[1], MODULE_ID) != 0) return;

  if      (strcmp(type, "INSTALL") == 0) { doInstall(); }
  else if (strcmp(type, "ACTIVATE") == 0) {
    active = true;
    sendRoger("activate");
    reportState();
    unsigned long now = millis();
    lastStream = lastTemp = lastHeartbeat = lastGear = now;
    nextMedia = now + random(10000, 25000);
  }
  else if (strcmp(type, "DEACTIVATE") == 0) { active = false; sendRoger("deactivate"); }
}


/* ===========================  ARDUINO ENTRY POINTS  ====================== */

void setup() {
  // begin(<name>) starts the SPP server and sets the discoverable Bluetooth name.
  // The name MUST contain `D.A.S.H` — that is how DASH recognises this as a module.
  SerialBT.begin(DASH_BT_NAME);
  randomSeed(esp_random());
  // Boot SILENT: send NOTHING until spoken to (§6). DASH connects out to us (we are
  // already bonded) and sends DISCOVER; only then do we speak.
}

void loop() {
  // Go SILENT the instant the link drops (§6). A dropped RFCOMM link is normal for a
  // wireless module (out of range / powered down); we reset to SILENT so that when
  // DASH reconnects and re-ACTIVATEs us we resume cleanly rather than streaming from a
  // stale clock. DASH treats a wireless module's absence as dormant, never a fault.
  bool clientNow = SerialBT.hasClient();
  if (linkUp && !clientNow) {
    active = false;
    rxLen  = 0;
  }
  linkUp = clientNow;

  while (DASH_SERIAL.available()) {
    char c = DASH_SERIAL.read();
    if (c == '\n') {
      rxLine[rxLen] = '\0';
      handleLine();
      rxLen = 0;
    } else if (c == '\r') {
      /* skip */
    } else if (rxLen < sizeof(rxLine) - 1) {
      rxLine[rxLen++] = c;
    } else {
      rxLen = 0;
    }
  }

  if (!active) return;
  unsigned long now = millis();

  if (now - lastStream >= STREAM_MS) {
    lastStream = now;
    speedPhase += 0.05;
    speedKmh = (int)(((sin(speedPhase) + 1.0) / 2.0) * 90.0 + 0.5);
    rpm      = 750 + speedKmh * 47;
    broadcastInt("vehicle_speed", speedKmh);
    broadcastInt("engine_rpm", rpm);
  }

  if (now - lastTemp >= TEMP_MS) {
    lastTemp = now;
    tempPhase += 0.02;
    ambientTemp = 22.0 + 2.0 * sin(tempPhase);
    broadcastFloat("ambient_temp", ambientTemp, 1);
  }

  if (now - lastGear >= GEAR_MS) {
    lastGear = now;
    gearIndex = (gearIndex + 1) % 4;
    broadcast("gear_position", GEARS[gearIndex]);
  }

  if (now >= nextMedia) {
    nextMedia = now + random(10000, 25000);
    broadcastEvent("media_next");
  }

  if (now - lastHeartbeat >= HEARTBEAT_MS) {
    lastHeartbeat = now;
    reportState();
  }
}
