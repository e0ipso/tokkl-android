---
type: map
title: Saveable navigation stack and session guard
description: >-
  Destination names survive restoration, while the host checks the session and
  presents trust prompts.
tags:
  - android
  - ui
  - navigation
kk_schema_version: 3
kk_id: map-saveable-navigation-stack-and-session-guard
kk_derived_from: []
kk_relates_to:
  - map-connection-trust-store-pinning-and-prompts
kk_depends_on: []
kk_confidence: high
---
`TokklNavHost` keeps a Navigation 3 back stack of `Route`s, saved with their serializers, so it survives configuration changes and process death. `Route` is an open interface in `:core:navigation`, which declares only `Route.Timer`, at the bottom of every signed-in stack, and `Route.Entry`, the entry form that the timer and sync features both open. Each feature declares its other routes as `@Serializable` objects or classes, such as `ClientsRoute`, `FullscreenTimerRoute` or `SignInRoute`, and registers them in `FeatureEntries.registerRoutes`. The host saves the stack with `NavBackStackSerializer(PolymorphicSerializer(Route::class))` and a `SavedStateConfiguration` whose serializers module `routeSerializers` builds from every feature, so a feature's new route needs no change in core. `SavedBackStackTest` round-trips a stack of every route through that serializer; a route a feature forgets to register fails it. The routes that moved to their features on 2026-11-08 keep, with `@SerialName`, the serial names they had in the sealed `Route` before (`com.github.e0ipso.tokkl.navigation.Route.Clients` and so on), so a stack saved by an earlier version reads as before; `SavedBackStackTest` checks those names. Their `contentKey`s, which are their `toString()`, changed with their class names. A route marks itself `focused`, as the fullscreen timer does, to hide the bar, and the entry form above it hides it too. Routes carry ids, never entries: the entry form's `EntryOpening` holds the model's `EntryId` and `WorkspaceId`, which serialize as their numbers. `EntryFormOpenings` keeps in memory the copy of an entry the cache lacks. `:core:navigation` depends on `:core:model`, `:core:ui` and Navigation 3 only: no session, no cache and no Koin. No feature names another's screens: each feature binds a `FeatureEntries` in its Koin module that adds the entries of its routes, and the host builds its entry provider from every one Koin holds. A screen opens another feature's screen through the `Navigator`, by its route. Each entry's view models live in a store that Navigation 3's `ViewModelStoreNavEntryDecorator` clears when the entry leaves the stack, and the view models several screens share live in the session's store, under a key no route has, of the same `ViewModelStoreProvider`. `NavigationStores`, a view model of MainActivity, holds that provider, so both outlive a configuration change, and drops it after every sign-out, also one that finished while MainActivity was made again. Lifecycle's `rememberViewModelStoreProvider` would not clear its stores in that case, which is why the host keeps its own. The host starts at sign-in until a server and token are stored, and at the Timer page after that. A back stack restored from saved state can outlive its session, after a sign-out that finished in the background or a token that no longer decrypts, so when its top is not a `SignInRoute` the host checks `Session.isSignedIn()` once, off the main thread, and goes to sign-in if nobody is signed in. Above every signed-in screen it shows the session's trust prompt, or after a cancel the notice that reopens it.

<!-- kk:related:start -->
# Related

- Related: [map-connection-trust-store-pinning-and-prompts](../../api-and-security/map-connection-trust-store-pinning-and-prompts.md)
<!-- kk:related:end -->
