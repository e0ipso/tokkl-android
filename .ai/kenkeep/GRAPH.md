---
schema_version: 3
nodes_hash: 'sha256:586ce891e0b8a28a6625f8eff95be2a5205d242f5a651818e4e1f2c2df024d8b'
node_count: 44
---
# kenkeep Graph

Total nodes: 44

## map-account-cache-and-capabilities

- **kind:** map
- **title:** Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource
- **path:** data-and-offline/map-account-cache-and-capabilities.md
- **tags:** offline, cache, room, api, compatibility
- **relates_to:** map-account-settings-source-and-account-time, practice-check-capabilities-not-server-version, practice-never-wait-for-server-on-start-stop, map-signin-session-settings-and-navigation-host, map-change-queue-sends-entry-changes-in-order

## map-account-settings-source-and-account-time

- **kind:** map
- **title:** Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest
- **path:** data-and-offline/map-account-settings-source-and-account-time.md
- **tags:** time, api, android, settings
- **relates_to:** practice-match-web-app-behavior-account-timezone, map-signin-session-settings-and-navigation-host, map-connection-trust-store-pinning-and-prompts, practice-refresh-on-events-no-polling, map-account-cache-and-capabilities

## map-api-client-shared-okhttp-serverurl-and-errors

- **kind:** map
- **title:** API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError
- **path:** api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md
- **tags:** api, network, okhttp
- **relates_to:** map-tokkl-toggl-api, practice-talk-only-to-configured-server, practice-pin-self-signed-certs-warn-on-http, map-connection-trust-store-pinning-and-prompts

## map-api-token-store-and-backup-exclusion

- **kind:** map
- **title:** API token store and the rules that keep app data out of backups
- **path:** api-and-security/map-api-token-store-and-backup-exclusion.md
- **tags:** security, credentials, backup
- **relates_to:** practice-pin-self-signed-certs-warn-on-http, practice-talk-only-to-configured-server

## map-app-id-license-android-versions

- **kind:** map
- **title:** Application ID, license and supported Android versions
- **path:** release/map-app-id-license-android-versions.md
- **tags:** release, fdroid, android
- **relates_to:** map-fdroid-metadata-release-artifacts, practice-own-version-from-v0-1-0-tag
- **derived_from:** docs/PRD.md

## map-catalog-pages-projects-clients-tags

- **kind:** map
- **title:** Catalog pages: projects, clients and tags changed on the server at once
- **path:** screens/map-catalog-pages-projects-clients-tags.md
- **tags:** ui, api, catalog
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-account-cache-and-capabilities, map-change-queue-sends-entry-changes-in-order

## map-change-queue-sends-entry-changes-in-order

- **kind:** map
- **title:** Change queue: ChangeQueue, ChangeSender, BackgroundSend and the offline picker
- **path:** data-and-offline/map-change-queue-sends-entry-changes-in-order.md
- **tags:** offline, cache, room, api, queue
- **relates_to:** practice-never-wait-for-server-on-start-stop, map-account-cache-and-capabilities, map-tokkl-toggl-api, practice-refresh-on-events-no-polling, map-timer-entry-form-and-entry-menu, map-api-client-shared-okhttp-serverurl-and-errors, practice-test-real-tokkl-server-speed-regressions

## map-connection-trust-store-pinning-and-prompts

- **kind:** map
- **title:** Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts
- **path:** api-and-security/map-connection-trust-store-pinning-and-prompts.md
- **tags:** security, network, ui, dialogs
- **relates_to:** practice-pin-self-signed-certs-warn-on-http, map-api-client-shared-okhttp-serverurl-and-errors, map-signin-session-settings-and-navigation-host, map-account-settings-source-and-account-time

## map-design-foundation-spacing-shapes-type-brand

- **kind:** map
- **title:** Design foundation: spacing, shapes, type roles, option rows, brand and insets
- **path:** design/map-design-foundation-spacing-shapes-type-brand.md
- **tags:** ui, theme, accessibility, android
- **relates_to:** map-tokkl-colour-tokens-and-font, practice-tokkl-palette-wcag-english-strings

## map-fdroid-metadata-release-artifacts

- **kind:** map
- **title:** F-Droid metadata layout and release artifacts
- **path:** release/map-fdroid-metadata-release-artifacts.md
- **tags:** fdroid, release
- **derived_from:** docs/PRD.md

## map-gradle-build-sdk-levels-kotlin-and-memory

