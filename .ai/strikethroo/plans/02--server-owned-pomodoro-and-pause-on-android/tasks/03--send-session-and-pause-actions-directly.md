---
id: 3
group: "timer-state"
dependencies: [2]
status: "completed"
created: 2026-10-28
skills:
  - kotlin
  - okhttp
complexity_score: 5
execution_profile: "complex-architecture"
---
# Send session and pause actions directly

## Objective

Provide one online action client for Pause, Resume, Discard, session Start, Next phase and End session.

## Skills Required

Kotlin API client logic and OkHttp request/error handling.

## Acceptance Criteria

- [x] The six server actions use the verified app routes, account Basic authentication, configured URL prefix, shared HTTP client and trust handling. Session Start sends the form fields, four lengths and the existing timer `created_with` value.
- [x] Resume, Discard, Next phase and End send the revision of the state the action represents. The API accepts the displayed revision from native controls, so an old notification cannot silently act on a newer phase. Pause and session Start follow their release-defined body contracts.
- [x] A 2xx state enters task 2's holder. A valid 409 state also enters the holder and reports exactly `Another device changed the timer first.` The revision guard still rejects an older response.
- [x] A connection failure reports exactly `Can't reach the server. Try again when you're back online.` It changes no timer/session/pause state and creates no queued request. Other errors and auth/trust failures use existing application paths.
- [x] Ordinary v9 Start, Stop and running-entry edits retain their queue path. Online actions never invoke local phase rules or automatically retry a mutation after uncertain delivery.
- [x] Add `TimerActionsTest` for request bodies/revisions, state adoption on 2xx/409, offline immutability and queue independence. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.timerstate.TimerActionsTest' --tests 'com.github.e0ipso.tokkl.api.SingleHttpClientTest'`; expect nonzero tests and zero failures.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

`TogglApiClient` currently turns unsuccessful statuses into `ApiError`. Preserve the 409 response body for these actions through a narrow shared-client extension or timer API adapter. Do not create a second OkHttpClient, bypass `ConfiguredServerGuard`, weaken pinning, or add a dependency.

## Input Dependencies

Hard dependency on task 2's state types and ingestion API. No soft dependencies.

## Output Artifacts

Timer GET/action adapter, typed success/conflict/offline results, composition-root injection and focused action-contract checks for every UI and native surface.

## Implementation Notes

<details>
<summary>Direct action implementation</summary>

1. Read `TogglApiClient`, `SharedHttpClient`, `ServerUrl`, `ApiCredentials`, `ApiError` and existing re-sign-in handling. Keep all network work off the main thread.
2. Implement the verified GET plus six POST actions using the existing request machinery. Preserve unsuccessful status/body information long enough to decode a 409 `TimerState` before reporting the conflict. A malformed body remains the existing invalid-response failure.
3. Accept a captured revision for revisioned actions. Screen callers use the held state they displayed; notification/widget/tile intents carry that displayed server revision. Do not fetch a fresh revision just to make a stale action succeed.
4. Treat mutations as one-shot after they may have reached the server, following the existing `resend = false` convention. A later GET or SSE can reveal an action whose response was lost; no offline retry item is stored.
5. Publish successful and refused states through the holder, then return a result the caller can show through its existing message path. Do not optimistically pause, advance, silence or clear the held session on a network error.
6. Keep local queue operations outside this adapter. Add a few boundary tests of request construction and observable state/error outcomes; real-server cross-device tests belong to task 11. Run focused JVM checks in the host's OOM-safe scope.

</details>

## Execution Result

Completed on 2026-10-28 against the Tokkl 1.10.0 contract (`v1.10.0:packages/api/src/routes/timer.ts`, `services/timerState.ts`, `http/context.ts` `readJson`, `app.ts` error handler and `packages/web/src/api/timerState.ts`).

- Shared client. `TogglApiClient.sendRefusable` is the narrow extension. It sends through `execute`, so it uses the shared client, `ConfiguredServerGuard`, pinning, the trust listener and the account's `Authorization` header. It always sends with `resend = false` (`SharedHttpClient.sendingOnce`). A 2xx body decodes as before. A 409 body that decodes as the expected type is a refusal with that value. A 409 whose body is a JSON string stays `UnexpectedStatus(409, reason)`, and one whose body is neither is `InvalidResponse`. Every other status keeps the existing mapping. `ApiError` has no new variant, so no existing `when` changes.
- Adapter. `timerstate/TimerActions.kt` has `read()` (`GET /api/app/timer`), `pause()`, `resume(revision)`, `discard(revision)`, `startSession(workspaceId, fields, lengths)`, `nextPhase(revision)` and `endSession(revision)`, all under `ServerUrl.app`, so a configured path prefix carries through. The four revisioned actions send `{"revision": N}` with the revision the caller passes, never the holder's. Pause sends `{}`. Start sends `workspace_id`, `created_with` (`CREATED_WITH`, the value the queue's v9 creates use), `description`, `project_id`, `task_id`, `tags` by name, `billable` and the four lengths. `SessionLengths` refuses lengths outside 1–180, 1–60, 1–60 and 1–12, the ranges `readSessionConfig` checks, so the start form keeps them after task 10 deletes `time/Pomodoro.kt`.
- Answers. Each call takes the account's `TimerStateSource` once, calls `begin()` before the request and `accept(read, state)` for a 2xx or a 409 state, so the revision guard and the cache reconciliation of task 02 apply. Results are `Done`, `Refused` (message `R.string.timer_action_refused`, "Another device changed the timer first."), `Offline` for `ApiError.Network` (message `R.string.timer_action_offline`, "Can't reach the server. Try again when you're back online."), and `Failed(error)` for everything else, which surfaces map through their existing paths such as `toServerProblem()`. Signed out is `Failed(AuthFailed)`, as in `TrashModel` and `CatalogModel`. A failure touches neither the holder nor the cache and stores nothing. Calls run in the injected `io` context.
- Composition root. `TokklApplication.timerActions` builds it from `Session(this)::client`, `{ timerState }` and `Dispatchers.IO`. No surface uses it yet; tasks 06 to 09 wire them.
- Queue and phase rules. Nothing under `cache/`, `time/` or `pomodoro/` changed (`git diff --stat` on those paths is empty). `TimerActions` references no change-queue class, `PomodoroSessionStore` or `time/Pomodoro`, which `TimerActionsTest.actionsUseNeitherTheChangeQueueNorTheLocalPhaseRules` checks in the compiled classes.
- TDD. The RED run, with the API stubbed by `TODO()`, compiled and ran 7 `TimerActionsTest` tests with 6 failures, each a `NotImplementedError`. The structural check passed against the stubs, as expected.
- Checks. In the OOM-safe scope through `scripts/mem-gate.sh`: `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.timerstate.TimerActionsTest' --tests 'com.github.e0ipso.tokkl.api.SingleHttpClientTest' --tests 'com.github.e0ipso.tokkl.api.TogglApiClientTest'` exited 0. JUnit files: `TimerActionsTest` 7 tests, `SingleHttpClientTest` 1, `TogglApiClientTest` 16, all with 0 failures, 0 errors and 0 skipped. `TogglApiClientTest` was added to the acceptance selection because `execute` and the reason parsing changed. After the owner dropped the memory gate, `./gradlew --no-daemon :app:lintDebug` ran in the OOM-safe scope only and exited 0. Its 9 findings are all in files this task did not touch.
- Device checks. This task asks for none. Real-server action and cross-device checks belong to task 11, so no row was added to `deferred-device-checks.md`.
