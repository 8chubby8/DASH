package com.dash.android.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.transport.TransportDevice
import com.dash.android.transport.TransportState
import com.dash.android.transport.WireDirection
import com.dash.android.transport.WireEvent
import com.dash.android.ui.settings.content.LinkButton
import com.dash.android.ui.theme.LocalDashTheme
import com.dash.android.ui.transports.LocalTransportDesk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.UK)
private const val MAX_ROWS = 500
private const val ALL = "ALL"
private const val NONE = "—"

// The wire colours. These carry meaning (a line's message type, a line's direction), so they stay
// literal rather than theme tokens — Roger's call, 2026-07-27: a message's colour distinguishes a kind
// of traffic and should not shift when a user picks a preset.
//
// The bright set, because the settings surface went dark at 1.5.12 (backgroundColourSecondary
// 848482 → 2C2C2E). On the old mid-grey these ran 1.06–1.28:1 and were unreadable whatever hue was
// tried; on the dark surface they run 4.7–8.1:1.
private val C_GREEN = Color(0xFF81C784)
private val C_BLUE = Color(0xFF4FC3F7)
private val C_AMBER = Color(0xFFFFB74D)
private val C_PURPLE = Color(0xFFBA9EDB)
private val C_RED = Color(0xFFE57373)

// Column widths. The message column takes whatever is left; everything before it is fixed so the
// columns line up down the page — a grid that doesn't align is just rows of text.
private val W_TIME = 92.dp
private val W_DIR = 56.dp
private val W_TRANSPORT = 96.dp
private val W_BOARD = 132.dp
private val W_MESSAGE = 116.dp
private val W_MODULE = 124.dp
private val W_PAYLOAD = 420.dp
private val GRID_WIDTH = W_TIME + W_DIR + W_TRANSPORT + W_BOARD + W_MESSAGE + W_MODULE + W_PAYLOAD + 70.dp

/**
 * One wire line, split into the fields the grid shows and the filters work on.
 *
 * The DASH grammar is `TYPE|id|…` (arduino.md §2), so the message is the first field and — for every
 * message that names one — the module id is the second. `DISCOVER` is the deliberate exception: it is
 * the one broadcast addressed to everybody, so it carries no id and shows [NONE].
 */
private data class WireRowData(
    val timestamp: Long,
    val outbound: Boolean,
    val transport: String,
    val board: String,
    val message: String,
    val moduleId: String,
    val payload: String,
    val raw: String,
)

private fun parseRow(ev: WireEvent, devices: List<TransportDevice>): WireRowData {
    val fields = ev.line.split('|')
    val message = fields.getOrNull(0)?.trim().orEmpty().ifBlank { NONE }
    val moduleId = fields.getOrNull(1)?.trim().orEmpty().ifBlank { NONE }
    // Everything past the id is the payload — kept whole so nothing on the wire is hidden by the split.
    val payload = fields.drop(2).joinToString("|").ifBlank { NONE }
    return WireRowData(
        timestamp = ev.timestamp,
        outbound = ev.direction == WireDirection.OUT,
        transport = ev.transportTag.uppercase(),
        board = boardLabel(ev, devices),
        message = message,
        moduleId = moduleId,
        payload = payload,
        raw = ev.line,
    )
}

/**
 * The board a line came from or went to — its friendly name when the device is in the live list, else
 * its raw key.
 *
 * A line with no device key has no board: it is a broadcast that went to the *pipe*, so every board on
 * that pipe heard it. It shows [NONE] rather than borrowing the transport tag — the tag is its own
 * column now, and letting it stand in for a board made the board filter list a mix of two different
 * kinds of thing (Roger, 2026-07-27).
 */
private fun boardLabel(ev: WireEvent, devices: List<TransportDevice>): String {
    val key = ev.deviceKey ?: return NONE
    return devices.firstOrNull { it.key == key && it.transportTag == ev.transportTag }?.label ?: key
}

