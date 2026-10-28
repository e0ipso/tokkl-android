---
id: 10
group: "migration-and-acceptance"
dependencies: [8, 9]
status: "completed"
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

- [x] Before each module removal, record its live callers and which surviving feature now owns any shared utility. Preserve `time/Snap.kt` and ordinary queued timer/entry behavior.
- [x] Delete `time/Pomodoro.kt`, its `PomodoroTest.kt` and copied `pomodoroSession.json`, local `PomodoroSessionStore`, `cache/PomodoroSession.kt`/DAO, local pause-chain/session/mark types and their id/revision/epoch fencing.
- [x] Remove the Pomodoro save path from ChangeQueue/ChangeStore, foreign-entry handoff from AccountCache/ChangeQueue and remaining local-session composition-root wiring. Sign-out no longer ends/saves a session or counts an unsaved local pomodoro; it still warns for real queued entry changes.
- [x] A non-destructive migration from schema 8 drops only `pomodoro_session`, `pause_chain` and `timer_session`, with no carryover. The next exported schema is tracked, older upgrade paths remain valid, and queued v9 rows, id mappings, held/refused state and short-stop Undo survive.
- [x] Delete tests covering only removed rules/handoff/chain behavior. Keep and adapt tests for retained queue, native alarm, defaults, account cleanup and moved server-state behavior.
- [x] `rg -n 'PauseChain|ChainMark|TimerSession|PomodoroSessionDao|timerHandoff|object Pomodoro([[:space:]]|$)' app/src` exits 1 with no matches. `time/Pomodoro.kt`, `cache/PomodoroSession.kt` and `app/src/test/resources/time/pomodoroSession.json` no longer exist.
- [x] Extend `CacheDatabaseMigrationTest` with an actual version-8 file containing all three retired tables plus pending v9 changes and Undo/mapping rows. After opening the current schema, assert retired tables are absent and retained queue data still sends through the existing sender.
- [x] Run `./gradlew --no-daemon :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`, then `TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.cache.CacheDatabaseMigrationTest,com.github.e0ipso.tokkl.cache.ChangeQueueServerTest,com.github.e0ipso.tokkl.cache.ChangeQueueRestartTest --no-daemon`. Expect zero failures, nonzero tests and successful device receipts.

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

## Execution Result

Completed on 2026-10-28 against Tokkl 1.10.0 (`ghcr.io/e0ipso/tokkl:1.10.0@sha256:207db4f2a6547fea35ad898570fef892f55ef3ab26b6f142bfc3e3e0e680ad2f`), on top of `0b7b5df`.

