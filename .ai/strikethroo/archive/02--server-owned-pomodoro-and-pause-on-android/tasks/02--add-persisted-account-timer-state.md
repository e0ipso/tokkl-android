---
id: 2
group: "timer-state"
dependencies: [1]
status: "completed"
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

- [x] Serializable Kotlin types mirror the verified `TimerState`, pause, Pomodoro phase/configuration and discarded-pomodoro shapes, reusing the existing v9 entry type. No local phase transition or long-rest rule is introduced.
- [x] One holder accepts GET, action and SSE states. It rejects lower revisions, accepts equal revisions to refresh the clock offset, and records arrival time and `server_time - receivedAt` consistently.
- [x] Latest state and the clock information needed after process death persist in an account store. A fresh process can read them without a request; sign-out clears them and late callbacks from the retired account cannot restore them.
- [x] Accepted `running_entry` updates the current-entry cache through its existing pending-write reconciliation. Queued local starts, stops and edits, including changes sent during an overlapping read, remain protected. Totals continue to count each real segment once.
- [x] Remaining time, overtime, the server's `number` of `long_rest_every`, paused seconds and clone elapsed time derive only from the state and corrected clock. Repeated `discarded_pomodoro.entry_id` values produce one user notice, including after state replay or process restart.
- [x] Add focused `TimerStateSourceTest` coverage of revision ordering, equal-revision clock refresh, persistence/account retirement and pending-write reconciliation. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.timerstate.TimerStateSourceTest' --tests 'com.github.e0ipso.tokkl.cache.CacheContentsTest'`; expect executed tests and zero failures.

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

## Execution Result

Completed on 2026-10-28 against the Tokkl 1.10.0 contract (`v1.10.0:packages/types/index.ts` and `packages/api/src/services/timerState.ts`).

- Types. `timerstate/TimerState.kt` mirrors `TimerState`, `TimerPause`, `TimerPomodoro`, `TimerPomodoroPhase` and `DiscardedPomodoro` field for field, with every field required. `running_entry` reuses `api.ServerTimeEntry`, now a data class so states compare by value. Phase kinds and transitions are their own enums, apart from `time/Pomodoro.kt`, and nothing in `timerstate/` applies a phase or long-rest rule.
- Holder. `TimerStateSource` has one state flow and one ingestion function, `accept(read, state)`, for GET, action, 409 and event states. `begin()` captures the cache before a request, or before a stream waits for its next event. Under one lock, `accept` drops a lower revision, replaces an equal one with its new arrival time and offset (`server_time - receivedAt`), stores it, publishes it and writes its running entry to the cache.
- Persistence and accounts. `TimerStateStore` keeps the held state, its arrival time and the taken discard notice in the private preferences file `timer_state`. `TokklApplication.timerState` loads it on first use. Sign-out retires the source right after forgetting the session, then clears its store before clearing the cache. Sign-in retires it again, clears the store and installs a new, empty source. A retired source, or a read made by another source, accepts nothing.
- Cache. The running entry goes through `AccountCache.checkedRunning`, the reconciliation the running entry check and the tile already use, behind the new `CurrentEntryCache` interface. That reconciliation now also protects entries whose changes were queued when the read began and were sent during it, plus the server ids those local ids took, as #17 did for the refresh. This also hardens `RunningCheckWorker` and `TileRefresh`. The writes of states accepted during a read count as server values, so a newer state still updates the cache. When a state removes a running entry, the app refreshes to bring back its stop.
- Derived values. `timerstate/TimerClock.kt` computes remaining time, overtime, clone elapsed time and the chain total from the state and the corrected clock only. "Pomodoro N of M" reads `number` and `long_rest_every` directly. `takeDiscardNotice()` is the single way to consume the too-short notice, keyed by `entry_id` and stored.
- No Room schema change: only the `ChangeDao.pendingEntryIds()` query was added.
- Checks. The acceptance command exited 0 with `TimerStateSourceTest` 13 tests and `CacheContentsTest` 16 tests, 0 failures. `RunningCheckTest` passed 14 tests with 0 failures (two new) and `NoDeviceZoneTest` 1 with 0 failures. `lintDebug` exited 0, with no issue in a touched file. On tokkl-api36, against the pinned 1.10.0 digest, `RunningEntryQueueTest` (10 tests, one new), `RunningCheckServerTest` (4), two `PauseChainTest` and two `PomodoroHandoffQueueTest` reconciliation methods ran 18 tests with 0 failures, 0 errors and 0 skipped. The first device attempt lost the emulator to a segfault before any results and was re-run.
