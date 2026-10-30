---
id: 2
group: "pomodoro-widget"
dependencies: []
status: "completed"
created: 2026-10-29
skills:
  - kotlin
  - android-storage
complexity_score: 6
complexity_notes: "The task owns one integrated deliverable; state, provider layout, device proof and documentation are separate tasks. Acceptance commands and ownership bound its integration."
execution_profile: "complex-architecture"
---
# Derive Pomodoro widget state and remember last fields

## Objective

Derive the second widget state from the shipped server timer holder, persist last session fields for idle Start, and prepare independent cache-key synchronization.

## Skills Required

kotlin, android-storage.

## Acceptance Criteria

- [x] Verify archived plan 02 tasks 03, 05, 07 and 08 are completed and their live actions, state, clock offset and phase-end mechanisms exist. Record this external hard dependency and evidence.
- [x] Represent no-account, unsupported, idle defaults/last fields, and active work/rest/ended states with phase timing, revision, number/cycle, project colour, tags and billable. Use held state and server offset; make no extra request.
- [x] Remember last fields when a session starts from any surface, restore them for idle Start, isolate them by account and clear them on sign-out. Reuse persisted timer state where sufficient.
- [x] Generalize WidgetSync/WidgetStore minimally for separate keys without breaking Timer sync. Provide Pomodoro state flow and phase-end redraw using existing alarm/ringing machinery, with no new idle wakeup.
- [x] Run focused JVM widget/state/defaults tests via ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.*' --tests 'com.github.e0ipso.tokkl.pomodoro.*'; expect nonzero tests and zero failures. Run :app:compileDebugAndroidTestKotlin for cross-component compilation.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

No new dependencies. Follow the approved plan/mockups and current PRD, with server-owned Pause/Resume semantics from completed plan 02. Every Gradle/device job runs in the OOM-safe scope with --no-daemon; serialize heavy jobs across concurrent workers.

## Input Dependencies

Own new PomodoroWidgetState/support persistence files, WidgetSync.kt/WidgetStore generalization, relevant new tests, any narrow session-start persistence hooks and Pomodoro-specific app wiring. Do not rewrite task 1 Timer widget state block. External hard dependency: archived plan 02 tasks 03/05/07/08, verified before implementation.

## Output Artifacts

The implementation and focused checks described above, task status and an Execution Result section containing exact proving commands, exit codes, JUnit counts and paths to evidence.

## Implementation Notes

<details>
<summary>Implementation procedure</summary>

Read the live TimerStateSource, TimerActions, PomodoroDefaultsSetting and alarm/ringing flows. Preserve captured revisions and account replacement safety. Keep sync generic over keys or state without creating duplicate polling/scheduling. Do not create UI/provider yet. Task 4 consumes this state. Six is justified by the indivisible holder/defaults/account-lifecycle integration; provider/actions UI and device proof are separate tasks.

You are not alone in the codebase. Do not revert other edits; accommodate concurrent changes. Read .ai/strikethroo/config/hooks/PRE_TASK_EXECUTION.md before implementation, and apply its meaningful-test RED/GREEN procedure. Do not create commits or PRs; the parent commits verified phases. Do not run update notices.

</details>

## Execution Result

Completed on 2026-10-29. External hard dependency verified before implementation: archived plan 02 tasks 03, 05, 07 and 08 all have `status: "completed"`. Their live mechanisms exist in `timerstate/TimerActions.kt` (direct online actions and captured revisions), `TimerStateSource.kt` (revision guard, account retirement, persisted arrival time), `TimerStateStore.kt` (`HeldTimerState.clockOffset`), `TimerEventStream.kt` and `TimerSyncService.kt` (one server event stream), `pomodoro/PomodoroScreenModel.kt` (server-owned session rendering/actions), and `PomodoroPhaseAlarm.kt` with `ServicePomodoroRinging.state` (existing planned-end alarm and ringing signal). Task 07 retains unchecked historical acceptance boxes; its completed status and live implementation, rather than those boxes alone, were verified.

### Implementation and phase 2 integration

- `widget/PomodoroWidgetState.kt` derives `NoAccount`, `Unsupported`, `Idle` and `Active`. `Active.session` retains the server's phase, upcoming phase, transition, completed count, number, cycle and per-session lengths; `Active.start/end` are corrected to the phone's clock. The `ended` flag changes at the corrected planned end or on the matching existing ringing signal. Keys hash JSON so tags containing separators cannot collide with different tag lists.
- `widget/PomodoroWidgetFieldsStore.kt` persists only entry fields and workspace, under the holder's random persisted account name. `newTimerState` seeds from the already persisted active session and remembers accepted session starts/edits before publication, covering direct action responses, 409 adoption, reads and SSE. Idle states retain the previous fields. Both account renewal and sign-out clear them after retiring the holder, so stale revisions and late retired-account responses cannot restore them.
- `TokklApplication.pomodoroWidgetState` follows current holder/account, defaults, capabilities, cached project/task labels and `pomodoroRinging.state`, with no network request, new scheduling or idle wakeup. It reads the current holder under its lock to avoid combining a previous account's state with a replacement account.
- `WidgetSync(keys = Flow<String>, ...)` supports independent providers while the existing `WidgetSync(state = { widgetState }, ...)` constructor retains Timer compatibility. `WidgetStore` keeps the Timer's original `shown` key and adds `POMODORO_KEY = "pomodoro_shown"`. Phase 2 should instantiate `app.pomodoroWidgetSync { /* provider updateAll */ }` once and call `start()` once; the provider is deliberately absent in this task. Its active buttons use the captured `account`, `revision` and `session.phase.start`; idle Start uses `entry.workspaceId`, `entry.fields` and all four `defaults`.
- Task 1's `WidgetState.kt`, `WidgetStateTest.kt`, Timer totals changes and Timer state block in `TokklApplication.kt` were left to their owner. Shared application edits are additive Pomodoro imports, holder persistence/lifecycle hooks and the new Pomodoro flow/factory.

