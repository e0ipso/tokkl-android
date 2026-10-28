---
id: 3
group: "instrumented-reliability"
dependencies: []
status: "failed"
created: 2026-10-26
skills:
  - compose
  - android-testing
complexity_score: 6
complexity_notes: "Keep the diagnosis and its layout/timing correction together; the existing strict bounds assertion is the acceptance oracle."
execution_profile: "standard-implementation"
---
# Triage and resolve sync status overlap

## Objective
Determine the cause of SyncStatusFitTest API 31 overlap, then fix the layout or timing while preserving the guarded behavior and strict assertions, or justify deletion only if no app behavior is covered.

## Skills Required
compose, android-testing

## Acceptance Criteria
- [ ] Inspect available CI artifact instrumented-reports-api-31-shard-1 from run 40348957121 and relevant main history; record unavailable evidence honestly.
- [ ] Establish a concrete layout or timing cause using code and the failing bounds; preserve strict assertions with no retries/skips.
- [ ] Run only the single failing SyncStatusFitTest method on API 31 as needed for diagnosis and targeted verification, with durable logs.
- [ ] Record verdict and cause and update a relevant sync-status kenkeep leaf if layout changes; identify affected screenshot methods for coordinator verification.

## Technical Requirements
Follow plan 1, its scope constraints, and method-only verification. Keep the diagnosis and its layout/timing correction together; the existing strict bounds assertion is the acceptance oracle.

## Input Dependencies
Existing repository and plan 1; no task prerequisites.

## Output Artifacts
Scoped changes and durable per-task verdict/verification evidence for phase review.

## Implementation Notes
Write a few tests, mostly integration. Write tests for custom business logic and algorithms, critical user workflows and data transformations, core edge cases and errors, integration points, and complex validation or calculations. Do not test third-party or framework behavior, simple CRUD, trivial getters/setters or static configuration, or obvious functionality. Combine related scenarios; favor critical-path integration coverage over per-method tests.

<details>
<summary>Execution guidance</summary>

Own SyncStatusFitTest.kt and the shared sync-status/navigation layout files it proves need a change, plus the relevant design/screens kenkeep leaf if it exists. Read the plan triage section and relevant kenkeep before testing. Fetch CI evidence via gh without printing .env. Never weaken assertions, add retries, extend timeouts speculatively or skip tests. Existing regression is the RED test; no extra framework test is needed. Keep device tests to the affected method; no classes or suites. All builds/device runs use systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh and ./gradlew --no-daemon or scripts/instrumented-tests.sh. The coordinator serializes device work. Do not commit, rebuild the shared index, or edit the plan body. Record exact runs and outcomes here.

You are not alone in the codebase. Do not revert others' edits; accommodate concurrent work within your assigned ownership.
</details>

## Noteworthy Events

