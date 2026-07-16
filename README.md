# DASH — Dynamic Automotive System Hub

> *"Fine. I'll do it myself."*

A modular, open, hardware-agnostic head unit operating system for your car — built on Android, controlled entirely by you.

---

## 1. Who I am, what this is, and why I'm building it

I'm Roger. I'm an HGV driver. I used to be an electrician, which at least means I know which end of a wire to hold. I am not a professional software developer — I'm building this with AI tooling and a stubbornness that borders on the unreasonable.

I wanted a custom infotainment system for my Jaguar X-Type. I wanted it to look the way I wanted, work the way I wanted, and connect to the hardware I wanted. I looked for something that did all of that. It didn't exist. So I said "fine — I'll do it myself."

That's DASH. It started as a personal project for one car and grew, because the more I thought about it the more I realised I wasn't the only person who'd ever looked at their car's infotainment and thought *no, that's not it.* Maybe you've got a classic car and want modern functionality without ruining its character. Maybe you've got a daily driver with a miserable factory system. Maybe you just want your head unit to look exactly the way *you* want it to.

**What it is, at heart:** DASH replaces your Android home screen and becomes the interface you see whenever you're in your vehicle. It reads data from physical hardware modules — climate, tyre pressures, steering-wheel buttons, whatever you build — through a simple open protocol, and it lets you decide how everything looks and behaves.

Three principles run through all of it:

- **DASH gets out of the way.** The platform gives you the foundation. What you build on top is entirely yours — your colours, your layout, your modules, your car.
- **The module is king within its own domain.** DASH draws a box; your module fills it. DASH has no opinion about what goes inside.
- **You are the master.** No locked features, no forced aesthetics, no decisions made on your behalf.

**What it isn't:** It's not Android Auto — it's a native system, not a phone projection. It's not an OEM replacement pretending to be a factory unit — it's better than that, it's yours.

DASH stands for **D**ynamic **A**utomotive **S**ystem **H**ub. The full ethos and the rules the whole project is built on live in [CLAUDE.md](CLAUDE.md).

---

## 2. Where DASH is right now

**Current version: 1.4.16.** Here's the honest truth: DASH is a solid, working *foundation* — but it is **not yet a finished head unit you'd run day to day.** Right now it's for testers, tinkerers, and people who want to build modules. If you install it today, this is what's real and what isn't.

**What works now:**
- DASH takes over as your home screen. The persistent **system bar** — the anchor of the whole interface — is live, with a configurable zone/element layout you can edit right on the device (drag the dividers, size the elements).
- App density and UI scaling, adapting to whatever screen it lands on.
- A **complete module transport layer** — modules connect and stream live data over **USB serial, WiFi, and Bluetooth**, all three at once. Discovery, a full install handshake, automatic reconnection, firmware-mismatch detection, and graceful install-failure handling are all built and hardware-verified.
- Developer instruments — a **Serial Monitor** (watch the raw wire, poke a module by hand) and a **Signal Monitor** (every system signal against its live value).
- A real **module SDK** — an Arduino library so anyone can build a module (see section 4).

**What's not there yet:**
- **Module panels** — modules connect and send data, but there's no on-screen panel *rendering* that data yet. That's the next big one.
- The settings panel, the app launcher tray, the viewport, and functional elements/overlays.

**What's next:**
- **1.5.x — the Settings Panel:** DASH's own configuration, on the device.
- **1.6.x — the Module Panel:** where a connected module finally gets a *face* on screen. This is the version that makes all the transport work visible — if you're here for the modules, this is the one to watch.

The full plan is in [roadmap.md](roadmap.md); the honest blow-by-blow of what was built and what broke along the way is in [changelog.md](changelog.md).

---

## 3. Installing DASH, and what to run it on

**Try it — the nightly build.** Every change to DASH is automatically built into a fresh APK. Grab the latest here:

