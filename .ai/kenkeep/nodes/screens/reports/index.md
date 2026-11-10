# kenkeep Index: screens / reports

↑ Parent: [screens](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Report APIs rounding and pagination**](map-report-apis-rounding-and-pagination.md) to learn about: Summary sums detailed search, Detailed follows server pagination, and Weekly reads rounded v3 rows. #reports #api #toggl
- Open [**Report ranges and cached filters**](map-report-ranges-and-cached-filters.md) to learn about: Shared report ranges follow account time, and cached project, client and tag filters apply together with capability-gated negation. #reports #ui #toggl
- Open [**Report state lifetimes and widget navigation**](map-report-state-lifetimes-and-widget-navigation.md) to learn about: Route and session stores retain different report choices, page lifecycle events refresh data, and widget taps request Today before collection. #reports #ui #navigation

## By topic

### #reports
- Open [**Report ranges and cached filters**](map-report-ranges-and-cached-filters.md) — Shared report ranges follow account time, and cached project, client and tag filters apply together with capability-gated negation.
- Open [**Report APIs rounding and pagination**](map-report-apis-rounding-and-pagination.md) — Summary sums detailed search, Detailed follows server pagination, and Weekly reads rounded v3 rows.
- Open [**Report state lifetimes and widget navigation**](map-report-state-lifetimes-and-widget-navigation.md) — Route and session stores retain different report choices, page lifecycle events refresh data, and widget taps request Today before collection.
### #toggl
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Report APIs rounding and pagination**](map-report-apis-rounding-and-pagination.md) — Summary sums detailed search, Detailed follows server pagination, and Weekly reads rounded v3 rows.
- Open [**Check server capabilities instead of requiring a server version**](../../api-and-security/practice-check-capabilities-not-server-version.md) — Probe optional Tokkl features by capability; snapping follows the latest supported contract, with no version selector.
### #ui
- Open [**Timer page layout and scroll-away header**](../timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Signed-in navigation chrome and adaptive windows**](../session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](../session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.
### #api
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Typed API resources and request contracts**](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #navigation
- Open [**Signed-in navigation chrome and adaptive windows**](../session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](../session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.
- Open [**Navigation route serialization and feature registration**](../session-and-navigation/navigation-state/map-navigation-route-serialization-and-feature-registration.md) — Feature serializers preserve route identities and numeric IDs, while navigation contracts isolate feature screens and entries.