---
id: 6
group: "timer-surfaces"
dependencies: [3, 4]
status: "completed"
created: 2026-10-28
skills:
  - kotlin
  - jetpack-compose
complexity_score: 6
complexity_notes: "The Timer page and focused timer share one control/read model and must agree on paused fields and elapsed time. Native surfaces and Pomodoro UI are separate tasks; this task owns the shared adapter and its two existing Compose consumers."
execution_profile: "complex-architecture"
---
# Move timer controls to server pause state

## Objective

Make the Timer page and focused timer display the server's pause chain and use online Pause, Resume and Discard while preserving queued ordinary timer actions.

## Skills Required

Kotlin control-model refactoring and Jetpack Compose timer UI.

## Acceptance Criteria

- [x] The shared timer control model reads the held server pause state and effective cached running entry. Paused time is `pause.seconds`; a matching clone adds elapsed time from the corrected clock. History/totals count real entries once, without adding a chain total a second time.
- [x] The Timer page and focused timer send Pause, Resume and Stop-while-paused through the direct action client. Resume and Discard use the shown revision; Pause follows its unrevisioned server contract. They display conflict/offline messages and leave state unchanged on a failed action.
- [x] Every Pause/Resume control in these screens is hidden without the capability. Pause is also hidden during a server Pomodoro session. While `pause.paused` is true, fields are read-only and Edit is hidden.
- [x] Ordinary Start/Continue/favorite Start, Stop of a running entry or clone, and running-entry edits retain their existing offline queue and editor validation. Navigation, Back/Exit and timer view/scroll restoration still work.
- [x] The shared notice path displays `This pomodoro was too short to save.` once for task 2's unacknowledged discarded entry.
- [x] Rewrite the existing `TimerControlsTest` and `TimerPauseTest` around server-state inputs. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.cache.TimerControlsTest' --tests 'com.github.e0ipso.tokkl.timer.TimerPauseTest'`; expect nonzero tests and zero failures.
- [x] Own the fifth flaky test from [issue #14's exact report](https://github.com/e0ipso/tokkl-android/issues/14#issuecomment-6419333769): keep and rewrite `FullscreenTimerAcceptanceTest.focusedLocalTicksAndFrozenPauseAddNoRequests` for direct server Pause. The action sends `POST /api/app/timer/pause`, creates no queued STOP and adopts the returned authoritative pause state before `frozenRequests` is sampled. Settle any earlier queued setup changes and their actual refresh jobs before counting. Keep exact assertions that local ticks, foreground clock catch-up beyond the allowed lifecycle refresh, and frozen paused time add no requests. Preserve frozen seconds and displayed-target identity; do not add retries, sleeps, skips or looser request allowances.
- [x] Run the exact-method selection below on both tokkl-api36 and tokkl-api31, covering both concrete subclasses `FullscreenTimerServerTest` and `FullscreenTimerLargeTextTest`. Require exactly two executed methods per API with zero failures or skips. Then run both complete focused timer classes on each API before screenshot/package or full-suite validation. Preserve each run's JUnit results and device/process-isolation receipts before the next run overwrites them.
- [x] Run `TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.ui.screenshot --no-daemon`. Review affected Timer/fullscreen/nav captures, update only justified references in this task's commit, and rerun to zero failures.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Primary files are `cache/TimerControls.kt`, `timer/TimerModel.kt`, `TimerViewModel.kt`, `TimerBar.kt` and `FullscreenTimerScreen.kt`, plus their entry-editing integration. Introduce server revision targets alongside any temporarily required legacy signatures; task 10 removes those signatures once native callers are converted. Do not preserve a device-local pause fallback.

This task also owns the affected acceptance behavior in `app/src/androidTest/java/com/github/e0ipso/tokkl/timer/FullscreenTimerServerTest.kt` and the minimal server-action/state-settlement adaptation of `TimerAcceptanceFixture.kt`. Task 3 supplies the direct client and task 2 supplies the holder. Do not reopen Plan 01 or first repair its obsolete local-Pause drain path. Ordinary queued Start, Stop and edits retain their sender behavior.

## Input Dependencies

Hard dependencies on task 3's action client and task 4's capability, with task 2's holder inherited transitively. No soft dependencies.

## Output Artifacts

Server-backed shared timer controls and revision targets, updated Timer/focused screens, retained no-request regression coverage with two-API focused-run evidence, focused control checks and reviewed screenshot changes.

## Implementation Notes

<details>
<summary>Shared controls and Compose consumers</summary>

1. Inventory `TimerControls` and target callers. Separate ordinary queued entry operations from server pause operations in the adapter; the remaining widget/tile/notification callers are migrated by task 9.
2. Build paused display fields from the server's `pause` object. For a running clone, add carried seconds only when its id matches `clone_entry_id`; otherwise use the effective running entry normally. Preserve queued local overlays when stream state lags an offline Start/Stop.
3. Pass the displayed revision into Resume and Discard. Show the two exact error messages from task 3 through the existing screen failure path. Do not reset a paused view while its request is in flight.
4. Hide paused editing and remove its form route/callbacks. Retain the shared running editor, including its date/start handling and stale-entry rejection. Preserve focused navigation and close/update behavior for authoritative remote changes.
5. Consume task 2's discarded-entry notice through one UI message path and acknowledge it once. Do not have both Timer and Pomodoro screens separately invent deduplication.
6. Rewrite tests of the moved control behavior; delete local-chain-only cases only when their replacement coverage exists. Adapt the inherited offline-action/editor case to keep queued offline running edits and Stop, assert online-only Pause/Resume/Discard failures, and cover successful server Pause/Resume online. Replace paused-field editing expectations with read-only fields and hidden Edit, including the shared action-count and rotation assertions. Keep navigation, recreation, stale-action and elapsed-time coverage.
7. Resolve the issue #14 regression as described below and pass the focused methods and both complete classes on both APIs before broader validation. Capture changed references on tokkl-api36 in the same commit, following the screenshot knowledge node. Use OOM-safe scopes for every Gradle/device command.

</details>

### Issue #14 ownership and root cause

The [fifth failure](https://github.com/e0ipso/tokkl-android/issues/14#issuecomment-6419333769) was reported on API 36 shard 2 in run `40340280048`, through `FullscreenTimerLargeTextTest`: `frozen display adds no requests`, expected 22, actual 23, at `FullscreenTimerServerTest.kt:300`. The method lives in the abstract `FullscreenTimerAcceptanceTest`; both concrete subclasses inherit it. Verdict: keep and rewrite here, because local ticking and a frozen paused display must not send requests.

Live code inspection on 2026-10-28 supports the reported race. `ChangeQueue.pause()` calls `stopWith()` and queues a STOP. `ChangeSender` invokes its `onSent: () -> Unit` callback, wired in `TokklApplication` to launch `AccountSettingsSource.refreshAfterChange()` without awaiting the returned job. `TimerAcceptanceFixture.drain()` waits for the sender and then launches its own `refreshAfterChange().join()`. Each refresh-after-change waits for the currently published refresh and then starts or joins another. Depending on scheduling, the sender's launched wrapper can chain a refresh after the fixture's wrapper has completed and `frozenRequests` has been sampled. An empty queue and visible Paused state therefore do not establish that every refresh has finished. This is a code-supported explanation, not a new reproduction or a passing test result.

Task 06 replaces the screen's queued Pause with `POST /api/app/timer/pause`. Keep the local-tick and frozen-time assertions, but replace the Pause/drain prerequisite with completion of that direct action and observation of its authoritative revision, `pause.paused`, `pause.seconds` and displayed target in the shared holder/UI. Finish any earlier queued setup work and await its actual post-send jobs before the request baseline; another `refreshAfterChange()` call is not proof those jobs finished. Account explicitly for action/lifecycle requests and the established SSE connection if task 5 is integrated. Do not count SSE events or keep-alive bytes as new requests, or permit arbitrary extra requests to hide unfinished work. The controlled-clock advance must leave the paused total and target unchanged with an exactly unchanged request count.

### Focused verification before broad suites

Use the runner's comma-separated `class=` selection, which it converts to a filter file. Select concrete subclasses, never the abstract base. Run these commands sequentially after the replacement coverage is implemented:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- env TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh '-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timer.FullscreenTimerServerTest#focusedLocalTicksAndFrozenPauseAddNoRequests,com.github.e0ipso.tokkl.timer.FullscreenTimerLargeTextTest#focusedLocalTicksAndFrozenPauseAddNoRequests' --no-daemon
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- env TOKKL_TEST_AVD=tokkl-api31 scripts/instrumented-tests.sh '-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timer.FullscreenTimerServerTest#focusedLocalTicksAndFrozenPauseAddNoRequests,com.github.e0ipso.tokkl.timer.FullscreenTimerLargeTextTest#focusedLocalTicksAndFrozenPauseAddNoRequests' --no-daemon
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- env TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh '-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timer.FullscreenTimerServerTest,com.github.e0ipso.tokkl.timer.FullscreenTimerLargeTextTest' --no-daemon
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- env TOKKL_TEST_AVD=tokkl-api31 scripts/instrumented-tests.sh '-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timer.FullscreenTimerServerTest,com.github.e0ipso.tokkl.timer.FullscreenTimerLargeTextTest' --no-daemon
```

Inspect fresh JUnit XML under `app/build/outputs/androidTest-results/connected/debug`. Each method-only run must name both subclasses and execute two tests. For each complete-class run, compare the executed method inventory against the rewritten source in both subclasses, with no unexpected omissions or skips and zero failures. The current base has seven inherited methods, so the current class pair has fourteen; update the expected inventory explicitly if the server-state rewrite splits a case. Preserve revision, server-image digest, API level, selectors, counts and runner receipts with the results. Investigate any failure in a focused run before proceeding to the broader gates in task 12.

## Execution Result

Completed on 2026-10-28 against Tokkl 1.10.0 (`ghcr.io/e0ipso/tokkl:1.10.0@sha256:207db4f2a6547fea35ad898570fef892f55ef3ab26b6f142bfc3e3e0e680ad2f`), on top of `d00b2cd`.

- Shared model. `serverTimerControls(running, held, timerApi)` in `cache/TimerControls.kt` builds the controls from the cache's running entry and the held server state. A paused chain shows `pause.seconds` and the fields from `pause`. A running entry whose id is `clone_entry_id` adds `pause.seconds` and counts on the server's clock (`clockOffset`). Any other running entry wins and adds nothing, so a queued Start or Stop still shows over a lagging state. A chain whose clone the cache no longer runs shows nothing. `pausable` is false without the capability and during a session. Without the capability, the held state counts for nothing. Server targets are `TimerTarget.server(revision, entryId)`, with session and chain 0, beside the legacy targets that the notification, widget, tile and reminder still use until task 9. `TokklApplication.timerControls` is the shared flow. It reads the cache and the held state under the holder's lock (`TimerStateSource.withState`), so a resumed clone never shows as idle between the state's publication and its cache write. That gap would close the focused route on a remote Resume. Totals still read cached rows only (`TimerTotalsTest` 15/0, its chain case included).
- Actions. `TimerModel` sends Pause (no revision), Resume and Stop while paused (`discard`, shown revision) through `PauseChainActions`, which `TimerActions` now implements. It shows what it showed until the server's answer reaches the controls, and it ignores other taps meanwhile. A description still being typed is queued and sent before Pause. A refusal or no answer becomes a `TimerNotice.ActionFailed`. Start, Continue, favorites, Stop of a running entry or clone, and running edits keep the queue. Paused fields are read-only, paused editing and its callbacks are gone, and `timer_fields_done` was removed.
- Screens. TimerBar hides Pause unless `pausable` and disables the fields while paused. The focused timer shows Resume/Stop/Exit while paused (no Edit), and Pause only when offered. One notice path, `TimerNotices` in the nav host on Timer, focused timer and Pomodoro, shares the short-Stop snackbar host and shows failures and `This pomodoro was too short to save.` (claimed once through `takeDiscardNotice`).
- Issue #14. The rewritten `focusedLocalTicksAndFrozenPauseAddNoRequests` starts its running entry on the server, so no queued setup change or unawaited post-send refresh exists. The event stream opens to the test server itself before the relay, so its request and keep-alives are not counted. The test joins the activity's start refresh before the baseline. Pause is `POST /api/app/timer/pause` with no queued STOP. The test asserts the held revision, `pause.paused`, `pause.seconds` and the displayed target, waits until the one refresh that the paused state starts has written the stopped entry back, and then asserts exactly one Pause plus one refresh's requests. Local ticks, foreground catch-up and the frozen hour keep exact no-request assertions. `RequestCounter` records request lines for these messages.
- Fixture. `prepare()` now stores the sign-in through `storeSignIn`, which gives each test account a new, empty timer state. `SessionRule` does not clear `TimerStateStore`, so each test process loaded the previous account's state, and its higher revision blocked the new account's states. The first API 36 method run failed on this in its second method, and the fix passed on every run after it.
- Checks, all in the OOM-safe scope. RED: 25 of 27 failed against stubs. JVM acceptance command: `TimerControlsTest` 10, `TimerPauseTest` 17, 0 failures, exit 0. Touched classes `TimerModelTest` 23, `RunningEntryEditorTest` 12, `TimerActionsTest` 7, `TimerStateSourceTest` 13 and `TimerTotalsTest` 15 had 0 failures, and `lintDebug` exited 0 with 9 findings, none in a touched file. Exact methods: API 36 2/2 (both subclasses) and API 31 2/2. Complete classes: API 36 14/14 and API 31 14/14, matching the 7 inherited methods in each subclass, 0 failures, 0 skipped. Screenshot package on API 36: 100/100, 0 failures, so no reference changed. Also `TimerTotalsServerTest` (shares the fixture) 3/3 on API 36, and the new `TimerNoticeServerTest` 1/1 on API 36 and API 31. JUnit XML, logcat and runner logs are kept per run under `app/build/task06-receipts/`.

