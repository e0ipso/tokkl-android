---
id: 4
group: "timer-state"
dependencies: [2]
status: "completed"
created: 2026-10-28
skills:
  - kotlin
complexity_score: 4
execution_profile: "standard-implementation"
---
# Probe and persist timer state capability

## Objective

Expose one capability flag that identifies servers supporting server-owned sessions and pause chains.

## Skills Required

Kotlin capability probing and persisted state updates.

## Acceptance Criteria

- [x] `Capabilities.timerState` defaults to false, persists with the other capability flags and clears at sign-out.
- [x] The existing account-refresh path probes `GET /api/app/timer` once. A parsed state means present and seeds the holder; 404 or a successful response that is not a timer state means absent. An inconclusive error preserves the previous flag.
- [x] Toggl Track and old Tokkl remain plain timer clients. Probing compares no server version and duplicates no `/me` requests.
- [x] Consumers can observe capability changes to start/stop the SSE service and hide Pomodoro/Pause controls in later tasks. This task owns the flag and refresh integration, not those consumers' UI.
- [x] Extend the existing capability tests with present, absent, invalid-body, unknown, persistence and sign-out cases. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.capabilities.CapabilitiesTest'`; expect zero failures and the new cases executed.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Follow `probeTrash`, `CapabilitiesSource`, `CapabilitiesStore` and `TokklApplication`'s account-refresh callback. All requests retain shared trust and URL-prefix handling. A previously enabled capability stays enabled while temporarily offline.

## Input Dependencies

Hard dependency on task 2's timer-state parser and holder. No soft dependencies; the GET probe can follow `probeTrash` without waiting for task 3's action client.

## Output Artifacts

Persisted timer-state flag, three-way probe result, refresh-to-holder wiring and focused probe checks.

## Implementation Notes

<details>
<summary>Capability integration</summary>

1. Add the flag to the live capability model without disturbing the newer CSV capability fields that are absent from older knowledge snapshots.
2. Add a three-way probe using the shared API client and task 2's DTO serializer. Treat parse failure on a 2xx response as definite absence, while network, auth, trust and rate-limit failures remain unknown.
3. Feed both the probe result and any valid state through the account-refresh callback. Do not issue a second GET only to seed the holder. An old account's late refresh must not repopulate a new account's flags or state.
4. Update store reads/writes and reset behavior. Missing stored keys read as false. Keep existing capability semantics intact.
5. Extend meaningful capability decision tests. UI gating is owned by tasks 6 through 9, and service gating by task 5. Run the acceptance command in the host's OOM-safe scope.

</details>
