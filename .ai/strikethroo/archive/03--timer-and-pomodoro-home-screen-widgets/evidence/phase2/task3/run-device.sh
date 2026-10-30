#!/usr/bin/env bash
set -uo pipefail
name=$1
classes=$2
base=.ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task3
mkdir -p "$base/$name"
# Run with the shared build lock held. Never let a prior capture count as current evidence.
rm -rf app/build/outputs/connected_android_test_additional_output
TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh "-Pandroid.testInstrumentationRunnerArguments.class=$classes" > "$base/$name.log" 2>&1
result=$?
printf '%s\n' "$result" > "$base/$name/exit-code.txt"
if [ -d app/build/outputs/androidTest-results/connected/debug ]; then
    cp -a app/build/outputs/androidTest-results/connected/debug "$base/$name/junit"
fi
if [ -d app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected ]; then
    cp -a app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected "$base/$name/captures"
fi
exit "$result"
