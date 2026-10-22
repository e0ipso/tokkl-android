# kenkeep Index: screens

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Sign-in, sign-out, the stored session, settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) to learn about: signin/ tries GET /me before storing anything, SignOut clears every account store, and TokklNavHost holds the back stack, top bar and bottom bar. #android #ui #credentials #navigation
- Open [**Timer page: layout, the List history, the Calendar week and timerView**](map-timer-page-history-calendar-and-timer-view.md) to learn about: timer/, history/ and calendar/: the page's scroll-away top, grouped days, older days from the server, the week grid and day strip, List or Calendar. #ui #history #calendar #offline #api
- Open [**Timer, entry form and entry menu: TimerModel, EntryFormModel, EntryActions**](map-timer-entry-form-and-entry-menu.md) to learn about: The timer starts, stops and edits through the queue, the ENTRY form edits or adds a past entry, and the menu duplicates or deletes. #ui #timer #offline #queue #accessibility
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) to learn about: reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, reads v3 weekly for Weekly, and filters all three. #reports #api #toggl #ui
- Open [**Catalog pages: projects, clients and tags changed on the server at once**](map-catalog-pages-projects-clients-tags.md) to learn about: catalog/ is the shared model and UI of the Projects, Clients and Tags pages: no queue, refreshAfterChange, the server's reason on a 4xx. #ui #api #catalog
- Open [**Favorite launcher shortcuts: cache publication and tap handling**](map-favorite-launcher-shortcuts-cache-publication-and-tap-handling.md) to learn about: FavoriteShortcutSync publishes favorites; StartFavoriteActivity resolves the current favorite and queues an offline start. #android #shortcuts #offline
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) to learn about: Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates. #android #widget #offline #battery
- Open [**Quick Settings tile: default listening and cached actions**](map-quick-settings-tile-default-listening-and-cached-actions.md) to learn about: RunningTileService follows the cache while visible and acts on the state it displayed. #android #tile #offline

## By topic

### #offline
- Open [**RunningCheck: guarded background current-entry refresh**](../data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Android stack: shipped UI, cache and native timer surfaces**](../build-and-tooling/map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
- Open [**Running timer notification follows the cache's running entry**](../data-and-offline/map-running-timer-notification-follows-the-cache.md) — notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache, with no foreground service.
### #ui
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
- Open [**Match Tokkl web app behavior in the account timezone**](../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #android
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Android stack: shipped UI, cache and native timer surfaces**](../build-and-tooling/map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
### #api
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Match Tokkl web app behavior in the account timezone**](../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #accessibility
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](../design/practice-tokkl-palette-wcag-english-strings.md) — Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources.
### #battery
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**RunningCheck: guarded background current-entry refresh**](../data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Refresh on events, never on a short polling timer**](../api-and-security/practice-refresh-on-events-no-polling.md) — Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas.
### #calendar
- Open [**Timer page: layout, the List history, the Calendar week and timerView**](map-timer-page-history-calendar-and-timer-view.md) — timer/, history/ and calendar/: the page's scroll-away top, grouped days, older days from the server, the week grid and day strip, List or Calendar.
### #catalog
- Open [**Catalog pages: projects, clients and tags changed on the server at once**](map-catalog-pages-projects-clients-tags.md) — catalog/ is the shared model and UI of the Projects, Clients and Tags pages: no queue, refreshAfterChange, the server's reason on a 4xx.
### #credentials
- Open [**Sign-in, sign-out, the stored session, settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) — signin/ tries GET /me before storing anything, SignOut clears every account store, and TokklNavHost holds the back stack, top bar and bottom bar.
- Open [**API token store and the rules that keep app data out of backups**](../api-and-security/map-api-token-store-and-backup-exclusion.md) — ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and the data extraction rules exclude every domain in every mode.
### #history
- Open [**Timer page: layout, the List history, the Calendar week and timerView**](map-timer-page-history-calendar-and-timer-view.md) — timer/, history/ and calendar/: the page's scroll-away top, grouped days, older days from the server, the week grid and day strip, List or Calendar.
### #navigation
- Open [**Sign-in, sign-out, the stored session, settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) — signin/ tries GET /me before storing anything, SignOut clears every account store, and TokklNavHost holds the back stack, top bar and bottom bar.
### #queue
- Open [**Change queue: ChangeQueue, ChangeSender, BackgroundSend and the offline picker**](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md) — The Room queue sends captured Stops, restores and entry changes offline, with persistent refusals and guarded background cancellation.
- Open [**Timer, entry form and entry menu: TimerModel, EntryFormModel, EntryActions**](map-timer-entry-form-and-entry-menu.md) — The timer starts, stops and edits through the queue, the ENTRY form edits or adds a past entry, and the menu duplicates or deletes.
### #reports
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) — reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, reads v3 weekly for Weekly, and filters all three.
### #shortcuts
- Open [**Favorite launcher shortcuts: cache publication and tap handling**](map-favorite-launcher-shortcuts-cache-publication-and-tap-handling.md) — FavoriteShortcutSync publishes favorites; StartFavoriteActivity resolves the current favorite and queues an offline start.
### #tile
- Open [**Quick Settings tile: default listening and cached actions**](map-quick-settings-tile-default-listening-and-cached-actions.md) — RunningTileService follows the cache while visible and acts on the state it displayed.
### #timer
- Open [**Timer, entry form and entry menu: TimerModel, EntryFormModel, EntryActions**](map-timer-entry-form-and-entry-menu.md) — The timer starts, stops and edits through the queue, the ENTRY form edits or adds a past entry, and the menu duplicates or deletes.
### #toggl
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Check server capabilities instead of requiring a server version**](../api-and-security/practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) — reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, reads v3 weekly for Weekly, and filters all three.
### #widget
- Open [**Home screen widget: cache state and a native ticking clock**](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.