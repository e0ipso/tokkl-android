# Task 04 verification

Task 04 adds the independent Pomodoro provider, responsive content, direct server actions and one application sync follower. The base commit is `a27d347991109e6094963288b070d641d929a1dc`. Final checks ran on the shared working tree, with before/after source and reference hashes unchanged during each final invocation.

## Accepted evidence

- `acceptance.log`, `acceptance-exit-code.txt`, `acceptance-junit/`, `lint-results-debug.xml`: exit 0, 56 widget JVM tests, zero failures/errors/skips, Android test compilation successful, zero lint errors.
- `api36-reference-comparison.log`, `api36-reference-comparison/exit-code.txt`, `api36-reference-comparison/junit/`: exit 0, five actual non-skipped API36 methods, zero failures/errors. Four methods compare 144 references and check text, phase words, full control bounds, target sizes and accessibility. The fifth checks corrected native countdown/overtime bases and ticking without RemoteViews updates.
- `verification.json`: counts and accepted result paths summarized for the parent.
- `acceptance-inputs/` and `api36-reference-comparison/inputs-*.sha256`: inputs unchanged before/after the respective runs.
- `references.sha256`: the 144 recorded `app/src/androidTest/assets/screenshots/pomodoro_widget_*.png` files.

## Reproduce

Run from the repository root. Both helpers copy JUnit and other results before the agreed lock releases. They invoke only `./gradlew --no-daemon` and `scripts/instrumented-tests.sh` for their respective checks.

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- flock /tmp/tokkl-plan3-gradle.lock bash .ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task4/unit-proof.sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- flock /tmp/tokkl-plan3-gradle.lock bash .ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task4/device-proof.sh api36-reference-comparison
```

The unit helper runs `:app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.*' :app:compileDebugAndroidTestKotlin :app:lintDebug`. The device helper selects the four `*PomodoroWidgetScreenshotTest#allSizesStatesAndControlsFitAndMatchReferences` methods and `PomodoroWidgetClockTest#nativeCountdownAndOvertimeTickWithoutProviderUpdates`, on `TOKKL_TEST_AVD=tokkl-api36`.

## Reference review

All 144 reference candidates were visually reviewed. Nine states cover work, short rest, long rest, each finished phase, idle, signed out and unsupported. Each uses all four sizes, light/dark and font scales 1.0/2.0. `final-captures/actual/` contains exactly the reviewed pixels copied to assets; `final-captures/origins.tsv` identifies the fresh recording run for each image. The 72 largest-font captures came from `api36-final-record`; the corrected 72 normal-font captures came from `api36-normal-record`. The final passing run compared all of them again against fresh renders.

Contact sheets are in `final-captures-review/`. Review checked phase words, complete action bounds, clocks, progress, number/cycle, entry chips, defaults and placeholders. Large-font layouts use icon actions, a smaller clock on the large card and fewer details on the medium card. The approved three sizes retain 48 dp targets. The extreme 110x40 bound uses 36 dp icon actions to fit the 40 dp cell; it receives fitting/full-bounds checks, while ATF runs on the three approved sizes. Long entry fields may ellipsize; phase headings at the three approved sizes must remain unellipsized. The absent server session-duration/saved-count line is omitted.

## RED/GREEN and corrections

`red.log` and `red-junit/` contain nine expected `NotImplementedError` failures from the custom action/clock stubs. `green.log` and `green-junit/` contain the same nine passing tests. They cover corrected clock bases, countdown/overtime/progress, account and phase guards, captured revisions, all idle fields/defaults, and one-shot offline/refused results.

Initial API36 capture failures identified an unbounded RemoteViews clock and Glance's ten-child limit dropping actions. Visual review then identified partly clipped large-font controls despite the original visibility check. The tests were strengthened to check complete clickable bounds and minimum target size; content now reserves the footer, and compact secondary buttons have explicit minimum widths. These assertions are part of the final passing comparison.

`acceptance-initial.log`, `acceptance-concurrent.log` and `acceptance-transient.log` are failed development attempts, not final evidence. They include corrected Glance imports/scopes, the concurrent Timer provider's missing `widget_duration` resource, corrected Android-test assertion/import diagnostics, and a lint internal `AsyncExecutionService` failure while files were being edited. Later final lint passed.

`api36-record/` preserves the complete initial five-case failure inventory. `api36-layout-fix/INTERRUPTED.txt` records the deliberately stopped obsolete focused run. `api36-bounds-interrupted/INTERRUPTED.txt` records an unexpected exit 143 with no accepted report; the parent confirmed it did not cancel it. Both interrupted attempts are excluded from success/failure counts. No other worker's scopes were stopped. Only focused methods were rerun; full workflow/API31 coverage remains task 05.

`kenkeep-lint.log` and `kenkeep-freshness.log` record existing orphan/redirected-edge and stale-node findings. Knowledge-base maintenance is outside task 04. No commits, PRs, pushes or update checks were performed.
