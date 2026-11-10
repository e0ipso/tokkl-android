---
type: map
title: Timer state acceptance and persistence
description: >-
  Revision acceptance, clock offsets, persisted state and cache reconciliation
  share one account holder.
tags:
  - pomodoro
  - sync
  - offline
kk_schema_version: 3
kk_id: map-timer-state-acceptance-and-persistence
kk_derived_from: []
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-runningcheck-guarded-background-current-entry-refresh
  - map-tokkl-toggl-api
  - map-account-timer-state-lifetime
  - map-online-timer-actions-and-native-control-failures
  - map-timer-event-stream-and-service-lifetime
kk_depends_on: []
kk_confidence: high
---
`timerstate/` holds the Tokkl server's timer state: the running entry, the pause chain and the Pomodoro session (`docs/PRD.md` T1, W2 to W10, section 6.3). The phone applies no phase or pause rule. `TimerStateSource` is the one holder per account. `accept` keeps the newest revision and drops a lower one, records when it arrived, so the clock offset is `server_time` minus that moment, and saves it through `TimerStateStore`. The store is a private preferences file, kept out of backups, holding no credential, so alarms, notifications and the widget act on it after process death without a request. It also holds the entry id of the last too-short pomodoro whose notice was shown. `accept` also writes the state's `running_entry` to the cache through `CurrentEntryCache`, the reconciliation `RunningCheck` uses, so queued starts, stops and edits keep showing until the server has them. Answers to `GET /api/app/timer`, to actions (409 refusals included) and stream events all enter through `accept`. The source holds the lock and the order of the steps; the revision rule (`acceptsRevision`), the notice filter (`unshownDiscard`) and the reconciliation of an answer with the cache writes accepted since its read (`withWrites` over `RecentWrites`) are pure functions in `TimerStateRules.kt`, tested on their own in `TimerStateRulesTest`, and the disk write (`HeldStateDisk`) and the notice (`DiscardNoticeSlot`) are its two collaborators. The source stores the server's `TimerState` as it came and publishes it as a `HeldTimerState` in the model's types (`PauseChain`, `PomodoroSession`, `DiscardedPomodoro`), which `TimerState.held` in `timerstate/TimerStateMapping.kt` maps, so no feature reads a timer wire type.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities → map-account-cache-storage-and-history-window](../account-cache/map-account-cache-storage-and-history-window.md)
- Related: [map-account-cache-and-capabilities → map-account-refresh-steps-and-preference-readers](../account-cache/map-account-refresh-steps-and-preference-readers.md)
- Related: [map-account-cache-and-capabilities → map-capability-probes-and-persisted-feature-flags](../account-cache/map-capability-probes-and-persisted-feature-flags.md)
- Related: [map-runningcheck-guarded-background-current-entry-refresh](../map-runningcheck-guarded-background-current-entry-refresh.md)
- Related: [map-tokkl-toggl-api](../../api-and-security/map-tokkl-toggl-api.md)
- Related: [map-account-timer-state-lifetime](map-account-timer-state-lifetime.md)
- Related: [map-online-timer-actions-and-native-control-failures](map-online-timer-actions-and-native-control-failures.md)
- Related: [map-timer-event-stream-and-service-lifetime](map-timer-event-stream-and-service-lifetime.md)
<!-- kk:related:end -->
