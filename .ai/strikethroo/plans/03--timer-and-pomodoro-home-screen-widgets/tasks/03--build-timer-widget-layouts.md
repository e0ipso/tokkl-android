---
id: 3
group: "timer-widget"
dependencies: [1]
status: "completed"
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

- [x] Implement LocalSize breakpoints from approved mockups: strip with project/description/clock and one Stop/Resume/Start; medium Today ticking total, stacked bar and fitting legend; large Total/Billable/Entries, project bars, Today link and template chip row.
- [x] Support running, paused, idle and no-account states in light/dark and font scale 1.0/largest. At 110×40 dp degrade to clock/button; drop project rows before large-only template chips. Cap stacked bar at five segments and fold remainder into Other.
- [x] Use native ticking Chronometers with explicit measured width, account-format static durations, project dots/bars and accessible words/descriptions. Keep pause/resume server-online semantics from shipped plan 02; ordinary start/stop/templates keep their offline queue behavior.
- [x] Lower provider minimums to allow 2×1, keep updatePeriodMillis zero, add resource strings and Today intent handling through MainActivity/Reports model. Body/Today taps route correctly even with a retained activity; no-account opens sign-in.
- [x] Add meaningful layout/breakpoint/navigation checks and update directly affected old tests. Run :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:lintDebug in the OOM-safe scope; expect build success, zero test failures and no lint errors. Before phase 2 commit, run focused Timer widget layout checks on API36 and record/review Timer screenshot references for three sizes and both themes/font scales. Task 5 expands workflow proof and full-suite validation.

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


## Execution Result

Completed on 2026-10-30 in the shared feature branch. Task 1's committed Execution Result was read before implementation. Its day clock contract is preserved: `day.running.start` plus `day.stoppedSeconds`, excluding pause-chain accumulation. The entry clock keeps its own accumulated seconds and corrected start.

The Timer provider now selects strip, medium and large cards from both LocalSize dimensions. It includes project dots, descriptions, native measured clocks, the Today report, at most five stacked segments with Other aggregation, fitting project legends/bars, Total/Billable/Entries tiles, and a large-only template chip row reserved before project rows. The 110×40 fallback retains the clock and one action. Static durations use the account format. Palette text contrast takes precedence over the mock's blue billable text; a blue marker accompanies readable billable text. Native controls retain 48dp targets where the placed size allows them.

`WidgetNavigation` opens Timer, Reports Today or the current Pomodoro page. The task 4 coordination contract is present exactly as `WidgetNavigation.OPEN_POMODORO = "open_pomodoro_widget"`. MainActivity consumes cold/new intent extras, saves pending routes across rotation and clears them on sign-out. The host checks sign-in and applies the existing Pomodoro capability guard. Reports opens Summary on Today and clears retained filters so its range matches the widget. Notification account/phase validation remains unchanged.

Pause, Resume and paused Stop/Discard reuse the shipped server-online callbacks from plan 02. Ordinary Start, running Stop and template starts retain their offline queue. The old plan's offline Pause/Resume wording was not implemented. No new network requests, wakeups, dependencies, commits, PRs, pushes or update checks were added. New Pomodoro provider/action/resource files, its manifest receiver and TokklApplication wiring were left to task 4. NativeWidget binding and clock/capture helpers remain generic.

### Verification

All paths below are relative to `evidence/phase2/task3/`. Every Gradle/device invocation ran from the repository root in the OOM-safe scope under `/tmp/tokkl-plan3-gradle.lock`. JUnit and capture copies were taken before releasing that lock.

Final required command:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- \
  flock /tmp/tokkl-plan3-gradle.lock bash \
  .ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task3/run-final-build.sh
```

The helper runs exactly `./gradlew --no-daemon :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:lintDebug`, then copies JUnit and lint XML within the lock. Old result files are removed first. Exit **0**, `BUILD SUCCESSFUL in 3m 28s`. Android test Kotlin compilation passed. **1,068 JVM tests in 114 suites, 0 failures/errors/skips**. Lint: **0 errors/fatal findings, 15 warnings and 1 hint**. Evidence: `final-build.log`, `final-build-exit-code.txt`, `final-junit/`, `final-lint.xml`.

| Focused check | Result | Evidence |
|---|---|---|
| RED sizing and Other aggregation | 2 tests, 2 expected assertion failures | `red-unit.log`, `red-junit/TEST-com.github.e0ipso.tokkl.widget.TimerWidgetLayoutTest.xml` |
| RED retained Weekly → Today | 1 test, expected SUMMARY but got WEEKLY | `red-unit.log`, `red-junit/TEST-com.github.e0ipso.tokkl.reports.ReportsModelTest.xml` |
| GREEN layout and Reports model, plus Android test compilation | Exit 0; 13 tests, 0 failures/errors/skips | `green-build-3.log`, `green-build-3-junit/` |
| Initial API36 layout checks | Exposed native clock identification and largest-font clipping; corrected before accepting references | `layout-initial.log`, `layout-initial/junit/`, `layout-record.log`, `layout-record/junit/` |
| Refined light capture | 32 cases passed layout, accessibility and clock-base assertions; 2 test failures only because references did not yet exist | `layout-refined-2.log`, `layout-refined-2/junit/`, `layout-refined-2/captures/` |
| Dark capture plus navigation/existing tests | 32 dark cases passed layout/accessibility assertions; 8 functional tests passed, 2 failures only for missing dark references | `dark-record-and-navigation.log`, `dark-record-and-navigation/junit/`, `dark-record-and-navigation/captures/` |
| Final API36 reference comparison and offline Start/Stop | Script exit 0, BUILD SUCCESSFUL; 5 tests, 0 failures/errors/skips; all 64 PNG comparisons passed | `reference-compare.log`, `reference-compare/exit-code.txt`, `reference-compare/junit/` |

The RED/GREEN JVM selector arguments after `./gradlew --no-daemon` were:

```text
RED:
:app:testDebugUnitTest --tests com.github.e0ipso.tokkl.widget.TimerWidgetLayoutTest --tests com.github.e0ipso.tokkl.reports.ReportsModelTest.widgetTodayReplacesARetainedWeeklyTabAndItsFiltersWithTheAccountDay
GREEN:
:app:testDebugUnitTest --tests com.github.e0ipso.tokkl.widget.TimerWidgetLayoutTest --tests com.github.e0ipso.tokkl.reports.ReportsModelTest :app:compileDebugAndroidTestKotlin
```

Device runs use this exact wrapper, whose maintained runner supplies `./gradlew --no-daemon` and the real pinned server:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- \
  flock /tmp/tokkl-plan3-gradle.lock bash \
  .ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task3/run-device.sh \
  <evidence-name> <comma-separated-class-or-method-list>
```

