---
id: 2
group: "instrumented-reliability"
dependencies: []
status: "completed"
created: 2026-10-26
skills:
  - android-testing
complexity_score: 3
execution_profile: "standard-implementation"
---
# Correct the test refresh wait and remove duplicate coverage

## Objective
Correct TimerServerTest.drainUntilEmpty to wait for a refresh begun after sending, and remove only CertificatePromptTest.pinnedCertificateSignsInWithoutAPrompt with code used solely by it.

## Skills Required
android-testing

## Acceptance Criteria
- [x] TimerServerTest.drainUntilEmpty uses refreshAfterChange().join(); assertions and other behavior remain strict.
- [x] The redundant pinned-sign-in method is absent; the CertificatePromptTest class and its other methods remain.
- [x] Run rg over source and docs to confirm no live reference to the removed method; verify docs/ACCESSIBILITY.md still describes the class correctly.
- [x] Record keep-and-fix-in-test and delete verdicts and causes; inspect the test diff for weakened assertions, retries, skips or ignores.

## Technical Requirements
Follow plan 1, its scope constraints, and method-only verification. 

## Input Dependencies
Existing repository and plan 1; no task prerequisites.

## Output Artifacts
Scoped changes and durable per-task verdict/verification evidence for phase review.

## Implementation Notes
Write a few tests, mostly integration. Write tests for custom business logic and algorithms, critical user workflows and data transformations, core edge cases and errors, integration points, and complex validation or calculations. Do not test third-party or framework behavior, simple CRUD, trivial getters/setters or static configuration, or obvious functionality. Combine related scenarios; favor critical-path integration coverage over per-method tests.

<details>
<summary>Execution guidance</summary>

Own only TimerServerTest.kt and CertificatePromptTest.kt. Read their helper usages before removing imports. The existing TimerAcceptanceFixture change is the precedent for refreshAfterChange. No new test is warranted for this helper correction and redundant-test deletion. Do not run Gradle or device tests; the coordinator will batch the selected methods after all changes. Do not commit or edit the plan body. Record static verification evidence in this task.

You are not alone in the codebase. Do not revert others' edits; accommodate concurrent work within your assigned ownership.
</details>

## Execution Evidence

### Verdicts and causes

- **Keep and fix in test — `TimerServerTest.startingWhileAnotherEntryRunsLeavesOneRunning`:** `drainUntilEmpty` previously joined `refresh()`, which can return a reconnect refresh already in progress whose server read preceded the phone's POST. It now joins `refreshAfterChange()`, so the asserted cache state comes from a refresh begun after queue delivery. All surviving assertions and the helper's queue-drain deadline remain unchanged.
- **Delete — `CertificatePromptTest.pinnedCertificateSignsInWithoutAPrompt`:** the method was redundant with the surviving trust-prompt flow, the pinned-certificate API test, and JVM trust/sign-in coverage. Its observed failure made no request and may have been a submit tap during IME/form animation, but that UI cause remains speculative. Only the method was deleted; no helper or import was solely used by it.

### Static verification

- `rg -n -A8 "private fun drainUntilEmpty" app/src/androidTest/java/com/github/e0ipso/tokkl/timer/TimerServerTest.kt` showed line 560 as `runBlocking { app.accountSettings.refreshAfterChange().join() }`, with the existing drain loop and deadline unchanged.
- `rg -n "pinnedCertificateSignsInWithoutAPrompt" app/src docs` returned no matches (`removed method absent from app/src and docs`).
- `rg -n "CertificatePromptTest" docs/ACCESSIBILITY.md` returned the existing class-level matrix reference at line 58, which remains accurate.
- Inspection of the zero-context scoped diff found no changed assertion, retry, `@Ignore`, or `FlakyTest` lines.
- `git diff --check -- app/src/androidTest/java/com/github/e0ipso/tokkl/timer/TimerServerTest.kt app/src/androidTest/java/com/github/e0ipso/tokkl/signin/CertificatePromptTest.kt` exited 0 with no output.
- Scoped diff stat: 2 files changed, 1 insertion, 10 deletions. No Gradle or device test was run, per task guidance; focused device verification is deferred to the coordinator.

## Noteworthy Events

- [2026-10-26] Followed the direct-implementation path because the task explicitly identifies the change as a test-helper timing correction plus deletion of redundant coverage and states that no new test is warranted.
