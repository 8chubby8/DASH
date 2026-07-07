package com.dash.android.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The reconciliation desk (roadmap 1.4.6) — the controller's first desk with a *clock*. Where
 * discovery collects and install converses, this desk **keeps the installed world alive**: every
 * session, on a timer, it asks the bus who is there and tells the installed modules to wake up.
 *
 * **One message, two meanings — the meaning lives here, not on the wire.** The sweep broadcasts the
 * same `DISCOVER` the Module Management button always sent; a module cannot tell install-discovery
 * from reconnection because it doesn't need to — it persists nothing (arduino.md §6) and can only
 * ever answer "here's who I am". DASH is the party that knows the difference, and this desk is where
 * it knows it: a `HELLO` from an id in the [ModuleDatabase] means *reconnect* (send `ACTIVATE`); a
 * `HELLO` from an unknown id is left to the discovery desk as an install candidate. (Decision,
 * 2026-07-06: a separate reconnect message was considered and rejected — a broadcast form is
 * impossible when modules don't know their own install state, and a per-id ping would grow the SDK
 * contract to do what `HELLO` already does.)
 *
 * **The sweep re-asserts, it doesn't remember.** Every sweep, every installed module that answers is
 * sent `ACTIVATE` again — not only ones DASH thinks are silent. `ACTIVATE` is idempotent for the
 * module, and both module states answer `DISCOVER` identically, so a fresh `ROGER` is the only proof
 * the module is genuinely active *now*. This is what makes a brown-out reboot (SILENT again, mid
 * drive) self-heal within one sweep instead of never.
 *
 * **Cadence.** A fast phase after start ([FAST_SWEEP_MS], for [FAST_PHASE_MS]) catches slow booters —
 * the Uno Q's ~20–30 s Linux boot is the design case — then a slow forever-rate ([SLOW_SWEEP_MS])
 * catches resets and hot-plugs for the life of the session. [sync] jumps the timer for an immediate
 * sweep: it serves the user's SYNC button and a transport coming up. The sweep holds off while an
 * install handshake is in flight so a broadcast never interleaves a declaration run.
 *
 * **Active/Dormant.** [activity] is the per-installed-module state Module Management renders. ACTIVE
 * means a `ROGER|id|activate` has been heard and the module has answered a sweep within [ABSENT_MS];
 * everything else is DORMANT — including "answers HELLO but never confirms", which is logged. The
 * §6 wired-fault-vs-wireless-quiet distinction is deferred with the rest of the designed failure
 * surface (per the 1.4.4 note); today absent is absent.
 *
 * **Uninstall** now has a wire half: an uninstalled module that is (or recently was) present is sent
 * `DEACTIVATE`, acknowledged and retried. Per §6 the record is deleted *immediately* — DASH
 * forgetting is still the uninstall — and the retries run behind it; only if no `ROGER` ever comes
 * does [unconfirmedDeactivation] raise the §6 warning (disconnect or power-cycle the module). A
 * module that was never seen this session skips the wire entirely, exactly as 1.4.5 behaved.
 */
