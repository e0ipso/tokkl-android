---
type: map
title: Pomodoro phase alarms and rearming
description: >-
  One exact alarm per server phase, set from its planned end and the clock
  offset. A state from any device silences it; rearming never advances a phase.
tags:
  - pomodoro
  - alarms
  - sync
kk_schema_version: 3
kk_id: map-pomodoro-phase-alarms-and-rearming
kk_derived_from:
  - '9d79d56b-af9b-4d8b-8914-97ca84e70b1e:map:0'
kk_relates_to:
  - map-timer-state-holder-and-sync-service
kk_depends_on: []
kk_confidence: medium
---
The alarm integration lives in `pomodoro/`. It follows the account's held timer state (see the holder node), not a session of the phone's own. `PomodoroPhaseAlarm` sets one `AlarmManager.setAlarmClock` at the phase's `planned_end` minus the held clock offset, keyed by `phase.start`, and every state change calls it again under one lock. A state that names another phase cancels the old alarm, stops its ring and arms the new phase. A state with no `pomodoro` cancels the alarm, stops the ringing service and closes the full-screen alarm, whichever device made the change. An alarm delivered for a phase other than the held one rings nothing.

`PomodoroAlarmRearmReceiver` sets the alarm again from the stored state after boot, package replacement, clock or timezone changes and an exact-alarm grant. After a time change it uses the stored offset until the next state arrives. A missed end rings at once and leaves the same phase in overtime until a device confirms it; rearming never advances a phase. The same receiver also starts the timer sync service after boot and update.

The page reads `permissionDenied`; exact-alarm declarations use `SCHEDULE_EXACT_ALARM` through API 32 and `USE_EXACT_ALARM` from API 33. Where no exact alarm can be set, a planned end already past still rings straight away. The ringing service is a separate `mediaPlayback` service from the `specialUse` sync service. Next phase and End session on the alarm go to the server and need a connection; offline, the ring continues until a state arrives.

<!-- kk:citations:start -->
# Citations

[1] 9d79d56b-af9b-4d8b-8914-97ca84e70b1e:map:0
<!-- kk:citations:end -->
