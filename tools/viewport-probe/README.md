# Viewport probe (roadmap 1.7.1 test)

A throwaway app used on 2026-10-01 to test, from adb, the ways DASH could put another app inside the
viewport. It is not part of DASH. Driven with intent extras, for example:

    adb shell am start -n dash.probe/.ProbeActivity --es act bounds --es pkg com.google.android.apps.maps \
        --ei l 300 --ei t 400 --ei r 1344 --ei b 2600     # ordinary-app bounded launch (Windowed rung)
    adb shell appops set dash.probe SYSTEM_ALERT_WINDOW allow
    adb shell am start -n dash.probe/.ProbeActivity --es act overlay --ei top 200 --ei left 500   # Draw on top
    adb shell am start -n dash.probe/.ProbeActivity --es act overlay_off

The shell/Shizuku rung was tested directly with `am start --windowingMode 5` and `am task resize`.
Results are in changelog.md, Version 1.7.1. Build it as its own Gradle project (copy DASH's gradle
wrapper and gradle.properties alongside).
