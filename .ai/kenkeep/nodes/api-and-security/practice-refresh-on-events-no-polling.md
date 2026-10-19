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

In the code, `AccountSettingsSource.refresh()` runs from `MainActivity.onStart`, after sign-in and after an accepted trust prompt. `refreshAfterChange()` runs after the queue sends a drain and after a catalog page's change, and waits out any refresh that may have read the server before the change. The Calendar marks the weeks it loaded as stale when the app comes back to the foreground and loads a week again only when it shows. Nothing in the history, the calendar or the reports loads on a timer, and the running block in the calendar grows with the phone's clock without a request.

Toggl's quotas are the reason. A later version points the app at Toggl's base URL. Toggl caps workspace and organization requests per user per organization at 30 an hour on Free, 240 on Starter and 600 on Premium. User endpoints such as `/me` are capped at 30 an hour on every plan below Enterprise. The window slides over 60 minutes, and a request over the limit gets HTTP 402. Tokkl's web app polls the running timer every 30 seconds, which alone is 120 requests an hour. An app that polled like that would hit the Free quota within 15 minutes.

Battery follows the same rule. The app makes no wakeups while no timer runs and the queue is empty.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
