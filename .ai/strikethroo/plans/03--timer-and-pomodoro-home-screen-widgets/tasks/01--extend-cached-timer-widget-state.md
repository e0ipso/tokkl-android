---
id: 1
group: "timer-widget"
dependencies: []
status: "completed"
created: 2026-10-29
skills:
  - kotlin
  - data-modeling
complexity_score: 5
execution_profile: "complex-architecture"
---
# Extend cached Timer widget state with day totals

## Objective

Give the Timer widget its project colours, account-local day report, formatted durations and cache-driven rollover without adding wakeups.

## Skills Required

kotlin, data-modeling.

## Acceptance Criteria

- [x] Extend WidgetState with running/paused project colour, full day report, billable seconds, entry count, sorted per-project stopped seconds, running marker and templates. Hash every rendered field.
- [x] Reuse TimerTotals day/start/deduplication rules, including local replacement rows, overnight entries, account timezone, DST and no-project grouping. Compare widget and Timer page totals in tests; avoid pause-chain double counting.
- [x] Wire cached entries, settings, mappings and a clock snapshot into TokklApplication.widgetState. Redraw on app open/cache changes and existing running check; add no idle alarm or polling.
- [x] Run ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.WidgetStateTest' --tests 'com.github.e0ipso.tokkl.timer.TimerTotalsTest'; expect nonzero tests and zero failures.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

No new dependencies. Follow the approved plan/mockups and current PRD, with server-owned Pause/Resume semantics from completed plan 02. Every Gradle/device job runs in the OOM-safe scope with --no-daemon; serialize heavy jobs across concurrent workers.

## Input Dependencies

Own WidgetState.kt, TimerTotals.kt shared grouping helpers, WidgetStateTest.kt and only the Timer widget state block/imports of TokklApplication.kt. Task 2 owns new Pomodoro state/sync code and its app wiring. Preserve current widget compilation until task 3 replaces layouts.

## Output Artifacts

The implementation and focused checks described above, task status and an Execution Result section containing exact proving commands, exit codes, JUnit counts and paths to evidence.

## Implementation Notes

<details>
<summary>Implementation procedure</summary>

Read the plan and relevant kenkeep data/screens leaves. Extract shared day/count helpers rather than copying TimerTotals logic. Running total uses entry elapsed once, distinct from pause-chain display accumulation. Keep the hash stable between meaningful redraw events. Add focused RED/GREEN cases before custom logic.

You are not alone in the codebase. Do not revert other edits; accommodate concurrent changes. Read .ai/strikethroo/config/hooks/PRE_TASK_EXECUTION.md before implementation, and apply its meaningful-test RED/GREEN procedure. Do not create commits or PRs; the parent commits verified phases. Do not run update notices.

</details>

## Execution Result

Completed on 2026-10-29. Changes are limited to `WidgetState.kt`, shared helpers in `TimerTotals.kt`, `WidgetStateTest.kt`, and the Timer widget state block and its imports in `TokklApplication.kt`. Concurrent task 2 changes remain intact. No commits, PRs, pushes, update checks or device runs were performed.

`WidgetState.Ready.day` supplies the account-local date, stopped and stopped-billable seconds, entry count, per-project stopped seconds and short formatted durations, project colours, running markers and the running segment. `totalSeconds(now)`, `billableSeconds(now)`, `formattedTotal(now)` and `formattedBillable(now)` provide the render-time values. For the day Chronometer, use `day.running.start` with `day.stoppedSeconds` as the accumulated time. The regular running timer still uses its existing pause-chain total and server-adjusted clock. The day report counts real cached segments once, using the same helpers as the Timer page. The key includes every state field and remains stable as the running clock ticks within the same day. Existing templates and provider APIs remain available for phase 2.

The Timer flow follows cached entries, ID mappings, settings and catalog changes. Activity-start events also refresh it when opening offline. It observes the existing `running-check` work's state changes to take a new day snapshot even when the check changes no cached row. It schedules no work or alarm and adds no polling.

