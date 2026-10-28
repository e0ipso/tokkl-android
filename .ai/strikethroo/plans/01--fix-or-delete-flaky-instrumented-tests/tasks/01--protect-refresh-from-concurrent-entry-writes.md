---
id: 1
group: "instrumented-reliability"
dependencies: []
status: "completed"
created: 2026-10-26
skills:
  - kotlin-coroutines
  - room
complexity_score: 7
complexity_notes: "The single refresh/send boundary is indivisible; sharpened to a before-read mark and atomic merge, with no schema change."
execution_profile: "complex-architecture"
---
# Protect refresh from concurrent entry writes

## Objective
Fix AccountCache.refresh so a snapshot read before a queue send cannot delete, remap backwards, or resurrect the row that send changes. This supplies the app fix for RunningCheckServerTest and CalendarServerTest.

## Skills Required
kotlin-coroutines, room

## Acceptance Criteria
- [x] A focused JVM regression demonstrates Start, Stop and local-ID remap across server read, send, refresh write, and a later uncontended refresh accepts server state.
- [x] Run the new regression before the production fix and record its expected failure, then run focused CacheContents, ChangeSender and RunningCheck JVM tests green.
- [x] Start and Stop stay offline and immediate; queue ordering and sign-out guard stay unchanged; no schema migration.
- [x] Update the two named kenkeep refresh/queue nodes and record keep-and-fix-in-app verdicts for RunningCheckServerTest and CalendarServerTest.

## Technical Requirements
Follow plan 1, its scope constraints, and method-only verification. The single refresh/send boundary is indivisible; sharpened to a before-read mark and atomic merge, with no schema change.

## Input Dependencies
Existing repository and plan 1; no task prerequisites.

## Output Artifacts
Scoped changes and durable per-task verdict/verification evidence for phase review.

## Implementation Notes
Write a few tests, mostly integration. Write tests for custom business logic and algorithms, critical user workflows and data transformations, core edge cases and errors, integration points, and complex validation or calculations. Do not test third-party or framework behavior, simple CRUD, trivial getters/setters or static configuration, or obvious functionality. Combine related scenarios; favor critical-path integration coverage over per-method tests.

<details>
<summary>Execution guidance</summary>

Own cache production files and cache JVM test files, plus .ai/kenkeep/nodes/data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md and .ai/kenkeep/nodes/data-and-offline/map-account-settings-source-and-account-time.md. Read ChangeDao, RoomChangeStore.sent, CacheDao.refresh, CacheContents.refreshedWith and runningCheckWrites before choosing the mark. A before-read snapshot of cached rows compared under the write transaction may follow the existing running-check precedent without new schema; also account for pending rows, remaps and removals. Protect rows changed during the read and retain normal history pruning and pending overlays. Stop and report if a schema change is necessary. Read relevant kenkeep branches and cross edges. Do not change instrumented assertions. Run only filtered JVM tests, through the OOM scope and memory gate. Do not run emulator tests in this task. Do not commit, rebuild the shared kenkeep index, or edit the plan body; the coordinator owns those operations. Document exact commands, exit codes, counts and logs in this task.

You are not alone in the codebase. Do not revert others' edits; accommodate concurrent work within your assigned ownership.
</details>


## Noteworthy Events

- 2026-10-26: Read PRE_TASK_EXECUTION and the refresh/queue/RunningCheck implementation before choosing a mark. The intended mark is an atomic snapshot of cached entry rows and pending entry IDs before the server read. Cached-row comparison alone misses a pending Stop whose send leaves the row unchanged, or a pending delete/short Stop whose row is already absent. Initially pending IDs must also remain protected through the stale write. No migration is needed. This design is not implemented yet.
- 2026-10-26: Drafted three focused CacheContentsTest regressions for Start/local-ID remap, Stop before/during the read, removal before/during the read, and a later uncontended refresh accepting server state. The draft includes input scaffolding for CacheContents.kt: EntryRefreshStart and an ignored `before` argument, only to obtain an assertion failure instead of a compilation failure. After admission was denied, removed all authored source edits and saved the uncompiled draft as `../evidence/task-001-refresh-regression-draft.patch`. Production and compiled test sources remain unchanged. Apply that draft only when resuming the RED attempt.
- 2026-10-26: RED attempt was blocked by the mandatory memory gate before Gradle started. Executed from `/home/e0ipso/workspace/apps/tokkl-android`:

  ```sh
  systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- env MEM_GATE_WAIT_S=30 /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.cache.CacheContentsTest.aRefreshReadBefore*' > build/plan-1-evidence/task-001/red.log 2>&1
  ```

  Exit code 75. Test count 0, because the workload never started. Final log line: `mem-gate: capacity deadline reached; workload was not started`. Last measured app.slice memory.high headroom was 2,403,246,080 bytes and memory.max headroom was 4,550,729,728 bytes, both below the 5000 MiB gate. The full capacity diagnostics are in `build/plan-1-evidence/task-001/red.log`. This is not an expected RED assertion failure. No GREEN run has occurred.
