# DASH System Commands — Tentative Vocabulary

> Working document. Incomplete. Subject to change.

**Behaviour key:**
- **store + event** — value saved to state store, event fired on change. Interface can react immediately or query store.
- **store only** — value saved to state store silently. Interface reads directly from store. No event fired.
- **event only** — momentary. Fired and forgotten. Nothing persisted.

---

## Doors & Access

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| door_driver_open | boolean | true / false | store + event |
| door_passenger_open | boolean | true / false | store + event |
| door_rear_left_open | boolean | true / false | store + event |
| door_rear_right_open | boolean | true / false | store + event |
| door_boot_open | boolean | true / false | store + event |
| door_boot_glass_open | boolean | true / false | store + event |
| door_bonnet_open | boolean | true / false | store + event |
| door_fuel_flap_open | boolean | true / false | store + event |
| charge_port_open | boolean | true / false | store + event |

---

## Windows

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| window_driver_up | boolean | true / false | store + event |
| window_passenger_up | boolean | true / false | store + event |

---

## Lights

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| headlights_on | boolean | true / false | store + event |
| fog_lights_front_on | boolean | true / false | store + event |
| fog_lights_rear_on | boolean | true / false | store + event |
| hazard_lights_on | boolean | true / false | store + event |
| interior_lights_on | boolean | true / false | store + event |
| indicator_left_on | boolean | true / false | store + event |
| indicator_right_on | boolean | true / false | store + event |

---

## Wipers

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| wipers_front_state | multi-state | off / intermittent / low / high | store + event |
| wipers_rear_on | boolean | true / false | store + event |

---

## Vehicle State

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| ignition_state | multi-state | off / accessory / on | store + event |
| handbrake_on | boolean | true / false | store + event |
| gear_position | multi-state | park / reverse / neutral / drive / 1 / 2 / 3 | store + event |
| vehicle_speed | continuous | km/h | store only |
| steering_angle | continuous | degrees | store only |
| engine_rpm | continuous | rpm | store only |
| fuel_level | continuous | percentage 0-100 | store only |
| coolant_temp | continuous | degrees C | store only |
| ambient_temp | continuous | degrees C | store only |
| ambient_light | continuous | lux | store only |

---

## Safety

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| seatbelt_driver_fastened | boolean | true / false | store + event |
| seatbelt_passenger_fastened | boolean | true / false | store + event |

---

## EV / Charging

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| charge_connected | boolean | true / false | store + event |
| charge_level | continuous | percentage 0-100 | store only |

---

## Media

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| media_muted | boolean | true / false | store + event |
| media_source | string | source name | store + event |

---

## Controls

| Signal | Type | Values | Behaviour |
|--------|------|--------|-----------|
| media_play_pause | event | — | event only |
| media_next | event | — | event only |
| media_prev | event | — | event only |
| media_volume_up | event | — | event only |
| media_volume_down | event | — | event only |
| voice_activate | event | — | event only |
| button_home_pressed | event | — | event only |
