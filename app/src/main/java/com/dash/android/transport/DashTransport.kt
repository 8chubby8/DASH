package com.dash.android.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Connection state of a transport, surfaced to the UI (the Serial Monitor). The absence of a
 * device is a normal state, not an error — a transport reports NO_DEVICE and keeps running,
 * honouring the capability-detection / graceful-degradation principle (CLAUDE.md).
 */
enum class TransportState {
    NO_DEVICE,            // nothing connected — normal, not a fault
    PERMISSION_REQUIRED,  // device present, awaiting the user's USB permission
    CONNECTING,
    CONNECTED,
    ERROR
}

data class TransportStatus(
    val state: TransportState,
    val detail: String = ""
) {
    companion object {
        val NO_DEVICE = TransportStatus(TransportState.NO_DEVICE, "No device")
    }
}

/**
 * The pluggable transport abstraction (roadmap 1.4.1). A transport is a *dumb pipe*: it moves
 * whole lines of UTF-8 text in and out and knows nothing about their meaning. USB serial is the
 * first implementation; WiFi TCP and the rest follow behind this same contract — which is exactly
 * transport.md's core philosophy: "the transport is simply the pipe. The message is what matters."
 *
 * Deliberately minimal. Parsing the pipe grammar (arduino/arduino.md) and running the module
 * lifecycle are the job of the layers above, built in 1.4.2+.
 */
interface DashTransport {
    /** Short human-readable tag identifying this transport, e.g. "usb". Rides on every WireEvent. */
    val tag: String

    /** Complete inbound lines (module → DASH), already framed with the trailing newline removed. */
    val incoming: Flow<String>

    /** Live connection status. */
    val status: StateFlow<TransportStatus>

    /** Begin operating: register listeners and attempt to connect. Idempotent. */
    fun start()

    /**
     * Send one line (DASH → module). Newline framing is added by the transport. Fire-and-forget:
     * live data is stateless and self-healing (arduino.md §6), so a failed send is not fatal.
     */
    fun send(line: String)

    /** Stop operating and release all resources. */
    fun stop()
}
