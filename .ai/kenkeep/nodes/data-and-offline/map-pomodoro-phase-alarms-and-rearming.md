---
type: map
title: Pomodoro phase alarms and rearming
description: >-
  The stored phase owns one exact alarm; rearming never advances it, including
  after a missed end.
tags:
  - pomodoro
  - alarms
  - offline
kk_schema_version: 3
kk_id: map-pomodoro-phase-alarms-and-rearming
kk_derived_from:
  - '9d79d56b-af9b-4d8b-8914-97ca84e70b1e:map:0'
kk_relates_to: []
kk_depends_on: []
kk_confidence: medium
---
The Pomodoro alarm integration lives in the `pomodoro/` package and follows `PomodoroSessionStore`. One `AlarmManager.setAlarmClock` targets the current phase's planned end; Next phase and End cancel the old alarm. The scheduler rebuilds that alarm from the stored session after boot, package replacement, clock or timezone changes, and an exact-alarm grant.

Alarm delivery signals the phase end without advancing the session. A missed planned end leaves the same phase in overtime until the user acts. The page can read `permissionDenied`; exact-alarm declarations use `SCHEDULE_EXACT_ALARM` through API 32 and `USE_EXACT_ALARM` from API 33.

<!-- kk:citations:start -->
# Citations

[1] 9d79d56b-af9b-4d8b-8914-97ca84e70b1e:map:0
<!-- kk:citations:end -->
