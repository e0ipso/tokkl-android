---
id: 6
group: "widget-documentation"
dependencies: [5]
status: "completed"
created: 2026-10-29
skills:
  - technical-writing
complexity_score: 3
execution_profile: "docs-and-config"
---
# Document verified Timer and Pomodoro widgets

## Objective

Update project scope and curated widget knowledge to the behavior verified by task 5.

## Skills Required

technical-writing.

## Acceptance Criteria

- [x] Update PRD N3 for three Timer sizes/day report/large-only templates, add Pomodoro provider row, section 6 built list and battery/account-midnight/online actions rules.
- [x] Rewrite Timer widget kenkeep leaf, add Pomodoro widget node and update screens index using supported kenkeep mechanisms where appropriate. Preserve node schema and repair only touched cross-edges.
- [x] Update CLAUDE.md widget sentence and any linked AGENTS guidance that describes changed behavior. Clarify that ordinary queue changes work offline while server Pause/Resume and session actions need connection.
- [x] Run npx kenkeep lint --verbose and inspect changed-node findings, plus git diff --check and documentation assertions in npm test if relevant. Expect no newly introduced lint findings or whitespace errors. Record inherited kenkeep findings separately.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

No new dependencies. Follow the approved plan/mockups and current PRD, with server-owned Pause/Resume semantics from completed plan 02. Every Gradle/device job runs in the OOM-safe scope with --no-daemon; serialize heavy jobs across concurrent workers.

## Input Dependencies

Own docs/PRD.md, CLAUDE.md and widget-related kenkeep nodes/index only. Use actual task 5 evidence and implementation to describe shipped behavior.

## Output Artifacts

The implementation and focused checks described above, task status and an Execution Result section containing exact proving commands, exit codes, JUnit counts and paths to evidence.

## Implementation Notes

<details>
<summary>Implementation procedure</summary>

No new code tests are warranted for prose; verify claims against live code and acceptance evidence. Read existing widget leaf and kenkeep schema before edits. Do not mark unsupported or unproved behavior built.

You are not alone in the codebase. Do not revert other edits; accommodate concurrent changes. Read .ai/strikethroo/config/hooks/PRE_TASK_EXECUTION.md before implementation, and apply its meaningful-test RED/GREEN procedure. Do not create commits or PRs; the parent commits verified phases. Do not run update notices.

</details>

## Execution Result

- Read the task 5 terminal evidence at `evidence/phase3/task5/README.md` and `evidence/phase3/documentation-handoff-notes.md`, and checked widget source and provider XML. Task 5 reports 736 API 36 cases passing across six CI shards, 1,069 JVM cases, compile and lint, 11 npm tests, and 208 screenshot comparisons with 40 reviewed reference updates. API 31 remains deferred to CI; no physical battery measurement is claimed.
- `npx kenkeep index rebuild` — exit 0; regenerated 16 indexes and GRAPH from 91 nodes.
- `npx kenkeep lint --verbose` — exit 0; no findings on the changed widget nodes. One existing orphan and 13 existing redirected edges remain elsewhere.
- `git diff --check` — exit 0. No new code tests or device runs were warranted for prose; npm tests cover release and commit tooling, not documentation assertions.
- `readlink CLAUDE.md` — exit 0, `AGENTS.md`; the tracked symlink is preserved.
