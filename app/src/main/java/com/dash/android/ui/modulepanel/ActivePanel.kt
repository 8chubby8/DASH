package com.dash.android.ui.modulepanel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dash.android.panel.Ambient
import com.dash.android.panel.LayoutSlot
import com.dash.android.panel.PanelDocument
import com.dash.android.panel.PanelLoader
import com.dash.android.panel.SlotOrientation
import com.dash.android.transport.InstalledModule
import com.dash.android.transport.ModuleDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The panel DASH is currently able to draw, or **null when there is none** (roadmap 1.6.6).
 *
 * > **No layout, no panel.** *(`module-layout.md` §6, ruled 2026-08-12.)*
 *
 * DASH holds a record of every layout every installed module provided, so it always knows what it
 * can honestly draw. A module with no layout for the selected size is **simply not shown** — not
 * stretched, not substituted, not apologised for with a placeholder. And with no module able to
 * fill the panel, **no panel is drawn at all**.
 *
 * That last part supersedes the empty-box tenancy this surface was built with at 1.6.2, which held
 * that *"with no module installed there is no king in the castle, so DASH occupying the empty box
 * is the one tenancy it is entitled to."* It is not entitled to it. Reserving a strip of screen to
 * display nothing is the same failure as stretching artwork to fill a shape — DASH taking space it
 * has no content for. The screen reclaims that space until a module can fill it.
 *
 * **Which module, when several could.** The first installed module that can fill the slot, in the
 * database's own order. One panel is drawn at 1.6.6; swiping between several is 1.6.8, and this is
 * the point that grows into the selection it needs.
 */
@Composable
fun rememberActivePanel(
    database: ModuleDatabase,
    slot: LayoutSlot,
): PanelDocument? {
    val context = LocalContext.current
    val modules by database.modules.collectAsState()
    val loader = remember(database) { PanelLoader(context, database) }

    // Which module *could* fill this slot is cheap — the record already says which slots it shipped,
    // so answering it never touches the disk. Only the winner is actually loaded.
    val candidate by remember(modules, slot) {
        derivedStateOf { modules.values.firstOrNull { it.canFill(slot) } }
    }

    var document by remember { mutableStateOf<PanelDocument?>(null) }

    LaunchedEffect(candidate?.id, candidate?.assets, slot) {
        val module = candidate
        document = if (module == null) null else {
            // Reading a layout means reading files and decoding a PNG that may be several megabytes.
            // The panel is the surface that must never hitch, so none of that happens on the main
            // thread — and until it lands there is simply no panel, which is a state DASH now has.
            withContext(Dispatchers.IO) { loader.load(module, slot) }
        }
    }

    return document
}

/**
 * Whether this module has a layout for [slot], including §6's night→day fallback.
 *
 * That fallback is the only one there is, and it is uncontroversial because it is the same artwork
 * rather than a distorted shape. Nothing falls back across a *size*.
 */
fun InstalledModule.canFill(slot: LayoutSlot): Boolean {
    val available = slots.toSet()
    return slot in available ||
        (slot.ambient == Ambient.NIGHT && slot.withAmbient(Ambient.DAY) in available)
}

/**
 * The slot to draw, from where the panel is docked and how big the user asked for it.
 *
 * **Ambient is always [Ambient.DAY] for now.** The switch that would choose otherwise is a version
 * 2 setting, so six of the twelve slots are unreachable across the whole 1.6.x era — night artwork
 * a module ships today is accepted, stored and never selected. That is the honest state of it, and
 * it is why the night path is a fallback in [canFill] rather than a branch here pretending to
 * decide something.
 */
fun slotFor(size: PanelSize, edge: PanelEdge): LayoutSlot =
    LayoutSlot(
        size = size,
        orientation = if (edge.horizontal) SlotOrientation.HORIZONTAL else SlotOrientation.VERTICAL,
        ambient = Ambient.DAY,
    )

// The panel's values moved to PanelPress.kt at 1.6.7. Reading the store was only half the story
// once a control could be pressed: what the panel draws is the module's reported variables *with
// any outstanding prediction laid over the top*, and that belongs with the press that made it.
