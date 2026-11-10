---
type: map
title: Typed API resources and request contracts
description: >-
  TokklApi resource groups fix request shapes, typed answers and module
  boundaries.
tags:
  - api
  - network
kk_schema_version: 3
kk_id: map-typed-api-resources-and-request-contracts
kk_derived_from: []
kk_relates_to:
  - map-tokkl-toggl-api
  - map-api-execution-retries-and-errors
  - map-api-test-fixtures
kk_depends_on: []
kk_confidence: high
---
The HTTP code lives in the `:core:api` module, `core/api/src/main/java/com/github/e0ipso/tokkl/api/`.

`TokklApi` is the app's whole API contract, the requests that section 6 of Tokkl's `docs/PRD.md` lists and the app sends. It groups one suspend method per request by resource: `account` (`/me`, `/me/preferences`, `/workspaces/{id}`), `entries`, `trash`, `projects` (with their tasks), `clients`, `tags`, `reports` and `timer`, each its own class in a file named after it (`AccountApi.kt` and so on). Each method fixes its request's method, path, query, body and whether OkHttp may send it twice, and takes typed values: core:model's typed ids (`WorkspaceId`, `EntryId`, `ProjectId`, `ClientId`, `TaskId`, `TagId`), `Instant`s and `LocalDate`s, and request bodies, so core:api depends on core:model and exposes it. Bodies with a fixed shape are `@Serializable` data classes encoded with `ApiJson`, which leaves out a property that has a default and still holds it, and writes every other one, null included. That is how `TimedEntryBody` sends `"stop":null` for a running entry and leaves `created_with` out of an update. The report bodies are written field by field in `ReportsApi.kt`, because the filter fields (`ReportFilter`) sit at a place that differs by report. Each method also fixes the type its answer reads as, and core:api declares those types; no caller passes a deserializer. `GET /me` has two methods: `account.me()` reads the whole `Me` that sign-in needs, and `account.defaultWorkspace()` reads `MeWorkspace`, the default workspace and role alone, for the catalog pages and the reports. `trash.list()` reads `TrashEntry` rows, whose `id` is an `EntryId`, and `trash.probe()` reads the same route for the capability probe and answers only whether it is a list. `preferences()` answers a `MePreferences`, which reads each key by the JSON type its reader expects (`string`, `number`, `boolean`, `truthy` and `has`) and the `favorites()`, so a malformed key fails its reader alone. `savePreferences(change)` checks that the answer is a JSON object and returns nothing. `entries.bounds()` answers `EntryBounds`, the first day with an entry or null for none; a `first_date` that is neither fails as `InvalidResponse`. `WorkspaceItems.kt` holds the catalog answers (`MeWorkspace`, `WorkspaceSettings`, `WorkspaceClient`, `ProjectClient`, `WorkspaceProject`, `ProjectTask`, `WorkspaceTag`, `PurgedEntries`) and `ReportsApi.kt` the report rows (`ReportSearchRow`, `ReportSearchEntry`, `WeeklyReportRow`). Each feature's server repository, such as `ServerTagsRepository`, `ServerProjectsRepository`, `ServerReportsRepository` or `ServerTrashRepository`, calls the resources itself and maps those answers to core:model or page types. `TokklApi` builds the groups over one internal `ApiTransport`, so a caller sends only the requests the groups name. `SharedHttpClient.api` is the one place that makes a `TokklApi`: `Session` calls it with its trust and success listeners, and sign-in through `ApiConnector`, which `SharedHttpClient` implements. `ApiClientProvider` and `Session.api()`, both suspend, hand out `TokklApi`; the account refresh reaches it through its narrow gateway, `ServerAccountReads`, the queue's `ChangeSender` and `DeleteUndoer` take the `TokklApi` and call its `entries` and `trash` groups, and nothing outside `:core:api` builds a URL or a JSON body. No public signature of `:core:api` names an OkHttp type: the transport, the endpoint builders (`ServerUrl.apiUrl`, `appUrl` and `reportsUrl`), `SharedHttpClient`'s OkHttp client and `sendingOnce` are internal, and `WithHeaders` gives a header by name. OkHttp and kotlinx-serialization-json are implementation dependencies of the module; kotlinx-serialization-core is an api one, because the API's types are serializable, and `ApiJson` is a `StringFormat` that reads and writes them as text, as the timer state's store and stream do. The one JSON tree left in a public signature is `reports.appSummary`'s `JsonObject`, whose unknown fields the summary shows, so feature:reports names the JSON library itself, as the features and the cache that write their own JSON do. To add a request, add a method to its group's class, and its exact bytes to `TokklApiTest`, which pins method, path with query, and body for every method.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-toggl-api](../map-tokkl-toggl-api.md)
- Related: [map-api-execution-retries-and-errors](map-api-execution-retries-and-errors.md)
- Related: [map-api-test-fixtures](map-api-test-fixtures.md)
<!-- kk:related:end -->
