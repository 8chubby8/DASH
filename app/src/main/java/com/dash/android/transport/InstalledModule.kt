package com.dash.android.transport

/**
 * A module that has completed the install handshake (roadmap 1.4.4). Where a [DiscoveredModule] is
 * only *found* (it answered `DISCOVER`), an installed module is *set up*: DASH has sent `INSTALL|id`,
 * read the module's type-specific declarations, and committed them here on `INSTALL_END`.
 *
 * The record carries the identity fields **from the original `HELLO`** (a module declares its identity
 * once, at discovery — the handshake adds only the type-specific payload) and exactly one of the three
 * payloads populated, per the module's type (arduino.md §7):
 *
 *  - **SYSTEM** → [signals], the standard signals it will `BROADCAST`.
 *  - **LISTENER** → [subscriptions], the signals it wants delivered.
 *  - **ACCESSORY** → [assets], the icon/panel blocks it shipped.
 *
 * This is **session-only** in 1.4.4 — held in memory, gone on app restart. The on-disk module database
 * is 1.4.5, and this record shape is deliberately what it will serialise. (ACCESSORY variables and
 * interactive controls — the other half of the panel contract — have no locked install-declaration
 * framing yet, arduino.md §10, so they are not part of this record until that framing is agreed.)
 */
data class InstalledModule(
    val id: String,
    val type: String,
    val name: String,
    val description: String,
    val version: String,
    val signals: List<String> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val assets: List<InstalledAsset> = emptyList()
)

/**
 * One `SUBSCRIBE|id|function|rate|threshold|gate|gate_value` line from a LISTENER (arduino.md §9). The
 * four control fields are optional; absent ones are stored blank. DASH captures all of them now but
 * does not yet *act* on rate/threshold/gate — the stream engine that honours them is roadmap 1.4.8.
 */
data class Subscription(
    val function: String,
    val rate: String = "",
    val threshold: String = "",
    val gate: String = "",
    val gateValue: String = ""
) {
    companion object {
        /** Parse a `SUBSCRIBE` line into a [Subscription], or null if it has no function field. */
        fun parse(line: String): Subscription? {
            val p = line.split('|')
            // SUBSCRIBE | id | function [ | rate | threshold | gate | gate_value ]
            if (p.size < 3) return null
            val function = p[2].trim()
            if (function.isEmpty()) return null
            fun field(i: Int) = p.getOrNull(i)?.trim().orEmpty()
            return Subscription(
                function = function,
                rate = field(3),
                threshold = field(4),
                gate = field(5),
                gateValue = field(6)
            )
        }
    }
}

/**
 * One asset received during an ACCESSORY install: a `BLOCK|id|name|length|crc` header and its payload.
 * In 1.4.4 DASH validates the payload and keeps only its metadata — the raw bytes are dropped once the
 * CRC is confirmed (there is nowhere to render or persist them until the panel, 1.6.x, and disk, 1.4.5).
 */
data class InstalledAsset(
    val name: String,
    val bytes: Int,
    val crcOk: Boolean
)

/**
 * The per-module state the Module Management screen renders. Absent from the map ⇒ merely discovered
 * (show the Install button). The three present states drive the pane: a progress bar while installing,
 * a green pane with a Details button once installed.
 */
sealed interface InstallState {
    /** Handshake under way. [progress] is a 0..1 fraction for ACCESSORY (known once MANIFEST lands),
     *  or null for SYSTEM/LISTENER, whose few-line handshake shows an indeterminate bar. */
    data class Installing(val progress: Float?) : InstallState

    /** Handshake complete; [module] is the committed record the Details dialog reads. */
    data class Installed(val module: InstalledModule) : InstallState
}
