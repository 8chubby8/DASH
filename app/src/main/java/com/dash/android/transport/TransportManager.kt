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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the set of active transports and exposes the read-only wire tap the Serial Monitor renders.
 *
 * Held for the lifetime of the running app (created in MainScreen), so the connection persists
 * independently of whether the monitor is open — the monitor is only a *view* onto the wire, never
 * its owner. The discovery/handshake brain (1.4.2+) sits above this and drives it through [send] and
 * [wire] while the monitor passively observes.
 *
 * Deliberately transport-agnostic: DASH sends to *all* active transports and merges what *all* of
 * them receive, so no message above this layer ever cares which pipe carried it. Today the list holds
 * one USB serial transport; WiFi TCP joins it in 1.4.10 behind the same [DashTransport] contract with
 * nothing here to change.
 *
 * The `wire` flow replays recent history to new collectors, so opening the monitor immediately
 * shows the last of the traffic rather than a blank screen.
 */
class TransportManager(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Every transport DASH can talk over. Nothing above this line is USB-specific. */
    private val transports: List<DashTransport> = listOf(
        UsbSerialTransport(context, scope)
    )

    /**
     * Aggregate status across every transport. Reports the "liveliest" state present — CONNECTED if
     * any pipe is up, down through to NO_DEVICE if none is — carrying that transport's detail text.
     * With a single transport this is simply that transport's status.
     */
    val status: StateFlow<TransportStatus> =
        combine(transports.map { it.status }) { statuses -> aggregate(statuses) }
            .stateIn(scope, SharingStarted.Eagerly, TransportStatus.NO_DEVICE)

    private val _wire = MutableSharedFlow<WireEvent>(
        replay = WIRE_REPLAY,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val wire: SharedFlow<WireEvent> = _wire.asSharedFlow()

    /**
     * The primary inbound message stream (module → DASH) the controller/brain consumes and routes.
     * Distinct from [wire]: [wire] is a read-only *observation* tap for the monitor (and a future SDK
     * logger) carrying both directions with replay; this is the live one-direction feed for the brain,
     * with no replay so a restarted collector never re-processes stale lines as new modules.
     */
    private val _inbound = MutableSharedFlow<String>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val inbound: SharedFlow<String> = _inbound.asSharedFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        // Merge inbound lines from every transport onto the wire tap (module → DASH), each tagged
        // with the pipe it arrived on.
        transports.forEach { t ->
            scope.launch {
                t.incoming.collect { line ->
                    _wire.tryEmit(WireEvent(System.currentTimeMillis(), WireDirection.IN, t.tag, line))
                    _inbound.tryEmit(line)
                }
            }
        }
        transports.forEach { it.start() }
    }

    /**
     * Send a line out on every *active* transport (DASH → module) and record each on the wire tap.
     * "Active" means CONNECTED — a pipe with no device attached is skipped rather than shown a
     * phantom outbound line. This is the "broadcast to all active transports" the discovery brain
     * (1.4.2+) relies on.
     */
    fun send(line: String) {
        val now = System.currentTimeMillis()
        transports.forEach { t ->
            if (t.status.value.state == TransportState.CONNECTED) {
                _wire.tryEmit(WireEvent(now, WireDirection.OUT, t.tag, line))
                t.send(line)
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        transports.forEach { it.stop() }
        scope.cancel()
    }

    /** Collapse many transport statuses into one, favouring the liveliest state present. */
    private fun aggregate(statuses: Array<TransportStatus>): TransportStatus {
        for (state in STATE_PRIORITY) {
            statuses.firstOrNull { it.state == state }?.let { return it }
        }
        return TransportStatus.NO_DEVICE
    }

    private companion object {
        const val WIRE_REPLAY = 200
        val STATE_PRIORITY = listOf(
            TransportState.CONNECTED,
            TransportState.CONNECTING,
            TransportState.PERMISSION_REQUIRED,
            TransportState.ERROR,
            TransportState.NO_DEVICE
        )
    }
}
