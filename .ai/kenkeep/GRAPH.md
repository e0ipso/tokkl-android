---
schema_version: 3
nodes_hash: 'sha256:23d51ed302336bf051c27e0c1d4ed7a096c18db777b5c3107ec084ec26cb1c9d'
node_count: 123
---
# kenkeep Graph

Total nodes: 123

## map-account-cache-storage-and-history-window

- **kind:** map
- **title:** Account cache storage and history window
- **path:** data-and-offline/account-cache/map-account-cache-storage-and-history-window.md
- **tags:** offline, cache, room
- **relates_to:** map-account-settings-source-and-account-time, practice-never-wait-for-server-on-start-stop, map-sign-out-ordering-and-account-cleanup, map-ordered-entry-sending-and-refusals, map-offline-entry-mutations-and-local-id-mapping, map-account-refresh-steps-and-preference-readers

## map-account-refresh-steps-and-preference-readers

- **kind:** map
- **title:** Account refresh steps and preference readers
- **path:** data-and-offline/account-cache/map-account-refresh-steps-and-preference-readers.md
- **tags:** api, cache, settings
- **relates_to:** map-account-settings-source-and-account-time, map-timer-state-holder-and-sync-service, map-account-cache-storage-and-history-window, map-capability-probes-and-persisted-feature-flags

## map-account-settings-loading-and-refresh

- **kind:** map
- **title:** Account settings loading and refresh
- **path:** data-and-offline/account-settings-and-time/map-account-settings-loading-and-refresh.md
- **tags:** settings, api, offline, cache
- **relates_to:** map-sign-in-request-and-credential-admission, map-session-stores-and-pre-frame-preferences, map-sign-out-ordering-and-account-cleanup, map-saveable-navigation-stack-and-session-guard, map-trash-capability-and-server-operations, map-connection-trust-store-pinning-and-prompts, practice-refresh-on-events-no-polling, map-account-cache-and-capabilities, map-account-time-fields-and-validation

## map-account-time-fields-and-validation

- **kind:** map
- **title:** Account time fields and validation
- **path:** data-and-offline/account-settings-and-time/map-account-time-fields-and-validation.md
- **tags:** time, settings, android
- **relates_to:** practice-match-web-app-behavior-account-timezone, map-signed-in-navigation-chrome-and-adaptive-windows, map-saveable-navigation-stack-and-session-guard, map-account-settings-loading-and-refresh

## map-account-timer-state-lifetime

- **kind:** map
- **title:** Account timer state lifetime
- **path:** data-and-offline/timer-state-and-sync/map-account-timer-state-lifetime.md
- **tags:** pomodoro, sync, offline
- **relates_to:** map-account-cache-and-capabilities, map-timer-state-acceptance-and-persistence, map-online-timer-actions-and-native-control-failures, map-timer-event-stream-and-service-lifetime

## map-android-sdk-kotlin-room-and-gradle-wrapper-settings

- **kind:** map
- **title:** Android SDK Kotlin Room and Gradle wrapper settings
- **path:** build-and-tooling/android-build/gradle-and-modules/map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md
- **tags:** android, build, gradle
- **relates_to:** map-jdk-android-sdk-and-agent-shells, practice-choose-plain-aosp-images-and-compatible-renderers, practice-let-the-runner-own-emulator-boot-and-settings, practice-account-for-emulator-recording-and-screenshot-quirks

## map-api-execution-retries-and-errors

- **kind:** map
- **title:** API execution retries and errors
- **path:** api-and-security/api-client/map-api-execution-retries-and-errors.md
- **tags:** api, network, coroutines
- **relates_to:** map-timer-state-holder-and-sync-service, map-typed-api-resources-and-request-contracts

## map-api-test-fixtures

- **kind:** map
- **title:** API test fixtures
- **path:** api-and-security/api-client/map-api-test-fixtures.md
- **tags:** api, testing
- **relates_to:** map-typed-api-resources-and-request-contracts, map-shared-http-client-and-configured-server-security

## map-api-token-store-and-backup-exclusion

- **kind:** map
- **title:** API token store and the rules that keep app data out of backups
- **path:** api-and-security/map-api-token-store-and-backup-exclusion.md
- **tags:** security, credentials, backup
- **relates_to:** practice-pin-self-signed-certs-warn-on-http, practice-talk-only-to-configured-server

## map-app-composition-and-lifecycle-hooks

- **kind:** map
- **title:** App composition and lifecycle hooks
- **path:** build-and-tooling/android-build/gradle-and-modules/map-app-composition-and-lifecycle-hooks.md
- **tags:** architecture, android, build
- **relates_to:** map-planned-android-stack, map-core-and-feature-module-boundaries

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

