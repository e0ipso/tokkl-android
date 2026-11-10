---
type: map
title: App composition and lifecycle hooks
description: >-
  Feature-owned Koin definitions, ordered AppHooks and injected dispatchers keep
  app wiring small.
tags:
  - architecture
  - android
  - build
kk_schema_version: 3
kk_id: map-app-composition-and-lifecycle-hooks
kk_derived_from: []
kk_relates_to:
  - map-planned-android-stack
  - map-core-and-feature-module-boundaries
kk_depends_on: []
kk_confidence: high
---
The modules are listed in `settings.gradle.kts`. `:app` is only the shell: `TokklApplication`, `MainActivity`, the startup and lifecycle observers, what spans the modules (`lifecycle/AppRefreshSteps`, what each account refresh does after its answer), the list of Koin modules in `di/AppModules.kt` and the navigation host in `navhost`. Each core module ships its own Koin module, as each feature does: `platformModule`, `sessionModule`, `trustModule`, `cacheModule` (with the queue's workers), `timerStateModule`, `timerNoticeModule` (core:timersync's second, in its `timernotice` package, with the worker that sends the notification's and the widget's timer actions) and `timerSyncModule`. The wiring that joins two components is a named class in the lower module, such as `QueueDrainEvents` and `CapabilitiesSignOut`, or a lambda in that module's Koin definition, such as `ChangeQueue`'s `onQueued` and `ForegroundRefresh`'s two refreshes in `cacheModule`. `AppModulesTest` fails when the app's own Koin modules define a core or feature class beyond the few that join every module's parts. What a module does at process start, on MainActivity's starts and when the user allows notifications is a class of its own that implements core:platform's `AppHook` interface, which names only those three moments, such as `RunningCheckHook` or `TimerSyncHook`, bound in the module's Koin module with `bind AppHook::class`. The app's `di/HooksModule.kt` lists every hook in the order they run, with the reason for each place, and `AppHooks` refuses a bound hook that list leaves out. `AppStartup` and `MainActivityForeground` run the list, and `AppHookOrderTest` pins it as a list of the hooks' class names, so a new step adds a hook class and a line in each. A hook that follows a flow for the whole process, such as the notifications', the widgets', the shortcuts', the phase alarm's and the running check's, collects it through core:platform's `collectDurably`, which reads the flow again after a failed read and tries a failed step again, after a wait of one second doubling up to two minutes, so a failure of the disk or the database never ends it. Koin binds every definition by its type and holds no string names. The only qualifiers left are the dispatchers', typed as `named<AppDispatcher.Main>()`, `Io` and `Default` in core:platform's `AppDispatcher.kt`. Main code takes every dispatcher from them: `io()`, `computation()` and `viewModelCoroutineScope()` read them in a Koin definition, and an Android component injects one, so no class falls back to a `Dispatchers` default. `LoadedState` and `ThemeSettingsRepository` take the context their startup load runs on, which Koin gives as `io()`. Plain definitions use Koin's constructor DSL, `singleOf` and `viewModelOf` with `bind` or `binds`; a definition keeps a lambda where an argument is `io()`, `androidContext()`, a property, a method reference or a qualifier. A class Koin builds takes a collaborator as a named type, such as `CacheRefresh` or `CurrentTimerState`, or, where it needs one call, as a function type that its definition fills with a method reference, such as `get<CapabilitiesSource>()::offersTimerApi`, so the extra types `AppModulesTest` allows in a constructor include function types.

<!-- kk:related:start -->
# Related

- Related: [map-planned-android-stack](../map-planned-android-stack.md)
- Related: [map-core-and-feature-module-boundaries](map-core-and-feature-module-boundaries.md)
<!-- kk:related:end -->
