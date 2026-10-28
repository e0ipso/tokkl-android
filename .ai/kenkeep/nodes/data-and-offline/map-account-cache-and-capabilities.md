---
type: map
title: >-
  Offline cache and capabilities: AccountCache, HistoryWindow,
  CapabilitiesSource
description: >-
  One refresh reads /me with related data and /me/preferences, then fills the
  Room cache and the capability flags that screens read offline.
tags:
  - offline
  - cache
  - room
  - api
  - compatibility
kk_schema_version: 3
kk_id: map-account-cache-and-capabilities
kk_derived_from: []
kk_relates_to:
  - map-account-settings-source-and-account-time
  - practice-check-capabilities-not-server-version
  - practice-never-wait-for-server-on-start-stop
  - map-timer-state-holder-and-sync-service
  - map-sign-out-ordering-and-account-cleanup
  - map-trash-capability-and-server-operations
  - map-ordered-entry-sending-and-refusals
  - map-offline-entry-mutations-and-local-id-mapping
kk_depends_on: []
kk_confidence: high
---
`cache/` keeps what the app last loaded from the server in the Room database `account_cache.db` (`CacheDatabase`): projects, clients, tags, tasks, favorites and the time entries of `HistoryWindow`, which is today and the 13 days before it, counted in the account's timezone. Screens read the flows of `AccountCache`, which come from the phone and never wait for the network. The account settings are not cached here; `AccountSettingsSource` stays their one source. The same database holds the queue of time entry changes, and a queued change shows over the cached row until the server has it.

`AccountCache.refresh` replaces the rows with one `ServerSnapshot`. A list that is null, because its request failed or the server does not offer it, keeps its rows, and an `SQLiteException` during the write keeps the whole cache as it was. When the phone cannot resolve the account's timezone, the refresh reads no entries and prunes none. A server without favorites, such as Toggl Track, gets an empty favorites list.

`capabilities/Capabilities` holds the Tokkl-only flags `trash`, `restore`, `favorites`, `timeResolution`, `negatedFilters` and `timerState`. `favorites` follows Tokkl's `role` in `/me` and also covers Tokkl's other preference keys that the app reads and writes, such as `timerView`. `timeResolution` follows `time_resolution_minutes`. `negatedFilters` also follows `role`: report requests may carry `exclude_project_ids`, `exclude_client_ids` and `exclude_tag_ids`, which every Tokkl release takes and Toggl Track's Reports API v3 lacks. `probeTrash` sends `GET /api/app/trash`: a list is `PRESENT`, a 404 or a 2xx that is not a list is `ABSENT`, and anything else is `UNKNOWN`, which keeps the trash and restore flags as they were. Restore lives in the same router as the trash, so the trash probe decides it too. A trash call outside the probe that gets the same 404 calls `CapabilitiesSource.trashMissing()`, which turns both flags off. `probeTimerState` sends `GET /api/app/timer` the same way: a body that parses as a timer state is `Present`, a 404 or a body that is not a timer state is `Absent`, and anything else is `Unknown`, which keeps the flag as it was. `timerState` is false on Toggl Track and on Tokkl before 1.10.0. When it is false, Pomodoro leaves the navigation, Pause and Resume leave every timer surface and the sync service does not run. A state the probe read goes to the timer state source, so the refresh sends no second request for it (see the timer state node). `CapabilitiesStore` keeps the flags in the private preferences file `capabilities`, and features read them from `CapabilitiesSource`.

One refresh feeds all of this. `AccountSettingsSource.refresh`, called when the app comes to the foreground or the network returns (see the refresh practice node), after sign-in and after an accepted trust prompt, sends `GET /api/v9/me?with_related_data=true` and `GET /api/v9/me/preferences` once. Its `onRefresh`, wired in `TokklApplication`, refreshes the capabilities with the trash and timer probes and then the cache, which adds `GET /api/v9/me/time_entries` for the history window. A feature that needs `/me` data takes it from this refresh instead of sending its own request. `MePreferences` keeps the raw JSON object, and each reader decodes only its own key, such as `duration_format`, `timeofday_format` and `collapseTimeEntries` for the settings, `timerView` for the Timer page's view and `favorites` for the cache, so a malformed key fails that reader alone.

Sign-out calls `AccountCache.clear()`, which empties the queue with the cache, after the sender has stopped, and clears the capabilities and their store (see the sign-out node).

<!-- kk:related:start -->
# Related

- Related: [map-account-settings-source-and-account-time](map-account-settings-source-and-account-time.md)
- Related: [practice-check-capabilities-not-server-version](../api-and-security/practice-check-capabilities-not-server-version.md)
- Related: [practice-never-wait-for-server-on-start-stop](practice-never-wait-for-server-on-start-stop.md)
- Related: [map-timer-state-holder-and-sync-service](map-timer-state-holder-and-sync-service.md)
- Related: [map-sign-out-ordering-and-account-cleanup](../screens/session-and-navigation/map-sign-out-ordering-and-account-cleanup.md)
- Related: [map-trash-capability-and-server-operations](../screens/session-and-navigation/map-trash-capability-and-server-operations.md)
- Related: [map-ordered-entry-sending-and-refusals](change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
<!-- kk:related:end -->