## map-build-conventions-resources-and-android-api-compatibility

- **kind:** map
- **title:** Build conventions resources and Android API compatibility
- **path:** build-and-tooling/android-build/gradle-and-modules/map-build-conventions-resources-and-android-api-compatibility.md
- **tags:** android, build, gradle
- **relates_to:** map-core-and-feature-module-boundaries, map-android-sdk-kotlin-room-and-gradle-wrapper-settings

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

## map-capability-probes-and-persisted-feature-flags

- **kind:** map
- **title:** Capability probes and persisted feature flags
- **path:** data-and-offline/account-cache/map-capability-probes-and-persisted-feature-flags.md
- **tags:** api, compatibility, offline
- **relates_to:** practice-check-capabilities-not-server-version, map-timer-state-holder-and-sync-service, map-trash-capability-and-server-operations, map-account-refresh-steps-and-preference-readers

## map-catalog-form-restoration-and-project-tasks

- **kind:** map
- **title:** Catalog form restoration and project tasks
- **path:** screens/catalog-pages/map-catalog-form-restoration-and-project-tasks.md
- **tags:** ui, catalog, navigation
- **relates_to:** map-catalog-page-loading-and-lifecycle, map-catalog-repositories-and-server-mutations, map-cached-entity-picker-and-offline-creation

## map-catalog-page-loading-and-lifecycle

- **kind:** map
- **title:** Catalog page loading and lifecycle
- **path:** screens/catalog-pages/map-catalog-page-loading-and-lifecycle.md
- **tags:** ui, catalog, lifecycle
- **relates_to:** map-account-cache-and-capabilities, map-catalog-form-restoration-and-project-tasks, map-catalog-repositories-and-server-mutations

## map-catalog-repositories-and-server-mutations

- **kind:** map
- **title:** Catalog repositories and server mutations
- **path:** screens/catalog-pages/map-catalog-repositories-and-server-mutations.md
- **tags:** api, catalog, offline
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-account-cache-and-capabilities, map-offline-entry-mutations-and-local-id-mapping, map-ordered-entry-sending-and-refusals, map-background-queue-work-and-empty-queue-cancellation, map-remote-running-conflicts-and-lost-create-responses, map-queued-delete-and-short-stop-undo, map-room-queue-migration-continuity, map-cached-entity-picker-and-offline-creation, map-catalog-page-loading-and-lifecycle, map-catalog-form-restoration-and-project-tasks

## map-connection-trust-store-pinning-and-prompts

- **kind:** map
- **title:** Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts
- **path:** api-and-security/map-connection-trust-store-pinning-and-prompts.md
- **tags:** security, network, ui, dialogs
- **relates_to:** practice-pin-self-signed-certs-warn-on-http, map-api-client-shared-okhttp-serverurl-and-errors, map-sign-in-request-and-credential-admission, map-session-stores-and-pre-frame-preferences, map-sign-out-ordering-and-account-cleanup, map-saveable-navigation-stack-and-session-guard, map-signed-in-navigation-chrome-and-adaptive-windows, map-trash-capability-and-server-operations, map-account-settings-source-and-account-time

## map-core-and-feature-module-boundaries

- **kind:** map
- **title:** Core and feature module boundaries
- **path:** build-and-tooling/android-build/gradle-and-modules/map-core-and-feature-module-boundaries.md
- **tags:** architecture, android, gradle
- **relates_to:** map-planned-android-stack, map-app-composition-and-lifecycle-hooks, map-build-conventions-resources-and-android-api-compatibility

## map-core-model-and-typed-ids

- **kind:** map
- **title:** core:model: what the screens read, with typed ids
- **path:** data-and-offline/map-core-model-and-typed-ids.md
- **tags:** cache, room, ui, api
- **relates_to:** map-account-cache-and-capabilities, map-offline-entry-mutations-and-local-id-mapping, map-catalog-pages-projects-clients-tags, map-saveable-navigation-stack-and-session-guard, map-gradle-build-sdk-levels-kotlin-and-memory

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

## map-external-navigation-requests-and-intent-consumption

- **kind:** map
- **title:** External navigation requests and intent consumption
- **path:** screens/session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md
- **tags:** android, navigation, ui
- **relates_to:** map-navigation-route-serialization-and-feature-registration, map-navigation-stores-and-restored-session-guard

## map-favorite-launcher-shortcuts-cache-publication-and-tap-handling

- **kind:** map
- **title:** Favorite launcher shortcuts: cache publication and tap handling
- **path:** screens/map-favorite-launcher-shortcuts-cache-publication-and-tap-handling.md
- **tags:** android, shortcuts, offline
- **relates_to:** map-offline-entry-mutations-and-local-id-mapping, map-ordered-entry-sending-and-refusals, map-background-queue-work-and-empty-queue-cancellation, map-remote-running-conflicts-and-lost-create-responses, map-queued-delete-and-short-stop-undo, map-room-queue-migration-continuity, map-cached-entity-picker-and-offline-creation, practice-never-wait-for-server-on-start-stop
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:4

