---
type: map
title: 'Favorite launcher shortcuts: cache publication and tap handling'
description: >-
  FavoriteShortcutSync publishes favorites; StartFavoriteActivity resolves the
  current favorite and queues an offline start.
tags:
  - android
  - shortcuts
  - offline
kk_schema_version: 3
kk_id: map-favorite-launcher-shortcuts-cache-publication-and-tap-handling
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:4'
kk_relates_to:
  - map-change-queue-sends-entry-changes-in-order
  - practice-never-wait-for-server-on-start-stop
kk_depends_on: []
kk_confidence: high
---
`app/src/main/java/com/github/e0ipso/tokkl/shortcuts/` owns dynamic favorite shortcuts. `FavoriteShortcutSync` follows cached favorites, projects and capabilities from process start, preserving favorite order and the launcher limit. It avoids republishing an unchanged list because Android limits background shortcut updates. Sign-out and loss of favorites capability remove published shortcuts.

A shortcut carries only a hash-derived favorite ID. `StartFavoriteActivity` resolves that ID against the current cached favorites and capability before starting through `ChangeQueue`. A removed favorite or stale pinned shortcut starts nothing. The transparent activity keeps the offline queue write alive and stays foreground until its confirmation toast can appear, then finishes without opening the main app.

<!-- kk:related:start -->
# Related

- Related: [map-change-queue-sends-entry-changes-in-order → map-background-queue-work-and-empty-queue-cancellation](../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-cached-entity-picker-and-offline-creation](../data-and-offline/change-queue/map-cached-entity-picker-and-offline-creation.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-offline-entry-mutations-and-local-id-mapping](../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-ordered-entry-sending-and-refusals](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-queued-delete-and-short-stop-undo](../data-and-offline/change-queue/map-queued-delete-and-short-stop-undo.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-remote-running-conflicts-and-lost-create-responses](../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-room-queue-migration-continuity](../data-and-offline/change-queue/map-room-queue-migration-continuity.md)
- Related: [practice-never-wait-for-server-on-start-stop](../data-and-offline/practice-never-wait-for-server-on-start-stop.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:4
<!-- kk:citations:end -->
