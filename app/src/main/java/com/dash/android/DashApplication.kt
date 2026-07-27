package com.dash.android

import android.app.Application
import com.dash.android.density.DensityManager
import com.dash.android.transport.DashController
import com.dash.android.transport.TransportManager

/**
 * The DASH application object — and the owner of the transport stack (roadmap 1.5.11).
 *
 * **Why this class exists.** The bus used to be created inside `MainScreen` with `remember { }`, which
 * ties its lifetime to a *composable* rather than to DASH. Any Android activity recreation therefore
 * tore the whole stack down and rebuilt it: every socket dropped, every module forced to reconnect,
 * `lastSeen` wiped so the installed list fell back to DORMANT, and any install handshake in flight
 * died mid-declaration. It was caught on the bench (2026-07-27) as a second `client c1` in the WiFi
 * log — a fresh connection counter, which only a fresh [TransportManager] can produce.
 *
 * That is not a rare event. `MainActivity` handles orientation and resize itself, but **not**
 * `density`, `uiMode`, `fontScale` or `locale` — so a dark-mode switch at dusk, a font-scale change,
 * or the system reclaiming the activity while the user is in Maps all recreate it. DASH's own App
 * Density feature changes `density` directly. In a car that was a silent, unannounced restart of the
 * entire module bus, mid-drive.
 *
 * **Why the Application and not a ViewModel.** A `ViewModel` survives a configuration-change
 * recreation but is cleared when the activity genuinely finishes — and DASH is a *launcher*, so the
 * user opening an app in the viewport backgrounds it by design. The bus must outlive the screen. The
 * process is the honest scope: one device, one bus, alive as long as DASH is.
 *
 * **Why nothing calls stop().** Process lifetime *is* the intended lifetime, so there is no teardown
 * path to get wrong — the old `onDispose` that stopped the stack is exactly what made a transient UI
 * event kill the bus. Android reclaiming the process takes the sockets with it.
 *
 * If real hardware ever shows the process itself being killed mid-drive, the next step is a foreground
 * service; that is a deliberate escalation, not a default.
 */
class DashApplication : Application() {

    /** The pipes. Given the application context — the previous activity context was also a leak
     *  waiting to happen, since the stack outlives any one activity. */
    lateinit var transport: TransportManager
        private set

    /** The brain above the pipes: routes inbound messages and holds the discovery / install /
     *  reconciliation desks the settings tabs read. */
    lateinit var controller: DashController
        private set

    /**
     * Whether this installation can drive Android's display density — probed **once**, here, and
     * shared by everything that asks (roadmap 1.5.14).
     *
     * It is cached rather than re-probed because a probe is a real privileged call, not a query, and
     * the answer cannot change while the process lives — DASH is or is not a system app. See
     * [DensityManager.checkCapability] for why that call was also made non-destructive in 1.5.14:
     * About asking the question in passing must never disturb a density the user has set.
     */
    val densityCapable: Boolean by lazy { DensityManager(this).checkCapability() }

    override fun onCreate() {
        super.onCreate()
        transport = TransportManager(this)
        controller = DashController(transport, this)
        // Started here rather than from the first screen, so a process spawned without UI (the boot
        // receiver on a head unit) still brings the bus up.
        transport.start()
        controller.start()
    }
}
