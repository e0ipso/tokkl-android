---
id: 12
group: "migration-and-acceptance"
dependencies: [11]
status: "completed"
created: 2026-10-28
skills:
  - android-testing
  - android-services
complexity_score: 6
complexity_notes: "This is one platform acceptance gate for the implemented service/alarm lifecycle, with explicit API 31/API 36 observations. Cross-device workflow tests are already supplied by task 11; this task reuses the runner and does not design a new harness."
execution_profile: "complex-architecture"
---
# Verify service lifecycle, Doze and final test gates

## Objective

Establish how the always-on sync and persisted alarms behave across Android lifecycle restrictions, then complete the plan's unit/Compose/device acceptance gates.

## Skills Required

Android instrumented acceptance testing and foreground-service/platform diagnostics.

## Acceptance Criteria

- [ ] (API 36 recorded below; API 31 not measured, by owner decision) On tokkl-api31 and tokkl-api36, record foreground `specialUse` service state and its silent notification after app open/sign-in, boot and package replacement. If Android refuses an exempt background start, record the refusal and prove the next app open recovers without a crash.
- [x] Process restart restores the held phase, clock correction, session/native notification and exact alarm without needing a request. Clock/timezone changes and an exact-alarm grant rearm the same phase; a missed end rings without advancing it.
- [x] Sign-out/capability loss stops streams/reconnects, notifications and session alarms, clears account state and rejects retired callbacks. Signing out does not end the server session for other devices.
- [ ] (API 36 recorded below; API 31 not measured, by owner decision) Force Doze with the app backgrounded, start a session remotely and record whether notification/state arrives and its measured delay on both APIs. Restore idle mode/settings after the probe. An observed Doze delay is reported for the PRD and owner, not hidden behind a new transport, exemption or battery workaround.
- [x] Retain permission-denied notification/exact-alarm/full-screen behavior, the existing sounds and ringing service, and the alarm's offline failure behavior. Review screenshot references on tokkl-api36 in the commit that changes them; API 31 uses its intended screenshot exclusions.
- [ ] (Written; passed on API 36 locally; API 31 delegated to CI) Add or adapt `TimerSyncServiceLifecycleTest` for critical account/process/start lifecycle behavior. Run it on each AVD through `scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timerstate.TimerSyncServiceLifecycleTest --no-daemon`; expect nonzero tests, zero unexpected failures and valid run receipts.
- [ ] (JVM suite and lint passed locally; full device suites delegated to CI on main) After all known focused failures are resolved, run `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug` and complete `TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh --no-daemon` plus the same full run with `TOKKL_TEST_AVD=tokkl-api31`. Require zero failures, expected nonzero test counts and device/process-isolation receipts. Retain results from each run before the next overwrites them.
- [x] The evidence matrix covers all six primary success criteria, including migration from version 8, plain offline Start/Stop and unsupported-server hiding. Separate measured platform limitations from passing results and surface any required decision before completing execution.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Use plain AOSP tokkl-api31/tokkl-api36, the maintained real-server runner and its orchestration/cleanup. Device diagnostics can use service/alarm/log dumps. For interactive device work use the current host's device tools and the exact `agent-device` launcher they supply; do not invent a second emulator control setup.

## Input Dependencies

Hard dependency on task 11's focused workflow tests and fixed known failures. No soft dependencies. Task 13 needs this task's measured lifecycle/Doze results.

## Output Artifacts

Lifecycle checks, two-API start/Doze/permission observations, final verification reports and a success-criterion evidence matrix for documentation and execution review.

## Implementation Notes

Write a few tests, mostly integration. Write tests for custom business logic and algorithms; critical user workflows and data transformations; edge cases and error conditions in core functionality; integration points between components; and complex validation or calculation logic. Do not write tests for third-party library/framework functionality, simple CRUD without custom logic, trivial getters/setters/static configuration, or obvious functionality that would break immediately if incorrect. Combine related scenarios into one task and favor integration/critical-path coverage over per-method tests. Never create one test task per CRUD operation.

<details>
<summary>Platform observations and final validation</summary>

