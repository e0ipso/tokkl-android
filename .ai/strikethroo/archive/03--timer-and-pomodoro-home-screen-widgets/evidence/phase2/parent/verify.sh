#!/usr/bin/env bash
set -uo pipefail
proof=.ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/parent
./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.*' --tests 'com.github.e0ipso.tokkl.reports.ReportsModelTest' --tests 'com.github.e0ipso.tokkl.timer.TimerTotalsTest' :app:compileDebugAndroidTestKotlin :app:lintDebug > "$proof/jvm-lint.log" 2>&1
result=$?
printf '%s\n' "$result" > "$proof/jvm-exit.txt"
cp -r app/build/test-results/testDebugUnitTest "$proof/jvm-results"
cp app/build/reports/lint-results-debug.xml "$proof/lint.xml"
if [ "$result" != 0 ]; then exit "$result"; fi
TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.widget > "$proof/api36.log" 2>&1
result=$?
printf '%s\n' "$result" > "$proof/device-exit.txt"
if [ -d app/build/outputs/androidTest-results/connected/debug ]; then cp -r app/build/outputs/androidTest-results/connected/debug "$proof/api36-results"; fi
exit "$result"
