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
import com.dash.android.ui.theme.LocalDashTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dash.android.transport.Discovery
import com.dash.android.transport.FailReason
import com.dash.android.transport.Install
import com.dash.android.transport.InstallState
import com.dash.android.transport.InstalledModule
import com.dash.android.transport.ModuleActivity
import com.dash.android.transport.ModuleDatabase
import com.dash.android.transport.Reconciliation

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
private val UPDATE_ACCENT = Color(0xFFC98A2B)   // amber — a firmware version mismatch (1.4.13)
private val NO_REPLY_ACCENT = Color(0xFFE67E22) // orange — an installed module that has gone silent (1.4.14)
private val FAIL_ACCENT = Color(0xFFD9534F)     // red — a failed install (1.4.14)

/**
 * The Module Management screen — a full-screen instrument reached from the settings panel, mirroring
 * the Serial Monitor route. The sweep (1.4.6) finds modules; INSTALL (1.4.4) runs the handshake; the
 * module database (1.4.5) makes the result permanent; reconciliation (1.4.6) keeps it alive.
 *
 * **One physical module = one card.** The list is a merge of two sources keyed by id: the on-disk
 * installed list (green cards, present the moment the screen opens — plugged in or not, each wearing
 * its ACTIVE/DORMANT state) and the discovered set (INSTALL cards for modules answering the sweep that
 * aren't installed). A module in both is one green card. Nothing here can remove an installed card:
 * those are sustained by the database.
 *
 * *(Amended for 1.4.6, 2026-07-06: the DISCOVER button became SYNC. As built in 1.4.2 the button was
 * the only broadcast — user-driven, list rebuilt per press. The reconciliation sweep now broadcasts
 * continuously, so cards appear when hardware is plugged in and age away when it stops answering;
 * SYNC simply runs the sweep immediately — the §6 manual "check now" — rather than owning discovery.)*
 */
@Composable
fun ModuleManagementScreen(
    discovery: Discovery,
    install: Install,
    database: ModuleDatabase,
    reconciliation: Reconciliation,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val discovered by discovery.modules.collectAsState()
    val installStates by install.states.collectAsState()
    val installed by database.modules.collectAsState()
    val activity by reconciliation.activity.collectAsState()
    val mismatch by reconciliation.versionMismatch.collectAsState()
    val unconfirmed by reconciliation.unconfirmedDeactivation.collectAsState()
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
                Text("MODULE MANAGEMENT", color = Color.White, fontSize = 15.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 3.sp)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("CLOSE ✕", fontSize = 12.sp, fontFamily = LocalDashTheme.current.font) }
            }

            // REFRESH re-scans the bus right now — the §6 manual "check now" after fixing wiring or
            // plugging something in. Beyond an immediate sweep it *tidies* (roadmap 1.4.14): discovered
            // cards that no longer answer are dropped, and installed modules that have gone silent turn
            // orange (NOT_RESPONDING). The sweep broadcasts to every module on every active transport, so
            // this screen is about the whole bus of modules, not any one device. Always pressable: with
            // nothing connected it simply finds nothing. (DISCOVER until 1.4.6, SYNC until 1.4.14.)
            Button(
                onClick = { reconciliation.refresh() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A237E),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) { Text("REFRESH", fontSize = 14.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 2.sp) }

            // List
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(LIST_BG)) {
                if (rows.isEmpty()) {
                    Text(
                        text = "No modules yet.\n\nPlug a module in and it appears here within moments.\nREFRESH checks the bus right now instead of waiting.",
                        color = MUTED,
                        fontSize = 13.sp,
                        fontFamily = LocalDashTheme.current.font,
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
                                activity = activity[row.id],
                                reportedVersion = mismatch[row.id],
                                installState = installStates[row.id],
                                onInstall = { install.install(row.id) },
                                onCancel = { install.cancel(row.id) },
                                onRetry = { install.install(row.id) },
                                onDismiss = { install.dismiss(row.id) },
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
            reportedVersion = mismatch[module.id],
            activity = activity[module.id],
            wired = reconciliation.isWired(module.id),
            // Since 1.4.6 uninstall goes through the reconciliation desk: the record is still deleted
            // immediately, but an active module is also sent DEACTIVATE so it stops transmitting.
            onUninstall = {
                reconciliation.uninstall(module)
                detailsFor = null
            },
            // 1.4.13: a firmware update is a reinstall — the controller forgets the stale record and
            // re-runs the handshake, re-capturing the contract from the new firmware.
            onUpdate = {
                onUpdate(module.id)
                detailsFor = null
            },
            onDone = { detailsFor = null }
        )
    }

    // The §6 warning: a DEACTIVATE that was never ROGERed. The record is already gone; the module may
    // still be transmitting, and only physical action can silence it.
    unconfirmed?.let { name ->
        UnconfirmedDeactivationDialog(name = name, onDismiss = { reconciliation.clearUnconfirmed() })
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
    activity: ModuleActivity?,
    reportedVersion: String?,
    installState: InstallState?,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
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
                fontFamily = LocalDashTheme.current.font
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isInstalled && reportedVersion != null) MismatchChip()
                if (isInstalled) ActivityChip(activity ?: ModuleActivity.DORMANT)
                TypeChip(row.type)
            }
        }
        if (row.description.isNotBlank()) {
            Text(row.description, color = MUTED, fontSize = 12.sp, fontFamily = LocalDashTheme.current.font)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(row.id, color = LABEL, fontSize = 11.sp, fontFamily = LocalDashTheme.current.font)
            if (row.version.isNotBlank()) {
                Text(row.version, color = LABEL, fontSize = 11.sp, fontFamily = LocalDashTheme.current.font)
            }
        }

        // Action area — the pane's status object: Install → progress (with Cancel) → Details, or a
        // designed Failed state with Retry/Dismiss (roadmap 1.4.14).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (val state = installState) {
                is InstallState.Installing -> {
                    InstallingBar(state.progress, Modifier.weight(1f))
                    ActionButton("CANCEL", UNINSTALL_ACCENT, onCancel)
                }
                is InstallState.Failed -> FailedContent(state.reason, Modifier.weight(1f), onRetry, onDismiss)
                null -> if (isInstalled) ActionButton("DETAILS", INACTIVE, onDetails)
                        else ActionButton("INSTALL", INSTALL_ACCENT, onInstall)
            }
        }
    }
}