1. Read the current testing knowledge branches and verify the runner's flags. Run Gradle and the instrumented script in `systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- ...`; let the runner own emulator settings and resources. Never call `./gradlew --stop`.
2. Verify foreground app/sign-in start, persisted-account boot and actual package replacement. Inspect the service's foreground type and notification channel, and record an exact platform refusal if present. Distinguish a missing capability/account from an Android start restriction.
3. Exercise process death with persisted server state and no network. Confirm the same phase identity, corrected planned-end schedule and native content are restored. A stale alarm must not ring a replacement phase; an expired held phase must ring at once without a transition.
4. Probe Doze while idle in the background and initiate a real remote session. Record elapsed arrival time, API level, image digest and stream diagnostics, then restore the device's idle settings in cleanup. The plan allows reporting a maintenance-window delay but does not authorize a workaround. Surface any broader inability to meet the agreed behavior.
5. Reuse or adapt retained permission/ringing tests. Run native permission probes alone with fresh permission when the testing instructions require it. Do not create tests for Android's alarm or service implementation itself; assert this app's behavior.
6. Inventory every failure, reproduce and fix it in a focused run, then perform the full JVM/lint and both full device runs once the known list is empty. Verify JUnit counts and runner receipts, not only `BUILD SUCCESSFUL`. Preserve immutable result copies under this plan's evidence directory with secrets removed.
7. Produce the acceptance matrix and platform observations task 13 will document. Do not claim a delayed Doze result was an ordinary two-second pass.

</details>

## Execution record

Written as each result comes in, because T3 restarts have ended the run twice. Raw excerpts are in [evidence/task-12-api36-probes.txt](../evidence/task-12-api36-probes.txt).

Coordinator change to the brief: the two full local instrumented suites are not run here. PR CI runs every instrumented test on six API 36 and six API 31 shards against the pinned image.

### JVM suite and lint

- 2026-10-28 20:34, before any change in this task: `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug` in the OOM-safe scope exited 0. `testDebugUnitTest` ran (not up to date): 110 JUnit files, 1025 tests, 0 failures, 0 errors, 0 skipped. Lint: 0 errors, 8 warnings, 1 hint (InlinedApi 1, NewerVersionAvailable 2, OldTargetApi 1, UnusedResources 1, UseKtx 3, ReportShortcutUsage hint 1).
- 21:34, after the host reboot wiped /tmp: the same command exited 0 ([evidence/task-12-jvm-lint.log](../evidence/task-12-jvm-lint.log)), but `testDebugUnitTest` was up to date, so it reused the 20:34 results (109 JUnit files, 1025 tests, 0 failures). Lint ran again: 0 errors, 8 warnings, 1 hint. The final run uses `--rerun` on the test task.
- 22:03, final, after the test changes: `./gradlew --no-daemon :app:testDebugUnitTest --rerun :app:lintDebug :app:compileDebugAndroidTestKotlin` in the OOM-safe scope exited 0 ([evidence/task-12-jvm-lint.log](../evidence/task-12-jvm-lint.log)). `testDebugUnitTest` executed: 109 JUnit files, all written by this run, 1025 tests, 0 failures, 0 errors, 0 skipped. Lint: 0 errors, 8 warnings, 1 hint, the same IDs as the baseline ([evidence/task-12-lint-results-debug.txt](../evidence/task-12-lint-results-debug.txt)). `compileDebugAndroidTestKotlin` was up to date: the 21:52 and 21:55 runner builds had compiled the same sources, both test edits included.

### Platform probes on tokkl-api36

Setup: the device tools' tokkl-api36 emulator (`qemu ... -gpu auto`, not `-read-only`, inside `t3.service`), the debug APK of the current tree (`com.canpicasoft.tokkl`, versionCode 5001, targetSdk 36, uid 10151), signed in with an API token to a disposable `ghcr.io/e0ipso/tokkl:1.10.0@sha256:207db4f2a6547fea35ad898570fef892f55ef3ab26b6f142bfc3e3e0e680ad2f` on `http://10.0.2.2:<port>`. The other device is `curl` on the host with the same account's token. Arrival is timed from the server's answer to the session notification's `posttimeElapsedMs`, mapped to the host clock through `dumpsys alarm` (`nowRTC`, `nowELAPSED`) and a measured device clock skew of about -545 ms (round trip 20 to 35 ms). Plain AOSP has deep idle disabled (`dumpsys deviceidle enabled` 0 for light and deep), so the probe enabled deep idle and disabled it again.

