---
id: 3
group: "timer-widget"
dependencies: [1]
status: "pending"
created: 2026-10-29
skills:
  - jetpack-glance
  - android-navigation
complexity_score: 6
complexity_notes: "The task owns one integrated deliverable; state, provider layout, device proof and documentation are separate tasks. Acceptance commands and ownership bound its integration."
execution_profile: "complex-architecture"
---
# Build Timer widget layouts and Today navigation

## Objective

Replace the old favorites rows with the approved Timer 2×1, 3×2 and 4×3 layouts and open Reports on Today.

## Skills Required

jetpack-glance, android-navigation.

## Acceptance Criteria

- [ ] Implement LocalSize breakpoints from approved mockups: strip with project/description/clock and one Stop/Resume/Start; medium Today ticking total, stacked bar and fitting legend; large Total/Billable/Entries, project bars, Today link and template chip row.
- [ ] Support running, paused, idle and no-account states in light/dark and font scale 1.0/largest. At 110×40 dp degrade to clock/button; drop project rows before large-only template chips. Cap stacked bar at five segments and fold remainder into Other.
- [ ] Use native ticking Chronometers with explicit measured width, account-format static durations, project dots/bars and accessible words/descriptions. Keep pause/resume server-online semantics from shipped plan 02; ordinary start/stop/templates keep their offline queue behavior.
- [ ] Lower provider minimums to allow 2×1, keep updatePeriodMillis zero, add resource strings and Today intent handling through MainActivity/Reports model. Body/Today taps route correctly even with a retained activity; no-account opens sign-in.
- [ ] Add meaningful layout/breakpoint/navigation checks and update directly affected old tests. Run :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:lintDebug in the OOM-safe scope; expect build success, zero test failures and no lint errors. Before phase 2 commit, run focused Timer widget layout checks on API36 and record/review Timer screenshot references for three sizes and both themes/font scales. Task 5 expands workflow proof and full-suite validation.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

No new dependencies. Follow the approved plan/mockups and current PRD, with server-owned Pause/Resume semantics from completed plan 02. Every Gradle/device job runs in the OOM-safe scope with --no-daemon; serialize heavy jobs across concurrent workers.

## Input Dependencies

Own TokklWidget.kt, WidgetActions.kt Timer actions/navigation, Timer provider XML, widget_strings.xml, shared WidgetLayout constants, MainActivity/Reports Today extra wiring and directly affected tests, Timer widget screenshot tests/references and shared NativeWidget host helpers. Keep shared NativeWidget changes generic/additive so task 4 can bind its provider. Task 4 owns only the new Pomodoro provider/resources and receiver manifest entry.

## Output Artifacts

The implementation and focused checks described above, task status and an Execution Result section containing exact proving commands, exit codes, JUnit counts and paths to evidence.

## Implementation Notes

<details>
<summary>Implementation procedure</summary>

Read both Timer mockup HTML and live theme/components. Use the state from task 1. Reuse existing actions and Chronometer measurement. Do not follow the outdated plan assumption that Pause/Resume work offline; plan 02/PRD require a connection. Document this scope reconciliation. Six is justified by the single size-adaptive layout/navigation deliverable; state and device tests are separate.

You are not alone in the codebase. Do not revert other edits; accommodate concurrent changes. Read .ai/strikethroo/config/hooks/PRE_TASK_EXECUTION.md before implementation, and apply its meaningful-test RED/GREEN procedure. Do not create commits or PRs; the parent commits verified phases. Do not run update notices.

</details>
