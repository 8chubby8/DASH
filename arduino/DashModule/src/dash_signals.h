/* ===========================================================================
   dash_signals.h — SUBSCRIBE defaults, mirrored from system_commands.md
   ---------------------------------------------------------------------------
   The firmware library fills a blank SUBSCRIBE rate/threshold from the signal's
   type before the line is sent (module-sdk.md §4c / §9) — never the builder,
   never DASH. Only **continuous** signals carry non-blank defaults; boolean,
   multi-state and event-only signals default to blank (event-driven delivery),
   which is also the safe fallback for any signal not listed here. So the table
   needs only the continuous signals.

   > SOURCE OF TRUTH: system_commands.md. Keep this list in step with the
   > "Default rate / Default threshold" columns there. (A generator that emits
   > this header from system_commands.md is a future nicety — with eight rarely-
   > changing entries, hand-mirroring with this note is enough for now.)

   Locked at DASH 1.4.15.
   =========================================================================== */
#ifndef DASH_SIGNALS_H
#define DASH_SIGNALS_H

#include <string.h>

struct DashContinuousDefault {
  const char* name;
  const char* rate;
  const char* threshold;
};

// The eight continuous standard signals and their default rate + threshold.
static const DashContinuousDefault DASH_CONTINUOUS[] = {
    {"vehicle_speed",  "5hz",   "1"},
    {"steering_angle", "20hz",  "2"},
    {"engine_rpm",     "5hz",   "50"},
    {"fuel_level",     "0.2hz", "1"},
    {"coolant_temp",   "0.5hz", "1"},
    {"ambient_temp",   "0.1hz", "0.5"},
    {"ambient_light",  "1hz",   "10"},
    {"charge_level",   "0.1hz", "1"},
};
static const int DASH_CONTINUOUS_NUM =
    (int)(sizeof(DASH_CONTINUOUS) / sizeof(DASH_CONTINUOUS[0]));

// The continuous-signal defaults for `name`, or nullptr if `name` is not a
// continuous signal (⇒ blank defaults / event-driven delivery).
inline const DashContinuousDefault* dashContinuous(const char* name) {
  for (int i = 0; i < DASH_CONTINUOUS_NUM; i++) {
    if (strcmp(DASH_CONTINUOUS[i].name, name) == 0) return &DASH_CONTINUOUS[i];
  }
  return nullptr;
}

#endif  // DASH_SIGNALS_H