| Probe | Result |
| --- | --- |
| A. Sign-in, app in the foreground | Pass. `TimerSyncService` `isForeground=true foregroundId=4 types=0x40000000` (specialUse), `mAllowStart_noBinding=PROC_STATE_TOP`. Channel `timer_sync`: importance 2 (low), no sound, vibration off, no badge. Notification flags `ONGOING_EVENT|ONLY_ALERT_ONCE|NO_CLEAR|FOREGROUND_SERVICE`. |
| B. Home, no Doze, session started by the other device | Arrived 205, 213 and 209 ms after the server's answer. Two earlier samples are void: their matcher could hit a stale record. |
| C. Forced deep Doze (screen off, `battery unplug`, `deviceidle enable deep`, `force-idle`), 90 s idle, then a remote session | Arrived 225 ms after the answer. In idle, `netpolicy` showed uid 10151 `procState=FGS`, `blocked=DOZE\|APP_BACKGROUND`, `allowed=FOREGROUND\|...`, `effective=NONE`: the foreground service keeps the network through Doze. The service stayed foreground. The arrival itself ended deep idle (`device_idle: [0,alarm]`, then `[1,no activity]`), because the new phase's `setAlarmClock`, 60 s ahead, fell inside the platform's `min_time_to_alarm` of 1 h. The alarm clock was at the server's planned end minus the held clock offset (device 20:49:40.480 against host 19:49:41.030Z, with a -545 ms skew). Restored afterwards: `unforce`, `disable deep`, `battery reset`, wake and dismiss keyguard; then light and deep enabled 0, `mForceIdle=false`, AC powered, awake. |
| D. Real package replacement (`adb install -r` of the same APK, app not opened) | Pass. The old process was killed, and within about 1 s a new one had the service `isForeground=true types=0x40000000` with `mAllowStart_noBinding=PACKAGE_REPLACED`. No refusal or crash line in logcat. A remote session then arrived 234 ms after the answer. |
| E. Real reboot (`adb reboot`, app not opened) | Not answered. `PomodoroAlarmRearmReceiver` was enabled and the system started the app's process for it, but the process hit "ANR ... failed to complete startup" 15 s later and was killed, before any app code ran. Its main thread was in `DexFileVerifier::CheckHeader` (adler32) in disk wait: the app ran uncompiled (`pm art dump`: `status=run-from-apk`, `location is error`). SystemUI failed the same boot twice ("failed to complete startup", then "executing service ... waited 20002ms", "crashed too many times"). So the specialUse start from BOOT_COMPLETED was never reached. |
| E2. Launcher open after E | The app crashed. The FGS start was allowed (`Background started FGS: Allowed ... uidState: TOP`) 3 s after the open, then "Bringing down service while still waiting for start foreground" at +33 s and `ForegroundServiceDidNotStartInTimeException` at +37 s. A second cold open did the same. SIGQUIT samples every 2 s showed why: the main thread waited 20+ s in `HardwareRenderer.<init>` for the RenderThread, which was stuck in `eglCreateContext` reading the emulator's host GL pipe (`rcCreateContext_enc`, `qemu_pipe_read`). SystemUI skipped 2200 frames in the same window. The emulator's GL pipe hung after `adb reboot` of a `-gpu auto` emulator. The app's `onStartCommand` promotes first, but it runs on the main thread after the activity's resume, so a main thread held this long cannot promote. Not an app fault, but recorded as a platform limitation. |
| E3. Cold boot (device tool close and open: a new qemu with a fresh GL pipe), app not opened, account still signed in, its server gone | Pass. The system started the app's process for `PomodoroAlarmRearmReceiver` 4 s after `sys.boot_completed`. 0.48 s later the FGS start was allowed (`Background started FGS: Allowed ... uidState: RCVR`), and the service ran with `isForeground=true types=0x40000000`, `mAllowStart_noBinding=BOOT_COMPLETED`. No refusal or crash line. The app still ran uncompiled, and SystemUI had an ANR in the same boot. So Android 16 lets a `specialUse` service start from BOOT_COMPLETED, and E failed on boot load, not on a platform refusal. |
| G. `kill -9` of the app's process with the service running in the background | Pass. Android logged "Scheduling restart of crashed service ... in 1000ms", started a new process "for service" 1 s later, and the restarted service was foreground with `types=0x40000000`, under the original `BOOT_COMPLETED` allowance. No refusal. The case the code guards against (a restart whose `startForeground` is refused) did not happen here. |

