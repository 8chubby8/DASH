# DASH Panel Preview — Project Brief & Claude Code Entry Point

---

## Welcome

If you are reading this document you are working on **DASH Panel Preview**, a companion
tool that lives inside the DASH repository but is not part of the DASH application.

This document is additive. **The DASH brief at the repository root still applies in full**
— the ethos, the Module Mantra, the SDKable principle, the additive-documentation rule.
Read that first. This document adds only what is specific to the tool.

**The specification this tool implements is `module-layout.md` in the repository root.**
Read it before touching anything here. It is the authoritative panel specification — the
layer types, the targets, the bindings, the slots, the theme tokens, the SVG subset. This
document describes the *tool*; that document describes *what the tool must be right about*.

---

## What This Is

DASH Panel Preview is a **layout previewer and linter** for ACCESSORY module panels.

A module author writes a panel layout — a JSON document describing layers and bindings —
and ships it to DASH inside the install handshake. Before this tool existed, the only way
to find out whether that layout was right was to flash it to a board, install it, and look
at the panel. A wrong number meant reflashing.

This tool removes that loop. Drop in a layout and its assets, and see the panel drawn at
its real slot ratio, with sliders standing in for live module data. Drag `rpm` from 0 to
8000 and watch the needle sweep, with no board, no flashing, and no DASH installation.

It is a single self-contained HTML file. Open it by double-clicking. No server, no build
step, no install, no internet connection.

---

## What This Is Not

**It is not part of the DASH application.** Nothing here ships in the APK. Nothing here
runs in a car.

**It is not a layout designer.** It does not create layouts, and it must never grow drag
handles, a properties inspector, or a canvas you draw on. The author's design tool is
Inkscape, Illustrator, Affinity, Figma — whatever they already use. This tool *reads* what
they made. See the scope guard below, and take it seriously.

**It is not the specification.** It is an implementation of the specification, and a
subordinate one. Where this tool and the Bible disagree, **the Bible is right and the tool
has a bug.**

---

## The Prime Directive — one subset, two consumers

DASH renders a **defined subset** of SVG, not all of it. That subset is the single most
important fact this tool has to know, because its whole value rests on telling an author
the truth about what DASH will do with their artwork.

**The subset is defined once, as data, and read by both this tool and the Kotlin parser in
the DASH app.** A plain machine-readable list of permitted elements and attributes. Both
implementations consume it. Neither hardcodes it.

This is not a nicety. If the subset is maintained by hand in two places, the two will
diverge — not immediately, but within a version or two, silently, and in the direction that
hurts most: the previewer showing something the app cannot draw. **A previewer that lies is
worse than no previewer at all**, because it manufactures confidence where there was
healthy doubt. An author who has no tool checks their work. An author with a tool that
quietly disagrees with reality does not.

If you find yourself adding a capability to this tool by editing a list that the app does
not read, stop. You are building the failure mode.

---

## The Trap — the browser is too good

The browser renders SVG *natively and completely*. Filters, masks, gradients, clip paths,
embedded fonts, SMIL animation — all of it, beautifully, with no effort from us. This is
the reason a browser is the ideal preview environment, and it is also the single biggest
hazard in this tool's design.

**DASH will not render most of that.** Its parser targets Compose's `ImageVector` and
handles a deliberately small subset. So if this tool simply hands the SVG to the browser
and shows the result, it will routinely display panels that DASH cannot reproduce.

Therefore: **rendering is half the job. Linting is the other half, and it is the half that
matters.**

The tool renders with the browser, and simultaneously checks the input against the subset,
and says plainly what DASH will ignore:

- *"`<filter>` on `#glow` — DASH will not render this. The element will draw unfiltered."*
- *"`<rect id=\"\">` has no id — nothing can bind to this element."*
- *"Binding targets `needle`, which appears in two vector layers."*

Warnings are not decoration. They are the product.

---

## Considered & Parked — why DASH does not simply embed a browser

