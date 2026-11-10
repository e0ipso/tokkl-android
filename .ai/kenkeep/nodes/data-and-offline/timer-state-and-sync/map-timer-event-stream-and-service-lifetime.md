---
type: map
title: Timer event stream and service lifetime
description: >-
  A specialUse foreground service owns the timer stream, watchdog, reconnects
  and account termination handling.
tags:
  - sync
  - network
  - android
  - battery
kk_schema_version: 3
kk_id: map-timer-event-stream-and-service-lifetime
kk_derived_from: []
kk_relates_to:
  - map-api-client-shared-okhttp-serverurl-and-errors
  - map-tokkl-toggl-api
  - map-pomodoro-phase-alarms-and-rearming
  - map-timer-state-acceptance-and-persistence
  - map-account-timer-state-lifetime
kk_depends_on: []
kk_confidence: high
---
The stream, its service and the follower sit in `:core:timersync` (package `timersync/`), above core:timerstate. `TimerSyncService` is a `specialUse` foreground service, declared with the manifest subtype property in that module's own manifest, with a silent low-importance notification on the `timer_sync` channel and no wake lock. `stopWithTask` is false. `onStartCommand` promotes before anything else and catches `ServiceStartNotAllowedException`, then stops itself; the next app open starts it. No static field holds the service. The follower tracks the promoted service instance internally. Device tests read its lifecycle through Android's activity manager. A stop goes through `TimerSyncFollower.stopService`, a request on a flow that the running service collects on the main thread, where it calls `stopSelf` with its latest start id, so a newer start that is still on its way promotes and decides for itself. A request made while no service runs reaches no later one. An intent action would not do: the stop would take a start id of its own and stop the service over a start sent before it. `TimerSyncFollowerTest` pins the follower's side. `TimerSync` owns the one stream. Its state changes one task at a time, in call order, on `Dispatchers.Default.limitedParallelism(1)`, so it has no lock: every method, the stream's callbacks on OkHttp threads included, hands its work to that scope and returns at once. The watchdog and the reconnects are delays in the same scope, timed by its `now` function, which Koin passes as `SystemClock::elapsedRealtime`, which counts deep sleep. `stop()` returns its job, which sign-out's step joins. `TimerEventStream` parses `GET /api/app/timer/events` by hand, a `timer` event is a state, and the 25 s `keep-alive` is an event with an empty `data:` line that only feeds the watchdog. The watchdog closes a stream silent for 60 s, reconnects wait 1 s and double up to 120 s, a stream open for 60 s resets the count, and the default network's changes, which the service collects from `DefaultNetwork` while it runs, reconnect at once. A 401 or 403 ends the stream for that account. `TimerSync` reports each end no reconnect fixes, refused or signed out, on its `terminations` flow, and `TimerSyncFollower` collects it through `collectDurably`: a refusal for the account signed in now pauses the queue through its `refusal` function, which Koin passes as `ChangeSender::signInRefused`, and either end stops the service. `TimerEventConnections` only opens the calls, so nothing below the follower holds the follower. `TimerSyncFollower.follow` decides: it runs the service only while the stream is wanted (the `timerState` capability, no refused credential, signed in), and holds the stream only for the service instance that runs now. The app open, each account refresh, sign-in and the boot and update receiver call it.

<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-execution-retries-and-errors](../../api-and-security/api-client/map-api-execution-retries-and-errors.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-test-fixtures](../../api-and-security/api-client/map-api-test-fixtures.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-shared-http-client-and-configured-server-security](../../api-and-security/api-client/map-shared-http-client-and-configured-server-security.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-typed-api-resources-and-request-contracts](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md)
- Related: [map-tokkl-toggl-api](../../api-and-security/map-tokkl-toggl-api.md)
- Related: [map-pomodoro-phase-alarms-and-rearming](../map-pomodoro-phase-alarms-and-rearming.md)
- Related: [map-timer-state-acceptance-and-persistence](map-timer-state-acceptance-and-persistence.md)
- Related: [map-account-timer-state-lifetime](map-account-timer-state-lifetime.md)
<!-- kk:related:end -->
