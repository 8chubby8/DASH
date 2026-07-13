package com.dash.android.transport.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.dash.android.transport.DashTransport
import com.dash.android.transport.FrameAssembler
import com.dash.android.transport.Inbound
import com.dash.android.transport.InboundFrame
import com.dash.android.transport.TransportDevice
import com.dash.android.transport.TransportState
import com.dash.android.transport.TransportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic (SPP) transport (roadmap 1.4.12) — the third [DashTransport] implementation, the
 * wireless sibling of WiFi and the strongest proof yet that the 1.4.1 abstraction holds: it slots in
 * beside USB and WiFi with a single line added in [com.dash.android.transport.TransportManager] and
 * nothing above the transport layer touched. A module that arrives over RFCOMM answers with its id and
 * the layers above route by id, exactly as they do for a cable or a socket (transport.md).
 *
 * **The model is USB's, not WiFi's.** On WiFi, DASH is the *server* and modules dial in. Bluetooth
 * Classic has no such inbound listener; like USB, **DASH connects out** — but with no bus to
 * enumerate, the "which devices?" answer is the set of *bonded* (paired) devices. Pairing happens once
 * in Android's own Bluetooth settings (transport.md), never programmatically here. So this transport
 * runs the same idempotent re-sweep USB does ([connectAvailable]): every [RESWEEP_MS] it looks at the
 * bonded set, keeps the DASH modules, and opens an RFCOMM socket to any not already connected.
 *
 * **Identifying a module by name — the [NAME_MARKER] convention.** Classic Bluetooth doesn't advertise
 * a service UUID the friendly way BLE does, and querying one needs a flaky SDP round-trip. So DASH uses
 * the device *name* as the marker (the Classic analogue of the BLE service-UUID filter transport.md
 * describes): a DASH module's Bluetooth name contains `D.A.S.H` (e.g. `D.A.S.H-Powertrain`). That token
 * is the product name and is effectively unique — no phone, headset or dashcam carries it — so it
 * cleanly excludes every non-module bonded device. The handshake is still the final judge: whatever we
 * connect to, only a real module answers `DISCOVER` with `HELLO`, so a mis-named device costs at most
 * one wasted connect attempt (transport.md).
 *
 * **One [FrameAssembler] per device — the 1.4.10 hard requirement, carried from cables and sockets to
 * RFCOMM.** Each [DeviceConnection] owns its own assembler and reader coroutine, so a length-prefixed
 * asset BLOCK from module A can never flip module B's live bytes into byte-count mode. [send] fans a
 * line out to every connection; [incoming] merges what all of them receive; [status]/[devices]
 * aggregate across them. The Serial Monitor's per-device selector and the Signal Monitor read these
 * merged flows with no Bluetooth-specific code.
 *
 * **Graceful degradation (CLAUDE.md).** Every reason Bluetooth might be unavailable — no adapter, the
 * radio switched off, or (API 31+) the runtime `BLUETOOTH_CONNECT` permission denied — resolves to a
 * quiet NO_DEVICE / PERMISSION_REQUIRED status and a transport that keeps sweeping, never a crash or a
 * hard error. The moment the missing piece appears (permission granted, radio switched on) the next
 * sweep picks it up, exactly like USB recovering when a device is plugged in. A dropped RFCOMM link
 * closes only that connection; reconciliation re-greets and re-activates the module when the sweep
 * reopens it — the same self-heal a brown-out reboot gets (arduino.md §6).
 *
 * It is a dumb pipe: it frames inbound bytes into lines and blocks and emits them; it never parses a
 * message.
 */
