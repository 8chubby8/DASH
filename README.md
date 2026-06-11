# DASH — Automotive Head Unit OS

> *"Fine. I'll do it myself."*

---

## What is this?

I'm Roger. I'm an HGV driver. I used to be an electrician, which at least means I know which end of a wire to hold. I am, by any reasonable measure, completely useless at coding.

I wanted a custom infotainment system for my Jaguar X-Type. I wanted it to look the way I wanted it to look, work the way I wanted it to work, and connect to the hardware I wanted to connect it to. I looked around for something that did all of that.

It didn't exist.

So I said fine. I'll do it myself.

That's DASH. It started as a personal project for one car and grew into something bigger because the more I thought about it, the more I realised I probably wasn't the only person who'd ever wanted this. Maybe you've got a classic car and you want modern infotainment that actually looks like it belongs. Maybe you've got a daily driver with a terrible factory system. Maybe you just want your head unit to look exactly the way you want it to look and not the way some manufacturer decided it should.

DASH is for you. And me. And anyone else who looked at what was available and thought — no, that's not it.

---

## What DASH is

DASH is a modular, open, hardware-agnostic automotive head unit operating system built on Android. It runs on anything from an old Android tablet to a dedicated single board computer. It connects to physical hardware modules — things like climate control, tyre pressure monitors, steering wheel controls — through a simple open protocol. It gives the user complete control over every aspect of how it looks, behaves, and integrates with their vehicle.

**DASH gets out of the way.** The platform provides the foundation. What you build on top of it is entirely yours.

The module is king within its own domain. DASH draws a box. Your module fills it. DASH has no opinion about what goes inside.

The user is the master. There are no locked features, no forced aesthetics, no decisions made on your behalf. Your car. Your system. Your choice.

---

## What DASH is not

**Not Android Auto.** DASH is a native system, not a phone projection tool.

**Not an OEM replacement.** It doesn't pretend to be a factory system. It's better than that — it's yours.

**Not opinionated.** DASH won't tell you what your interface should look like. That's your job.

**Not finished.** This is a living project. Version 1 is a tablet proof of concept. Version 2 is a proper board installation. Version 3 is where it gets seriously capable. We're at the beginning.

**Not written by a professional developer.** I cannot stress this enough. I'm an HGV driver with an old electrician's instinct for wiring and an idea I was stubborn enough to pursue. I am building this with the help of AI tooling and a determination that borders on the unreasonable. If you're a proper developer and you find something in here that makes you wince, please — kindly — tell me. Better yet, fix it and submit a pull request.

---

## Current Status

**Version:** Pre 1.1.1 — Project setup and documentation phase.

Development has not yet begun in earnest. The architecture is defined, the documentation is written, and the project is being set up. The first development version — 1.1.1 — will implement the density and scaling foundation.

See [roadmap.md](roadmap.md) for the full development plan and version history.
See [changelog.md](changelog.md) for what has actually been built and what broke along the way.

---

## The Hardware

DASH runs on any Android device with USB host support. That includes phones and tablets you might already own. You don't need to spend money on anything special to try it.

For a proper permanent installation there are three hardware tiers.

**Bronze** — Any Android device. Phone, tablet, whatever you have. All vehicle interfaces via USB adapters. Full DASH functionality minus camera stitching.

**Silver** — A capable single board computer with some native automotive interfaces. Enough GPU power for camera integration.

**Gold** — Automotive grade SBC hardware with native CAN, RS485, and UART. The full works.

See [hardware.md](hardware.md) for detailed board comparisons, peripheral requirements, and recommendations.

---

## The Bible

DASH has a set of core documentation files called the Bible. These define what the project is, how it works, and the principles it is built on. If you want to understand DASH properly — or contribute to it — start here.

| Document | What it is |
|----------|-----------|
| [claude.md](claude.md) | The project brief. What DASH is, where it came from, the ethos, the rules. Read this first. |
| [transport.md](transport.md) | The protocol bible. How modules communicate with DASH. The definitive reference for module builders. |
| [interface.md](interface.md) | The interface bible. How DASH looks and behaves. Every visual and interaction decision documented. |
| [hardware.md](hardware.md) | The hardware reference. Board selection, tier standards, peripheral requirements. |
| [roadmap.md](roadmap.md) | The development plan. Where DASH is going and in what order. |
| [changelog.md](changelog.md) | The honest record. What was built, what broke, what was fixed. |

---

## Getting Started

Honest answer — we're not quite there yet. Version 1.1.1 is the first development release and installation instructions will be written as part of that milestone.

What you can do right now is read the Bible, understand what DASH is trying to be, and get in touch if you want to be involved.

---

## Contributing

DASH is open source and contributions are genuinely welcome. A few things worth knowing before you dive in.

Read claude.md first. Understand the ethos. Everything in this project flows from the principle that DASH gets out of the way and the user is master. Contributions that conflict with that principle won't be merged regardless of technical quality.

This is a benevolent dictator project. Roger makes the final call on what goes into the main codebase. That's not a power statement — it's a clarity statement. You know the rules before you invest time in a contribution. Fork freely, experiment freely, propose changes via pull request, and understand that not every proposal will be accepted.

The SDKable principle applies to all code. Every built-in element and overlay must be built as if it were a community SDK component. No special internal access. The playing field between built-in and community components must be level.

DASH stands for Dynamic Automotive System Hub. Use it naturally in code, naming, and documentation alongside descriptive functional naming for everything else.

If you're a proper developer and you see something that could be done better — please say so. Kindly. Remember who built this.

---

## The Origin

I wanted something for my X-Type. It didn't exist. I said fine, I'll do it myself.

Then I thought about my other car. Then I thought about my friends' cars. Then I thought about every person who has ever looked at their car's infotainment system and wished they could have exactly what they wanted instead of what someone else decided they should have.

That's everyone who's ever owned a classic car and wanted modern functionality without ruining the character of the vehicle. That's everyone with a van or a truck who spends their working life in a cab that deserves better than a stock radio. That's everyone who looked at aftermarket Android head units and thought — close, but still not quite mine.

DASH is for all of them. Starting with me. Starting with one X-Type. Growing from there.

Fine. I'll do it myself. And then let everyone else do it themselves too.

---

## License

Copyright © 2026 Roger Davies

DASH is released under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.

The short version — you can use it, modify it, and distribute it. If you build something on it you share your changes back. Nobody gets to take this and lock it down.

---

## Contact

For now the best way to get in touch is through GitHub issues. If you have a question, an idea, a bug report, or just want to say that you too have looked at your car's infotainment system and thought — no, that's not good enough — open an issue. I read them all.

I'm an HGV driver who decided to build an operating system. I genuinely welcome anyone who wants to help make it better than I could make it alone.

---

*Built by someone who had no business building it. Which is exactly why it got built.*
