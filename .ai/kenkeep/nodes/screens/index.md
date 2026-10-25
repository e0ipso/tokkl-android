# kenkeep Index: screens

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`session-and-navigation/`](session-and-navigation/index.md) for more information on sign-in, account lifetime, navigation and adaptive chrome; read before changing authentication, sign-out or page routing.
- Load [`timer-and-entry-editing/`](timer-and-entry-editing/index.md) for more information on timer controls, shared fields, entry forms and menus; read before changing entry editing or local start and stop behavior.
- Load [`timer-history-calendar/`](timer-history-calendar/index.md) for more information on Timer layout, cached totals, history and calendar; read before changing timer totals, entry browsing or saved view state.

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) to learn about: reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, reads v3 weekly for Weekly, and filters all three. #reports #api #toggl #ui
- Open [**Catalog pages: projects, clients and tags changed on the server at once**](map-catalog-pages-projects-clients-tags.md) to learn about: catalog/ is the shared model and UI of the Projects, Clients and Tags pages: no queue, refreshAfterChange, the server's reason on a 4xx. #ui #api #catalog
- Open [**Favorite launcher shortcuts: cache publication and tap handling**](map-favorite-launcher-shortcuts-cache-publication-and-tap-handling.md) to learn about: FavoriteShortcutSync publishes favorites; StartFavoriteActivity resolves the current favorite and queues an offline start. #android #shortcuts #offline
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) to learn about: Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates. #android #widget #offline #battery
- Open [**Quick Settings tile: default listening and cached actions**](map-quick-settings-tile-default-listening-and-cached-actions.md) to learn about: RunningTileService follows the cache while visible and acts on the state it displayed. #android #tile #offline

## By topic

### #android
- Open [**Let the runner own emulator boot and settings**](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #api
- Open [**Trash capability and server operations**](session-and-navigation/map-trash-capability-and-server-operations.md) — Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling.
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Ordered entry sending and refusals**](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #ui
- Open [**Timer page layout and scroll-away header**](timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Timer List and Calendar preference sync**](timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Tokkl favorites storage and capability**](timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.
### #battery
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**RunningCheck: guarded background current-entry refresh**](../data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Refresh on events, never on a short polling timer**](../api-and-security/practice-refresh-on-events-no-polling.md) — Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas.
### #catalog
- Open [**Catalog pages: projects, clients and tags changed on the server at once**](map-catalog-pages-projects-clients-tags.md) — catalog/ is the shared model and UI of the Projects, Clients and Tags pages: no queue, refreshAfterChange, the server's reason on a 4xx.
### #reports
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) — reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, reads v3 weekly for Weekly, and filters all three.
### #shortcuts
- Open [**Favorite launcher shortcuts: cache publication and tap handling**](map-favorite-launcher-shortcuts-cache-publication-and-tap-handling.md) — FavoriteShortcutSync publishes favorites; StartFavoriteActivity resolves the current favorite and queues an offline start.
### #tile
- Open [**Quick Settings tile: default listening and cached actions**](map-quick-settings-tile-default-listening-and-cached-actions.md) — RunningTileService follows the cache while visible and acts on the state it displayed.
### #toggl
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Check server capabilities instead of requiring a server version**](../api-and-security/practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) — reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, reads v3 weekly for Weekly, and filters all three.
### #widget
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.