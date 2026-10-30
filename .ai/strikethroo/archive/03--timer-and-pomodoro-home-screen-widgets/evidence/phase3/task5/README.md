# Task 5 acceptance evidence

Task 5 runtime acceptance is complete within the authorized local API36 scope. All six sequential CI shards passed the exact 736-case suite with zero failures/errors/skips. All 33 widget methods and eight reference matrices passed. Fresh JVM/compile/lint/npm checks pass, and all 1,011 current input files match every accepted final run. API31 remains deferred to existing CI by the user. Self Validation 7's audit is performed; task 6 owns documentation updates and the parent owns the subsequent doc-match and phase gates. Whole-plan documentation success is not claimed here.

## Final-input verification

| Receipt | Terminal result |
| --- | --- |
| `api36-final-shard-0/` | Exit 0; 134 cases, 0 failures/skips; BUILD SUCCESSFUL in 22m 13s. |
| `api36-final-shard-1/` | Exit 0; 108 cases, 0 failures/skips; BUILD SUCCESSFUL in 17m 8s. |
| `api36-final-shard-2/` | Exit 0; 108 cases, 0 failures/skips; BUILD SUCCESSFUL in 17m 18s. |
| `api36-final-shard-3/` | Exit 0; 132 cases, 0 failures/skips; BUILD SUCCESSFUL in 20m 11s. |
| `api36-final-shard-4/` | Exit 0; 127 cases, 0 failures/skips; BUILD SUCCESSFUL in 17m 6s. |
| `api36-final-shard-5/` | Exit 0; 127 cases, 0 failures/skips; BUILD SUCCESSFUL in 23m 56s. |
| `final-host-corrected/` | Exit 0; 1,069 fresh JVM cases, 0 failures/errors/skips; Android test compilation executed; lint 0 Error/Fatal, 12 warnings and 1 hint; BUILD SUCCESSFUL in 12m 49s. |
| `final-npm-corrected/` | Exit 0; 11 Node tests passed, 0 failed/cancelled/skipped, including the wrapper executing five Python discovery lifecycle cases. |

Every accepted shard has matching actual per-case start/finish events and identical source/test/resource/build/runner/npm hashes before/after and against the fresh host/npm runs. `terminal-audit.json` and `resource-summary.json` retain each shard's evidence. `final-shard-union-audit.json` proves exactly 736 expected cases with no omissions, duplicates or unexpected tests, all 33 widget methods, all eight widget matrix classes (208 reference comparisons), six distinct owned boots and all eight ordered restart groups kept whole. `final-current-input-audit.json` verifies all 1,011 current files against both manifests of all eight accepted final host/npm/shard runs. The aggregate current-manifest SHA-256 is `5f95d4ca29d44b45828490e8dcc241943a443b20438ba6473f2c0a97d531aa39`. The parent's independent XML/current-input check is `parent-shard-xml-audit.json`.

`acceptance-map.json` maps all seven Self Validation steps and four plan success criteria to exact selectors, shard/case receipts, host reports, visual review and scoped deferrals. `audit-completed-shard.py`, `audit-shards.py`, `audit-final-inputs.py` and `map-acceptance.py` reproduce these checks. The older `partial-shard-union-audit.json` is retained as progress-only evidence, not the terminal gate.

`final-resource-summary.json` records a maximum sampled cumulative emulator peak of 4,958,023,680 bytes under the 6 GiB cap and zero sampled emulator swap across all six runs. Owned scope journals retain terminal resource receipts. `final-runtime-cleanup.json` verifies all 18 final service/inner/emulator units inactive; a final Device list showed both API36/API31 off and no open panel attachment. No unrelated runtime was changed. Final `git diff --check` passed (exit 0); its receipt is `final-diff-check.json`.

The forced host command is `./gradlew --no-daemon --rerun-tasks :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`. All 47 tasks executed; 115 fresh JUnit XML files contain the 1,069 JVM cases. `host-audit.json` checks compilation, lint and matching final inputs. `npm-summary.json` records the native 11-test output and names; generic JUnit `cases=0` is not used as npm's test count. Host/npm need rerunning only if their inputs change.

## Commands and runtime ownership

