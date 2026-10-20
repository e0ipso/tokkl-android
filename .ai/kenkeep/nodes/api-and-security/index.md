# kenkeep Index: api-and-security

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) to learn about: No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it. #api #toggl #compatibility
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) to learn about: Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning. #security #network
- Open [**Refresh on events, never on a short polling timer**](practice-refresh-on-events-no-polling.md) to learn about: Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas. #network #toggl #battery
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) to learn about: No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server. #privacy #network #fdroid #security

## Components (what exists)
- Open [**API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError**](map-api-client-shared-okhttp-serverurl-and-errors.md) to learn about: The api package holds the app's only OkHttpClient, base URL handling, JSON calls, sendNoContent for empty bodies and typed errors. #api #network #okhttp
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) to learn about: The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in. #security #network #ui #dialogs
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) to learn about: Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints. #api #toggl #network
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) to learn about: ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and the data extraction rules exclude every domain in every mode. #security #credentials #backup

## By topic

### #network
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError**](map-api-client-shared-okhttp-serverurl-and-errors.md) — The api package holds the app's only OkHttpClient, base URL handling, JSON calls, sendNoContent for empty bodies and typed errors.
### #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) — The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in.
### #api
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Match Tokkl web app behavior in the account timezone**](../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #toggl
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it.
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](../screens/map-reports-summary-from-detailed-search.md) — reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, reads v3 weekly for Weekly, and filters all three.
### #backup
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) — ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and the data extraction rules exclude every domain in every mode.
### #battery
- Open [**Refresh on events, never on a short polling timer**](practice-refresh-on-events-no-polling.md) — Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas.
### #compatibility
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](../data-and-offline/map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it.
### #credentials
- Open [**Sign-in, sign-out, the stored session, settings and the navigation host**](../screens/map-signin-session-settings-and-navigation-host.md) — signin/ tries GET /me before storing anything, SignOut clears every account store, and TokklNavHost holds the back stack, top bar and bottom bar.
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) — ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and the data extraction rules exclude every domain in every mode.
### #dialogs
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) — The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in.
### #fdroid
- Open [**F-Droid metadata layout and release artifacts**](../release/map-fdroid-metadata-release-artifacts.md) — Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries.
- Open [**Application ID, license and supported Android versions**](../release/map-app-id-license-android-versions.md) — Application ID com.github.e0ipso.tokkl, MIT license, Android 12 (API 31) and later, one server and one account at a time.
- Open [**Keep release builds reproducible and signed with the project key**](../release/practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #okhttp
- Open [**API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError**](map-api-client-shared-okhttp-serverurl-and-errors.md) — The api package holds the app's only OkHttpClient, base URL handling, JSON calls, sendNoContent for empty bodies and typed errors.
### #privacy
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](../build-and-tooling/practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #ui
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
- Open [**Match Tokkl web app behavior in the account timezone**](../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.