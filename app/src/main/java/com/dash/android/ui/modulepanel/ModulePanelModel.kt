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
 * The three panel sizes (roadmap 1.6.4), each a **fixed aspect ratio** rather than a measurement.
 *
 * **The six layout slots, in whole numbers** (Roger, 1.6.4 — whole numbers read better than the
 * fractional forms they were first sketched in, and a module author should never have to picture
 * a shape described in quarters):
 *
 * |        | Horizontal | Vertical |
 * |--------|------------|----------|
 * | Large  | **8 × 3**  | 3 × 8    |
 * | Medium | **16 × 3** | 3 × 16   |
 * | Small  | **16 × 1** | 1 × 16   |
 *
 * Every vertical slot is its horizontal twin stood on its end, so **one pair of numbers serves
 * both** — which is why the ratio is expressed against the *long edge* rather than against width.
 * Held as [longUnits] and [thickUnits] rather than a float so the published ratio and the
 * arithmetic are the same fact and cannot drift apart; [aspect] is derived, never stored.
 *
 * Chosen from what Roger wants in his own car rather than derived for a general case. Small lands
 * at roughly system-bar height on a wide screen, which is where interface.md's original "1× the
 * system bar" impression always pointed. **Provisional** until the slot set locks into
 * `module-sdk.md` at 1.6.10.
 */
@Serializable
enum class PanelSize(val label: String, val longUnits: Int, val thickUnits: Int) {
    SMALL("Small", 16, 1),
    MEDIUM("Medium", 16, 3),
    LARGE("Large", 8, 3);

    /** Long edge ÷ thickness. Derived, so it can never disagree with the published ratio. */
    val aspect: Float get() = longUnits.toFloat() / thickUnits.toFloat()

    /** The slot as a module author writes it: long edge first, thickness second. */
    val horizontalRatio: String get() = "$longUnits × $thickUnits"
    val verticalRatio: String get() = "$thickUnits × $longUnits"
}

/**
 * The module panel's configuration (roadmap 1.6.3).
 *
 * [edge] is the user's **preference**, not necessarily where the panel is drawn — see
 * [effectiveEdge]. Deliberately shaped to grow: [size] arrived at 1.6.4, and persistent / floating
 * mode lands at 1.6.9, which belongs here rather than as a loose key.
 */
@Serializable
data class ModulePanelConfig(
    val edge: PanelEdge = PanelEdge.BOTTOM,
    val size: PanelSize = PanelSize.LARGE,
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
