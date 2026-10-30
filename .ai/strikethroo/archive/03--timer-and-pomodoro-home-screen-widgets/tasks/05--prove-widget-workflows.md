---
id: 5
group: "widget-verification"
dependencies: [3, 4]
status: "completed"
created: 2026-10-29
skills:
  - android-testing
  - screenshot-testing
complexity_score: 7
complexity_notes: "The task owns one integrated deliverable; state, provider layout, device proof and documentation are separate tasks. Acceptance commands and ownership bound its integration."
execution_profile: "complex-architecture"
---
# Prove widget workflows on API36 and defer API31 to CI

## Objective

Prove the plan Success Criteria and Self Validation on API36 locally, with API31 deferred to CI by the user and record new visual references.

## Skills Required

android-testing, screenshot-testing.

## Acceptance Criteria

- [x] Update WidgetServerTest, WidgetLargeTextTest and NativeWidget for both providers and all three sizes. Assert ticking day total against Timer page, project grouping/no project, running/paused/idle, largest text/accessibility, Today navigation and running-check midnight rollover.
- [x] Prove ordinary offline Start/Stop/templates and online Pause/Resume, plus Pomodoro second-provider registration, remote session arrival/countdown, phase-end overtime within a second, Next/End/idle Start with defaults/last fields, conflict and offline messages. Test app behavior rather than Glance/launcher internals.
- [x] Verify the API36 screenshot references recorded in phase 2, and update any references for further appearance fixes in the same commit as those fixes. If recording needs the dedicated screenshot runner, follow existing scripts and test practices.
- [x] Run focused widget checks on tokkl-api36; defer tokkl-api31 coverage to CI, inventory and isolate every failure before broader runs, then full API36 instrumented/Compose and JVM suites plus lint/npm tests. Use only scripts/instrumented-tests.sh for device tests, OOM-safe scope and serialized heavy jobs. Save commands, JUnit counts, runner receipts and screenshots under plan evidence.
- [x] Execute all seven plan Self Validation steps and map each success criterion to concrete evidence. Zero failing tests and successful lint/build required; never treat another agent report or old plan evidence as proof.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

No new dependencies. Follow the approved plan/mockups and current PRD, with server-owned Pause/Resume semantics from completed plan 02. Every Gradle/device job runs in the OOM-safe scope with --no-daemon; serialize heavy jobs across concurrent workers.

## Input Dependencies

Own widget androidTest files, screenshot references and plan evidence. Small implementation fixes discovered by tests are permitted but document ownership and preserve others edits. All provider/state work must precede this task.

## Output Artifacts

The implementation and focused checks described above, task status and an Execution Result section containing exact proving commands, exit codes, JUnit counts and paths to evidence.

## User-approved review fixes and local scope

The user approved addressing all four advisory findings. Parent owns the small production fixes and meaningful focused tests: a safe session-only phase-end fallback when exact alarms are denied, a phase-colour band without frozen elapsed percentages, a native running Billable clock, and removal of obsolete layout helpers/resources. Device acceptance owns integration tests and updated references for their visual changes. API36 is the only local target. API31 is deferred to the existing CI matrix and must be reported as pending CI, not as locally verified.

## Parent integration notes

Phase 2 parent review found `favoritesThatFit` and the old favorites-row sizing constants/helpers have no production callers after the layout replacement. Their two old WidgetStateTest cases describe the retired layout. Remove that dead implementation and obsolete tests, preserving the new responsive fitting checks. Remove the unused `widget_start` and `pomodoro_widget_open` strings if still unreferenced. Review new lint plural warnings for real grammar issues, and keep any necessary fixes within widget resources. Do not add compatibility for the retired layout.

Phase 2 tests exercise rendering and clocks; task 5 must add the real-server Pomodoro widget action/remote-change/last-fields/offline/overtime workflow coverage and the day-total/midnight integration acceptance. Run all Self Validation steps, not only existing test classes. Parent verification of phase 2 is saved under `evidence/phase2/parent`.

## Implementation Notes

<details>
<summary>Implementation procedure</summary>

Write tests for custom business logic, critical user workflows, data transformations, edge/error conditions and component integration. Do not test third-party/framework behavior, simple CRUD, trivial getters/setters or static configuration. Combine related scenarios; favor integration/critical paths. Seven is justified by the indivisible end-to-end acceptance matrix for both widgets; implementation tasks already own focused tests and this task owns runtime proof. Use runner supported filters and secure credential files, never print secrets. Read testing root and matching leaves before device work.

You are not alone in the codebase. Do not revert other edits; accommodate concurrent changes. Read .ai/strikethroo/config/hooks/PRE_TASK_EXECUTION.md before implementation, and apply its meaningful-test RED/GREEN procedure. Do not create commits or PRs; the parent commits verified phases. Do not run update notices.

</details>


## Execution Result

Task 5 is completed within the user-authorized local API36 runtime scope. All acceptance criteria are proven on the current source/test/resource/build/runner/npm inputs. API31 is deferred to the existing CI matrix. Self Validation 7's documentation audit is performed; task 6 owns updates and the parent owns the subsequent doc-match/phase gates. This does not claim whole-plan documentation completion.

The full API36 instrumented/Compose suite passed through the existing CI configuration, six sequential maintained-runner invocations on fresh owned boots:

`env TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh --no-daemon -Pandroid.testInstrumentationRunnerArguments.numShards=6 -Pandroid.testInstrumentationRunnerArguments.shardIndex=N`, for N=0 through 5.

