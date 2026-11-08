---
type: practice
title: 'Give composables plain state from view models, with previews beside them'
description: >-
  Screens read one immutable state from their view model; leaf composables get
  plain values and callbacks, never the cache or Koin. Light and dark previews
  live next to each composable.
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
A screen's route, the entry a feature adds in its `FeatureEntries`, gets its view models with `koinViewModel()`, or with `koinViewModel(viewModelStoreOwner = LocalSignedInSession.current)` for the signed-in session's. A page that loads from the server has a route composable, such as `ClientsPage` in `ClientsEntries.kt`, which takes the view model, and a screen, such as `ClientsScreen`, which takes its state and callbacks. The navigation host takes plain values that MainActivity injects, and core:navigation has no Koin. Below that, no composable calls `koinInject`, takes `AccountCache` or a `Flow`, or computes data from the cache. Each view model exposes one `@Immutable` state for its screen with the derived data already in it, shared with `whileShown` from `platform/Definitions.kt`, and the screen collects it with `collectAsStateWithLifecycle`. The picker gets a `PickerSource` and whether the phone is offline, the timer bar a `TimerPageState` and a `TimerBarActions`, the signed-in frame a `SignedInState`. A value that ticks, such as the Timer page's totals, is a flow of its own, collected where only its row reads it.

A composable passes its view model's functions on as callbacks, never the view model, so detekt's `ViewModelForwarding` needs no allow-list. Effects that tie a model to the page take callbacks too, and the route composable calls them: `PageVisitEffect(onOpen, onClose)` and `TaskListEffect`.

`collectAsState` stays only where a value has to be current on the first frame back from the background, or where there is no lifecycle owner, and each such call carries a comment that says which: the Glance widgets, the signed-in host's timer state, the navigation host's sign-outs and capabilities, sign-in's result, the tile dialog's controls and the Pomodoro alarm's phase. Compose draws no frame while the activity is stopped, so collecting only while started hides nothing the user could see then; on the way back, a value can be a frame late until collection resumes, which is why those calls keep collecting.

Each module that owns a composable keeps private `@PreviewLightDark` functions in an `XPreviews.kt` file beside it, with small sample states in the same file. The compose convention plugin adds `ui-tooling-preview` to every Compose module and `ui-tooling` to debug builds only.
