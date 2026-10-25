---
schema_version: 3
nodes_hash: 'sha256:3d73ca401fd96b7c6e306ab78877bffc37009667897762802185132d6ba1f5ce'
node_count: 87
---
# kenkeep Graph

Total nodes: 87

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

## map-background-queue-work-and-empty-queue-cancellation

- **kind:** map
- **title:** Background queue work and empty-queue cancellation
- **path:** data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md
- **tags:** offline, queue, android
- **relates_to:** practice-refresh-on-events-no-polling, map-ordered-entry-sending-and-refusals
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-cached-entity-picker-and-offline-creation

- **kind:** map
- **title:** Cached entity picker and offline creation
- **path:** data-and-offline/change-queue/map-cached-entity-picker-and-offline-creation.md
- **tags:** offline, cache, ui
- **relates_to:** map-shared-timer-and-entry-fields, map-account-cache-and-capabilities
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-cached-history-days-and-grouping

- **kind:** map
- **title:** Cached history days and grouping
- **path:** screens/timer-history-calendar/map-cached-history-days-and-grouping.md
- **tags:** ui, timer, cache, time
- **relates_to:** map-account-cache-and-capabilities, map-account-settings-source-and-account-time, practice-match-web-app-behavior-account-timezone, map-shared-timer-and-entry-fields
- **derived_from:** b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0

## map-catalog-pages-projects-clients-tags

- **kind:** map
- **title:** Catalog pages: projects, clients and tags changed on the server at once
- **path:** screens/map-catalog-pages-projects-clients-tags.md
- **tags:** ui, api, catalog
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-account-cache-and-capabilities, map-change-queue-sends-entry-changes-in-order

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

## map-entry-form-and-account-time-validation

- **kind:** map
- **title:** Entry form and account-time validation
- **path:** screens/timer-and-entry-editing/map-entry-form-and-account-time-validation.md
- **tags:** ui, timer, offline, queue
- **relates_to:** map-offline-entry-mutations-and-local-id-mapping, practice-match-web-app-behavior-account-timezone, map-shared-timer-and-entry-fields

## map-favorite-launcher-shortcuts-cache-publication-and-tap-handling

- **kind:** map
- **title:** Favorite launcher shortcuts: cache publication and tap handling
- **path:** screens/map-favorite-launcher-shortcuts-cache-publication-and-tap-handling.md
- **tags:** android, shortcuts, offline
- **relates_to:** map-change-queue-sends-entry-changes-in-order, practice-never-wait-for-server-on-start-stop
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:4

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

## map-history-entry-menu-operations

- **kind:** map
- **title:** History entry menu operations
- **path:** screens/timer-and-entry-editing/map-history-entry-menu-operations.md
- **tags:** ui, timer, offline, queue
- **relates_to:** map-cached-history-days-and-grouping, map-offline-entry-mutations-and-local-id-mapping, practice-check-capabilities-not-server-version, practice-match-web-app-behavior-account-timezone

## map-home-screen-widget-cache-state-and-a-native-ticking-clock

- **kind:** map
- **title:** Home screen widget: cache state and a native ticking clock
- **path:** screens/map-home-screen-widget-cache-state-and-a-native-ticking-clock.md
- **tags:** android, widget, offline, battery
- **relates_to:** practice-never-wait-for-server-on-start-stop, map-change-queue-sends-entry-changes-in-order, practice-refresh-on-events-no-polling
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:2

## map-jdk-android-sdk-and-agent-shells

- **kind:** map
- **title:** JDK and Android SDK on this machine, and how agent shells reach them
- **path:** build-and-tooling/map-jdk-android-sdk-and-agent-shells.md
- **tags:** android, tooling, agents, build

## map-offline-entry-mutations-and-local-id-mapping

- **kind:** map
- **title:** Offline entry mutations and local ID mapping
- **path:** data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md
- **tags:** offline, cache, room, queue
- **relates_to:** practice-never-wait-for-server-on-start-stop, map-account-cache-and-capabilities, map-timer-controls-and-cached-start-suggestions
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-oom-kill-ends-agent-session

- **kind:** map
- **title:** An OOM kill inside an agent session's scope ends the whole session
- **path:** build-and-tooling/map-oom-kill-ends-agent-session.md
- **tags:** agents, machine, memory, emulator
- **relates_to:** map-t3-daily-restart-stops-agent-sessions, practice-emulator-plain-aosp-image-headless

