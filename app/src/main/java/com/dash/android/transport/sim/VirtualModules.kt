package com.dash.android.transport.sim

import com.dash.android.transport.Inbound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.zip.CRC32
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The virtual modules behind the [SimulatedModuleTransport]. Each is firmware in all but silicon:
 * it follows arduino.md to the letter — boots SILENT, answers `DISCOVER` with `HELLO`, runs its
 * type's install handshake, wakes only on `ACTIVATE`, `ROGER`s every command, and sends live data
 * only while active (§6). They deliberately have no access to anything a real board couldn't see:
 * lines in, lines out, nothing else.
 */
abstract class VirtualModule(
    protected val scope: CoroutineScope,
    private val reply: (Inbound) -> Unit
) {
    abstract val id: String
    protected abstract val hello: String

    private val _active = MutableStateFlow(false)
    /** Mirrors the firmware's one bit of live state: SILENT (false) or ACTIVE (true). The State
     *  Inspector reads this to enable its poke buttons — a bench convenience, not a wire message. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    protected fun sendLine(line: String) = reply(Inbound.Line(line))
    protected fun sendBlock(header: String, bytes: ByteArray) = reply(Inbound.Block(header, bytes))

    /** One line from DASH. The dispatch mirrors the sketches' handleLine(). */
    @Synchronized
    fun onLine(line: String) {
        val parts = line.split('|')
        val type = parts.getOrNull(0)?.trim().orEmpty()
        // DISCOVER is the one message with no id — everyone answers (§4).
        if (type == "DISCOVER") { sendLine(hello); return }
        // Every other command names its target. Not for us? Stay quiet (§5).
        if (parts.getOrNull(1)?.trim() != id) return
        when (type) {
            "INSTALL" -> doInstall()
            "ACTIVATE" -> {
                _active.value = true
                sendLine("ROGER|$id|activate")
                onActivated()
            }
            "DEACTIVATE" -> {
                _active.value = false
                sendLine("ROGER|$id|deactivate")
                onDeactivated()
            }
            // A user operated one of this module's panel controls (§4, roadmap 1.4.9). value is absent
            // for a momentary control. A module that has no panel (SYSTEM/LISTENER) ignores it.
            "ACTION" -> if (isModuleActive) onAction(parts.getOrNull(2)?.trim().orEmpty(), parts.getOrNull(3)?.trim())
            // Unknown TYPE: ignore — forward compatibility (§2).
        }
    }

    /** Power yanked — the pretend lead unplugged. Instant silence, no goodbye, state lost (§6). */
    @Synchronized
    fun powerOff() {
        _active.value = false
        onDeactivated()
    }

    protected val isModuleActive get() = _active.value

    protected abstract fun doInstall()
    protected abstract fun onActivated()
    protected abstract fun onDeactivated()

    /** A panel control was operated (roadmap 1.4.9). Only ACCESSORY modules own a panel, so the default
     *  is to do nothing — a SYSTEM or LISTENER module never has controls to operate. */
    protected open fun onAction(control: String, value: String?) = Unit
}

/**
 * The pretend car — a virtual SYSTEM module. Its physical inputs (a door switch, a gear selector,
 * a headlight stalk, a steering-wheel button) don't exist, so the State Inspector's poke methods
 * stand in for the physical world; everything from the input inward is honest firmware behaviour.
 *
 * Signals, chosen to exercise all three §5a behaviours:
 *  - `vehicle_speed`, `engine_rpm` — continuous, store-only; streamed every [STREAM_MS] while active.
 *  - `door_driver_open` — boolean, store-and-event; flips at random (plus the poke) — Roger's
 *    "random data change like a door open".
 *  - `headlights_on`, `gear_position` — poke-driven boolean and multi-state.
 *  - `media_next` — event-only, no value field on the wire; poke-driven.
 *
 * §4b throughout: on ACTIVATE it dumps every stateful signal's current value; every [HEARTBEAT_MS]
 * it re-sends them all, changed or not. Change detection is DASH's job, not this module's.
 */
