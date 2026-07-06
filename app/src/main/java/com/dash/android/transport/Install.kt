package com.dash.android.transport

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * covers only handshakes under way this session; a completed session is handed to [commit] — wired to
 * the database, which persists it and owns it from then on. Asset payload bytes are held in the session
 * for exactly that hop: validated here, written to disk by the database, never kept in memory after.
 *
 * **Keyed by id, holds many.** Sessions live in a map keyed by module id, because every declaration
 * carries its id (arduino.md §2). The UI drives one install at a time, but the wire is id-addressed, so
 * the frame naturally supports more than one in flight — build the frame for many, drive it as one.
 *
 * **Well-mannered.** A declaration for an id with no open session (a stray, or a message type a later
 * build will handle) is logged and ignored, never fatal.
 *
 * **Scope.** Happy path plus the one unavoidable failure: a block whose CRC or length does not match
 * aborts that session cleanly (record dropped, pane returns to Install). There is deliberately no
 * timeout — a handshake that never sends `INSTALL_END` simply stays pending; the timeout and a designed
 * failure surface are later work. ACTIVATE and live data are 1.4.6+, so nothing here starts a module
 * sending — an installed module is dormant.
 */
class Install(
    private val send: (String) -> Unit,
    private val identityOf: (String) -> DiscoveredModule?,
    private val isInstalled: (String) -> Boolean,
    private val commit: (InstalledModule, List<ByteArray>) -> Unit
) {
    private val sessions = mutableMapOf<String, InstallSession>()

    private val _states = MutableStateFlow<Map<String, Installing>>(emptyMap())
    /** Handshakes under way, keyed by id. Absent ⇒ not installing (installed lives in the database). */
    val states: StateFlow<Map<String, Installing>> = _states.asStateFlow()

    /** User pressed Install on a discovered module: open a session and begin the handshake. */
    @Synchronized
    fun install(id: String) {
        if (sessions.containsKey(id)) return               // already installing
        if (isInstalled(id)) return                        // already installed — uninstall first
        val seed = identityOf(id) ?: return                // can't install an identity we don't hold
        sessions[id] = InstallSession(seed)
        setState(id, Installing(progress = null))
        send("$INSTALL|$id")
    }

    /** `SYSTEM_SIGNAL|id|function` — a standard signal this SYSTEM module will broadcast. */
    @Synchronized
    fun onSignal(line: String) {
        val session = sessionFor(line) ?: return
        val function = line.split('|').getOrNull(2)?.trim().orEmpty()
        if (function.isNotEmpty()) session.signals += function
    }

    /** `SUBSCRIBE|id|function|…` — a signal this LISTENER module wants delivered. */
    @Synchronized
    fun onSubscribe(line: String) {
        val session = sessionFor(line) ?: return
        Subscription.parse(line)?.let { session.subscriptions += it }
    }

    /** `MANIFEST|id|blocks|bytes` — an ACCESSORY's asset table-of-contents; sets the progress total. */
    @Synchronized
    fun onManifest(line: String) {
        val session = sessionFor(line) ?: return
        val parts = line.split('|')
        session.declaredBlocks = parts.getOrNull(2)?.trim()?.toIntOrNull()
        session.totalBytes = parts.getOrNull(3)?.trim()?.toIntOrNull()
        emitProgress(session)
    }

    /** A completed asset block (header + raw bytes). Validate the CRC and length, then keep it —
     *  metadata for the record, payload bytes for the database to write at commit. */
    @Synchronized
    fun onBlock(block: Inbound.Block) {
        val parts = block.header.split('|')            // BLOCK|id|name|length|crc
        val id = parts.getOrNull(1)?.trim().orEmpty()
        val session = sessions[id]
        if (session == null) {
            Log.w(TAG, "block for id $id with no open install session — ignored")
            return
        }
        val name = parts.getOrNull(2)?.trim().orEmpty()
        val declaredLen = parts.getOrNull(3)?.trim()?.toIntOrNull()
        val declaredCrc = parts.getOrNull(4)?.trim()?.let { runCatching { it.toLong(16) }.getOrNull() }

        val actualCrc = CRC32().apply { update(block.bytes) }.value
        val lengthOk = declaredLen == null || declaredLen == block.bytes.size
        val crcOk = declaredCrc != null && declaredCrc == actualCrc

        if (!lengthOk || !crcOk) {
            // The one unavoidable failure path: corrupt asset → abort the whole install.
            Log.w(TAG, "block '$name' failed validation (lengthOk=$lengthOk crcOk=$crcOk) — install aborted for $id")
            abort(id)
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
        _states.value = _states.value - id
        commit(session.record(), session.payloads)
    }

    /** Abort an in-progress install: drop the session and return the pane to its discovered state. */
    private fun abort(id: String) {
        sessions.remove(id)
        _states.value = _states.value - id
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
        setState(session.seed.id, Installing(session.progress()))

    private fun setState(id: String, state: Installing) {
        _states.value = _states.value + (id to state)
    }

    private companion object {
        const val INSTALL = "INSTALL"
        const val TAG = "DashInstall"
    }
}

/**
 * A handshake under way — the per-module state the Module Management screen renders as a progress bar.
 * [progress] is a 0..1 fraction for ACCESSORY (known once MANIFEST lands), or null for SYSTEM/LISTENER,
 * whose few-line handshake shows an indeterminate bar.
 */
data class Installing(val progress: Float?)

/**
 * One install in progress. Accumulates a module's declarations between `INSTALL` and `INSTALL_END` —
 * asset payload bytes included, held only until commit — then flattens into an [InstalledModule]
 * record. Only the desk touches it, under the desk's lock.
 */
private class InstallSession(val seed: DiscoveredModule) {
    val signals = mutableListOf<String>()
    val subscriptions = mutableListOf<Subscription>()
    val assets = mutableListOf<InstalledAsset>()
    val payloads = mutableListOf<ByteArray>()          // aligned index-for-index with [assets]

    var declaredBlocks: Int? = null
    var totalBytes: Int? = null
    var receivedBytes: Int = 0

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