## map-ordered-entry-sending-and-refusals

- **kind:** map
- **title:** Ordered entry sending and refusals
- **path:** data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md
- **tags:** offline, api, queue
- **relates_to:** map-tokkl-toggl-api, map-api-client-shared-okhttp-serverurl-and-errors, practice-refresh-on-events-no-polling
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-planned-android-stack

- **kind:** map
- **title:** Android stack: shipped UI, cache and native timer surfaces
- **path:** build-and-tooling/map-planned-android-stack.md
- **tags:** architecture, android, offline
- **derived_from:** docs/PRD.md, 9c8de91d-411b-498c-b4ed-241fabaa877e:map:0

## map-pomodoro-defaults-four-preferences-with-offline-persistence

- **kind:** map
- **title:** Pomodoro defaults: four preferences with offline persistence
- **path:** data-and-offline/map-pomodoro-defaults-four-preferences-with-offline-persistence.md
- **tags:** settings, offline, api, pomodoro
- **relates_to:** map-account-settings-source-and-account-time, practice-check-capabilities-not-server-version
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:6

## map-pomodoro-phase-alarms-and-rearming

- **kind:** map
- **title:** Pomodoro phase alarms and rearming
- **path:** data-and-offline/map-pomodoro-phase-alarms-and-rearming.md
- **tags:** pomodoro, alarms, offline
- **derived_from:** 9d79d56b-af9b-4d8b-8914-97ca84e70b1e:map:0

## map-queued-delete-and-short-stop-undo

- **kind:** map
- **title:** Queued delete and short-stop Undo
- **path:** data-and-offline/change-queue/map-queued-delete-and-short-stop-undo.md
- **tags:** offline, queue, cache
- **relates_to:** map-offline-entry-mutations-and-local-id-mapping, map-ordered-entry-sending-and-refusals
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-quick-settings-tile-default-listening-and-cached-actions

- **kind:** map
- **title:** Quick Settings tile: default listening and cached actions
- **path:** screens/map-quick-settings-tile-default-listening-and-cached-actions.md
- **tags:** android, tile, offline
- **relates_to:** map-change-queue-sends-entry-changes-in-order, practice-never-wait-for-server-on-start-stop
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:3

## map-remote-running-conflicts-and-lost-create-responses

- **kind:** map
- **title:** Remote running conflicts and lost create responses
- **path:** data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md
- **tags:** offline, api, queue
- **relates_to:** map-tokkl-toggl-api, map-api-client-shared-okhttp-serverurl-and-errors, practice-test-real-tokkl-server-speed-regressions
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-reports-summary-from-detailed-search

- **kind:** map
- **title:** Reports: Summary summed on the phone, Detailed paged, Weekly from v3
- **path:** screens/map-reports-summary-from-detailed-search.md
- **tags:** reports, api, toggl, ui
- **relates_to:** map-tokkl-toggl-api, practice-match-web-app-behavior-account-timezone, practice-refresh-on-events-no-polling, practice-port-tokkl-typescript-with-javascript-semantics

## map-room-queue-migration-continuity

- **kind:** map
- **title:** Room queue migration continuity
- **path:** data-and-offline/change-queue/map-room-queue-migration-continuity.md
- **tags:** offline, room, queue
- **relates_to:** map-offline-entry-mutations-and-local-id-mapping, map-account-cache-and-capabilities
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-running-timer-notification-follows-the-cache

- **kind:** map
- **title:** Running timer notification follows the cache's running entry
- **path:** data-and-offline/map-running-timer-notification-follows-the-cache.md
- **tags:** notification, offline, android
- **relates_to:** practice-never-wait-for-server-on-start-stop, map-change-queue-sends-entry-changes-in-order, practice-talk-only-to-configured-server

## map-runningcheck-guarded-background-current-entry-refresh

- **kind:** map
- **title:** RunningCheck: guarded background current-entry refresh
- **path:** data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md
- **tags:** offline, cache, android, battery
- **relates_to:** practice-refresh-on-events-no-polling, map-account-cache-and-capabilities, map-running-timer-notification-follows-the-cache
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:5

## map-saveable-navigation-stack-and-session-guard

- **kind:** map
- **title:** Saveable navigation stack and session guard
- **path:** screens/session-and-navigation/map-saveable-navigation-stack-and-session-guard.md
- **tags:** android, ui, navigation
- **relates_to:** map-connection-trust-store-pinning-and-prompts