- Caller audit, taken from the live tree before each deletion. Task 09's inventory missed several of these.
  - `time/Pomodoro.kt`: only `pomodoro/PomodoroSessionStore.kt` and tests (`PomodoroTest`, `PomodoroSessionStoreTest`, `PomodoroSignOutTest`, `PomodoroTimerHandoffTest`, `PomodoroHandoffQueueTest`, `PomodoroHandoffServerTest`, `PomodoroSignOutQueueTest`). Its length ranges had no caller. The start form already checks `PomodoroDefaults.WORK_MINUTES` and the other three ranges (`PomodoroDefaultsSetting.kt`), and `SessionLengths` (`timerstate/TimerActions.kt`) checks them again. The phase clock is `pomodoro/PomodoroClock.kt` on the held state. `time/Snap.kt`, `PomodoroClock.kt` and `PomodoroDefaultsSetting.kt` have no diff.
  - `pomodoro/PomodoroSessionStore.kt` (`PomodoroStep`, `snappedPomodoro`, the Room adapter): `TokklApplication` (the store, its sign-out `forget()`, and `unsavedChangeCount()` behind the sign-out warning), and the `pomodoro` suppliers of `AccountCache` and `ChangeQueue`.
  - `cache/PomodoroSession.kt` and `PomodoroSessionDao.kt`: the store, `CacheDatabase`, `AccountCache.endForForeignTimer`, the migration test.
  - `cache/PauseChain.kt` (`PauseChain`, `ChainMark`, `TimerSession`, `newTimerId`): `ChangeDao` (chain, segment and session queries, `reconcilePauseChain`, `chainMark`), `ChangeStore` (`touchPauseChain`, `remapPauseChain`, `endChainOf`), `ChangeQueue` (the `timerControls` flow, `pause`, `resume`, `stop(target)`, `editPaused`, the chain deletes in start, stop, update, updateOlder, both deletes and discard, `PauseSegmentCheckpoint`, `pauseChainAfterDiscardedCreate`), `AccountCache` (`ChainMark`, `RunningCheckStart.chain`, the new timer session in `clear()`), `TimerControls` (`timerControls(running, chain, session)`, `TimerTarget.session` and `chainId`) and `CacheDatabase`. `TimerStateSource` passed `RunningCheckStart.chain` along. No surface read `ChangeQueue.timerControls`; only tests did.
  - `ChangeDao.possiblySentStarts` came with the handoff (`71269d1`) and had no other caller, so it went too.
  - Retained server-state names that the plan's looser grep also matches were renamed: `timerstate.PauseChainActions` became `PauseActions`, and `object PomodoroSessionNotification { ID = 3 }` became `PomodoroSessionNotifier.NOTIFICATION_ID`, as `RunningNotifier.NOTIFICATION_ID` already is.
