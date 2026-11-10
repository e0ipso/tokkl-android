---
type: map
title: Catalog form restoration and project tasks
description: >-
  SavedStateHandle restores catalog drafts, and the project form owns its task
  list and online task actions.
tags:
  - ui
  - catalog
  - navigation
kk_schema_version: 3
kk_id: map-catalog-form-restoration-and-project-tasks
kk_derived_from: []
kk_relates_to:
  - map-catalog-page-loading-and-lifecycle
  - map-catalog-repositories-and-server-mutations
  - map-cached-entity-picker-and-offline-creation
kk_depends_on: []
kk_confidence: high
---
The client and project forms keep what they hold in the view model, as the entry form does: the open form is the catalog's overlay, `ClientsOverlay.New` or `Edit` with a `ClientDraft`, and `ProjectForm.New` or `Edit` with a `ProjectDraft`, and the form's fields call `editDraft`, which changes it through `CatalogModel.change`. `ProjectForm.of(project, projectCount)` opens a form with the colour the web app offers. Each view model keeps the open form, its client or project as the server sent it and its draft, in the route's `SavedStateHandle` (`SavedClientsOverlay`, `SavedProjectForm`, and the tags page's rename form or delete question as `SavedTagsOverlay`), and a view model a new process makes opens it again over the list through `CatalogModel`'s `restored` overlay, before the list loads; Save waits for the list. A restored project form selects its task list (`TasksModel.select`), which loads when the page shows (`TasksModel.shown`), not before. The composables keep only what is not the draft: a name's mistake mark and whether the colour field is open. The clients page keeps its question whether to archive a client there too, so a new process asks it again. `ClientsViewModelTest` and `ProjectsViewModelTest` pin the restore.

A project's form holds its tasks (P5), as the web app's `ProjectTasks` does. `projects/TasksModel` and `ProjectTasks.kt` draw and run the list, and `ServerTasksRepository` calls `/api/v9/workspaces/{id}/projects/{id}/tasks`. A field adds a task while the project is active, then come the open tasks and the done ones, each part sorted as Tokkl sorts them. A tap on a task opens a form that renames it and marks it done, and Delete asks first and names the task. Names stop at 255 characters, the web app's limit, which Tokkl enforces too. Task changes follow the catalog's rules: sent at once, nothing queued, every action off while offline, and a cache refresh after each accepted change so the entry picker offers it.

<!-- kk:related:start -->
# Related

- Related: [map-catalog-page-loading-and-lifecycle](map-catalog-page-loading-and-lifecycle.md)
- Related: [map-catalog-repositories-and-server-mutations](map-catalog-repositories-and-server-mutations.md)
- Related: [map-cached-entity-picker-and-offline-creation](../../data-and-offline/change-queue/map-cached-entity-picker-and-offline-creation.md)
<!-- kk:related:end -->
