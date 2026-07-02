package com.dash.android.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The controller — DASH's message brain, the dispatcher at the desk.
 *
 * It sits above the [TransportManager] (the dumb pipe) and understands what lines *mean*. Every
 * inbound line lands here; the controller reads its TYPE word (the first pipe-separated field) and
 * routes it to the desk that handles that kind of message. Anything DASH sends goes back out through
 * the same pipe.
 *
 * This is the *frame*, built ahead of the features it will hold — the roadmap fills the desks one per
 * version (reconciliation 1.4.6, system-message routing 1.4.7, module data 1.4.9…). Two desks are
 * staffed today: [discovery] (`HELLO`) and [install] (the handshake — `SYSTEM_SIGNAL` / `SUBSCRIBE` /
 * `MANIFEST` / asset blocks / `INSTALL_END`, roadmap 1.4.4). Every other TYPE word is deliberately
 * ignored for now rather than mishandled — it still shows on the wire tap, so nothing is lost, and
 * adding its desk later is a single new branch in [route], never a rewrite.
 *
 * Future direction (see the transport-brain design notes): routing becomes *configurable* — standard
 * signals act by sensible defaults, custom signals fall through to a user-defined desk, and a
 * patch-bay override redirects signals to Android, DASH, or back out to another module. The shape
 * here — one inbox, sort by TYPE, dispatch, one outbox — is what makes that possible without rework.
 */
class DashController(private val transport: TransportManager) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The discovery desk. Broadcasts DISCOVER on the user's command and collects the HELLO replies. */
    val discovery = Discovery(broadcast = transport::send)

    /** The install desk. Runs the install handshake for a module the user chose from the discovery list. */
    val install = Install(
        send = transport::send,
        identityOf = { id -> discovery.modules.value.firstOrNull { it.id == id } }
    )

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            transport.inbound.collect { frame ->
                when (frame) {
                    is Inbound.Line -> route(frame.text)
                    is Inbound.Block -> install.onBlock(frame)   // asset payload → straight to the desk
                }
            }
        }
    }

    /** Sort an inbound line by its TYPE word and hand it to the desk that owns that message type. */
    private fun route(line: String) {
        when (line.substringBefore('|').trim()) {
            "HELLO" -> discovery.onHello(line)
            "SYSTEM_SIGNAL" -> install.onSignal(line)
            "SUBSCRIBE" -> install.onSubscribe(line)
            "MANIFEST" -> install.onManifest(line)
            "INSTALL_END" -> install.onInstallEnd(line)
            // BLOCK headers arrive as Inbound.Block (header + bytes together), so no branch is needed
            // here; they still show on the wire tap. No desk for other TYPE words yet — added one per
            // version.
            else -> Unit
        }
    }

    fun stop() {
        if (!started) return
        started = false
        scope.cancel()
    }
}