- Queue. Start, Stop, create, update, `updateOlder`, both deletes, `discard`, `undoShortStop`, id remapping and the captured and short Stop paths remain, minus the chain and session steps. `EntryChanges` loses its four chain defaults. A queued Stop of the server's clone or open pomodoro is an ordinary Stop.
- Sign-out. It no longer forgets, ends or saves a local session. The warning counts `changes.unsentCount` again, and the plural text no longer mentions unsaved Pomodoro work (the wording from before `d6d8aaf`). Its steps make no server timer request, so sign-out sends no End session.
- Migration. `CacheDatabase` is version 9 without the three entities. `AutoMigration(from = 8, to = 9, spec = CacheDatabase.DropLocalTimerTables::class)` names `pomodoro_session`, `pause_chain` and `timer_session` in `@DeleteTable.Entries`. Migrations 1 to 8 and schemas `1.json` to `8.json` are unchanged. The new `app/schemas/com.github.e0ipso.tokkl.cache.CacheDatabase/9.json` (identity hash `056926b082c8c580bac4595b21f4295a`, the same nine tables as version 6) must be committed; its retained tables equal version 8's field for field. RED: with the `timer_session` entry removed, KSP failed ("AutoMigration Failure in 'CacheDatabase': Table 'timer_session' has been either removed or ..."). GREEN after restoring it.
- Tests deleted, as they cover only removed behaviour: `PomodoroTest` with `pomodoroSession.json`, `PauseChainDiscardTest`, `PomodoroSessionStoreTest`, `PomodoroSignOutTest`, `PomodoroTimerHandoffTest`, `PauseChainTest`, `PauseChainDiscardQueueTest`, `PomodoroHandoffQueueTest`, `PomodoroHandoffServerTest` and `PomodoroSignOutQueueTest`. Short Stop and Undo against the server stay covered by `ChangeQueueServerTest`, and the queued-change warning by `SignOutServerTest` and `SignOutModelTest`.
- Tests rewritten against the server state: `CacheDatabaseMigrationTest` (v6 stops a migrated running entry, v7 keeps its queue without the dropped tables, and the new v8 test is described under Checks), `RunningEntryQueueTest.correctingTheServersResumedCloneChangesOnlyItsOwnElapsedTime`, `RunningEntryEditorServerTest.aServerPauseWhileTheEditorIsOpenCannotBeRevivedBySave`, `TimerTotalsServerTest.offlineStartStopEditDeleteAndUndoRemapExactlyOnceWithoutReportsOrPaging` (offline Pause is gone, so its segments are a Stop and a second Start), `TimerAcceptanceFixture` (reads `app.timerControls`; Stop of a paused chain is the server's discard), `PomodoroSignOutServerTest` (the warning counts only queued changes while the server holds a session, and sign-out leaves no held state), `SignOutServerTest` (no `timer_session` reads) and `NativePomodoroPermissionTest` (reads the held server session). `pauseAndResumeCannotMakeAnEarlierEditorsIdentityCurrentAgain` was deleted; `sameInstantReplacementRejectsTheOriginalIdentity` and `stoppedEntryRejectsStaleSaveWithoutAnotherQueuedChange` cover its identity rule. Unit tests lost the chain overrides, the `RunningCheckStart` chain argument and the legacy controls builder, and `TimerActionsTest` lost its two forbidden entries for deleted classes.
- Not done: the `pomodoro_phase_alarm` preferences file that Task 08's removed store wrote (`phase_kind`, `phase_started_at`) stays unread on upgraded phones. This task's criteria name only the three Room tables.
- Checks, all in the OOM-safe scope:
  - Removal scan: the task's `rg` and the plan's `grep -rnE "PauseChain|ChainMark|TimerSession|PomodoroSessionDao|timerHandoff|object Pomodoro" app/src` both exit 1 with no output. The three named paths are gone.
  - JVM: the host rule allows focused runs only, so `:app:testDebugUnitTest` ran 19 affected classes: 257 tests, 0 failures, 0 errors, 0 skipped. The full suite is Task 12's. `:app:compileDebugAndroidTestKotlin` passed, also with `-PtokklFreshNotificationPermission=true`. `:app:lintDebug` exited 0 with 9 findings, none in a touched file (`widget_stop` has been unused since `177d6be`).
  - The task's device command on tokkl-api36: 24 tests, 0 failures, 0 errors, 0 skipped (`CacheDatabaseMigrationTest` 8, `ChangeQueueServerTest` 14, `ChangeQueueRestartTest` 2), exit 0. The v8 test seeds a raw version 8 file with a saved-pomodoro create, a captured Stop of a server timer, a held refused edit, an id mapping, a short Stop Undo row and rows in all three dropped tables. After the open, the dropped tables are absent and the rest is intact. The existing `ChangeSender` then delivers the create and the Stop to the server, leaves the held edit held and unsent, and keeps the mapping and the Undo row, whose Undo still queues a RESTORE.
  - Rewritten tests on tokkl-api36: 23 tests, 0 failures (`RunningEntryQueueTest` 9, the three single methods 1 each, `PomodoroSignOutServerTest` 4, `SignOutServerTest` 1, `PomodoroSessionNotifierTest` 6).
  - Both sets on tokkl-api31: the first run had 46 tests and 1 failure, the second 46 tests and 0 failures. The failure was `SignOutServerTest`, whose Cancel tap found two dialogs with a Cancel button while only one held the sign-out text. The second dialog was most likely the Pomodoro defaults or reminder editor. The test then passed alone (1/1) and in the repeated batch, so it is intermittent, 1 failure in 3 API 31 runs, and passed on API 36. Why the second dialog opened is not proven.
  - `NativePomodoroPermissionTest` alone with a fresh permission on tokkl-api36: `grantingTheFirstSessionDialogKeepsTheSameStartedSession` 1/1. `denyingThenRecreatingKeepsPromptsAndLaterStartsAskOnlyOnExplicitRetry` fails at its wait for the session to end after End session. Two diagnostic runs, both reverted, showed revision 1 before and after the tap, End enabled before and after, nothing queued and no refusal or offline message. So the page sent nothing and reported nothing. The probe could not pass at `0b7b5df` either, since it read the local store that the Pomodoro page has not used since Task 07. The End path after a recreate belongs to the Pomodoro page model, outside this task.
