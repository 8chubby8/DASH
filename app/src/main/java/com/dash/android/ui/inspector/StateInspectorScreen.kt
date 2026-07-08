package com.dash.android.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.core.SystemEvent
import com.dash.android.transport.DashController
import com.dash.android.transport.ModuleActivity
import com.dash.android.transport.sim.SimulatedModuleTransport
import com.dash.android.transport.sim.VirtualModule
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The State Inspector (roadmap 1.4.7, extended 1.4.9) — the window into the cores, and the versions'
 * verification surface. Three panes: the **state store** (current value + age of every stateful signal
 * heard since boot), the **event log** (events as they fire), and **module reports** (each active
 * module's private variables — the sourceful per-module store, 1.4.9). Between them and the Serial
 * Monitor, every routing behaviour is visible: heartbeats streaming on the wire while the event pane
 * stays silent is change detection working; a broadcast that never reaches the store is the gatekeeper
 * refusing it; and the "ACTION ▸" button closes the specific column's loop — press it and the Sim
 * Accessory's `button_presses` climbs in the reports pane.
 *
 * Deliberately quick and dirty (agreed with Roger, 2026-07-07): the *machinery* it observes is the
 * permanent product; this screen is a dev instrument with no design pass, replaceable without
 * ceremony. It also carries the simulator bench controls — the pretend USB lead and the pretend
 * physical inputs of the virtual SYSTEM module.
 */
@Composable
fun StateInspectorScreen(
    controller: DashController,
    sim: SimulatedModuleTransport,
    onDismiss: () -> Unit
) {
    val values by controller.systemState.values.collectAsState()
    val reports by controller.moduleData.values.collectAsState()
    val installed by controller.database.modules.collectAsState()
    val activity by controller.reconciliation.activity.collectAsState()
    val simEnabled by sim.enabled.collectAsState()
    val vehicleActive by sim.vehicle.active.collectAsState()
    val accessoryActive by sim.accessory.active.collectAsState()

    // Rolling event log, newest first. The bus replays recent history, so opening late isn't blank.
    val events = remember { mutableStateListOf<SystemEvent>() }
    LaunchedEffect(Unit) {
        controller.systemState.events.collect {
            events.add(0, it)
            while (events.size > EVENT_LOG_MAX) events.removeAt(events.size - 1)
        }
    }

    // One-second ticker so the age column counts up without new data arriving.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A12))) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("STATE INSPECTOR", color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("CLOSE ✕", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }

            // Simulator bench controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { sim.setEnabled(!simEnabled) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (simEnabled) ACTIVE else INACTIVE,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(if (simEnabled) "SIMULATOR ON" else "SIMULATOR OFF", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }

                // The pretend physical world: door switch, gear selector, light stalk, wheel button.
                PokeButton("DOOR", vehicleActive) { sim.vehicle.pokeDoor() }
                PokeButton("GEAR", vehicleActive) { sim.vehicle.pokeGear() }
                PokeButton("LIGHTS", vehicleActive) { sim.vehicle.pokeHeadlights() }
                PokeButton("MEDIA ▸", vehicleActive) { sim.vehicle.pokeMediaNext() }
                // The pretend panel control: stands in for a user pressing the Sim Accessory's button
                // (1.4.9). Sends ACTION out; the accessory reports button_presses back — round trip.
                PokeButton("ACTION ▸", accessoryActive) { controller.actions.sendAction(sim.accessory.id, "sim_button") }
            }
            Text(
                text = listOf(
                    simStatus("Sim Vehicle", sim.vehicle, simEnabled, installed.containsKey(sim.vehicle.id), activity[sim.vehicle.id]),
                    simStatus("Sim Accessory", sim.accessory, simEnabled, installed.containsKey(sim.accessory.id), activity[sim.accessory.id]),
                    simStatus("Sim Relay", sim.relay, simEnabled, installed.containsKey(sim.relay.id), activity[sim.relay.id])
                ).joinToString("    ·    "),
                color = HINT,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )

            // The two panes: store left, events right.
            Row(
                modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("STATE STORE", color = LABEL, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    if (values.isEmpty()) {
                        Text(
                            "store empty — nothing heard yet",
                            color = HINT, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(values.entries.sortedBy { it.key }, key = { it.key }) { (function, stored) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(function, color = Color(0xFF80CBC4), fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.4f))
                                Text(stored.value, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.8f))
                                Text(age(now - stored.updatedAt), color = HINT, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.5f))
                            }
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("EVENTS", color = LABEL, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    if (events.isEmpty()) {
                        Text(
                            "no events fired yet",
                            color = HINT, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(events) { event ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(TIME_FORMAT.format(Date(event.at)), color = HINT, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    "  ${event.function}${event.value?.let { "  $it" } ?: ""}",
                                    color = if (event.value == null) Color(0xFFFFCC80) else Color.White,
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                // The per-module reports store (1.4.9) — the sourceful twin of the state store. Grouped
                // by module (its id is kept, unlike a broadcast), name-labelled where the record is known.
                Column(modifier = Modifier.weight(1f)) {
                    Text("MODULE REPORTS", color = LABEL, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    if (reports.isEmpty()) {
                        Text(
                            "no reports yet",
                            color = HINT, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        reports.entries.sortedBy { it.key }.forEach { (id, vars) ->
                            item(key = "hdr_$id") {
                                Text(
                                    installed[id]?.name ?: id,
                                    color = Color(0xFF9FA8DA), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                            items(vars.entries.sortedBy { it.key }.toList(), key = { "$id.${it.key}" }) { (variable, stored) ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(variable, color = Color(0xFF80CBC4), fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.4f))
                                    Text(stored.value, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.8f))
                                    Text(age(now - stored.updatedAt), color = HINT, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PokeButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = INACTIVE, contentColor = Color.White,
            disabledContainerColor = Color(0xFF1A1A1A), disabledContentColor = Color(0xFF444444)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
}

private fun simStatus(
    name: String,
    module: VirtualModule,
    simEnabled: Boolean,
    isInstalled: Boolean,
    activity: ModuleActivity?
): String = when {
    !simEnabled -> "$name: off"
    !isInstalled -> "$name: not installed — install from MANAGE MODULES"
    activity == ModuleActivity.ACTIVE || module.active.value -> "$name: ACTIVE"
    else -> "$name: DORMANT"
}

private fun age(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.UK)
private val LABEL = Color(0xFF666666)
private val HINT = Color(0xFF888888)
private val INACTIVE = Color(0xFF2A2A2A)
private val ACTIVE = Color(0xFF2E7D32)
private const val EVENT_LOG_MAX = 100
