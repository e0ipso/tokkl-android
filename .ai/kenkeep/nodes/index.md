---
okf_version: '0.1'
---
# kenkeep Index

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) to learn about: The script starts a pinned Tokkl container, seeds an account and passes it to the tests. Plain connectedDebugAndroidTest fails them. #testing #android #emulator #docker
- Open [**Test on the plain AOSP API 36 emulator, headless, and stop it afterwards**](practice-emulator-plain-aosp-image-headless.md) to learn about: AVD tokkl-api36 uses the AOSP image, since ATD images drop SystemUI and the launcher. Boot it with -no-window -accel on and kill it after. #testing #android #emulator #energy
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) to learn about: Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me. #api #ui #time
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) to learn about: Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning. #security #network
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) to learn about: No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server. #privacy #network #fdroid #security
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](practice-port-tokkl-typescript-with-javascript-semantics.md) to learn about: The Kotlin time ports copy JavaScript's whitespace, rounding and replace rules, return null for NaN, and reuse the .test.ts cases verbatim. #time #parsing #testing #tokkl
- Open [**Refresh on events, never on a short polling timer**](practice-refresh-on-events-no-polling.md) to learn about: Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas. #network #toggl #battery
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) to learn about: Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one. #testing #ci #performance
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) to learn about: Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources. #ui #accessibility #i18n
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) to learn about: ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree. #build #gradle #agents #worktrees
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) to learn about: No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it. #api #toggl #compatibility
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) to learn about: Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine. #offline #network #api
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) to learn about: Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple. #testing #android #emulator #ui
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) to learn about: The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks. #git #commits #tooling
- Open [**Version the app on its own, starting from a hand-made v0.1.0 tag**](practice-own-version-from-v0-1-0-tag.md) to learn about: The app has its own v* tags and changelog, independent of Tokkl. The first release is a hand-made v0.1.0 tag; semantic-release counts on. #release #versioning
- Open [**Keep release builds reproducible and signed with the project key**](practice-reproducible-builds-project-key.md) to learn about: Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA. #release #fdroid #security #ci
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) to learn about: sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out. #privacy #tooling #android

## Components (what exists)
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) to learn about: Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin. #android #tooling #agents #build
- Open [**Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest**](map-account-settings-source-and-account-time.md) to learn about: Sign-in and each refresh store timezone, week start, resolution and duration format. AccountTime gives zone and dates, never the device's. #time #api #android #settings
- Open [**API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError**](map-api-client-shared-okhttp-serverurl-and-errors.md) to learn about: The api package holds the app's only OkHttpClient, base URL handling, JSON calls, sendNoContent for empty bodies and typed errors. #api #network #okhttp
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) to learn about: The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in. #security #network #ui #dialogs
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) to learn about: ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup. #ui #theme #accessibility #android
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) to learn about: One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline. #offline #cache #room #api #compatibility
- Open [**Sign-in, the stored session, theme settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) to learn about: signin/ tries GET /me before storing anything, session/ and settings/ keep their own preference files, TokklNavHost is a saveable back stack. #android #ui #credentials #navigation
- Open [**Tokkl colour tokens and Anybody font in ../tokkl**](map-tokkl-colour-tokens-and-font.md) to learn about: The colour tokens in ../tokkl/packages/web/src/styles.css are the palette's source of truth. Tokkl ships Anybody under the SIL OFL 1.1. #ui #tokkl
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) to learn about: Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents. #agents #machine #memory #emulator
- Open [**F-Droid metadata layout and release artifacts**](map-fdroid-metadata-release-artifacts.md) to learn about: Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries. #fdroid #release
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) to learn about: Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints. #api #toggl #network
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) to learn about: ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and both rule files exclude every domain in every mode. #security #credentials #backup
- Open [**Planned stack: Compose, Glance, TileService, Room, WorkManager, OkHttp**](map-planned-android-stack.md) to learn about: Proposed stack: Kotlin and Compose Material 3, Glance widget, TileService tile, Room cache and queue, WorkManager, OkHttp. #architecture #android #offline
- Open [**Reports: the summary summed on the phone from the detailed search**](map-reports-summary-from-detailed-search.md) to learn about: reports/ pages the Reports v3 detailed search and sums it on the phone, since Tokkl's totals and summary carry no rates or billable split. #reports #api #toggl #ui
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) to learn about: t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session. #agents #tooling #machine
- Open [**Application ID, license and supported Android versions**](map-app-id-license-android-versions.md) to learn about: Application ID com.github.e0ipso.tokkl, MIT license, Android 8.0 (API 26) and later, one server and one account at a time. #release #fdroid #android
- Open [**Gradle build settings: SDK levels, Kotlin version source and memory**](map-gradle-build-sdk-levels-kotlin-and-memory.md) to learn about: compileSdk follows the dependencies, targetSdk matches the emulator, the catalog sets Kotlin, and the Gradle heap stays small. #android #build #gradle