- **kind:** map
- **title:** Gradle build settings: SDK levels, Kotlin version, memory and build cache
- **path:** build-and-tooling/map-gradle-build-sdk-levels-kotlin-and-memory.md
- **tags:** android, build, gradle
- **relates_to:** map-planned-android-stack, practice-emulator-plain-aosp-image-headless, map-jdk-android-sdk-and-agent-shells

## map-jdk-android-sdk-and-agent-shells

- **kind:** map
- **title:** JDK and Android SDK on this machine, and how agent shells reach them
- **path:** build-and-tooling/map-jdk-android-sdk-and-agent-shells.md
- **tags:** android, tooling, agents, build

## map-oom-kill-ends-agent-session

- **kind:** map
- **title:** An OOM kill inside an agent session's scope ends the whole session
- **path:** build-and-tooling/map-oom-kill-ends-agent-session.md
- **tags:** agents, machine, memory, emulator
- **relates_to:** map-t3-daily-restart-stops-agent-sessions, practice-emulator-plain-aosp-image-headless

## map-planned-android-stack

- **kind:** map
- **title:** Android stack: Compose, Room, WorkManager and OkHttp built; Glance and tile next
- **path:** build-and-tooling/map-planned-android-stack.md
- **tags:** architecture, android, offline
- **derived_from:** docs/PRD.md

## map-reports-summary-from-detailed-search

- **kind:** map
- **title:** Reports: Summary summed on the phone, Detailed paged, Weekly from v3
- **path:** screens/map-reports-summary-from-detailed-search.md
- **tags:** reports, api, toggl, ui
- **relates_to:** map-tokkl-toggl-api, practice-match-web-app-behavior-account-timezone, practice-refresh-on-events-no-polling, practice-port-tokkl-typescript-with-javascript-semantics

## map-running-timer-notification-follows-the-cache

- **kind:** map
- **title:** Running timer notification follows the cache's running entry
- **path:** data-and-offline/map-running-timer-notification-follows-the-cache.md
- **tags:** notification, offline, android
- **relates_to:** practice-never-wait-for-server-on-start-stop, map-change-queue-sends-entry-changes-in-order, practice-talk-only-to-configured-server

## map-signin-session-settings-and-navigation-host

- **kind:** map
- **title:** Sign-in, sign-out, the stored session, settings and the navigation host
- **path:** screens/map-signin-session-settings-and-navigation-host.md
- **tags:** android, ui, credentials, navigation
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-api-token-store-and-backup-exclusion, map-tokkl-colour-tokens-and-font, map-account-settings-source-and-account-time, map-connection-trust-store-pinning-and-prompts, map-account-cache-and-capabilities, map-reports-summary-from-detailed-search, map-change-queue-sends-entry-changes-in-order, map-ui-components-and-compose-layout-gotchas

## map-t3-daily-restart-stops-agent-sessions

- **kind:** map
- **title:** T3 restarts daily and ends running agent sessions
- **path:** build-and-tooling/map-t3-daily-restart-stops-agent-sessions.md
- **tags:** agents, tooling, machine
- **relates_to:** map-oom-kill-ends-agent-session, map-jdk-android-sdk-and-agent-shells

## map-timer-entry-form-and-entry-menu

- **kind:** map
- **title:** Timer, entry form and entry menu: TimerModel, EntryFormModel, EntryActions
- **path:** screens/map-timer-entry-form-and-entry-menu.md
- **tags:** ui, timer, offline, queue, accessibility
- **relates_to:** map-timer-page-history-calendar-and-timer-view, map-change-queue-sends-entry-changes-in-order, practice-never-wait-for-server-on-start-stop, practice-match-web-app-behavior-account-timezone, practice-check-capabilities-not-server-version

## map-timer-page-history-calendar-and-timer-view

- **kind:** map
- **title:** Timer page: layout, the List history, the Calendar week and timerView
- **path:** screens/map-timer-page-history-calendar-and-timer-view.md
- **tags:** ui, history, calendar, offline, api
- **relates_to:** map-account-cache-and-capabilities, map-change-queue-sends-entry-changes-in-order, practice-check-capabilities-not-server-version, map-account-settings-source-and-account-time, map-timer-entry-form-and-entry-menu, map-ui-components-and-compose-layout-gotchas

## map-tokkl-colour-tokens-and-font

- **kind:** map
- **title:** Tokkl colour tokens and Anybody font in ../tokkl
- **path:** design/map-tokkl-colour-tokens-and-font.md
- **tags:** ui, tokkl
- **relates_to:** practice-tokkl-palette-wcag-english-strings, map-design-foundation-spacing-shapes-type-brand
- **derived_from:** docs/PRD.md

