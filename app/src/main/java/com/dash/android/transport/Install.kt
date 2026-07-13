package com.dash.android.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.zip.CRC32

/**
 * The install desk (roadmap 1.4.4) — the [DashController]'s first *stateful* desk, and its first
 * *bidirectional* one. Where the discovery desk is fire-and-collect (each `HELLO` is self-contained),
 * an install is a **conversation**: DASH sends `INSTALL|id`, a run of declaration lines arrives that
 * all belong together, and `INSTALL_END|id` closes it. This desk accumulates that run into one
 * in-progress [InstallSession] per module and commits it on the close.
 *
 * **Opened by us, fed by the module, closed by the module.** [install] opens a session (seeded from the
 * module's discovery identity) and sends `INSTALL`; the controller then feeds each declaration in via
 * [onSignal] / [onSubscribe] / [onManifest] / [onBlock]; [onInstallEnd] validates and commits.
 *
 * **The desk holds *installing*; the [ModuleDatabase] holds *installed*** (since 1.4.5). [states] here
 * covers only handshakes under way this session (plus a lingering [InstallState.Failed] badge until the
 * user retries or dismisses it); a completed session is handed to [commit] — wired to the database,
 * which persists it and owns it from then on. Asset payload bytes are held in the session for exactly
 * that hop: validated here, written to disk by the database, never kept in memory after.
 *
 * **Designed failure (roadmap 1.4.14).** An install no longer stays pending forever. Three things can
 * end one unhappily, each surfaced as a [InstallState.Failed] the card renders with a reason and a
 * retry — never a silent snap-back:
 *  - **[FailReason.STALLED]** — an *idle* watchdog: if a session hears nothing for [IDLE_TIMEOUT_MS]
 *    it is aborted. Idle, not a total-duration cap, so a large asset transfer that is genuinely making
 *    progress is never killed — every declaration and block resets the clock.
 *  - **[FailReason.DISCONNECTED]** — the fast, precise path: [devicesPresent] fails a session the
 *    instant the device carrying it leaves the bus (board unplugged, socket dropped), rather than
 *    waiting out the idle timeout.
 *  - **[FailReason.CORRUPT]** — an asset block whose CRC or length does not match (was the one
 *    unavoidable abort since 1.4.4; now it wears a designed fail state like the others).
 * A user [cancel] is deliberate, so it is *not* a failure — it reverts the card cleanly with no badge.
 *
 * **Busy means in-flight, not badged.** [isBusy] counts only live [sessions], never a lingering
 * `Failed` badge, so the reconciliation sweep (which pauses while an install is in flight) is freed the
 * moment a handshake ends — closing the 1.4.6 "a wedged install pauses the sweep forever" item.
 *
 * **Keyed by id, holds many.** Sessions live in a map keyed by module id, because every declaration
 * carries its id (arduino.md §2). The UI drives one install at a time, but the wire is id-addressed, so
 * the frame naturally supports more than one in flight — build the frame for many, drive it as one.
 *
 * **Well-mannered.** A declaration for an id with no open session (a stray, a message type a later
 * build will handle, or the tail of a cancelled/failed install still monologuing) is logged and
 * ignored, never fatal. ACTIVATE and live data are 1.4.6+, so nothing here starts a module sending —
 * an installed module is dormant until reconciliation wakes it.
 */