## map-session-stores-and-pre-frame-preferences

- **kind:** map
- **title:** Session stores and pre-frame preferences
- **path:** screens/session-and-navigation/map-session-stores-and-pre-frame-preferences.md
- **tags:** android, credentials, storage
- **relates_to:** map-api-token-store-and-backup-exclusion, map-tokkl-colour-tokens-and-font, map-account-settings-source-and-account-time, map-connection-trust-store-pinning-and-prompts, map-account-cache-and-capabilities

## map-shared-timer-and-entry-fields

- **kind:** map
- **title:** Shared timer and entry fields
- **path:** screens/timer-and-entry-editing/map-shared-timer-and-entry-fields.md
- **tags:** ui, timer, accessibility
- **relates_to:** map-timer-controls-and-cached-start-suggestions

## map-sign-in-request-and-credential-admission

- **kind:** map
- **title:** Sign-in request and credential admission
- **path:** screens/session-and-navigation/map-sign-in-request-and-credential-admission.md
- **tags:** android, ui, credentials
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-api-token-store-and-backup-exclusion, map-account-settings-source-and-account-time

## map-sign-out-ordering-and-account-cleanup

- **kind:** map
- **title:** Sign-out ordering and account cleanup
- **path:** screens/session-and-navigation/map-sign-out-ordering-and-account-cleanup.md
- **tags:** android, credentials, offline
- **relates_to:** map-offline-entry-mutations-and-local-id-mapping, map-account-cache-and-capabilities, map-api-token-store-and-backup-exclusion

## map-signed-in-navigation-chrome-and-adaptive-windows

- **kind:** map
- **title:** Signed-in navigation chrome and adaptive windows
- **path:** screens/session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md
- **tags:** android, ui, navigation
- **relates_to:** map-tokkl-colour-tokens-and-font, map-ui-components-and-compose-layout-gotchas, map-reports-summary-from-detailed-search

## map-t3-daily-restart-stops-agent-sessions

- **kind:** map
- **title:** T3 restarts daily and ends running agent sessions
- **path:** build-and-tooling/map-t3-daily-restart-stops-agent-sessions.md
- **tags:** agents, tooling, machine
- **relates_to:** map-oom-kill-ends-agent-session, map-jdk-android-sdk-and-agent-shells

## map-timer-controls-and-cached-start-suggestions

- **kind:** map
- **title:** Timer controls and cached start suggestions
- **path:** screens/timer-and-entry-editing/map-timer-controls-and-cached-start-suggestions.md
- **tags:** ui, timer, offline, queue
- **relates_to:** map-timer-page-layout-and-scroll-away-header, map-offline-entry-mutations-and-local-id-mapping, practice-never-wait-for-server-on-start-stop

## map-timer-list-and-calendar-preference-sync

- **kind:** map
- **title:** Timer List and Calendar preference sync
- **path:** screens/timer-history-calendar/map-timer-list-and-calendar-preference-sync.md
- **tags:** ui, timer, offline
- **relates_to:** practice-check-capabilities-not-server-version, map-account-cache-and-capabilities
- **derived_from:** b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0

## map-timer-page-layout-and-scroll-away-header

- **kind:** map
- **title:** Timer page layout and scroll-away header
- **path:** screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md
- **tags:** ui, timer, android
- **relates_to:** map-ui-components-and-compose-layout-gotchas, map-timer-controls-and-cached-start-suggestions
- **derived_from:** b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0

## map-timer-today-and-this-week-totals

- **kind:** map
- **title:** Timer Today and This week totals
- **path:** screens/timer-history-calendar/map-timer-today-and-this-week-totals.md
- **tags:** ui, timer, cache, time
- **relates_to:** map-account-cache-and-capabilities, map-account-settings-source-and-account-time, practice-match-web-app-behavior-account-timezone, map-offline-entry-mutations-and-local-id-mapping
- **derived_from:** b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0

## map-tokkl-colour-tokens-and-font

- **kind:** map
- **title:** Tokkl colour tokens and Anybody font in ../tokkl
- **path:** design/map-tokkl-colour-tokens-and-font.md
- **tags:** ui, tokkl
- **relates_to:** practice-tokkl-palette-wcag-english-strings, map-design-foundation-spacing-shapes-type-brand
- **derived_from:** docs/PRD.md

