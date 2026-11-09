---
type: map
title: 'core:model: what the screens read, with typed ids'
description: >-
  TimeEntry, Project and the rest with value class ids; Room rows stay in
  core:cache and API wire types stay below the screens.
tags:
  - cache
  - room
  - ui
  - api
kk_schema_version: 3
kk_id: map-core-model-and-typed-ids
kk_derived_from: []
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-offline-entry-mutations-and-local-id-mapping
  - map-catalog-pages-projects-clients-tags
  - map-saveable-navigation-stack-and-session-guard
  - map-gradle-build-sdk-levels-kotlin-and-memory
kk_depends_on: []
kk_confidence: high
---
`:core:model`, package `model`, holds what the screens show and change: `TimeEntry`, `Project`, `Client`, `Task`, `Tag`, `Favorite`, `EntryFields`, `EntryWithSendState` and `RunningEntryIdentity`, with `noTimeDay` and `fields()`. It is a Kotlin/JVM module, alone in the bottom layer, and needs no other app module, only kotlinx-serialization-core for the ids. `:core:api` depends on it and exposes it, because its calls take these ids and its answers carry them.

The ids are value classes: `EntryId`, `WorkspaceId`, `ProjectId`, `ClientId`, `TaskId` and `TagId`, so a project id cannot go where a task id belongs. Each is `@Serializable` and serializes as its number, so routes and core:api's request and answer types hold one. Its `toString` is the number, so text keys and logs built from an id read as before. `LocalEntryId` is the negative id of an entry the phone created, and only `EntryId.local` makes one (see the change queue node).

Where each boundary maps:

- `:core:cache` maps its Room rows to the model in `CacheModels.kt`, and `ServerTimeEntry.toTimeEntry()` reads a server entry. The rows keep `Long` columns. Room 2.8 binds value class columns (on 2026-11-08 KSP generated `entity.id.value` for a `TagId` column and the exported schema stayed the same), but typed rows would retype the queue, the sender and their tests with no change outside the module.
- core:api reads each catalog answer into a type of its own in `WorkspaceItems.kt`, such as `WorkspaceTag`, `WorkspaceClient`, `WorkspaceProject` and `ProjectTask`, with typed ids, and the feature's server repository (`ServerTagsRepository`, `ServerClientsRepository`, `ServerProjectsRepository`, `ServerTasksRepository`) maps it to the model or a page type. `ServerReportsRepository` maps `ReportSearchRow`, `ReportSearchEntry` and `WeeklyReportRow` the same way. Reports keep their filter ids as numbers, the shape of their requests.
- The server's timer state (`TimerState` and its parts) stays core:api's wire type, which core:timerstate stores as the server sent it. Above it the screens read `HeldTimerState`, whose pause chain, Pomodoro session and discarded pomodoro are the model's `PauseChain`, `PomodoroSession`, `PomodoroPhase` and `DiscardedPomodoro`, mapped once by `TimerState.held` in `timerstate/TimerStateMapping.kt`. `PhaseKind` and `PhaseTransition` live in core:model, and the wire types use them too. The trash's `TrashEntry`, whose `id` is an `EntryId`, stays core:api's type, and the trash page only shows its rows.

`PackageLayersTest` checks two rules: no main source outside `:core:cache` names a Room row, and no feature file that declares a view model or a composable imports a `@Serializable` class of `:core:api`, apart from its `SERVER_STATE` list, which holds only `TrashEntry`.

A `LazyColumn` key or a `rememberSaveable` value goes into a Bundle, which cannot hold a value class, so it takes `id.value`, as the tags list and the project form's client do. Intent extras and Glance action parameters hold the number too, with 0 for no entry where `TimerTarget.entryId` is null. JUnit's `assertEquals(Object, Object)` compiles with an `EntryId` on one side and a `Long` on the other, then fails at run time: wrap the expected number in its id type, or compare `id.value`.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities](map-account-cache-and-capabilities.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-catalog-pages-projects-clients-tags](../screens/map-catalog-pages-projects-clients-tags.md)
- Related: [map-saveable-navigation-stack-and-session-guard](../screens/session-and-navigation/map-saveable-navigation-stack-and-session-guard.md)
- Related: [map-gradle-build-sdk-levels-kotlin-and-memory](../build-and-tooling/map-gradle-build-sdk-levels-kotlin-and-memory.md)
<!-- kk:related:end -->