Each device invocation is `env TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh --no-daemon`, plus the recorded focused selector or existing CI arguments `-Pandroid.testInstrumentationRunnerArguments.numShards=6 -Pandroid.testInstrumentationRunnerArguments.shardIndex=N`, N=0 through 5. Shards run sequentially, with no custom class batching. The shipped `StepAwareShardingFilter` keeps ordered restart classes whole. API31 is never booted locally.

Every heavy command is serialized through `flock --close /tmp/tokkl-plan3-gradle.lock systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- ...`. Durable named user services give the job an independent lifetime while retaining that required inner scope and Gradle `--no-daemon`. Each `launcher.json` records the complete service argv and safe PATH/JAVA_HOME/Android SDK allowlist; `command.json` records the actual test argv. No secret is printed or put into process arguments.

The maintained runner owns bounded lavapipe/read-only emulator boot, stock LatinIME selection/verification, test server, private credentials, authenticated gRPC discovery publication and cleanup. The ownership-checked discovery link preserves the private token-bearing record; no unauthenticated gRPC or global service restart was used. Fresh device-list/open/screenshot calls succeeded on runner-owned boots. Panel attachments close without requesting shutdown; only the runner ends its emulator. No manual UI input or adb UI fallback is used.

`run-evidence.py` retains input SHA-256 manifests, terminal exit, fresh JUnit XML, case inventories and TestRunner process receipts. Artifacts older than run start are excluded. Raw discovery logs are never counted as execution. `sample-progress.py` records owned service/scope/emulator metrics and shared host memory pressure read-only; these progress observations never replace terminal XML. Scope journals retain final resource observations. The existing boot-memory wait applies only when another emulator slot is held and is not claimed as a general single-emulator memory guard. Competing host scopes are untouched.

## RED/GREEN and fixture ownership

| Receipt | Result and purpose |
| --- | --- |
| `api36-diagnostic-focused/` → `api36-discovery-auth-focused/` | Original two selectors: 2/2 failures, then 2/2 pass after proper replacement sign-in and native host ownership cleanup. |
| `midnight-clock-red-isolated/` → `api36-acceptance-focused/` | Intended midnight RED expected 2026-10-31, got 2026-10-30; GREEN with the narrow event Clock seam restored. The earlier `midnight-clock-red/` map-lookup failure is invalid as rollover RED. |
| `api36-acceptance-focused/` | 10 cases, 1 failure, 0 skips: midnight, native matching/stale redraw, denied scheduler, Billable ticking and five Pomodoro workflows pass; stale native action capture was isolated next. |
| `api36-stale-tap-red-focused/` → `api36-stale-tap-green-focused/` | Exact 1-case RED/GREEN. Both same-phase old Next and End revisions are refused, preserve server state and adopt the conflict response. |
| `api36-full-final/` | First complete inventory: 736 cases, 10 failures, 0 skips, unchanged inputs. All 33 widget methods and eight reference matrices passed. Exact ten failures are in `complete-failure-selectors.txt` and `terminal-nonpass.json`. |
| `api36-full-failures-diagnostic-red/` → `api36-full-failures-green/` | Exact complete ten-case RED: 10 failures; GREEN: all 10 pass, 0 skips, unchanged inputs. |
| `api36-affected-surrounding/` | All 41 cases in the five affected classes pass, 0 skips, unchanged inputs; exact case set verified against the full inventory. |
| `api36-crash-selector-isolated/` | After the later infrastructure interruption, the unchanged interrupted selector passes: 1 case in 83.282s, matching actual start/finish, 0 failures/skips. |

The replacement-account fixture restores the HTTP confirmation cleared by production sign-out, uses production `SignIn` to read the replacement account's own `/me`, and asserts replacement user/workspace identity. Account/revision/forgotten-field guards remain intact.

`NativeWidget` reserves host IDs in a locked persistent file across isolated processes and skips occupied legacy IDs without deleting them. Cleanup asserts removal of its own host/widget. Partial construction releases owned resources while preserving the original error; teardown tolerates preparation failing before the host is assigned. No global host/widget deletion is used.

