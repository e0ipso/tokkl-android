---
type: map
title: Online timer actions and native control failures
description: >-
  Session and pause controls use captured account revisions, single server
  attempts and shared failure notices.
tags:
  - pomodoro
  - network
  - android
  - offline
kk_schema_version: 3
kk_id: map-online-timer-actions-and-native-control-failures
kk_derived_from: []
kk_relates_to:
  - practice-never-wait-for-server-on-start-stop
  - map-api-client-shared-okhttp-serverurl-and-errors
  - map-tokkl-toggl-api
  - map-pomodoro-phase-alarms-and-rearming
  - map-timer-state-acceptance-and-persistence
  - map-account-timer-state-lifetime
kk_depends_on: []
kk_confidence: high
---
`TimerActions` sends the six actions as `POST /api/app/timer/pause`, `/resume`, `/discard` and `/api/app/pomodoro`, `/next`, `/end`, through `TokklApi.timer`, whose actions use `sendRefusable`, which reads a 409 body as a timer state too. They run in the application's scope, so an action reaches the server and its answer the holder also when the surface that sent it goes; `read()` runs in the caller's coroutine and stops with it. They are never queued and never resent: a lost answer shows up on the next read or event. Offline gives `TimerActionResult.Offline` and changes nothing; a 409 gives `Refused` and the holder takes the server's state. Resume, Discard, Next phase and End send the revision the control was drawn at. `TimerTarget` is sealed: `Running` with an entry id or `Paused` with a revision, each with the account name it was drawn for, so a tap on an old notification or an old account's widget sends nothing. PendingIntents, work data and widget parameters keep it flat (`flatRevision`, `flatEntryId`) and read it back with `timerTarget`, which gives null without an account. `SurfaceTap` and `TimerFailureNotice` serve the notification, widget, tile and reminder. Every such adapter runs its action through `runSurfaceCommand` (`timernotice/TimerFailureNotice.kt`): a refused or unanswered server action and a storage failure (`SQLiteException`, `IOException`) each show a toast, and the work that ran it ends as failed (`toWorkResult`), never as success. `TimerActionResult` carries no text: the `timernotice` package, in core:timersync, holds the refusal and offline strings, `timerNoticeText` words each outcome, and `TimerFailureNotice` is the interface a surface calls when an action fails. core:timerstate shows nothing and enqueues no work itself; its only Android framework type is the `Context` its store opens its file with. feature:notification binds `ToastTimerFailureNotice`, which shows the toast on the main thread, and the surfaces take the notice from Koin. `SessionLengths` checks the ranges the server enforces (work 1 to 180, rests 1 to 60, long rest every 1 to 12), because the server answers 400 outside them. Start, Stop and edits of the running entry stay on the change queue.

A v9 edit of the running entry moves the server's revision, the phone's own included, so a native surface tapped before the stream delivers the new state can get a 409. The Pomodoro page's `PomodoroSessionEdits`, which holds the page's session commands and the open pomodoro's edits, sends the queue and reads the revision first. Its edits go through core:cache's `RunningEntryEdits`, as the timer's do, whose `awaitQueued` waits for the edits on their way into the queue and says whether one reached it since. The page's view model keeps the start form's fields and typed lengths in the route's `SavedStateHandle` (`SavedPomodoroForm`), so a new process that restores the page shows the form as it was. `docs/PRD.md` section 6.4 lists the platform limits measured on API 36; nothing was measured on API 31. The ringing service stays a separate `mediaPlayback` service (see the alarms node).

<!-- kk:related:start -->
# Related

- Related: [practice-never-wait-for-server-on-start-stop](../practice-never-wait-for-server-on-start-stop.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-execution-retries-and-errors](../../api-and-security/api-client/map-api-execution-retries-and-errors.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-test-fixtures](../../api-and-security/api-client/map-api-test-fixtures.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-shared-http-client-and-configured-server-security](../../api-and-security/api-client/map-shared-http-client-and-configured-server-security.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-typed-api-resources-and-request-contracts](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md)
- Related: [map-tokkl-toggl-api](../../api-and-security/map-tokkl-toggl-api.md)
- Related: [map-pomodoro-phase-alarms-and-rearming](../map-pomodoro-phase-alarms-and-rearming.md)
- Related: [map-timer-state-acceptance-and-persistence](map-timer-state-acceptance-and-persistence.md)
- Related: [map-account-timer-state-lifetime](map-account-timer-state-lifetime.md)
<!-- kk:related:end -->
