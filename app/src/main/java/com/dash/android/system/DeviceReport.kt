package com.dash.android.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.dash.android.BuildConfig
import com.dash.android.DashApplication

/**
 * What DASH found on this device (roadmap 1.5.14) — the report block at the foot of About DASH.
 *
 * **Why it exists.** DASH is sideloaded onto hardware nobody has a list of: a tablet, a phone, an
 * Orange Pi, a no-name head unit off a marketplace. When a user says "it doesn't work on my box",
 * the first several questions are always the same ones, and every one of them is answerable from
 * here. It is the screen to ask for a copy of before asking anything else.
 *
 * It reports **facts and capabilities, not identity**: model and Android version, the screen, and
 * whether the privileged and hardware paths DASH cares about are open. Nothing here identifies a
 * person, and nothing needs a permission to read.
 *
 * The capability lines are the useful half. DASH is one codebase across three hardware tiers and
 * decides what it can do by probing at runtime, so "density control: unavailable" is not a fault —
 * it is Bronze behaving exactly as designed, and saying so plainly saves the user wondering.
 */
data class ReportLine(val label: String, val value: String)

fun buildDeviceReport(context: Context, dashTextScale: Float): List<ReportLine> {
    val res = context.resources
    val metrics = res.displayMetrics
    val config = res.configuration
    val pm = context.packageManager

    val widthDp = (metrics.widthPixels / metrics.density).toInt()
    val heightDp = (metrics.heightPixels / metrics.density).toInt()

    val densityCapable = (context.applicationContext as? DashApplication)?.densityCapable == true

    return listOf(
        ReportLine("DASH", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · built ${BuildConfig.BUILD_DATE}"),
        ReportLine("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        ReportLine("Device", "${Build.MANUFACTURER} ${Build.MODEL}"),
        ReportLine("Board", Build.DEVICE),
        ReportLine(
            "Screen",
            "$widthDp × $heightDp dp · ${metrics.densityDpi} dpi " +
                "(${metrics.widthPixels} × ${metrics.heightPixels} px)",
        ),
        ReportLine("Android font scale", "%.2f".format(config.fontScale)),
        ReportLine("DASH text scale", "%.2f".format(dashTextScale)),
        ReportLine("Default launcher", yesNo(isDefaultLauncher(context))),
        ReportLine("Density control", if (densityCapable) "Available" else "Unavailable (Bronze)"),
        ReportLine("USB host", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST))),
        ReportLine("Bluetooth", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))),
        ReportLine("WiFi", yesNo(pm.hasSystemFeature(PackageManager.FEATURE_WIFI))),
    )
}

/** The report as plain text, for the clipboard — the form it actually travels in. */
fun formatDeviceReport(lines: List<ReportLine>): String {
    val width = lines.maxOfOrNull { it.label.length } ?: 0
    return lines.joinToString("\n") { "${it.label.padEnd(width)}  ${it.value}" }
}

private fun yesNo(value: Boolean) = if (value) "Yes" else "No"

/** Whether DASH is the device's home app. `MainActivity` asks the same question for its banner; this
 *  copy takes a plain context, so the report does not need to reach for the activity. */
private fun isDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return info?.activityInfo?.packageName == context.packageName
}
