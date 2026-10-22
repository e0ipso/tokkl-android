---
type: map
title: 'Change queue: ChangeQueue, ChangeSender, BackgroundSend and the offline picker'
description: >-
  The Room queue sends captured Stops, restores and entry changes offline, with
  persistent refusals and guarded background cancellation.
tags:
  - offline
  - cache
  - room
  - api
  - queue
kk_schema_version: 3
kk_id: map-change-queue-sends-entry-changes-in-order
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:1'
kk_relates_to:
  - practice-never-wait-for-server-on-start-stop
  - map-account-cache-and-capabilities
  - map-tokkl-toggl-api
  - practice-refresh-on-events-no-polling
  - map-timer-entry-form-and-entry-menu
  - map-api-client-shared-okhttp-serverurl-and-errors
  - practice-test-real-tokkl-server-speed-regressions
kk_depends_on: []
kk_confidence: high
---
`cache/ChangeQueue` starts, stops, creates, edits and deletes time entries on the phone (`docs/PRD.md` O1). Screen models take it as the `EntryChanges` interface, so a JVM test can stand in for the database. Each call changes the cached entry and queues the change in one transaction, then asks the sender to send. No call waits for the network. Start and Stop read the tap time from the clock before anything else, so call them from the tap's handler. A start first stops a cached running entry at that same time, as the server does. `AccountTime.snapCapturedStop` predicts the latest independent-endpoint snapping for a Stop or switch, while `PendingChange.capturedStop` preserves the immutable tap instant. `ChangeQueue.stopAt` removes a too-short result from history immediately and writes a `ShortStopUndo` row for its stopped zero-length restore. A server without `time_resolution_minutes` keeps the tap time. The legacy `stopSpan` predictor remains for migrated queued stops and switch reconciliation. `updateOlder` sets every field of a stopped entry that the cache may not hold, such as one from the history's older days; it queues a full PUT and the cache holds the entry until a refresh prunes it. Start and create take the workspace from `AccountSettings.defaultWorkspaceId`, which the phone keeps, so they work offline.

`ChangeStore` keeps the queue as `PendingChange` rows. Each one carries every field of the entry as the change left it, so the server gets full values and the change that reaches it last wins (O3). An entry created on the phone gets a negative local id, minus its create's queue sequence. When the server answers the create, `EntryIdMapping` records the server's id and every queued change to that entry takes it. The cache marks an entry unsent until the server has every queued change to it, and shows the change over refreshed rows until then.

`cache/ChangeSender` sends the queue oldest first. A create is a POST, an update a full PUT, a captured Stop a PATCH to `.../time_entries/{id}/stop` with the captured `stop` timestamp, a restore a POST to `/api/app/time_entries/{id}/restore`, and a delete a DELETE. A pre-migration Stop with no captured instant retains its original PUT request. No network, a 5xx, a 408 or a 429 stops the drain and keeps the change. A legacy Stop's 409 counts as already done. A captured Stop's 409 or 404 counts as already done only after its exact predicted span and deletion state match the server entry or Trash row; an unverifiable refusal is held for attention. A 404 on an update or legacy Stop discards every queued change to that entry only when a read confirms the entry is gone, and counts the discarded changes for the notice. A 404 for a missing project or another related entity is held instead. A 400 or other permanent 4xx keeps the change and server reason in the needs-attention list. The user can edit, resend or discard it; later changes to the same entry wait, while unrelated entries can drain. A 401 or 403 pauses the entire queue for reauthentication, preserving the account and its data. One exception: when a try's connection failed after the request left the phone (`ApiError.worthAnotherTryAtOnce()`: a network failure that is neither a timeout nor one that left nothing on the server, such as a pooled connection Tokkl closed after Node's 5 s keep-alive), the drain tries that change once more at once on a new connection, at most once per change per drain, before it stops. After a 429 the next try waits for `Retry-After`, or 60 s without it. The wait lives in memory and the queue on disk. `onSent` fires once per drain that sent anything and calls `AccountSettingsSource.refreshAfterChange()`, never `refresh()`. A refresh already running may have read the server before the send, and `refreshAfterChange` waits for it and starts a new one.

The sender drains when a change is queued, after each refresh and when a 429's wait ends. `cache/BackgroundSend` covers the network coming back, also with the app closed (O4). `ChangeQueue`'s `onQueued` enqueues one unique one-time WorkManager work, `send-queue`, with a connected-network constraint and `ExistingWorkPolicy.KEEP`, so nothing wakes the phone while the queue is empty. Its worker calls the same `ChangeSender.sendAndWait`, so the worker and the app never send at once. A transient failure retries with exponential backoff from 30 s. `ChangeSender.onEmptied` runs when a drain that sent something leaves the queue empty and calls `BackgroundSend.cancelWaiting`, which cancels a waiting retry through `BackgroundSendRuns`. Worker registration and completed cancellation share a lock, and the worker checks committed WorkManager state under that lock before joining. A worker that joined the drain ends on its own. The queue is checked after cancellation and work is rescheduled if another change arrived. Sign-out cancels the work.

