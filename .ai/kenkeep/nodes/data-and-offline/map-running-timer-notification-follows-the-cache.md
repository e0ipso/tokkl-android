---
type: map
title: Running timer notification follows the cache's running entry
description: >-
  notification/RunningNotificationSync posts, replaces and removes the ongoing
  notification from the cache and the held timer state. Stop works offline,
  Pause needs the server.
tags:
  - notification
  - offline
  - android
kk_schema_version: 3
kk_id: map-running-timer-notification-follows-the-cache
kk_derived_from: []
kk_relates_to:
  - map-timer-state-holder-and-sync-service
  - practice-never-wait-for-server-on-start-stop
  - map-ordered-entry-sending-and-refusals
  - map-offline-entry-mutations-and-local-id-mapping
  - practice-talk-only-to-configured-server
kk_depends_on: []
kk_confidence: high
---
`notification/RunningNotificationSync` keeps the running timer's ongoing notification (`docs/PRD.md` N1) in step with the cache's running entry. `TokklApplication.onCreate` starts it in every process, off the main thread. Every start, stop and edit lands in the cache first, whichever surface makes it, so following the cache covers the app, an offline start, the notification's own Stop and a refresh that finds a timer started elsewhere. Do not post the notification from a screen.

`RunningNotifier` shows the description, the project and a system chronometer that counts from the entry's start, so this notification needs no foreground service and the app sets no alarm to update it. The timer sync service has its own notification (see the timer state node). Its Stop action reaches `StopTimerReceiver`, which is not exported, stops the entry on the phone and queues the change without opening the app, so it works offline. A Pause action goes to the server through `TimerSurfaceActions`, so it needs a connection; a failure shows a toast and the notification stays as it was. While the server's chain is paused, a quiet notification that is not ongoing shows the paused total with Resume and Stop, and its Stop is the server's Discard. Pause is absent without the `timerState` capability and during a Pomodoro session. Each action carries the `TimerTarget` it was drawn for: the entry id or the paused revision, and an account name, so an old notification does nothing. The app declares `POST_NOTIFICATIONS` but does not ask for it yet (TKA-0058). On Android 13 and later, until the user allows notifications, `post` does nothing.

A swiped-away notification stays away until the running entry changes, also across a new process. `RunningNotificationStore` keeps the followed entry's key and id in its own preferences file. The key (`RunningNotificationContent.key`) is a SHA-256 hash of the entry's fields with its start to the second, so the file holds no description or name. The server's echo of a phone-made start brings a new id, whole seconds, a trimmed description and the account's tag case, and the key does not change for any of them. The followed id is mapped through `ChangeQueue.currentId`, so when the server has answered the create, its id counts as the same entry and nothing is posted again. Two starts with the same fields within one second share a key, and the id tells them apart, so the newer one still posts. Sign-out calls `forget()`.

<!-- kk:related:start -->
# Related

- Related: [map-timer-state-holder-and-sync-service](map-timer-state-holder-and-sync-service.md)
- Related: [practice-never-wait-for-server-on-start-stop](practice-never-wait-for-server-on-start-stop.md)
- Related: [map-ordered-entry-sending-and-refusals](change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [practice-talk-only-to-configured-server](../api-and-security/practice-talk-only-to-configured-server.md)
<!-- kk:related:end -->
