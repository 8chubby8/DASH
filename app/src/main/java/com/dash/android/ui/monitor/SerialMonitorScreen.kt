package com.dash.android.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.transport.TransportDevice
import com.dash.android.transport.TransportManager
import com.dash.android.transport.TransportState
import com.dash.android.transport.WireDirection
import com.dash.android.transport.WireEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BG = Color(0xFF0A0A12)
private val LOG_BG = Color(0xFF06060A)
private val INACTIVE = Color(0xFF2A2A2A)
private val LABEL = Color(0xFF666666)
private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.UK)
private const val MAX_ROWS = 500

/**
 * The Serial Monitor — a persistent development instrument reached from the settings panel
 * (mirroring the system-bar edit workspace route). It is a read-only *view* onto the transport's
 * wire tap plus a send box: watch traffic fly past live, and type a line to poke a real module
 * (e.g. `DISCOVER` → watch the `HELLO` come back) before any handshake automation exists.
 *
 * It never owns the connection — the TransportManager does — so closing this screen leaves the
 * link running underneath.
 */
@Composable
fun SerialMonitorScreen(
    transport: TransportManager,
    onDismiss: () -> Unit
) {
    val events = remember { mutableStateListOf<WireEvent>() }
    var paused by remember { mutableStateOf(false) }
    var sendText by remember { mutableStateOf("") }
    val status by transport.status.collectAsState()
    val devices by transport.devices.collectAsState()
    var selectedDevice by remember { mutableStateOf<TransportDevice?>(null) }  // null = all devices
    var deviceMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // If the chosen device is unplugged, fall back to broadcasting to all rather than a dead target.
    LaunchedEffect(devices) {
        if (selectedDevice != null && devices.none { it.key == selectedDevice!!.key && it.transportTag == selectedDevice!!.transportTag }) {
            selectedDevice = null
        }
    }

    // Collect the wire tap. replay in the SharedFlow means recent history appears immediately.
    LaunchedEffect(transport) {
        transport.wire.collect { ev ->
            if (!paused) {
                events.add(ev)
                if (events.size > MAX_ROWS) events.removeRange(0, events.size - MAX_ROWS)
            }
        }
    }
    // Keep the newest line in view unless paused.
    LaunchedEffect(events.size, paused) {
        if (!paused && events.isNotEmpty()) listState.animateScrollToItem(events.size - 1)
    }

    val send: () -> Unit = {
        val line = sendText.trim()
        if (line.isNotEmpty()) {
            val target = selectedDevice
            if (target == null) transport.send(line) else transport.sendTo(target, line)
            sendText = ""
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(
            // imePadding() shrinks this column when the keyboard shows, so the weight(1f) log area
            // resizes and the send box lifts to sit just above the keyboard rather than being hidden.
            modifier = Modifier.fillMaxSize().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SERIAL MONITOR", color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("CLOSE ✕", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }

            // Status + log controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(status.state, status.detail)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (paused) "RESUME" else "PAUSE") { paused = !paused }
                    SmallButton("CLEAR") { events.clear() }
                }
            }

            // Log
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(LOG_BG)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(events) { ev -> WireRow(ev) }
                }
            }

            // Send target — which device the send box talks to. "All devices" broadcasts (the default,
            // and exactly what the controller does); pick one board to talk to it alone (roadmap 1.4.10).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SEND TO", color = LABEL, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                Box {
                    SmallButton("${selectedDevice?.label ?: "ALL DEVICES"}  ▾") { deviceMenuOpen = true }
                    DropdownMenu(expanded = deviceMenuOpen, onDismissRequest = { deviceMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("All devices", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            onClick = { selectedDevice = null; deviceMenuOpen = false }
                        )
                        devices.forEach { d ->
                            DropdownMenuItem(
                                text = { Text("${d.label}  ·  ${d.transportTag}", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                                onClick = { selectedDevice = d; deviceMenuOpen = false }
                            )
                        }
                    }
                }
                if (devices.isEmpty()) {
                    Text("(none connected — broadcasts)", color = LABEL, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // Send box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = sendText,
                    onValueChange = { sendText = it.replace("\n", "") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("type a line, e.g. DISCOVER", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = LABEL) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = INACTIVE,
                        unfocusedContainerColor = INACTIVE,
                        focusedIndicatorColor = Color(0xFF2E7D32),
                        unfocusedIndicatorColor = Color(0xFF444444),
                        cursorColor = Color.White
                    )
                )
                Button(
                    onClick = send,
                    enabled = status.state == TransportState.CONNECTED,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A237E),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF1A1A1A),
                        disabledContentColor = Color(0xFF444444)
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) { Text("SEND", fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun WireRow(ev: WireEvent) {
    val outbound = ev.direction == WireDirection.OUT
    val arrow = if (outbound) "→" else "←"
    val arrowColor = if (outbound) Color(0xFF4FC3F7) else Color(0xFF81C784)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(TIME_FMT.format(Date(ev.timestamp)), color = Color(0xFF555555), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(arrow, color = arrowColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(ev.line, color = typeColor(ev.line), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Colour-code a line by its TYPE word (the first pipe-separated field) for at-a-glance scanning. */
private fun typeColor(line: String): Color = when (line.substringBefore('|').trim()) {
    "HELLO" -> Color(0xFF81C784)
    "DISCOVER", "INSTALL", "ACTIVATE", "DEACTIVATE" -> Color(0xFF4FC3F7)
    "BROADCAST", "REPORT" -> Color(0xFFFFB74D)
    "SYSTEM_SIGNAL", "SUBSCRIBE", "INSTALL_END", "MANIFEST", "BLOCK", "LISTEN", "ACTION" -> Color(0xFFBA9EDB)
    "ROGER" -> Color(0xFF9E9E9E)
    "TRIGGER" -> Color(0xFFE57373)
    else -> Color(0xFFCCCCCC)
}

@Composable
private fun StatusChip(state: TransportState, detail: String) {
    val (dot, label) = when (state) {
        TransportState.CONNECTED -> Color(0xFF2E7D32) to "CONNECTED"
        TransportState.CONNECTING -> Color(0xFFF9A825) to "CONNECTING"
        TransportState.PERMISSION_REQUIRED -> Color(0xFFF9A825) to "PERMISSION"
        TransportState.ERROR -> Color(0xFFC62828) to "ERROR"
        TransportState.NO_DEVICE -> Color(0xFF555555) to "NO DEVICE"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Text(
            text = if (detail.isNotBlank()) "$label  ·  $detail" else label,
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) { Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
}
