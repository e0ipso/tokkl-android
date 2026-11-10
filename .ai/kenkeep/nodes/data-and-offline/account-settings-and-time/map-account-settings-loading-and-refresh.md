---
type: map
title: Account settings loading and refresh
description: >-
  Process-wide settings loading and account refresh preserve local changes made
  during server reads.
tags:
  - settings
  - api
  - offline
  - cache
kk_schema_version: 3
kk_id: map-account-settings-loading-and-refresh
kk_derived_from: []
kk_relates_to:
  - map-sign-in-request-and-credential-admission
  - map-session-stores-and-pre-frame-preferences
  - map-sign-out-ordering-and-account-cleanup
  - map-saveable-navigation-stack-and-session-guard
  - map-trash-capability-and-server-operations
  - map-connection-trust-store-pinning-and-prompts
  - practice-refresh-on-events-no-polling
  - map-account-cache-and-capabilities
  - map-account-time-fields-and-validation
kk_depends_on: []
kk_confidence: high
---
`session/AccountSettingsSource` is the one place screens read the settings from. A reader of one part takes that part: the `state` or `loaded` flow or its `store` function. `CatalogModel` takes the source and reads `defaultWorkspaceId()` for the catalog pages. The app's Koin account module builds it, one for the process, over `AccountSettingsStore`, which keeps them in the private preferences file `account_settings`, so screens have them offline and after a restart. It loads them at process start, off the main thread; code outside the screens that may run before then reads `current()` or the `loaded` flow, which wait for the load, and its writes suspend and run one at a time under a `Mutex`. `session/AccountRefresh` reads them again and stores them in the source. Coming to the foreground (through `ForegroundRefresh`, not on a rotation), the network's return (`refreshOnReconnect()`) and each sign-in call it, so a change made in the web app reaches the phone. It implements `CacheRefresh`, which pages and steps take. After a change the app sent itself, callers use `refreshAfterChange()`, which waits for a refresh already running, since it may have read the server before the change, and then starts a new one. A refresh sends `GET /api/v9/me?with_related_data=true` and `GET /api/v9/me/preferences` once and hands both answers to its `AccountRefreshSteps`, with the `ServerAccountReads` over `TokklApi` it made them through (`AccountRefreshTest` answers from a MockWebServer), where the capabilities and the cache refresh from them. A second `refresh()` while one runs gets the running job instead of sending another request. A failed request or write keeps what is stored, failed preferences keep the stored duration format, and a refresh drops its answer when sign-in stored newer settings while the request ran.

`ServerCacheRefresh.refresh` captures cached entry rows and pending entry IDs together before reading entry history. Its write transaction compares that snapshot with the current rows and preserves additions, changes, remaps and removals during the read. IDs pending at the start stay protected even if sending left an already-stopped row unchanged or an already-deleted row absent. Existing local-to-server mappings also protect a pending local ID that was sent and removed during the read. Remaining queued changes apply on top in order. Other entries still follow server values and history pruning; a later refresh without intervening changes accepts server state. The request holds no database transaction, so Start, Stop and the sender remain independent of the network read.

<!-- kk:related:start -->
# Related

- Related: [map-sign-in-request-and-credential-admission](../../screens/session-and-navigation/map-sign-in-request-and-credential-admission.md)
- Related: [map-session-stores-and-pre-frame-preferences](../../screens/session-and-navigation/map-session-stores-and-pre-frame-preferences.md)
- Related: [map-sign-out-ordering-and-account-cleanup](../../screens/session-and-navigation/map-sign-out-ordering-and-account-cleanup.md)
- Related: [map-saveable-navigation-stack-and-session-guard → map-external-navigation-requests-and-intent-consumption](../../screens/session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md)
- Related: [map-saveable-navigation-stack-and-session-guard → map-navigation-route-serialization-and-feature-registration](../../screens/session-and-navigation/navigation-state/map-navigation-route-serialization-and-feature-registration.md)
- Related: [map-saveable-navigation-stack-and-session-guard → map-navigation-stores-and-restored-session-guard](../../screens/session-and-navigation/navigation-state/map-navigation-stores-and-restored-session-guard.md)
- Related: [map-trash-capability-and-server-operations](../../screens/session-and-navigation/map-trash-capability-and-server-operations.md)
- Related: [map-connection-trust-store-pinning-and-prompts](../../api-and-security/map-connection-trust-store-pinning-and-prompts.md)
- Related: [practice-refresh-on-events-no-polling](../../api-and-security/practice-refresh-on-events-no-polling.md)
- Related: [map-account-cache-and-capabilities → map-account-cache-storage-and-history-window](../account-cache/map-account-cache-storage-and-history-window.md)
- Related: [map-account-cache-and-capabilities → map-account-refresh-steps-and-preference-readers](../account-cache/map-account-refresh-steps-and-preference-readers.md)
- Related: [map-account-cache-and-capabilities → map-capability-probes-and-persisted-feature-flags](../account-cache/map-capability-probes-and-persisted-feature-flags.md)
- Related: [map-account-time-fields-and-validation](map-account-time-fields-and-validation.md)
<!-- kk:related:end -->
