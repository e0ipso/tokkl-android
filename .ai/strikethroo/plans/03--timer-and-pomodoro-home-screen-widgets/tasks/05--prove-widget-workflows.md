---
id: 5
group: "widget-verification"
dependencies: [3, 4]
status: "pending"
created: 2026-10-29
skills:
  - android-testing
  - screenshot-testing
complexity_score: 7
complexity_notes: "The task owns one integrated deliverable; state, provider layout, device proof and documentation are separate tasks. Acceptance commands and ownership bound its integration."
execution_profile: "complex-architecture"
---
# Prove widget workflows on API 31 and 36

## Objective

Prove the plan Success Criteria and Self Validation on both supported emulator APIs and record new visual references.

## Skills Required

android-testing, screenshot-testing.

## Acceptance Criteria

- [ ] Update WidgetServerTest, WidgetLargeTextTest and NativeWidget for both providers and all three sizes. Assert ticking day total against Timer page, project grouping/no project, running/paused/idle, largest text/accessibility, Today navigation and running-check midnight rollover.
- [ ] Prove ordinary offline Start/Stop/templates and online Pause/Resume, plus Pomodoro second-provider registration, remote session arrival/countdown, phase-end overtime within a second, Next/End/idle Start with defaults/last fields, conflict and offline messages. Test app behavior rather than Glance/launcher internals.
- [ ] Verify the API36 screenshot references recorded in phase 2, and update any references for further appearance fixes in the same commit as those fixes. If recording needs the dedicated screenshot runner, follow existing scripts and test practices.
- [ ] Run focused widget checks on tokkl-api36 and tokkl-api31, inventory and isolate every failure before broader runs, then full instrumented/Compose and JVM suites plus lint/npm tests. Use only scripts/instrumented-tests.sh for device tests, OOM-safe scope and serialized heavy jobs. Save commands, JUnit counts, runner receipts and screenshots under plan evidence.
- [ ] Execute all seven plan Self Validation steps and map each success criterion to concrete evidence. Zero failing tests and successful lint/build required; never treat another agent report or old plan evidence as proof.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

No new dependencies. Follow the approved plan/mockups and current PRD, with server-owned Pause/Resume semantics from completed plan 02. Every Gradle/device job runs in the OOM-safe scope with --no-daemon; serialize heavy jobs across concurrent workers.

## Input Dependencies

Own widget androidTest files, screenshot references and plan evidence. Small implementation fixes discovered by tests are permitted but document ownership and preserve others edits. All provider/state work must precede this task.

## Output Artifacts

The implementation and focused checks described above, task status and an Execution Result section containing exact proving commands, exit codes, JUnit counts and paths to evidence.

## Implementation Notes

<details>
<summary>Implementation procedure</summary>

Write tests for custom business logic, critical user workflows, data transformations, edge/error conditions and component integration. Do not test third-party/framework behavior, simple CRUD, trivial getters/setters or static configuration. Combine related scenarios; favor integration/critical paths. Seven is justified by the indivisible end-to-end acceptance matrix for both widgets; implementation tasks already own focused tests and this task owns runtime proof. Use runner supported filters and secure credential files, never print secrets. Read testing root and matching leaves before device work.

You are not alone in the codebase. Do not revert other edits; accommodate concurrent changes. Read .ai/strikethroo/config/hooks/PRE_TASK_EXECUTION.md before implementation, and apply its meaningful-test RED/GREEN procedure. Do not create commits or PRs; the parent commits verified phases. Do not run update notices.

</details>