## By topic

### #android
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Test on the plain AOSP API 36 emulator, headless, and stop it afterwards**](practice-emulator-plain-aosp-image-headless.md) — AVD tokkl-api36 uses the AOSP image, since ATD images drop SystemUI and the launcher. Boot it with -no-window -accel on and kill it after.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) — The script starts a pinned Tokkl container, seeds an account and passes it to the tests. Plain connectedDebugAndroidTest fails them.
### #api
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it.
### #ui
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) — Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources.
### #network
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError**](map-api-client-shared-okhttp-serverurl-and-errors.md) — The api package holds the app's only OkHttpClient, base URL handling, JSON calls, sendNoContent for empty bodies and typed errors.
### #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) — The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in.
### #testing
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) — The script starts a pinned Tokkl container, seeds an account and passes it to the tests. Plain connectedDebugAndroidTest fails them.
- Open [**Test on the plain AOSP API 36 emulator, headless, and stop it afterwards**](practice-emulator-plain-aosp-image-headless.md) — AVD tokkl-api36 uses the AOSP image, since ATD images drop SystemUI and the launcher. Boot it with -no-window -accel on and kill it after.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
### #agents
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents.
### #emulator
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) — The script starts a pinned Tokkl container, seeds an account and passes it to the tests. Plain connectedDebugAndroidTest fails them.
- Open [**Test on the plain AOSP API 36 emulator, headless, and stop it afterwards**](practice-emulator-plain-aosp-image-headless.md) — AVD tokkl-api36 uses the AOSP image, since ATD images drop SystemUI and the launcher. Boot it with -no-window -accel on and kill it after.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
### #fdroid
- Open [**F-Droid metadata layout and release artifacts**](map-fdroid-metadata-release-artifacts.md) — Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries.
- Open [**Application ID, license and supported Android versions**](map-app-id-license-android-versions.md) — Application ID com.github.e0ipso.tokkl, MIT license, Android 8.0 (API 26) and later, one server and one account at a time.
- Open [**Keep release builds reproducible and signed with the project key**](practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #release
- Open [**F-Droid metadata layout and release artifacts**](map-fdroid-metadata-release-artifacts.md) — Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries.
- Open [**Application ID, license and supported Android versions**](map-app-id-license-android-versions.md) — Application ID com.github.e0ipso.tokkl, MIT license, Android 8.0 (API 26) and later, one server and one account at a time.
- Open [**Keep release builds reproducible and signed with the project key**](practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #toggl
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it.
- Open [**Reports: the summary summed on the phone from the detailed search**](map-reports-summary-from-detailed-search.md) — reports/ pages the Reports v3 detailed search and sums it on the phone, since Tokkl's totals and summary carry no rates or billable split.
### #tooling
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #build
- Open [**Gradle build settings: SDK levels, Kotlin version source and memory**](map-gradle-build-sdk-levels-kotlin-and-memory.md) — compileSdk follows the dependencies, targetSdk matches the emulator, the catalog sets Kotlin, and the Gradle heap stays small.
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
### #offline
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
- Open [**Planned stack: Compose, Glance, TileService, Room, WorkManager, OkHttp**](map-planned-android-stack.md) — Proposed stack: Kotlin and Compose Material 3, Glance widget, TileService tile, Room cache and queue, WorkManager, OkHttp.
### #time
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
- Open [**Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest**](map-account-settings-source-and-account-time.md) — Sign-in and each refresh store timezone, week start, resolution and duration format. AccountTime gives zone and dates, never the device's.
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](practice-port-tokkl-typescript-with-javascript-semantics.md) — The Kotlin time ports copy JavaScript's whitespace, rounding and replace rules, return null for NaN, and reuse the .test.ts cases verbatim.
### #accessibility
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) — Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources.
### #ci
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one.
- Open [**Keep release builds reproducible and signed with the project key**](practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #compatibility
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
- Open [**Check server capabilities instead of requiring a server version**](practice-check-capabilities-not-server-version.md) — No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it.
### #credentials
- Open [**Sign-in, the stored session, theme settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) — signin/ tries GET /me before storing anything, session/ and settings/ keep their own preference files, TokklNavHost is a saveable back stack.
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) — ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and both rule files exclude every domain in every mode.
### #gradle
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
- Open [**Gradle build settings: SDK levels, Kotlin version source and memory**](map-gradle-build-sdk-levels-kotlin-and-memory.md) — compileSdk follows the dependencies, targetSdk matches the emulator, the catalog sets Kotlin, and the Gradle heap stays small.
### #machine
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #privacy
- Open [**Talk only to the configured server and use only free software**](practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #tokkl
- Open [**Tokkl colour tokens and Anybody font in ../tokkl**](map-tokkl-colour-tokens-and-font.md) — The colour tokens in ../tokkl/packages/web/src/styles.css are the palette's source of truth. Tokkl ships Anybody under the SIL OFL 1.1.
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](practice-port-tokkl-typescript-with-javascript-semantics.md) — The Kotlin time ports copy JavaScript's whitespace, rounding and replace rules, return null for NaN, and reuse the .test.ts cases verbatim.
### #architecture
- Open [**Planned stack: Compose, Glance, TileService, Room, WorkManager, OkHttp**](map-planned-android-stack.md) — Proposed stack: Kotlin and Compose Material 3, Glance widget, TileService tile, Room cache and queue, WorkManager, OkHttp.
### #backup
- Open [**API token store and the rules that keep app data out of backups**](map-api-token-store-and-backup-exclusion.md) — ApiTokenStore encrypts the token with a Keystore AES-GCM key. Backup is off and both rule files exclude every domain in every mode.
### #battery
- Open [**Refresh on events, never on a short polling timer**](practice-refresh-on-events-no-polling.md) — Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas.
### #cache
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
### #commits
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #dialogs
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](map-connection-trust-store-pinning-and-prompts.md) — The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in.
### #docker
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) — The script starts a pinned Tokkl container, seeds an account and passes it to the tests. Plain connectedDebugAndroidTest fails them.
### #energy
- Open [**Test on the plain AOSP API 36 emulator, headless, and stop it afterwards**](practice-emulator-plain-aosp-image-headless.md) — AVD tokkl-api36 uses the AOSP image, since ATD images drop SystemUI and the launcher. Boot it with -no-window -accel on and kill it after.
### #git
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #i18n
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) — Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources.
### #memory
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents.
### #navigation
- Open [**Sign-in, the stored session, theme settings and the navigation host**](map-signin-session-settings-and-navigation-host.md) — signin/ tries GET /me before storing anything, session/ and settings/ keep their own preference files, TokklNavHost is a saveable back stack.
### #okhttp
- Open [**API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError**](map-api-client-shared-okhttp-serverurl-and-errors.md) — The api package holds the app's only OkHttpClient, base URL handling, JSON calls, sendNoContent for empty bodies and typed errors.
### #parsing
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](practice-port-tokkl-typescript-with-javascript-semantics.md) — The Kotlin time ports copy JavaScript's whitespace, rounding and replace rules, return null for NaN, and reuse the .test.ts cases verbatim.
### #performance
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one.
### #reports
- Open [**Reports: the summary summed on the phone from the detailed search**](map-reports-summary-from-detailed-search.md) — reports/ pages the Reports v3 detailed search and sums it on the phone, since Tokkl's totals and summary carry no rates or billable split.
### #room
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
### #settings
- Open [**Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest**](map-account-settings-source-and-account-time.md) — Sign-in and each refresh store timezone, week start, resolution and duration format. AccountTime gives zone and dates, never the device's.
### #theme
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
### #versioning
- Open [**Version the app on its own, starting from a hand-made v0.1.0 tag**](practice-own-version-from-v0-1-0-tag.md) — The app has its own v* tags and changelog, independent of Tokkl. The first release is a hand-made v0.1.0 tag; semantic-release counts on.
### #worktrees
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