**→ [Download the latest nightly APK](https://github.com/8chubby8/DASH/releases/download/nightly/dash-nightly.apk)** &nbsp;·&nbsp; ([all releases](https://github.com/8chubby8/DASH/releases))

To install:
1. Download the APK (link above).
2. Tap it to install. The first time, Android will warn that it's from an "unknown source" — tap the prompt to allow your browser (or file app) to install it, then carry on. *(This is a normal one-time permission for sideloading — DASH isn't on the Play Store.)*
3. Make **DASH** your home screen — no digging through Android settings needed. Open the DASH app; it'll show a banner saying it isn't your default launcher yet — **tap it and choose DASH.** (You can also do it any time from DASH's own settings, under the **Launcher** section — the **Change Launcher** button.) Switch back to your normal launcher the same way whenever you like.

It updates in place — download a newer nightly over the top and it simply replaces the old one.

**What you need:**
- **Android 7.0 (API 24) or newer.**
- **USB OTG (host) support** if you want to connect a *wired* module — most tablets have it. WiFi and Bluetooth modules don't need it.
- Nothing special to buy. An old Android tablet you already own is a perfectly good place to start.

**Hardware tiers** (for a permanent install — see [hardware.md](hardware.md) for the detail):
- **Bronze** — any Android device. Phone, tablet, whatever you have. Vehicle interfaces via USB adapters. Full DASH functionality minus camera stitching.
- **Silver** — a capable single-board computer with some native automotive interfaces and enough GPU for camera integration.
- **Gold** — automotive-grade SBC with native CAN, RS485 and UART. The full works. (The production target is an Orange Pi 5 / RK3588 board.)

DASH **never requires root**, on any device, ever. Anything that needs elevated access degrades gracefully where it can't get it.

---

## 4. Modules — what they are, why they exist, and how to build one

**What a module is.** A module is a piece of hardware — usually a cheap microcontroller like an Arduino or ESP32 — that talks to DASH over USB, WiFi, or Bluetooth. It might read your doors and lights, your tyre pressures, your engine data, a steering-wheel button — anything at all. It introduces itself to DASH, says what it can do, and DASH gives it a home.

Every module is **one of three types**, and choosing the type is the first and most important decision you make — it's what tells DASH how to treat your hardware.

**SYSTEM — the car's senses.** A SYSTEM module *produces* DASH's standard signals: the shared vehicle data everything else is built on — speed, revs, gear, doors, lights, cabin temperature. It broadcasts them into DASH's central pool of "what the car is doing right now," which any part of the interface can read.
> *Why it matters:* this is how DASH knows anything at all about your vehicle. And because that pool is *sourceless* — a value is simply "the current gear," not "what module X said" — two modules can even feed the same signal and DASH just takes the latest. A SYSTEM module is the bridge from the car to everything on screen.

**ACCESSORY — its own little app.** An ACCESSORY brings its *own* self-contained panel: its own data, its own controls, its own look — a custom gauge, a media widget, a control page. It ships its own icons and layout, reports its own private readings, and takes button-presses back from the screen.
> *Why it matters:* this is where *the module is king* bites hardest — DASH hands an ACCESSORY a box and renders exactly what it declares, but never dictates what goes inside; the module owns its panel completely. And here's what makes it so powerful: an ACCESSORY is **car-agnostic**. Unlike a SYSTEM or LISTENER module, it needs nothing from any particular vehicle — it stands entirely on its own, so the same accessory drops into *anyone's* DASH, in *any* car. Build it once and it works for everyone. It's the closest thing DASH has to a portable, shareable app. (The on-screen panel arrives with the Module Panel era, 1.6.x — an ACCESSORY can connect and send today, but gets its face then.)

**LISTENER — hardware that reacts.** A LISTENER is the mirror image of a SYSTEM module: instead of producing signals it *subscribes* to them and does something physical in response — an LED that follows your headlights, a relay that clicks when the doors lock, a buzzer on low tyre pressure. It tells DASH which signals it cares about, and DASH delivers them.
> *Why it matters:* it lets real hardware respond to the state of the car, with DASH doing the matchmaking. It produces nothing of its own — it listens, and it acts.

**Why an Arduino, and what a module really is.** DASH talks to a board like an Arduino because its firmware can control and listen to *all manner* of electronics — relays, sensors, switches, lights, motors, anything you can wire to a pin. So at its simplest, a DASH module is just the *endpoint on the screen* for whatever electronics you build around that board — and the three types above are the three shapes that endpoint can take. Every effort has gone into making it as easy as possible to bridge your vehicle's electronics to a touchscreen, so you can control your car exactly the way you want.

**The protocol itself is deliberately simple** — plain pipe-separated text lines (`TYPE|id|…`) you can read straight off a serial monitor. Whichever type you build, the library speaks that grammar for you.

**How to build one:**
1. Grab an Arduino or an ESP32.
2. Install the **DashModule** Arduino library (it's in this repo at [`arduino/DashModule/`](arduino/DashModule/) — copy or symlink it into your Arduino `libraries/` folder).
3. Start from one of the ready-made example sketches in **[`arduino/current_sketches/`](arduino/current_sketches/)**:
   - **`BodyUsb`** — an Arduino sending body signals (doors, lights, cabin) over USB
   - **`BodyWifi`** — the same, over WiFi
   - **`PowertrainUsb`** — an ESP32 sending speed / rpm / gear over USB
   - **`PowertrainBt`** — the same, over Bluetooth
4. Flash it, and it appears in DASH ready to install. The library handles all the protocol plumbing — the framing, the handshake, the lifecycle — so you write only your own sensors and controls.

The complete, locked module contract (what a module *must* do) is in **[module-sdk.md](module-sdk.md)**; the working record with all the *why* behind it is in **[arduino/arduino.md](arduino/arduino.md)**.

---

## 5. How you can help

DASH is open, GPL-3.0, and genuinely welcomes help. I'm one HGV driver with AI tooling and a lot of determination — there's plenty I can't do alone.

**What I could really use:**
- **Proper developers** to look at the code and tell me — kindly — what makes them wince. Better yet, fix it and send a pull request.
- **Testers.** Install the nightly on whatever Android devices you have, try it in a real car, and tell me what breaks. Right now this is the single most useful thing anyone can do.
- **Module builders.** Build something with the SDK and tell me where the library or the docs let you down.
- **Documentation and design help** — writing, diagrams, a better README than this one.

**How to get involved:**
- Open an **[issue](https://github.com/8chubby8/DASH/issues)** — a bug, an idea, a question, or just to say hello. I read them all.
- Fork freely, experiment freely, and propose changes by **pull request**.
- **Read [CLAUDE.md](CLAUDE.md) first.** Everything in DASH flows from one principle: DASH gets out of the way, and the user is master. Contributions that fight that principle won't be merged, however good the code.
- This is a **benevolent-dictator project** — Roger makes the final call on what goes into main. That's not a power statement, it's a clarity one: you know the rules before you invest your time.

If you're a proper developer and you spot something done badly — please say so. Kindly. Remember who built it.

---

## 6. License

Copyright © 2026 Roger Davies. Released under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

The short version: you can use it, modify it, and distribute it. If you build something on it, you share your changes back. Nobody gets to take this and lock it down.

---

*Built by someone who had no business building it. Which is exactly why it got built.*
