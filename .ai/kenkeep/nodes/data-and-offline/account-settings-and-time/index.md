# kenkeep Index: data-and-offline / account-settings-and-time

↑ Parent: [data-and-offline](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Account settings loading and refresh**](map-account-settings-loading-and-refresh.md) to learn about: Process-wide settings loading and account refresh preserve local changes made during server reads. #settings #api #offline #cache
- Open [**Account time fields and validation**](map-account-time-fields-and-validation.md) to learn about: Account settings define timezone, week start, duration and clock formatting; unresolved account zones disable time input. #time #settings #android

## By topic

### #settings
- Open [**Account settings loading and refresh**](map-account-settings-loading-and-refresh.md) — Process-wide settings loading and account refresh preserve local changes made during server reads.
- Open [**Account refresh steps and preference readers**](../account-cache/map-account-refresh-steps-and-preference-readers.md) — One account refresh loads related data, probes capabilities, fills the cache and hands each preference to its reader.
- Open [**Pomodoro defaults: four preferences with offline persistence**](../map-pomodoro-defaults-four-preferences-with-offline-persistence.md) — PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone.
### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #api
- Open [**Never make Start or Stop wait for the server**](../practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Typed API resources and request contracts**](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Ordered entry sending and refusals**](../change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #cache
- Open [**Cached entity picker and offline creation**](../change-queue/map-cached-entity-picker-and-offline-creation.md) — Shared project, task and tag selection uses cached data and explicit offline creation limits.
- Open [**Account cache storage and history window**](../account-cache/map-account-cache-storage-and-history-window.md) — AccountCache exposes Room-backed model flows, while server refresh preserves unavailable data and account isolation.
- Open [**Offline entry mutations and local ID mapping**](../change-queue/map-offline-entry-mutations-and-local-id-mapping.md) — Atomic local entry changes, full pending snapshots and negative-ID remapping.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #time
- Open [**Honor account clock formatting in time ports**](../typescript-time-ports/practice-honor-account-clock-formatting-in-time-ports.md) — formatTime matches the account 12-hour or 24-hour preference and server value semantics.
- Open [**Match JavaScript decimal and percentage formatting**](../typescript-time-ports/practice-match-javascript-decimal-and-percentage-formatting.md) — Binary toFixed and shortest-decimal Intl formatting require separate helpers.
- Open [**Preserve JavaScript parsing and numeric boundaries**](../typescript-time-ports/practice-preserve-javascript-parsing-and-numeric-boundaries.md) — Match JavaScript whitespace and rounding while representing invalid numeric results safely.