/**
 * Modules › Serial Monitor (roadmap 1.5.12) — the 1.4.1 instrument rehomed into the settings shell and
 * rebuilt as a **filterable grid**.
 *
 * A read-only *view* onto the transport's wire tap plus a send box: watch traffic fly past live, and
 * type a line to poke a real board (`DISCOVER` → watch the `HELLO` come back). It never owns the
 * connection — since 1.5.11 that belongs to `DashApplication` — so leaving this tab leaves every link
 * running underneath, exactly as closing the old full-screen route did.
 *
 * **Why a grid.** Every line on the wire is already a structured record, not a sentence: `TYPE|id|…`.
 * Rendering it as one string made the eye do the parsing, and made the interesting question — "show me
 * only what this board said" or "only the BROADCASTs" — impossible on a bus with three pipes and a
 * board chattering ten times a second. Splitting the fields into columns is what gives the filters
 * something real to bite on.
 *
 * **The filters live in the header**, one per column that has a meaningful set of values: direction,
 * transport, board, message and module id. Time has none, deliberately. Each option list is built from what has
 * actually been *seen* — DASH renders exactly what exists rather than a hardcoded vocabulary, so a
 * message invented by a community module appears in the message filter the first time it is sent.
 * They combine, and they filter the *view* only: nothing is dropped from the buffer, so clearing a
 * filter brings the traffic straight back.
 *
 * There are no per-pipe status lights here — Transport Manager owns "what is connected and is it
 * healthy", and saying it in two tabs only invites the two to disagree.
 */
