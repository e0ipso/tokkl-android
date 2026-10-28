---
type: practice
title: Never make Start or Stop wait for the server
description: >-
  Start and Stop change state on the phone at once and queue the change. Pause,
  Resume and session actions wait for the server. No sync engine.
tags:
  - offline
  - network
  - api
kk_schema_version: 3
kk_id: practice-never-wait-for-server-on-start-stop
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-timer-state-holder-and-sync-service
  - map-ordered-entry-sending-and-refusals
  - map-offline-entry-mutations-and-local-id-mapping
kk_depends_on: []
kk_confidence: high
---
Tapping Start or Stop never fails because the server is out of reach. Pause, Resume, Stop while paused and the Pomodoro session actions are the exception: the server owns the pause chain and the session, so they go to it at once, never through the queue, and offline they fail with "Can't reach the server. Try again when you're back online." and change nothing (see the timer state node). A Stop of a running timer, a resumed one included, stays queued. A tap in the app, notification, widget or tile changes the state on the phone without waiting for the server. The notification, widget and tile read the running entry from the phone, so they react at once, and the queue sends the change behind them.

Each offline change waits in a queue on the phone, marked as not sent, and goes out in order when the server is reachable. A timer started offline keeps its real start time. The queue sends in the background when the network comes back, even with the app closed. The app shows how many changes are not sent and when the server last answered (O1 to O5). Some phone makers restrict background work, which can delay sending the queue.

The app has no sync engine and no full local copy of the account. The server is the source of truth. The phone keeps a cache of what it last loaded and the queue of unsent changes. The cache holds projects, clients, tags, tasks, favorites and about two weeks of history, all readable and editable offline. When the phone and another client both changed an entry, the change that reaches the server last wins. Signing out warns when unsent changes would be lost, then clears the cache and the queue.

`cache/ChangeQueue`, `ChangeSender` and `BackgroundSend` build the cache-and-queue part, with WorkManager sending the queue when the network returns (see the change queue node). In the app, the Timer page's Start and Stop, its field edits and the entry form's Save go to the queue and return at once. `TimerModel` shows what a tap did until the cache shows it, and ignores Start and Stop taps in between, so a double tap starts one entry. The running timer's notification follows the cache (see the notification node), and its Stop works offline, as do the widget's and the tile's. Sign-out asks first, with the count, when the queue holds unsent changes.

Reports and history older than the cache need the server. With no connection the reports screen says so instead of showing stale numbers (R5).

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-timer-state-holder-and-sync-service](map-timer-state-holder-and-sync-service.md)
- Related: [map-ordered-entry-sending-and-refusals](change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
<!-- kk:related:end -->
