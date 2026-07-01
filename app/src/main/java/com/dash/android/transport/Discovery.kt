package com.dash.android.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The discovery desk (roadmap 1.4.2, with 1.4.3's HELLO parsing merged in). One handler behind the
 * [DashController] router — the router hands it every `HELLO` line; it owns nothing else.
 *
 * Discovery here is a **user-driven method of installation, not reconnection**. DASH broadcasts
 * `DISCOVER` only when the user presses the DISCOVER button on the Module Management screen — never on
 * a timer. It is the user's job to make sure a module has finished booting before they press it; a
 * module still coming up simply won't answer that round. The automatic low-rate re-sweep of
 * *already-installed* modules (arduino.md §6) belongs to reconnection / startup reconciliation
 * (roadmap 1.4.6) — a separate mechanism from this manual, install-time sweep.
 *
 * Every DISCOVER press [discover] clears the list and re-broadcasts; the list then repopulates as
 * `HELLO` replies arrive over the following moment. [onHello] upserts by module id, so a module that
 * answers twice appears once. The list is app-lifetime state, so it survives closing and reopening
 * the screen — only the next DISCOVER press clears it.
 */
class Discovery(private val broadcast: (String) -> Unit) {

    private val _modules = MutableStateFlow<List<DiscoveredModule>>(emptyList())
    val modules: StateFlow<List<DiscoveredModule>> = _modules.asStateFlow()

    /** User pressed DISCOVER: empty the list and broadcast afresh. Replies repopulate as they land. */
    fun discover() {
        _modules.value = emptyList()
        broadcast(DISCOVER)
    }

    /** Handle a `HELLO` line routed here by the controller: parse it and upsert by id (last wins). */
    fun onHello(line: String) {
        val module = DiscoveredModule.parseHello(line) ?: return
        _modules.update { current -> current.filterNot { it.id == module.id } + module }
    }

    private companion object {
        const val DISCOVER = "DISCOVER"
    }
}