## map-fdroid-metadata-release-artifacts

- **kind:** map
- **title:** F-Droid metadata layout and release artifacts
- **path:** release/map-fdroid-metadata-release-artifacts.md
- **tags:** fdroid, release
- **relates_to:** map-release-signing-key-actions-secrets-and-signature-checks, practice-reproducible-builds-project-key, map-app-id-license-android-versions, map-oom-kill-ends-agent-session
- **derived_from:** docs/PRD.md, fdroid/README.md

## map-gradle-memory-caches-and-checkout-local-lint

- **kind:** map
- **title:** Gradle memory caches and checkout-local lint
- **path:** build-and-tooling/android-build/gradle-and-modules/map-gradle-memory-caches-and-checkout-local-lint.md
- **tags:** build, gradle, memory
- **relates_to:** map-jdk-android-sdk-and-agent-shells, practice-respect-emulator-slots-and-resource-ownership, practice-let-the-runner-own-emulator-boot-and-settings

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
- **relates_to:** map-timer-state-holder-and-sync-service, practice-never-wait-for-server-on-start-stop, map-ordered-entry-sending-and-refusals, map-offline-entry-mutations-and-local-id-mapping, practice-refresh-on-events-no-polling, map-pomodoro-home-screen-widget
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:2

## map-jdk-android-sdk-and-agent-shells

- **kind:** map
- **title:** JDK and Android SDK on this machine, and how agent shells reach them
- **path:** build-and-tooling/host-runtime/map-jdk-android-sdk-and-agent-shells.md
- **tags:** android, tooling, agents, build

## map-navigation-route-serialization-and-feature-registration

- **kind:** map
- **title:** Navigation route serialization and feature registration
- **path:** screens/session-and-navigation/navigation-state/map-navigation-route-serialization-and-feature-registration.md
- **tags:** android, ui, navigation
- **relates_to:** map-navigation-stores-and-restored-session-guard, map-external-navigation-requests-and-intent-consumption

## map-navigation-stores-and-restored-session-guard

- **kind:** map
- **title:** Navigation stores and restored session guard
- **path:** screens/session-and-navigation/navigation-state/map-navigation-stores-and-restored-session-guard.md
- **tags:** android, navigation, credentials
- **relates_to:** map-connection-trust-store-pinning-and-prompts, map-navigation-route-serialization-and-feature-registration, map-external-navigation-requests-and-intent-consumption

## map-no-time-entries

- **kind:** map
- **title:** No-time entries
- **path:** screens/timer-and-entry-editing/map-no-time-entries.md
- **tags:** entries, calendar, offline
- **relates_to:** map-entry-form-and-account-time-validation, map-week-calendar-layout-and-queued-dragging, map-cached-history-days-and-grouping, map-history-entry-menu-operations, map-offline-entry-mutations-and-local-id-mapping, practice-check-capabilities-not-server-version, practice-match-web-app-behavior-account-timezone
- **derived_from:** 771b5c9e-92ac-4792-b45b-2e479b3c3dba:map:0

## map-offline-entry-mutations-and-local-id-mapping

- **kind:** map
- **title:** Offline entry mutations and local ID mapping
- **path:** data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md
- **tags:** offline, cache, room, queue
- **relates_to:** practice-never-wait-for-server-on-start-stop, map-account-cache-and-capabilities, map-timer-controls-and-cached-start-suggestions
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-online-timer-actions-and-native-control-failures

- **kind:** map
- **title:** Online timer actions and native control failures
- **path:** data-and-offline/timer-state-and-sync/map-online-timer-actions-and-native-control-failures.md
- **tags:** pomodoro, network, android, offline
- **relates_to:** practice-never-wait-for-server-on-start-stop, map-api-client-shared-okhttp-serverurl-and-errors, map-tokkl-toggl-api, map-pomodoro-phase-alarms-and-rearming, map-timer-state-acceptance-and-persistence, map-account-timer-state-lifetime

## map-oom-kill-ends-agent-session

- **kind:** map
- **title:** An OOM kill inside an agent session's scope ends the whole session
- **path:** build-and-tooling/host-runtime/map-oom-kill-ends-agent-session.md
- **tags:** agents, machine, memory, emulator
- **relates_to:** map-t3-daily-restart-stops-agent-sessions, practice-choose-plain-aosp-images-and-compatible-renderers, practice-respect-emulator-slots-and-resource-ownership, practice-let-the-runner-own-emulator-boot-and-settings, practice-clean-up-only-owned-emulator-resources, practice-account-for-emulator-recording-and-screenshot-quirks

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
- **path:** build-and-tooling/android-build/map-planned-android-stack.md
- **tags:** architecture, android, offline
- **derived_from:** docs/PRD.md, 9c8de91d-411b-498c-b4ed-241fabaa877e:map:0

