# kenkeep Index: screens / catalog-pages

↑ Parent: [screens](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Catalog form restoration and project tasks**](map-catalog-form-restoration-and-project-tasks.md) to learn about: SavedStateHandle restores catalog drafts, and the project form owns its task list and online task actions. #ui #catalog #navigation
- Open [**Catalog page loading and lifecycle**](map-catalog-page-loading-and-lifecycle.md) to learn about: PageLoads refreshes open catalog pages while retaining rows and forms, and avoids rotation requests. #ui #catalog #lifecycle
- Open [**Catalog repositories and server mutations**](map-catalog-repositories-and-server-mutations.md) to learn about: Repositories map server rows, ServerChanges finishes accepted changes and cache refreshes, and stale loads cannot overwrite mutations. #api #catalog #offline

## By topic

### #catalog
- Open [**Catalog form restoration and project tasks**](map-catalog-form-restoration-and-project-tasks.md) — SavedStateHandle restores catalog drafts, and the project form owns its task list and online task actions.
- Open [**Catalog page loading and lifecycle**](map-catalog-page-loading-and-lifecycle.md) — PageLoads refreshes open catalog pages while retaining rows and forms, and avoids rotation requests.
- Open [**Catalog repositories and server mutations**](map-catalog-repositories-and-server-mutations.md) — Repositories map server rows, ServerChanges finishes accepted changes and cache refreshes, and stale loads cannot overwrite mutations.
### #ui
- Open [**Timer page layout and scroll-away header**](../timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Signed-in navigation chrome and adaptive windows**](../session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](../session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.
### #api
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Typed API resources and request contracts**](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #lifecycle
- Open [**Catalog page loading and lifecycle**](map-catalog-page-loading-and-lifecycle.md) — PageLoads refreshes open catalog pages while retaining rows and forms, and avoids rotation requests.
- Open [**Split process restart acceptance into orchestrated steps**](../../testing/instrumented-test-authoring/practice-split-process-restart-acceptance-into-orchestrated-steps.md) — Instrumentation cannot survive killing its own process, so restart assertions span isolated steps.
### #navigation
- Open [**Signed-in navigation chrome and adaptive windows**](../session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](../session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.
- Open [**Navigation route serialization and feature registration**](../session-and-navigation/navigation-state/map-navigation-route-serialization-and-feature-registration.md) — Feature serializers preserve route identities and numeric IDs, while navigation contracts isolate feature screens and entries.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.