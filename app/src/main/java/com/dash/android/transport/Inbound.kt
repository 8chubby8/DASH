package com.dash.android.transport

/**
 * One framed unit arriving from a module (module → DASH), as produced by the [FrameAssembler].
 *
 * The wire carries two kinds of framing (arduino/arduino.md §1, §8):
 *
 *  - **[Line]** — the ordinary case: a message is one line of UTF-8 text ended by '\n'. Every message
 *    in the grammar is a line *except* the raw bytes of an asset block.
 *  - **[Block]** — an ACCESSORY asset payload: a `BLOCK|id|name|length|crc` header followed by exactly
 *    `length` raw bytes, which may themselves contain '\n' (that is *why* the block is length-prefixed
 *    rather than newline-terminated). The assembler reads the byte count verbatim and hands the whole
 *    thing up as one unit, header and bytes together.
 *
 * Both variants flow through a **single ordered stream**, so a `MANIFEST` line, the `BLOCK`s that
 * follow it, and the closing `INSTALL_END` line reach the brain in exactly the order the module sent
 * them — which the install handshake (roadmap 1.4.4) relies on for progress accounting.
 *
 * Framing lives in the transport (where the bytes are); *meaning* — CRC validation, record assembly —
 * lives above it in the install desk. The assembler never validates a block; it only carves it out.
 */
sealed interface Inbound {
    data class Line(val text: String) : Inbound

    class Block(val header: String, val bytes: ByteArray) : Inbound {
        // A rendering of the payload for the wire tap / Serial Monitor — never the raw bytes as text
        // (that would spew binary), just a readable note that this many bytes arrived.
        val note: String get() = "«${bytes.size} bytes»"
    }
}

/**
 * A framed [Inbound] unit plus its **origin** — which transport, and which device on that transport,
 * delivered it (roadmap 1.4.14). The origin is stamped where the frame is assembled (inside the
 * per-device/per-socket connection, which already knows its key) and travels up with the frame, so
 * the layers above can do two things they couldn't before:
 *
 *  - **Attribute a module to its pipe** — the wired-vs-wireless distinction the reconciliation desk
 *    uses to word a "not responding" module correctly (a dead cable reads differently from a
 *    wireless module out of range).
 *  - **Fail the right install on a disconnect** — when the device carrying an in-flight install
 *    handshake leaves the bus, the install desk can abort *that* session at once, instead of waiting
 *    out the idle timeout.
 *
 * [deviceKey] pairs with [transportTag] to identify a [TransportDevice]. A transport that never
 * distinguishes devices may leave it null and rely on the tag alone.
 */
data class InboundFrame(
    val frame: Inbound,
    val transportTag: String,
    val deviceKey: String?
)

/**
 * A specific device on a specific transport (roadmap 1.4.14) — the pair that identifies where an
 * install's declarations are arriving from, and, matched against the live device list, which device
 * leaving the bus should fail an in-flight install. Distinct from [TransportDevice]: this is the bare
 * identity used for matching, not the richer object the Serial Monitor renders.
 */
data class DeviceRef(val transportTag: String, val deviceKey: String)
