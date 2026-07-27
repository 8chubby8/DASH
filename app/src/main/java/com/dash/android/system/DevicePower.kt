package com.dash.android.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager

/**
 * Restarting and shutting down the whole device (roadmap 1.5.14).
 *
 * **Available on Silver and Gold only, and that is by Android's design, not DASH's.** Both actions
 * sit behind `signature|privileged` permissions — `REBOOT` and `SHUTDOWN` — which are granted to a
 * system app and cannot be granted to a sideloaded one however the manifest asks. There is no public
 * intent for either, and the only other routes are root, which the No-Root Constraint forbids DASH
 * from ever requiring or encouraging. So on Bronze these controls are **absent**, not disabled and
 * not erroring: capability detection, degrading to nothing at all.
 *
 * **Why this probes the permission rather than attempting the action.** The App Density probe works
 * by making the privileged call and seeing whether it throws, because there is no way to ask. Here
 * there is — and attempting a reboot to find out whether you are allowed to reboot is not a probe,
 * it is a reboot. A granted `signature|privileged` permission is an authoritative answer, so it is
 * the right one to trust.
 */
class DevicePower(private val context: Context) {

    val canRestart: Boolean get() = granted(Manifest.permission.REBOOT)

    /** `SHUTDOWN` is not in the public SDK, so it is named directly. */
    val canShutDown: Boolean get() = granted("android.permission.SHUTDOWN")

    /** Restart the device. Returns false if the call was refused, so the caller can stay quiet rather
     *  than claim something happened. */
    fun restart(): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java).reboot(null)
        true
    }.getOrDefault(false)

    /**
     * Shut the device down. `ACTION_REQUEST_SHUTDOWN` is a hidden system intent — the platform's own
     * power menu raises it — so the action is named directly. `EXTRA_KEY_CONFIRM` is false because
     * DASH has already taken its own confirmation; a second dialog on top of tap-to-confirm is one
     * dialog too many at the wheel.
     */
    fun shutDown(): Boolean = runCatching {
        val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
            .putExtra("android.intent.extra.KEY_CONFIRM", false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
