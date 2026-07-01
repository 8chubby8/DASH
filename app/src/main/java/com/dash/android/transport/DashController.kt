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
 * version (install handshake 1.4.4, reconciliation 1.4.6, system-message routing 1.4.7, module data
 * 1.4.9…). Today exactly one desk is staffed: [discovery] (`HELLO`). Every other TYPE word is
 * deliberately ignored for now rather than mishandled — it still shows on the wire tap, so nothing is
 * lost, and adding its desk later is a single new branch in [route], never a rewrite.
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

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            transport.inbound.collect { line -> route(line) }
        }
    }

    /** Sort an inbound line by its TYPE word and hand it to the desk that owns that message type. */
    private fun route(line: String) {
        when (line.substringBefore('|').trim()) {
            "HELLO" -> discovery.onHello(line)
            // No desk for these TYPE words yet — added one per version. Still visible on the wire tap.
            else -> Unit
        }
    }

    fun stop() {
        if (!started) return
        started = false
        scope.cancel()
    }
}
