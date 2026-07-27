package com.dash.android.ui.signal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dash.android.core.SystemCommands
import com.dash.android.ui.modules.LocalModuleDesk
import com.dash.android.ui.theme.LocalDashTheme
import kotlinx.coroutines.delay

/** A signal carrying a live value, in the same green the other two instruments use for "good". */
private val LIVE = Color(0xFF3DA35D)

/**
 * Modules › Signal Monitor (roadmap 1.5.12) — the 1.4.10 instrument rehomed into the settings shell.
 *
 * The live board of DASH's system messages: every standard signal in the vocabulary
 * (`system_commands.md` via [SystemCommands]) against the current value held in the sourceless core.
 * A signal not yet heard shows "—".
 *
 * It reads the store only, and the store is **sourceless by design** (arduino.md §5): a value is "the
 * current state of `gear_position`", never "what board X said". That is exactly why two boards can
 * both feed `ambient_temp` and this screen simply shows the latest — so it deliberately shows no
 * source and no subscriber, a decision taken with Roger at 1.4.10 and kept through the rehome.
 *
 * Rebuilt on the settings surface rather than ported: the old near-black screen carried its own header
 * and CLOSE button, which a tab has no use for — the nav names it and the shell frames it.
 */
@Composable
fun SignalMonitorContent() {
    val theme = LocalDashTheme.current
    val desk = LocalModuleDesk.current
    if (desk == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Module desk unavailable.",
                color = theme.textColourSecondary.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontFamily = theme.font,
            )
        }
        return
    }

    val values by desk.systemState.values.collectAsState()
    val functions = remember { SystemCommands.allFunctions() }

    // One-second ticker so the age column counts up without new data arriving.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }

    val ink = theme.textColourSecondary
    val inkFaint = ink.copy(alpha = 0.55f)
    val idle = ink.copy(alpha = 0.32f)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pinned: how much of the vocabulary is actually live. No in-box heading — the settings nav
        // already names the tab, the same rule Module Manager follows.
        Text(
            "${values.size} of ${functions.size} signals live",
            color = inkFaint,
            fontSize = 12.sp,
            fontFamily = theme.font,
        )

        Row(Modifier.fillMaxWidth()) {
            ColumnHead("SYSTEM MESSAGE", 1.6f, inkFaint, theme.font)
            ColumnHead("STATE", 0.9f, inkFaint, theme.font)
            ColumnHead("AGE", 0.4f, inkFaint, theme.font)
        }

        // The list takes the remaining height and scrolls on its own — the tab is `fillsBox`, so the
        // count and the column heads above stay pinned.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(functions, key = { it }) { function ->
                val stored = values[function]
                val live = stored != null
                Row(Modifier.fillMaxWidth()) {
                    Cell(function, if (live) LIVE else idle, 1.6f, theme.font)
                    Cell(stored?.value ?: "—", if (live) ink else idle, 0.9f, theme.font)
                    Cell(if (live) age(now - stored!!.updatedAt) else "", inkFaint, 0.4f, theme.font)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ColumnHead(
    text: String,
    weight: Float,
    colour: Color,
    font: FontFamily,
) {
    Text(
        text,
        color = colour,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontFamily = font,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    colour: Color,
    weight: Float,
    font: FontFamily,
) {
    Text(
        text,
        color = colour,
        fontSize = 12.sp,
        fontFamily = font,
        modifier = Modifier.weight(weight),
    )
}

private fun age(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}
