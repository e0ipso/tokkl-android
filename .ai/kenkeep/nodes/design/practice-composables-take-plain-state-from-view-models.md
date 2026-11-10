---
type: practice
title: Give composables plain state from view models
description: >-
  Screens read one immutable state from their view model; leaf composables get
  plain values and callbacks, never the cache or Koin.
tags:
  - ui
  - compose
  - architecture
kk_schema_version: 3
kk_id: practice-composables-take-plain-state-from-view-models
kk_derived_from: []
kk_relates_to:
  - map-ui-components-and-compose-layout-gotchas
  - map-catalog-pages-projects-clients-tags
  - map-cached-entity-picker-and-offline-creation
  - map-timer-today-and-this-week-totals
kk_depends_on: []
kk_confidence: high
---
A screen's route, the entry a feature adds in its `FeatureRoutes`, gets its view models with `koinViewModel()`, or with `koinViewModel(viewModelStoreOwner = LocalSignedInSession.current)` for the signed-in session's. A page that loads from the server has a route composable, such as `ClientsPage` in `ClientsEntries.kt`, which takes the view model, and a screen, such as `ClientsScreen(page, actions)`, which takes its page state and its actions. The navigation host takes plain values that MainActivity injects, and core:navigation has no Koin. Below that, no composable calls `koinInject`, takes `AccountCache` or a `Flow`, or computes data from the cache. Each screen reads one page state from its view model, `@Immutable`, with the values derived from it already worked out, shared with `whileShown` from `platform/AppDispatcher.kt`, and collects it with `collectAsStateWithLifecycle`. The parts a view model combines into it stay private, or `internal` where the module's tests read them. The entry form reads `EntryFormViewModel.page`, an `EntryFormPage` with the form, the picker and whether the phone is offline; the Pomodoro page's `page` carries its `picker` and `offline` too; Reports reads `ReportsViewModel.page`. `TagsPageState`, `ClientsPageState`, `ProjectsPageState` and `TrashPageState` work out `serverNeeded`, `canChange`, the rows, `hasNotices`, `formOpen`, `done` and the trash's `entries`, and `EntryFormState.Open.canChange` says whether the form's fields can change, so no screen computes them. The picker gets a `PickerSource` and whether the phone is offline, the timer bar a `TimerPageState` and a `TimerBarActions`, the signed-in frame a `SignedInState`. A value that ticks, such as the Timer page's totals, is a flow of its own, collected where only its row reads it.

A composable passes its view model's functions on as callbacks, never the view model, so detekt's `ViewModelForwarding` needs no allow-list. No effect tells a model that its page opened or closed: a page that loads from the server learns that it shows from its own shared state (`stateInPage`), and goes with its view model when its route leaves the back stack.

`collectAsState` stays only where a value has to be current on the first frame back from the background, or where there is no lifecycle owner, and each such call carries a comment that says which: the Glance widgets, the signed-in host's timer state, the navigation host's sign-outs and capabilities, sign-in's result, the tile dialog's controls and the Pomodoro alarm's phase. Compose draws no frame while the activity is stopped, so collecting only while started hides nothing the user could see then; on the way back, a value can be a frame late until collection resumes, which is why those calls keep collecting.

Screens have no previews. Only core:ui keeps private `@PreviewLightDark` functions, for the shared components, in `ComponentPreviews.kt`, with small sample states in the same file. The compose convention plugin adds `ui-tooling-preview` to every Compose module and `ui-tooling` to debug builds only.
