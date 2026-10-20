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
kk_relates_to: []
kk_depends_on: []
kk_confidence: high
---
The app makes few requests. It refreshes the running entry and the visible data when it opens, after a change, and when the network comes back. It does not poll on a short timer. The running clock ticks on the phone from the start time.

In the code, `session/ForegroundRefresh` decides when the app reads the server again while it is in the foreground: once each time it comes to the foreground, and once each time the network comes back while it is there. A rotation stops and starts the activity without leaving the screen, so its start reads nothing; `ForegroundRefresh` lives as long as the process and tells the two apart through `isChangingConfigurations`. `session/NetworkWatch` follows the default network's `NET_CAPABILITY_INTERNET` only from `MainActivity.onStart` to `onStop`, so no network change reaches the app in the background. `reachesInternet()` in the same file is the one offline test, shared with the pickers' offline notice, and a network that reaches a local Tokkl server but not the internet counts as offline. On the network's return, `AccountSettingsSource.refreshOnReconnect()` waits for a refresh that is running and starts one more unless `/me` answered after the reconnect, because the running one may have sent its request while the phone was offline. `MainActivity.networkChanged` then calls `reconnected()` on the history, calendar and reports models, which load again only what failed offline. `AccountSettingsSource.refresh()` also runs after sign-in and after an accepted trust prompt. `refreshAfterChange()` runs after the queue sends a drain and after a catalog page's change, and waits out any refresh that may have read the server before the change. The Calendar marks the weeks it loaded as stale when the app comes back to the foreground and loads a week again only when it shows. The Projects, Clients and Tags pages, and a project's task list, load again when the app comes back to the foreground after a stop that was not a configuration change, and when the phone comes back online. When the network returns, WorkManager's one-time `send-queue` work drains the queue, and its `onSent` refresh follows. That work exists only while the queue holds a change. Nothing in the history, the calendar or the reports loads on a timer, and the running block in the calendar grows with the phone's clock without a request.

Toggl's quotas are the reason. A later version points the app at Toggl's base URL. Toggl caps workspace and organization requests per user per organization at 30 an hour on Free, 240 on Starter and 600 on Premium. User endpoints such as `/me` are capped at 30 an hour on every plan below Enterprise. The window slides over 60 minutes, and a request over the limit gets HTTP 402. Tokkl's web app polls the running timer every 30 seconds, which alone is 120 requests an hour. An app that polled like that would hit the Free quota within 15 minutes.

Battery follows the same rule. The app makes no wakeups while no timer runs and the queue is empty.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
