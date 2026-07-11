package com.dash.android.transport.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.dash.android.transport.DashTransport
import com.dash.android.transport.FrameAssembler
import com.dash.android.transport.Inbound
import com.dash.android.transport.TransportDevice
import com.dash.android.transport.TransportState
import com.dash.android.transport.TransportStatus
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * USB serial transport (roadmap 1.4.1/1.4.2, made multi-device in 1.4.10), built on the
 * usb-serial-for-android library.
 *
 * Connection parameters are fixed to the known module profile — 115200 8N1 — so there is nothing
 * for the user to configure. It auto-connects to *every* attached serial device (not just the
 * first — 1.4.10), reconnects on hot-plug, and requests USB permission on demand, per device. When
 * no device is attached, or permission is denied, it reports NO_DEVICE / PERMISSION_REQUIRED and
 * keeps running rather than failing hard — the capability-detection / graceful-degradation pattern
 * DASH requires.
 *
 * **Multi-device (1.4.10).** DASH addresses *modules by id*, never by physical cable (transport.md:
 * every message carries the module id; a board doing several jobs presents several modules). So this
 * transport opens each attached device into its own [DeviceConnection] and treats the set as one
 * bus: [send] fans a line out to every connection, [incoming] merges what all of them receive, and
 * the module ignores anything not addressed to it — exactly as modules do on a shared bus. A given
 * module answers with its id and the layers above can't tell (and don't care) which cable carried
 * it. The overall [status] is the liveliest across all devices.
 *
 * **One [FrameAssembler] per device — the hard requirement of 1.4.10.** Each [DeviceConnection]
 * owns its own assembler, so a length-prefixed asset BLOCK arriving from device A switches only
 * A's framing into byte-count mode; device B's innocent live-data bytes are framed by B's own
 * assembler and can never be swallowed into A's block. A single global assembler would be correct
 * only while there is one device.
 *
 * **Driver resolution is per device, and inclusive — no ESP32 board is locked out (1.4.10).** Each
 * device is resolved on its own merits (never all-or-nothing across the bus): first against the
 * library's known VID/PID table (CP210x — the official ESP32-DevKitC — plus CH34x, FTDI, PL2303 and
 * recognised CDC boards), and if that misses, accepted as CDC-ACM *only if it actually exposes a CDC
 * interface*. That second step catches every native-USB ESP32 (S2/S3/C3/C6/H2) on Espressif's own
 * VID that the built-in table doesn't list, while a non-serial peripheral on the same hub (a flash
 * drive, a dashcam, a phone) answers the interface check with "no" and is left alone.
 *
 * It is a dumb pipe: it frames inbound bytes into lines and blocks and emits them; it never parses
 * a message.
 */