class VirtualSystemModule(
    scope: CoroutineScope,
    reply: (Inbound) -> Unit
) : VirtualModule(scope, reply) {

    override val id = "0000DA5E0001"   // assigned id (§3 no-MAC fallback), DA5E = simulated bench
    override val hello =
        "HELLO|$id|SYSTEM|Sim Vehicle|Simulated SYSTEM module - the pretend car|v1.0"

    // The pretend car's current state — what real firmware would read off its input pins.
    private var speedPhase = 0.0
    private var speed = 0
    private var rpm = 0
    private var doorOpen = false
    private var headlights = false
    private var gear = "park"

    private var liveJob: Job? = null

    override fun doInstall() {
        scope.launch {
            // One SYSTEM_SIGNAL per signal this module sends (§7) — including the momentary control.
            delay(WIRE_PAUSE_MS)
            listOf(
                "vehicle_speed", "engine_rpm", "door_driver_open",
                "headlights_on", "gear_position", "media_next"
            ).forEach { sendLine("SYSTEM_SIGNAL|$id|$it") }
            delay(WIRE_PAUSE_MS)
            sendLine("INSTALL_END|$id")
        }
    }

    override fun onActivated() {
        dumpState()                          // §4b: the on-activation full dump
        liveJob?.cancel()
        liveJob = scope.launch {
            var lastHeartbeat = System.currentTimeMillis()
            var nextDoorFlip = System.currentTimeMillis() + randomDoorInterval()
            while (isActive) {
                delay(STREAM_MS)
                if (!isModuleActive) break
                tickDrive()
                broadcast("vehicle_speed", speed.toString())
                broadcast("engine_rpm", rpm.toString())
                val now = System.currentTimeMillis()
                if (now >= nextDoorFlip) {   // the random door — nobody pressed anything
                    nextDoorFlip = now + randomDoorInterval()
                    doorOpen = !doorOpen
                    broadcast("door_driver_open", doorOpen.toString())
                }
                if (now - lastHeartbeat >= HEARTBEAT_MS) {   // §4b: periodic full state report
                    lastHeartbeat = now
                    dumpState()
                }
            }
        }
    }

    override fun onDeactivated() {
        liveJob?.cancel()
        liveJob = null
    }

    /* ---- the State Inspector's pokes: the pretend physical world ---- */

    @Synchronized fun pokeDoor() {
        if (!isModuleActive) return
        doorOpen = !doorOpen
        broadcast("door_driver_open", doorOpen.toString())
    }

    @Synchronized fun pokeGear() {
        if (!isModuleActive) return
        gear = when (gear) { "park" -> "reverse"; "reverse" -> "neutral"; "neutral" -> "drive"; else -> "park" }
        broadcast("gear_position", gear)
    }

    @Synchronized fun pokeHeadlights() {
        if (!isModuleActive) return
        headlights = !headlights
        broadcast("headlights_on", headlights.toString())
    }

    @Synchronized fun pokeMediaNext() {
        if (!isModuleActive) return
        sendLine("BROADCAST|$id|media_next")   // event-only: no value field — its absence is the signal
    }

    /* ---- internals ---- */

    /** A gentle drive: speed sweeps 0..90 km/h on a slow sine; rpm loosely follows. */
    private fun tickDrive() {
        speedPhase += 0.05
        speed = (((sin(speedPhase) + 1) / 2) * 90).roundToInt()
        rpm = 750 + speed * 47
    }

    /** §4b state report: every stateful signal's current value, changed or not. Momentary controls
     *  (`media_next`) have no state — never dumped, never heartbeated. */
    private fun dumpState() {
        broadcast("vehicle_speed", speed.toString())
        broadcast("engine_rpm", rpm.toString())
        broadcast("door_driver_open", doorOpen.toString())
        broadcast("headlights_on", headlights.toString())
        broadcast("gear_position", gear)
    }

    private fun broadcast(function: String, value: String) =
        sendLine("BROADCAST|$id|$function|$value")

    private fun randomDoorInterval() = Random.nextLong(8_000, 20_000)

    private companion object {
        const val STREAM_MS = 500L
        const val HEARTBEAT_MS = 5_000L
        const val WIRE_PAUSE_MS = 60L
    }
}

/**
 * A virtual ACCESSORY. Exercises the MANIFEST/BLOCK install path from the simulator side and, while
 * active, sends a `REPORT` every 2 s. Until 1.4.7 those reports went nowhere but the wire tap (proving
 * accessory traffic must *not* reach the sourceless system store); from 1.4.9 they are routed into the
 * per-module data store and appear in the State Inspector's reports pane.
 *
 * It is also the 1.4.9 round-trip rig for `ACTION`. It declares one panel control (`sim_button`) in its
 * layout asset, and the State Inspector's "ACTION ▸" button stands in for a user pressing it (there is
 * no panel yet to press). On receiving `ACTION|id|sim_button|…` it does what a real accessory would —
 * it acts, then reports its new state back — so the whole loop is visible: press → `ACTION` out on the
 * wire → the module reacts → `REPORT|id|button_presses|N` back in → the count climbs in the reports pane.
 */