class Install(
    private val scope: CoroutineScope,
    private val send: (String) -> Unit,
    private val identityOf: (String) -> DiscoveredModule?,
    private val isInstalled: (String) -> Boolean,
    private val commit: (InstalledModule, List<ByteArray>) -> Unit
) {
    private val sessions = mutableMapOf<String, InstallSession>()

    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    /** Per-module install state: [InstallState.Installing] while a handshake runs, [InstallState.Failed]
     *  until the user retries or dismisses. Absent ⇒ nothing in progress (installed lives in the database). */
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()

    /** True while any handshake is genuinely in flight — the sweep gate (1.4.6/1.4.14). A `Failed`
     *  badge sitting on a card is *not* busy, so it can never freeze reconciliation. */
    @Synchronized
    fun isBusy(): Boolean = sessions.isNotEmpty()

    /** User pressed Install (or Retry): clear any stale badge, open a session, arm its watchdog, and
     *  begin the handshake. */
    @Synchronized
    fun install(id: String) {
        if (sessions.containsKey(id)) return               // already installing
        if (isInstalled(id)) return                        // already installed — uninstall first
        val seed = identityOf(id) ?: run {
            _states.value = _states.value - id             // identity gone (module unplugged) — clear any badge
            return
        }
        val session = InstallSession(seed)
        sessions[id] = session
        setState(id, InstallState.Installing(progress = null))
        arm(id, session)
        send("$INSTALL|$id")
    }

    /** User pressed Cancel on an in-progress install. A deliberate stop is not a failure — drop the
     *  session and return the card to its plain discovered state, no badge. (The module keeps sending
     *  its declaration run to the end; those strays drop-and-log against the now-absent session — a
     *  wire-level abort message is an SDK-lock decision, roadmap 1.4.15.) */
    @Synchronized
    fun cancel(id: String) {
        val session = sessions.remove(id) ?: return
        session.watchdog?.cancel()
        _states.value = _states.value - id
    }

    /** User pressed Dismiss on a failed install: clear the badge without retrying. */
    @Synchronized
    fun dismiss(id: String) {
        _states.value = _states.value - id
    }

    /**
     * Device presence changed (roadmap 1.4.14) — the aggregated transport device list, fed by the
     * controller. Fail any in-flight install whose source device has left the bus: a module unplugged
     * mid-handshake, caught at once instead of waiting out the idle timeout. A session whose source is
     * not yet known (no declaration has arrived, so no origin captured) is left to the watchdog.
     */
    @Synchronized
    fun devicesPresent(present: Set<DeviceRef>) {
        sessions.values
            .filter { it.origin != null && it.origin !in present }
            .toList()                                       // snapshot before mutating the map in fail()
            .forEach { fail(it.seed.id, it, FailReason.DISCONNECTED) }
    }

    /** `SYSTEM_SIGNAL|id|function` — a standard signal this SYSTEM module will broadcast. */
    @Synchronized
    fun onSignal(line: String, origin: DeviceRef?) {
        val session = sessionFor(line) ?: return
        touch(session, origin)
        val function = line.split('|').getOrNull(2)?.trim().orEmpty()
        if (function.isNotEmpty()) session.signals += function
    }

    /** `SUBSCRIBE|id|function|…` — a signal this LISTENER module wants delivered. */
    @Synchronized
    fun onSubscribe(line: String, origin: DeviceRef?) {
        val session = sessionFor(line) ?: return
        touch(session, origin)
        Subscription.parse(line)?.let { session.subscriptions += it }
    }

    /** `MANIFEST|id|blocks|bytes` — an ACCESSORY's asset table-of-contents; sets the progress total. */
    @Synchronized
    fun onManifest(line: String, origin: DeviceRef?) {
        val session = sessionFor(line) ?: return
        touch(session, origin)
        val parts = line.split('|')
        session.declaredBlocks = parts.getOrNull(2)?.trim()?.toIntOrNull()
        session.totalBytes = parts.getOrNull(3)?.trim()?.toIntOrNull()
        emitProgress(session)
    }

    /** A completed asset block (header + raw bytes). Validate the CRC and length, then keep it —
     *  metadata for the record, payload bytes for the database to write at commit. */
    @Synchronized
    fun onBlock(block: Inbound.Block, origin: DeviceRef?) {
        val parts = block.header.split('|')            // BLOCK|id|name|length|crc
        val id = parts.getOrNull(1)?.trim().orEmpty()
        val session = sessions[id]
        if (session == null) {
            Log.w(TAG, "block for id $id with no open install session — ignored")
            return
        }
        touch(session, origin)
        val name = parts.getOrNull(2)?.trim().orEmpty()
        val declaredLen = parts.getOrNull(3)?.trim()?.toIntOrNull()
        val declaredCrc = parts.getOrNull(4)?.trim()?.let { runCatching { it.toLong(16) }.getOrNull() }

        val actualCrc = CRC32().apply { update(block.bytes) }.value
        val lengthOk = declaredLen == null || declaredLen == block.bytes.size
        val crcOk = declaredCrc != null && declaredCrc == actualCrc

        if (!lengthOk || !crcOk) {
            // A corrupt asset ends the install — now with a designed fail state, not a silent revert.
            Log.w(TAG, "block '$name' failed validation (lengthOk=$lengthOk crcOk=$crcOk) — install aborted for $id")
            fail(id, session, FailReason.CORRUPT)
            return
        }

        session.assets += InstalledAsset(name = name, bytes = block.bytes.size, crcOk = true)
        session.payloads += block.bytes
        session.receivedBytes += block.bytes.size
        emitProgress(session)
    }

    /** `INSTALL_END|id` — hand the accumulated session to the database and close it here. */
    @Synchronized
    fun onInstallEnd(line: String) {
        val id = idOf(line) ?: return
        val session = sessions.remove(id) ?: return
        session.watchdog?.cancel()
        _states.value = _states.value - id
        commit(session.record(), session.payloads)
    }

    /**
     * Arm the idle watchdog for a session. Runs until the session has been silent for
     * [IDLE_TIMEOUT_MS], then fails it STALLED. Re-checks rather than re-arms on activity: [touch]
     * just moves [InstallSession.lastActivity] forward, and the loop recomputes the remaining wait
     * after each delay — so a steady block transfer is never killed while it keeps arriving.
     */
    private fun arm(id: String, session: InstallSession) {
        session.watchdog = scope.launch {
            while (isActive) {
                val remaining = IDLE_TIMEOUT_MS - (System.currentTimeMillis() - session.lastActivity)
                if (remaining <= 0) {
                    fail(id, session, FailReason.STALLED)
                    return@launch
                }
                delay(remaining)
            }
        }
    }

    /** Mark a session alive (resets the idle watchdog) and capture where its declarations come from
     *  the first time we can, so a later disconnect can fail exactly this install. */
    private fun touch(session: InstallSession, origin: DeviceRef?) {
        session.lastActivity = System.currentTimeMillis()
        if (origin != null && session.origin == null) session.origin = origin
    }

    /** End a session unhappily: cancel its watchdog, drop it, and leave a [InstallState.Failed] badge.
     *  The identity guard makes it safe to call from the watchdog coroutine — a session already closed
     *  or replaced by a fresh install is left alone. */
    @Synchronized
    private fun fail(id: String, session: InstallSession, reason: FailReason) {
        if (sessions[id] !== session) return
        session.watchdog?.cancel()
        sessions.remove(id)
        _states.value = _states.value + (id to InstallState.Failed(reason))
        Log.w(TAG, "install failed for $id: $reason")
    }

    private fun sessionFor(line: String): InstallSession? {
        val id = idOf(line) ?: return null
        return sessions[id] ?: run {
            Log.w(TAG, "declaration for id $id with no open install session — ignored")
            null
        }
    }

    private fun idOf(line: String): String? =
        line.split('|').getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun emitProgress(session: InstallSession) =
        setState(session.seed.id, InstallState.Installing(session.progress()))

    private fun setState(id: String, state: InstallState) {
        _states.value = _states.value + (id to state)
    }

    private companion object {
        const val INSTALL = "INSTALL"
        const val TAG = "DashInstall"

        /** Silence for this long during a handshake ⇒ stalled. Generous enough to cover the gaps
         *  between blocks of a real asset transfer, short enough to catch a wedged module promptly. */
        const val IDLE_TIMEOUT_MS = 10_000L
    }
}

