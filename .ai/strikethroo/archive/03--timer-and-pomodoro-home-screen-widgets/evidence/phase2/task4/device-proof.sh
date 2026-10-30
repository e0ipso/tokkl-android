#!/usr/bin/env bash
# Invoke inside the agreed OOM-safe scope and build lock. All reports copy before the lock releases.
set -u
name=$1
root=.ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task4
mkdir -p "$root/$name"
python3 "$root/capture-inputs.py" "$root/$name/inputs-before.sha256"
classes=${2:-"com.github.e0ipso.tokkl.widget.LightPomodoroWidgetScreenshotTest#allSizesStatesAndControlsFitAndMatchReferences,com.github.e0ipso.tokkl.widget.DarkPomodoroWidgetScreenshotTest#allSizesStatesAndControlsFitAndMatchReferences,com.github.e0ipso.tokkl.widget.LightLargestPomodoroWidgetScreenshotTest#allSizesStatesAndControlsFitAndMatchReferences,com.github.e0ipso.tokkl.widget.DarkLargestPomodoroWidgetScreenshotTest#allSizesStatesAndControlsFitAndMatchReferences,com.github.e0ipso.tokkl.widget.PomodoroWidgetClockTest#nativeCountdownAndOvertimeTickWithoutProviderUpdates"}
TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh \
  -Pandroid.testInstrumentationRunnerArguments.class="$classes" \
  > "$root/$name.log" 2>&1
result=$?
python3 "$root/capture-inputs.py" "$root/$name/inputs-after.sha256"
printf '%s\n' "$result" > "$root/$name/exit-code.txt"
if test -d app/build/outputs/androidTest-results/connected; then
  cp -r app/build/outputs/androidTest-results/connected "$root/$name/junit"
fi
if test -d app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected; then
  cp -r app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected "$root/$name/output"
fi
exit "$result"
