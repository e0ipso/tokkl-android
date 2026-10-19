# kenkeep Index: screens

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Sign-in, the stored session, theme settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) to learn about: signin/ tries GET /me before storing anything, each store keeps its own preference file, and TokklNavHost holds the back stack and bottom bar. #android #ui #credentials #navigation
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) to learn about: reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, and reads the v3 weekly endpoint for Weekly. #reports #api #toggl #ui
- Open [**Timer page: the List history, the Calendar week and the timerView choice**](map-timer-page-history-calendar-and-timer-view.md) to learn about: timer/, history/ and calendar/: the cached two weeks, older days paged from the server, the week grid, and List or Calendar kept in Tokkl. #ui #history #calendar #offline #api
- Open [**Catalog pages: projects, clients and tags changed on the server at once**](map-catalog-pages-projects-clients-tags.md) to learn about: catalog/ is the shared model and UI of the Projects, Clients and Tags pages: no queue, refreshAfterChange, the server's reason on a 4xx. #ui #api #catalog

## By topic

### #ui
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard and a dialog that lays itself out on every frame.
- Open [**Match Tokkl web app behavior in the account timezone**](../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #api
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Match Tokkl web app behavior in the account timezone**](../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #android
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Write instrumented tests that hold from API 26 to 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse the relays, AirplaneMode, BackgroundTrip, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](../testing/practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container, seeds an account, passes test filters as a file and splits runs below Android 12 into checked batches.
### #calendar
- Open [**Timer page: the List history, the Calendar week and the timerView choice**](map-timer-page-history-calendar-and-timer-view.md) — timer/, history/ and calendar/: the cached two weeks, older days paged from the server, the week grid, and List or Calendar kept in Tokkl.
### #catalog
- Open [**Catalog pages: projects, clients and tags changed on the server at once**](map-catalog-pages-projects-clients-tags.md) — catalog/ is the shared model and UI of the Projects, Clients and Tags pages: no queue, refreshAfterChange, the server's reason on a 4xx.
### #credentials
- Open [**Sign-in, the stored session, theme settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) — signin/ tries GET /me before storing anything, each store keeps its own preference file, and TokklNavHost holds the back stack and bottom bar.
- Open [**API token store and the rules that keep app data out of backups**](../api-and-security/map-api-token-store-and-backup-exclusion.md) — ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and both rule files exclude every domain in every mode.
### #history
- Open [**Timer page: the List history, the Calendar week and the timerView choice**](map-timer-page-history-calendar-and-timer-view.md) — timer/, history/ and calendar/: the cached two weeks, older days paged from the server, the week grid, and List or Calendar kept in Tokkl.
### #navigation
- Open [**Sign-in, the stored session, theme settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) — signin/ tries GET /me before storing anything, each store keeps its own preference file, and TokklNavHost holds the back stack and bottom bar.
### #offline
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](../data-and-offline/map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
- Open [**Change queue: ChangeQueue, ChangeSender, PendingChange and the offline picker**](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md) — Entry changes land in the cache and a Room queue at once. ChangeSender sends them oldest first, stop as a full PUT, and refreshes after.
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
### #reports
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) — reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, and reads the v3 weekly endpoint for Weekly.
### #toggl
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Check server capabilities instead of requiring a server version**](../api-and-security/practice-check-capabilities-not-server-version.md) — No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it.
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](map-reports-summary-from-detailed-search.md) — reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, and reads the v3 weekly endpoint for Weekly.