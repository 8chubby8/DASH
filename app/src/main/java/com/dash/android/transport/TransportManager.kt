package com.dash.android.transport

import android.content.Context
import com.dash.android.transport.usb.UsbSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Owns the set of active transports and exposes the read-only wire tap the Serial Monitor renders.
 *
 * Held for the lifetime of the running app (created in MainScreen), so the connection persists
 * independently of whether the monitor is open — the monitor is only a *view* onto the wire, never
 * its owner. In 1.4.1 there is a single USB transport; the discovery/handshake brain (1.4.2+) will
 * consume the same transports through this manager while the monitor passively observes.
 *
 * The `wire` flow replays recent history to new collectors, so opening the monitor immediately
 * shows the last of the traffic rather than a blank screen.
 */
class TransportManager(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val usb = UsbSerialTransport(context, scope)

    /** Aggregate status. With a single transport this is simply the USB transport's status. */
    val status: StateFlow<TransportStatus> = usb.status

    private val _wire = MutableSharedFlow<WireEvent>(
        replay = WIRE_REPLAY,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val wire: SharedFlow<WireEvent> = _wire.asSharedFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        // Mirror every inbound line onto the wire tap (module → DASH).
        scope.launch {
            usb.incoming.collect { line ->
                _wire.tryEmit(WireEvent(System.currentTimeMillis(), WireDirection.IN, usb.tag, line))
            }
        }
        usb.start()
    }

    /** Send a line out (DASH → module) and record it on the wire tap. */
    fun send(line: String) {
        _wire.tryEmit(WireEvent(System.currentTimeMillis(), WireDirection.OUT, usb.tag, line))
        usb.send(line)
    }

    fun stop() {
        if (!started) return
        started = false
        usb.stop()
        scope.cancel()
    }

    private companion object {
        const val WIRE_REPLAY = 200
    }
}
