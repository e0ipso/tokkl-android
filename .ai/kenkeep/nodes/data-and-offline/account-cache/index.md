# kenkeep Index: data-and-offline / account-cache

↑ Parent: [data-and-offline](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Account refresh steps and preference readers**](map-account-refresh-steps-and-preference-readers.md) to learn about: One account refresh loads related data, probes capabilities, fills the cache and hands each preference to its reader. #api #cache #settings
- Open [**Account cache storage and history window**](map-account-cache-storage-and-history-window.md) to learn about: AccountCache exposes Room-backed model flows, while server refresh preserves unavailable data and account isolation. #offline #cache #room
- Open [**Capability probes and persisted feature flags**](map-capability-probes-and-persisted-feature-flags.md) to learn about: Trash and timer probes distinguish absent endpoints from temporary failures and load flags before early callers. #api #compatibility #offline

## By topic

### #api
- Open [**Never make Start or Stop wait for the server**](../practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Typed API resources and request contracts**](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Ordered entry sending and refusals**](../change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #cache
- Open [**Cached entity picker and offline creation**](../change-queue/map-cached-entity-picker-and-offline-creation.md) — Shared project, task and tag selection uses cached data and explicit offline creation limits.
- Open [**Account cache storage and history window**](map-account-cache-storage-and-history-window.md) — AccountCache exposes Room-backed model flows, while server refresh preserves unavailable data and account isolation.
- Open [**Offline entry mutations and local ID mapping**](../change-queue/map-offline-entry-mutations-and-local-id-mapping.md) — Atomic local entry changes, full pending snapshots and negative-ID remapping.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #compatibility
- Open [**Check server capabilities instead of requiring a server version**](../../api-and-security/practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
- Open [**Capability probes and persisted feature flags**](map-capability-probes-and-persisted-feature-flags.md) — Trash and timer probes distinguish absent endpoints from temporary failures and load flags before early callers.
### #room
- Open [**Offline entry mutations and local ID mapping**](../change-queue/map-offline-entry-mutations-and-local-id-mapping.md) — Atomic local entry changes, full pending snapshots and negative-ID remapping.
- Open [**Account cache storage and history window**](map-account-cache-storage-and-history-window.md) — AccountCache exposes Room-backed model flows, while server refresh preserves unavailable data and account isolation.
- Open [**Room queue migration continuity**](../change-queue/map-room-queue-migration-continuity.md) — Exported schemas and consecutive migrations preserve pending requests and Undo state.
### #settings
- Open [**Account settings loading and refresh**](../account-settings-and-time/map-account-settings-loading-and-refresh.md) — Process-wide settings loading and account refresh preserve local changes made during server reads.
- Open [**Account refresh steps and preference readers**](map-account-refresh-steps-and-preference-readers.md) — One account refresh loads related data, probes capabilities, fills the cache and hands each preference to its reader.
- Open [**Pomodoro defaults: four preferences with offline persistence**](../map-pomodoro-defaults-four-preferences-with-offline-persistence.md) — PomodoroDefaultsSetting syncs four clamped Tokkl preferences, retries after refresh and keeps Toggl values on the phone.