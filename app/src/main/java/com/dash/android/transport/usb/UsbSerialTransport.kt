package com.dash.android.transport.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.dash.android.transport.DashTransport
import com.dash.android.transport.FrameAssembler
import com.dash.android.transport.Inbound
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
 * USB serial transport (roadmap 1.4.1/1.4.2), built on the usb-serial-for-android library.
 *
 * Connection parameters are fixed to the known module profile — 115200 8N1 — so there is nothing
 * for the user to configure. It auto-connects when a device is present, reconnects on hot-plug,
 * and requests USB permission on demand. When no device is attached, or permission is denied, it
 * reports NO_DEVICE / PERMISSION_REQUIRED and keeps running rather than failing hard — the
 * capability-detection / graceful-degradation pattern DASH requires.
 *
 * It is a dumb pipe: it frames inbound bytes into lines (LineAssembler, mirroring the firmware)
 * and emits them; it never parses a message.
 */
class UsbSerialTransport(
    context: Context,
    private val scope: CoroutineScope
) : DashTransport, SerialInputOutputManager.Listener {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    override val tag: String = "usb"

    private val _incoming = MutableSharedFlow<Inbound>(extraBufferCapacity = 128)
    override val incoming: SharedFlow<Inbound> = _incoming.asSharedFlow()

    private val _status = MutableStateFlow(TransportStatus.NO_DEVICE)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    // The device we've already asked permission for, so the re-sweep doesn't re-prompt every tick.
    private var pendingPermissionDevice: UsbDevice? = null

    // The IO thread feeds bytes in; completed frames (lines and asset blocks) are published to the
    // incoming flow. The assembler decides line-vs-block framing synchronously on that same thread.
    private val assembler = FrameAssembler { frame -> _incoming.tryEmit(frame) }

    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        pendingPermissionDevice = null
                        connectFirstAvailable()
                    } else {
                        // Leave pendingPermissionDevice set so the re-sweep won't re-prompt; a
                        // physical replug clears it (device disappears) and asks again.
                        _status.value = TransportStatus(TransportState.PERMISSION_REQUIRED, "Permission denied")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> connectFirstAvailable()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> closeConnection("Device detached")
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
        // for runtime-registered receivers, so we don't depend on it: while disconnected, we simply
        // re-scan for a device every RESWEEP_MS. This makes hot-plug reliable and also picks up a
        // slow-booting module whenever it appears. It idles (a bare delay) once connected.
        scope.launch {
            while (isActive) {
                if (port == null) connectFirstAvailable()
                delay(RESWEEP_MS)
            }
        }
    }

    override fun send(line: String) {
        val p = port ?: return
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        scope.launch(Dispatchers.IO) {
            runCatching { p.write(bytes, WRITE_TIMEOUT_MS) }
                .onFailure { closeConnection("Write failed: ${it.message}") }
        }
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { appContext.unregisterReceiver(receiver) }
        closeConnection("Stopped")
    }

    private fun findDrivers(): List<UsbSerialDriver> {
        val defaults = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (defaults.isNotEmpty()) return defaults
        // Fallback: a module may be a CDC-ACM device the built-in VID/PID table doesn't list.
        // Treat any attached device as CDC-ACM and let open() be the judge — a genuine non-serial
        // device simply fails to open and we degrade to ERROR, no crash.
        return usbManager.deviceList.values.map { CdcAcmSerialDriver(it) }
    }

    @Synchronized
    private fun connectFirstAvailable() {
        if (port != null) return  // already connected
        val driver = findDrivers().firstOrNull()
        if (driver == null) {
            _status.value = TransportStatus.NO_DEVICE
            pendingPermissionDevice = null   // no device present — a replug is a fresh request
            return
        }
        val device = driver.device
        if (usbManager.hasPermission(device)) {
            pendingPermissionDevice = null
            openDriver(driver)
            return
        }
        // Need permission. Request it once for this device, then wait for the broadcast rather than
        // re-prompting on every re-sweep tick (which would stack up permission dialogs).
        if (pendingPermissionDevice?.deviceId != device.deviceId) {
            pendingPermissionDevice = device
            _status.value = TransportStatus(TransportState.PERMISSION_REQUIRED, "Awaiting USB permission")
            requestPermission(device)
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(appContext, 0, intent, flags)
        usbManager.requestPermission(device, pi)
    }

    private fun openDriver(driver: UsbSerialDriver) {
        _status.value = TransportStatus(TransportState.CONNECTING, "Opening")
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            _status.value = TransportStatus(TransportState.ERROR, "Could not open device")
            return
        }
        val serialPort = driver.ports.firstOrNull()
        if (serialPort == null) {
            _status.value = TransportStatus(TransportState.ERROR, "No serial port on device")
            return
        }
        val opened = runCatching {
            serialPort.open(connection)
            serialPort.setParameters(BAUD_RATE, DATA_BITS, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        }
        if (opened.isFailure) {
            runCatching { serialPort.close() }
            _status.value = TransportStatus(TransportState.ERROR, "Open failed: ${opened.exceptionOrNull()?.message}")
            return
        }
        // Assert DTR/RTS. Many USB-CDC bridges (the R4 WiFi's ESP32-S3 among them) gate data
        // until the host raises these lines — without them the port opens but the wire stays dead.
        // Wrapped separately so a driver that doesn't support the control lines still connects.
        runCatching {
            serialPort.setDTR(true)
            serialPort.setRTS(true)
        }
        port = serialPort
        // Fresh framing state for the new connection — a previous session that dropped mid-block
        // must not bleed its raw-mode state into this one. Safe here: the IO manager isn't running yet.
        assembler.reset()
        ioManager = SerialInputOutputManager(serialPort, this).also { it.start() }
        _status.value = TransportStatus(TransportState.CONNECTED, "CDC @ $BAUD_RATE 8N1")
    }

    @Synchronized
    private fun closeConnection(reason: String) {
        ioManager?.let { runCatching { it.stop() } }
        ioManager = null
        port?.let { runCatching { it.close() } }
        port = null
        if (started) _status.value = TransportStatus(TransportState.NO_DEVICE, reason)
    }

    // --- SerialInputOutputManager.Listener (called on the IO manager's own thread) ---

    override fun onNewData(data: ByteArray) {
        assembler.feed(data, data.size)
    }

    override fun onRunError(e: Exception) {
        closeConnection("Read error: ${e.message}")
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "com.dash.android.USB_PERMISSION"
        const val BAUD_RATE = 115200
        const val DATA_BITS = 8
        const val WRITE_TIMEOUT_MS = 2000
        const val RESWEEP_MS = 1500L
    }
}
