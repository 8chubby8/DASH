package com.dash.android.transport

import java.io.ByteArrayOutputStream

/**
 * Reassembles an inbound byte stream into complete protocol lines. This mirrors the firmware's own
 * framing exactly (arduino/arduino.md §1):
 *
 *  - a line ends at '\n',
 *  - a stray '\r' is tolerated and dropped (so CRLF works),
 *  - an over-long line is discarded rather than allowed to grow without bound.
 *
 * Bytes are accumulated and decoded as UTF-8 only once a full line has arrived, so a multi-byte
 * character split across two reads is handled correctly. Not thread-safe: feed it from a single
 * reader thread (the usb-serial IO thread).
 */
class LineAssembler(
    private val maxLineBytes: Int = 1024,
    private val onLine: (String) -> Unit
) {
    private val buffer = ByteArrayOutputStream()
    private var overflowed = false

    fun feed(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            when (val b = data[i].toInt()) {
                NEWLINE -> {
                    if (!overflowed) onLine(buffer.toString(Charsets.UTF_8.name()))
                    buffer.reset()
                    overflowed = false
                }
                CARRIAGE_RETURN -> { /* tolerate CRLF: drop the CR */ }
                else -> {
                    if (buffer.size() < maxLineBytes) buffer.write(b)
                    else overflowed = true   // over-long line: drop the whole thing safely
                }
            }
        }
    }

    private companion object {
        const val NEWLINE = '\n'.code
        const val CARRIAGE_RETURN = '\r'.code
    }
}