### RED/GREEN evidence

Every Gradle command below ran with `systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- flock /tmp/tokkl-plan3-gradle.lock`, followed by a `bash -c` wrapper which redirected output and returned Gradle's exit code. JUnit copies were made before releasing the lock except the initial RED report, copied immediately after that command completed.

- RED state/fields: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.github.e0ipso.tokkl.widget.PomodoroWidgetStateTest"` exited 1. All 11 tests ran and failed with the expected `kotlin.NotImplementedError` from the API stubs; zero errors/skips. Log `/tmp/tokkl-plan3-task2-red.log`; JUnit `/tmp/tokkl-plan3-task2/red-junit/TEST-com.github.e0ipso.tokkl.widget.PomodoroWidgetStateTest.xml`. The complete failure inventory is the 11 test cases in that report, all isolated to this class. The first RED report was copied immediately after the command completed.
- GREEN state/fields: the same command exited 0, 11 tests, zero failures/errors/skips. Log `/tmp/tokkl-plan3-task2-green-state.log`; JUnit `/tmp/tokkl-plan3-task2/green-state-junit/TEST-com.github.e0ipso.tokkl.widget.PomodoroWidgetStateTest.xml`.
- RED independent synchronization: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.github.e0ipso.tokkl.widget.PomodoroWidgetSyncTest"` exited 1, 3 tests and 3 expected `kotlin.NotImplementedError` failures: `eachProviderKeepsItsOwnKeyAcrossChangesAndProcessRestart`, `failedStorageStillAllowsTheNextPhaseToDraw`, `aFailedDrawDoesNotRememberAKeyThatWasNeverDrawn`. Zero errors/skips. Log `/tmp/tokkl-plan3-task2-red-sync-rerun.log`; JUnit `/tmp/tokkl-plan3-task2/red-sync-junit/TEST-com.github.e0ipso.tokkl.widget.PomodoroWidgetSyncTest.xml`.
- GREEN integration: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.github.e0ipso.tokkl.widget.PomodoroWidgetStateTest" --tests "com.github.e0ipso.tokkl.widget.PomodoroWidgetSyncTest" --tests "com.github.e0ipso.tokkl.timerstate.TimerStateSourceTest"` exited 0, 29 tests: state/fields 13, synchronization 3, holder guards 13; zero failures/errors/skips. Log `/tmp/tokkl-plan3-task2-green-integration.log`; XML copies `/tmp/tokkl-plan3-task2/green-integration-junit/`.

### Final acceptance evidence

- `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.github.e0ipso.tokkl.widget.*" --tests "com.github.e0ipso.tokkl.pomodoro.*"` exited 0. JUnit reports contain **139 tests in 12 classes, zero failures, errors or skips**. Per-class counts: `PomodoroAlarmModelTest` 13, `PomodoroDefaultsSettingTest` 10, `PomodoroPageFullScreenStateTest` 1, `PomodoroPagePhaseActionsTest` 12, `PomodoroPermissionsTest` 4, `PomodoroPhaseAlarmTest` 13, `PomodoroRingingTest` 13, `PomodoroScreenModelTest` 18, `PomodoroSessionNotificationTest` 11, `PomodoroWidgetStateTest` 13, `PomodoroWidgetSyncTest` 3, `WidgetStateTest` 28. Log `/tmp/tokkl-plan3-task2-acceptance.log`; JUnit copies `/tmp/tokkl-plan3-task2/acceptance-junit/`. Normal report location `app/build/test-results/testDebugUnitTest/` is shared with concurrent runs; the copied evidence is specific to this run.
- `./gradlew --no-daemon :app:compileDebugAndroidTestKotlin` exited 0, `BUILD SUCCESSFUL`, 26 tasks up-to-date. Log `/tmp/tokkl-plan3-task2-androidtest-compile.log`. This is compilation evidence; no device tests or screenshots were run for this state-only task.
- `git diff --check` exited 0. No remaining task blockers. No commits, PRs, pushes, update checks or provider/UI changes were made. Phase completion and commits remain with the parent.

### Noteworthy events

- The initial synchronization RED attempt, `/tmp/tokkl-plan3-task2-red-sync.log`, exited 1 at Kotlin compilation because app wiring was added while the build still saw the preceding `WidgetStore` constructor. Both diagnostics were in `TokklApplication.kt` at the new call: `keyName` absent and `POMODORO_KEY` unresolved. The constructor/constants were added and the same isolated class rerun to the expected 3 RED failures above, then all 3 passed in GREEN. No unresolved compiler blocker remains from that attempt.
- `npx --no-install kenkeep lint --verbose` exited 0 with existing orphan/redirected-edge findings. `npx --no-install kenkeep freshness --verbose` also recorded existing stale guidance in `/tmp/tokkl-plan3-task2-kenkeep-freshness.log`. Named live mechanisms were checked directly; knowledge-base maintenance is outside this task.
