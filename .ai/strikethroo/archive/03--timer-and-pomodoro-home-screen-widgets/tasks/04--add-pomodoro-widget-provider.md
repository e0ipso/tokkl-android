---
id: 4
group: "pomodoro-widget"
dependencies: [2]
status: "completed"
created: 2026-10-29
skills:
  - jetpack-glance
  - android-timers
complexity_score: 6
complexity_notes: "The task owns one integrated deliverable; state, provider layout, device proof and documentation are separate tasks. Acceptance commands and ownership bound its integration."
execution_profile: "complex-architecture"
---
# Add Pomodoro provider layouts and server actions

## Objective

Add the picker-visible Pomodoro widget with three layouts, server countdown/overtime and online session actions.

## Skills Required

jetpack-glance, android-timers.

## Acceptance Criteria

- [x] Register PomodoroWidget, receiver, provider info, picker name/description and zero update period, using task 2 independent state/sync. Show no-account/unsupported/idle/session states.
- [x] Match approved 2×1/3×2/4×3 mockups with phase words/colours, countdown/progress, Pomodoro N of M and cycle dots, entry fields/chips, Next/End, and idle defaults/Start/Edit. Omit mocked session duration/saved count line absent from server contract.
- [x] Countdown uses planned end corrected by server offset; phase-end redraw switches to upward overtime, full running-colour bar, finished-phase words and filled Next. Preserve accessibility and largest font/minimum-size fitting.
- [x] Next/End/Start use shipped TimerActions with captured displayed revision and defaults/last fields. Adopt 409 state and show conflict/offline messages, never queue or retry actions. Body/Edit opens Pomodoro with sign-in guarding.
- [x] Run :app:testDebugUnitTest --tests com.github.e0ipso.tokkl.widget.* and :app:compileDebugAndroidTestKotlin :app:lintDebug in the OOM-safe scope; expect nonzero passing tests, successful compile and no lint errors. Add focused custom countdown/action tests with RED/GREEN evidence. Before phase 2 commit, run focused Pomodoro layout checks on API36 and record/review its screenshot references for three sizes and both themes/font scales; task 5 expands workflow proof.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

No new dependencies. Follow the approved plan/mockups and current PRD, with server-owned Pause/Resume semantics from completed plan 02. Every Gradle/device job runs in the OOM-safe scope with --no-daemon; serialize heavy jobs across concurrent workers.

## Input Dependencies

Own new PomodoroWidget.kt, PomodoroWidgetActions.kt, Pomodoro provider XML/resources, manifest receiver and any narrow Pomodoro-only state/sync/app wiring, plus new Pomodoro widget androidTests and screenshot references. Task 3 owns shared NativeWidget helper changes; use its generic provider support or additive non-overlapping helpers. Avoid task 3 Timer layout/Today navigation files; reuse the established Pomodoro navigation extra.

## Output Artifacts

The implementation and focused checks described above, task status and an Execution Result section containing exact proving commands, exit codes, JUnit counts and paths to evidence.

## Implementation Notes

<details>
<summary>Implementation procedure</summary>

Read mockup HTML and task 2 state API. Reuse native chronometer embedding; coordinate shared utility extraction through additive edits without replacing task 3 work. External hard dependency remains plan 02 tasks 03/05/07/08. Six is justified by a single responsive provider with captured-revision actions; model/storage and device validation are separate.

You are not alone in the codebase. Do not revert other edits; accommodate concurrent changes. Read .ai/strikethroo/config/hooks/PRE_TASK_EXECUTION.md before implementation, and apply its meaningful-test RED/GREEN procedure. Do not create commits or PRs; the parent commits verified phases. Do not run update notices.

</details>


## Execution Result

Completed on 2026-10-30, Europe/Madrid. Task 2's completed handoff and shipped state/action interfaces were read before implementation. No commits, PRs or pushes were made; the parent owns phase gates and the phase commit.

### Implementation