The stale-action fixture stops SSE after proving remote arrival, settles the native view, and asserts its held revision remains old while the server revision advances in the same phase. A retained View alone cannot freeze extras of a reused PendingIntent. Next and End must each produce refusal feedback, leave authoritative state unchanged and adopt the conflict response. The dedicated remote-arrival test keeps SSE active.

Four scheduling assertions had counted framework Glance work. Only the two fixture `allWork()` helpers changed: they query all states for the positive union of the three current app Worker class tags (`BackgroundSendWorker`, `RunningCheckWorker`, `ReminderWorker`). Existing unique-work, idle/empty/single, backoff/retry and cancellation assertions remain intact; unrelated framework work is neither deleted nor cancelled.

Six keyboard failures shared one actual environment cause. Temporary diagnostics showed selected `com.callstack.agentdevice.imehelper/.TestInputMethodService`, secure flag 1, portrait rotation 0, fullscreen false, focused active input and zero input-view height on a boot with no panel attached. The runner now enables/selects/verifies installed stock `com.android.inputmethod.latin/.LatinIME` only in `boot_emulator()`. Attached/CI/user devices take no changed setup path; the helper remains installed/enabled. Diagnostics were removed. `ime-tests-preserved.json` proves all three original keyboard-test files, including real touch, IME geometry and state assertions, are byte-identical to the first full inputs. `failure-inventory.json` and the diagnostic directories preserve exact selectors and causes.

## Visual references

`api36-record-changed-references-durable/` executed all eight widget screenshot classes and all 208 matrix captures. Its eight failures are expected reference differences only: 40 actual/diff pairs, zero layout/accessibility failures, zero skips and unchanged inputs; 168 captures already matched. The matrix covers both providers, four host sizes including tiny fallback, light/dark, normal/largest text and all supplied states.

Reviewed every changed actual/diff pair at readable enlarged size in `visual-review/changed-01.png` through `changed-10.png`. The 36 Pomodoro differences are the phase-colour band for three active phases at three intended sizes across four theme/font variants. Four Timer differences are the running Billable value at 375x338 across those variants. Words, clocks, context and controls remain readable; no unexpected geometry change was accepted.

Copied exactly those 40 original capture PNGs into screenshot assets and preserved 168 others. `visual-review/manifest.json` records every capture/diff/contact sheet, old/new SHA-256, finding and destination. `reference-copy-audit.json` proves the 40 approved bytes match and the other 168 references match their pre-recording bytes. All eight matrix tests passed across the final six shards on the unchanged reviewed references.

## Clock, permission and documentation scope

`WidgetMidnightServerTest` uses `TokklApplication.widgetClock`, defaulting to `Clock.systemUTC()`, to advance only the widget's account-day event read. Real account, Room cache and existing real `RunningCheck` remain in use; an unchanged server response leaves Room unchanged but moves the day. Before rollover the native Chronometer base matches the Timer page's same-cache total to one second and the actual `TimerTotals` component is rendered. After rollover it renders/compares zero, keeps the running entry on its start day, confirms one running worker and cancellation at idle. OS/network/alarm time stays untouched; no idle scheduling was added.

API36 directly grants `USE_EXACT_ALARM`; denial is injected into the production native scheduler, not presented as a real revocation. `PomodoroDeniedAlarmWidgetTest` checks one pending inexact redraw, exact replacement/cancellation and no ringing under denial. `PomodoroWidgetRedrawTest` delivers real PendingIntent broadcasts: stale phases cannot redraw, matching phases redraw, neither advances/ends a session or rings. The live deadline test proves overtime within a second independently of an alarm/ringing service. Existing real ringing/full-screen tests remain part of the full 736-case suite. Inexact recovery is best effort under Android scheduling.

Current PRD and completed plan 02 supersede old offline Pause/Resume wording: ordinary Start/Stop/templates queue offline; Pause/Resume and server-owned session actions need a connection. Self Validation 7 is the audit performed here: PRD N3/built/Battery and old widget knowledge are stale, and a separate Pomodoro widget node is missing. Task 6 owns updates; the parent verifies the later doc-match. Whole-plan documentation success is not claimed here.

## Seven Self Validation steps

