#!/usr/bin/env bash
set -uo pipefail
proof=.ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/parent/api36-final
mkdir -p "$proof"
TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.widget > "$proof/runner.log" 2>&1
result=$?
printf '%s\n' "$result" > "$proof/exit.txt"
if [ -d app/build/outputs/androidTest-results/connected/debug ]; then cp -r app/build/outputs/androidTest-results/connected/debug "$proof/junit"; fi
exit "$result"
