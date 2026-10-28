# kenkeep Index: screens / timer-history-calendar

↑ Parent: [screens](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Cached history days and grouping**](map-cached-history-days-and-grouping.md) to learn about: Account-local history groups and pages cached stopped entries with reconnect-aware retention. #ui #timer #cache #time
- Open [**Timer page layout and scroll-away header**](map-timer-page-layout-and-scroll-away-header.md) to learn about: Bounded Timer content and the shared scroll-away header adapt to short and wide windows. #ui #timer #android
- Open [**Timer List and Calendar preference sync**](map-timer-list-and-calendar-preference-sync.md) to learn about: View selection persists locally and writes the supported server preference without blocking navigation. #ui #timer #offline
- Open [**Timer Today and This week totals**](map-timer-today-and-this-week-totals.md) to learn about: Cached real segments contribute once using account timezone, week start and duration formatting. #ui #timer #cache #time
- Open [**Week calendar layout and queued dragging**](map-week-calendar-layout-and-queued-dragging.md) to learn about: Adaptive week blocks, accessible navigation and snap-grid dragging operate on cached entries. #ui #timer #cache #accessibility

## By topic

### #timer
- Open [**Timer List and Calendar preference sync**](map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Timer controls and cached start suggestions**](../timer-and-entry-editing/map-timer-controls-and-cached-start-suggestions.md) — Optimistic timer actions settle input and use cached history and favorites to start entries.
- Open [**Entry form and account-time validation**](../timer-and-entry-editing/map-entry-form-and-account-time-validation.md) — Stopped and manual entry editing preserve untouched seconds and validate ranges in account time.
### #ui
- Open [**Timer page layout and scroll-away header**](map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Timer List and Calendar preference sync**](map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Tokkl favorites storage and capability**](../timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.
### #cache
- Open [**Cached entity picker and offline creation**](../../data-and-offline/change-queue/map-cached-entity-picker-and-offline-creation.md) — Shared project, task and tag selection uses cached data and explicit offline creation limits.
- Open [**Cached history days and grouping**](map-cached-history-days-and-grouping.md) — Account-local history groups and pages cached stopped entries with reconnect-aware retention.
- Open [**Timer Today and This week totals**](map-timer-today-and-this-week-totals.md) — Cached real segments contribute once using account timezone, week start and duration formatting.
### #time
- Open [**Honor account clock formatting in time ports**](../../data-and-offline/typescript-time-ports/practice-honor-account-clock-formatting-in-time-ports.md) — FormatTime matches the account 12-hour or 24-hour preference and server value semantics.
- Open [**Match JavaScript decimal and percentage formatting**](../../data-and-offline/typescript-time-ports/practice-match-javascript-decimal-and-percentage-formatting.md) — Binary toFixed and shortest-decimal Intl formatting require separate helpers.
- Open [**Preserve JavaScript parsing and numeric boundaries**](../../data-and-offline/typescript-time-ports/practice-preserve-javascript-parsing-and-numeric-boundaries.md) — Match JavaScript whitespace and rounding while representing invalid numeric results safely.
### #accessibility
- Open [**Shared timer and entry fields**](../timer-and-entry-editing/map-shared-timer-and-entry-fields.md) — Responsive field rows, picker dialogs and accessibility are shared between the timer and entry form.
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
### #android
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.