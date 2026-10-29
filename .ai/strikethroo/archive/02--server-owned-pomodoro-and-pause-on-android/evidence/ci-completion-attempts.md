# CI completion attempts

No local tests were run during the completion session on 2026-10-29. GitHub
Actions ran the JVM, lint, reproducibility and full API 31/API 36 device checks.

## Run 40827864331, e7d03ec

[CI run](https://github.com/e0ipso/tokkl-android/actions/runs/40827864331).
The build, 1,025 JVM tests, lint, commitlint and reproducibility checks passed.
Ten device shards passed. The complete reports contained two distinct failing
methods, with the lifecycle failure present on both API levels:

- `RunningNotificationRestartTest#step2OpensTheAppInANewProcessAndAnEditPostsTheNotificationAgain`, API 31. The setup dismissed a notification without checking that it represented the new entry or that its restart record was persisted. Commit 4f31350 waits for both before dismissal and keeps the restart and edit assertions.
- `TimerSyncServiceLifecycleTest#aServerThatLosesTheTimerApiStopsTheStreamTheSessionNotificationAndTheAlarm`, API 31 and 36. An in-flight refresh could still be reading the previous server when the test saved the legacy endpoint. Commit 4f31350 waits for a refresh begun after the switch.

## Run 40829864093, 4f31350

[CI run](https://github.com/e0ipso/tokkl-android/actions/runs/40829864093).
The complete run had three distinct failing methods. Both failures above passed on API 31 and API 36. Further reports identified:

- `PomodoroRingingServiceTest#alarmsAllowedDndKeepsTheAlarmPlayerLoopingAndVibrationRequested`, API 31. The shell DND command returned before the requested interruption filter settled. Logcat showed alarms and off mode changes while setup was still applying. Commit 5047082 waits for the exact requested filter during setup and cleanup, sets volume before the final setup mode change, and retains both DND and output assertions.
- `RunningTileServerTest#openingTheShadeOfflineAttemptsNoRequest`, API 31. The test's server-failure probe returned while ConnectivityManager still reported internet access. Commit 18517ff waits for the tile's actual connectivity precondition before switching to the counted relay. Both zero-request assertions remain. Diagnostic connection events also record request paths, without headers or query values, to identify a background caller if a connection recurs.
- `FullscreenTimerLargeTextTest#unsentRunningEditAndFocusedRecreationKeepTheLocalIdentity`, API 36. The title assertion ran before asynchronous editor loading had exposed the running editor in the merged semantics tree. Commit 8aa6057 waits for that title after opening and recreation, keeping the identity and navigation assertions.

Runs 40831823166 and 40832242094 were superseded by 8aa6057 and cancelled to
free runners. No checks were removed or disabled. The full workflow runs on
the final commit.

## Run 40832371274, 8aa6057

[CI run](https://github.com/e0ipso/tokkl-android/actions/runs/40832371274).
All three host checks and 11 of 12 device shards passed. All five previously
failing methods passed. The only failure was
`WidgetServerTest#theWidgetFollowsTheAppNotificationAndTileWithinASecond`
on API 36, waiting for the Quick Settings tile before its measured widget
propagation actions. The widget already showed the expected entry.

The helper issued add/remove shell commands without waiting for SystemUI's
asynchronous settings changes. [Android 16's tile interactor](https://android.googlesource.com/platform/frameworks/base/+/android16-release/packages/SystemUI/src/com/android/systemui/qs/pipeline/domain/interactor/CurrentTilesInteractor.kt)
launches those changes in coroutines. Commit 3ee879e waits for the exact tile
specification to appear/disappear in the secure tile list and reports that
list on a later panel timeout. The exact ordering of SystemUI's state at the
failure was not captured, so that race is inferred from the setup and the
platform implementation. The one-second propagation checks stay intact.

## Run 40835625387, 3ee879e

[CI run](https://github.com/e0ipso/tokkl-android/actions/runs/40835625387).
All three host checks and 10 of 12 device shards passed. The widget/tile test
passed. Two further methods failed:

- `TimerCrossDeviceServerTest#aSessionFromAnotherDeviceReachesTheBackgroundedPhoneAndItsNextAndEndSilenceTheRing`, API 36. The notification's corrected timestamp passed, but the test also required the physical device clock to be within one second of the server clock. Observed skew was 1.050827 seconds. Commit c603aaf derives the expected notification timestamp independently as receipt time plus the remaining server interval and retains exact millisecond equality. This tests correction without requiring pre-aligned clocks. Chronometer flags, phase, alarm delivery and remote silencing assertions remain.
- `HistoryServerTest#offlineOlderDaysLoadOnceTheNetworkComesBack`, API 31. Android first exposed a cellular route and then replaced it with Wi-Fi without another offline interval. ForegroundRefresh debounced the later online update, so an older-page load that failed on the first route never retried. Commit 089a644 forwards online updates to failed history pages while keeping account refreshes debounced and stopped activities silent. A page failure also remembers an online update received while it was loading. New JVM regression tests cover callback deduplication/background behavior and a held, failing page read followed by an online event. Commit 31d9bdb additionally filters NetworkWatch events by default network, INTERNET and VALIDATED, suppresses unchanged initial/signal reports and rejects stale callbacks. Three more JVM tests cover those network transitions without requiring a device.

Run 40839149162 at c603aaf was superseded and cancelled after the network
callback filter was added. No failing result from that incomplete run is
used as validation.

## Final validation

[Run 40839958989](https://github.com/e0ipso/tokkl-android/actions/runs/40839958989)
at 31d9bdb completed successfully with all 18 jobs passing. It published
[v0.6.0](https://github.com/e0ipso/tokkl-android/releases/tag/v0.6.0), including
the signed APK, after every test and reproducibility check passed.

- JVM: 110 suites, 1,030 tests, zero failures/errors/skips.
- API 31: all six shards, 606 tests, zero failures/errors/skips.
- API 36: all six shards, 713 tests, zero failures/errors/skips.
- Android lint: zero errors, seven existing warnings.
- Commitlint and reproducible-release: passed.

[summary.json](ci-40839958989/summary.json) records each shard's counts.
The same folder preserves all 12 device JUnit reports and the three JVM
reports covering the new network/history regressions. The historical local
run files were preserved from the referenced thread; this completion session
ran no local tests.