/** The designed install-failure state (roadmap 1.4.14): an honest reason with RETRY and DISMISS —
 *  never a silent snap-back to the plain card. */
@Composable
private fun FailedContent(reason: FailReason, modifier: Modifier, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val message = when (reason) {
        FailReason.STALLED -> "Install stalled — no response"
        FailReason.DISCONNECTED -> "Module disconnected during install"
        FailReason.CORRUPT -> "Corrupt asset — install aborted"
    }
    Text(message, color = FAIL_ACCENT, fontSize = 12.sp, fontFamily = LocalDashTheme.current.font, modifier = modifier)
    ActionButton("DISMISS", INACTIVE, onDismiss)
    ActionButton("RETRY", INSTALL_ACCENT, onRetry)
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
            fontFamily = LocalDashTheme.current.font
        )
    }
}

@Composable
private fun ActionButton(label: String, colour: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = colour, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
    ) { Text(label, fontSize = 12.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 1.sp) }
}

/**
 * The Details dialog (a settings-side modal, distinct from a v3 Overlay). Read-only proof of what the
 * install captured — since 1.4.5, read back from the on-disk record — with Uninstall and Done. Its
 * content is type-shaped: signals for SYSTEM, subscriptions for LISTENER, assets for ACCESSORY.
 */
@Composable
private fun ModuleDetailsDialog(
    module: InstalledModule,
    reportedVersion: String?,
    activity: ModuleActivity?,
    wired: Boolean?,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
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
                Text(module.name.ifBlank { "(unnamed)" }, color = Color.White, fontSize = 16.sp, fontFamily = LocalDashTheme.current.font)
                TypeChip(module.type)
            }
            Text("${module.id}   ${module.version}", color = LABEL, fontSize = 11.sp, fontFamily = LocalDashTheme.current.font)

            // 1.4.13: when the module reports a version different from the one stored at install, the
            // stored declarations below can't be trusted — the module is held DORMANT and all its
            // traffic refused until it's updated. Surface both versions and the reason, never swallow it.
            if (reportedVersion != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UPDATE_ACCENT.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, UPDATE_ACCENT.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("FIRMWARE VERSION MISMATCH", color = UPDATE_ACCENT, fontSize = 10.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 2.sp)
                    Text("installed  ${module.version.ifBlank { "(none)" }}", color = Color.White, fontSize = 12.sp, fontFamily = LocalDashTheme.current.font)
                    Text("reporting  ${reportedVersion.ifBlank { "(none)" }}", color = Color.White, fontSize = 12.sp, fontFamily = LocalDashTheme.current.font)
                    Text("Held inactive — its data is refused until it is updated. UPDATE re-runs the install.", color = MUTED, fontSize = 11.sp, fontFamily = LocalDashTheme.current.font)
                }
            }

            // 1.4.14: a module that was here this session and went silent. The state is the same orange
            // whether wired or wireless; the reason is worded by how it last reached DASH.
            if (activity == ModuleActivity.NOT_RESPONDING) {
                val reason = when (wired) {
                    true -> "It last connected over a wired link, so this looks like a fault — check the module's power and cable."
                    false -> "It last connected wirelessly, so it may simply be out of range or powered down."
                    null -> "It has stopped answering DASH."
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NO_REPLY_ACCENT.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, NO_REPLY_ACCENT.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("NOT RESPONDING", color = NO_REPLY_ACCENT, fontSize = 10.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 2.sp)
                    Text(reason, color = MUTED, fontSize = 11.sp, fontFamily = LocalDashTheme.current.font)
                }
            }

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
                ) { Text("UNINSTALL", fontSize = 12.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 1.sp) }
                if (reportedVersion != null) {
                    Button(
                        onClick = onUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = UPDATE_ACCENT, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) { Text("UPDATE", fontSize = 12.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 1.sp) }
                }
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = INSTALL_ACCENT, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) { Text("DONE", fontSize = 12.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 1.sp) }
            }
        }
    }
}

