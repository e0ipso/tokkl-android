---
type: map
title: 'Timer state holder and sync service: the server owns Pomodoro and pause'
description: >-
  One holder keeps the server's timer state; a specialUse service holds its
  event stream. Session and pause actions go online, never queued.
tags:
  - pomodoro
  - sync
  - network
  - android
  - battery
  - offline
kk_schema_version: 3
kk_id: map-timer-state-holder-and-sync-service
kk_derived_from: []
kk_relates_to:
  - map-pomodoro-phase-alarms-and-rearming
  - map-account-cache-and-capabilities
  - map-runningcheck-guarded-background-current-entry-refresh
  - map-tokkl-toggl-api
  - practice-never-wait-for-server-on-start-stop
  - map-api-client-shared-okhttp-serverurl-and-errors
kk_depends_on: []
kk_confidence: high
---
`timerstate/` holds the Tokkl server's timer state: the running entry, the pause chain and the Pomodoro session (`docs/PRD.md` T1, W2 to W10, section 6.3). The phone applies no phase or pause rule. `TimerStateSource` is the one holder per account. `accept` keeps the newest revision and drops a lower one, records when it arrived, so the clock offset is `server_time` minus that moment, and saves it through `TimerStateStore`. The store is a private preferences file, kept out of backups, holding no credential, so alarms, notifications and the widget act on it after process death without a request. It also holds the entry id of the last too-short pomodoro whose notice was shown. `accept` also writes the state's `running_entry` to the cache through `CurrentEntryCache`, the reconciliation `RunningCheck` uses, so queued starts, stops and edits keep showing until the server has them. Answers to `GET /api/app/timer`, to actions (409 refusals included) and stream events all enter through `accept`. Sign-out `retire`s the source before clearing the cache and the store, and the next sign-in makes a new one, so a late answer for the old account changes nothing.

`TimerActions` sends the six actions as `POST /api/app/timer/pause`, `/resume`, `/discard` and `/api/app/pomodoro`, `/next`, `/end`, through `sendRefusable`, which reads a 409 body as a timer state too. They are never queued and never resent: a lost answer shows up on the next read or event. Offline gives `TimerActionResult.Offline` and changes nothing; a 409 gives `Refused` and the holder takes the server's state. Resume, Discard, Next phase and End send the revision the control was drawn at, and `TimerTarget` also carries an account name, so a tap on an old notification or an old account's widget sends nothing. `SurfaceTap` and `showTimerActionFailure` serve the notification, widget, tile and reminder. `SessionLengths` checks the ranges the server enforces (work 1 to 180, rests 1 to 60, long rest every 1 to 12), because the server answers 400 outside them. Start, Stop and edits of the running entry stay on the change queue.

`TimerSyncService` is a `specialUse` foreground service, with the manifest subtype property, a silent low-importance notification on the `timer_sync` channel and no wake lock. `stopWithTask` is false. `onStartCommand` promotes before anything else and catches `ServiceStartNotAllowedException`, then stops itself; the next app open starts it. `TimerSync` owns the one stream: `TimerEventStream` parses `GET /api/app/timer/events` by hand, a `timer` event is a state, and the 25 s `keep-alive` is an event with an empty `data:` line that only feeds the watchdog. The watchdog closes a stream silent for 60 s, reconnects wait 1 s and double up to 120 s, a stream open for 60 s resets the count, and the default network's changes reconnect at once. A 401 or 403 stops the service and calls `sender.signInRefused()`. `TokklApplication.followTimerSync` decides: it runs the service only while `timerSyncWanted` (the `timerState` capability, no refused credential, signed in). The app open, each account refresh, sign-in and the boot and update receiver call it.

A v9 edit of the running entry moves the server's revision, the phone's own included, so a native surface tapped before the stream delivers the new state can get a 409. The Pomodoro page sends the queue and reads the revision first. `docs/PRD.md` section 6.4 lists the platform limits measured on API 36; nothing was measured on API 31. The ringing service stays a separate `mediaPlayback` service (see the alarms node).

<!-- kk:related:start -->
# Related

- Related: [map-pomodoro-phase-alarms-and-rearming](map-pomodoro-phase-alarms-and-rearming.md)
- Related: [map-account-cache-and-capabilities](map-account-cache-and-capabilities.md)
- Related: [map-runningcheck-guarded-background-current-entry-refresh](map-runningcheck-guarded-background-current-entry-refresh.md)
- Related: [map-tokkl-toggl-api](../api-and-security/map-tokkl-toggl-api.md)
- Related: [practice-never-wait-for-server-on-start-stop](practice-never-wait-for-server-on-start-stop.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors](../api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md)
<!-- kk:related:end -->
