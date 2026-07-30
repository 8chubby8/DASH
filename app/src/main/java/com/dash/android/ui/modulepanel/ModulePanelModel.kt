package com.dash.android.ui.modulepanel

import com.dash.android.ui.systembar.BarPosition
import kotlinx.serialization.Serializable

/** The screen edge the module panel docks to. All four are available (roadmap 1.6.3). */
@Serializable
enum class PanelEdge(val label: String) {
    TOP("Top"),
    BOTTOM("Bottom"),
    LEFT("Left"),
    RIGHT("Right");

    /** Top and bottom give a horizontal panel; left and right a vertical one. */
    val horizontal: Boolean get() = this == TOP || this == BOTTOM

    val opposite: PanelEdge
        get() = when (this) {
            TOP -> BOTTOM
            BOTTOM -> TOP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}

/**
 * The module panel's configuration (roadmap 1.6.3).
 *
 * [edge] is the user's **preference**, not necessarily where the panel is drawn — see
 * [effectiveEdge]. Deliberately shaped to grow: the panel size arrives at 1.6.4 and persistent /
 * floating mode at 1.6.9, both of which belong here rather than as loose keys.
 */
@Serializable
data class ModulePanelConfig(
    val edge: PanelEdge = PanelEdge.BOTTOM,
) {
    companion object {
        fun default() = ModulePanelConfig()
    }
}

/**
 * Where the panel actually draws, given the user's preference and where the bar currently is.
 *
 * **The panel and the system bar never share an edge and never stack** (Roger, 1.6.2). The bar is
 * senior, so it is the panel that yields — and DASH yields it *for* the user rather than leaving
 * them to discover the collision and fix it themselves.
 *
 * **The preference is never overwritten.** A collision displaces the panel to the opposite edge for
 * as long as it lasts; move the bar away and the panel returns to the edge the user chose. Storing
 * the displacement instead would let DASH quietly rewrite a user's setting and never give it back,
 * which is the opposite of getting out of the way. Only horizontal preferences can ever collide —
 * the bar occupies top or bottom, so a left- or right-docked panel is always safe.
 */
fun effectiveEdge(preferred: PanelEdge, bar: BarPosition): PanelEdge {
    val barEdge = if (bar == BarPosition.TOP) PanelEdge.TOP else PanelEdge.BOTTOM
    return if (preferred == barEdge) preferred.opposite else preferred
}