- 2026-10-26: Applied POST_ERROR_DETECTION by marking this task failed and reporting the blocker to the coordinator. Resume after host/cgroup capacity changes: apply the draft patch and run the same three regression methods to obtain an actual RED result, implement the atomic mark and merge, then run filtered CacheContentsTest, ChangeSenderTest and RunningCheckTest through the same scope and gate. Do not bypass the gate or treat the admission failure as test evidence.
- Verdicts from code inspection: keep and fix in app for `RunningCheckServerTest.startingATimerSchedulesOneCheckAndStoppingItCancelsIt` and `CalendarServerTest.anUnsentBlockSaysNotSentUntilTheServerHasIt`. Their assertions remain unchanged. The fix, named kenkeep updates and JVM verification are still pending; focused device verification belongs to task 004.

- 2026-10-26 resumed: Restored the saved regression patch and changed failed to in-progress. The first resumed targeted RED command used the same scope, gate, 30-second admission timeout and Gradle filter as above, with output `build/plan-1-evidence/task-001/resume-red.log`. It exited 75 before Gradle started, test count 0. Final app.slice memory.high headroom was 3776876544 bytes and memory.max headroom was 5924360192 bytes; host available was 10441641984 bytes. This was admission failure, not RED.
- 2026-10-26 resumed: The coordinator explicitly authorized one bounded `memory.reclaim` of `2G swappiness=0` before the next unchanged gated attempt. It completed with exit 0 under `timeout 30`; app.slice memory.current fell from 5,946,265,600 to 3,530,600,448 bytes. Evidence: `build/plan-1-evidence/task-001/resume-reclaim.log`. No cgroup limits, admission floors or processes changed. The next targeted RED attempt was admitted and is logged in `build/plan-1-evidence/task-001/resume-red-after-reclaim.log`.

- 2026-10-26 resumed RED: The following command exited 1 after 3m 42s. JUnit reported 3 tests, 3 failures, 0 errors and 0 skips. Each failure was the intended assertion: the remapped Start disappeared, the stopped row became running, and the removed row returned. No production fix had been applied. The only production scaffolding was the unused before-read argument/type from the saved draft. Full log: `build/plan-1-evidence/task-001/resume-red-after-reclaim.log`; copied JUnit result: `build/plan-1-evidence/task-001/resume-red-results.xml`.

  ```sh
  systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- env MEM_GATE_WAIT_S=30 /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.cache.CacheContentsTest.aRefreshReadBefore*' > build/plan-1-evidence/task-001/resume-red-after-reclaim.log 2>&1
  ```

- 2026-10-26 implementation: `CacheDao.entryRefreshStart` atomically reads cached entries and pending IDs. `AccountCache.refresh` captures that with the pause-chain mark before the server read and compares current rows under its existing write transaction. `CacheContents.refreshedWith` protects additions, changes, removals and initially pending IDs before overlaying remaining pending changes. Existing ID mappings also protect a pending negative ID that was created and removed during the read. If the initial snapshot fails with SQLiteException, the refresh returns rather than using an unguarded response. There is no database transaction around network I/O, no schema change, and no change to queue order, offline Start/Stop or the RunningCheck sign-out guard. Both named kenkeep leaves describe the rule; rebuilding the shared index remains the coordinator's responsibility.
- 2026-10-26 GREEN launch: First admission sample was below the memory.high floor. The coordinator reclaimed file cache, and the same bounded gate admitted the command without retrying it. The focused run includes CacheContentsTest, ChangeSenderTest and RunningCheckTest. No AccountCache JVM test exists; AccountCacheServerTest is instrumented and remains for coordinator-owned device validation.

- 2026-10-26 resumed GREEN: The command below exited 0, `BUILD SUCCESSFUL in 47s`. Fresh JUnit results: CacheContentsTest 16, ChangeSenderTest 64, RunningCheckTest 12; total 92 tests, 0 failures, 0 errors, 0 skips. The three new regressions passed, including the initially pending Stop/Delete cases and a subsequent uncontended refresh taking server state. No full suite or device tests ran, and GREEN was not repeated. Full log: `build/plan-1-evidence/task-001/resume-green.log`. JUnit copies: `resume-green-CacheContentsTest.xml`, `resume-green-ChangeSenderTest.xml` and `resume-green-RunningCheckTest.xml` in the same directory. The exclusive Gradle slot was released to the coordinator immediately on exit.

  ```sh
  systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- env MEM_GATE_WAIT_S=30 /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.cache.CacheContentsTest' --tests 'com.github.e0ipso.tokkl.cache.ChangeSenderTest' --tests 'com.github.e0ipso.tokkl.cache.RunningCheckTest' > build/plan-1-evidence/task-001/resume-green.log 2>&1
  ```

- 2026-10-26 final verdicts: **keep and fix in app** for `RunningCheckServerTest.startingATimerSchedulesOneCheckAndStoppingItCancelsIt` and `CalendarServerTest.anUnsentBlockSaysNotSentUntilTheServerHasIt`. The guarded refresh fixes the shared stale-write cause; their instrumented assertions were not changed. Focused API 31/36 verification remains with task 004, not claimed here. `git diff --check` exited 0. No schema changes, commits, pushes, merges or issue writes; unrelated edits preserved. Source hashes for this verified implementation are in `build/plan-1-evidence/task-001/resume-verified-sources.log`.
