---
type: map
title: Catalog repositories and server mutations
description: >-
  Repositories map server rows, ServerChanges finishes accepted changes and
  cache refreshes, and stale loads cannot overwrite mutations.
tags:
  - api
  - catalog
  - offline
kk_schema_version: 3
kk_id: map-catalog-repositories-and-server-mutations
kk_derived_from: []
kk_relates_to:
  - map-api-client-shared-okhttp-serverurl-and-errors
  - map-account-cache-and-capabilities
  - map-offline-entry-mutations-and-local-id-mapping
  - map-ordered-entry-sending-and-refusals
  - map-background-queue-work-and-empty-queue-cancellation
  - map-remote-running-conflicts-and-lost-create-responses
  - map-queued-delete-and-short-stop-undo
  - map-room-queue-migration-continuity
  - map-cached-entity-picker-and-offline-creation
  - map-catalog-page-loading-and-lifecycle
  - map-catalog-form-restoration-and-project-tasks
kk_depends_on: []
kk_confidence: high
---
Nothing is queued: offline, or with the server out of reach, nothing changes and the page says why. After each change the server accepts, `ServerChanges`, which `CatalogModel` takes beside the `AccountSettingsSource`, calls `refreshAfterChange()` on the `CacheRefresh`, so the offline cache catches up and the picker, the history and the reports show the change. `managedWorkspace` takes the workspace from the stored account settings, which `CatalogModel` reads through `AccountSettingsSource.defaultWorkspaceId()`, or from `GET /api/v9/me` when the stored settings predate that field. `CatalogModel` reads through a `CatalogRepository`, which finds the workspace and loads the list. Each page's view model gets its repository from Koin as a class: `ServerClientsRepository`, `ServerProjectsRepository`, `ServerTasksRepository` and `ServerTagsRepository`, and for the trash and the reports `ServerTrashRepository` and `ServerReportsRepository`. The view models' and `TasksModel`'s tests run them against a MockWebServer. The HTTP contract stays in the repository and server tests, which use a MockWebServer: `ClientsRequestsTest`, `ProjectsRequestsTest`, `TasksRequestsTest`, `TagsRequestsTest`, `ReportsServerTest` and `AppSummaryServerTest`. A server repository calls the API resources itself through core:session's `signedIn`, an extension on `ApiClientProvider` that fails a signed-out call with `AuthFailed` before any request; `ApiClientProvider.managedWorkspace`, in core:catalog's `CatalogModel.kt`, finds the catalog pages' workspace with it. `CatalogModel.send` and the trash page send each change through core:session's `ServerChanges`. It runs the change in the application scope and, once the server accepts it, starts `refreshAfterChange()`, so the change and the refresh after it land when the page goes meanwhile. The model awaits the answer and shows it only while its list is open. A load that started before a change the server accepted drops its answer and loads again, so a reload cannot put back the list from before the change. The model counts the lists it opens, and `close()` moves the count on, so a late answer for project A's task list that arrives after the form moved to project B changes nothing in B's list; the refresh after it still runs. `TasksModelTest` pins that.

`ServerProblem` in core:session turns an `ApiError` into what a page shows, and `problemText` words it with core:session's strings, so the timer's notices in core:timerstate say a failure in the same words. A 502, 503 or 504 counts as the server being out of reach, because a reverse proxy answers those while Tokkl is down. A 4xx whose body is a JSON string becomes `Refused` with the server's own reason, such as "Name has already been taken". `CatalogPage` is the scrolling frame with a snackbar that names the accepted change in the web app's words, such as "Created Website". `ConfirmDialog` asks before a change that touches more than the item, as the web app's `ConfirmModal` does. Its Cancel and confirm buttons stack at full width, because `AlertDialog`'s own button row put one over the other at 200% font scale.

`projects/`, `clients/` and `tags/` each hold a page's screen, model, view model, repository and server calls. Projects and clients are created, edited, archived and restored. Tags are created, renamed and deleted. core:api reads each answer into its own type, such as `WorkspaceTag` or `WorkspaceClient`, and each server repository maps it to the model's types, the same the cache gives for its offline rows: `ServerTagsRepository` gives `Tag`, `ServerClientsRepository` gives `Client` with its notes, which the cache leaves null, and `ServerTasksRepository` gives `Task`. The projects page keeps its own `ProjectDetails` (in `ProjectDetails.kt`), a project with its client's name, rate, currency and estimate, which the model's `Project` lacks. The pages' workspace is a `WorkspaceId`, and their ids are the model's typed ids.

<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-execution-retries-and-errors](../../api-and-security/api-client/map-api-execution-retries-and-errors.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-test-fixtures](../../api-and-security/api-client/map-api-test-fixtures.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-shared-http-client-and-configured-server-security](../../api-and-security/api-client/map-shared-http-client-and-configured-server-security.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-typed-api-resources-and-request-contracts](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md)
- Related: [map-account-cache-and-capabilities → map-account-cache-storage-and-history-window](../../data-and-offline/account-cache/map-account-cache-storage-and-history-window.md)
- Related: [map-account-cache-and-capabilities → map-account-refresh-steps-and-preference-readers](../../data-and-offline/account-cache/map-account-refresh-steps-and-preference-readers.md)
- Related: [map-account-cache-and-capabilities → map-capability-probes-and-persisted-feature-flags](../../data-and-offline/account-cache/map-capability-probes-and-persisted-feature-flags.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-ordered-entry-sending-and-refusals](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-background-queue-work-and-empty-queue-cancellation](../../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md)
- Related: [map-remote-running-conflicts-and-lost-create-responses](../../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md)
- Related: [map-queued-delete-and-short-stop-undo](../../data-and-offline/change-queue/map-queued-delete-and-short-stop-undo.md)
- Related: [map-room-queue-migration-continuity](../../data-and-offline/change-queue/map-room-queue-migration-continuity.md)
- Related: [map-cached-entity-picker-and-offline-creation](../../data-and-offline/change-queue/map-cached-entity-picker-and-offline-creation.md)
- Related: [map-catalog-page-loading-and-lifecycle](map-catalog-page-loading-and-lifecycle.md)
- Related: [map-catalog-form-restoration-and-project-tasks](map-catalog-form-restoration-and-project-tasks.md)
<!-- kk:related:end -->
