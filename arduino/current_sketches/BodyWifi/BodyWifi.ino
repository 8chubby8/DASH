/* ===========================================================================
   DASH Module — Body (WiFi)              |  module type: SYSTEM
   Board: Arduino Uno R4 WiFi             |  transport: WiFi TCP (client)
   Built on the DashModule library.
   ---------------------------------------------------------------------------
   The WiFi twin of BodyUsb: SAME module, SAME signals — only the pipe differs.
   Where BodyUsb talks to DASH over USB-serial, this connects over a TCP socket
   to DASH's module server (port 3274). The library talks the protocol over any
   Arduino Stream, and a WiFiClient IS a Stream, so the only thing this sketch
   adds over the USB version is keeping the link up:

     THE INVERSION vs USB — on USB, DASH is the host and opens the device. On
     WiFi, DASH runs a TCP SERVER and this module is the CLIENT that connects in.
     So the firmware's one extra job is maintainLink(): associate to WiFi, connect
     to DASH's server, and — the important bit — go SILENT on any drop (a socket
     death sends no DEACTIVATE), waiting for DASH to re-DISCOVER and re-ACTIVATE.
     dash.linkLost() does that reset for us; on reconnect we are SILENT again (§6).

   FIRST BUILD: copy arduino_secrets.h.example to arduino_secrets.h in this
   folder and fill it in — SECRET_SSID, SECRET_PASS, DASH_HOST (the DASH
   device's IP), DASH_PORT (3274). Your copy is gitignored; the .example is not.
   =========================================================================== */
#include <Dash.h>
#include <WiFiS3.h>            // Uno R4 WiFi radio (onboard ESP32-S3)
#include "arduino_secrets.h"   // SECRET_SSID / SECRET_PASS / DASH_HOST / DASH_PORT

// The DASH pipe is a TCP client. Serial is freed for human-readable bench debug.
WiFiClient client;
#define DBG Serial

// A DISTINCT id from the serial Body (...EE05) so both can coexist on the bench.
DashSystem dash("0000DA58EE07", "Body WiFi", "R4 body over WiFi: doors, lights, cabin", "v1.1");

/* -------- this board's own pretend car ---------------------------------------- */
bool  doorOpen    = false;
bool  headlights  = false;
int   ambientLux  = 0;
float ambientTemp = 19.5;    // SHARED with the ESP32 board
float luxPhase = 0.0, tempPhase = 1.0;

/* -------- the builder's own schedulers ---------------------------------------- */
unsigned long lastStream   = 0;
unsigned long lastLights   = 0;
unsigned long nextDoorFlip = 0;
unsigned long nextButton   = 0;
unsigned long lastLinkTry  = 0;   // rate-limit reconnection attempts
const unsigned long STREAM_MS = 1000, LIGHTS_MS = 15000, LINK_RETRY_MS = 3000;

bool linkUp = false;         // TCP socket to DASH currently connected?

/* -------- state report (§4b): dump + heartbeat, driven by the library --------- */
void report() {
  dash.broadcast("door_driver_open", doorOpen ? "true" : "false");
  dash.broadcast("headlights_on",    headlights ? "true" : "false");
  dash.broadcast("ambient_light",    (long)ambientLux);
  dash.broadcast("ambient_temp",     ambientTemp, 1);
}

/* -------- runs once on the SILENT->ACTIVE transition -------------------------- */
void onActivate() {
  unsigned long now = millis();
  lastStream = lastLights = now;
  nextDoorFlip = now + random(8000, 20000);
  nextButton   = now + random(12000, 30000);
}

/* -------- keep WiFi + the DASH socket up; SILENT across any drop --------------
   Returns true when the DASH socket is connected. On the up->down transition it
   tells the library to forget it was active (dash.linkLost()), so a reconnect
   waits for DASH's fresh ACTIVATE. Reconnects are rate-limited so a missing AP or
   a down DASH doesn't spin; the blocking calls only happen while SILENT. */
bool maintainLink() {
  if (client.connected()) { linkUp = true; return true; }

  if (linkUp) { DBG.println(F("[link] lost - going SILENT")); dash.linkLost(); }
  linkUp = false;

  unsigned long now = millis();
  if (now - lastLinkTry < LINK_RETRY_MS) return false;
  lastLinkTry = now;

  if (WiFi.status() != WL_CONNECTED) {
    DBG.print(F("[wifi] connecting to ")); DBG.println(SECRET_SSID);
    WiFi.begin(SECRET_SSID, SECRET_PASS);
    if (WiFi.status() != WL_CONNECTED) return false;   // try again next window
    DBG.print(F("[wifi] up, IP ")); DBG.println(WiFi.localIP());
  }

  DBG.print(F("[link] connecting to DASH ")); DBG.print(DASH_HOST);
  DBG.print(':'); DBG.println(DASH_PORT);
  if (client.connect(DASH_HOST, DASH_PORT)) {
    linkUp = true;
    DBG.println(F("[link] up - SILENT, waiting for DISCOVER"));
    return true;
  }
  return false;   // DASH not reachable yet — retry next window
}

void setup() {
  DBG.begin(115200);           // debug console only — NOT the DASH pipe
  dash.begin(client);          // the library reads/writes the socket

  dash.addSignal("door_driver_open");
  dash.addSignal("headlights_on");
  dash.addSignal("ambient_light");
  dash.addSignal("ambient_temp");
  dash.addSignal("button_home_pressed");

  dash.onReport(report);
  dash.onActivate(onActivate);

  randomSeed(analogRead(A0));
}

void loop() {
  if (!maintainLink()) return;   // no socket, no conversation

  dash.loop();                   // pump the wire + heartbeat/dump
  if (!dash.isActive()) return;

  unsigned long now = millis();

  if (now - lastStream >= STREAM_MS) {
    lastStream = now;
    luxPhase  += 0.06;
    tempPhase += 0.02;
    ambientLux  = (int)(((sin(luxPhase) + 1.0) / 2.0) * 1000.0 + 0.5);
    ambientTemp = 20.0 + 2.0 * sin(tempPhase);
    dash.broadcast("ambient_light", (long)ambientLux);
    dash.broadcast("ambient_temp", ambientTemp, 1);
  }

  if (now >= nextDoorFlip) {
    nextDoorFlip = now + random(8000, 20000);
    doorOpen = !doorOpen;
    dash.broadcast("door_driver_open", doorOpen ? "true" : "false");
  }

  if (now - lastLights >= LIGHTS_MS) {
    lastLights = now;
    headlights = !headlights;
    dash.broadcast("headlights_on", headlights ? "true" : "false");
  }

  if (now >= nextButton) {
    nextButton = now + random(12000, 30000);
    dash.event("button_home_pressed");
  }
}