## map-tokkl-toggl-api

- **kind:** map
- **title:** Tokkl and Toggl API endpoints, auth and Tokkl-only extensions
- **path:** api-and-security/map-tokkl-toggl-api.md
- **tags:** api, toggl, network
- **derived_from:** docs/PRD.md

## map-ui-components-and-compose-layout-gotchas

- **kind:** map
- **title:** UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas
- **path:** design/map-ui-components-and-compose-layout-gotchas.md
- **tags:** ui, android, accessibility, compose
- **relates_to:** map-design-foundation-spacing-shapes-type-brand, map-signin-session-settings-and-navigation-host

## practice-check-capabilities-not-server-version

- **kind:** practice
- **title:** Check server capabilities instead of requiring a server version
- **path:** api-and-security/practice-check-capabilities-not-server-version.md
- **tags:** api, toggl, compatibility
- **relates_to:** map-account-cache-and-capabilities
- **derived_from:** docs/PRD.md

## practice-drain-sittings-gate-on-memory-not-a-sitting-cap

- **kind:** practice
- **title:** Gate drain sittings on free memory, not on a sitting count
- **path:** practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md
- **tags:** sift, agents, memory
- **relates_to:** practice-reuse-drain-checks-by-content-and-test-configuration, map-oom-kill-ends-agent-session, practice-emulator-plain-aosp-image-headless

## practice-emulator-plain-aosp-image-headless

- **kind:** practice
- **title:** Test on plain AOSP emulators, headless, and stop them afterwards
- **path:** testing/practice-emulator-plain-aosp-image-headless.md
- **tags:** testing, android, emulator, energy, memory
- **relates_to:** map-oom-kill-ends-agent-session, practice-test-real-tokkl-server-speed-regressions, map-jdk-android-sdk-and-agent-shells, practice-run-instrumented-tests-only-through-the-script
- **derived_from:** scripts/instrumented-tests.sh, README.md

## practice-gradle-no-daemon-in-parallel-worktrees

- **kind:** practice
- **title:** Build with --no-daemon in parallel worktrees and never run --stop
- **path:** build-and-tooling/practice-gradle-no-daemon-in-parallel-worktrees.md
- **tags:** build, gradle, agents, worktrees
- **relates_to:** map-jdk-android-sdk-and-agent-shells, practice-run-npm-install-in-every-clone-and-worktree

## practice-instrumented-tests-on-every-api-level

- **kind:** practice
- **title:** Write instrumented tests that hold on API 31 and 36, with the shared helpers
- **path:** testing/practice-instrumented-tests-on-every-api-level.md
- **tags:** testing, android, emulator
- **relates_to:** practice-run-instrumented-tests-only-through-the-script, practice-emulator-plain-aosp-image-headless, practice-screenshot-references-on-tokkl-api36

## practice-match-web-app-behavior-account-timezone

- **kind:** practice
- **title:** Match Tokkl web app behavior in the account timezone
- **path:** data-and-offline/practice-match-web-app-behavior-account-timezone.md
- **tags:** api, ui, time
- **relates_to:** practice-port-tokkl-typescript-with-javascript-semantics, map-account-settings-source-and-account-time
- **derived_from:** docs/PRD.md

## practice-never-wait-for-server-on-start-stop

- **kind:** practice
- **title:** Never make Start or Stop wait for the server
- **path:** data-and-offline/practice-never-wait-for-server-on-start-stop.md
- **tags:** offline, network, api
- **relates_to:** map-change-queue-sends-entry-changes-in-order
- **derived_from:** docs/PRD.md

## practice-opt-sdk-tools-out-of-usage-metrics

- **kind:** practice
- **title:** Opt the Android SDK tools out of usage metrics with ~/.androidrc
- **path:** build-and-tooling/practice-opt-sdk-tools-out-of-usage-metrics.md
- **tags:** privacy, tooling, android
- **relates_to:** practice-talk-only-to-configured-server, map-jdk-android-sdk-and-agent-shells

## practice-own-version-from-v0-1-0-tag

- **kind:** practice
- **title:** Version the app on its own, starting from a hand-made v0.1.0 tag
- **path:** release/practice-own-version-from-v0-1-0-tag.md
- **tags:** release, versioning
- **relates_to:** map-fdroid-metadata-release-artifacts
- **derived_from:** docs/PRD.md

## practice-pin-self-signed-certs-warn-on-http

