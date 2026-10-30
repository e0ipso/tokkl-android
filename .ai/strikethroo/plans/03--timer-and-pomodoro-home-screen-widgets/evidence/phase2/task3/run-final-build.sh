#!/usr/bin/env bash
set -uo pipefail
base=.ai/strikethroo/plans/03--timer-and-pomodoro-home-screen-widgets/evidence/phase2/task3
# Caller holds /tmp/tokkl-plan3-gradle.lock through verification and evidence copies.
rm -rf app/build/test-results/testDebugUnitTest
rm -f app/build/reports/lint-results-debug.xml
./gradlew --no-daemon :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:lintDebug > "$base/final-build.log" 2>&1
result=$?
printf '%s\n' "$result" > "$base/final-build-exit-code.txt"
mkdir -p "$base/final-junit"
if [ -d app/build/test-results/testDebugUnitTest ]; then cp app/build/test-results/testDebugUnitTest/TEST-*.xml "$base/final-junit/"; fi
if [ -f app/build/reports/lint-results-debug.xml ]; then cp app/build/reports/lint-results-debug.xml "$base/final-lint.xml"; fi
exit "$result"
