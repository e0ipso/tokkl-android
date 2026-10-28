# kenkeep Index: screens / timer-and-entry-editing

↑ Parent: [screens](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Timer controls and cached start suggestions**](map-timer-controls-and-cached-start-suggestions.md) to learn about: Optimistic timer actions settle input and use cached history and favorites to start entries. #ui #timer #offline #queue
- Open [**Shared timer and entry fields**](map-shared-timer-and-entry-fields.md) to learn about: Responsive field rows, picker dialogs and accessibility are shared between the timer and entry form. #ui #timer #accessibility
- Open [**Entry form and account-time validation**](map-entry-form-and-account-time-validation.md) to learn about: Stopped and manual entry editing preserve untouched seconds and validate ranges in account time. #ui #timer #offline #queue
- Open [**History entry menu operations**](map-history-entry-menu-operations.md) to learn about: Delete with Undo, duplicate and split actions use the queue and server capabilities. #ui #timer #offline #queue
- Open [**Tokkl favorites storage and capability**](map-tokkl-favorites-storage-and-capability.md) to learn about: Favorite starts share cached fields while preference writes use supported server capabilities. #ui #timer #api

## By topic

### #timer
- Open [**Timer List and Calendar preference sync**](../timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Timer controls and cached start suggestions**](map-timer-controls-and-cached-start-suggestions.md) — Optimistic timer actions settle input and use cached history and favorites to start entries.
- Open [**Entry form and account-time validation**](map-entry-form-and-account-time-validation.md) — Stopped and manual entry editing preserve untouched seconds and validate ranges in account time.
### #ui
- Open [**Timer page layout and scroll-away header**](../timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Timer List and Calendar preference sync**](../timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Tokkl favorites storage and capability**](map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #queue
- Open [**Timer controls and cached start suggestions**](map-timer-controls-and-cached-start-suggestions.md) — Optimistic timer actions settle input and use cached history and favorites to start entries.
- Open [**Entry form and account-time validation**](map-entry-form-and-account-time-validation.md) — Stopped and manual entry editing preserve untouched seconds and validate ranges in account time.
- Open [**History entry menu operations**](map-history-entry-menu-operations.md) — Delete with Undo, duplicate and split actions use the queue and server capabilities.
### #accessibility
- Open [**Shared timer and entry fields**](map-shared-timer-and-entry-fields.md) — Responsive field rows, picker dialogs and accessibility are shared between the timer and entry form.
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
### #api
- Open [**Trash capability and server operations**](../session-and-navigation/map-trash-capability-and-server-operations.md) — Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling.
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.