---
type: map
title: 'Change queue: ChangeQueue, ChangeSender, PendingChange and the offline picker'
description: >-
  Entry changes land in the cache and a Room queue at once. ChangeSender sends
  them oldest first, stop as a full PUT, and refreshes after.
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
kk_depends_on: []
kk_confidence: high
---
`cache/ChangeQueue` starts, stops, creates, edits and deletes time entries on the phone (`docs/PRD.md` O1). Each call changes the cached entry and queues the change in one transaction, then asks the sender to send. No call waits for the network. Start and Stop read the tap time from the clock before anything else, so call them from the tap's handler. A start first stops a cached running entry at that same time, as the server does. Start and create take the workspace from `AccountSettings.defaultWorkspaceId`, which the phone keeps, so they work offline.

`ChangeStore` keeps the queue as `PendingChange` rows. Each one carries every field of the entry as the change left it, so the server gets full values and the change that reaches it last wins (O3). An entry created on the phone gets a negative local id, minus its create's queue sequence. When the server answers the create, `EntryIdMapping` records the server's id and every queued change to that entry takes it. The cache marks an entry unsent until the server has every queued change to it, and shows the change over refreshed rows until then.

`cache/ChangeSender` sends the queue oldest first. A create is a POST, an update and a stop a PUT with every field, and a delete a DELETE. Stop goes as a full PUT because Tokkl's `PATCH .../stop` would take the server's clock instead of the tap time. A failure stops the drain and keeps the change, so the order holds: no network, a 5xx, a 429 and, until TKA-0073, any other failure. After a 429 the next try waits for `Retry-After`, or 60 s without it. The wait lives in memory and the queue on disk. `onSent` fires once per drain that sent anything and calls `AccountSettingsSource.refreshAfterChange()`, never `refresh()`. A refresh already running may have read the server before the send, and `refreshAfterChange` waits for it and starts a new one. Background sending when the network returns comes with WorkManager in TKA-0037.

The queue made `CacheDatabase` version 2, reached from version 1 through Room's `AutoMigration`, with both schemas exported to `app/schemas/`. `CacheDatabaseMigrationTest` opens a version 1 file under the current version without room-testing, since Room checks the migrated tables against the exported schema when it opens the file.

`picker/EntityPicker` is the project, task and tag picker that the timer and the entry edit screens will share (P1). It reads only the cache, so search and selection work offline. Offline it says that creating a project, client or task needs a connection, and the manifest holds `ACCESS_NETWORK_STATE` for that message. A tag typed by name joins the entry by name, and the server creates it when the queue sends the change. The picker's strings live in `picker_strings.xml`.

<!-- kk:related:start -->
# Related

- Related: [practice-never-wait-for-server-on-start-stop](practice-never-wait-for-server-on-start-stop.md)
- Related: [map-account-cache-and-capabilities](map-account-cache-and-capabilities.md)
- Related: [map-tokkl-toggl-api](../api-and-security/map-tokkl-toggl-api.md)
- Related: [practice-refresh-on-events-no-polling](../api-and-security/practice-refresh-on-events-no-polling.md)
<!-- kk:related:end -->
