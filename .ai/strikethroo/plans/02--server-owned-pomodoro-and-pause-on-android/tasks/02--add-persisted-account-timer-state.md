---
id: 2
group: "timer-state"
dependencies: [1]
status: "pending"
created: 2026-10-28
skills:
  - kotlin
  - android-persistence
complexity_score: 6
complexity_notes: "One account-state ingestion operation spans persistence and the existing cache reconciliation boundary. Keep it together so revision acceptance, saved state and published current entry agree; service transport and all UI consumers are separate tasks."
execution_profile: "complex-architecture"
---
# Add persisted account timer state

## Objective

Create the single account-scoped source of server timer state, its corrected clock and its current-entry cache update.

## Skills Required

Kotlin state modeling and Android private account persistence.

## Acceptance Criteria

- [ ] Serializable Kotlin types mirror the verified `TimerState`, pause, Pomodoro phase/configuration and discarded-pomodoro shapes, reusing the existing v9 entry type. No local phase transition or long-rest rule is introduced.
- [ ] One holder accepts GET, action and SSE states. It rejects lower revisions, accepts equal revisions to refresh the clock offset, and records arrival time and `server_time - receivedAt` consistently.
- [ ] Latest state and the clock information needed after process death persist in an account store. A fresh process can read them without a request; sign-out clears them and late callbacks from the retired account cannot restore them.
- [ ] Accepted `running_entry` updates the current-entry cache through its existing pending-write reconciliation. Queued local starts, stops and edits, including changes sent during an overlapping read, remain protected. Totals continue to count each real segment once.
- [ ] Remaining time, overtime, the server's `number` of `long_rest_every`, paused seconds and clone elapsed time derive only from the state and corrected clock. Repeated `discarded_pomodoro.entry_id` values produce one user notice, including after state replay or process restart.
- [ ] Add focused `TimerStateSourceTest` coverage of revision ordering, equal-revision clock refresh, persistence/account retirement and pending-write reconciliation. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.timerstate.TimerStateSourceTest' --tests 'com.github.e0ipso.tokkl.cache.CacheContentsTest'`; expect executed tests and zero failures.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Use a cohesive `timerstate/` package for the new DTOs, holder and derived values. The precise persistence adapter may follow the app's existing private stores. The new store is a snapshot of server state, not a replacement local session database. `CacheContents` and `AccountCache` already protect in-flight writes on current main; preserve that behavior when adding state ingestion.

## Input Dependencies

Hard dependency on task 1's verified schema and release contract. No soft dependencies.

## Output Artifacts

Timer state DTOs, holder, account persistence/cleanup wiring, corrected-clock helpers, once-only discard notice delivery and a cache ingestion boundary for every later consumer.

## Implementation Notes

<details>
<summary>State ingestion and cache integration</summary>

1. Read the live `AccountCache`, `CacheContents`, `RunningCheck`, private account stores and sign-out ordering. List existing current-entry reconciliation callers before adding a new one.
2. Define snake_case serialization names from task 1's contract. Keep server phase/transition values distinct from the legacy `time/Pomodoro.kt` types so new consumers do not inherit its rules.
3. Expose one state flow and one ingestion function. Serialize acceptance and persistence sufficiently that a slower lower-revision result cannot overwrite a newer persisted state. Equal revisions may replace clock information.
4. Apply the existing cache overlay and overlap protection to `running_entry`; do not write directly around the queue or clear queued changes. Reuse current reconciliation concepts rather than creating a second conflict policy. Add focused coverage for an incoming null current entry while a local Start is pending and for an overlapping sent write.
5. Persist the held state, arrival/offset information and acknowledged discard-notice key with the account's data. Recreate the holder with a new account lifetime at sign-in. Reject callbacks retaining an old account handle. This account isolation does not retain the local pause chain's fencing types.
6. Calculate countdown as server `planned_end` minus corrected now. A running clone adds elapsed time to `pause.seconds`; a paused chain is frozen. Publish the too-short notice through a single consumption path so several surfaces cannot display it repeatedly.
7. Add tests for custom ordering, clock and reconciliation logic rather than serialization-library internals. Run the acceptance command in the host's OOM-safe scope, and confirm the test count in its reports.

</details>
