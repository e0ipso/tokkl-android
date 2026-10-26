# kenkeep Index: api-and-security

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Refresh on events, never on a short polling timer**](practice-refresh-on-events-no-polling.md) to learn about: Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas. #network #toggl #battery
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) to learn about: Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector. #api #toggl #compatibility
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) to learn about: No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server. #privacy #network #fdroid #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) to learn about: Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning. #security #network

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
- Open [**Keep release builds reproducible and signed with the project key**](../release/practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
- Open [**Release signing key, Actions secrets and signature checks**](../release/map-release-signing-key-actions-secrets-and-signature-checks.md) — The owner's PKCS12 key signs releases through four Actions secrets. Fingerprint and apksigcopier commands check what F-Droid checks.
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
### #api
- Open [**Trash capability and server operations**](../screens/session-and-navigation/map-trash-capability-and-server-operations.md) — Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling.
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Ordered entry sending and refusals**](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #toggl
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
- Open [**Reports: Summary summed on the phone, Detailed paged, Weekly from v3**](../screens/map-reports-summary-from-detailed-search.md) — reports/ sums the v3 detailed search on the phone for Summary, pages it for Detailed, reads v3 weekly for Weekly, and filters all three.
### #backup
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) — ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and the data extraction rules exclude every domain in every mode.
### #battery
- Open [**Home screen widget: cache state and a native ticking clock**](../screens/map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**RunningCheck: guarded background current-entry refresh**](../data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Refresh on events, never on a short polling timer**](practice-refresh-on-events-no-polling.md) — Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas.
### #compatibility
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](../data-and-offline/map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
### #credentials
- Open [**Session stores and pre-frame preferences**](../screens/session-and-navigation/map-session-stores-and-pre-frame-preferences.md) — Private session stores separate credentials, account settings, trust and device preferences.
- Open [**Sign-in request and credential admission**](../screens/session-and-navigation/map-sign-in-request-and-credential-admission.md) — Validate configured-server credentials and required account settings before storing a session.
- Open [**Sign-out ordering and account cleanup**](../screens/session-and-navigation/map-sign-out-ordering-and-account-cleanup.md) — Warn about unsent work and retire account tasks before clearing local session data.
### #dialogs
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) — The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in.
### #fdroid
- Open [**Keep release builds reproducible and signed with the project key**](../release/practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
- Open [**Release signing key, Actions secrets and signature checks**](../release/map-release-signing-key-actions-secrets-and-signature-checks.md) — The owner's PKCS12 key signs releases through four Actions secrets. Fingerprint and apksigcopier commands check what F-Droid checks.
- Open [**F-Droid metadata layout and release artifacts**](../release/map-fdroid-metadata-release-artifacts.md) — Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries.
### #okhttp
- Open [**API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError**](map-api-client-shared-okhttp-serverurl-and-errors.md) — The api package holds the app's only OkHttpClient, base URL handling, JSON calls, sendNoContent for empty bodies and typed errors.
### #privacy
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](../build-and-tooling/practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #ui
- Open [**Timer page layout and scroll-away header**](../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Timer List and Calendar preference sync**](../screens/timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Tokkl favorites storage and capability**](../screens/timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.