## map-pomodoro-defaults-four-preferences-with-offline-persistence

- **kind:** map
- **title:** Pomodoro defaults: four preferences with offline persistence
- **path:** data-and-offline/map-pomodoro-defaults-four-preferences-with-offline-persistence.md
- **tags:** settings, offline, api, pomodoro
- **relates_to:** map-account-settings-source-and-account-time, practice-check-capabilities-not-server-version
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:6

## map-pomodoro-home-screen-widget

- **kind:** map
- **title:** Pomodoro home screen widget
- **path:** screens/map-pomodoro-home-screen-widget.md
- **tags:** android, widget, pomodoro, battery
- **relates_to:** map-home-screen-widget-cache-state-and-a-native-ticking-clock, map-timer-state-holder-and-sync-service, map-pomodoro-phase-alarms-and-rearming

## map-pomodoro-phase-alarms-and-rearming

- **kind:** map
- **title:** Pomodoro phase alarms and rearming
- **path:** data-and-offline/map-pomodoro-phase-alarms-and-rearming.md
- **tags:** pomodoro, alarms, sync
- **relates_to:** map-timer-state-holder-and-sync-service
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
- **relates_to:** map-timer-state-holder-and-sync-service, map-ordered-entry-sending-and-refusals, map-offline-entry-mutations-and-local-id-mapping, practice-never-wait-for-server-on-start-stop
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:3

## map-release-signing-key-actions-secrets-and-signature-checks

- **kind:** map
- **title:** Release signing key, Actions secrets and signature checks
- **path:** release/map-release-signing-key-actions-secrets-and-signature-checks.md
- **tags:** release, fdroid, security, ci
- **relates_to:** practice-reproducible-builds-project-key, practice-own-version-from-v0-1-0-tag, map-fdroid-metadata-release-artifacts

## map-remote-running-conflicts-and-lost-create-responses

- **kind:** map
- **title:** Remote running conflicts and lost create responses
- **path:** data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md
- **tags:** offline, api, queue
- **relates_to:** map-tokkl-toggl-api, map-api-client-shared-okhttp-serverurl-and-errors, practice-test-against-a-real-tokkl-server
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1

## map-report-apis-rounding-and-pagination

- **kind:** map
- **title:** Report APIs rounding and pagination
- **path:** screens/reports/map-report-apis-rounding-and-pagination.md
- **tags:** reports, api, toggl
- **relates_to:** map-tokkl-toggl-api, practice-preserve-javascript-parsing-and-numeric-boundaries, practice-keep-upstream-golden-cases-with-source-pinned-ports, practice-match-javascript-decimal-and-percentage-formatting, map-report-ranges-and-cached-filters

## map-report-ranges-and-cached-filters

- **kind:** map
- **title:** Report ranges and cached filters
- **path:** screens/reports/map-report-ranges-and-cached-filters.md
- **tags:** reports, ui, toggl
- **relates_to:** practice-match-web-app-behavior-account-timezone, practice-honor-account-clock-formatting-in-time-ports, practice-preserve-tokkl-timezone-conversion-and-database-limits, map-report-apis-rounding-and-pagination, map-report-state-lifetimes-and-widget-navigation

## map-report-state-lifetimes-and-widget-navigation

- **kind:** map
- **title:** Report state lifetimes and widget navigation
- **path:** screens/reports/map-report-state-lifetimes-and-widget-navigation.md
- **tags:** reports, ui, navigation
- **relates_to:** practice-refresh-on-events-no-polling, map-report-ranges-and-cached-filters, map-report-apis-rounding-and-pagination

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
- **relates_to:** map-timer-state-holder-and-sync-service, practice-never-wait-for-server-on-start-stop, map-ordered-entry-sending-and-refusals, map-offline-entry-mutations-and-local-id-mapping, practice-talk-only-to-configured-server

## map-runningcheck-guarded-background-current-entry-refresh

- **kind:** map
- **title:** RunningCheck: guarded background current-entry refresh
- **path:** data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md
- **tags:** offline, cache, android, battery
- **relates_to:** practice-refresh-on-events-no-polling, map-account-cache-and-capabilities, map-running-timer-notification-follows-the-cache, map-timer-state-holder-and-sync-service
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:map:5

## map-session-stores-and-pre-frame-preferences