class VirtualAccessoryModule(
    scope: CoroutineScope,
    reply: (Inbound) -> Unit
) : VirtualModule(scope, reply) {

    override val id = "0000DA5E0002"
    override val hello =
        "HELLO|$id|ACCESSORY|Sim Accessory|Simulated ACCESSORY - install path and REPORT traffic|v1.0"

    private var reportJob: Job? = null
    private var reportCount = 0L
    private var buttonPresses = 0L

    // Two small assets — enough to run the MANIFEST/BLOCK/CRC machinery, tiny enough to read here.
    private val icon = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">
          <rect x="4" y="4" width="40" height="40" rx="9" fill="#455a64"/>
          <text x="24" y="31" font-size="18" text-anchor="middle" fill="#ffffff">S</text>
        </svg>
    """.trimIndent().toByteArray()

    private val layout = """
        # Simulated accessory test layout (provisional format, arduino.md section 11)
        TEST_LAYOUT|panel_background
        TEST_OVERLAY|text|test_counter|0.50|0.50|@barText|18
        TEST_CONTROL|button|sim_button|0.50|0.80
    """.trimIndent().toByteArray()

    override fun doInstall() {
        scope.launch {
            delay(WIRE_PAUSE_MS)
            val assets = listOf("icon" to icon, "panel_layout" to layout)
            sendLine("MANIFEST|$id|${assets.size}|${assets.sumOf { it.second.size }}")
            assets.forEach { (name, bytes) ->
                delay(WIRE_PAUSE_MS)   // lets the progress bar exist for more than one frame
                val crc = CRC32().apply { update(bytes) }.value.toString(16)   // lowercase hex, 1.4.4 format
                sendBlock("BLOCK|$id|$name|${bytes.size}|$crc", bytes)
            }
            delay(WIRE_PAUSE_MS)
            sendLine("INSTALL_END|$id")
        }
    }

    override fun onActivated() {
        reportJob?.cancel()
        reportJob = scope.launch {
            while (isActive) {
                delay(REPORT_MS)
                if (!isModuleActive) break
                sendLine("REPORT|$id|test_counter|${reportCount++}")
            }
        }
    }

    override fun onDeactivated() {
        reportJob?.cancel()
        reportJob = null
    }

    /** The user pressed a panel control. This module owns one — `sim_button`. Like a real accessory it
     *  acts on the press and reports its resulting state back, so the ACTION→REPORT loop closes on the
     *  wire. Any other control id is unknown to this panel and ignored. */
    override fun onAction(control: String, value: String?) {
        if (control != CONTROL_ID) return
        sendLine("REPORT|$id|button_presses|${++buttonPresses}")
    }

    private companion object {
        const val REPORT_MS = 2_000L
        const val WIRE_PAUSE_MS = 80L
        const val CONTROL_ID = "sim_button"
    }
}

/**
 * A virtual LISTENER (roadmap 1.4.8 test rig) — firmware in all but silicon, like its siblings. It
 * subscribes, at install, to every standard signal the [VirtualSystemModule] puts out, so with both
 * plugged in the whole stream path is visible on the Serial Monitor: the Sim Vehicle `BROADCAST`s a
 * signal → it lands in the sourceless core → the streams desk delivers it straight back out as
 * `LISTEN|thisId|function|value`, gated, throttled and deadbanded per this module's subscriptions —
 * to a module that never knew where the value came from (§9's headline: sourceless mediation).
 *
 * The subscriptions are chosen to exercise every path (§9):
 *  - `vehicle_speed` — continuous, **rate-capped at 1 Hz with a deadband of 2**, against a 2 Hz source:
 *    the cap makes the leading-and-trailing throttle visible (roughly half the `BROADCAST`s pass), and
 *    the deadband holds moves smaller than 2 km/h.
 *  - `engine_rpm` — continuous, **rate-capped at 1 Hz**, no deadband.
 *  - `door_driver_open`, `headlights_on`, `gear_position` — plain on-change (a boolean/multi-state
 *    "stream with no cap"): delivered the instant they change, and on the 5 s heartbeat.
 *  - `media_next` — event-only: a valueless `LISTEN` on each fire, no heartbeat, no dump.
 *
 * Being a LISTENER, it declares nothing to send and holds no live loop — it wakes on `ACTIVATE`,
 * `ROGER`s it (base class), and simply receives. A real one would switch a relay on what it hears; the
 * sim's proof is the `LISTEN` traffic on the wire tap, so it does not need to echo receipt.
 */
class VirtualListenerModule(
    scope: CoroutineScope,
    reply: (Inbound) -> Unit
) : VirtualModule(scope, reply) {

    override val id = "0000DA5E0003"
    override val hello =
        "HELLO|$id|LISTENER|Sim Relay|Simulated LISTENER - subscribes to the Sim Vehicle signals|v1.0"

    override fun doInstall() {
        scope.launch {
            // One SUBSCRIBE per signal wanted (§7/§9). Trailing fields set here stand in for what the
            // firmware library will fill from the signal's type once it exists — DASH records them
            // verbatim and honours them at delivery; it holds no defaults of its own.
            delay(WIRE_PAUSE_MS)
            listOf(
                "SUBSCRIBE|$id|vehicle_speed|1hz|2",   // rate-capped + deadband
                "SUBSCRIBE|$id|engine_rpm|1hz",        // rate-capped
                "SUBSCRIBE|$id|door_driver_open",      // plain on-change
                "SUBSCRIBE|$id|headlights_on",         // plain on-change
                "SUBSCRIBE|$id|gear_position",         // plain on-change (multi-state)
                "SUBSCRIBE|$id|media_next"             // event-only
            ).forEach { sendLine(it) }
            delay(WIRE_PAUSE_MS)
            sendLine("INSTALL_END|$id")
        }
    }

    // A LISTENER sends nothing once active — it only receives. Nothing to start or stop.
    override fun onActivated() = Unit
    override fun onDeactivated() = Unit

    private companion object {
        const val WIRE_PAUSE_MS = 60L
    }
}
