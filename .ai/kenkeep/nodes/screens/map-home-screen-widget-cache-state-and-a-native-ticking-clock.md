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
  - practice-never-wait-for-server-on-start-stop
  - map-change-queue-sends-entry-changes-in-order
  - practice-refresh-on-events-no-polling
kk_depends_on: []
kk_confidence: high
---
`app/src/main/java/com/github/e0ipso/tokkl/widget/` owns `TokklWidget`, its receiver, state and actions. The widget reads the running entry and ordered favorites from the phone. Start, favorite taps and Stop use `ChangeQueue`, so they work offline without opening the app. Stop targets the entry the widget displayed.

`WidgetSync` starts from `TokklApplication` and redraws placed widgets when their state key changes. `WidgetStore` persists a SHA-256 key rather than descriptions or names. `TokklWidget` embeds the native Chronometer from `app/src/main/res/layout/widget_chronometer.xml` as RemoteViews; the launcher ticks it. `app/src/main/res/xml/tokkl_widget_info.xml` sets `updatePeriodMillis` to zero. There is no app timer or periodic widget work.

Favorites fill only the rows that fit the current height and font scale. The minimum resize is 240 by 152 dp; the minimum initial height is 168 dp. Signed out, the widget opens sign-in. Missing favorites capability hides favorites.

<!-- kk:related:start -->
# Related

- Related: [practice-never-wait-for-server-on-start-stop](../data-and-offline/practice-never-wait-for-server-on-start-stop.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-background-queue-work-and-empty-queue-cancellation](../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-cached-entity-picker-and-offline-creation](../data-and-offline/change-queue/map-cached-entity-picker-and-offline-creation.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-offline-entry-mutations-and-local-id-mapping](../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-ordered-entry-sending-and-refusals](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-queued-delete-and-short-stop-undo](../data-and-offline/change-queue/map-queued-delete-and-short-stop-undo.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-remote-running-conflicts-and-lost-create-responses](../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-room-queue-migration-continuity](../data-and-offline/change-queue/map-room-queue-migration-continuity.md)
- Related: [practice-refresh-on-events-no-polling](../api-and-security/practice-refresh-on-events-no-polling.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:2
<!-- kk:citations:end -->