- **kind:** map
- **title:** Session stores and pre-frame preferences
- **path:** screens/session-and-navigation/map-session-stores-and-pre-frame-preferences.md
- **tags:** android, credentials, storage
- **relates_to:** map-api-token-store-and-backup-exclusion, map-tokkl-colour-tokens-and-font, map-account-settings-source-and-account-time, map-connection-trust-store-pinning-and-prompts, map-account-cache-and-capabilities

## map-shared-http-client-and-configured-server-security

- **kind:** map
- **title:** Shared HTTP client and configured server security
- **path:** api-and-security/api-client/map-shared-http-client-and-configured-server-security.md
- **tags:** api, network, okhttp, security
- **relates_to:** practice-talk-only-to-configured-server, practice-pin-self-signed-certs-warn-on-http, map-connection-trust-store-pinning-and-prompts

## map-shared-test-fixtures-and-module-move-verification

- **kind:** map
- **title:** Shared test fixtures and module move verification
- **path:** build-and-tooling/android-build/gradle-and-modules/map-shared-test-fixtures-and-module-move-verification.md
- **tags:** testing, architecture, gradle
- **relates_to:** map-core-and-feature-module-boundaries, practice-respect-emulator-slots-and-resource-ownership, practice-clean-up-only-owned-emulator-resources

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
- **path:** build-and-tooling/host-runtime/map-t3-daily-restart-stops-agent-sessions.md
- **tags:** agents, tooling, machine
- **relates_to:** map-oom-kill-ends-agent-session, map-jdk-android-sdk-and-agent-shells

## map-timer-controls-and-cached-start-suggestions

- **kind:** map
- **title:** Timer controls and cached start suggestions
- **path:** screens/timer-and-entry-editing/map-timer-controls-and-cached-start-suggestions.md
- **tags:** ui, timer, offline, queue
- **relates_to:** map-timer-page-layout-and-scroll-away-header, map-offline-entry-mutations-and-local-id-mapping, practice-never-wait-for-server-on-start-stop

## map-timer-event-stream-and-service-lifetime

- **kind:** map
- **title:** Timer event stream and service lifetime
- **path:** data-and-offline/timer-state-and-sync/map-timer-event-stream-and-service-lifetime.md
- **tags:** sync, network, android, battery
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-tokkl-toggl-api, map-pomodoro-phase-alarms-and-rearming, map-timer-state-acceptance-and-persistence, map-account-timer-state-lifetime

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

## map-timer-state-acceptance-and-persistence

- **kind:** map
- **title:** Timer state acceptance and persistence
- **path:** data-and-offline/timer-state-and-sync/map-timer-state-acceptance-and-persistence.md
- **tags:** pomodoro, sync, offline
- **relates_to:** map-account-cache-and-capabilities, map-runningcheck-guarded-background-current-entry-refresh, map-tokkl-toggl-api, map-account-timer-state-lifetime, map-online-timer-actions-and-native-control-failures, map-timer-event-stream-and-service-lifetime

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
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors
- **derived_from:** docs/PRD.md

## map-trash-capability-and-server-operations

- **kind:** map
- **title:** Trash capability and server operations
- **path:** screens/session-and-navigation/map-trash-capability-and-server-operations.md
- **tags:** android, ui, api
- **relates_to:** map-account-cache-and-capabilities, map-api-client-shared-okhttp-serverurl-and-errors

## map-typed-api-resources-and-request-contracts

- **kind:** map
- **title:** Typed API resources and request contracts
- **path:** api-and-security/api-client/map-typed-api-resources-and-request-contracts.md
- **tags:** api, network
- **relates_to:** map-tokkl-toggl-api, map-api-execution-retries-and-errors, map-api-test-fixtures

## map-ui-components-and-compose-layout-gotchas

- **kind:** map
- **title:** UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas
- **path:** design/map-ui-components-and-compose-layout-gotchas.md
- **tags:** ui, android, accessibility, compose
- **relates_to:** map-design-foundation-spacing-shapes-type-brand, map-sign-in-request-and-credential-admission, map-session-stores-and-pre-frame-preferences, map-sign-out-ordering-and-account-cleanup, map-saveable-navigation-stack-and-session-guard, map-signed-in-navigation-chrome-and-adaptive-windows, map-trash-capability-and-server-operations

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
- **derived_from:** scripts/instrumented-tests.sh

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
- **relates_to:** map-account-cache-and-capabilities, map-timer-state-holder-and-sync-service
- **derived_from:** docs/PRD.md, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:2

## practice-choose-plain-aosp-images-and-compatible-renderers

- **kind:** practice
- **title:** Choose plain AOSP images and compatible renderers
- **path:** testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md
- **tags:** testing, android, emulator
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, map-jdk-android-sdk-and-agent-shells
- **derived_from:** scripts/instrumented-tests.sh

