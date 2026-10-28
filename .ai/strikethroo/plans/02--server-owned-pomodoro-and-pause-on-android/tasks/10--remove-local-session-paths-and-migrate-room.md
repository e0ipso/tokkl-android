---
id: 10
group: "migration-and-acceptance"
dependencies: [8, 9]
status: "pending"
created: 2026-10-28
skills:
  - kotlin-refactoring
  - room-migrations
complexity_score: 7
complexity_notes: "One removal boundary retires the interdependent local session/chain transaction graph and its three Room tables after every consumer is migrated. Separating table removal from caller cleanup would leave an uncompilable graph; queue preservation and migration checks bound the task."
execution_profile: "complex-architecture"
---
# Remove local session paths and migrate Room

## Objective

Delete the device-owned Pomodoro and pause implementation, and upgrade the cache without losing ordinary queued changes.

## Skills Required

Kotlin caller-driven removal and Room schema migrations.

## Acceptance Criteria

- [ ] Before each module removal, record its live callers and which surviving feature now owns any shared utility. Preserve `time/Snap.kt` and ordinary queued timer/entry behavior.
- [ ] Delete `time/Pomodoro.kt`, its `PomodoroTest.kt` and copied `pomodoroSession.json`, local `PomodoroSessionStore`, `cache/PomodoroSession.kt`/DAO, local pause-chain/session/mark types and their id/revision/epoch fencing.
- [ ] Remove the Pomodoro save path from ChangeQueue/ChangeStore, foreign-entry handoff from AccountCache/ChangeQueue and remaining local-session composition-root wiring. Sign-out no longer ends/saves a session or counts an unsaved local pomodoro; it still warns for real queued entry changes.
- [ ] A non-destructive migration from schema 8 drops only `pomodoro_session`, `pause_chain` and `timer_session`, with no carryover. The next exported schema is tracked, older upgrade paths remain valid, and queued v9 rows, id mappings, held/refused state and short-stop Undo survive.
- [ ] Delete tests covering only removed rules/handoff/chain behavior. Keep and adapt tests for retained queue, native alarm, defaults, account cleanup and moved server-state behavior.
- [ ] `rg -n 'PauseChain|ChainMark|TimerSession|PomodoroSessionDao|timerHandoff|object Pomodoro([[:space:]]|$)' app/src` exits 1 with no matches. `time/Pomodoro.kt`, `cache/PomodoroSession.kt` and `app/src/test/resources/time/pomodoroSession.json` no longer exist.
- [ ] Extend `CacheDatabaseMigrationTest` with an actual version-8 file containing all three retired tables plus pending v9 changes and Undo/mapping rows. After opening the current schema, assert retired tables are absent and retained queue data still sends through the existing sender.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`, then `TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.cache.CacheDatabaseMigrationTest,com.github.e0ipso.tokkl.cache.ChangeQueueServerTest,com.github.e0ipso.tokkl.cache.ChangeQueueRestartTest --no-daemon`. Expect zero failures, nonzero tests and successful device receipts.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Own final removal in the cache/queue/store graph, `CacheDatabase`, `ChangeDao`, exported schemas, account cleanup and obsolete tests. At generation the database is version 8; use version 9 unless the live tree has advanced, in which case retain an explicit upgrade from version 8. Do not use destructive migration or drop the database file.

## Input Dependencies

Hard dependencies on tasks 8 and 9, which transitively require all screen, service and native migrations. No soft dependencies. Remove code only after this full caller conversion is available.

## Output Artifacts

One server-owned session/pause path, the table-drop migration/exported schema, preserved ordinary queue behavior and migration/removal verification results.

## Implementation Notes

<details>
<summary>Caller audit, deletion and migration</summary>

1. Use `rg` to enumerate callers before deleting each legacy module. The paths named in the plan are a starting point, not an exhaustive list. Inspect ChangeDao/ChangeStore transactions and the application composition root as well as direct imports.
2. Delete legacy rules, entity/DAO and store only after the new UI, alarms and native controls no longer need them. Remove handoff callbacks and transaction branches, retaining entry snapping, pending-change overlay and ordinary queue delivery.
3. Remove sign-out's local Pomodoro finalization and local unsaved-session count. Stop account stream/alarm work and clear held state through the already converted lifecycle; sign-out must not send a remote End session as a substitute.
4. Add an explicit table-drop Room migration or Room-supported migration spec, register it, generate the next schema and keep historical schemas for upgrades. Ordinary queued stopped entries produced by older builds remain ordinary v9 requests and must still send.
5. Extend the repository's existing migration-test technique, which opens databases from exported schemas. Do not add a testing dependency just to test Room itself. Seed version-8 legacy data with raw SQL so tests need no deleted entity types.
6. Remove tests that only assert deleted behavior; rewrite mixed tests to assert the retained queue or server-state contract. Record the caller/removal audit and commands in task evidence. Use OOM-safe scopes, and investigate every focused failure before broader checks.

</details>