- Shards 0–5: 134 / 108 / 108 / 132 / 127 / 127 cases; every exit 0, zero failures/errors/skips. `final-shard-union-audit.json` proves the exact 736-case multiset against the complete first-full inventory, with no omissions/duplicates/unexpected tests, all 33 widget methods, all eight reference matrix classes (208 comparisons), six distinct owned boots and all eight ordered restart groups kept whole. Actual per-case start/finish receipts match every terminal case. The parent's independent XML audit agrees.
- `./gradlew --no-daemon --rerun-tasks :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`: exit 0, BUILD SUCCESSFUL in 12m 49s, all 47 tasks executed. 1,069 fresh JVM cases in 115 XML files, zero failures/errors/skips; Android test compilation executed; lint zero Error/Fatal issues, 12 warnings and one hint.
- `npm test`: exit 0; all 11 Node tests passed, zero failed/cancelled/skipped, including the wrapper executing five Python discovery lifecycle cases. Native counts/names are in `final-npm-corrected/npm-summary.json`.
- `git diff --check`: exit 0.
- All 1,011 current input files match before/after manifests of the accepted host/npm runs and all six shards. Aggregate manifest SHA-256: `5f95d4ca29d44b45828490e8dcc241943a443b20438ba6473f2c0a97d531aa39`.
- Reviewed every changed actual/diff pair at readable size. Copied exactly 40 approved captures (36 phase-band and 4 Billable differences), preserving 168 references. The copy audit and all eight passing final matrices verify the 208 references.
- Focused complete RED/GREEN inventory, all 41 surrounding cases and the unchanged interrupted-selector retry pass. Account/revision/queue/keyboard/geometry/real-touch assertions remain intact. Original keyboard files are byte-identical to the first full inputs.

Every job used the required serialized `flock --close /tmp/tokkl-plan3-gradle.lock systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- ...` prefix inside a durable named user service. All 18 final service/inner/emulator units are inactive; both AVDs are off and the panel has no open attachment. Maximum recorded final emulator peak was 4,958,023,680 bytes under the 6 GiB cap; zero sampled emulator swap. Unrelated host jobs were untouched. No project commits, pushes, PRs or task-6 edits were made.

Exact commands, JUnit/process/resource receipts, current-input audit, visual review, all seven Self Validation steps and all four scoped success-criterion maps are in [task 5 evidence](../evidence/phase3/task5/README.md), `acceptance-map.json`, `final-shard-union-audit.json` and `final-current-input-audit.json`. Clock/permission adaptations and all invalid/interrupted attempts are explicitly distinguished there.

## Noteworthy Events

- [2026-10-30 final acceptance] Six existing CI shards passed 736 exact API36 cases with zero failures/errors/skips on unchanged final inputs, including all widget workflows and 208 reference comparisons. Fresh JVM 1,069, Android test compile, lint and npm 11 pass. All eight restart groups remain whole and owned runtime cleanup is verified. API31 and documentation/doc-match remain explicitly downstream under the accepted scope.

- [2026-10-30 authorized resume] Ownership-safe authenticated emulator discovery repaired Device panel captures on multiple fresh owned boots. Reproduced the complete failure inventory before fixes: production replacement sign-in setup and host ownership cleanup, then stale native revision capture, then four WorkManager ownership assertions and six shared-IME failures. Preserved account/revision/queue/geometry/touch assertions. Scheduling fixtures now query all three current app Worker tags across every state; unrelated Glance work is untouched. Temporary keyboard diagnostics identified the selected agent IME with no input view; the original keyboard actions/assertions were restored unchanged and the runner now selects/verifies installed stock LatinIME only on its own boot.
- [2026-10-30 infrastructure recovery] The corrected single-boot full attempt ended after a QEMU hang under shared host memory pressure; its own emulator stayed below the 6 GiB cap with zero swap. Preserved 535 passing, one unfinished and 200 unexecuted cases distinctly from discovery logs. The same interrupted selector passed unchanged in isolation. Parent authorized the existing six-shard CI mode sequentially, preserving whole restart-test classes and requiring the exact 736-case union. Competing host scopes are not modified.

- [2026-10-30 diagnostic follow-up] User directed diagnosis of Device support. The hub diagnostic route exposed gRPC status 16: missing authorization header. The runner's isolated runtime contains a token-bearing emulator discovery record, but the hub searches standard directories. Detailed cause and ownership-safe discovery-link repair are recorded in [device-support diagnosis](../evidence/phase3/device-support-diagnosis.md). No repair or successful capture is claimed. The completed focused run executed both known failing selectors and both still failed: replacement-account setup has a null-value error, and host cleanup sees one widget. Exact unchanged-input results are in `evidence/phase3/task5/api36-diagnostic-focused/`. This supersedes the earlier unverified fixture-fix status. The owned emulator scope is inactive/dead, and this thread's panel attachment was closed. Task remains failed pending further user direction; API31 remains CI-only.
- [2026-10-30] Required Device panel attachment to hostId `local`, deviceId `emulator-5554`, named `tokkl-api36`, succeeded after the maintained runner booted its bounded lavapipe read-only instance. `device_screenshot` returned exactly `Device screenshot failed: Could not communicate with device support. Try refreshing devices.` The advised device-list refresh succeeded, but the single screenshot retry returned the same error. Stopped device work as explicitly instructed and preserved the unfinished run distinctly.
- [2026-10-30] Before interruption, TestRunner logs exposed two fixture problems: the idle last-fields view includes the resource's complete `Last:` description/project/task line, and replacement-account creation must happen before sign-out because the shared HTTP client enforces the configured server. Corrected both fixtures without weakening stale-revision/account-safety assertions. Their exact selectors are recorded for the first focused rerun once device support is restored. Discovery-only zero-duration events were excluded from executed-test claims.
- [2026-10-30] Read POST_ERROR_DETECTION and marked task `failed` because its device acceptance criteria remain unmet. Full host validation succeeded independently. No parent phase gate or final code-review gate was run.