*(Recorded 2026-08-06, during the panel-format design. Roger asked the obvious and correct
question: if a browser can render the whole SVG spec, why can't DASH?)*

The answer is that **full SVG support is not a rendering feature — it is a browser.**
SVG 1.1 contains `<foreignObject>`, whose purpose is to embed arbitrary HTML; it contains
`<script>`; it contains SMIL animation, filters (a complete image-processing pipeline),
masks, patterns, markers, text-on-a-path and embedded fonts. "Support the full spec"
resolves, unavoidably, to "implement an HTML engine, a CSS engine, a font engine, a
JavaScript engine and an image-processing pipeline." A browser can do it because a browser
*is* that.

Android ships one — **WebView** — and DASH could host it. The panel would be an HTML page
and the full spec would come free. This was considered seriously, and it has one genuinely
attractive property: **the previewer and DASH would be the same renderer**, which dissolves
the Prime Directive above entirely. Nothing to keep in sync, no drift, no lying previewer.

**Parked, for three reasons, in ascending order of weight:**

1. **Compositing and plumbing.** Mixing a WebView with Compose-drawn layers is awkward for
   z-order and transparency; touch bindings would route through a JS bridge; theme tokens
   would need injecting into the page.
2. **Performance, on the worst possible surface for it.** The module panel is persistent,
   always on screen, and driven by live vehicle data. WebView is heavy to instantiate and
   hold; multi-module swiping (roadmap 1.6.8) means several instances or a rebuild per
   swipe; and driving a needle means crossing the JS bridge many times a second. On a
   Bronze-tier Android 7 device that is a real risk of a janky gauge.
3. **It destroys module universality — the decisive reason.** WebView updates through the
   Play Store, but a dedicated head unit (the Orange Pi 5 in production) may have no Play
   Services at all, leaving its WebView frozen at whatever the vendor image shipped. So
   "full SVG" would in practice mean *whatever this particular device's browser happens to
   support* — varying by device, Android version and vendor, and **undefined**.

That last point is the one that settles it. **A published subset is a contract**; an author
knows exactly what renders, everywhere, forever. Full SVG via WebView sounds more generous
but is *less* defined — the author cannot know what their module will do in someone else's
car. It is the same reasoning that made the slot ratios fixed rather than flexible:
universality needs a known target, and a smaller known target beats a larger unknown one.

**The subset is also less limiting than it sounds.** What a gauge needs — paths, rects,
circles, ellipses, lines, polygons, groups, transforms, fills, strokes, opacity, gradients
— is a modest list. What is excluded is mostly *effects*, and effects can be **baked into
the artwork at export time**: Inkscape and Illustrator will flatten a filter to paths, and
a drop shadow drawn as shapes is still a drop shadow. The honest framing is not "a crippled
SVG" but **effects get flattened at export; geometry stays live** — and geometry is the part
that must stay live, because that is what the module's data drives.

**What would revive this:** if the parser spike struggles on real exported artwork, or if
flattening effects proves a genuine pain in authoring practice, WebView is a legitimate
fallback rather than a rejected one. Revisit it with this reasoning rather than
re-deriving it.

---

## What It Must Do

The minimum useful version, and close to the maximum wise one:

1. **Load a layout** — paste JSON, or open a local file.
2. **Load assets** — the PNGs and SVGs the layout references, from local files.
3. **Draw the panel at its real slot ratio** — the six shapes, in either orientation.
   The panel is a fixed aspect; the preview must honour it exactly, because that fidelity
   is the entire point.
4. **Fake the live data** — one slider or field per bound variable, so bindings can be
   exercised through their full range.
5. **Switch day/night**, and resolve the nine DASH theme tokens so `@textColourPrimary`
   shows what it will actually be.
6. **Lint against the subset** and report every warning, prominently.
   Including **asset resolution advice**: DASH caps nothing and rejects nothing, so the only
   place an author learns that a 4096 px asset will be downsampled on most devices is here,
   at the moment they can still do something about it. Phrase it as advice, never as an
   error — *"this renders at 2560; the extra pixels cost memory and buy nothing."*
7. **Report normalised coordinates** for a clicked point or a dragged box. Raster authoring
   requires every dynamic element's position to be measured by hand as 0–1 fractions — a
   climate panel is twenty of them — and doing that with a ruler and arithmetic is miserable.
   **This is a measuring tape, not an editor**: it *reports* a coordinate for the author to
   type into their layout. It never writes to the layout. That distinction is the whole of
   the scope guard below, so keep it.

---

## What It Must Never Become

This tool wants to become a layout designer. Resist it.

The moment it has a canvas you can drag things on, it needs undo, selection, snapping,
z-order handles, a properties panel, file management, and a save format — and it becomes a
project the size of DASH itself, competing with tools that are already better at it.

**Not wanted:** drag-and-drop editing, WYSIWYG authoring, an asset library, project files,
cloud anything, accounts, a backend.

If a feature would make the tool *author* a layout rather than *read* one, it is out of
scope. The answer to "it would be nice if I could just nudge that" is Inkscape.

---

## Technical Constraints

- **One self-contained HTML file.** All CSS and JS inline. Assets loaded from the user's
  own disk via a file picker.
- **No build step.** No bundler, no transpiler, no `npm install`. Edit the file, refresh
  the browser.
- **No dependencies, no CDN, no network calls.** It must work with the laptop in
  aeroplane mode, in a garage, with no signal. This mirrors DASH's own offline-first
  discipline.
- **No telemetry, no analytics, nothing phoning home.** Ever.
- **Plain modern JavaScript.** No framework.
- **Published later via GitHub Pages** at roadmap 1.6.10, from this folder, as part of the
  module SDK. Same file, hosted — not a second version.

---

## The Panel Model — read `module-layout.md`

**The specification lives in `module-layout.md` at the repository root, and this document
deliberately does not restate it.** Three layer types, three target kinds, six bindings,
twelve slots, the theme tokens, the SVG subset — all of it is there, in one place, and this
tool's job is to be correct about that document rather than to hold a second copy of it.

If you need the model in order to work here, go and read it. Anything summarised here would
be a second version of the truth waiting to drift, which is the exact failure this tool
exists to prevent — see the Prime Directive above.

> **It is provisional.** `module-layout.md` locks at roadmap 1.6.10, once real modules have
> been drawn through it. Expect it to move under you until then, and treat every change as a
> reason to re-check this tool against it rather than the other way round. **Where the tool
> and the specification disagree, the specification is right and the tool has a bug.**

---

## Current Status

> **Built — 2026-08-16. `index.html` exists and works.** The paragraph below is left as
> written, per the additive rule; the section that follows it records what was built and what
> building it taught. Everything else in this brief still stands unchanged.

**Not started.** This document is the brief; no tool exists yet.

**This is a side quest, and it holds no version hostage.** *(Roger, 2026-08-12.)* It has no
roadmap number, it blocks nothing, and no DASH version waits on it. It gets built when it is
useful to build.

The natural home for publishing it, if and when it exists, is **roadmap 1.6.10** — the module
SDK version, where it would become part of how a community author writes a module, and a
direct discharge of the SDKable principle: the author gets the same tool DASH's own modules
were designed with rather than a lesser one. **An aspiration, not a commitment.** 1.6.10 ships
whether or not this does.

**The layout notation is not yet designed.** JSON is chosen as the format and the vocabulary
is settled, but neither the concrete syntax nor the SVG subset list exists yet — both are
marked open in `module-layout.md` §9.

This tool is expected to *help settle them*, by making it cheap to try a shape, look at it,
and change it before anything is committed to the specification. That is worth stating
plainly because it inverts the usual order: the previewer is not built to a finished spec,
it is built alongside one, and what it teaches goes back into `module-layout.md`.

> **Both halves of that paragraph have since closed.** *(2026-08-16.)* The JSON notation was
> drafted 2026-08-06 and the subset was written as data on 2026-08-12 — `svg-subset.json` at
> the repository root, the item `module-layout.md` had marked *blocking*. The tool was built
> against a settled format rather than helping settle it, which is a better position than the
> one anticipated here. What it still does is feed corrections back; see the findings below.

---

## What Was Built — 2026-08-16

**`panel-preview/index.html`.** One file, roughly 106 kB, no dependencies, no build step, no
network calls. Opened by double-clicking. Artwork and layouts are dropped onto the window —
whole folders work.

**Run it either way:**

- **Off disk.** `firefox panel-preview/index.html`, then drop `svg-subset.json` *and* the
  module's `assets/` folder. The subset must be dropped in because a `file://` page is not
  permitted to read its own siblings.
- **Served.** `python3 -m http.server` from the repository root, then
  `/panel-preview/`. The subset is fetched automatically and only the artwork is dropped.
  This is what GitHub Pages will be like.

**The subset is never embedded in the HTML.** It is fetched or dropped, and the tool
**refuses to draw a vector layer without it** rather than falling back to the browser's own
rendering. A second copy compiled into this file would be exactly the divergence the Prime
Directive exists to prevent, and it would be silent. Refusing is the honest failure.

### What it does

Draws the panel at its real slot ratio, in either orientation, with the six shapes selectable
and day/night switching the active slot — including *no layout, no panel* when a module never
drew the shape asked for. Stands in for the module with one control per variable, built from
the layout's own `variables` board (§8a): declared lists get a stepper, ranged variables get a
slider over the binding's own `from`, and everything has a **Not reported** state, because a
binding whose variable never arrives is a case an author needs to be able to see. Presses do
the optimistic update, including saying when a press at an end stop sends nothing at all.

And it lints: the artwork is pruned against `svg-subset.json` *before* anything is drawn, so
what reaches the screen is only what DASH would also draw, and everything removed is reported
with **the reason and the advice taken from that file** — the same words the app would use.

### Verified against both real modules

All six climate slots; theme tokens resolving; cased styles firing; the 16×1 strip showing its
cut-down four variables. The Tank Gauge for the rest — raster beneath vector, a needle rotating
about a pivot in its own box, a threshold turning a readout red and a lamp pulsing, momentary
controls. `reveal` and `translate` were tested against the **1.6.6** gauge layout pulled out of
git, since 1.6.7 dropped them. On deliberately broken input the linter produced 36 warnings and
the panel showed DASH's behaviour rather than the browser's: `<text>` absent, a dashed stroke
drawn solid, `class="hot"` and an invented colour name drawing black.

### What building it taught

**One real fidelity bug, invisible without hardware to compare against.** The tool eased every
binding in from the artwork's own colours on each build. Compose's `animate*AsState` **starts at
its target on first composition**, so DASH arrives correct and animates only when a value
afterwards changes. The preview was showing a 200 ms fade that never happens on the tablet.
Fixed by suppressing transitions for the first frame. *This is the failure mode the Prime
Directive warns about, arriving through the door it does not guard — see below.*

**The Prime Directive covers the vocabulary, not the behaviour.** The subset is shared data and
cannot drift. The **layout engine** is 3,324 lines of Kotlin across `panel/` and
`ui/modulepanel/`, reproduced here by reading it — layer boxes, the merge rule, pivot
resolution, reveal-as-clip, hit testing on bounding boxes rather than outlines, the step-from
rule. Nothing keeps those in step. The mitigation is that **every behaviour in `index.html`
names the Kotlin file it was copied from**, so the two can be diffed deliberately. That is the
best available answer, not a solved problem, and it is worth re-checking at the 1.6.10 lock.

**One documented approximation.** DASH composes a `translate` against an element's fully
accumulated transform, so it acts in viewBox space however deeply the element is nested. The
previewer leaves the element in the DOM, so a translate on something inside a transformed `<g>`
would act in that group's space instead. No artwork does this today. The caveat is written at
the line that would be wrong.

### Corrections owed to `module-layout.md`

Found by implementing it. None are urgent; all want settling before the spec locks at 1.6.10.

| Where | What |
|---|---|
| §9, *The document* | Still says *"Two lists, and nothing else"* and shows `{ layers, bindings }`. §8a added a third top-level key, `variables`, and every shipped climate layout starts with it. The complete example has no `variables` either. |
| §9, *The SVG subset* | The prose colour list gives hex, `rgb()`, named and `none`; `svg-subset.json` also carries `currentColor` and `url()`. The data is authoritative and both implementations read it, so nothing breaks — but it is the exact divergence the Prime Directive exists to prevent, now living inside one document. |
| §3, *Targets* | *"In every major vector tool, a layer or object name becomes the SVG element's `id` on export."* Not true of Inkscape: renaming in the Objects panel writes `inkscape:label`, which DASH ignores entirely. The `id` is a separate field in Object Properties. This is a genuine trap for the most likely tool an author will use. |

### Still untested

**No artwork has yet come out of a drawing tool.** Both `gauge.svg` and the six climate files
are hand-written — there is not one `inkscape:label` between them. The subset was drafted
against real Inkscape output during the parser spike, but no *module* has been authored that
way, so the first genuine export is new ground for the parser and for this tool. That is
precisely the case the previewer was built for.

---

*Read the DASH brief at the repository root first. This document only adds to it.*