Cleanup of tokkl-api36, which the device tools' emulator writes to: the app is uninstalled (with its `POST_NOTIFICATIONS` grant), `hide_error_dialogs` deleted (it was unset), the SIGQUIT traces removed, `adb shell sync`, then shut down. Deep idle reads disabled and AC powered.

### Platform probes on tokkl-api31

Not measured, by owner decision; a known gap. The owner stopped all local device runs before the API 31 probes, so app-open start, boot, package replacement, process restart and Doze have no API 31 measurement. PR CI runs the instrumented tests on API 31, but no test there reboots, reinstalls or forces Doze.

### Device runs through the runner

| When | AVD | Selection | Exit | JUnit | Result |
| --- | --- | --- | --- | --- | --- |
| 21:45 | tokkl-api36 | `TimerSyncServiceLifecycleTest`, `RunningTileServerTest#anOfflineLongPressPauseSaysWhyAndStopStillQueues` | 1 | 3 tests, 1 failure | Both lifecycle methods passed (8.3 s, 10.1 s). The tile method failed: "Compact controls did not show Pause timer" after 67.7 s. Receipt `TOKKL_DEVICE_BUILD` sdk 36, one JUnit file. Log: [evidence/task-12-runner-api36-1.log](../evidence/task-12-runner-api36-1.log). |
| 21:45 | tokkl-api36 | `RunningTileServerTest#anOfflineLongPressPauseSaysWhyAndStopStillQueues` alone | 0 | 1 test, 0 failures | Passed in 9.9 s. A host-side read-only poll showed `mKeyguardShowing=false`, `Awake` for the whole run. SystemUI logged `dismissKeyguardThenExecute, afterKeyguardGone: false` after the long-click at (290, 204). In the 21:41 failure it logged `afterKeyguardGone: true` after the long-click at (324, 1355), the shade's lock-screen layout, and then started no activity. Working hypothesis: the failure needs a showing keyguard. |
| 21:47 | tokkl-api36 | the 21:45 selection again: `TimerSyncServiceLifecycleTest`, then the tile method | 1 | 3 tests, 2 failures | The tile method failed as at 21:41, with `mKeyguardShowing=false`, `Awake` and `screen_off_timeout=2147483647` throughout, so the keyguard hypothesis is wrong. Root cause, from UiObject2's node dumps: in both failures the long-click hit `android:id/title` in SystemUI, the running timer notification's title, which also reads the entry's description; in the pass it hit `com.android.systemui:id/tile_label`. The notification exists only when an earlier test in the run granted `POST_NOTIFICATIONS` (here the lifecycle class's `@Before`, in task 11 the toast callers), so `By.text(description)` is ambiguous depending on test order. A test bug, not SystemUI behaviour; the test checks the app's offline Pause message and queued Stop, so it is fixed, not deleted. New: `aServerThatLosesTheTimerApi...` failed with "the sync notification: not within 2000 ms" (diagnosis below). |

Fixes after the 21:47 run, both in test code:
- `RunningTileServerTest.openCompactControls` long-presses `By.res("com.android.systemui", "tile_label").text(description)` instead of any text equal to the description. RED was the 21:41 and 21:47 runs (the lifecycle class first, so notifications were allowed); GREEN is the rerun below.
- `TimerSyncServiceLifecycleTest` waits up to 12 s for the sync notification. Android 12 and later may defer showing a foreground service's notification by up to 10 s, and the service runs (`timerSyncService` set after `startForeground`) before it shows. The 21:41 run passed and the 21:47 run failed on this wait alone. The deferral is the platform's and affects only when the silent notification appears.

| When | AVD | Selection | Exit | JUnit | Result |
| --- | --- | --- | --- | --- | --- |
| 21:52 | tokkl-api36 | the 21:47 selection, after both fixes | 0 | 3 tests, 0 failures | GREEN: lose-the-timer-API 7.8 s, sign-out 10.1 s, tile 11.3 s. One `TOKKL_DEVICE_BUILD` receipt (sdk 36), no `AndroidTestRunner failed`. |
| 21:55 | tokkl-api36 | `TimerSyncServiceLifecycleTest`, both compact-control methods of `RunningTileServerTest`, `PomodoroRingingServiceTest`, `PomodoroPermissionPromptTest`, `PomodoroAlarmActivityTest`, `PomodoroPhaseAlarmRearmTest`, `PomodoroSessionNotificationRestartTest` | 0 | 27 tests, 0 failures, 0 skipped | Lifecycle 2, tile 2, ringing 9, permission prompt 3 (one is API 31 to 32 only and not counted on 36), alarm activity 8, boot re-arm 1, session notification restart 2. One receipt (sdk 36), no `AndroidTestRunner failed`. JUnit copies: [evidence/task-12-junit-api36-5](../evidence/task-12-junit-api36-5). |