class BluetoothSppTransport(
    context: Context,
    private val scope: CoroutineScope
) : DashTransport {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override val tag: String = "bt"

    // Bluetooth is wireless: a module falling silent is ordinary (out of range / off), not a fault (1.4.14).
    override val wired: Boolean = false

    private val _incoming = MutableSharedFlow<InboundFrame>(extraBufferCapacity = 256)
    override val incoming: SharedFlow<InboundFrame> = _incoming.asSharedFlow()

    private val _status = MutableStateFlow(TransportStatus.NO_DEVICE)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<List<TransportDevice>>(emptyList())
    override val devices: StateFlow<List<TransportDevice>> = _devices.asStateFlow()

    // One entry per open RFCOMM link, keyed by the device's MAC address (stable and unique). Guarded
    // by `this`.
    private val connections = mutableMapOf<String, DeviceConnection>()

    // Devices we are mid-connect on. socket.connect() blocks for seconds and can fail (out of range),
    // so it runs off the sweep; this set stops each tick from restacking a second attempt on the same
    // device while the first is still in flight. Guarded by `this`.
    private val connecting = mutableSetOf<String>()

    private var started = false

    override fun start() {
        if (started) return
        started = true
        // Idempotent re-sweep, exactly as USB (arduino.md §6): connectAvailable skips devices already
        // open or already connecting, so it costs almost nothing while the set is settled, and it
        // recovers a module that comes back into range — or one authorised/switched on after launch —
        // whenever it next appears.
        scope.launch {
            while (isActive) {
                connectAvailable()
                delay(RESWEEP_MS)
            }
        }
    }

    /** Fan a line out to every open device (DASH → modules). Each module ignores what isn't addressed
     *  to its id, exactly as it would on a shared bus. */
    override fun send(line: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        val targets = synchronized(this) { connections.values.toList() }
        if (targets.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            targets.forEach { conn ->
                runCatching { conn.write(bytes) }
                    .onFailure { closeDevice(conn.address, "write failed: ${it.message}") }
            }
        }
    }

    /** Send to exactly one device (roadmap 1.4.10 — the Serial Monitor's per-device target). The key
     *  is the device's MAC address; an unknown key (device gone) is a silent no-op. */
    override fun send(line: String, deviceKey: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        val conn = synchronized(this) { connections[deviceKey] } ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { conn.write(bytes) }
                .onFailure { closeDevice(conn.address, "write failed: ${it.message}") }
        }
    }

    override fun stop() {
        if (!started) return
        started = false
        val open = synchronized(this) {
            val all = connections.values.toList()
            connections.clear()
            connecting.clear()
            all
        }
        open.forEach { it.close() }
        _devices.value = emptyList()
        _status.value = TransportStatus(TransportState.NO_DEVICE, "Stopped")
    }

    /** True when DASH may touch the Bluetooth adapter. On API 31+ that needs the runtime
     *  BLUETOOTH_CONNECT grant; below 31 the legacy (normal) BLUETOOTH permission is auto-granted, so
     *  access is always permitted. */
    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * One sweep: work out why we can't run (and report it), or enumerate the bonded DASH modules and
     * open the ones not open yet. Idempotent — a device already connected or already connecting is
     * skipped. All the graceful-degradation exits live here, each leaving the transport running so a
     * later sweep recovers once the blocker clears.
     */
    private fun connectAvailable() {
        val adapter = adapter
        if (adapter == null) {                                   // no Bluetooth hardware at all
            _status.value = TransportStatus(TransportState.NO_DEVICE, "Bluetooth unavailable")
            return
        }
        if (!hasConnectPermission()) {                           // API 31+ grant not given (yet)
            _status.value = TransportStatus(TransportState.PERMISSION_REQUIRED, "Awaiting Bluetooth permission")
            return
        }
        if (!adapter.isEnabled) {                                // radio switched off — normal, not a fault
            _status.value = TransportStatus(TransportState.NO_DEVICE, "Bluetooth off")
            return
        }

        // bondedDevices / device.name are permission-gated; we hold the grant, but a revoke between the
        // check and the call would throw SecurityException — treat that as "can't run this sweep".
        val bonded = try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            _status.value = TransportStatus(TransportState.PERMISSION_REQUIRED, "Awaiting Bluetooth permission")
            return
        }

        for (device in bonded) {
            if (!isDashModule(device)) continue                  // not one of ours (name marker)
            val address = device.address
            val skip = synchronized(this) {
                connections.containsKey(address) || !connecting.add(address)
            }
            if (skip) continue                                   // already open, or a connect in flight
            openDevice(device)                                   // launches its own IO coroutine
        }
        publishState()
    }

    /** A bonded device is a DASH module if its name carries the [NAME_MARKER] token (transport.md /
     *  arduino.md §12). Name reads are permission-gated and can be null before the first service query. */
    private fun isDashModule(device: BluetoothDevice): Boolean =
        try {
            device.name?.contains(NAME_MARKER) == true
        } catch (e: SecurityException) {
            false
        }

    /**
     * Open one RFCOMM link to a bonded module, off the sweep thread. connect() blocks for seconds and
     * throws if the module is out of range or refuses — on failure we simply drop the connecting flag
     * so the next sweep retries; one unreachable module never blocks the others. On success the live
     * [DeviceConnection] joins [connections] and starts reading.
     */
    private fun openDevice(device: BluetoothDevice) {
        val address = device.address
        val label = isDashModuleLabel(device)
        scope.launch(Dispatchers.IO) {
            val socket: BluetoothSocket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                synchronized(this@BluetoothSppTransport) { connecting.remove(address) }
                return@launch
            }
            try {
                socket.connect()                                 // blocking; throws if unreachable
            } catch (e: Exception) {
                runCatching { socket.close() }
                synchronized(this@BluetoothSppTransport) { connecting.remove(address) }
                Log.i(TAG, "connect to $label ($address) failed: ${e.message}")
                return@launch
            }
            val conn = try {
                DeviceConnection(address, label, socket)
            } catch (e: Exception) {
                runCatching { socket.close() }
                synchronized(this@BluetoothSppTransport) { connecting.remove(address) }
                return@launch
            }
            synchronized(this@BluetoothSppTransport) {
                connecting.remove(address)
                connections[address] = conn
            }
            conn.start()
            publishState()
            Log.i(TAG, "connected to $label ($address)")
        }
    }

    /** Best-effort human label — the module's Bluetooth name, falling back to its MAC. */
    private fun isDashModuleLabel(device: BluetoothDevice): String =
        try {
            device.name?.takeIf { it.isNotBlank() } ?: device.address
        } catch (e: SecurityException) {
            device.address
        }

    /** Close and forget one device (on disconnect, read error, or write failure). */
    private fun closeDevice(address: String, reason: String) {
        val conn = synchronized(this) { connections.remove(address) } ?: return
        conn.close()
        publishState()
        Log.i(TAG, "device $address closed — $reason")
    }

    /** Publish the per-device picture: the [devices] list (one entry per open link, for the Serial
     *  Monitor's selector) and the collapsed [status] — CONNECTED naming the module(s) if any link is
     *  open, else NO_DEVICE while we are up and paired-but-idle. The unavailable/permission/off states
     *  are set directly in [connectAvailable] and left alone here. */
    private fun publishState() {
        synchronized(this) {
            _devices.value = connections.values.map { TransportDevice(it.address, it.label, tag) }
            val open = connections.values.toList()
            if (open.isNotEmpty()) {
                val detail = when (open.size) {
                    1 -> "1 module on BT — ${open.first().label}"
                    else -> "${open.size} modules on BT"
                }
                _status.value = TransportStatus(TransportState.CONNECTED, detail)
            } else {
                _status.value = TransportStatus(TransportState.NO_DEVICE, "No paired module")
            }
        }
    }

    /**
     * One connected module over RFCOMM, the Bluetooth analogue of the WiFi transport's
     * ClientConnection. Owns its own reader coroutine and — the 1.4.10 requirement — its own
     * [FrameAssembler], so its block framing can never bleed into another module's stream. A read
     * returning -1 (clean close) or throwing (link lost) closes exactly this device and no other.
     */
    private inner class DeviceConnection(
        val address: String,
        val label: String,
        private val socket: BluetoothSocket
    ) : java.io.Closeable {

        // This device's private framing state — the heart of the per-device guarantee. Each frame
        // carries this RFCOMM link's address as it leaves the assembler (1.4.14), so an install can be
        // failed the instant its device drops off.
        private val assembler = FrameAssembler { frame -> _incoming.tryEmit(InboundFrame(frame, tag, address)) }
        private val output: OutputStream = socket.outputStream
        private var readerJob: Job? = null

        fun start() {
            readerJob = scope.launch(Dispatchers.IO) {
                val buf = ByteArray(READ_BUF)
                try {
                    val input = socket.inputStream
                    while (isActive) {
                        val n = input.read(buf)
                        if (n < 0) break                 // link closed cleanly
                        if (n > 0) assembler.feed(buf, n)
                    }
                } catch (e: Exception) {
                    // link lost — fall through to the close below
                } finally {
                    closeDevice(address, "link closed")
                }
            }
        }

        /** Writes are serialised on the stream so two fan-out sends can't interleave bytes. */
        @Synchronized
        fun write(bytes: ByteArray) {
            output.write(bytes)
            output.flush()
        }

        override fun close() {
            readerJob?.cancel()
            runCatching { socket.close() }
        }
    }

    private companion object {
        /** The token a DASH module's Bluetooth name must contain to be recognised (transport.md /
         *  arduino.md). The product name `D.A.S.H` — effectively unique, so it excludes every
         *  non-module bonded device (phones, headsets, dashcams) while a builder is free to append
         *  their own name, e.g. `D.A.S.H-Powertrain`. Changing it is a protocol decision. */
        const val NAME_MARKER = "D.A.S.H"

        /** The well-known Serial Port Profile service UUID — the RFCOMM service every SPP module
         *  exposes and DASH opens a socket to. This is the address we connect *to*, not how we choose
         *  devices (the name marker does that). */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        const val READ_BUF = 1024
        const val RESWEEP_MS = 3000L
        const val TAG = "DashBtSpp"
    }
}