- 2026-10-26: Kept `SyncStatusFitTest.theStatusFitsAboveTheBarAndOpensTheNeedsAttentionList`: it protects the user-visible requirement that a multiline sync action remain above the compact navigation bar at Android's 200% font and largest display settings. Its assertions were not changed, retried, skipped or weakened.
- 2026-10-26: Downloaded `instrumented-reports-api-31-shard-1` from main run `40348957121` (`5b8e906`) with `gh run download 40348957121 -n instrumented-reports-api-31-shard-1`. The report contains 93 tests and one failure. The exact assertion was `needs attention label DpRect(left=28.148148.dp, top=520.8889.dp, right=291.85184.dp, bottom=595.55554.dp) overlaps Pomodoro item DpRect(left=65.77778.dp, top=575.1111.dp, right=123.55556.dp, bottom=655.1111.dp)`, an overlap of 20.44444dp.
- 2026-10-26: Downloaded the same API 31 shard from earlier main run `40287959415` (`b58fa73`) with `gh run download 40287959415 -n instrumented-reports-api-31-shard-1`; the same method passed in 5.815s. Main run `40358339173` (`c599620`) also succeeded according to the coordinator's run-history inspection, while `40348957121` failed and later runs `40380995661`/`40381019276` were not complete when inspected. Durable excerpts from the failing and passing JUnit/logcat artifacts are in `../evidence/task-003-sync-status-ci.txt`.
- 2026-10-26: `git diff b58fa73..5b8e906` shows no change to `SyncStatusFitTest`, `SyncStatusBand`, `SignedInFrame`, app navigation or navigation theme code. The intervening application source changes are launcher-icon assets, so the comparison identifies no relevant source edit that explains the pass/fail difference; it does not rule out dependency, asset or runtime effects. The failed log records API 31 reaching 540dpi and font scale 2.0 before the test body, so the evidence does not support late delivery of those device settings as the cause, but the exact cause remains open.
- 2026-10-26: Static layout inspection shows `SyncStatusBand` and `AppNavigationBar` are sibling children of the same `Column` in `Scaffold.bottomBar`. The test obtains each status and bar rectangle through separate `SemanticsNodeInteraction.getUnclippedBoundsInRoot()` fetches, so an API 31 relayout between fetches could combine rectangles from different layout generations; a transient multiline `TextButton` measurement defect is the other live explanation. Neither hypothesis is an established root cause without the required focused run. On resume, atomically capture the relevant bounds after one idle synchronization while preserving the existing pairwise assertions. If a single-snapshot capture still overlaps, correct `SyncStatusBand`'s multiline action measurement instead. Do not add polling retries or arbitrary waits.
- 2026-10-26: No build or device command was launched. The coordinator's OOM-safe memory gate rejected the reserved Gradle/device capacity probe with exit 75: `app.slice` had about 7.8GB current use, 10GiB high and 12GiB max, leaving about 2.9GB/5.1GB against the required 8192MiB device floor. Per owner direction, execution stopped with the single API 31 method and any affected screenshot verification pending. Applied `POST_ERROR_DETECTION` by marking this task failed.
- 2026-10-26: If a production layout correction is ultimately required, the affected screenshot selectors are `com.github.e0ipso.tokkl.ui.screenshot.ScreenshotTest#timer` (compact status above the bar) and `#timerBesideTheRail` (status beside the rail), on the screenshot test's API 36 reference device. No kenkeep leaf was changed because no layout change was made.
- 2026-10-26: Resumed with the existing run `40348957121` failure as RED evidence because the mandatory 8192MiB device admission remained unavailable after bounded cache reclamation; no redundant emulator attempt was made. Corrected `SyncStatusFitTest.assertNoOverlap` to resolve semantics interactions before `runOnIdle` and read every node's live position and size in one main-thread task, following `SignInFormLayout.layoutOf`. Every original geometry, text, touch-target and click assertion remains. This addresses a statically demonstrated sampling weakness but does not establish that cross-layout sampling caused the CI failure. No production layout or kenkeep leaf changed; compilation and the exact API 31 method remain pending, so the task stays in progress.


## Owner-authorized verification, 2026-10-27

The owner directed "Try anyways", overriding the local admission preflight for a constrained attempt. Android-test compilation and lint completed with BUILD SUCCESSFUL in 6m 14s using one worker, Xmx1g, MaxMetaspace512m and a 3 GiB per-job cap. Lint XML reports zero Error/Fatal issues, nine issues total. The parent read the full final log and parsed the report; the tool-host reset prevented capture of the original shell exit status, so none is invented. Evidence is retained in ../evidence/override-compile-lint.log and override-lint-results-debug.xml; verified source hashes are in override-verified-test-sources.sha256.

The subsequent exact API 31 method attempt used the same small Gradle configuration and a 15-minute timeout, bypassing only the local admission preflight under the owner's instruction. The runner booted a read-only tokkl-api31 instance as emulator-5554 in tokkl-emulator-5554-6022e5a6.scope, and created its pinned real-server/TLS fixtures. During APK packaging, QEMU threads stopped responding for 16-29 seconds and the emulator's hang detector ended it with status 139. Gradle reached connectedDebugAndroidTest, then failed with DeviceException: No connected devices!; BUILD FAILED in 6m 33s. No method ran and no fresh JUnit XML was produced. Full memory pressure was avg10=38.04, avg60=43.27, avg300=45.05; host available memory was 7199 MB and swap free 253 MB at the runner's report. This is an infrastructure failure, not a failed layout assertion.

The coordinator applied POST_ERROR_DETECTION and marked this task failed. Both the named job scope and emulator scope are inactive, no tokkl-android-tests containers or instrumented slot locks remain, and the owned runner directory f340d2ef was removed. The crashed AVD was read-only; its temporary writes were discarded. The original shared 10/12 GiB limits remain unchanged. No device retry was performed. A later retry can reuse the newly packaged APKs, avoiding the packaging work that overlapped emulator startup, but still requires owner direction after this execution failure.


### Owner decision, 2026-10-27

Owner selected "Leave device verification pending". Retain failed status as the infrastructure attempt's outcome; runtime verification is explicitly deferred. No automatic retry. Compilation/lint passed and source changes are saved in 207bc9e.
