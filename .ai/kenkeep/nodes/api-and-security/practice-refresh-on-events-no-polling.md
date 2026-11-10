---
type: practice
title: 'Refresh on events, never on a short polling timer'
description: >-
  Refresh when the app opens, after a change and when the network returns. No
  short polling, so Toggl's base URL later fits its quotas.
tags:
  - network
  - toggl
  - battery
kk_schema_version: 3
kk_id: practice-refresh-on-events-no-polling
kk_derived_from:
  - docs/PRD.md
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:5'
kk_relates_to:
  - map-timer-state-holder-and-sync-service
kk_depends_on: []
kk_confidence: high
---
The app makes few requests. It refreshes the running entry and the visible data when it opens, after a change, and when the network comes back. It does not poll on a short timer. The running clock ticks on the phone from the start time.

In the code, `session/ForegroundRefresh` decides when the app reads the server again while it is in the foreground: once each time it comes to the foreground, and once each time the network comes back while it is there. A rotation stops and starts the activity without leaving the screen, so its start reads nothing; `ForegroundRefresh` lives as long as the process and tells the two apart through `isChangingConfigurations`. It asks through the two functions core:cache's `cacheModule` passes it, `refresh` and `refreshOnReconnect`: each sends the queue first and then asks `AccountRefresh` to read. `session/NetworkWatch` follows the default network's `NET_CAPABILITY_INTERNET` only from `MainActivity.onStart` to `onStop`, so no network change reaches the app in the background. `NetworkWatch` reads the network from `platform/DefaultNetwork`, the one injected network source, which the screens' offline state (`platform/DefaultNetwork.kt`'s `offlineIn`, which each screen's view model follows while the screen shows), the tile and `TimerSyncService` read too. Each collector of its reports gets a callback of its own while it collects, so each watches only in its own window. `reachesInternet()` is the one offline test, and a network that reaches a local Tokkl server but not the internet counts as offline. On the network's return, `AccountRefresh.refreshOnReconnect()` waits for a refresh that is running and starts one more unless `/me` answered after the reconnect, because the running one may have sent its request while the phone was offline. The screens' view models follow the same events through `platform/AppForeground`, which the app builds from `ProcessLifecycleOwner` and `DefaultNetwork`: `foreground` is true while one of the app's activities is started, and `ProcessLifecycleOwner` waits about 700 ms after the last one stops, so a rotation is no trip; `reconnects` reports each relevant return of the network (`platform/changes()`, the same filter `NetworkWatch` uses) while the app is in the foreground. The history and the calendar retry what failed offline on each of those returns. `AccountRefresh.refresh()` also runs after sign-in and after an accepted trust prompt. `refreshAfterChange()` runs after the queue sends a drain and after a catalog page's change, and waits out any refresh that may have read the server before the change. The Calendar marks the weeks it loaded as stale when the app comes back to the foreground and loads a week again only when it shows. The Projects, Clients and Tags pages, a project's task list, the trash and the reports share their page state with `platform/PageLoads.kt`'s `stateInPage`: the state's first reader loads the page, the app's return to the foreground and each network return load it again while a screen reads it, and the page leaves, forgetting what it loaded, once no screen has read it for five seconds with the app in the foreground. A configuration change keeps the view model and its sharing, so turning the phone loads nothing. When the network returns, WorkManager's one-time `send-queue` work drains the queue, and the refresh after `ChangeSenderEvents.sent` follows. That work exists only while the queue holds a change. Nothing in the history, the calendar or the reports loads on a timer, and the running block in the calendar grows with the phone's clock without a request.

Toggl's quotas are the reason. A later version points the app at Toggl's base URL. Toggl caps workspace and organization requests per user per organization at 30 an hour on Free, 240 on Starter and 600 on Premium. User endpoints such as `/me` are capped at 30 an hour on every plan below Enterprise. The window slides over 60 minutes, and a request over the limit gets HTTP 402. Tokkl's web app polls the running timer every 30 seconds, which alone is 120 requests an hour. An app that polled like that would hit the Free quota within 15 minutes.

On a Tokkl server with the timer API the app does not poll but holds an event stream open, so a timer, pause or session started elsewhere arrives in seconds (see the timer state node). The stream is a long-lived connection with a 25 second keep-alive, not a request on a timer, and it exists only on that server. Toggl Track and older Tokkl keep the events above, so Toggl's quotas stay out of reach.
`cache/RunningCheck` is the sole periodic job. It schedules one connected 15-minute current-entry check while the cache holds a running timer and cancels it when idle. It updates the cache used by native timer surfaces without refreshing history or draining the queue.

Battery follows the same rule on servers without the stream: no wakeups while no timer runs and the queue is empty. With the stream, the connection and its notification stay up while signed in, which `docs/PRD.md` section 7 accepts as the price of cross-device sessions.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
[2] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:5
<!-- kk:citations:end -->