A queued start goes out as queued unless the server's running entry started after it. The sender reads the running entry first, one GET per start. When that entry is later, the start goes as a stopped entry that ends before the later timer (`ChangeStore.endBefore`), and the later timer keeps running. The sender's `switchSpan` places that end where the server stores a timer that a new start stopped: on Tokkl, both ends on the grid with the stop where the later timer's start snaps, so the two never overlap; on Toggl, the times as they are. Otherwise Tokkl stops its earlier timer itself when the start arrives (O3).

A create reaches the server once, though neither Tokkl nor Toggl takes an idempotency key on it. OkHttp sends a request again on a new connection when a reused connection fails after the request went out, so a POST whose answer was lost could create two entries. `TogglApiClient.send(..., resend = false)` wraps the body as one-shot (`SentOnce`), which OkHttp resends only when the request never left the phone, and the sender posts creates that way. Before sending a create or delete, the sender marks it `PendingChange.maybeSent` on disk. A failure that proves the server stored nothing, a 4xx or no route, clears the mark. Before posting a marked create again, the sender looks among the server's entries within an hour of its start for one that `couldHaveMade` it: same workspace, description, project, task and tags, and a start equal to the create's or to where the server snaps it. Tags compare as Tokkl matches names, trimmed and with ASCII letters in lower case, because an entry queued with the tag "work" comes back tagged "Work" when Tokkl has that tag. It skips the ids in `PendingChange.existingIds`, the server entries the phone held near that start when it queued the create, so the source of a duplicate is never taken for the copy. A marked delete keeps its mark whatever later tries get, and a 404 on a retry counts as sent. Catalog creates (projects, clients, tags, tasks) go through `catalog/CreateOnce.kt`'s `createOnce`, which sends with `resend = false` and, after a network failure, reads the list again and takes the matching item as the one the create stored, because Tokkl refuses a second copy as a duplicate name. When the read finds none, the create goes once more, and only once.

`ChangeSender.undoDelete` takes a delete back (H3). A delete still in the queue leaves it with no request. One the server has, or may have, comes back through `POST /api/app/time_entries/{id}/restore`, so call it only when `Capabilities.restore` is true. Deletes go out holding the lock that `undoDelete` holds, so Undo never decides while a delete is on its way.

`ChangeQueue.undoShortStop` queues `ChangeKind.RESTORE` behind the captured Stop and shows its stopped zero-length row immediately, including offline. `short_stop_undo` persists the offer after the Stop leaves the queue and across process restarts. Dismissing the offer leaves the queued Stop and server Trash intact.

`CacheDatabase` is at version 6. Version 5 adds `pending_changes.held` and `pending_changes.refusal`; version 6 adds `capturedStop`, `shortStop` and `short_stop_undo` without rewriting older queued requests. Each version from 1 through 6 has an exported schema in `app/schemas/`, with consecutive Room `AutoMigration` steps. Never drop the database file, since that loses queued changes. `CacheDatabaseMigrationTest` opens files written by earlier schema versions under the current version without room-testing, relying on Room's exported-schema validation.

`picker/EntityPicker` is the project, task and tag picker that the timer and the entry form share through `entry/EntryFieldsEditor` (P1). It reads only the cache, so search and selection work offline. Offline it says that creating a project, client or task needs a connection, and the manifest holds `ACCESS_NETWORK_STATE` for that message and for the background send's network wait. A tag typed by name joins the entry by name, and the server creates it when the queue sends the change. The picker's strings live in `picker_strings.xml`.

<!-- kk:related:start -->
# Related

- Related: [practice-never-wait-for-server-on-start-stop](practice-never-wait-for-server-on-start-stop.md)
- Related: [map-account-cache-and-capabilities](map-account-cache-and-capabilities.md)
- Related: [map-tokkl-toggl-api](../api-and-security/map-tokkl-toggl-api.md)
- Related: [practice-refresh-on-events-no-polling](../api-and-security/practice-refresh-on-events-no-polling.md)
- Related: [map-timer-entry-form-and-entry-menu](../screens/map-timer-entry-form-and-entry-menu.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors](../api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [practice-test-real-tokkl-server-speed-regressions](../testing/practice-test-real-tokkl-server-speed-regressions.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1
<!-- kk:citations:end -->