class Reconciliation(
    private val scope: CoroutineScope,
    private val send: (String) -> Unit,
    private val database: ModuleDatabase,
    private val installBusy: () -> Boolean,
    private val pruneDiscovered: (cutoffMs: Long) -> Unit
) {

    private val _activity = MutableStateFlow<Map<String, ModuleActivity>>(emptyMap())
    /** Liveness per installed module id. Absent ⇒ not installed; DORMANT until proven ACTIVE. */
    val activity: StateFlow<Map<String, ModuleActivity>> = _activity.asStateFlow()

    private val _unconfirmedDeactivation = MutableStateFlow<String?>(null)
    /** Module name whose DEACTIVATE was never ROGERed — the UI shows the §6 warning, then [clearUnconfirmed]. */
    val unconfirmedDeactivation: StateFlow<String?> = _unconfirmedDeactivation.asStateFlow()

    /** When each id last answered the bus (HELLO or ROGER), for absence aging and prune cutoffs. */
    private val lastSeen = mutableMapOf<String, Long>()

    /**
     * Monotonic count of ROGERs per "id|which". Ack-waits watch for the count to *rise past* a
     * snapshot taken before sending, so a stale ROGER can never satisfy a fresh command and a fast
     * one can never be missed — a StateFlow condition-wait has no subscribe race.
     */
    private val acks = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val activateJobs = mutableMapOf<String, Job>()
    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Begin the sweep loop. Waits for the database's disk load so the first HELLOs match against the
     *  real installed list rather than briefly reading as install candidates. */
    fun start() {
        scope.launch {
            database.loaded.first { it }
            val startedAt = System.currentTimeMillis()
            while (isActive) {
                sweep()
                val interval =
                    if (System.currentTimeMillis() - startedAt < FAST_PHASE_MS) FAST_SWEEP_MS
                    else SLOW_SWEEP_MS
                withTimeoutOrNull(interval) { syncRequests.first() }   // a sync() wakes the loop early
            }
        }
    }

    /** Sweep now — the SYNC button, and a transport coming up. The loop's timer resumes after. */
    fun sync() {
        syncRequests.tryEmit(Unit)
    }

    @Synchronized
    private fun sweep() {
        if (installBusy()) {
            Log.i(TAG, "sweep held — install handshake in flight")
            return
        }
        val now = System.currentTimeMillis()
        // Age first: rebuild the activity map from the current installed list, keeping ACTIVE only
        // for modules heard from recently. Adds new installs as DORMANT, drops uninstalled ids.
        _activity.value = database.modules.value.keys.associateWith { id ->
            if (now - (lastSeen[id] ?: Long.MIN_VALUE) <= ABSENT_MS) {
                _activity.value[id] ?: ModuleActivity.DORMANT
            } else ModuleActivity.DORMANT
        }
        pruneDiscovered(now - ABSENT_MS)
        send(DISCOVER)
    }

    /** Live traffic (a `BROADCAST` passing the gatekeeper, 1.4.7) counts as being heard — a module
     *  streaming between sweeps obviously isn't absent, so its ACTIVE state must not age out under
     *  it. Only touches the liveness clock; ACTIVE is still only ever granted by a `ROGER`. */
    @Synchronized
    fun heard(id: String) {
        lastSeen[id] = System.currentTimeMillis()
    }

    /** Every `HELLO` lands here as well as at the discovery desk. An installed id gets re-asserted. */
    @Synchronized
    fun onHello(line: String) {
        val id = line.split('|').getOrNull(1)?.trim().orEmpty()
        if (id.isEmpty()) return
        lastSeen[id] = System.currentTimeMillis()
        if (database.modules.value.containsKey(id)) activate(id)
    }

    /** `ROGER|id|which` — a module confirming a command. Feeds the ack counters; an activate ack is
     *  the one and only thing that turns a module ACTIVE. */
    @Synchronized
    fun onRoger(line: String) {
        val parts = line.split('|')
        val id = parts.getOrNull(1)?.trim().orEmpty()
        val which = parts.getOrNull(2)?.trim()?.lowercase().orEmpty()
        if (id.isEmpty() || which.isEmpty()) return
        lastSeen[id] = System.currentTimeMillis()
        acks.value = acks.value + (ackKey(id, which) to ackCount(id, which) + 1)
        if (which == ACK_ACTIVATE && database.modules.value.containsKey(id)) {
            _activity.value = _activity.value + (id to ModuleActivity.ACTIVE)
        }
    }

    /**
     * Send `ACTIVATE|id` and stand over it until a fresh `ROGER` arrives — [ACK_ATTEMPTS] tries,
     * [ACK_TIMEOUT_MS] apart. Called on every sweep answer (the re-assert) and directly by the
     * controller the moment an install commits (§7: the handshake ends `INSTALL_END` → `ACTIVATE`).
     * One attempt runs per module at a time; the sweep that follows an unconfirmed run tries afresh.
     */
    @Synchronized
    fun activate(id: String) {
        if (activateJobs[id]?.isActive == true) return
        activateJobs[id] = scope.launch {
            repeat(ACK_ATTEMPTS) {
                val before = ackCount(id, ACK_ACTIVATE)
                send("$ACTIVATE|$id")
                val acked = withTimeoutOrNull(ACK_TIMEOUT_MS) {
                    acks.first { (it[ackKey(id, ACK_ACTIVATE)] ?: 0) > before }
                }
                if (acked != null) return@launch
            }
            Log.w(TAG, "module $id answered the sweep but never confirmed ACTIVATE — left dormant")
        }
    }

    /**
     * The uninstall path since 1.4.6: forget first (per §6 the record is deleted immediately — DASH
     * forgetting *is* the uninstall), then hush the module if it's around to hear it.
     */
    @Synchronized
    fun uninstall(module: InstalledModule) {
        activateJobs.remove(module.id)?.cancel()
        database.uninstall(module.id)
        _activity.value = _activity.value - module.id
        val present = System.currentTimeMillis() - (lastSeen[module.id] ?: Long.MIN_VALUE) <= ABSENT_MS
        if (!present) return   // never heard from this session — nothing to hush, as in 1.4.5
        scope.launch {
            repeat(ACK_ATTEMPTS) {
                val before = ackCount(module.id, ACK_DEACTIVATE)
                send("$DEACTIVATE|${module.id}")
                val acked = withTimeoutOrNull(ACK_TIMEOUT_MS) {
                    acks.first { (it[ackKey(module.id, ACK_DEACTIVATE)] ?: 0) > before }
                }
                if (acked != null) return@launch
            }
            Log.w(TAG, "module ${module.id} never confirmed DEACTIVATE — raising the §6 warning")
            _unconfirmedDeactivation.value = module.name.ifBlank { module.id }
        }
    }

    /** The user has read the unconfirmed-deactivation warning. */
    fun clearUnconfirmed() {
        _unconfirmedDeactivation.value = null
    }

    private fun ackKey(id: String, which: String) = "$id|$which"
    private fun ackCount(id: String, which: String) = acks.value[ackKey(id, which)] ?: 0

    private companion object {
        const val DISCOVER = "DISCOVER"
        const val ACTIVATE = "ACTIVATE"
        const val DEACTIVATE = "DEACTIVATE"
        const val ACK_ACTIVATE = "activate"
        const val ACK_DEACTIVATE = "deactivate"

        /** Fast phase: catch slow booters (Uno Q ≈ 20–30 s) quickly after DASH comes up. */
        const val FAST_SWEEP_MS = 5_000L
        const val FAST_PHASE_MS = 60_000L

        /** Forever-rate: low enough to stay out of the way, often enough that a crank-reset module
         *  is back within half a minute. */
        const val SLOW_SWEEP_MS = 30_000L

        /** Not heard from for this long ⇒ absent (≈ two missed slow sweeps, with margin). Doubles as
         *  the discovery-list prune horizon so both views of the bus age on the same clock. */
        const val ABSENT_MS = 75_000L

        const val ACK_TIMEOUT_MS = 2_000L
        const val ACK_ATTEMPTS = 3

        const val TAG = "DashReconcile"
    }
}

/** Liveness of an installed module (arduino.md §6): ACTIVE is confirmed by a `ROGER`; DORMANT is
 *  everything else — never-seen, aged-out, or answering but unconfirmed. */
enum class ModuleActivity { ACTIVE, DORMANT }