/** How an install ended unhappily (roadmap 1.4.14). Each renders a distinct, honest reason on the card. */
enum class FailReason { STALLED, DISCONNECTED, CORRUPT }

/**
 * What the Module Management card renders for a module the install desk is currently tracking.
 *  - [Installing] — a handshake in flight. [progress] is a 0..1 fraction for ACCESSORY (known once
 *    MANIFEST lands), or null for SYSTEM/LISTENER, whose few-line handshake shows an indeterminate bar.
 *  - [Failed] — the handshake ended unhappily and is waiting on the user to retry or dismiss.
 */
sealed interface InstallState {
    data class Installing(val progress: Float?) : InstallState
    data class Failed(val reason: FailReason) : InstallState
}

/**
 * One install in progress. Accumulates a module's declarations between `INSTALL` and `INSTALL_END` —
 * asset payload bytes included, held only until commit — then flattens into an [InstalledModule]
 * record. Only the desk touches it, under the desk's lock (except [watchdog]/[lastActivity], which the
 * watchdog coroutine reads, guarded by the same lock in [Install.fail]).
 */
private class InstallSession(val seed: DiscoveredModule) {
    val signals = mutableListOf<String>()
    val subscriptions = mutableListOf<Subscription>()
    val assets = mutableListOf<InstalledAsset>()
    val payloads = mutableListOf<ByteArray>()          // aligned index-for-index with [assets]

    var declaredBlocks: Int? = null
    var totalBytes: Int? = null
    var receivedBytes: Int = 0

    /** Where this install's declarations arrive from (roadmap 1.4.14) — captured on the first one,
     *  used to fail the session if that device leaves the bus. Null until the first declaration lands. */
    var origin: DeviceRef? = null

    /** When this session last heard anything, for the idle watchdog. */
    var lastActivity: Long = System.currentTimeMillis()
    var watchdog: Job? = null

    /** 0..1 once an ACCESSORY MANIFEST has given a byte total; null (indeterminate) until then and for
     *  SYSTEM/LISTENER, whose handshakes carry no total. */
    fun progress(): Float? {
        val total = totalBytes ?: return null
        if (total <= 0) return null
        return (receivedBytes.toFloat() / total).coerceIn(0f, 1f)
    }

    fun record() = InstalledModule(
        id = seed.id,
        type = seed.type,
        name = seed.name,
        description = seed.description,
        version = seed.version,
        signals = signals.toList(),
        subscriptions = subscriptions.toList(),
        assets = assets.toList()
    )
}