## practice-clean-up-only-owned-emulator-resources

- **kind:** practice
- **title:** Clean up only owned emulator resources
- **path:** testing/emulator-runtime/practice-clean-up-only-owned-emulator-resources.md
- **tags:** testing, emulator, energy
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, map-oom-kill-ends-agent-session
- **derived_from:** scripts/instrumented-tests.sh

## practice-compare-clean-unsigned-release-builds

- **kind:** practice
- **title:** Compare clean unsigned release builds
- **path:** release/reproducible-builds/practice-compare-clean-unsigned-release-builds.md
- **tags:** release, fdroid, ci
- **relates_to:** map-fdroid-metadata-release-artifacts, practice-own-version-from-v0-1-0-tag, practice-sign-releases-and-verify-optimized-apks, practice-pin-ci-actions-and-run-the-android-matrix
- **derived_from:** docs/PRD.md

## practice-composables-take-plain-state-from-view-models

- **kind:** practice
- **title:** Give composables plain state from view models
- **path:** design/practice-composables-take-plain-state-from-view-models.md
- **tags:** ui, compose, architecture
- **relates_to:** map-ui-components-and-compose-layout-gotchas, map-catalog-pages-projects-clients-tags, map-cached-entity-picker-and-offline-creation, map-timer-today-and-this-week-totals

## practice-document-the-current-state-without-change-history

- **kind:** practice
- **title:** Document the current state without change history
- **path:** build-and-tooling/contributor-guidance/practice-document-the-current-state-without-change-history.md
- **tags:** documentation
- **relates_to:** map-design-foundation-spacing-shapes-type-brand
- **derived_from:** 06026fd6-dcbf-497f-8e2a-ff7db6ab716c:practice:0

## practice-drain-sittings-gate-on-memory-not-a-sitting-cap

- **kind:** practice
- **title:** Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap
- **path:** build-and-tooling/host-runtime/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md
- **tags:** sift, agents, memory
- **relates_to:** practice-reuse-drain-checks-by-content-and-test-configuration, map-oom-kill-ends-agent-session, practice-choose-plain-aosp-images-and-compatible-renderers, practice-let-the-runner-own-emulator-boot-and-settings

## practice-fix-detekt-findings-in-code

- **kind:** practice
- **title:** Fix detekt findings in code, and baseline only a fix that changes behaviour
- **path:** build-and-tooling/android-build/practice-fix-detekt-findings-in-code.md
- **tags:** build, gradle, kotlin
- **relates_to:** map-gradle-build-sdk-levels-kotlin-and-memory
- **derived_from:** config/detekt/detekt.yml, build-logic/convention/src/main/kotlin/Detekt.kt

## practice-gradle-no-daemon-in-parallel-worktrees

- **kind:** practice
- **title:** Build with --no-daemon in parallel worktrees and never run --stop
- **path:** build-and-tooling/host-runtime/practice-gradle-no-daemon-in-parallel-worktrees.md
- **tags:** build, gradle, agents, worktrees
- **relates_to:** map-jdk-android-sdk-and-agent-shells, practice-run-npm-install-in-every-clone-and-worktree
- **derived_from:** 19763361-1d08-4efb-a116-177429a8efa4:practice:2

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
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-use-the-pinned-test-server-and-tls-proxies, practice-keep-instrumented-credentials-out-of-process-arguments, practice-pass-class-and-package-lists-through-runner-filter-files, practice-verify-device-test-results-and-process-isolation-receipts, practice-reuse-drain-checks-by-content-and-test-configuration

## practice-isolate-session-and-background-state-with-shared-rules

- **kind:** practice
- **title:** Isolate session and background state with shared rules
- **path:** testing/instrumented-test-authoring/practice-isolate-session-and-background-state-with-shared-rules.md
- **tags:** testing, android, offline
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3

## practice-keep-developer-guidance-in-kenkeep

- **kind:** practice
- **title:** Keep developer guidance in Kenkeep
- **path:** build-and-tooling/contributor-guidance/practice-keep-developer-guidance-in-kenkeep.md
- **tags:** documentation, kenkeep
- **relates_to:** practice-run-npm-install-in-every-clone-and-worktree
- **derived_from:** e5c52983-d0d2-4752-8e90-a77f468b82a2:practice:0

## practice-keep-instrumented-credentials-out-of-process-arguments

- **kind:** practice
- **title:** Keep instrumented credentials out of process arguments
- **path:** testing/instrumented-runner/practice-keep-instrumented-credentials-out-of-process-arguments.md
- **tags:** testing, android, security
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-keep-strikethroo-plans-and-archives-out-of-git

