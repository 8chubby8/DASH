package com.dash.android.ui.viewport

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dash.android.ui.theme.LocalDashTheme
import com.dash.android.viewport.ViewportHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LaunchableApp(val pkg: String, val label: String, val icon: ImageBitmap?)

/**
 * **A stand-in launcher, drawn in the viewport — test build only** (roadmap 1.7.1).
 *
 * The App Launcher is 1.8.x, but the viewport cannot be judged without something to put in it, so
 * this lists every launchable app as a plain grid and opens the tapped one through [ViewportHost].
 * It is deliberately bare and labelled as a test: it is scaffolding for seeing the viewport, not a
 * first draft of the launcher, and it goes when 1.8.x arrives.
 *
 * The thin outline is the measured viewport boundary itself — drawn so the box an app is meant to
 * land in can be seen before anything is in it.
 */
@Composable
fun ViewportTestLauncher(host: ViewportHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val theme = LocalDashTheme.current
    val statuses by host.statuses.collectAsState()
    val active by host.activeFlow.collectAsState()
    val testing by host.testing.collectAsState()
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { info ->
                    LaunchableApp(
                        pkg = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString(),
                        icon = runCatching { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    val ink = theme.textColourSecondary
    Box(modifier.border(1.dp, theme.accentColourPrimary.copy(alpha = 0.5f))) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val method = if (testing || statuses.isEmpty()) "testing…" else active.label
            Text(
                "VIEWPORT TEST BUILD — opens apps using: $method",
                color = ink.copy(alpha = 0.7f),
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                fontFamily = theme.font,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(apps, key = { it.pkg }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { host.launch(app.pkg) }.padding(4.dp),
                    ) {
                        app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                        Text(
                            app.label,
                            color = ink,
                            fontSize = 12.sp,
                            fontFamily = theme.font,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
