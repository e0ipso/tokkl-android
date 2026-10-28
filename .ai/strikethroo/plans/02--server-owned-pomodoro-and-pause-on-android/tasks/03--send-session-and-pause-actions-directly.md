---
id: 3
group: "timer-state"
dependencies: [2]
status: "pending"
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

- [ ] The six server actions use the verified app routes, account Basic authentication, configured URL prefix, shared HTTP client and trust handling. Session Start sends the form fields, four lengths and the existing timer `created_with` value.
- [ ] Resume, Discard, Next phase and End send the revision of the state the action represents. The API accepts the displayed revision from native controls, so an old notification cannot silently act on a newer phase. Pause and session Start follow their release-defined body contracts.
- [ ] A 2xx state enters task 2's holder. A valid 409 state also enters the holder and reports exactly `Another device changed the timer first.` The revision guard still rejects an older response.
- [ ] A connection failure reports exactly `Can't reach the server. Try again when you're back online.` It changes no timer/session/pause state and creates no queued request. Other errors and auth/trust failures use existing application paths.
- [ ] Ordinary v9 Start, Stop and running-entry edits retain their queue path. Online actions never invoke local phase rules or automatically retry a mutation after uncertain delivery.
- [ ] Add `TimerActionsTest` for request bodies/revisions, state adoption on 2xx/409, offline immutability and queue independence. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.timerstate.TimerActionsTest' --tests 'com.github.e0ipso.tokkl.api.SingleHttpClientTest'`; expect nonzero tests and zero failures.

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
