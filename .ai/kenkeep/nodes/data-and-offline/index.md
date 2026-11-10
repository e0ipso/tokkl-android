# kenkeep Index: data-and-offline

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`account-cache/`](account-cache/index.md) for more information on the Room cache ownership, capability probes and account refresh ordering; read before changing cached data, optional server features or refresh steps.
- Load [`account-settings-and-time/`](account-settings-and-time/index.md) for more information on account time fields, timezone validation and settings refresh; read before changing time formatting, account settings or cache refresh ordering.
- Load [`change-queue/`](change-queue/index.md) for more information on offline entry mutations, delivery, recovery and picker data; read before changing the entry queue or its sender.
- Load [`timer-state-and-sync/`](timer-state-and-sync/index.md) for more information on server timer acceptance, account lifetime, online actions and event-stream services; read before changing Pomodoro, pause, native timer controls or timer synchronization.
- Load [`typescript-time-ports/`](typescript-time-ports/index.md) for more information on JavaScript-compatible time and formatting ports; read before porting Tokkl parsing, rounding or timezone behavior.

## Conventions (how we build)
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) to learn about: Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me. #api #ui #time
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) to learn about: Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine. #offline #network #api

## Components (what exists)
- Open [**Pomodoro phase alarms and rearming**](map-pomodoro-phase-alarms-and-rearming.md) to learn about: One exact alarm per server phase, set from its planned end and the clock offset. A state from any device silences it; rearming never advances a phase. #pomodoro #alarms #sync
- Open [**Running timer notification follows the cache's running entry**](map-running-timer-notification-follows-the-cache.md) to learn about: notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache and the held timer state. Stop works offline, Pause needs the server. #notification #offline #android
- Open [**RunningCheck: guarded background current-entry refresh**](map-runningcheck-guarded-background-current-entry-refresh.md) to learn about: One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out. #offline #cache #android #battery
- Open [**core:model: what the screens read, with typed ids**](map-core-model-and-typed-ids.md) to learn about: TimeEntry, Project and the rest with value class ids; Room rows stay in core:cache and API wire types stay below the screens. #cache #room #ui #api
- Open [**Pomodoro defaults: four preferences with offline persistence**](map-pomodoro-defaults-four-preferences-with-offline-persistence.md) to learn about: PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone. #settings #offline #api #pomodoro

## By topic

### #api
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Typed API resources and request contracts**](../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Ordered entry sending and refusals**](change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #offline
- Open [**Background queue work and empty-queue cancellation**](change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #cache
- Open [**Cached entity picker and offline creation**](change-queue/map-cached-entity-picker-and-offline-creation.md) — Shared project, task and tag selection uses cached data and explicit offline creation limits.
- Open [**Account cache storage and history window**](account-cache/map-account-cache-storage-and-history-window.md) — AccountCache exposes Room-backed model flows, while server refresh preserves unavailable data and account isolation.
- Open [**Offline entry mutations and local ID mapping**](change-queue/map-offline-entry-mutations-and-local-id-mapping.md) — Atomic local entry changes, full pending snapshots and negative-ID remapping.
### #pomodoro
- Open [**Account timer state lifetime**](timer-state-and-sync/map-account-timer-state-lifetime.md) — AccountTimerStates owns intake, read-only timer state and retirement across sign-in and sign-out.
- Open [**Timer state acceptance and persistence**](timer-state-and-sync/map-timer-state-acceptance-and-persistence.md) — Revision acceptance, clock offsets, persisted state and cache reconciliation share one account holder.
- Open [**Online timer actions and native control failures**](timer-state-and-sync/map-online-timer-actions-and-native-control-failures.md) — Session and pause controls use captured account revisions, single server attempts and shared failure notices.
### #ui
- Open [**Timer page layout and scroll-away header**](../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Signed-in navigation chrome and adaptive windows**](../screens/session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](../screens/session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.
### #alarms
- Open [**Pomodoro phase alarms and rearming**](map-pomodoro-phase-alarms-and-rearming.md) — One exact alarm per server phase, set from its planned end and the clock offset. A state from any device silences it; rearming never advances a phase.
### #battery
- Open [**Home screen widget: cache state and a native ticking clock**](../screens/map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**RunningCheck: guarded background current-entry refresh**](map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Pomodoro home screen widget**](../screens/map-pomodoro-home-screen-widget.md) — A separate provider shows the server session and uses native countdowns and online-only session actions.
### #network
- Open [**Typed API resources and request contracts**](../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
### #notification
- Open [**Running timer notification follows the cache's running entry**](map-running-timer-notification-follows-the-cache.md) — notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache and the held timer state. Stop works offline, Pause needs the server.
- Open [**Run native permission probes alone with a fresh notification permission**](../testing/instrumented-runner/practice-run-native-permission-probes-alone-with-a-fresh-permission.md) — TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION=1 resets POST_NOTIFICATIONS before one probe method starts, on an API 33+ emulator the script boots.
### #room
- Open [**Offline entry mutations and local ID mapping**](change-queue/map-offline-entry-mutations-and-local-id-mapping.md) — Atomic local entry changes, full pending snapshots and negative-ID remapping.
- Open [**Account cache storage and history window**](account-cache/map-account-cache-storage-and-history-window.md) — AccountCache exposes Room-backed model flows, while server refresh preserves unavailable data and account isolation.
- Open [**Room queue migration continuity**](change-queue/map-room-queue-migration-continuity.md) — Exported schemas and consecutive migrations preserve pending requests and Undo state.
### #settings
- Open [**Account settings loading and refresh**](account-settings-and-time/map-account-settings-loading-and-refresh.md) — Process-wide settings loading and account refresh preserve local changes made during server reads.
- Open [**Account refresh steps and preference readers**](account-cache/map-account-refresh-steps-and-preference-readers.md) — One account refresh loads related data, probes capabilities, fills the cache and hands each preference to its reader.
- Open [**Pomodoro defaults: four preferences with offline persistence**](map-pomodoro-defaults-four-preferences-with-offline-persistence.md) — PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone.
### #sync
- Open [**Timer state acceptance and persistence**](timer-state-and-sync/map-timer-state-acceptance-and-persistence.md) — Revision acceptance, clock offsets, persisted state and cache reconciliation share one account holder.
- Open [**Account timer state lifetime**](timer-state-and-sync/map-account-timer-state-lifetime.md) — AccountTimerStates owns intake, read-only timer state and retirement across sign-in and sign-out.
- Open [**Pomodoro phase alarms and rearming**](map-pomodoro-phase-alarms-and-rearming.md) — One exact alarm per server phase, set from its planned end and the clock offset. A state from any device silences it; rearming never advances a phase.
### #time
- Open [**Honor account clock formatting in time ports**](typescript-time-ports/practice-honor-account-clock-formatting-in-time-ports.md) — formatTime matches the account 12-hour or 24-hour preference and server value semantics.
- Open [**Match JavaScript decimal and percentage formatting**](typescript-time-ports/practice-match-javascript-decimal-and-percentage-formatting.md) — Binary toFixed and shortest-decimal Intl formatting require separate helpers.
- Open [**Preserve JavaScript parsing and numeric boundaries**](typescript-time-ports/practice-preserve-javascript-parsing-and-numeric-boundaries.md) — Match JavaScript whitespace and rounding while representing invalid numeric results safely.