## map-tokkl-favorites-storage-and-capability

- **kind:** map
- **title:** Tokkl favorites storage and capability
- **path:** screens/timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md
- **tags:** ui, timer, api
- **relates_to:** practice-check-capabilities-not-server-version, map-timer-controls-and-cached-start-suggestions

## map-tokkl-toggl-api

- **kind:** map
- **title:** Tokkl and Toggl API endpoints, auth and Tokkl-only extensions
- **path:** api-and-security/map-tokkl-toggl-api.md
- **tags:** api, toggl, network
- **derived_from:** docs/PRD.md

## map-trash-capability-and-server-operations

- **kind:** map
- **title:** Trash capability and server operations
- **path:** screens/session-and-navigation/map-trash-capability-and-server-operations.md
- **tags:** android, ui, api
- **relates_to:** map-account-cache-and-capabilities, map-api-client-shared-okhttp-serverurl-and-errors

## map-ui-components-and-compose-layout-gotchas

- **kind:** map
- **title:** UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas
- **path:** design/map-ui-components-and-compose-layout-gotchas.md
- **tags:** ui, android, accessibility, compose
- **relates_to:** map-design-foundation-spacing-shapes-type-brand, map-signin-session-settings-and-navigation-host

## map-week-calendar-layout-and-queued-dragging

- **kind:** map
- **title:** Week calendar layout and queued dragging
- **path:** screens/timer-history-calendar/map-week-calendar-layout-and-queued-dragging.md
- **tags:** ui, timer, cache, accessibility
- **relates_to:** map-offline-entry-mutations-and-local-id-mapping, map-account-settings-source-and-account-time, map-ui-components-and-compose-layout-gotchas, practice-match-web-app-behavior-account-timezone
- **derived_from:** b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0

## practice-account-for-emulator-recording-and-screenshot-quirks

- **kind:** practice
- **title:** Account for emulator recording and screenshot quirks
- **path:** testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md
- **tags:** testing, android, emulator
- **relates_to:** practice-screenshot-references-on-tokkl-api36
- **derived_from:** scripts/instrumented-tests.sh, README.md

## practice-assert-text-height-and-dialog-bounds-at-large-fonts

- **kind:** practice
- **title:** Assert text height and dialog bounds at large fonts
- **path:** testing/instrumented-test-authoring/practice-assert-text-height-and-dialog-bounds-at-large-fonts.md
- **tags:** testing, android, accessibility
- **relates_to:** practice-screenshot-references-on-tokkl-api36
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3

## practice-check-capabilities-not-server-version

- **kind:** practice
- **title:** Check server capabilities instead of requiring a server version
- **path:** api-and-security/practice-check-capabilities-not-server-version.md
- **tags:** api, toggl, compatibility
- **relates_to:** map-account-cache-and-capabilities
- **derived_from:** docs/PRD.md, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:2

## practice-choose-plain-aosp-images-and-compatible-renderers

- **kind:** practice
- **title:** Choose plain AOSP images and compatible renderers
- **path:** testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md
- **tags:** testing, android, emulator
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, map-jdk-android-sdk-and-agent-shells
- **derived_from:** scripts/instrumented-tests.sh, README.md

## practice-clean-up-only-owned-emulator-resources

- **kind:** practice
- **title:** Clean up only owned emulator resources
- **path:** testing/emulator-runtime/practice-clean-up-only-owned-emulator-resources.md
- **tags:** testing, emulator, energy
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, map-oom-kill-ends-agent-session
- **derived_from:** scripts/instrumented-tests.sh, README.md

## practice-drain-sittings-gate-on-memory-not-a-sitting-cap

- **kind:** practice
- **title:** Gate drain sittings on free memory, not on a sitting count
- **path:** build-and-tooling/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md
- **tags:** sift, agents, memory
- **relates_to:** practice-reuse-drain-checks-by-content-and-test-configuration, map-oom-kill-ends-agent-session, practice-emulator-plain-aosp-image-headless

## practice-gradle-no-daemon-in-parallel-worktrees

- **kind:** practice
- **title:** Build with --no-daemon in parallel worktrees and never run --stop
- **path:** build-and-tooling/practice-gradle-no-daemon-in-parallel-worktrees.md
- **tags:** build, gradle, agents, worktrees
- **relates_to:** map-jdk-android-sdk-and-agent-shells, practice-run-npm-install-in-every-clone-and-worktree

