# kenkeep Index: data-and-offline / change-queue

↑ Parent: [data-and-offline](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Offline entry mutations and local ID mapping**](map-offline-entry-mutations-and-local-id-mapping.md) to learn about: Atomic local entry changes, full pending snapshots and negative-ID remapping. #offline #cache #room #queue
- Open [**Ordered entry sending and refusals**](map-ordered-entry-sending-and-refusals.md) to learn about: Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication. #offline #api #queue
- Open [**Background queue work and empty-queue cancellation**](map-background-queue-work-and-empty-queue-cancellation.md) to learn about: Connected WorkManager delivery and serialized cancellation when the queue empties. #offline #queue #android
- Open [**Cached entity picker and offline creation**](map-cached-entity-picker-and-offline-creation.md) to learn about: Shared project, task and tag selection uses cached data and explicit offline creation limits. #offline #cache #ui
- Open [**Queued delete and short-stop Undo**](map-queued-delete-and-short-stop-undo.md) to learn about: Undo operations preserve ordering and restore stopped zero-length entries. #offline #queue #cache
- Open [**Remote running conflicts and lost create responses**](map-remote-running-conflicts-and-lost-create-responses.md) to learn about: Captured start conflict handling and duplicate prevention after uncertain POST delivery. #offline #api #queue
- Open [**Room queue migration continuity**](map-room-queue-migration-continuity.md) to learn about: Exported schemas and consecutive migrations preserve pending requests and Undo state. #offline #room #queue

## By topic

### #offline
- Open [**Background queue work and empty-queue cancellation**](map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #queue
- Open [**Timer controls and cached start suggestions**](../../screens/timer-and-entry-editing/map-timer-controls-and-cached-start-suggestions.md) — Optimistic timer actions settle input and use cached history and favorites to start entries.
- Open [**Entry form and account-time validation**](../../screens/timer-and-entry-editing/map-entry-form-and-account-time-validation.md) — Stopped and manual entry editing preserve untouched seconds and validate ranges in account time.
- Open [**History entry menu operations**](../../screens/timer-and-entry-editing/map-history-entry-menu-operations.md) — Delete with Undo, duplicate and split actions use the queue and server capabilities.
### #cache
- Open [**Cached entity picker and offline creation**](map-cached-entity-picker-and-offline-creation.md) — Shared project, task and tag selection uses cached data and explicit offline creation limits.
- Open [**Cached history days and grouping**](../../screens/timer-history-calendar/map-cached-history-days-and-grouping.md) — Account-local history groups and pages cached stopped entries with reconnect-aware retention.
- Open [**Timer Today and This week totals**](../../screens/timer-history-calendar/map-timer-today-and-this-week-totals.md) — Cached real segments contribute once using account timezone, week start and duration formatting.
### #api
- Open [**Trash capability and server operations**](../../screens/session-and-navigation/map-trash-capability-and-server-operations.md) — Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling.
- Open [**Never make Start or Stop wait for the server**](../practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Ordered entry sending and refusals**](map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #room
- Open [**Offline entry mutations and local ID mapping**](map-offline-entry-mutations-and-local-id-mapping.md) — Atomic local entry changes, full pending snapshots and negative-ID remapping.
- Open [**Room queue migration continuity**](map-room-queue-migration-continuity.md) — Exported schemas and consecutive migrations preserve pending requests and Undo state.
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](../map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
### #android
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #ui
- Open [**Timer page layout and scroll-away header**](../../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Timer List and Calendar preference sync**](../../screens/timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Tokkl favorites storage and capability**](../../screens/timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.