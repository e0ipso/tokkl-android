---
type: map
title: Offline entry mutations and local ID mapping
description: 'Atomic local entry changes, full pending snapshots and negative-ID remapping.'
tags:
  - offline
  - cache
  - room
  - queue
kk_schema_version: 3
kk_id: map-offline-entry-mutations-and-local-id-mapping
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:1'
kk_relates_to:
  - practice-never-wait-for-server-on-start-stop
  - map-account-cache-and-capabilities
  - map-timer-controls-and-cached-start-suggestions
kk_depends_on: []
kk_confidence: high
---
`cache/ChangeQueue` starts, stops, creates, edits and deletes time entries on the phone (`docs/PRD.md` O1). Screen models take it through two interfaces, so a JVM test can stand in for the database: `RunningEntryChanges` for Start, Stop and the running segment's edits, and `EntryChanges` for creating, editing and deleting the history's entries. Both extend `CurrentEntryIds`, and every operation is abstract, so a test fake states each call it takes and fails on the rest. `localWrite` turns only the storage's `SQLiteException` and `IOException` into `LocalWrite.Failed`; cancellation and programming errors still throw, and the entry form and the timer's taps give themselves back to the user when a write fails. Each call changes the cached entry and queues the change in one transaction, then asks the sender to send. No call waits for the network. Start and Stop read the tap time from the clock before anything else, so call them from the tap's handler. A start first stops a cached running entry at that same time, as the server does. `AccountTime.snapCapturedStop` predicts the latest independent-endpoint snapping for a Stop or switch, while `PendingChange.capturedStop` preserves the immutable tap instant. `ChangeQueue.stopAt` removes a too-short result from history immediately and writes a `ShortStopUndo` row for its stopped zero-length restore, which the app's snackbar reads as a `ShortStopOffer`, the entry's id and the tap time. A server without `time_resolution_minutes` keeps the tap time. The legacy `stopSpan` predictor remains for migrated queued stops and switch reconciliation. `updateOlder` sets every field of a stopped entry that the cache may not hold, such as one from the history's older days; it queues a full PUT and the cache holds the entry until a refresh prunes it. Start and create take the workspace from `AccountSettings.defaultWorkspaceId`, which the phone keeps, so they work offline.

`RoomChangeStore` is the one way into the queue's tables: `ChangeQueue` takes it and runs each change through its `transaction` with its DAO, and the sender reads and records through its `ChangeStore` interface, which `ChangeSenderTest` replaces with an in-memory queue. The queue is kept as `PendingChange` rows. Each one carries every field of the entry as the change left it, so the server gets full values and the change that reaches it last wins (O3). An entry created on the phone gets a negative local id, minus its create's queue sequence. When the server answers the create, `EntryIdMapping` records the server's id and every queued change to that entry takes it. The calls take the model's `EntryId`, local or not. `EntryId.local` gives a `LocalEntryId` only for a negative id, and the lookups in the mapping take that type: `RoomChangeStore.resolve`, which the queue and `takeBackDelete` share, `AccountCache.serverIdsOf` and the totals' `serverIdOf`, so a server id cannot reach them. The rows keep plain `Long` columns. The cache marks an entry unsent until the server has every queued change to it, and shows the change over refreshed rows until then. The needs-attention list and the Pomodoro page read a refused change as a `HeldChange`: its sequence, kind, the entry as it leaves it and the server's reason.

<!-- kk:related:start -->
# Related

- Related: [practice-never-wait-for-server-on-start-stop](../practice-never-wait-for-server-on-start-stop.md)
- Related: [map-account-cache-and-capabilities](../map-account-cache-and-capabilities.md)
- Related: [map-timer-controls-and-cached-start-suggestions](../../screens/timer-and-entry-editing/map-timer-controls-and-cached-start-suggestions.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1
<!-- kk:citations:end -->