## practice-honor-account-clock-formatting-in-time-ports

- **kind:** practice
- **title:** Honor account clock formatting in time ports
- **path:** data-and-offline/typescript-time-ports/practice-honor-account-clock-formatting-in-time-ports.md
- **tags:** time, formatting, tokkl
- **relates_to:** practice-match-web-app-behavior-account-timezone
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0

## practice-isolate-all-known-failing-tests-before-full-suite-validation

- **kind:** practice
- **title:** Isolate all known failing tests before full-suite validation
- **path:** practice-isolate-all-known-failing-tests-before-full-suite-validation.md
- **tags:** testing, ci, agents
- **relates_to:** practice-run-instrumented-tests-only-through-the-script, practice-reuse-drain-checks-by-content-and-test-configuration

## practice-isolate-session-and-background-state-with-shared-rules

- **kind:** practice
- **title:** Isolate session and background state with shared rules
- **path:** testing/instrumented-test-authoring/practice-isolate-session-and-background-state-with-shared-rules.md
- **tags:** testing, android, offline
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3

## practice-keep-instrumented-credentials-out-of-process-arguments

- **kind:** practice
- **title:** Keep instrumented credentials out of process arguments
- **path:** testing/instrumented-runner/practice-keep-instrumented-credentials-out-of-process-arguments.md
- **tags:** testing, android, security
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-keep-upstream-golden-cases-with-source-pinned-ports

- **kind:** practice
- **title:** Keep upstream golden cases with source-pinned ports
- **path:** data-and-offline/typescript-time-ports/practice-keep-upstream-golden-cases-with-source-pinned-ports.md
- **tags:** time, testing, tokkl
- **relates_to:** practice-match-web-app-behavior-account-timezone
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0

## practice-let-the-runner-own-emulator-boot-and-settings

- **kind:** practice
- **title:** Let the runner own emulator boot and settings
- **path:** testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md
- **tags:** testing, android, emulator
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection
- **derived_from:** scripts/instrumented-tests.sh, README.md

## practice-match-javascript-decimal-and-percentage-formatting

- **kind:** practice
- **title:** Match JavaScript decimal and percentage formatting
- **path:** data-and-offline/typescript-time-ports/practice-match-javascript-decimal-and-percentage-formatting.md
- **tags:** time, formatting, tokkl
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0

## practice-match-production-windows-and-real-touch-hit-testing

- **kind:** practice
- **title:** Match production windows and real touch hit testing
- **path:** testing/instrumented-test-authoring/practice-match-production-windows-and-real-touch-hit-testing.md
- **tags:** testing, android, ui
- **relates_to:** practice-let-the-runner-own-emulator-boot-and-settings
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3

## practice-match-web-app-behavior-account-timezone

- **kind:** practice
- **title:** Match Tokkl web app behavior in the account timezone
- **path:** data-and-offline/practice-match-web-app-behavior-account-timezone.md
- **tags:** api, ui, time
- **relates_to:** practice-port-tokkl-typescript-with-javascript-semantics, map-account-settings-source-and-account-time
- **derived_from:** docs/PRD.md, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:1

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

## practice-pass-class-and-package-lists-through-runner-filter-files

- **kind:** practice
- **title:** Pass class and package lists through runner filter files
- **path:** testing/instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md
- **tags:** testing, android, gradle
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-gradle-no-daemon-in-parallel-worktrees
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-pin-self-signed-certs-warn-on-http

- **kind:** practice
- **title:** Pin self-signed certificates by fingerprint and warn before plain HTTP
- **path:** api-and-security/practice-pin-self-signed-certs-warn-on-http.md
- **tags:** security, network
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-connection-trust-store-pinning-and-prompts
- **derived_from:** docs/PRD.md

## practice-preserve-javascript-parsing-and-numeric-boundaries

- **kind:** practice
- **title:** Preserve JavaScript parsing and numeric boundaries
- **path:** data-and-offline/typescript-time-ports/practice-preserve-javascript-parsing-and-numeric-boundaries.md
- **tags:** time, parsing, tokkl
- **relates_to:** practice-match-web-app-behavior-account-timezone
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0

## practice-preserve-tokkl-timezone-conversion-and-database-limits

