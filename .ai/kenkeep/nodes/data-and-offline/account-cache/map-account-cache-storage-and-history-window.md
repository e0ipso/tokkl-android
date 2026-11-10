---
type: map
title: Account cache storage and history window
description: >-
  AccountCache exposes Room-backed model flows, while server refresh preserves
  unavailable data and account isolation.
tags:
  - offline
  - cache
  - room
kk_schema_version: 3
kk_id: map-account-cache-storage-and-history-window
kk_derived_from: []
kk_relates_to:
  - map-account-settings-source-and-account-time
  - practice-never-wait-for-server-on-start-stop
  - map-sign-out-ordering-and-account-cleanup
  - map-ordered-entry-sending-and-refusals
  - map-offline-entry-mutations-and-local-id-mapping
  - map-account-refresh-steps-and-preference-readers
kk_depends_on: []
kk_confidence: high
---
`cache/` keeps what the app last loaded from the server in the Room database `account_cache.db` (`CacheDatabase`): projects, clients, tags, tasks, favorites and the time entries of `HistoryWindow`, which is today and the 13 days before it, counted in the account's timezone. Screens read the flows of `AccountCache`, which come from the phone and never wait for the network. `AccountCache(database)` only reads and clears the cache; `ServerCacheRefresh` writes the server's answers into it. The flows give the model's types of `:core:model`, such as `TimeEntry`, `Project` and `Favorite`. The Room rows (`CachedTimeEntry` and the other `Cached*` entities, `PendingChange`, `ShortStopUndo`) never leave `:core:cache`, and `CacheModels.kt` is the one mapping between them and the model. The account settings are not cached here; `AccountSettingsSource` stays their one source. The same database holds the queue of time entry changes, and a queued change shows over the cached row until the server has it.

`ServerCacheRefresh.refresh` replaces the rows with one `ServerSnapshot`. A list that is null, because its request failed or the server does not offer it, keeps its rows, and an `SQLiteException` during the write keeps the whole cache as it was. When the phone cannot resolve the account's timezone, the refresh reads no entries and prunes none. A server without favorites, such as Toggl Track, gets an empty favorites list.

Every call of `AccountCache` is main-safe: the flows read off the main thread, and `serverIdsOf` and `clear` suspend and switch to the IO dispatcher themselves. `replaceFavorites`, which `FavoritesServer` takes as a function, suspends: it takes the save's `AccountContext` and writes on the cache's I/O dispatcher inside that context's `ifCurrent`, which holds sign-out back until the write returns, and answers false once the account has ended. What must run under a caller's lock, the running entry now and the running entry check's read and write, goes through `CurrentEntryCache`, which `RoomCurrentEntries` implements and whose methods carry `@WorkerThread`: the timer state holder, the running check's worker and the tile call it on a worker thread. Sign-out calls `AccountCache.clear()`, which empties the queue with the cache, after the sender has stopped, and clears the capabilities and their store (see the sign-out node).

<!-- kk:related:start -->
# Related

- Related: [map-account-settings-source-and-account-time → map-account-settings-loading-and-refresh](../account-settings-and-time/map-account-settings-loading-and-refresh.md)
- Related: [map-account-settings-source-and-account-time → map-account-time-fields-and-validation](../account-settings-and-time/map-account-time-fields-and-validation.md)
- Related: [practice-never-wait-for-server-on-start-stop](../practice-never-wait-for-server-on-start-stop.md)
- Related: [map-sign-out-ordering-and-account-cleanup](../../screens/session-and-navigation/map-sign-out-ordering-and-account-cleanup.md)
- Related: [map-ordered-entry-sending-and-refusals](../change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-account-refresh-steps-and-preference-readers](map-account-refresh-steps-and-preference-readers.md)
<!-- kk:related:end -->
