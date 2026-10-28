---
id: 12
group: "migration-and-acceptance"
dependencies: [11]
status: "pending"
created: 2026-10-28
skills:
  - android-testing
  - android-services
complexity_score: 6
complexity_notes: "This is one platform acceptance gate for the implemented service/alarm lifecycle, with explicit API 31/API 36 observations. Cross-device workflow tests are already supplied by task 11; this task reuses the runner and does not design a new harness."
execution_profile: "complex-architecture"
---
# Verify service lifecycle, Doze and final test gates

## Objective

Establish how the always-on sync and persisted alarms behave across Android lifecycle restrictions, then complete the plan's unit/Compose/device acceptance gates.

## Skills Required

Android instrumented acceptance testing and foreground-service/platform diagnostics.

## Acceptance Criteria

- [ ] On tokkl-api31 and tokkl-api36, record foreground `specialUse` service state and its silent notification after app open/sign-in, boot and package replacement. If Android refuses an exempt background start, record the refusal and prove the next app open recovers without a crash.
- [ ] Process restart restores the held phase, clock correction, session/native notification and exact alarm without needing a request. Clock/timezone changes and an exact-alarm grant rearm the same phase; a missed end rings without advancing it.
- [ ] Sign-out/capability loss stops streams/reconnects, notifications and session alarms, clears account state and rejects retired callbacks. Signing out does not end the server session for other devices.
- [ ] Force Doze with the app backgrounded, start a session remotely and record whether notification/state arrives and its measured delay on both APIs. Restore idle mode/settings after the probe. An observed Doze delay is reported for the PRD and owner, not hidden behind a new transport, exemption or battery workaround.
- [ ] Retain permission-denied notification/exact-alarm/full-screen behavior, the existing sounds and ringing service, and the alarm's offline failure behavior. Review screenshot references on tokkl-api36 in the commit that changes them; API 31 uses its intended screenshot exclusions.
- [ ] Add or adapt `TimerSyncServiceLifecycleTest` for critical account/process/start lifecycle behavior. Run it on each AVD through `scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timerstate.TimerSyncServiceLifecycleTest --no-daemon`; expect nonzero tests, zero unexpected failures and valid run receipts.
- [ ] After all known focused failures are resolved, run `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug` and complete `TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh --no-daemon` plus the same full run with `TOKKL_TEST_AVD=tokkl-api31`. Require zero failures, expected nonzero test counts and device/process-isolation receipts. Retain results from each run before the next overwrites them.
- [ ] The evidence matrix covers all six primary success criteria, including migration from version 8, plain offline Start/Stop and unsupported-server hiding. Separate measured platform limitations from passing results and surface any required decision before completing execution.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Use plain AOSP tokkl-api31/tokkl-api36, the maintained real-server runner and its orchestration/cleanup. Device diagnostics can use service/alarm/log dumps. For interactive device work use the current host's device tools and the exact `agent-device` launcher they supply; do not invent a second emulator control setup.

## Input Dependencies

Hard dependency on task 11's focused workflow tests and fixed known failures. No soft dependencies. Task 13 needs this task's measured lifecycle/Doze results.

## Output Artifacts

Lifecycle checks, two-API start/Doze/permission observations, final verification reports and a success-criterion evidence matrix for documentation and execution review.

## Implementation Notes

Write a few tests, mostly integration. Write tests for custom business logic and algorithms; critical user workflows and data transformations; edge cases and error conditions in core functionality; integration points between components; and complex validation or calculation logic. Do not write tests for third-party library/framework functionality, simple CRUD without custom logic, trivial getters/setters/static configuration, or obvious functionality that would break immediately if incorrect. Combine related scenarios into one task and favor integration/critical-path coverage over per-method tests. Never create one test task per CRUD operation.

<details>
<summary>Platform observations and final validation</summary>

1. Read the current testing knowledge branches and verify the runner's flags. Run Gradle and the instrumented script in `systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- ...`; let the runner own emulator settings and resources. Never call `./gradlew --stop`.
2. Verify foreground app/sign-in start, persisted-account boot and actual package replacement. Inspect the service's foreground type and notification channel, and record an exact platform refusal if present. Distinguish a missing capability/account from an Android start restriction.
3. Exercise process death with persisted server state and no network. Confirm the same phase identity, corrected planned-end schedule and native content are restored. A stale alarm must not ring a replacement phase; an expired held phase must ring at once without a transition.
4. Probe Doze while idle in the background and initiate a real remote session. Record elapsed arrival time, API level, image digest and stream diagnostics, then restore the device's idle settings in cleanup. The plan allows reporting a maintenance-window delay but does not authorize a workaround. Surface any broader inability to meet the agreed behavior.
5. Reuse or adapt retained permission/ringing tests. Run native permission probes alone with fresh permission when the testing instructions require it. Do not create tests for Android's alarm or service implementation itself; assert this app's behavior.
6. Inventory every failure, reproduce and fix it in a focused run, then perform the full JVM/lint and both full device runs once the known list is empty. Verify JUnit counts and runner receipts, not only `BUILD SUCCESSFUL`. Preserve immutable result copies under this plan's evidence directory with secrets removed.
7. Produce the acceptance matrix and platform observations task 13 will document. Do not claim a delayed Doze result was an ordinary two-second pass.

</details>