- **kind:** practice
- **title:** Keep Strikethroo plans and archives out of Git
- **path:** build-and-tooling/contributor-guidance/practice-keep-strikethroo-plans-and-archives-out-of-git.md
- **tags:** git, strikethroo, artifacts
- **relates_to:** practice-reuse-drain-checks-by-content-and-test-configuration
- **derived_from:** 01a18edf-3a7e-7cb2-9cf8-761626a304cb:practice:0

## practice-keep-tests-that-protect-app-quality

- **kind:** practice
- **title:** Keep tests that protect app quality
- **path:** testing/practice-keep-tests-that-protect-app-quality.md
- **tags:** testing, quality
- **relates_to:** practice-isolate-all-known-failing-tests-before-full-suite-validation, practice-test-against-a-real-tokkl-server
- **derived_from:** 19763361-1d08-4efb-a116-177429a8efa4:practice:0

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
- **derived_from:** scripts/instrumented-tests.sh, scripts/emulator-discovery.py

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
- **relates_to:** practice-preserve-javascript-parsing-and-numeric-boundaries, practice-keep-upstream-golden-cases-with-source-pinned-ports, practice-match-javascript-decimal-and-percentage-formatting, practice-honor-account-clock-formatting-in-time-ports, practice-preserve-tokkl-timezone-conversion-and-database-limits, map-account-settings-source-and-account-time
- **derived_from:** docs/PRD.md, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:1

## practice-never-wait-for-server-on-start-stop

- **kind:** practice
- **title:** Never make Start or Stop wait for the server
- **path:** data-and-offline/practice-never-wait-for-server-on-start-stop.md
- **tags:** offline, network, api
- **relates_to:** map-timer-state-holder-and-sync-service, map-ordered-entry-sending-and-refusals, map-offline-entry-mutations-and-local-id-mapping
- **derived_from:** docs/PRD.md

## practice-opt-sdk-tools-out-of-usage-metrics

- **kind:** practice
- **title:** Opt the Android SDK tools out of usage metrics with ~/.androidrc
- **path:** build-and-tooling/host-runtime/practice-opt-sdk-tools-out-of-usage-metrics.md
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

## practice-pin-ci-actions-and-run-the-android-matrix

- **kind:** practice
- **title:** Pin CI actions and run the Android matrix
- **path:** release/reproducible-builds/practice-pin-ci-actions-and-run-the-android-matrix.md
- **tags:** release, ci, android
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-use-the-pinned-test-server-and-tls-proxies, practice-keep-instrumented-credentials-out-of-process-arguments, practice-pass-class-and-package-lists-through-runner-filter-files, practice-verify-device-test-results-and-process-isolation-receipts, practice-compare-clean-unsigned-release-builds
- **derived_from:** docs/PRD.md

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
- **relates_to:** map-timer-state-holder-and-sync-service
- **derived_from:** docs/PRD.md, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:5

## practice-respect-emulator-slots-and-resource-ownership

- **kind:** practice
- **title:** Respect emulator slots and resource ownership
- **path:** testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md
- **tags:** testing, emulator, memory
- **relates_to:** map-oom-kill-ends-agent-session, practice-test-against-a-real-tokkl-server, practice-use-the-instrumented-runner-and-its-device-selection
- **derived_from:** scripts/instrumented-tests.sh

## practice-reuse-drain-checks-by-content-and-test-configuration

- **kind:** practice
- **title:** Reuse drain checks by content and test configuration
- **path:** testing/practice-reuse-drain-checks-by-content-and-test-configuration.md
- **tags:** sift, testing, agents
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-use-the-pinned-test-server-and-tls-proxies, practice-keep-instrumented-credentials-out-of-process-arguments, practice-pass-class-and-package-lists-through-runner-filter-files, practice-verify-device-test-results-and-process-isolation-receipts

## practice-reuse-relays-and-throwaway-server-accounts

- **kind:** practice
- **title:** Reuse relays and throwaway server accounts
- **path:** testing/instrumented-test-authoring/practice-reuse-relays-and-throwaway-server-accounts.md
- **tags:** testing, android, api
- **relates_to:** practice-use-the-pinned-test-server-and-tls-proxies
- **derived_from:** 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3

## practice-run-coroutine-tests-in-virtual-time

- **kind:** practice
- **title:** Run JVM coroutine tests in virtual time, and never sleep
- **path:** testing/practice-run-coroutine-tests-in-virtual-time.md
- **tags:** testing, kotlin, coroutines
- **relates_to:** map-gradle-build-sdk-levels-kotlin-and-memory, practice-isolate-all-known-failing-tests-before-full-suite-validation
- **derived_from:** core/platform/src/testFixtures/java/com/github/e0ipso/tokkl/platform/MainDispatcherRule.kt

