---
id: 4
group: "instrumented-reliability"
dependencies: [1, 2, 3]
status: "pending"
created: 2026-10-26
skills:
  - android-testing
complexity_score: 5
execution_profile: "standard-implementation"
---
# Verify kept methods and acceptance gates

## Objective
Verify the integrated fixes with focused API 31/36 method runs and required screenshots, audit all five verdicts, and record the post-merge main acceptance gate.

## Skills Required
android-testing

## Acceptance Criteria
- [ ] The kept RunningCheckServerTest, TimerServerTest, CalendarServerTest and SyncStatusFitTest methods pass once each on API 31 and 36, reusing matching evidence from task 3.
- [ ] If layout changed, run only affected screenshot methods supported by each AVD and inspect produced images; document API restrictions rather than fabricate results.
- [ ] Focused JVM red/green regression and related test evidence is recorded; test diffs contain no weakened assertions, retries, skips or ignores.
- [ ] Rebuild kenkeep index; lint passes; five verdicts and all commands, test counts, API levels, fingerprints, server image and log paths are recorded.
- [ ] After owner merge/push, gh run list --workflow ci.yml --branch main shows three consecutive green instrumented-tests runs on API 31 and 36; otherwise leave this external gate explicitly pending and do not claim final acceptance.

## Technical Requirements
Follow plan 1, its scope constraints, and method-only verification. 

## Input Dependencies
Tasks 1, 2, 3 completed.

## Output Artifacts
Scoped changes and durable per-task verdict/verification evidence for phase review.

## Implementation Notes
Write a few tests, mostly integration. Write tests for custom business logic and algorithms, critical user workflows and data transformations, core edge cases and errors, integration points, and complex validation or calculations. Do not test third-party or framework behavior, simple CRUD, trivial getters/setters or static configuration, or obvious functionality. Combine related scenarios; favor critical-path integration coverage over per-method tests.

<details>
<summary>Execution guidance</summary>

Own integrated verification evidence and this task file only. Coordinate with the parent before starting expensive tests. Use the runner filter for the exact fqcn#method from the plan, never an entire class or full suite. Run one AVD at a time. Reuse a prior pass only when all checked inputs, server image and test configuration match. Use the OOM scope and memory gate above, the repository runner and --no-daemon. Inspect receipts for real executed counts, not only Gradle exit status. Do not repeat green runs without code changes or unresolved concerns. The required main acceptance gate needs the owner merge; do not merge, push, edit/close issue #14, or create repeated full CI runs autonomously. If it is pending, report the external dependency and keep the plan active. Do not commit or edit the plan body.

You are not alone in the codebase. Do not revert others' edits; accommodate concurrent work within your assigned ownership.
</details>
