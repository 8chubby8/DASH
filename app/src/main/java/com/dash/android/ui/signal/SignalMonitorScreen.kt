package com.dash.android.ui.signal

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.core.SystemCommands
import com.dash.android.transport.DashController
import kotlinx.coroutines.delay

/**
 * The Signal Monitor (roadmap 1.4.10) — the live board of DASH's system messages. It lists every
 * standard signal in the vocabulary (`system_commands.md` via [SystemCommands]) against the current
 * value held in the sourceless core ([DashController.systemState]). Two columns: the system message,
 * and its state. A signal not yet heard shows "—".
 *
 * It reads the store only — sourceless by design (arduino.md §5): the value is "the current state of
 * `gear_position`", never "what module X said". That is exactly why two boards can both feed
 * `ambient_temp` and the board simply shows the latest — this screen is the window onto that core, and
 * deliberately shows no source or subscriber (a decision taken with Roger: keep it to message + state).
 *
 * A dev instrument like the Serial Monitor — reached from settings, no design pass, replaceable.
 */
@Composable
fun SignalMonitorScreen(
    controller: DashController,
    onDismiss: () -> Unit
) {
    val values by controller.systemState.values.collectAsState()
    val functions = remember { SystemCommands.allFunctions() }

    // One-second ticker so the age column counts up without new data arriving.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SIGNAL MONITOR", color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                    Text(
                        "${values.size} of ${functions.size} live",
                        color = HINT, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = INACTIVE, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("CLOSE ✕", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                Text("SYSTEM MESSAGE", color = LABEL, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, modifier = Modifier.weight(1.6f))
                Text("STATE", color = LABEL, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, modifier = Modifier.weight(0.9f))
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(functions, key = { it }) { function ->
                    val stored = values[function]
                    val live = stored != null
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            function,
                            color = if (live) LIVE_NAME else IDLE,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1.6f)
                        )
                        Text(
                            stored?.value ?: "—",
                            color = if (live) Color.White else IDLE,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(0.9f)
                        )
                        Text(
                            if (live) age(now - stored!!.updatedAt) else "",
                            color = HINT, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(0.4f)
                        )
                    }
                }
            }
        }
    }
}

private fun age(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}

private val BG = Color(0xFF0A0A12)
private val LABEL = Color(0xFF666666)
private val HINT = Color(0xFF888888)
private val INACTIVE = Color(0xFF2A2A2A)
private val LIVE_NAME = Color(0xFF80CBC4)
private val IDLE = Color(0xFF454552)