Owner decision, relayed by the coordinator at about 22:00: no more local device or emulator runs; PR CI runs every instrumented test on API 36 and API 31. The 21:55 run had already finished. No emulator, tokkl container or adb server is left running.

### Evidence matrix

| Primary success criterion | Covered by | Local evidence |
| --- | --- | --- |
| 1. A remote session reaches the backgrounded phone within 2 s | `TimerCrossDeviceServerTest` (remote session) | Task 11: 12 to 23 ms on both APIs. Probes B and C on API 36: 205 to 234 ms, 225 ms in forced deep Doze. |
| 2. The phone rings at the planned end, and a remote Next or End silences it within 2 s. A stale confirm gets 409 and shows the server's state | `TimerCrossDeviceServerTest`, `PomodoroRingingServiceTest`, `PomodoroAlarmActivityTest`, `PomodoroPhaseAlarmTest` | Task 11: ring 48 to 98 ms after the end, silenced 59 to 143 ms after the remote action. 21:55 run: 27/27 on API 36. |
| 3. A session and pause started on the phone show through the API. Pause, Resume and Discard work both ways with the same total | `TimerCrossDeviceServerTest` (phone session; pause, resume and discard) | Task 11: 7/7 on both APIs, totals within 1 s. |
| 4. Toggl Track and Tokkl without the timer API hide Pomodoro and Pause, and Start and Stop still work | `TimerCrossDeviceServerTest` (Tokkl 1.8.1), `PomodoroAvailabilityTest`, `CapabilitiesTest`, `CapabilitiesServerTest`, `TimerSyncServiceLifecycleTest` (capability loss) | Task 11 against a real 1.8.1. JVM `CapabilitiesTest` passed. |
| 5. Offline session and pause actions fail with the message and change nothing, while Start and Stop still queue | `TimerActionsOfflineServerTest`, `TimerActionsTest`, `RunningTileServerTest` (offline long press), `WidgetServerTest`, `ReminderServerTest` | Task 11: 3/3 on both APIs. 21:55 run: tile compact controls 2/2. |
| 6. The legacy types are gone, Room migrates from 8 and keeps the queue, and every suite passes | Task 10's removal scan, `CacheDatabaseMigrationTest`, the JVM suite, all instrumented tests | Removal scan empty. JVM suite 1025 tests with 0 failures and lint with 0 errors at 22:03. The full API 36 and API 31 device suites run in CI on main: run 40767794471 at 380fc01. |

### Measured platform limits (not passes)

- API 31 has no boot, package-replace, restart or Doze measurement. The owner stopped local device runs first.
- Plain AOSP images ship with deep idle off, so the Doze probe had to enable and force it. The arrival's own `setAlarmClock` (60 s ahead, inside `min_time_to_alarm`) ended deep idle, so this was not a long-idle measurement.
- After `adb reboot` the emulator's GL pipe hung and boot-time ANRs hit both the uncompiled app and SystemUI. A cold boot started the `specialUse` service from BOOT_COMPLETED without a refusal. Physical devices are untested.
- Android may show a foreground service's notification up to 10 s after the service starts.
- From Task 05: the stream watchdog does not count deep sleep, and reconnects back off up to 120 s.
- From Task 08: a rearm after a time change uses the stored clock offset until the next state arrives.
- From Tasks 03 and 07: a native surface tapped right after the phone's own edit can get a 409 and show "Another device changed the timer first." The Pomodoro page sends the queue and re-reads the revision first; native surfaces do not.

### Owner decisions to surface

- The 120 s reconnect backoff cap. The plan says only "capped".
- Whether the wording of the 409 after the phone's own edit on native surfaces is acceptable, or whether they should re-read the revision as the Pomodoro page does.

