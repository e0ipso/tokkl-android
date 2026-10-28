---
type: map
title: 'RunningCheck: guarded background current-entry refresh'
description: >-
  One connected 15-minute worker follows cached running state and protects
  unsent edits, newer writes and sign-out.
tags:
  - offline
  - cache
  - android
  - battery
kk_schema_version: 3
kk_id: map-runningcheck-guarded-background-current-entry-refresh
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:5'
kk_relates_to:
  - practice-refresh-on-events-no-polling
  - map-account-cache-and-capabilities
  - map-running-timer-notification-follows-the-cache
  - map-timer-state-holder-and-sync-service
kk_depends_on: []
kk_confidence: high
---
`app/src/main/java/com/github/e0ipso/tokkl/cache/RunningCheck.kt` owns unique periodic WorkManager work named `running-check`. `TokklApplication` follows whether the cache holds a running timer. One connected check waits every 15 minutes while running and is cancelled when idle; the first check also waits a period.

`RunningCheckWorker` reads only `GET /api/v9/me/time_entries/current`, then calls `AccountCache.checkedRunning`. It neither drains the queue nor refreshes history. A failed request leaves the cache unchanged until the next period, with no intervening retry. A remote stop clears the cached running row, which also clears the cache-following notification, widget and tile.

The check is unchanged by the timer event stream and still runs every 15 minutes while a timer runs, on every server. On a Tokkl server with the timer API the stream delivers a remote start or stop in seconds, and the check is the fallback when the stream is down. `TimerStateSource.accept` writes each state's running entry through the same `CurrentEntryCache` calls, `runningCheckStart` and `checkedRunning`. `RunningCheckStart.pendingIds` records which entries had queued changes when the request began, so an entry whose change the sender may have sent during the request stays as it is.

The response cannot overwrite entries with queued changes or cache rows changed after the request began. `RunningCheckResults` serializes writes with sign-out using a generation guard, since a synchronous request can finish after worker cancellation. Sign-out forgets the generation and waits for WorkManager cancellation before clearing account data.

<!-- kk:related:start -->
# Related

- Related: [practice-refresh-on-events-no-polling](../api-and-security/practice-refresh-on-events-no-polling.md)
- Related: [map-account-cache-and-capabilities](map-account-cache-and-capabilities.md)
- Related: [map-running-timer-notification-follows-the-cache](map-running-timer-notification-follows-the-cache.md)
- Related: [map-timer-state-holder-and-sync-service](map-timer-state-holder-and-sync-service.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:5
<!-- kk:citations:end -->
