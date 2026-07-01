package com.dash.android.ui.modules

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.transport.Discovery
import com.dash.android.transport.DiscoveredModule

private val BG = Color(0xFF0A0A12)
private val LIST_BG = Color(0xFF06060A)
private val CARD_BG = Color(0xFF14141F)
private val INACTIVE = Color(0xFF2A2A2A)
private val LABEL = Color(0xFF666666)
private val MUTED = Color(0xFF888888)

/**
 * The Module Management screen — a full-screen instrument reached from the settings panel, mirroring
 * the Serial Monitor route. Its job in 1.4.2: the DISCOVER button. Pressing it broadcasts `DISCOVER`
 * on every active transport (via the discovery desk) and the modules that answer with a `HELLO`
 * appear in the list below, by name.
 *
 * Discovery is an installation action, not reconnection: the list is what's *out there and answering
 * right now*, rebuilt from scratch on each press. It is the user's responsibility to make sure a
 * module has finished booting before pressing DISCOVER. The installed-module database (1.4.5) and the
 * per-module install/enable actions layer into this same screen in later versions.
 */
@Composable
fun ModuleManagementScreen(
    discovery: Discovery,
    onDismiss: () -> Unit
) {
    val modules by discovery.modules.collectAsState()

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
                if (modules.isEmpty()) {
                    Text(
                        text = "No modules yet.\n\nMake sure your module has finished booting, then press DISCOVER.",
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
                        items(modules, key = { it.id }) { module -> ModuleCard(module) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(module: DiscoveredModule) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD_BG, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = module.name.ifBlank { "(unnamed)" },
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
            TypeChip(module.type)
        }
        if (module.description.isNotBlank()) {
            Text(module.description, color = MUTED, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(module.id, color = LABEL, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            if (module.version.isNotBlank()) {
                Text(module.version, color = LABEL, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
        Text(type.ifBlank { "?" }.uppercase(), color = colour, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
    }
}