@Composable
private fun DetailSection(heading: String, rows: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, color = MUTED, fontSize = 11.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 2.sp)
        rows.forEach { row ->
            Text(row, color = Color.White, fontSize = 13.sp, fontFamily = LocalDashTheme.current.font)
        }
    }
}

/** One subscription rendered for the dialog: the function plus any throttle/gate fields that were set. */
private fun com.dash.android.transport.Subscription.display(): String {
    val tail = listOf(rate, threshold, gate, gateValue).filter { it.isNotBlank() }
    return if (tail.isEmpty()) function else "$function  [${tail.joinToString("  ")}]"
}

/** An installed module's liveness (arduino.md §6): green ACTIVE — it answered the sweep and ROGERed
 *  its ACTIVATE — grey DORMANT, or orange NO REPLY (roadmap 1.4.14) for a module that was here this
 *  session and then went silent. Why it went silent (wired fault vs wireless range) is spelt out in
 *  DETAILS. */
@Composable
private fun ActivityChip(activity: ModuleActivity) {
    val (label, colour) = when (activity) {
        ModuleActivity.ACTIVE -> "ACTIVE" to INSTALLED_BORDER
        ModuleActivity.NOT_RESPONDING -> "NO REPLY" to NO_REPLY_ACCENT
        ModuleActivity.DORMANT -> "DORMANT" to MUTED
    }
    Box(
        modifier = Modifier
            .background(colour.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = colour, fontSize = 10.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 1.sp)
    }
}

/** The 1.4.13 firmware-mismatch marker: the module is reporting a version different from the one
 *  stored at install, so its stored contract may be stale and a one-tap UPDATE is offered in DETAILS. */
@Composable
private fun MismatchChip() {
    Box(
        modifier = Modifier
            .background(UPDATE_ACCENT.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text("UPDATE", color = UPDATE_ACCENT, fontSize = 10.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 1.sp)
    }
}

/** The arduino.md §6 unconfirmed-deactivation warning, shown once per occurrence. */
@Composable
private fun UnconfirmedDeactivationDialog(name: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .background(CARD_BG, RoundedCornerShape(10.dp))
                .border(1.dp, UNINSTALL_ACCENT, RoundedCornerShape(10.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("UNINSTALLED — NOT CONFIRMED", color = Color.White, fontSize = 14.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 2.sp)
            Text(
                text = "$name has been uninstalled, but it never confirmed deactivation. It could still " +
                    "send misleading data, so either disconnect the module or power-cycle the module.",
                color = MUTED,
                fontSize = 13.sp,
                fontFamily = LocalDashTheme.current.font
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) { Text("UNDERSTOOD", fontSize = 12.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 1.sp) }
            }
        }
    }
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
        Text(type.ifBlank { "?" }.uppercase(), color = colour, fontSize = 10.sp, fontFamily = LocalDashTheme.current.font, letterSpacing = 1.sp)
    }
}