| Step | Exact proof |
| --- | --- |
| 1. Build/install/sign-in | Maintained-runner focused runs and final CI shards build/install and sign disposable accounts into pinned Tokkl 1.10.0. All six final shard receipts passed. |
| 2. Sizes/states/mockups | Eight matrix classes, 208 captures, both providers/all host sizes, grouping/no project, paused/idle/running, normal/largest text and accessibility. All 40 changed actual/diffs reviewed; all eight matrix tests pass in the exact final union. |
| 3. Same-cache total/midnight | `WidgetMidnightServerTest#unchangedRealRunningCheckRollsTheAccountDayAndMatchesTimerTotalsWithoutIdleWork`: valid isolated date RED and focused GREEN; real native base and actual Timer totals before/after. |
| 4. Offline controls/Today | `WidgetServerTest#startAndStopWorkOfflineThroughTheNativeControls`, `#favoritesResizeAndEachNativeTapQueuesAllItsFieldsOffline`, `#pauseResumeAndStopWhilePausedGoToTheServerFromTheNativeControls`, `#anOfflinePauseSaysWhyAndKeepsTheTimerWhileStopStillQueues`; `WidgetNavigationTest#todayOpensOnColdStartAndReplacesRetainedWeeklySelection`. All selectors pass in the final union; `acceptance-map.json` gives each exact shard receipt. |
| 5. Pomodoro/server/deadline/ringing | All six `PomodoroWidgetServerTest` workflows; native clock, denied scheduler and matching/stale redraw tests; existing `PomodoroAlarmActivityTest`, `PomodoroRingingServiceTest`, notification and cross-device real-phase integration. All listed behavior and existing ringing/full-screen integration pass in the final exact union. |
| 6. Suites/references | Focused exact RED/GREEN and surrounding 41 pass; fresh JVM 1,069, Android test compile, lint and npm 11 pass. Final API36 union: exactly 736 cases passed, zero failures/errors/skips, no omissions/duplicates. API31 is CI-deferred. |
| 7. Documentation audit | PRD N3/built/Battery and widget knowledge audited; stale/missing content recorded. Task 6 owns updates and parent doc-match; no premature whole-plan documentation claim. |

## Four plan success criteria

1. Local API36 Timer layout/font/accessibility, ticking day/Billable clocks and all 208 reference comparisons pass. API31 remains CI-deferred.
2. Same-cache midnight has valid RED/GREEN and final full-suite proof. Native ordinary offline actions, online Pause/Resume and Today navigation all pass; exact selectors/shards are in `acceptance-map.json`.
3. Second provider, remote arrival/countdown, defaults/last fields, guarded Next/End/Start, offline feedback, live overtime, safe fallback and existing real ringing/full-screen integration all pass in the final exact union.
4. Local API36 full suite, fresh JVM/compile/lint/npm and reviewed references pass on identical current inputs. API31 CI and task-6 documentation/doc-match remain downstream; whole-plan criterion 4 is not claimed before those scopes finish.

## Interrupted attempts retained distinctly

`api36-record-changed-references/` unexpectedly ended with exit 143 and no terminal receipt. Its incomplete artifacts supply no references or acceptance. No OOM/runtime-limit/cancellation cause was confirmed. Durable detached services then avoided dependence on foreground exec lifetime. Earlier `api36-initial/` and invalid/interrupted attempts also supply no acceptance.

`api36-full-corrected/` ended after a QEMU hang/exit 139: 535 passing cases, one unfinished `TimerActionsOfflineServerTest#sessionStartNextAndEndFailOfflineOnThePageTheNotificationsAndTheAlarm` with empty failure after 285.721s, and 200 unexecuted. The 200 remaining raw files contain discovery PID 2427 and near-zero-duration discovery pairs, not execution. `interruption-inventory.json`, `terminal-nonpass.json`, raw/actual process audits, host-after-crash diagnostics and resource samples preserve this distinction. QEMU main/CPU threads were unresponsive for 16–27s while shared host PSI showed sustained stalls. The owned emulator stayed below its 6 GiB cap (maximum recorded cumulative peak 5,162,627,072 bytes, no swap), with no OOM entry in its journal. No app-source fix was inferred; the same selector passed unchanged in isolation before the sequential CI-shard recovery.
