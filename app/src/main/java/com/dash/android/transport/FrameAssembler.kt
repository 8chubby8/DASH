package com.dash.android.transport

import java.io.ByteArrayOutputStream

/**
 * Reassembles an inbound byte stream into framed [Inbound] units. It mirrors the firmware's own
 * framing exactly (arduino/arduino.md §1, §8) and understands the wire's **two** framing rules:
 *
 *  - **Newline framing (the default).** A line ends at '\n'; a stray '\r' is tolerated and dropped
 *    (so CRLF works); an over-long line is discarded rather than allowed to grow without bound. Bytes
 *    are decoded as UTF-8 only once a full line has arrived, so a multi-byte character split across
 *    two reads is handled correctly. Every message is a line — except one.
 *
 *  - **Length-prefix framing (asset blocks).** A completed line of the shape `BLOCK|id|name|length|crc`
 *    is *not* a message in its own right: it announces that exactly `length` raw bytes follow, and
 *    those bytes may contain '\n'. On seeing such a header the assembler switches to raw mode, reads
 *    the byte count verbatim, and emits the header and bytes together as one [Inbound.Block].
 *
 * The switch happens **synchronously, inside the byte loop, on the single reader thread** — this is
 * the whole point. If the decision to read raw bytes were made asynchronously anywhere above this
 * class, the reader would already have chewed the payload into mis-framed lines before the decision
 * landed. Because framing is decided here, in step with byte consumption, there is no such race.
 *
 * This class does **framing only** — where does each unit begin and end. It never validates a block's
 * CRC, never assembles a record, never interprets a message. That is the job of the layers above
 * (the install desk, 1.4.4). Not thread-safe: feed it from one reader thread (the usb-serial IO
 * thread); call [reset] only when that thread is idle (i.e. between connections).
 */
class FrameAssembler(
    private val maxLineBytes: Int = 1024,
    // Provisional upper bound on a single asset block. Guards against a corrupt header claiming a
    // vast length. The real asset-size caps are an open item (arduino.md §10); this is a safe default.
    private val maxBlockBytes: Int = 64 * 1024,
    private val onInbound: (Inbound) -> Unit
) {
    private val buffer = ByteArrayOutputStream()
    private var overflowed = false

    // Raw-mode state: while rawRemaining > 0 we are reading a block payload, not a line.
    private val rawBuffer = ByteArrayOutputStream()
    private var rawRemaining = 0
    private var pendingHeader: String? = null

    fun feed(data: ByteArray, length: Int) {
        var i = 0
        while (i < length) {
            if (rawRemaining > 0) {
                // Raw mode: take as many payload bytes as this chunk offers, in one copy. A '\n' in
                // here is payload, counted like any other byte — never a delimiter.
                val take = minOf(rawRemaining, length - i)
                rawBuffer.write(data, i, take)
                i += take
                rawRemaining -= take
                if (rawRemaining == 0) emitBlock()
            } else {
                when (val b = data[i++].toInt()) {
                    NEWLINE -> completeLine()
                    CARRIAGE_RETURN -> { /* tolerate CRLF: drop the CR */ }
                    else -> {
                        if (buffer.size() < maxLineBytes) buffer.write(b)
                        else overflowed = true   // over-long line: drop the whole thing safely
                    }
                }
            }
        }
    }

    /** A line just ended. Either it announces a block (switch to raw mode) or it is an ordinary line. */
    private fun completeLine() {
        val wasOverflow = overflowed
        val line = buffer.toString(Charsets.UTF_8.name())
        buffer.reset()
        overflowed = false
        if (wasOverflow) return   // over-long line already discarded

        val blockLen = blockLengthOrNull(line)
        when {
            blockLen == null -> onInbound(Inbound.Line(line))          // ordinary message
            blockLen > maxBlockBytes -> onInbound(Inbound.Line(line))  // implausible: don't buffer it
            blockLen == 0 -> onInbound(Inbound.Block(line, ByteArray(0)))
            else -> {                                                  // switch to raw mode
                pendingHeader = line
                rawRemaining = blockLen
            }
        }
    }

    private fun emitBlock() {
        val header = pendingHeader ?: return
        onInbound(Inbound.Block(header, rawBuffer.toByteArray()))
        rawBuffer.reset()
        pendingHeader = null
    }

    /**
     * The declared byte length if [line] is a well-formed block header, else null. The header is
     * exactly five fields `BLOCK|id|name|length|crc` (delimiters can't appear inside a field, §2), so
     * anything else is treated as an ordinary line, not a header.
     */
    private fun blockLengthOrNull(line: String): Int? {
        if (!line.startsWith("BLOCK|")) return null
        val parts = line.split('|')
        if (parts.size != 5) return null
        val len = parts[3].trim().toIntOrNull() ?: return null
        return if (len < 0) null else len
    }

    /** Clear all framing state. Call between connections so a mid-block disconnect can't corrupt the
     *  next session's framing. Only safe to call while the reader thread is idle. */
    fun reset() {
        buffer.reset()
        overflowed = false
        rawBuffer.reset()
        rawRemaining = 0
        pendingHeader = null
    }

    private companion object {
        const val NEWLINE = '\n'.code
        const val CARRIAGE_RETURN = '\r'.code
    }
}