@Composable
fun SerialMonitorContent() {
    val theme = LocalDashTheme.current
    val desk = LocalTransportDesk.current
    if (desk == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Transport desk unavailable.",
                color = theme.textColourSecondary.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontFamily = theme.font,
            )
        }
        return
    }

    val events = remember { mutableStateListOf<WireEvent>() }
    var paused by remember { mutableStateOf(false) }
    var sendText by remember { mutableStateOf("") }
    // Kept only to gate SEND — typing into a bus with nothing connected should not look armed.
    val status by desk.status.collectAsState()
    val devices by desk.devices.collectAsState()
    var selectedDevice by remember { mutableStateOf<TransportDevice?>(null) }   // null = all boards
    var deviceMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // The four filters. ALL is the unset value in every one of them.
    var fDirection by remember { mutableStateOf(ALL) }
    var fTransport by remember { mutableStateOf(ALL) }
    var fBoard by remember { mutableStateOf(ALL) }
    var fMessage by remember { mutableStateOf(ALL) }
    var fModule by remember { mutableStateOf(ALL) }

    // If the chosen board goes away, fall back to broadcasting rather than aiming at a dead target.
    LaunchedEffect(devices) {
        val chosen = selectedDevice
        if (chosen != null && devices.none { it.key == chosen.key && it.transportTag == chosen.transportTag }) {
            selectedDevice = null
        }
    }

    // Collect the wire tap. The flow's replay means recent history appears at once rather than a blank
    // screen — the tab is a window onto traffic that has been flowing all along.
    LaunchedEffect(desk) {
        desk.wire.collect { ev ->
            if (!paused) {
                events.add(ev)
                if (events.size > MAX_ROWS) events.removeRange(0, events.size - MAX_ROWS)
            }
        }
    }

    val rows = events.map { parseRow(it, devices) }
    val shown = rows.filter {
        (fDirection == ALL || (if (it.outbound) "OUT" else "IN") == fDirection) &&
            (fTransport == ALL || it.transport == fTransport) &&
            (fBoard == ALL || it.board == fBoard) &&
            (fMessage == ALL || it.message == fMessage) &&
            (fModule == ALL || it.moduleId == fModule)
    }

    LaunchedEffect(shown.size, paused) {
        if (!paused && shown.isNotEmpty()) listState.animateScrollToItem(shown.size - 1)
    }

    val send: () -> Unit = {
        val line = sendText.trim()
        if (line.isNotEmpty()) {
            val target = selectedDevice
            if (target == null) desk.send(line) else desk.sendTo(target, line)
            sendText = ""
        }
    }

    val ink = theme.textColourSecondary
    val inkFaint = ink.copy(alpha = 0.55f)
    val filtered = fDirection != ALL || fTransport != ALL || fBoard != ALL || fMessage != ALL || fModule != ALL

    Column(
        // imePadding() shrinks this column when the keyboard shows, so the weight(1f) log resizes and
        // the send box lifts to sit above the keyboard rather than hiding behind it.
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Pinned controls. The row count is the honest answer to "why am I not seeing everything" when
        // a filter is on — without it a filtered grid looks like a quiet bus.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (filtered) "${shown.size} of ${rows.size} lines" else "${rows.size} lines",
                color = inkFaint,
                fontSize = 12.sp,
                fontFamily = theme.font,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (filtered) LinkButton("CLEAR FILTERS") {
                    fDirection = ALL; fTransport = ALL; fBoard = ALL; fMessage = ALL; fModule = ALL
                }
                LinkButton(if (paused) "RESUME" else "PAUSE") { paused = !paused }
                LinkButton("CLEAR") { events.clear() }
            }
        }

        // The grid. It scrolls sideways as one piece so the header and the rows can never drift out of
        // alignment, and the body scrolls vertically inside `fillsBox` with the header pinned above it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.width(GRID_WIDTH).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderLabel("TIME", W_TIME, inkFaint, theme.font)
                FilterHeader("DIR", W_DIR, fDirection, listOf("IN", "OUT"), ink, inkFaint, theme.font) { fDirection = it }
                FilterHeader("TRANSPORT", W_TRANSPORT, fTransport, rows.map { it.transport }.distinct().sorted(), ink, inkFaint, theme.font) { fTransport = it }
                FilterHeader("BOARD", W_BOARD, fBoard, rows.map { it.board }.distinct().sorted(), ink, inkFaint, theme.font) { fBoard = it }
                FilterHeader("MESSAGE", W_MESSAGE, fMessage, rows.map { it.message }.distinct().sorted(), ink, inkFaint, theme.font) { fMessage = it }
                FilterHeader("MODULE ID", W_MODULE, fModule, rows.map { it.moduleId }.distinct().sorted(), ink, inkFaint, theme.font) { fModule = it }
                HeaderLabel("PAYLOAD", W_PAYLOAD, inkFaint, theme.font)
            }

            Box(Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    Text(
                        if (rows.isEmpty()) "Nothing on the wire yet." else "Nothing matches these filters.",
                        color = inkFaint,
                        fontSize = 13.sp,
                        fontFamily = theme.font,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(shown) { row -> GridRow(row, ink, inkFaint, theme.font) }
                    }
                }
            }
        }

        // Send target — "All boards" broadcasts, exactly as the controller does; pick one to talk to
        // it alone (roadmap 1.4.10). Boards, not modules: this addresses a thing on a pipe.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SEND TO", color = inkFaint, fontSize = 10.sp, fontFamily = theme.font, letterSpacing = 2.sp)
            Box {
                LinkButton("${selectedDevice?.label ?: "ALL BOARDS"}  ▾") { deviceMenuOpen = true }
                DropdownMenu(expanded = deviceMenuOpen, onDismissRequest = { deviceMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("All boards", fontFamily = theme.font, fontSize = 12.sp) },
                        onClick = { selectedDevice = null; deviceMenuOpen = false }
                    )
                    devices.forEach { d ->
                        DropdownMenuItem(
                            text = { Text("${d.label}  ·  ${d.transportTag}", fontFamily = theme.font, fontSize = 12.sp) },
                            onClick = { selectedDevice = d; deviceMenuOpen = false }
                        )
                    }
                }
            }
            if (devices.isEmpty()) {
                Text("(none connected — broadcasts)", color = inkFaint, fontSize = 10.sp, fontFamily = theme.font)
            }
        }

        // Send box, in the modern DASH idiom rather than a Material TextField — a token-bordered well
        // and an outline button, matching every other control in the panel.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ink.copy(alpha = 0.08f))
                    .border(1.dp, ink.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                if (sendText.isEmpty()) {
                    Text(
                        "type a line, e.g. DISCOVER",
                        color = ink.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        fontFamily = theme.font,
                    )
                }
                BasicTextField(
                    value = sendText,
                    onValueChange = { sendText = it.replace("\n", "") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = theme.font, fontSize = 13.sp, color = ink),
                    cursorBrush = SolidColor(ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SendButton(enabled = status.state == TransportState.CONNECTED, ink = ink, font = theme.font, onClick = send)
        }
    }
}

