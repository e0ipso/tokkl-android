# kenkeep Index: data-and-offline / timer-state-and-sync

↑ Parent: [data-and-offline](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Account timer state lifetime**](map-account-timer-state-lifetime.md) to learn about: AccountTimerStates owns intake, read-only timer state and retirement across sign-in and sign-out. #pomodoro #sync #offline
- Open [**Timer state acceptance and persistence**](map-timer-state-acceptance-and-persistence.md) to learn about: Revision acceptance, clock offsets, persisted state and cache reconciliation share one account holder. #pomodoro #sync #offline
- Open [**Online timer actions and native control failures**](map-online-timer-actions-and-native-control-failures.md) to learn about: Session and pause controls use captured account revisions, single server attempts and shared failure notices. #pomodoro #network #android #offline
- Open [**Timer event stream and service lifetime**](map-timer-event-stream-and-service-lifetime.md) to learn about: A specialUse foreground service owns the timer stream, watchdog, reconnects and account termination handling. #sync #network #android #battery

## By topic

### #offline
- Open [**Background queue work and empty-queue cancellation**](../change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #pomodoro
- Open [**Account timer state lifetime**](map-account-timer-state-lifetime.md) — AccountTimerStates owns intake, read-only timer state and retirement across sign-in and sign-out.
- Open [**Timer state acceptance and persistence**](map-timer-state-acceptance-and-persistence.md) — Revision acceptance, clock offsets, persisted state and cache reconciliation share one account holder.
- Open [**Online timer actions and native control failures**](map-online-timer-actions-and-native-control-failures.md) — Session and pause controls use captured account revisions, single server attempts and shared failure notices.
### #sync
- Open [**Timer state acceptance and persistence**](map-timer-state-acceptance-and-persistence.md) — Revision acceptance, clock offsets, persisted state and cache reconciliation share one account holder.
- Open [**Account timer state lifetime**](map-account-timer-state-lifetime.md) — AccountTimerStates owns intake, read-only timer state and retirement across sign-in and sign-out.
- Open [**Pomodoro phase alarms and rearming**](../map-pomodoro-phase-alarms-and-rearming.md) — One exact alarm per server phase, set from its planned end and the clock offset. A state from any device silences it; rearming never advances a phase.
### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #network
- Open [**Typed API resources and request contracts**](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../../api-and-security/map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Never make Start or Stop wait for the server**](../practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
### #battery
- Open [**Home screen widget: cache state and a native ticking clock**](../../screens/map-home-screen-widget-cache-state-and-a-native-ticking-clock.md) — Glance redraws on cache changes; a launcher Chronometer ticks without periodic app updates.
- Open [**RunningCheck: guarded background current-entry refresh**](../map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Pomodoro home screen widget**](../../screens/map-pomodoro-home-screen-widget.md) — A separate provider shows the server session and uses native countdowns and online-only session actions.