---
type: map
title: 'Pomodoro defaults: four preferences with offline persistence'
description: >-
  PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after
  refresh and keeps Toggl values on the phone.
tags:
  - settings
  - offline
  - api
  - pomodoro
kk_schema_version: 3
kk_id: map-pomodoro-defaults-four-preferences-with-offline-persistence
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:6'
kk_relates_to:
  - map-account-settings-source-and-account-time
  - practice-check-capabilities-not-server-version
kk_depends_on: []
kk_confidence: high
---
`app/src/main/java/com/github/e0ipso/tokkl/pomodoro/PomodoroDefaultsSetting.kt` owns the work length, short rest, long rest and long-rest frequency. Defaults are 25, 5, 15 and 4. Values are clamped to the accepted ranges. `PomodoroDefaultsStore` persists them and their unsent flag for cold starts and offline use; sign-out clears them.

With Tokkl preference support, a change shows immediately and POSTs only `pomodoro_focus_interval_in_minutes`, `pomodoro_break_interval_in_minutes`, `pomodoro_long_break_interval_in_minutes` and `pomodoro_long_break_every` to `/api/v9/me/preferences`. Failed writes remain unsent and retry on refresh. A refresh protects a local edit from an older response. Without support, including Toggl Track, values stay on the phone and no request is sent. The defaults component does not read or write `pomodoro_enabled` or `pomodoro_stop_timer_at_end`. The start form begins from these defaults and sends all four as per-session overrides to `POST /api/app/pomodoro`, so a later change to a default does not touch a running session. The Pomodoro page itself exists only with the `timerState` capability.

<!-- kk:related:start -->
# Related

- Related: [map-account-settings-source-and-account-time](map-account-settings-source-and-account-time.md)
- Related: [practice-check-capabilities-not-server-version](../api-and-security/practice-check-capabilities-not-server-version.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:6
<!-- kk:citations:end -->
