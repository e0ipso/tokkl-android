# kenkeep Index: screens / session-and-navigation

↑ Parent: [screens](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Saveable navigation stack and session guard**](map-saveable-navigation-stack-and-session-guard.md) to learn about: Destination names survive restoration, while the host checks the session and presents trust prompts. #android #ui #navigation
- Open [**Sign-out ordering and account cleanup**](map-sign-out-ordering-and-account-cleanup.md) to learn about: Warn about unsent work and retire account tasks before clearing local session data. #android #credentials #offline
- Open [**Trash capability and server operations**](map-trash-capability-and-server-operations.md) to learn about: Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling. #android #ui #api
- Open [**Session stores and pre-frame preferences**](map-session-stores-and-pre-frame-preferences.md) to learn about: Private session stores separate credentials, account settings, trust and device preferences; their holders load at process start and write through suspend calls. #android #credentials #storage
- Open [**Sign-in request and credential admission**](map-sign-in-request-and-credential-admission.md) to learn about: Validate configured-server credentials and required account settings before storing a session. #android #ui #credentials
- Open [**Signed-in navigation chrome and adaptive windows**](map-signed-in-navigation-chrome-and-adaptive-windows.md) to learn about: Bar, rail, back-stack and width rules control signed-in page layout. #android #ui #navigation

## By topic

### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #ui
- Open [**Timer page layout and scroll-away header**](../timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Tokkl favorites storage and capability**](../timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.
- Open [**Timer List and Calendar preference sync**](../timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
### #credentials
- Open [**Sign-out ordering and account cleanup**](map-sign-out-ordering-and-account-cleanup.md) — Warn about unsent work and retire account tasks before clearing local session data.
- Open [**Session stores and pre-frame preferences**](map-session-stores-and-pre-frame-preferences.md) — Private session stores separate credentials, account settings, trust and device preferences; their holders load at process start and write through suspend calls.
- Open [**Sign-in request and credential admission**](map-sign-in-request-and-credential-admission.md) — Validate configured-server credentials and required account settings before storing a session.
### #navigation
- Open [**Saveable navigation stack and session guard**](map-saveable-navigation-stack-and-session-guard.md) — Destination names survive restoration, while the host checks the session and presents trust prompts.
- Open [**Signed-in navigation chrome and adaptive windows**](map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
### #api
- Open [**Trash capability and server operations**](map-trash-capability-and-server-operations.md) — Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling.
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Match Tokkl web app behavior in the account timezone**](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #storage
- Open [**Session stores and pre-frame preferences**](map-session-stores-and-pre-frame-preferences.md) — Private session stores separate credentials, account settings, trust and device preferences; their holders load at process start and write through suspend calls.