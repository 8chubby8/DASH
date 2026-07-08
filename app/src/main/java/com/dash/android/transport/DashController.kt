package com.dash.android.transport

import android.content.Context
import com.dash.android.core.ModuleData
import com.dash.android.core.SystemState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
 * version. Desks staffed today: [discovery] (`HELLO` collection), [install] (the handshake —
 * `SYSTEM_SIGNAL` / `SUBSCRIBE` / `MANIFEST` / asset blocks / `INSTALL_END`, roadmap 1.4.4),
 * [reconciliation] (the sweep, `ACTIVATE`/`DEACTIVATE`, `ROGER` — roadmap 1.4.6), [broadcasts]
 * (`BROADCAST` → the sourceless core, roadmap 1.4.7), [streams] (the core → `LISTEN` out to subscribers,
 * roadmap 1.4.8), [moduleReports] (`REPORT` → the per-module store, roadmap 1.4.9) and [actions]
 * (`ACTION` out to a module's panel control, roadmap 1.4.9). Every other TYPE word is deliberately
 * ignored for now rather than mishandled — it still shows on the wire tap, so nothing is lost, and
 * adding its desk later is a single new branch in [route], never a rewrite.
 *
 * The 2×2 of arduino.md §4 is now fully staffed: [broadcasts]/[streams] are the general (sourceless)
 * column in and out, [moduleReports]/[actions] the specific (per-module, sourceful) column in and out.
 *
 * Two desks are not in [route]: [streams] is driven by *watching* the sourceless core, and [actions] by
 * a *user gesture* in a panel — neither by an inbound TYPE word. They are the two that produce without
 * consuming the wire, the outbound mirrors of [broadcasts] and [moduleReports] respectively.
 *
 * `HELLO` is the one line two desks receive — deliberately. The install/reconnect distinction lives
 * in DASH, not on the wire (arduino.md §6: a module doesn't know its own install state), so the same
 * reply is an install candidate to the discovery desk and a liveness report to the reconciliation
 * desk; each ignores the ids that aren't its business.
 *
 * Future direction (see the transport-brain design notes): routing becomes *configurable* — standard
 * signals act by sensible defaults, custom signals fall through to a user-defined desk, and a
 * patch-bay override redirects signals to Android, DASH, or back out to another module. The shape
 * here — one inbox, sort by TYPE, dispatch, one outbox — is what makes that possible without rework.
 */
class DashController(private val transport: TransportManager, context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The module database (1.4.5). The on-disk installed list — the single source of truth for
     *  install state (arduino.md §6), loaded every boot, fed by the install desk on commit. */
    val database = ModuleDatabase(context)

    /** The discovery desk. Collects the HELLO replies the reconciliation sweep raises (since 1.4.6;
     *  the broadcast itself was this desk's until then). */
    val discovery = Discovery()

    /** The reconciliation desk (1.4.6). Owns the persistent DISCOVER sweep, keeps installed modules
     *  ACTIVE, and hushes them with DEACTIVATE on uninstall. */
    val reconciliation: Reconciliation = Reconciliation(
        scope = scope,
        send = transport::send,
        database = database,
        installBusy = { install.states.value.isNotEmpty() },
        pruneDiscovered = discovery::prune
    )

    /** The sourceless core's system state (1.4.7) — the §5a state store and event bus. The interface
     *  era reads from this; today the State Inspector is its window. */
    val systemState = SystemState()

    /** The broadcast desk (1.4.7). The §5 gatekeeper: checks the sender is installed and ACTIVE,
     *  consumes the id, then routes the signal into [systemState] by its §5a behaviour. */
    val broadcasts: Broadcasts = Broadcasts(
        state = systemState,
        isInstalled = { id -> database.modules.value.containsKey(id) },
        isActive = { id -> reconciliation.activity.value[id] == ModuleActivity.ACTIVE },
        heard = { id -> reconciliation.heard(id) }
    )

    /** The streams desk (1.4.8) — LISTENER delivery, the outbound mirror of [broadcasts]. Watches
     *  [systemState] and the installed/active modules, and delivers subscribed signals back out as
     *  `LISTEN`, evaluating rate/threshold/gate here (arduino.md §9). */
    val streams: Streams = Streams(
        scope = scope,
        send = transport::send,
        state = systemState,
        installed = database.modules,
        activity = reconciliation.activity
    )

    /** The per-module data store (1.4.9) — the sourceful twin of [systemState]. A module's private
     *  panel variables (`REPORT`) land here keyed by id. The module panel (1.6.x) reads it; today the
     *  State Inspector is its window. */
    val moduleData = ModuleData()

    /** The module reports desk (1.4.9). The §5 gatekeeper for the specific column: checks the sender is
     *  installed and ACTIVE, then routes the variable into [moduleData] under its id (kept, not
     *  consumed — a report is private to its module). */
    val moduleReports: ModuleReports = ModuleReports(
        data = moduleData,
        isInstalled = { id -> database.modules.value.containsKey(id) },
        isActive = { id -> reconciliation.activity.value[id] == ModuleActivity.ACTIVE },
        heard = { id -> reconciliation.heard(id) }
    )

    /** The actions desk (1.4.9) — the outbound mirror of [moduleReports]. The 1.6.x module panel calls
     *  [Actions.sendAction] when the user operates a control; it emits `ACTION|id|control|value` to that
     *  module, gatekept to installed + ACTIVE. Holds no state. */
    val actions: Actions = Actions(
        send = transport::send,
        isInstalled = { id -> database.modules.value.containsKey(id) },
        isActive = { id -> reconciliation.activity.value[id] == ModuleActivity.ACTIVE }
    )

    /** The install desk. Runs the install handshake for a module the user chose from the discovery
     *  list; a completed handshake commits into the [database], then goes straight to activation —
     *  the §7 flow ends `INSTALL_END` → save → `ACTIVATE`. */
    val install: Install = Install(
        send = transport::send,
        identityOf = { id -> discovery.modules.value.firstOrNull { it.id == id } },
        isInstalled = { id -> database.modules.value.containsKey(id) },
        commit = { module, payloads ->
            database.commit(module, payloads)
            reconciliation.activate(module.id)
        }
    )

    private var started = false

    fun start() {
        if (started) return
        started = true
        database.load()
        reconciliation.start()
        streams.start()
        scope.launch {
            transport.inbound.collect { frame ->
                when (frame) {
                    is Inbound.Line -> route(frame.text)
                    is Inbound.Block -> install.onBlock(frame)   // asset payload → straight to the desk
                }
            }
        }
        // A transport coming up (boot, replug, permission granted) is the moment its modules become
        // reachable — sweep straight away rather than waiting out the timer.
        scope.launch {
            transport.status
                .map { it.state == TransportState.CONNECTED }
                .distinctUntilChanged()
                .filter { it }
                .collect { reconciliation.sync() }
        }
    }

    /** Sort an inbound line by its TYPE word and hand it to the desk that owns that message type. */
    private fun route(line: String) {
        when (line.substringBefore('|').trim()) {
            "HELLO" -> {
                discovery.onHello(line)
                reconciliation.onHello(line)
            }
            "ROGER" -> reconciliation.onRoger(line)
            "BROADCAST" -> broadcasts.onBroadcast(line)
            "REPORT" -> moduleReports.onReport(line)
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
