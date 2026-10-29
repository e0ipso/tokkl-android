---
id: 4
group: "pomodoro-widget"
dependencies: [2]
status: "pending"
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

- [ ] Register PomodoroWidget, receiver, provider info, picker name/description and zero update period, using task 2 independent state/sync. Show no-account/unsupported/idle/session states.
- [ ] Match approved 2×1/3×2/4×3 mockups with phase words/colours, countdown/progress, Pomodoro N of M and cycle dots, entry fields/chips, Next/End, and idle defaults/Start/Edit. Omit mocked session duration/saved count line absent from server contract.
- [ ] Countdown uses planned end corrected by server offset; phase-end redraw switches to upward overtime, full running-colour bar, finished-phase words and filled Next. Preserve accessibility and largest font/minimum-size fitting.
- [ ] Next/End/Start use shipped TimerActions with captured displayed revision and defaults/last fields. Adopt 409 state and show conflict/offline messages, never queue or retry actions. Body/Edit opens Pomodoro with sign-in guarding.
- [ ] Run :app:testDebugUnitTest --tests com.github.e0ipso.tokkl.widget.* and :app:compileDebugAndroidTestKotlin :app:lintDebug in the OOM-safe scope; expect nonzero passing tests, successful compile and no lint errors. Add focused custom countdown/action tests with RED/GREEN evidence. Before phase 2 commit, run focused Pomodoro layout checks on API36 and record/review its screenshot references for three sizes and both themes/font scales; task 5 expands workflow proof.

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
