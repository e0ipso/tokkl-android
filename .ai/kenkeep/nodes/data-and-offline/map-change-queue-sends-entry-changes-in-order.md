---
type: map
title: 'Change queue: ChangeQueue, ChangeSender, BackgroundSend and the offline picker'
description: >-
  Entry changes land in the cache and a Room queue at once. ChangeSender sends
  them oldest first, once each, also from WorkManager when the network returns.
tags:
  - offline
  - cache
  - room
  - api
  - queue
kk_schema_version: 3
kk_id: map-change-queue-sends-entry-changes-in-order
kk_derived_from: []
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
`cache/ChangeQueue` starts, stops, creates, edits and deletes time entries on the phone (`docs/PRD.md` O1). Screen models take it as the `EntryChanges` interface, so a JVM test can stand in for the database. Each call changes the cached entry and queues the change in one transaction, then asks the sender to send. No call waits for the network. Start and Stop read the tap time from the clock before anything else, so call them from the tap's handler. A start first stops a cached running entry at that same time, as the server does. Both stops are put on the grid when they are queued, where the server would store them: `stopSpan` in `time/Snap.kt`, a port of Tokkl 1.5.0's `stopEntryAt`, through `AccountTime.snapStop`. Stop uses the one-step rule of Tokkl's stop endpoint (a stop that lands on the start moves one step later); a switch, where a new timer stops the running one, has no minimum and ends where the new timer's start snaps. Against a server without `time_resolution_minutes`, the tap time stays. The queued span is already on the grid, so `snapUnsent`, which shows other unsent entries where the server will put them, leaves it as it is, and the server stores it as sent. `updateOlder` sets every field of a stopped entry that the cache may not hold, such as one from the history's older days; it queues a full PUT and the cache holds the entry until a refresh prunes it. Start and create take the workspace from `AccountSettings.defaultWorkspaceId`, which the phone keeps, so they work offline.

`ChangeStore` keeps the queue as `PendingChange` rows. Each one carries every field of the entry as the change left it, so the server gets full values and the change that reaches it last wins (O3). An entry created on the phone gets a negative local id, minus its create's queue sequence. When the server answers the create, `EntryIdMapping` records the server's id and every queued change to that entry takes it. The cache marks an entry unsent until the server has every queued change to it, and shows the change over refreshed rows until then.

`cache/ChangeSender` sends the queue oldest first. A create is a POST, an update and a stop a PUT with every field, and a delete a DELETE. Stop goes as a full PUT because Tokkl's `PATCH .../stop` would take the server's clock instead of the tap time. A failure stops the drain and keeps the change, so the order holds: no network, a 5xx, a 429 and, until TKA-0073, any other failure. One exception: when a try's connection failed after the request left the phone (`ApiError.worthAnotherTryAtOnce()`: a network failure that is neither a timeout nor one that left nothing on the server, such as a pooled connection Tokkl closed after Node's 5 s keep-alive), the drain tries that change once more at once on a new connection, at most once per change per drain, before it stops. After a 429 the next try waits for `Retry-After`, or 60 s without it. The wait lives in memory and the queue on disk. `onSent` fires once per drain that sent anything and calls `AccountSettingsSource.refreshAfterChange()`, never `refresh()`. A refresh already running may have read the server before the send, and `refreshAfterChange` waits for it and starts a new one.

The sender drains when a change is queued, after each refresh and when a 429's wait ends. `cache/BackgroundSend` covers the network coming back, also with the app closed (O4). `ChangeQueue`'s `onQueued` enqueues one unique one-time WorkManager work, `send-queue`, with a connected-network constraint and `ExistingWorkPolicy.KEEP`, so nothing wakes the phone while the queue is empty. Its worker calls the same `ChangeSender.sendAndWait`, so the worker and the app never send at once. A transient failure retries with exponential backoff from 30 s. `ChangeSender.onEmptied` runs when a drain that sent something leaves the queue empty and calls `BackgroundSend.cancelWaiting`, which cancels the unique work only while it is `ENQUEUED`, so a retry waiting out its backoff never wakes the phone for nothing; a running worker joined the same drain and ends on its own. A narrow race remains when a worker starts between the check and the cancel (TKA-0234). Sign-out cancels the work.

A queued start goes out as queued unless the server's running entry started after it. The sender reads the running entry first, one GET per start. When that entry is later, the start goes as a stopped entry that ends before the later timer (`ChangeStore.endBefore`), and the later timer keeps running. The sender's `switchSpan` places that end where the server stores a timer that a new start stopped: on Tokkl, both ends on the grid with the stop where the later timer's start snaps, so the two never overlap; on Toggl, the times as they are. Otherwise Tokkl stops its earlier timer itself when the start arrives (O3).

A create reaches the server once, though neither Tokkl nor Toggl takes an idempotency key on it. OkHttp sends a request again on a new connection when a reused connection fails after the request went out, so a POST whose answer was lost could create two entries. `TogglApiClient.send(..., resend = false)` wraps the body as one-shot (`SentOnce`), which OkHttp resends only when the request never left the phone, and the sender posts creates that way. Before sending a create or delete, the sender marks it `PendingChange.maybeSent` on disk. A failure that proves the server stored nothing, a 4xx or no route, clears the mark. Before posting a marked create again, the sender looks among the server's entries within an hour of its start for one that `couldHaveMade` it: same workspace, description, project, task and tags, and a start equal to the create's or to where the server snaps it. Tags compare as Tokkl matches names, trimmed and with ASCII letters in lower case, because an entry queued with the tag "work" comes back tagged "Work" when Tokkl has that tag. It skips the ids in `PendingChange.existingIds`, the server entries the phone held near that start when it queued the create, so the source of a duplicate is never taken for the copy. A marked delete keeps its mark whatever later tries get, and a 404 on a retry counts as sent. Catalog creates (projects, clients, tags, tasks) go through `catalog/CreateOnce.kt`'s `createOnce`, which sends with `resend = false` and, after a network failure, reads the list again and takes the matching item as the one the create stored, because Tokkl refuses a second copy as a duplicate name. When the read finds none, the create goes once more, and only once.

`ChangeSender.undoDelete` takes a delete back (H3). A delete still in the queue leaves it with no request. One the server has, or may have, comes back through `POST /api/app/time_entries/{id}/restore`, so call it only when `Capabilities.restore` is true. Deletes go out holding the lock that `undoDelete` holds, so Undo never decides while a delete is on its way.

`CacheDatabase` is at version 4: version 2 added the queue, `pending_changes` and `entry_ids`, version 3 `pending_changes.maybeSent`, and version 4 `pending_changes.existingIds`. Each step is a Room `AutoMigration`, and every schema is exported to `app/schemas/`. Never drop the file, since that would lose queued changes. `CacheDatabaseMigrationTest` opens files written by versions 1, 2 and 3 under the current version without room-testing, since Room checks the migrated tables against the exported schema when it opens the file.

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