class UsbSerialTransport(
    context: Context,
    private val scope: CoroutineScope
) : DashTransport {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    override val tag: String = "usb"

    private val _incoming = MutableSharedFlow<Inbound>(extraBufferCapacity = 256)
    override val incoming: SharedFlow<Inbound> = _incoming.asSharedFlow()

    private val _status = MutableStateFlow(TransportStatus.NO_DEVICE)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<List<TransportDevice>>(emptyList())
    override val devices: StateFlow<List<TransportDevice>> = _devices.asStateFlow()

    // One entry per physically-attached serial device we've opened, keyed by UsbDevice.deviceId
    // (stable for the lifetime of an attachment). Guarded by `this`.
    private val connections = mutableMapOf<Int, DeviceConnection>()

    // Devices we've already asked permission for, so the re-sweep doesn't restack dialogs. An id
    // lingers here after a denial (don't re-prompt) and is cleared when the device is unplugged
    // (a physical replug is a fresh request). Guarded by `this`.
    private val pendingPermission = mutableSetOf<Int>()

    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        // Grant arrived — connectAvailable will now find hasPermission true for it
                        // and open it. Idempotent, so we don't need to fish the device out here.
                        connectAvailable()
                    } else {
                        // Leave the id in pendingPermission so the re-sweep won't re-prompt; a
                        // physical replug clears it (the device disappears) and asks again.
                        @Suppress("DEPRECATION")
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (device != null) synchronized(this@UsbSerialTransport) {
                            pendingPermission.add(device.deviceId)
                        }
                        publishState()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> connectAvailable()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null) closeDevice(device.deviceId, "Device detached")
                }
            }
        }
    }

    override fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // NOT_EXPORTED: the permission broadcast is delivered by the system to our own PendingIntent,
        // and the attach/detach broadcasts are system-originated — matching the existing pattern in
        // MainScreen for ACTION_SCREEN_ON.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Low-rate re-sweep (arduino.md §6). The ACTION_USB_DEVICE_ATTACHED broadcast is unreliable
        // for runtime-registered receivers, so we don't depend on it: we simply re-scan for devices
        // every RESWEEP_MS. connectAvailable is idempotent — it skips devices already connected or
        // already awaiting permission — so it costs almost nothing to run while the bus is settled,
        // and it picks up a slow-booting module or a second board plugged in later whenever it appears.
        scope.launch {
            while (isActive) {
                connectAvailable()
                delay(RESWEEP_MS)
            }
        }
    }

    /** Fan a line out to every open device (DASH → modules). Each module ignores what isn't
     *  addressed to its id, exactly as it would on a shared bus. */
    override fun send(line: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        val targets = synchronized(this) { connections.values.toList() }
        if (targets.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            targets.forEach { conn ->
                runCatching { conn.write(bytes) }
                    .onFailure { closeDevice(conn.deviceId, "Write failed: ${it.message}") }
            }
        }
    }

    /** Send to exactly one device (roadmap 1.4.10 — the Serial Monitor's per-device target). The key
     *  is the device's [UsbDevice.deviceId] as text; an unknown key (device gone) is a silent no-op. */
    override fun send(line: String, deviceKey: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        val conn = synchronized(this) { connections[deviceKey.toIntOrNull()] } ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { conn.write(bytes) }
                .onFailure { closeDevice(conn.deviceId, "Write failed: ${it.message}") }
        }
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { appContext.unregisterReceiver(receiver) }
        val open = synchronized(this) {
            val all = connections.values.toList()
            connections.clear()
            pendingPermission.clear()
            all
        }
        open.forEach { it.close() }
        _devices.value = emptyList()
        _status.value = TransportStatus(TransportState.NO_DEVICE, "Stopped")
    }

    /**
     * Resolve a driver for one device, inclusively (1.4.10). Known bridges first (CP210x/CH34x/
     * FTDI/PL2303 and recognised CDC boards); failing that, accept it as CDC-ACM *only* if it
     * genuinely exposes a CDC interface — which admits every native-USB ESP32 not in the table
     * while leaving non-serial peripherals (mass storage, dashcams, phones) untouched. Returns null
     * for a device that is not a serial device at all.
     */
    private fun resolveDriver(device: UsbDevice): UsbSerialDriver? =
        UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: if (CdcAcmSerialDriver.probe(device)) CdcAcmSerialDriver(device) else null

    /**
     * Scan every attached device and open the ones we can that aren't open yet. Idempotent: a
     * device already connected is skipped, a device already awaiting a permission decision is not
     * re-prompted, and a device with no serial driver is ignored. Also prunes bookkeeping for
     * devices that have vanished, so a replug is treated as a fresh request.
     */
    @Synchronized
    private fun connectAvailable() {
        val devices = usbManager.deviceList.values.toList()
        val presentIds = devices.map { it.deviceId }.toSet()

        // A device gone without a DETACHED broadcast (or between ticks): drop its pending flag and
        // close any lingering connection so the maps reflect only what is physically present.
        pendingPermission.retainAll(presentIds)
        (connections.keys - presentIds).toList().forEach { id ->
            connections.remove(id)?.close()
        }

        for (device in devices) {
            val id = device.deviceId
            if (connections.containsKey(id)) continue          // already open
            val driver = resolveDriver(device) ?: continue      // not a serial device — leave it
            if (usbManager.hasPermission(device)) {
                pendingPermission.remove(id)
                openDriver(driver)                              // adds to `connections` on success
            } else if (pendingPermission.add(id)) {
                // Newly needing permission — ask once, then wait for the broadcast rather than
                // re-prompting every re-sweep tick (which would stack up dialogs).
                requestPermission(device)
            }
        }
        publishState()
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(appContext, device.deviceId, intent, flags)
        usbManager.requestPermission(device, pi)
    }

    /** Open one resolved driver into a live [DeviceConnection]. Called only while holding `this`
     *  (from [connectAvailable]); a failure to open simply leaves the device out and the sweep
     *  retries it next tick — one board that won't open never blocks the others. */
    private fun openDriver(driver: UsbSerialDriver) {
        val device = driver.device
        val connection: UsbDeviceConnection? = usbManager.openDevice(device)
        if (connection == null) return
        val serialPort = driver.ports.firstOrNull()
        if (serialPort == null) {
            runCatching { connection.close() }
            return
        }
        val opened = runCatching {
            serialPort.open(connection)
            serialPort.setParameters(BAUD_RATE, DATA_BITS, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        }
        if (opened.isFailure) {
            runCatching { serialPort.close() }
            return
        }
        // Assert DTR/RTS. Many USB-CDC bridges (the R4 WiFi's ESP32-S3 among them) gate data until
        // the host raises these lines — without them the port opens but the wire stays dead.
        // Both-high is also the ESP32 DevKitC's normal "run" state (its auto-reset circuit only
        // fires on the opposite-state sequence esptool uses). Wrapped separately so a driver that
        // doesn't support the control lines still connects.
        runCatching {
            serialPort.setDTR(true)
            serialPort.setRTS(true)
        }
        val conn = DeviceConnection(device.deviceId, describe(device), serialPort)
        connections[device.deviceId] = conn
        conn.start()
    }

    /** Close and forget one device (on detach, read error, write failure, or vanish). */
    private fun closeDevice(deviceId: Int, reason: String) {
        val conn = synchronized(this) { connections.remove(deviceId) } ?: return
        conn.close()
        publishState()
    }

    /** Publish the per-device picture up to the layers above: the [devices] list (one entry per open
     *  connection, for the Serial Monitor's selector) and the collapsed [status] — CONNECTED if any
     *  device is open, else PERMISSION_REQUIRED if any is awaiting a decision, else NO_DEVICE. */
    private fun publishState() {
        synchronized(this) {
            _devices.value = connections.values.map { TransportDevice(it.deviceId.toString(), it.name, tag) }
            val n = connections.size
            _status.value = when {
                n > 0 -> TransportStatus(
                    TransportState.CONNECTED,
                    if (n == 1) "1 device @ $BAUD_RATE 8N1" else "$n devices @ $BAUD_RATE 8N1"
                )
                pendingPermission.isNotEmpty() -> TransportStatus(TransportState.PERMISSION_REQUIRED, "Awaiting USB permission")
                else -> TransportStatus(TransportState.NO_DEVICE, "No device")
            }
        }
    }

    /** A short human label for a device — its product name if it offers one, else its VID:PID. Used
     *  in logs today; the Devices view (1.4.10 Stage 2) will surface it to the user. */
    private fun describe(device: UsbDevice): String =
        device.productName?.takeIf { it.isNotBlank() }
            ?: "USB %04X:%04X".format(device.vendorId, device.productId)

    /**
     * One physically-attached serial device, opened. Owns its own IO manager thread and — the
     * 1.4.10 requirement — its own [FrameAssembler], so its block framing can never bleed into
     * another device's stream. It is its own [SerialInputOutputManager.Listener] so a read error
     * closes exactly this device and no other.
     */
    private inner class DeviceConnection(
        val deviceId: Int,
        val name: String,
        private val port: UsbSerialPort
    ) : SerialInputOutputManager.Listener {

        // This device's private framing state — the heart of the per-device guarantee.
        private val assembler = FrameAssembler { frame -> _incoming.tryEmit(frame) }
        private val ioManager = SerialInputOutputManager(port, this)

        fun start() = ioManager.start()

        fun write(bytes: ByteArray) {
            port.write(bytes, WRITE_TIMEOUT_MS)
        }

        fun close() {
            runCatching { ioManager.stop() }
            runCatching { port.close() }
        }

        // --- SerialInputOutputManager.Listener (called on this device's own IO thread) ---

        override fun onNewData(data: ByteArray) {
            assembler.feed(data, data.size)
        }

        override fun onRunError(e: Exception) {
            closeDevice(deviceId, "Read error: ${e.message}")
        }
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "com.dash.android.USB_PERMISSION"
        const val BAUD_RATE = 115200
        const val DATA_BITS = 8
        const val WRITE_TIMEOUT_MS = 2000
        const val RESWEEP_MS = 1500L
    }
}
