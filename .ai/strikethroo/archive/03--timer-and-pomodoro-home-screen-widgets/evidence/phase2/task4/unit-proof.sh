#!/usr/bin/env bash
# Invoke inside the OOM-safe scope and agreed lock. Keep copies tied to this invocation.
set -u
root=.ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task4
mkdir -p "$root/acceptance-inputs"
python3 "$root/capture-inputs.py" "$root/acceptance-inputs/before.sha256"
./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.*' :app:compileDebugAndroidTestKotlin :app:lintDebug > "$root/acceptance.log" 2>&1
result=$?
python3 "$root/capture-inputs.py" "$root/acceptance-inputs/after.sha256"
printf '%s\n' "$result" > "$root/acceptance-exit-code.txt"
mkdir -p "$root/acceptance-junit"
cp app/build/test-results/testDebugUnitTest/TEST-com.github.e0ipso.tokkl.widget.*.xml "$root/acceptance-junit/"
if test "$result" -eq 0; then cp app/build/reports/lint-results-debug.xml "$root/"; fi
exit "$result"
