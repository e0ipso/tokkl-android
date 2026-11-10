---
type: map
title: Capability probes and persisted feature flags
description: >-
  Trash and timer probes distinguish absent endpoints from temporary failures
  and load flags before early callers.
tags:
  - api
  - compatibility
  - offline
kk_schema_version: 3
kk_id: map-capability-probes-and-persisted-feature-flags
kk_derived_from: []
kk_relates_to:
  - practice-check-capabilities-not-server-version
  - map-timer-state-holder-and-sync-service
  - map-trash-capability-and-server-operations
  - map-account-refresh-steps-and-preference-readers
kk_depends_on: []
kk_confidence: high
---
`capabilities/Capabilities` holds the Tokkl-only flags `trash`, `restore`, `favorites`, `timeResolution`, `negatedFilters` and `timerState`. `favorites` follows Tokkl's `role` in `/me` and also covers Tokkl's other preference keys that the app reads and writes, such as `timerView`. `timeResolution` follows `time_resolution_minutes`. `negatedFilters` also follows `role`: report requests may carry `exclude_project_ids`, `exclude_client_ids` and `exclude_tag_ids`, which every Tokkl release takes and Toggl Track's Reports API v3 lacks. `probeTrash` sends `GET /api/app/trash` through `trash.probe()`, which answers whether it is a list: a list is `PRESENT`, a 404 or a 2xx that is not a list is `ABSENT`, and anything else is `UNKNOWN`, which keeps the trash and restore flags as they were. Restore lives in the same router as the trash, so the trash probe decides it too. A trash call outside the probe that gets the same 404 calls `CapabilitiesSource.trashMissing()`, which turns both flags off. `probeTimerState` sends `GET /api/app/timer` the same way: a body that parses as a timer state is `Present`, a 404 or a body that is not a timer state is `Absent`, and anything else is `Unknown`, which keeps the flag as it was. `timerState` is false on Toggl Track and on Tokkl before 1.10.0. When it is false, Pomodoro leaves the navigation, Pause and Resume leave every timer surface and the sync service does not run. A state the probe read goes to the timer state source, so the refresh sends no second request for it (see the timer state node). `CapabilitiesStore` keeps the flags in the private preferences file `capabilities`, and features read them from `CapabilitiesSource`, which loads them at process start off the main thread. Code outside the screens that may run before that load ends reads them through `current()`, the `loaded` flow or the suspending checks, such as `offersTimerApi()`, which wait for it. A class that needs only the timer API check takes it as a `suspend () -> Boolean`, which Koin fills with `CapabilitiesSource::offersTimerApi`; the change sender, the Timer view, the Pomodoro defaults and the app's refresh steps take `CapabilitiesSource` itself. A reader of one flow takes the flow (`state` or `loaded`), and a caller of one change of the flags takes the function, such as `trashMissing` or `csvMissing`; every change of the flags suspends and runs one at a time under a `Mutex`.

<!-- kk:related:start -->
# Related

- Related: [practice-check-capabilities-not-server-version](../../api-and-security/practice-check-capabilities-not-server-version.md)
- Related: [map-timer-state-holder-and-sync-service → map-account-timer-state-lifetime](../timer-state-and-sync/map-account-timer-state-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-online-timer-actions-and-native-control-failures](../timer-state-and-sync/map-online-timer-actions-and-native-control-failures.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-event-stream-and-service-lifetime](../timer-state-and-sync/map-timer-event-stream-and-service-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-state-acceptance-and-persistence](../timer-state-and-sync/map-timer-state-acceptance-and-persistence.md)
- Related: [map-trash-capability-and-server-operations](../../screens/session-and-navigation/map-trash-capability-and-server-operations.md)
- Related: [map-account-refresh-steps-and-preference-readers](map-account-refresh-steps-and-preference-readers.md)
<!-- kk:related:end -->
