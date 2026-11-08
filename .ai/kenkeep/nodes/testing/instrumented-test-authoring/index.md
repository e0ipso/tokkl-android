# kenkeep Index: testing / instrumented-test-authoring

↑ Parent: [testing](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Isolate session and background state with shared rules**](practice-isolate-session-and-background-state-with-shared-rules.md) to learn about: SessionRule retires prior account observers and work before composing the next test. #testing #android #offline
- Open [**Assert text height and dialog bounds at large fonts**](practice-assert-text-height-and-dialog-bounds-at-large-fonts.md) to learn about: Compose text and dialog acceptance must detect real wrapping, overflow and overlap. #testing #android #accessibility
- Open [**Match production windows and real touch hit testing**](practice-match-production-windows-and-real-touch-hit-testing.md) to learn about: Edge-to-edge, keyboard insets, stable controls and visible press points prevent false UI results. #testing #android #ui
- Open [**Reuse relays and throwaway server accounts**](practice-reuse-relays-and-throwaway-server-accounts.md) to learn about: Shared relay helpers and a bounded failed-password budget keep real-server tests isolated. #testing #android #api
- Open [**Split process restart acceptance into orchestrated steps**](practice-split-process-restart-acceptance-into-orchestrated-steps.md) to learn about: Instrumentation cannot survive killing its own process, so restart assertions span isolated steps. #testing #android #lifecycle
- Open [**Use runner annotations and owned settings across APIs**](practice-use-runner-annotations-and-owned-settings-across-apis.md) to learn about: SdkSuppress and test rules make API-specific coverage and device settings explicit. #testing #android #emulator
- Open [**Wait on the app's own handlers, never on a latch in production code**](practice-wait-on-the-apps-handlers-not-production-latches.md) to learn about: Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches. #testing #android

## Components (what exists)
_None yet._

## By topic

### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #testing
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #accessibility
- Open [**Shared timer and entry fields**](../../screens/timer-and-entry-editing/map-shared-timer-and-entry-fields.md) — Responsive field rows, picker dialogs and accessibility are shared between the timer and entry form.
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
### #api
- Open [**Trash capability and server operations**](../../screens/session-and-navigation/map-trash-capability-and-server-operations.md) — Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling.
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Match Tokkl web app behavior in the account timezone**](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #emulator
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #lifecycle
- Open [**Split process restart acceptance into orchestrated steps**](practice-split-process-restart-acceptance-into-orchestrated-steps.md) — Instrumentation cannot survive killing its own process, so restart assertions span isolated steps.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #ui
- Open [**Timer page layout and scroll-away header**](../../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Tokkl favorites storage and capability**](../../screens/timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.
- Open [**Timer List and Calendar preference sync**](../../screens/timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.