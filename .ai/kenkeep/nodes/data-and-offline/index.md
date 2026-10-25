# kenkeep Index: data-and-offline

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`change-queue/`](change-queue/index.md) for more information on offline entry mutations, delivery, recovery and picker data; read before changing the entry queue or its sender.
- Load [`typescript-time-ports/`](typescript-time-ports/index.md) for more information on JavaScript-compatible time and formatting ports; read before porting Tokkl parsing, rounding or timezone behavior.

## Conventions (how we build)
- Open [**Match Tokkl web app behavior in the account timezone**](practice-match-web-app-behavior-account-timezone.md) to learn about: Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me. #api #ui #time
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) to learn about: Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine. #offline #network #api

## Components (what exists)
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) to learn about: One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline. #offline #cache #room #api #compatibility
- Open [**Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest**](map-account-settings-source-and-account-time.md) to learn about: Sign-in and each refresh store timezone, week start, resolution and duration format. AccountTime gives zone and dates, never the device's. #time #api #android #settings
- Open [**Running timer notification follows the cache's running entry**](map-running-timer-notification-follows-the-cache.md) to learn about: notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache, with no foreground service. #notification #offline #android
- Open [**Pomodoro defaults: four preferences with offline persistence**](map-pomodoro-defaults-four-preferences-with-offline-persistence.md) to learn about: PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone. #settings #offline #api #pomodoro
- Open [**Pomodoro phase alarms and rearming**](map-pomodoro-phase-alarms-and-rearming.md) to learn about: The stored phase owns one exact alarm; rearming never advances it, including after a missed end. #pomodoro #alarms #offline
- Open [**RunningCheck: guarded background current-entry refresh**](map-runningcheck-guarded-background-current-entry-refresh.md) to learn about: One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out. #offline #cache #android #battery

## By topic

### #offline
- Open [**Background queue work and empty-queue cancellation**](change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #api
- Open [**Trash capability and server operations**](../screens/session-and-navigation/map-trash-capability-and-server-operations.md) — Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling.
- Open [**Never make Start or Stop wait for the server**](practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Ordered entry sending and refusals**](change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #android
- Open [**Let the runner own emulator boot and settings**](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #cache
- Open [**Cached entity picker and offline creation**](change-queue/map-cached-entity-picker-and-offline-creation.md) — Shared project, task and tag selection uses cached data and explicit offline creation limits.
- Open [**Cached history days and grouping**](../screens/timer-history-calendar/map-cached-history-days-and-grouping.md) — Account-local history groups and pages cached stopped entries with reconnect-aware retention.
- Open [**Timer Today and This week totals**](../screens/timer-history-calendar/map-timer-today-and-this-week-totals.md) — Cached real segments contribute once using account timezone, week start and duration formatting.
### #pomodoro
- Open [**Pomodoro defaults: four preferences with offline persistence**](map-pomodoro-defaults-four-preferences-with-offline-persistence.md) — PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone.
- Open [**Pomodoro phase alarms and rearming**](map-pomodoro-phase-alarms-and-rearming.md) — The stored phase owns one exact alarm; rearming never advances it, including after a missed end.
### #settings
- Open [**Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest**](map-account-settings-source-and-account-time.md) — Sign-in and each refresh store timezone, week start, resolution and duration format. AccountTime gives zone and dates, never the device's.
- Open [**Pomodoro defaults: four preferences with offline persistence**](map-pomodoro-defaults-four-preferences-with-offline-persistence.md) — PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone.
### #time
- Open [**Honor account clock formatting in time ports**](typescript-time-ports/practice-honor-account-clock-formatting-in-time-ports.md) — FormatTime matches the account 12-hour or 24-hour preference and server value semantics.
- Open [**Match JavaScript decimal and percentage formatting**](typescript-time-ports/practice-match-javascript-decimal-and-percentage-formatting.md) — Binary toFixed and shortest-decimal Intl formatting require separate helpers.
- Open [**Preserve JavaScript parsing and numeric boundaries**](typescript-time-ports/practice-preserve-javascript-parsing-and-numeric-boundaries.md) — Match JavaScript whitespace and rounding while representing invalid numeric results safely.
### #alarms
- Open [**Pomodoro phase alarms and rearming**](map-pomodoro-phase-alarms-and-rearming.md) — The stored phase owns one exact alarm; rearming never advances it, including after a missed end.
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
### #room
- Open [**Offline entry mutations and local ID mapping**](change-queue/map-offline-entry-mutations-and-local-id-mapping.md) — Atomic local entry changes, full pending snapshots and negative-ID remapping.
- Open [**Room queue migration continuity**](change-queue/map-room-queue-migration-continuity.md) — Exported schemas and consecutive migrations preserve pending requests and Undo state.
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
### #ui
- Open [**Timer page layout and scroll-away header**](../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Timer List and Calendar preference sync**](../screens/timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Tokkl favorites storage and capability**](../screens/timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.