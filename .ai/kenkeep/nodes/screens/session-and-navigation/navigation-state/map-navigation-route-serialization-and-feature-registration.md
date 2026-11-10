---
type: map
title: Navigation route serialization and feature registration
description: >-
  Feature serializers preserve route identities and numeric IDs, while
  navigation contracts isolate feature screens and entries.
tags:
  - android
  - ui
  - navigation
kk_schema_version: 3
kk_id: map-navigation-route-serialization-and-feature-registration
kk_derived_from: []
kk_relates_to:
  - map-navigation-stores-and-restored-session-guard
  - map-external-navigation-requests-and-intent-consumption
kk_depends_on: []
kk_confidence: high
---
`TokklNavHost` keeps a Navigation 3 back stack of `Route`s, saved with their serializers, so it survives configuration changes and process death. `Route` is an open interface in `:core:navigation`, which declares only `Route.Timer`, at the bottom of every signed-in stack, and `Route.Entry`, the entry form that the timer and sync features both open. Each feature declares its other routes as `@Serializable` objects or classes, such as `ClientsRoute`, `FullscreenTimerRoute` or `SignInRoute`, and registers them in `FeatureRoutes.registerRoutes`. The host saves the stack with `NavBackStackSerializer(PolymorphicSerializer(Route::class))` and a `SavedStateConfiguration` whose serializers module `routeSerializers` builds from every feature's `FeatureRoutes`, so a feature's new route needs no change in core. `SavedBackStackTest` round-trips a stack of every route through that serializer; a route a feature forgets to register fails it. `FeatureRoutesTest` assembles the navigation from the app's Koin graph and fails when a route a feature registers, or names as a top-level page, has no entry or no serializer. The routes that moved to their features on 2026-11-08 keep, with `@SerialName`, the serial names they had in the sealed `Route` before (`com.github.e0ipso.tokkl.navigation.Route.Clients` and so on), so a stack saved by an earlier version reads as before; `SavedBackStackTest` checks those names. Each route supplies an explicit `contentKey`; entry forms use their unique opening id, and sign-in keys include the account. Debug text does not determine saved-state identity. A route marks itself `focused`, as the fullscreen timer does, to hide the bar, and the entry form above it hides it too. Routes carry ids, never entries: the entry form's `EntryOpening` holds the model's `EntryId` and `WorkspaceId`, which serialize as their numbers. `EntryFormOpenings` keeps in memory the copy of an entry the cache lacks, and its `take` gives every route its opening, so a form restored in a new process opens on its route and its own saved state. `:core:navigation` depends on `:core:model`, `:core:ui` and Navigation 3 only: no session, no cache and no Koin. No feature names another's screens: each feature with a screen binds a `FeatureRoutes` in its Koin module that names its top-level pages, registers its routes and adds their entries, and the host builds its entry provider from every one Koin holds. The contract has no default members, so a feature without a page says so with an empty `pages`. The app's navigation module gathers every feature's `FeatureRoutes`, `FeatureRequests` and `FrameContributions` into one `FeatureNavigation`, which MainActivity injects and hands the host. A screen opens another feature's screen through the `Navigator`, by its route.

<!-- kk:related:start -->
# Related

- Related: [map-navigation-stores-and-restored-session-guard](map-navigation-stores-and-restored-session-guard.md)
- Related: [map-external-navigation-requests-and-intent-consumption](map-external-navigation-requests-and-intent-consumption.md)
<!-- kk:related:end -->