- **kind:** practice
- **title:** Pin self-signed certificates by fingerprint and warn before plain HTTP
- **path:** api-and-security/practice-pin-self-signed-certs-warn-on-http.md
- **tags:** security, network
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-connection-trust-store-pinning-and-prompts
- **derived_from:** docs/PRD.md

## practice-port-tokkl-typescript-with-javascript-semantics

- **kind:** practice
- **title:** Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases
- **path:** data-and-offline/practice-port-tokkl-typescript-with-javascript-semantics.md
- **tags:** time, parsing, testing, tokkl
- **relates_to:** practice-match-web-app-behavior-account-timezone

## practice-refresh-on-events-no-polling

- **kind:** practice
- **title:** Refresh on events, never on a short polling timer
- **path:** api-and-security/practice-refresh-on-events-no-polling.md
- **tags:** network, toggl, battery
- **derived_from:** docs/PRD.md

## practice-reproducible-builds-project-key

- **kind:** practice
- **title:** Keep release builds reproducible and signed with the project key
- **path:** release/practice-reproducible-builds-project-key.md
- **tags:** release, fdroid, security, ci
- **relates_to:** map-fdroid-metadata-release-artifacts, practice-own-version-from-v0-1-0-tag, practice-run-instrumented-tests-only-through-the-script
- **derived_from:** docs/PRD.md

## practice-reuse-drain-checks-by-content-and-test-configuration

- **kind:** practice
- **title:** Reuse drain checks by content and test configuration
- **path:** practice-reuse-drain-checks-by-content-and-test-configuration.md
- **tags:** sift, testing, agents
- **relates_to:** practice-run-instrumented-tests-only-through-the-script

## practice-run-instrumented-tests-only-through-the-script

- **kind:** practice
- **title:** Run instrumented tests only through scripts/instrumented-tests.sh
- **path:** testing/practice-run-instrumented-tests-only-through-the-script.md
- **tags:** testing, android, emulator, docker
- **relates_to:** practice-test-real-tokkl-server-speed-regressions, practice-emulator-plain-aosp-image-headless, practice-gradle-no-daemon-in-parallel-worktrees, practice-screenshot-references-on-tokkl-api36
- **derived_from:** scripts/instrumented-tests.sh

## practice-run-npm-install-in-every-clone-and-worktree

- **kind:** practice
- **title:** Run npm install in every new clone or worktree to turn on the commit hook
- **path:** build-and-tooling/practice-run-npm-install-in-every-clone-and-worktree.md
- **tags:** git, commits, tooling

## practice-screenshot-references-on-tokkl-api36

- **kind:** practice
- **title:** Record screenshot references on tokkl-api36, in the commit that changes the look
- **path:** testing/practice-screenshot-references-on-tokkl-api36.md
- **tags:** testing, android, emulator, ui
- **relates_to:** practice-run-instrumented-tests-only-through-the-script, practice-emulator-plain-aosp-image-headless, map-design-foundation-spacing-shapes-type-brand

## practice-talk-only-to-configured-server

- **kind:** practice
- **title:** Talk only to the configured server and use only free software
- **path:** api-and-security/practice-talk-only-to-configured-server.md
- **tags:** privacy, network, fdroid, security
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors
- **derived_from:** docs/PRD.md

## practice-test-real-tokkl-server-speed-regressions

- **kind:** practice
- **title:** Test against a real Tokkl server and catch speed regressions in CI
- **path:** testing/practice-test-real-tokkl-server-speed-regressions.md
- **tags:** testing, ci, performance
- **relates_to:** practice-run-instrumented-tests-only-through-the-script, map-change-queue-sends-entry-changes-in-order
- **derived_from:** docs/PRD.md

## practice-tokkl-palette-wcag-english-strings

- **kind:** practice
- **title:** Use Tokkl's palette, WCAG AA contrast and English string resources
- **path:** design/practice-tokkl-palette-wcag-english-strings.md
- **tags:** ui, accessibility, i18n
- **relates_to:** map-tokkl-colour-tokens-and-font, map-design-foundation-spacing-shapes-type-brand
- **derived_from:** docs/PRD.md

## practice-wordmark-only-on-sign-in-and-splash

- **kind:** practice
- **title:** Show the wordmark only on sign-in and the launch splash, never in a header
- **path:** design/practice-wordmark-only-on-sign-in-and-splash.md
- **tags:** ui, brand, design
- **relates_to:** map-design-foundation-spacing-shapes-type-brand, map-tokkl-colour-tokens-and-font, map-timer-page-history-calendar-and-timer-view, map-ui-components-and-compose-layout-gotchas
- **derived_from:** docs/PRD.md