/** A column head with nothing to filter on — TIME and MESSAGE. */
@Composable
private fun HeaderLabel(text: String, width: Dp, colour: Color, font: FontFamily) {
    Text(text, color = colour, fontSize = 13.sp, letterSpacing = 1.2.sp, fontFamily = font, modifier = Modifier.width(width))
}

/**
 * A column head that *is* its filter. Unset it shows the column name quietly; set it shows the chosen
 * value in full ink with a border, so a filtered column is obvious at a glance rather than something
 * you discover by wondering where the traffic went.
 */
@Composable
private fun FilterHeader(
    name: String,
    width: Dp,
    selected: String,
    options: List<String>,
    ink: Color,
    inkFaint: Color,
    font: FontFamily,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val on = selected != ALL
    Box(Modifier.width(width)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .then(if (on) Modifier.border(1.dp, ink.copy(alpha = 0.45f), RoundedCornerShape(5.dp)) else Modifier)
                .clickable { open = true }
                .padding(horizontal = if (on) 5.dp else 0.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (on) selected else name,
                color = if (on) ink else inkFaint,
                fontSize = 13.sp,
                letterSpacing = if (on) 0.sp else 1.2.sp,
                fontFamily = font,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(" ▾", color = if (on) ink else inkFaint, fontSize = 11.sp, fontFamily = font)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // ALL first, then whatever has actually been seen on the wire.
            (listOf(ALL) + options).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontFamily = font, fontSize = 12.sp) },
                    onClick = { onSelect(option); open = false }
                )
            }
        }
    }
}

@Composable
private fun GridRow(row: WireRowData, ink: Color, inkFaint: Color, font: FontFamily) {
    // Arrows are from a bystander's view of the wire: inbound (board → DASH) points right, outbound
    // (DASH → board) points left.
    val arrow = if (row.outbound) "←" else "→"
    val arrowColour = if (row.outbound) C_BLUE else C_GREEN
    Row(
        modifier = Modifier.width(GRID_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Cell(TIME_FMT.format(Date(row.timestamp)), inkFaint, W_TIME, font)
        Cell(arrow, arrowColour, W_DIR, font)
        Cell(row.transport, inkFaint, W_TRANSPORT, font)
        Cell(row.board, if (row.board == NONE) ink.copy(alpha = 0.3f) else inkFaint, W_BOARD, font)
        Cell(row.message, messageColour(row.message, ink), W_MESSAGE, font)
        Cell(row.moduleId, if (row.moduleId == NONE) ink.copy(alpha = 0.3f) else inkFaint, W_MODULE, font)
        Cell(row.payload, if (row.payload == NONE) ink.copy(alpha = 0.3f) else ink, W_PAYLOAD, font)
    }
}

@Composable
private fun Cell(text: String, colour: Color, width: Dp, font: FontFamily) {
    Text(
        text,
        color = colour,
        fontSize = 12.sp,
        fontFamily = font,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun SendButton(enabled: Boolean, ink: Color, font: FontFamily, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val colour = if (enabled) ink else ink.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, colour.copy(alpha = 0.5f), shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("SEND", color = colour, fontSize = 12.sp, fontFamily = font, letterSpacing = 1.sp)
    }
}

/** Colour-code a line by its message word (the first field) for at-a-glance scanning. */
private fun messageColour(message: String, ink: Color): Color = when (message) {
    "HELLO" -> C_GREEN
    "DISCOVER", "INSTALL", "ACTIVATE", "DEACTIVATE" -> C_BLUE
    "BROADCAST", "REPORT" -> C_AMBER
    "SYSTEM_SIGNAL", "SUBSCRIBE", "INSTALL_END", "MANIFEST", "BLOCK", "LISTEN", "ACTION" -> C_PURPLE
    "ROGER" -> ink.copy(alpha = 0.6f)
    "TRIGGER" -> C_RED
    else -> ink
}
