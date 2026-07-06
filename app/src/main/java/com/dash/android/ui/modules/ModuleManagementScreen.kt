package com.dash.android.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dash.android.transport.Discovery
import com.dash.android.transport.Install
import com.dash.android.transport.Installing
import com.dash.android.transport.InstalledModule
import com.dash.android.transport.ModuleDatabase

private val BG = Color(0xFF0A0A12)
private val LIST_BG = Color(0xFF06060A)
private val CARD_BG = Color(0xFF14141F)
private val INSTALLED_BG = Color(0xFF0D160D)
private val INSTALLED_BORDER = Color(0xFF3DA35D)
private val INACTIVE = Color(0xFF2A2A2A)
private val LABEL = Color(0xFF666666)
private val MUTED = Color(0xFF888888)
private val INSTALL_ACCENT = Color(0xFF00695C)
private val UNINSTALL_ACCENT = Color(0xFF7A2222)

/**
 * The Module Management screen — a full-screen instrument reached from the settings panel, mirroring
 * the Serial Monitor route. DISCOVER (1.4.2) finds modules; INSTALL (1.4.4) runs the handshake; the
 * module database (1.4.5) makes the result permanent.
 *
 * **One physical module = one card.** The list is a merge of two sources keyed by id: the on-disk
 * installed list (green cards, present the moment the screen opens — no DISCOVER needed, plugged in or
 * not) and this session's discovered set (INSTALL cards for modules answering right now that aren't
 * installed). A module in both is one green card — discovery just confirmed something DASH knew.
 * DISCOVER still rebuilds the discovered set from scratch each press (an installation action, not
 * reconnection), but that can never remove an installed card: those are sustained by the database.
 *
 * An installed module is still *dormant* here — ACTIVATE and live data are 1.4.6+.
 */
@Composable
fun ModuleManagementScreen(
    discovery: Discovery,
    install: Install,
    database: ModuleDatabase,
    onDismiss: () -> Unit
) {
    val discovered by discovery.modules.collectAsState()
    val installing by install.states.collectAsState()
    val installed by database.modules.collectAsState()
    var detailsFor by remember { mutableStateOf<InstalledModule?>(null) }

    // The merged list: installed modules first (alphabetical — they're what you own), then this
    // session's discovered-but-not-installed modules in the order they answered.
    val rows = remember(discovered, installed) {
        installed.values
            .sortedBy { it.name.lowercase() }
            .map { ModuleRow(it.id, it.type, it.name, it.description, it.version, installedRecord = it) } +
        discovered
            .filterNot { installed.containsKey(it.id) }
            .map { ModuleRow(it.id, it.type, it.name, it.description, it.version, installedRecord = null) }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MODULE MANAGEMENT", color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("CLOSE ✕", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }

            // DISCOVER broadcasts to every module on every active transport, so this screen is about
            // the whole bus of modules, not any one device — there is deliberately no single-device
            // status here (device/connection state belongs to the Devices view and Serial Monitor).
            // Always pressable: with nothing connected it simply finds nothing.
            Button(
                onClick = { discovery.discover() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A237E),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) { Text("DISCOVER", fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp) }

            // List
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(LIST_BG)) {
                if (rows.isEmpty()) {
                    Text(
                        text = "No modules yet.\n\nInstalled modules appear here automatically.\nTo add one, make sure it has finished booting, then press DISCOVER.",
                        color = MUTED,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rows, key = { it.id }) { row ->
                            ModuleCard(
                                row = row,
                                installing = installing[row.id],
                                onInstall = { install.install(row.id) },
                                onDetails = { detailsFor = row.installedRecord }
                            )
                        }
                    }
                }
            }
        }
    }

    detailsFor?.let { module ->
        ModuleDetailsDialog(
            module = module,
            onUninstall = {
                database.uninstall(module.id)
                detailsFor = null
            },
            onDone = { detailsFor = null }
        )
    }
}

/**
 * One row of the merged list — one physical module, whichever source knows it. Identity fields come
 * from the installed record when there is one (the durable truth), otherwise from this session's HELLO.
 */
private data class ModuleRow(
    val id: String,
    val type: String,
    val name: String,
    val description: String,
    val version: String,
    val installedRecord: InstalledModule?
)