- **kind:** practice
- **title:** Preserve Tokkl timezone conversion and database limits
- **path:** data-and-offline/typescript-time-ports/practice-preserve-tokkl-timezone-conversion-and-database-limits.md
- **tags:** time, parsing, tokkl
- **relates_to:** practice-match-web-app-behavior-account-timezone
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0

## practice-refresh-on-events-no-polling

- **kind:** practice
- **title:** Refresh on events, never on a short polling timer
- **path:** api-and-security/practice-refresh-on-events-no-polling.md
- **tags:** network, toggl, battery
- **derived_from:** docs/PRD.md, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:5

## practice-reproducible-builds-project-key

- **kind:** practice
- **title:** Keep release builds reproducible and signed with the project key
- **path:** release/practice-reproducible-builds-project-key.md
- **tags:** release, fdroid, security, ci
- **relates_to:** map-fdroid-metadata-release-artifacts, practice-own-version-from-v0-1-0-tag, practice-run-instrumented-tests-only-through-the-script
- **derived_from:** docs/PRD.md

## practice-respect-emulator-slots-and-resource-ownership

- **kind:** practice
- **title:** Respect emulator slots and resource ownership
- **path:** testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md
- **tags:** testing, emulator, memory
- **relates_to:** map-oom-kill-ends-agent-session, practice-test-real-tokkl-server-speed-regressions, practice-use-the-instrumented-runner-and-its-device-selection
- **derived_from:** scripts/instrumented-tests.sh, README.md

## practice-reuse-drain-checks-by-content-and-test-configuration

- **kind:** practice
- **title:** Reuse drain checks by content and test configuration
- **path:** testing/practice-reuse-drain-checks-by-content-and-test-configuration.md
- **tags:** sift, testing, agents
- **relates_to:** practice-run-instrumented-tests-only-through-the-script

## practice-reuse-relays-and-throwaway-server-accounts

- **kind:** practice
- **title:** Reuse relays and throwaway server accounts
- **path:** testing/instrumented-test-authoring/practice-reuse-relays-and-throwaway-server-accounts.md
- **tags:** testing, android, api
- **relates_to:** practice-use-the-pinned-test-server-and-tls-proxies
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3

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

## practice-split-process-restart-acceptance-into-orchestrated-steps

- **kind:** practice
- **title:** Split process restart acceptance into orchestrated steps
- **path:** testing/instrumented-test-authoring/practice-split-process-restart-acceptance-into-orchestrated-steps.md
- **tags:** testing, android, lifecycle
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3

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

## practice-use-runner-annotations-and-owned-settings-across-apis

- **kind:** practice
- **title:** Use runner annotations and owned settings across APIs
- **path:** testing/instrumented-test-authoring/practice-use-runner-annotations-and-owned-settings-across-apis.md
- **tags:** testing, android, emulator
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-let-the-runner-own-emulator-boot-and-settings, practice-screenshot-references-on-tokkl-api36
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3

## practice-use-the-instrumented-runner-and-its-device-selection

- **kind:** practice
- **title:** Use the instrumented runner and its device selection
- **path:** testing/instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md
- **tags:** testing, android, emulator, docker
- **relates_to:** practice-let-the-runner-own-emulator-boot-and-settings, practice-test-real-tokkl-server-speed-regressions, practice-gradle-no-daemon-in-parallel-worktrees
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-use-the-pinned-test-server-and-tls-proxies

- **kind:** practice
- **title:** Use the pinned test server and TLS proxies
- **path:** testing/instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md
- **tags:** testing, android, docker, api
- **relates_to:** practice-let-the-runner-own-emulator-boot-and-settings, practice-test-real-tokkl-server-speed-regressions
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-verify-device-test-results-and-process-isolation-receipts

- **kind:** practice
- **title:** Verify device test results and process isolation receipts
- **path:** testing/instrumented-runner/practice-verify-device-test-results-and-process-isolation-receipts.md
- **tags:** testing, android, emulator
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-screenshot-references-on-tokkl-api36
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-wordmark-only-on-sign-in-and-splash

- **kind:** practice
- **title:** Show the wordmark only on sign-in and the launch splash, never in a header
- **path:** design/practice-wordmark-only-on-sign-in-and-splash.md
- **tags:** ui, brand, design
- **relates_to:** map-design-foundation-spacing-shapes-type-brand, map-tokkl-colour-tokens-and-font, map-timer-page-history-calendar-and-timer-view, map-ui-components-and-compose-layout-gotchas
- **derived_from:** docs/PRD.md
