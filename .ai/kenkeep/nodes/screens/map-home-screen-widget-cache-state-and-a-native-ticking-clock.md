---
type: map
title: 'Home screen widget: cache state and a native ticking clock'
description: >-
  Glance redraws on cache changes; a launcher Chronometer ticks without periodic
  app updates.
tags:
  - android
  - widget
  - offline
  - battery
kk_schema_version: 3
kk_id: map-home-screen-widget-cache-state-and-a-native-ticking-clock
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:2'
kk_relates_to:
  - map-timer-state-holder-and-sync-service
  - practice-never-wait-for-server-on-start-stop
  - map-ordered-entry-sending-and-refusals
  - map-offline-entry-mutations-and-local-id-mapping
  - practice-refresh-on-events-no-polling
  - map-pomodoro-home-screen-widget
kk_depends_on: []
kk_confidence: high
---
`widget/` owns the Timer provider, state and actions. It draws a strip below 260 × 200 dp, a medium card below 340 × 310 dp, and a large card above those thresholds. The medium card adds Today’s total and a stacked project bar; the large card adds Total, Billable and Entries, project rows, Reports/Today and template chips. The number of chips adapts to width and font scale. Both providers can resize to 110 × 40 dp.

Day totals use the Timer page’s `countedTimerEntries`: an entry belongs to its account-local start day and pause segments count once. Project groups include No project, sorted by stopped seconds; over five groups become four plus Other. Account midnight is reread on app-open, cache and RunningCheck events, including an unchanged current-entry response. There is no midnight job.

Start, ordinary Stop and template starts use `ChangeQueue` offline. Start and the template chips carry the account name the widget state was drawn for (`WidgetState.Ready.account`), and `StartTimerAction` starts nothing for another account or none. Pause, Resume and Stop while paused go to the server and need a connection; controls carry the account and revision they were drawn for. `WidgetActions` stops a running entry on the phone before the Glance callback returns, and hands the server actions to the same expedited `TimerActionWorker` as the notification's (`sendTimerAction`), which checks the target when it runs and sends once, so the process lives until the server answers. Pause is hidden during a session. Signed out opens sign-in.

`WidgetSync` follows state changes and stores separate redraw keys for each provider. It lives as long as the process and follows its keys through core:platform's `collectDurably`, with only the newest key kept (`latestOnly`): a failed read of the state reads it again, and a failed redraw tries the same key again, each after a wait of one second doubling up to two minutes, while the newest key waits. A failure of the disk or the database (`IOException`, `SQLiteException`) is expected; any other exception goes to the phone's log first. Cancellation ends it. `PomodoroWidgetSyncTest` pins both retries, and `CollectDurablyTest` the waits. `TimerWidgetHook` (after the reminder's hook in the app's hook order) builds the Timer widget's `WidgetSync` when it runs at process start, with its `WidgetStore` and a `GlanceRedraw`, the `WidgetRedraw` that asks Glance to draw every placed widget again; Koin holds no `WidgetSync` of its own. Native Chronometers tick elapsed time and applicable totals in the launcher; `updatePeriodMillis` is zero, with no periodic widget work.

<!-- kk:related:start -->
# Related

- Related: [map-timer-state-holder-and-sync-service](../data-and-offline/map-timer-state-holder-and-sync-service.md)
- Related: [practice-never-wait-for-server-on-start-stop](../data-and-offline/practice-never-wait-for-server-on-start-stop.md)
- Related: [map-ordered-entry-sending-and-refusals](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [practice-refresh-on-events-no-polling](../api-and-security/practice-refresh-on-events-no-polling.md)
- Related: [map-pomodoro-home-screen-widget](map-pomodoro-home-screen-widget.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:2
<!-- kk:citations:end -->