Final proving command, run from `/home/e0ipso/workspace/apps/tokkl-android`:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- flock /tmp/tokkl-plan3-gradle.lock bash -c '
./gradlew --no-daemon :app:testDebugUnitTest --tests com.github.e0ipso.tokkl.widget.WidgetStateTest --tests com.github.e0ipso.tokkl.timer.TimerTotalsTest :app:compileDebugAndroidTestKotlin
result=$?
mkdir -p /tmp/tokkl-plan3-task1-final
cp app/build/test-results/testDebugUnitTest/TEST-com.github.e0ipso.tokkl.widget.WidgetStateTest.xml app/build/test-results/testDebugUnitTest/TEST-com.github.e0ipso.tokkl.timer.TimerTotalsTest.xml /tmp/tokkl-plan3-task1-final/
exit "$result"
' > /tmp/tokkl-plan3-task1-final.log 2>&1
```

Exit code **0**, `BUILD SUCCESSFUL in 48s`. Android test Kotlin compilation passed, which also compiled the existing provider and main app wiring. No instrumented tests were executed.

| Suite | Tests | Failures | Errors | Skipped | Preserved JUnit evidence |
|---|---:|---:|---:|---:|---|
| WidgetStateTest | 28 | 0 | 0 | 0 | `/tmp/tokkl-plan3-task1-final/TEST-com.github.e0ipso.tokkl.widget.WidgetStateTest.xml` |
| TimerTotalsTest | 15 | 0 | 0 | 0 | `/tmp/tokkl-plan3-task1-final/TEST-com.github.e0ipso.tokkl.timer.TimerTotalsTest.xml` |
| Total | 43 | 0 | 0 | 0 | `/tmp/tokkl-plan3-task1-final.log` |

RED/GREEN evidence, each Gradle invocation serialized with the same lock and OOM-safe scope:

| Check | Gradle arguments after `./gradlew --no-daemon` | Exit / result | Evidence |
|---|---|---|---|
| RED project colour | `:app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.WidgetStateTest.projectColourChangesRedrawRunningAndPausedWidgets'` | 1; 1 test, 1 assertion failure because changing colour left the key unchanged | `/tmp/tokkl-plan3-task1-red-colour.log`, `/tmp/tokkl-plan3-task1-red-colour/TEST-com.github.e0ipso.tokkl.widget.WidgetStateTest.xml` |
| First colour GREEN attempt | `:app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.WidgetStateTest.projectColourChangesRedrawRunningAndPausedWidgets'` | 1; test compilation blocked by concurrent task 2's not-yet-implemented Pomodoro model references; no tests executed | `/tmp/tokkl-plan3-task1-green-colour.log` |
| RED day report | `:app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.WidgetStateTest.dayReportMatchesTimerTotalsWithMappedRowsBillableTimeAndNoProject'` | 1; 1 test, 1 failure because the day report was absent | `/tmp/tokkl-plan3-task1-red-day.log`, lines 59–62; its JUnit output was replaced by the other serialized run before a copy could be taken |
| GREEN day and colour | `:app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.WidgetStateTest' --tests 'com.github.e0ipso.tokkl.timer.TimerTotalsTest'` | 0; 27 WidgetState tests and 15 TimerTotals tests, 0 failures/errors/skips | `/tmp/tokkl-plan3-task1-green-state.log`, both JUnit XML files in `/tmp/tokkl-plan3-task1-green-state/` |
| RED mapped running row | `:app:testDebugUnitTest --tests com.github.e0ipso.tokkl.widget.WidgetStateTest.aMappedRunningRowKeepsTheLocalContributionAndUnassignedRunningMarker` | 1; 1 test, 1 assertion failure, expected 100 seconds but got 0 | `/tmp/tokkl-plan3-task1-red-running.log`, `/tmp/tokkl-plan3-task1-red-running/TEST-com.github.e0ipso.tokkl.widget.WidgetStateTest.xml` |
| GREEN mapped running row | `:app:testDebugUnitTest --tests com.github.e0ipso.tokkl.widget.WidgetStateTest.aMappedRunningRowKeepsTheLocalContributionAndUnassignedRunningMarker` | 0; 1 test, 0 failures/errors/skips | `/tmp/tokkl-plan3-task1-green-running.log`, `/tmp/tokkl-plan3-task1-green-running/TEST-com.github.e0ipso.tokkl.widget.WidgetStateTest.xml` |

The final run follows the refactor and includes all new regressions. `git diff --check` also exited 0. Remaining task 1 blockers: none. Parent-owned phase gates and task 5's device rollover/layout proof remain outside this task.

## Noteworthy Events

- [2026-10-29] Live `RunningCheckWorker` still performs the v9 current-entry fallback on TimerState-capable servers too. `runningCheckWrites` suppresses an equal upsert, so the plan's assumption that every check produces a cache invalidation is false. Observing existing WorkManager state transitions in the owned Timer block handles rollover without changing the worker or adding wakeups.
- [2026-10-29] The first GREEN compile overlapped task 2's RED tests. All diagnostics came from its missing Pomodoro model/remembered-fields APIs. Those files were left untouched; once its implementation appeared, focused task 1 runs compiled and passed.
- [2026-10-29] A separate focused regression exposed loss of running-day time during local-to-server ID replacement. The report now takes the effective cached running row after shared deduplication rather than matching only the controls' already-remapped ID.
- [2026-10-29] Required `npx kenkeep lint --verbose` and `npx kenkeep freshness --verbose` both exited 0. Existing knowledge diagnostics include an orphan node, redirected edges and 28 potentially stale nodes. No knowledge-base files were changed within this task's ownership.