## practice-run-native-permission-probes-alone-with-a-fresh-permission

- **kind:** practice
- **title:** Run native permission probes alone with a fresh notification permission
- **path:** testing/instrumented-runner/practice-run-native-permission-probes-alone-with-a-fresh-permission.md
- **tags:** testing, android, emulator, notification
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-pass-class-and-package-lists-through-runner-filter-files

## practice-run-npm-install-in-every-clone-and-worktree

- **kind:** practice
- **title:** Run npm install in every new clone or worktree to turn on the commit hook
- **path:** build-and-tooling/contributor-guidance/practice-run-npm-install-in-every-clone-and-worktree.md
- **tags:** git, commits, tooling

## practice-screenshot-references-on-tokkl-api36

- **kind:** practice
- **title:** Record screenshot references on tokkl-api36, in the commit that changes the look
- **path:** testing/practice-screenshot-references-on-tokkl-api36.md
- **tags:** testing, android, emulator, ui
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-use-the-pinned-test-server-and-tls-proxies, practice-choose-plain-aosp-images-and-compatible-renderers, practice-account-for-emulator-recording-and-screenshot-quirks, map-design-foundation-spacing-shapes-type-brand

## practice-sign-releases-and-verify-optimized-apks

- **kind:** practice
- **title:** Sign releases and verify optimized APKs
- **path:** release/reproducible-builds/practice-sign-releases-and-verify-optimized-apks.md
- **tags:** release, fdroid, security
- **relates_to:** map-fdroid-metadata-release-artifacts, practice-own-version-from-v0-1-0-tag, practice-compare-clean-unsigned-release-builds
- **derived_from:** docs/PRD.md

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
- **relates_to:** map-api-client-shared-okhttp-serverurl-and-errors, map-timer-state-holder-and-sync-service
- **derived_from:** docs/PRD.md

## practice-test-against-a-real-tokkl-server

- **kind:** practice
- **title:** Test against a real Tokkl server
- **path:** testing/practice-test-against-a-real-tokkl-server.md
- **tags:** testing, ci
- **relates_to:** map-timer-state-holder-and-sync-service, practice-use-the-instrumented-runner-and-its-device-selection, practice-use-the-pinned-test-server-and-tls-proxies, map-ordered-entry-sending-and-refusals, map-offline-entry-mutations-and-local-id-mapping
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
- **relates_to:** practice-let-the-runner-own-emulator-boot-and-settings, practice-test-against-a-real-tokkl-server, practice-gradle-no-daemon-in-parallel-worktrees
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-use-the-pinned-test-server-and-tls-proxies

- **kind:** practice
- **title:** Use the pinned test server and TLS proxies
- **path:** testing/instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md
- **tags:** testing, android, docker, api
- **relates_to:** practice-let-the-runner-own-emulator-boot-and-settings, practice-test-against-a-real-tokkl-server
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-verify-device-test-results-and-process-isolation-receipts

- **kind:** practice
- **title:** Verify device test results and process isolation receipts
- **path:** testing/instrumented-runner/practice-verify-device-test-results-and-process-isolation-receipts.md
- **tags:** testing, android, emulator
- **relates_to:** practice-use-the-instrumented-runner-and-its-device-selection, practice-screenshot-references-on-tokkl-api36
- **derived_from:** scripts/instrumented-tests.sh, 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4

## practice-wait-on-the-apps-handlers-not-production-latches

- **kind:** practice
- **title:** Wait on the app's own handlers, never on a latch in production code
- **path:** testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md
- **tags:** testing, android
- **relates_to:** practice-isolate-session-and-background-state-with-shared-rules, map-pomodoro-phase-alarms-and-rearming
- **derived_from:** app/src/androidTest/java/com/github/e0ipso/tokkl/pomodoro/PomodoroWorkWaits.kt, feature/pomodoro/src/main/java/com/github/e0ipso/tokkl/pomodoro/PomodoroButtons.kt

## practice-wordmark-only-on-sign-in-and-splash

- **kind:** practice
- **title:** Show the wordmark only on sign-in and the launch splash, never in a header
- **path:** design/practice-wordmark-only-on-sign-in-and-splash.md
- **tags:** ui, brand, design
- **relates_to:** map-design-foundation-spacing-shapes-type-brand, map-tokkl-colour-tokens-and-font, map-timer-page-layout-and-scroll-away-header, map-timer-today-and-this-week-totals, map-timer-list-and-calendar-preference-sync, map-cached-history-days-and-grouping, map-week-calendar-layout-and-queued-dragging, map-ui-components-and-compose-layout-gotchas
- **derived_from:** docs/PRD.md
