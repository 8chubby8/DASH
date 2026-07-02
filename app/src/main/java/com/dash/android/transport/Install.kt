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
 * in-progress [InstallSession] per module and commits it to an [InstalledModule] only on the close.
 *
 * **Opened by us, fed by the module, closed by the module.** [install] opens a session (seeded from the
 * module's discovery identity) and sends `INSTALL`; the controller then feeds each declaration in via
 * [onSignal] / [onSubscribe] / [onManifest] / [onBlock]; [onInstallEnd] validates and commits.
 *
 * **Keyed by id, holds many.** Sessions live in a map keyed by module id, because every declaration
 * carries its id (arduino.md §2). The UI drives one install at a time, but the wire is id-addressed, so
 * the frame naturally supports more than one in flight — build the frame for many, drive it as one.
 *
 * **Well-mannered.** A declaration for an id with no open session (a stray, or a message type a later
 * build will handle) is logged and ignored, never fatal.
 *
 * **Scope (1.4.4).** Happy path plus the one unavoidable failure: a block whose CRC or length does not
 * match aborts that session cleanly (record dropped, pane returns to Install). There is deliberately no
 * timeout — a handshake that never sends `INSTALL_END` simply stays pending; the timeout and a designed
 * failure surface are later work. Records are session-only (disk is 1.4.5); ACTIVATE and live data are
 * 1.4.6+, so nothing here starts a module sending.
 */
class Install(
    private val send: (String) -> Unit,
    private val identityOf: (String) -> DiscoveredModule?
) {
    private val sessions = mutableMapOf<String, InstallSession>()

    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    /** Per-module UI state, keyed by id. Absent ⇒ merely discovered (show Install). */
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()

    /** User pressed Install on a discovered module: open a session and begin the handshake. */
    @Synchronized
    fun install(id: String) {
        if (sessions.containsKey(id)) return               // already installing
        val seed = identityOf(id) ?: return                // can't install an identity we don't hold
        sessions[id] = InstallSession(seed)
        setState(id, InstallState.Installing(progress = null))
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

    /** A completed asset block (header + raw bytes). Validate the CRC and length, then record it. */
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
            // The one unavoidable failure path in 1.4.4: corrupt asset → abort the whole install.
            Log.w(TAG, "block '$name' failed validation (lengthOk=$lengthOk crcOk=$crcOk) — install aborted for $id")
            abort(id)
            return
        }

        session.assets += InstalledAsset(name = name, bytes = block.bytes.size, crcOk = true)
        session.receivedBytes += block.bytes.size
        emitProgress(session)
    }

    /** `INSTALL_END|id` — commit the accumulated session to an installed record. */
    @Synchronized
    fun onInstallEnd(line: String) {
        val id = idOf(line) ?: return
        val session = sessions.remove(id) ?: return
        setState(id, InstallState.Installed(session.commit()))
    }

    /** Drop a module's record (session-only in 1.4.4). No wire message: the module was never activated
     *  and persists nothing (arduino.md §6), so DASH forgetting is the whole of an uninstall for now. */
    @Synchronized
    fun uninstall(id: String) {
        sessions.remove(id)
        _states.value = _states.value - id
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
        setState(session.seed.id, InstallState.Installing(session.progress()))

    private fun setState(id: String, state: InstallState) {
        _states.value = _states.value + (id to state)
    }

    private companion object {
        const val INSTALL = "INSTALL"
        const val TAG = "DashInstall"
    }
}

/**
 * One install in progress. Accumulates a module's declarations between `INSTALL` and `INSTALL_END`,
 * then [commit]s them into an [InstalledModule]. Only the desk touches it, under the desk's lock.
 */
private class InstallSession(val seed: DiscoveredModule) {
    val signals = mutableListOf<String>()
    val subscriptions = mutableListOf<Subscription>()
    val assets = mutableListOf<InstalledAsset>()

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

    fun commit() = InstalledModule(
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
