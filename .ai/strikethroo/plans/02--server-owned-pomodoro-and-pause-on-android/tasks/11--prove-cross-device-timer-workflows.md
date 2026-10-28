---
id: 11
group: "migration-and-acceptance"
dependencies: [10]
status: "pending"
created: 2026-10-28
skills:
  - android-testing
  - api-testing
complexity_score: 6
complexity_notes: "One real-server workflow suite covers both directions of the same revisioned timer contract. The cases and two-second bounds are explicit; Android lifecycle/Doze and the final full-suite gate are separated into task 12."
execution_profile: "complex-architecture"
---
# Prove cross-device timer workflows

## Objective

Verify that the phone and another credential on the real released server share sessions, phases and pause chains without an offline session queue.

## Skills Required

Android instrumented workflow testing and authenticated real-server API testing.

## Acceptance Criteria

- [ ] A session started through another credential of the same user reaches the backgrounded phone within 2 seconds. Its phase, countdown and `Pomodoro N of M` match the server, and a phone-started session appears through the API with its form fields and overrides.
- [ ] At the server planned end the phone rings. API Next phase and End session each stop ringing and close its alarm within 2 seconds. A stale phone Next receives 409, advances nothing and shows the server state plus `Another device changed the timer first.`
- [ ] Pause/Resume work in both directions and Discard clears a paused chain. Compare `pause.seconds` and the clone contribution using a controlled sampling instant/tolerance, and verify Stop of a running clone stays an ordinary v9 stop.
- [ ] All six session/pause actions fail offline with the exact offline message and unchanged held state/no queued session work. Exercise the app, alarm/session notification, running notification, widget, tile and reminder where each action is exposed. Ordinary queued Start/Stop still change the phone immediately.
- [ ] On an actually unsupported older Tokkl server, Pomodoro and every Pause/Resume control are hidden while Start/Stop still work. Include a non-Tokkl capability decision through existing capability coverage, without requiring a personal Toggl account or adding a fake server.
- [ ] Cover remote phase replacement, repeated discarded-entry notices and a stale native revision through focused workflow cases or the meaningful retained component checks. Do not recreate tests of server phase rules in Android.
- [ ] Add `TimerCrossDeviceServerTest` and `TimerActionsOfflineServerTest`, and update affected existing server workflow tests. For each of tokkl-api36 and tokkl-api31 run `scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timerstate.TimerCrossDeviceServerTest,com.github.e0ipso.tokkl.timerstate.TimerActionsOfflineServerTest --no-daemon` with `TOKKL_TEST_AVD` set. Expect the stated cases executed, zero failures and successful runner receipts. Retain case counts, timing and image digest in evidence.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Use the existing `TokklTestServer`/credential-file support and the image pinned by task 1. Another credential must address the same user, such as password auth alongside that user's API token. A second user would not exercise shared timer state. Unsupported-server checks may start the old digest-pinned 1.8.1 image using the existing runner setup pattern, without changing the primary pin or introducing a replacement harness.

## Input Dependencies

Hard dependency on task 10's complete server-backed app and surviving queue/migration paths. No soft dependencies.

## Output Artifacts

Focused real-server integration tests, an explicit scenario-to-test/surface matrix and retained results on both supported emulator API levels.

## Implementation Notes

Write a few tests, mostly integration. Write tests for custom business logic and algorithms; critical user workflows and data transformations; edge cases and error conditions in core functionality; integration points between components; and complex validation or calculation logic. Do not write tests for third-party library/framework functionality, simple CRUD without custom logic, trivial getters/setters/static configuration, or obvious functionality that would break immediately if incorrect. Combine related scenarios into one task and favor integration/critical-path coverage over per-method tests. Never create one test task per CRUD operation.

<details>
<summary>Cross-device and offline workflow coverage</summary>

1. Inventory retained tests before adding cases. Reuse existing server helpers, native action helpers and component tests; record the matrix showing which test proves each required action/surface. Do not multiply identical transport tests across every button.
2. Make remote actions with a distinct credential for the signed-in user and the real API. Keep tokens/passwords in the runner's private credential files, never process arguments or retained logs. Use API responses as the authoritative phase/revision/total assertions.
3. Background the activity while SSE remains active, start a real short session and measure observable arrival against the action completion with a monotonic deadline. Verify actual ringing/full-screen lifecycle at its planned end. Use bounded condition waits rather than arbitrary sleeps.
4. Capture a revision, change the phase remotely, then invoke the phone action with the captured revision. Assert 409, no extra server transition, holder adoption, message and remote silencing. An action that silently upgrades to a fresh revision must fail the test.
5. Exercise offline actions through actual consumer paths. Check the held state and queue before/after; separately assert immediate queued Start/Stop. Do not fake timer API responses or infer a pass only from a snackbar.
6. Use an older real pinned server for the absent-capability scenario and inspect bar/rail plus native controls. Preserve the main new-server pin and the runner's setup, isolation and cleanup rules; reuse existing test setup mechanisms rather than adding a new harness.
7. Run focused tests on each AVD through the maintained script in OOM-safe scopes. Confirm nonzero per-class JUnit cases and device/orchestrator receipts. Fix all known focused failures before task 12's full-suite validation.

</details>