- Added `PomodoroWidget`, its receiver/provider XML, picker strings and zero update period. It renders no-account, unsupported, idle and active/finished work/rest states at all three approved sizes, plus the 110x40 resize minimum.
- Reused the independent task 2 state flow and started exactly one `pomodoroWidgetSync { PomodoroWidget().updateAll(this) }` follower from application startup. Shared application/manifest edits are additive: two application lines and the new receiver block. Timer provider, `WidgetActions.kt`, shared layout constants, `NativeWidget` and navigation files remain with task 3.
- Body and Edit reuse `WidgetNavigation.OPEN_POMODORO`. Actions capture account, server phase start and displayed revision; idle Start captures workspace, fields and all four defaults. The shipped `TimerActions` sends once and adopts server/409 state. Same-phase edits retain the displayed revision; a different phase or account sends nothing. Offline/conflict messages use `showTimerActionFailure`. No queue, retries or optimistic ring/state changes were added.
- Native clocks use corrected start/end boundaries. The existing ringing/state signal selects finished words, upward overtime and a full running-colour progress bar. No polling, new alarm or idle wakeup was added. Layout sections stay within Glance's child limit and reserve complete action targets. The server does not supply session duration/saved count, so that mocked line is omitted.

### Verification and evidence

Evidence root: [task4 evidence](../evidence/phase2/task4/README.md). Machine summary: [verification.json](../evidence/phase2/task4/verification.json).

Every Gradle/device invocation used `systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- flock /tmp/tokkl-plan3-gradle.lock`, with `./gradlew --no-daemon`. All accepted JUnit/report copies were made while holding the lock. No `--stop` was used.

- RED: `:app:testDebugUnitTest --tests "com.github.e0ipso.tokkl.widget.PomodoroWidgetActionsTest"`, exit 1, nine expected `NotImplementedError` failures, zero errors/skips. `red.log`, `red-junit/`.
- GREEN: the same selector, exit 0, nine tests, zero failures/errors/skips. `green.log`, `green-junit/`.
- Final required checks: `:app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.*' :app:compileDebugAndroidTestKotlin :app:lintDebug`, exit 0. **56 tests**, zero failures/errors/skips; successful Android test compilation; **zero lint errors**. Per-class counts: `PomodoroWidgetActionsTest` 9, `PomodoroWidgetStateTest` 13, `PomodoroWidgetSyncTest` 3, `WidgetStateTest` 28, `TimerWidgetLayoutTest` 3. Logs/JUnit: `acceptance.log`, `acceptance-junit/`, `lint-results-debug.xml`.
- Final device command: the prefix above followed by `bash .ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task4/device-proof.sh api36-reference-comparison`. The helper uses only `TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh` with the exact five method selectors documented in the evidence README. Exit 0; **five actual tests**, zero failures/errors/skips. Four methods compare **144 reviewed references** for nine states, four bounds, both themes and font scales 1.0/2.0; the fifth proves corrected native countdown/overtime bases and ticking without provider updates. `api36-reference-comparison.log`, `api36-reference-comparison/junit/`.
- Both final runs have unchanged before/after source/reference fingerprints. Recorded asset hashes are in `references.sha256`; reviewed captures and contact sheets are in `final-captures/` and `final-captures-review/`. `git diff --check` passed.

The full workflow/API31 matrix remains task 05. At the extreme 110x40 bound, controls use 36 dp to fit that cell; the three approved sizes retain 48 dp and pass ATF plus complete-bounds checks. Entry names may ellipsize, while phase words at the approved sizes are checked for ellipsis.

### Noteworthy events

The evidence README inventories transient compile/lint failures and the layout defects found and corrected during focused runs. Two interrupted attempts are explicitly excluded from final evidence: an obsolete focused run deliberately stopped after identifying dropped action rows, and `api36-bounds` unexpectedly terminated with exit 143 before complete reports were copied. The parent confirmed it did not cancel the latter; its cause remains unknown. The focused retry and final comparison completed successfully. No other worker's scope was stopped. Kenkeep lint/freshness recorded existing maintenance findings without changing the knowledge base. No remaining task 04 blocker.
