package com.dash.android.transport.sim

import com.dash.android.transport.DashTransport
import com.dash.android.transport.Inbound
import com.dash.android.transport.TransportState
import com.dash.android.transport.TransportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A simulated transport (roadmap 1.4.7 test rig) — a loopback [DashTransport] with two virtual
 * modules living behind it. Nothing above this class can tell it from real hardware, which is the
 * point: the virtual modules are discovered, installed, activated, and gatekept through exactly the
 * same path as a physical board, so the 1.4.7 pipeline is verifiable with no firmware in the loop.
 *
 * **Honesty rules.** The transport cheats nowhere: DASH's outbound lines arrive via [send] like any
 * other pipe, the modules answer onto [incoming], and every message shows on the wire tap under the
 * "sim" tag. The virtual modules keep firmware discipline — SILENT until `ACTIVATE`, nothing sent
 * unprompted, no privileged access to DASH internals (SDKable: they use only the wire). If a
 * simulated module could skip the gatekeeper, the test would prove nothing.
 *
 * **Off is unplugged.** Disabled (the default, every boot) the transport reports NO_DEVICE and the
 * modules are powered off — installed records go DORMANT exactly as unplugged hardware does. The
 * State Inspector's toggle is the pretend USB lead.
 *
 * Two modules on one pipe is not how point-to-point USB behaves — it is a preview of a shared bus,
 * which the message grammar handles by design (every message carries its id, arduino.md §5).
 */
class SimulatedModuleTransport(scope: CoroutineScope) : DashTransport {

    override val tag = "sim"

    private val _incoming = MutableSharedFlow<Inbound>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incoming: Flow<Inbound> = _incoming.asSharedFlow()

    private val _status = MutableStateFlow(TransportStatus(TransportState.NO_DEVICE, "Simulator off"))
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    /** Whether the pretend USB lead is plugged in. The State Inspector renders and drives this. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** The pretend car — a virtual SYSTEM module broadcasting standard signals. */
    val vehicle = VirtualSystemModule(scope) { _incoming.tryEmit(it) }

    /** A virtual ACCESSORY — exercises the block-transfer install and sends (unrouted) REPORTs. */
    val accessory = VirtualAccessoryModule(scope) { _incoming.tryEmit(it) }

    private val modules = listOf(vehicle, accessory)

    /** Plug in / unplug the pretend lead. Powering off silences the modules without ceremony —
     *  exactly what yanking a cable does; no DEACTIVATE, no goodbye. */
    fun setEnabled(on: Boolean) {
        if (_enabled.value == on) return
        _enabled.value = on
        if (on) {
            _status.value = TransportStatus(TransportState.CONNECTED, "Simulated modules")
        } else {
            modules.forEach { it.powerOff() }
            _status.value = TransportStatus(TransportState.NO_DEVICE, "Simulator off")
        }
    }

    /** Nothing to do at app start — the simulator always boots unplugged. */
    override fun start() = Unit

    /** DASH → modules. Every virtual module hears every line, like drops on a shared bus; each one
     *  ignores what isn't addressed to it, exactly as the firmware sketches do. */
    override fun send(line: String) {
        if (_status.value.state != TransportState.CONNECTED) return
        modules.forEach { it.onLine(line) }
    }

    override fun stop() = setEnabled(false)
}
