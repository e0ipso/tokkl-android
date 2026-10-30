# PR 19 midnight fixture correction

The initial CI run 41113595866 at revision 9ad88d5047c5a167d52a7170a84c0da3ec589542 reported one failure among the 93 cases in API31 shard 2. Its JUnit report is preserved in `ci-api31/`. Other shards were still running when that failure was diagnosed, so this is not a complete initial-run failure inventory.

`WidgetMidnightServerTest#unchangedRealRunningCheckRollsTheAccountDayAndMatchesTimerTotalsWithoutIdleWork` created a running entry at noon on the account-local current day. During the CI run, Asia/Tokyo was before noon. The test injected a clock near account midnight for day-state evaluation, but native Chronometers correctly used real wall time and clamped that future start to zero elapsed time. Converting that native base using the injected clock did not preserve the expected total.

The unchanged selector reproduced the same assertion failure on local API36: one case, one failure, exit 1, with unchanged input hashes. See `red/`. This demonstrates that the failure depends on execution time rather than Android API level.

The fixture now starts the running entry at account midnight, in the past for both clocks. Existing totals, native-base comparison, actual TimerTotals UI, RunningCheck rollover, unchanged Room response, worker uniqueness and idle cancellation assertions are retained. No app implementation or assertion tolerance changed. API31 remains CI-only.

Local verification uses the maintained runner, a serialized OOM-safe inner scope and a detached outer service. Source/test input hashes, commands, exit codes, actual JUnit cases and per-case process receipts are recorded for each local run. Device panel attachment and screenshot succeeded for the corrected run. The earlier red run finished and shut down its owned emulator before panel attachment.

Focused verification after the fixture correction passed the exact selector on local API36: one case, zero failures/errors/skips, exit 0, unchanged run inputs. Both red and green terminal JUnit were independently parsed. See `green/`. The initial CI inventory at that point included successful API31 shards 1 and 4; nine device jobs remained pending and are not credited as passing.

Surrounding verification then passed all three exact API36 selectors: the corrected midnight test, `WidgetServerTest#nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod`, and `WidgetServerTest#billableDayClockAddsOnlyStoppedBillableTimeAndTicksNatively`. Exit 0, three actual JUnit cases, zero failures/errors/skips and unchanged input hashes. See `surrounding/`. Parent verification re-read the terminal XML, compared green/surrounding input manifests and matched every recorded input hash to the current tree. `git diff --check` and the staged check passed. Both APIs' complete CI suites remain a merge gate on the new revision.
