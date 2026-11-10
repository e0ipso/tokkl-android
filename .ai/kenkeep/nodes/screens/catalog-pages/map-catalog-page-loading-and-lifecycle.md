---
type: map
title: Catalog page loading and lifecycle
description: >-
  PageLoads refreshes open catalog pages while retaining rows and forms, and
  avoids rotation requests.
tags:
  - ui
  - catalog
  - lifecycle
kk_schema_version: 3
kk_id: map-catalog-page-loading-and-lifecycle
kk_derived_from: []
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-catalog-form-restoration-and-project-tasks
  - map-catalog-repositories-and-server-mutations
kk_depends_on: []
kk_confidence: high
---
`catalog/` holds what the Projects, Clients and Tags pages share (`docs/PRD.md` P2 to P4). `CatalogModel` loads a page's items from the account's default workspace each time the page opens, when the phone comes back online and when the app comes back to the foreground with the page open, and sends each change at once. Every create of a project, client, tag or task goes through `createOnce` in `catalog/CreateOnce.kt`, which finds the item a create with a lost answer made by reading the list again, so a retry never meets Tokkl's duplicate-name refusal (see the change queue node). `CatalogModel` is a `PageLoads`: each page's view model shares its page state with `stateInPage` from `platform/PageLoads.kt` (`whileShown`, five seconds), which calls `shown` when a screen starts reading the state (the first time, or after none read it for five seconds, as when the Pomodoro alarm or the tile's dialog covered the page), `returned` when the app comes back to the foreground through `ProcessLifecycleOwner` while a screen reads it, and `reconnected` on each network return while a screen reads it. A rotation keeps the view model and its sharing, so turning the phone sends no request. A load keeps the list and the open form or question on screen until the answer comes, so a page that shows again, after a trip to the background or from under another activity, keeps what the user had open and loads again for it; the projects page also loads the open form's tasks again. `reconnected` loads only while the page could not reach the server. What a page loaded and has open goes with its view model, the route's own, when the route leaves the back stack. `CatalogModel.close()` resets a list, which only the project form's task list does, as its form closes. `TagsViewModelTest`, `ClientsViewModelTest`, `ProjectsViewModelTest` and `TrashViewModelTest` pin that the open form or question survives the screen hidden for longer than five seconds.

Each page's route composable, `ClientsPage`, `ProjectsPage` or `TagsPage` in the feature's `XEntries.kt`, takes the view model and collects its page state; `ClientsScreen`, `ProjectsScreen` and `TagsScreen` take the page state and the actions, as in `TagsScreen(page, actions)`. Each page's view model keeps its `CatalogModel` state internal and exposes one page state, `TagsPageState`, `ClientsPageState` or `ProjectsPageState`: the catalog with the offline state and the cached rows of the page's workspace, and the values the screen derives from them, `serverNeeded` and `canChange` on each, the tag `rows` and `hasNotices` on the tags page, `formOpen` on the clients page and the task list's or the catalog's `done` on the projects page. `TagsPageStateTest` pins the tags page's derived values. The projects view model opens a project's task list when its form shows a project the server sent, and closes it when the form closes.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities → map-account-cache-storage-and-history-window](../../data-and-offline/account-cache/map-account-cache-storage-and-history-window.md)
- Related: [map-account-cache-and-capabilities → map-account-refresh-steps-and-preference-readers](../../data-and-offline/account-cache/map-account-refresh-steps-and-preference-readers.md)
- Related: [map-account-cache-and-capabilities → map-capability-probes-and-persisted-feature-flags](../../data-and-offline/account-cache/map-capability-probes-and-persisted-feature-flags.md)
- Related: [map-catalog-form-restoration-and-project-tasks](map-catalog-form-restoration-and-project-tasks.md)
- Related: [map-catalog-repositories-and-server-mutations](map-catalog-repositories-and-server-mutations.md)
<!-- kk:related:end -->