`run-device.sh` sets `TOKKL_TEST_AVD=tokkl-api36`, invokes only `scripts/instrumented-tests.sh` with the `android.testInstrumentationRunnerArguments.class` selector, and copies JUnit/captures under the build lock. Exact successful functional selectors for `dark-record-and-navigation` were:

```text
com.github.e0ipso.tokkl.widget.DarkTimerWidgetScreenshotTest,com.github.e0ipso.tokkl.widget.DarkLargestTimerWidgetScreenshotTest,com.github.e0ipso.tokkl.widget.WidgetNavigationTest,com.github.e0ipso.tokkl.widget.WidgetServerTest#todayReportTapOpensReportsOnToday,com.github.e0ipso.tokkl.widget.WidgetServerTest#nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod,com.github.e0ipso.tokkl.widget.WidgetServerTest#favoritesResizeAndEachNativeTapQueuesAllItsFieldsOffline,com.github.e0ipso.tokkl.widget.WidgetLargeTextTest
```

Final `reference-compare` selectors:

```text
com.github.e0ipso.tokkl.widget.LightTimerWidgetScreenshotTest,com.github.e0ipso.tokkl.widget.LightLargestTimerWidgetScreenshotTest,com.github.e0ipso.tokkl.widget.DarkTimerWidgetScreenshotTest,com.github.e0ipso.tokkl.widget.DarkLargestTimerWidgetScreenshotTest,com.github.e0ipso.tokkl.widget.WidgetServerTest#startAndStopWorkOfflineThroughTheNativeControls
```

The functional proof covers cold and retained Today/Pomodoro routes, rotation and consumed extras, no-account sign-in, the Pomodoro capability fallback, an actual widget Today tap, template omission/resizing and all queued template fields, two native clocks ticking without RemoteViews updates, and largest font with largest display size. Across the two final device selections there are **13 distinct successful JUnit test methods**, including 4 screenshot methods that cover **64 cases**. Full API31 and workflow-suite validation belongs to task 5.

### Screenshot review and scope receipt

All 64 new `app/src/androidTest/assets/screenshots/timer_widget_*.png` references were inspected before acceptance: 196×100, 298×222, 375×338 and 110×40 dp; light/dark; font 1.0/2.0; running/paused/idle/no account. The native fit assertions include clock width and text height. ATF runs for widths at least 196dp; the physical 110×40 fallback has geometry checks. Clocks are frozen only for image capture. Separate tests observe real native ticks and compare entry/day clock bases.

Reviewed captures are in `layout-refined-2/captures/` and `dark-record-and-navigation/captures/`. Review pages: `review-light-normal.html`, `review-light-largest.html`, `review-dark-normal.html`, `review-dark-largest.html`, and `reviewed-timer-references.html`. `reviewed-light-sha256.txt`, `reviewed-dark-sha256.txt` and `verified-timer-inputs.sha256` identify the accepted images and current implementation inputs.

The final reference comparison's maintained test script returned **0**, recorded in `reference-compare/exit-code.txt`, with 5 passing tests and copied JUnit. Its outer systemd/tool transport reported **143 after those files were saved**. Both facts are preserved; the successful child result is not substituted for the outer status. The separate final Gradle/build/lint scope returned **0** normally. No other worker's scope, emulator or runner was stopped. An earlier compile-blocked `layout-refined` attempt also had outer status 143; its recorded child result is 1 and it is not used as passing evidence.

Outstanding task 3 blockers: none. Parent-owned phase gates and commit remain pending. `git diff --check` passed after the implementation.

## Noteworthy Events

- [2026-10-29] Initial integrated builds overlapped new resources/provider APIs. Their compile errors are retained in `green-unit.log`, `green-build.log` and `green-build-2.log`. The subsequent focused GREEN and final full check supersede those failed attempts.
- [2026-10-30] API36 geometry assertions caught small largest-font clock/Entries clipping. Measured native typography, reserved row heights and template-first space allocation now pass those assertions. Capture helpers identify multiple clocks by display order because Glance does not reliably retain native child content descriptions.
- [2026-10-30] Added the separate widget Pomodoro page route at task 4's request. It does not reuse or relax notification account/phase validation and carries no session mutation.
- [2026-10-30] API36 screenshots were reviewed and recorded during this task, before the parent-owned phase 2 commit. References compare successfully on the current shared tree.
- [2026-10-30] Required kenkeep lint/freshness found existing orphan/redirect warnings and 28 potentially stale nodes. The old widget node's minimum sizes/favorites rows no longer describe this provider; task 6 owns the documentation update.