@Composable
private fun ModuleCard(
    row: ModuleRow,
    installing: Installing?,
    onInstall: () -> Unit,
    onDetails: () -> Unit
) {
    val isInstalled = row.installedRecord != null
    val cardModifier = Modifier
        .fillMaxWidth()
        .background(if (isInstalled) INSTALLED_BG else CARD_BG, RoundedCornerShape(6.dp))
        .then(
            if (isInstalled) Modifier.border(1.dp, INSTALLED_BORDER, RoundedCornerShape(6.dp))
            else Modifier
        )
        .padding(horizontal = 14.dp, vertical = 12.dp)

    Column(modifier = cardModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.name.ifBlank { "(unnamed)" },
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
            TypeChip(row.type)
        }
        if (row.description.isNotBlank()) {
            Text(row.description, color = MUTED, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(row.id, color = LABEL, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            if (row.version.isNotBlank()) {
                Text(row.version, color = LABEL, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Action area — the pane's status object: Install → progress → Details.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                installing != null -> InstallingBar(installing.progress, Modifier.weight(1f))
                isInstalled -> ActionButton("DETAILS", INACTIVE, onDetails)
                else -> ActionButton("INSTALL", INSTALL_ACCENT, onInstall)
            }
        }
    }
}

/** The in-progress bar. Determinate once an ACCESSORY MANIFEST gives a total; indeterminate otherwise. */
@Composable
private fun InstallingBar(progress: Float?, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier.weight(1f),
                color = INSTALLED_BORDER,
                trackColor = INACTIVE
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f),
                color = INSTALLED_BORDER,
                trackColor = INACTIVE
            )
        }
        Text(
            text = if (progress == null) "INSTALLING…" else "${(progress * 100).toInt()}%",
            color = MUTED,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ActionButton(label: String, colour: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = colour, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
    ) { Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp) }
}

/**
 * The Details dialog (a settings-side modal, distinct from a v3 Overlay). Read-only proof of what the
 * install captured — since 1.4.5, read back from the on-disk record — with Uninstall and Done. Its
 * content is type-shaped: signals for SYSTEM, subscriptions for LISTENER, assets for ACCESSORY.
 */
@Composable
private fun ModuleDetailsDialog(
    module: InstalledModule,
    onUninstall: () -> Unit,
    onDone: () -> Unit
) {
    Dialog(onDismissRequest = onDone) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .background(CARD_BG, RoundedCornerShape(10.dp))
                .border(1.dp, INSTALLED_BORDER, RoundedCornerShape(10.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(module.name.ifBlank { "(unnamed)" }, color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                TypeChip(module.type)
            }
            Text("${module.id}   ${module.version}", color = LABEL, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

            when (module.type.uppercase()) {
                "SYSTEM" -> DetailSection("SIGNALS BROADCAST", module.signals.ifEmpty { listOf("(none declared)") })
                "LISTENER" -> DetailSection("SUBSCRIPTIONS", module.subscriptions.map { it.display() }.ifEmpty { listOf("(none declared)") })
                "ACCESSORY" -> DetailSection("ASSETS RECEIVED", module.assets.map { "${it.name}  —  ${it.bytes} bytes ${if (it.crcOk) "✓" else "✗"}" }.ifEmpty { listOf("(none received)") })
                else -> DetailSection("DECLARATIONS", listOf("(unknown module type)"))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onUninstall,
                    colors = ButtonDefaults.buttonColors(containerColor = UNINSTALL_ACCENT, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) { Text("UNINSTALL", fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp) }
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = INSTALL_ACCENT, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) { Text("DONE", fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp) }
            }
        }
    }
}

@Composable
private fun DetailSection(heading: String, rows: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, color = MUTED, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        rows.forEach { row ->
            Text(row, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** One subscription rendered for the dialog: the function plus any throttle/gate fields that were set. */
private fun com.dash.android.transport.Subscription.display(): String {
    val tail = listOf(rate, threshold, gate, gateValue).filter { it.isNotBlank() }
    return if (tail.isEmpty()) function else "$function  [${tail.joinToString("  ")}]"
}

/** Colour the type word by the three module types (arduino.md §4a). Unknown types read neutral. */
@Composable
private fun TypeChip(type: String) {
    val colour = when (type.uppercase()) {
        "SYSTEM" -> Color(0xFF4FC3F7)
        "ACCESSORY" -> Color(0xFF81C784)
        "LISTENER" -> Color(0xFFBA9EDB)
        else -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .background(colour.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(type.ifBlank { "?" }.uppercase(), color = colour, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
    }
}
