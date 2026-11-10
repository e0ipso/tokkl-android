# kenkeep Index: api-and-security

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`api-client/`](api-client/index.md) for more information on typed API requests, HTTP execution, configured-server security and test fixtures; read before changing requests, transport or client wiring.

## Conventions (how we build)
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) to learn about: Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector. #api #toggl #compatibility
- Open [**Refresh on events, never on a short polling timer**](practice-refresh-on-events-no-polling.md) to learn about: Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas. #network #toggl #battery
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) to learn about: No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server. #privacy #network #fdroid #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) to learn about: Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning. #security #network

## Components (what exists)
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) to learn about: Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints. #api #toggl #network
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) to learn about: The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrust prompts on every screen but sign-in. #security #network #ui #dialogs
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) to learn about: ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and the data extraction rules exclude every domain in every mode. #security #credentials #backup

## By topic

### #network
- Open [**Typed API resources and request contracts**](api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
### #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Sign releases and verify optimized APKs**](../release/reproducible-builds/practice-sign-releases-and-verify-optimized-apks.md) — Keep the project key outside the repository and exercise the optimized signed artifact, including upgrades.
### #toggl
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Report APIs rounding and pagination**](../screens/reports/map-report-apis-rounding-and-pagination.md) — Summary sums detailed search, Detailed follows server pagination, and Weekly reads rounded v3 rows.
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
### #api
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Typed API resources and request contracts**](api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Ordered entry sending and refusals**](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #backup
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) — ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and the data extraction rules exclude every domain in every mode.
### #battery
- Open [**Home screen widget: cache state and a native ticking clock**](../screens/map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**RunningCheck: guarded background current-entry refresh**](../data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Pomodoro home screen widget**](../screens/map-pomodoro-home-screen-widget.md) — A separate provider shows the server session and uses native countdowns and online-only session actions.
### #compatibility
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
- Open [**Capability probes and persisted feature flags**](../data-and-offline/account-cache/map-capability-probes-and-persisted-feature-flags.md) — Trash and timer probes distinguish absent endpoints from temporary failures and load flags before early callers.
### #credentials
- Open [**Sign-out ordering and account cleanup**](../screens/session-and-navigation/map-sign-out-ordering-and-account-cleanup.md) — Warn about unsent work and retire account tasks before clearing local session data.
- Open [**Session stores and pre-frame preferences**](../screens/session-and-navigation/map-session-stores-and-pre-frame-preferences.md) — Private session stores separate credentials, account settings, trust and device preferences; their holders load at process start and write through suspend calls.
- Open [**Sign-in request and credential admission**](../screens/session-and-navigation/map-sign-in-request-and-credential-admission.md) — Validate configured-server credentials and required account settings before storing a session.
### #dialogs
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) — The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrust prompts on every screen but sign-in.
### #fdroid
- Open [**Sign releases and verify optimized APKs**](../release/reproducible-builds/practice-sign-releases-and-verify-optimized-apks.md) — Keep the project key outside the repository and exercise the optimized signed artifact, including upgrades.
- Open [**Release signing key, Actions secrets and signature checks**](../release/map-release-signing-key-actions-secrets-and-signature-checks.md) — The owner's PKCS12 key signs releases through four Actions secrets. Fingerprint and apksigcopier commands check what F-Droid checks.
- Open [**F-Droid metadata layout and release artifacts**](../release/map-fdroid-metadata-release-artifacts.md) — Store text lives in fastlane/metadata/android/en-US/, the fdroiddata recipe in fdroid/metadata/com.canpicasoft.tokkl.yml. Check it in Docker before every new build.
### #privacy
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](../build-and-tooling/host-runtime/practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #ui
- Open [**Timer page layout and scroll-away header**](../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Signed-in navigation chrome and adaptive windows**](../screens/session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](../screens/session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.