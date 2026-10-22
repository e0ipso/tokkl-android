# kenkeep Index: data-and-offline

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) to learn about: Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine. #offline #network #api
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) to learn about: Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me. #api #ui #time
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](practice-port-tokkl-typescript-with-javascript-semantics.md) to learn about: Copy upstream golden cases and JavaScript semantics, including nullable independent-endpoint snapping in the account timezone. #time #parsing #testing #tokkl

## Components (what exists)
- Open [**Change queue: ChangeQueue, ChangeSender, BackgroundSend and the offline picker**](map-change-queue-sends-entry-changes-in-order.md) to learn about: The Room queue sends captured Stops, restores and entry changes offline, with persistent refusals and guarded background cancellation. #offline #cache #room #api #queue
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) to learn about: One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline. #offline #cache #room #api #compatibility
- Open [**Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest**](map-account-settings-source-and-account-time.md) to learn about: Sign-in and each refresh store timezone, week start, resolution and duration format. AccountTime gives zone and dates, never the device's. #time #api #android #settings
- Open [**Running timer notification follows the cache's running entry**](map-running-timer-notification-follows-the-cache.md) to learn about: notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache, with no foreground service. #notification #offline #android
- Open [**Pomodoro defaults: four preferences with offline persistence**](map-pomodoro-defaults-four-preferences-with-offline-persistence.md) to learn about: PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone. #settings #offline #api #pomodoro
- Open [**RunningCheck: guarded background current-entry refresh**](map-runningcheck-guarded-background-current-entry-refresh.md) to learn about: One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out. #offline #cache #android #battery

## By topic

### #api
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #offline
- Open [**RunningCheck: guarded background current-entry refresh**](map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Android stack: shipped UI, cache and native timer surfaces**](../build-and-tooling/map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
- Open [**Running timer notification follows the cache's running entry**](map-running-timer-notification-follows-the-cache.md) — notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache, with no foreground service.
### #android
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Android stack: shipped UI, cache and native timer surfaces**](../build-and-tooling/map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
### #cache
- Open [**Change queue: ChangeQueue, ChangeSender, BackgroundSend and the offline picker**](map-change-queue-sends-entry-changes-in-order.md) — The Room queue sends captured Stops, restores and entry changes offline, with persistent refusals and guarded background cancellation.
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
- Open [**RunningCheck: guarded background current-entry refresh**](map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
### #time
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
- Open [**Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest**](map-account-settings-source-and-account-time.md) — Sign-in and each refresh store timezone, week start, resolution and duration format. AccountTime gives zone and dates, never the device's.
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](practice-port-tokkl-typescript-with-javascript-semantics.md) — Copy upstream golden cases and JavaScript semantics, including nullable independent-endpoint snapping in the account timezone.
### #room
- Open [**Change queue: ChangeQueue, ChangeSender, BackgroundSend and the offline picker**](map-change-queue-sends-entry-changes-in-order.md) — The Room queue sends captured Stops, restores and entry changes offline, with persistent refusals and guarded background cancellation.
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
### #settings
- Open [**Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest**](map-account-settings-source-and-account-time.md) — Sign-in and each refresh store timezone, week start, resolution and duration format. AccountTime gives zone and dates, never the device's.
- Open [**Pomodoro defaults: four preferences with offline persistence**](map-pomodoro-defaults-four-preferences-with-offline-persistence.md) — PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone.
### #battery
- Open [**Home screen widget: cache state and a native ticking clock**](../screens/map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**RunningCheck: guarded background current-entry refresh**](map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Refresh on events, never on a short polling timer**](../api-and-security/practice-refresh-on-events-no-polling.md) — Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas.
### #compatibility
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
- Open [**Check server capabilities instead of requiring a server version**](../api-and-security/practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
### #network
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](../api-and-security/practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError**](../api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md) — The api package holds the app's only OkHttpClient, base URL handling, JSON calls, sendNoContent for empty bodies and typed errors.
### #notification
- Open [**Running timer notification follows the cache's running entry**](map-running-timer-notification-follows-the-cache.md) — notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache, with no foreground service.
### #parsing
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](practice-port-tokkl-typescript-with-javascript-semantics.md) — Copy upstream golden cases and JavaScript semantics, including nullable independent-endpoint snapping in the account timezone.
### #pomodoro
- Open [**Pomodoro defaults: four preferences with offline persistence**](map-pomodoro-defaults-four-preferences-with-offline-persistence.md) — PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone.
### #queue
- Open [**Change queue: ChangeQueue, ChangeSender, BackgroundSend and the offline picker**](map-change-queue-sends-entry-changes-in-order.md) — The Room queue sends captured Stops, restores and entry changes offline, with persistent refusals and guarded background cancellation.
- Open [**Timer, entry form and entry menu: TimerModel, EntryFormModel, EntryActions**](../screens/map-timer-entry-form-and-entry-menu.md) — The timer starts, stops and edits through the queue, the ENTRY form edits or adds a past entry, and the menu duplicates or deletes.
### #testing
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](../testing/practice-run-instrumented-tests-only-through-the-script.md) — The script pins Tokkl 1.8.1, seeds accounts, configures TLS and test filters, and rejects empty device runs.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
### #tokkl
- Open [**Tokkl colour tokens and Anybody font in ../tokkl**](../design/map-tokkl-colour-tokens-and-font.md) — The colour tokens in ../tokkl/packages/web/src/styles.css are the palette's source of truth. Tokkl ships Anybody under the SIL OFL 1.1.
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](practice-port-tokkl-typescript-with-javascript-semantics.md) — Copy upstream golden cases and JavaScript semantics, including nullable independent-endpoint snapping in the account timezone.
### #ui
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.