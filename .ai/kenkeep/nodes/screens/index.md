# kenkeep Index: screens

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`catalog-pages/`](catalog-pages/index.md) for more information on catalog loading, saved forms, project tasks and immediate server mutations; read before changing Projects, Clients, Tags or their shared coordinator.
- Load [`reports/`](reports/index.md) for more information on report API calculations, filters, ranges and screen state; read before changing report requests, formatting, restoration or widget day navigation.
- Load [`session-and-navigation/`](session-and-navigation/index.md) for more information on sign-in, account lifetime, navigation and adaptive chrome; read before changing authentication, sign-out or page routing.
- Load [`timer-and-entry-editing/`](timer-and-entry-editing/index.md) for more information on timer controls, shared fields, entry forms and menus; read before changing entry editing or local start and stop behavior.
- Load [`timer-history-calendar/`](timer-history-calendar/index.md) for more information on Timer layout, cached totals, history and calendar; read before changing timer totals, entry browsing or saved view state.

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) to learn about: Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates. #android #widget #offline #battery
- Open [**Pomodoro home screen widget**](map-pomodoro-home-screen-widget.md) to learn about: A separate provider shows the server session and uses native countdowns and online-only session actions. #android #widget #pomodoro #battery
- Open [**Favorite launcher shortcuts: cache publication and tap handling**](map-favorite-launcher-shortcuts-cache-publication-and-tap-handling.md) to learn about: FavoriteShortcutSync publishes favorites; StartFavoriteActivity resolves the current favorite and queues an offline start. #android #shortcuts #offline
- Open [**Quick Settings tile: default listening and cached actions**](map-quick-settings-tile-default-listening-and-cached-actions.md) to learn about: RunningTileService follows the cache while visible and acts on the state it displayed. #android #tile #offline

## By topic

### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #battery
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**RunningCheck: guarded background current-entry refresh**](../data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Pomodoro home screen widget**](map-pomodoro-home-screen-widget.md) — A separate provider shows the server session and uses native countdowns and online-only session actions.
### #widget
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**Pomodoro home screen widget**](map-pomodoro-home-screen-widget.md) — A separate provider shows the server session and uses native countdowns and online-only session actions.
### #pomodoro
- Open [**Account timer state lifetime**](../data-and-offline/timer-state-and-sync/map-account-timer-state-lifetime.md) — AccountTimerStates owns intake, read-only timer state and retirement across sign-in and sign-out.
- Open [**Timer state acceptance and persistence**](../data-and-offline/timer-state-and-sync/map-timer-state-acceptance-and-persistence.md) — Revision acceptance, clock offsets, persisted state and cache reconciliation share one account holder.
- Open [**Online timer actions and native control failures**](../data-and-offline/timer-state-and-sync/map-online-timer-actions-and-native-control-failures.md) — Session and pause controls use captured account revisions, single server attempts and shared failure notices.
### #shortcuts
- Open [**Favorite launcher shortcuts: cache publication and tap handling**](map-favorite-launcher-shortcuts-cache-publication-and-tap-handling.md) — FavoriteShortcutSync publishes favorites; StartFavoriteActivity resolves the current favorite and queues an offline start.
### #tile
- Open [**Quick Settings tile: default listening and cached actions**](map-quick-settings-tile-default-listening-and-cached-actions.md) — RunningTileService follows the cache while visible and acts on the state it displayed.