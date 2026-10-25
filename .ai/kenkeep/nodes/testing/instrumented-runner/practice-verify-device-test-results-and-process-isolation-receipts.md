---
type: practice
title: Verify device test results and process isolation receipts
description: >-
  Device liveness, nonempty JUnit XML and orchestrated processes matter beyond
  Gradle success.
tags:
  - testing
  - android
  - emulator
kk_schema_version: 3
kk_id: practice-verify-device-test-results-and-process-isolation-receipts
kk_derived_from:
  - scripts/instrumented-tests.sh
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4'
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
  - practice-screenshot-references-on-tokkl-api36
kk_depends_on: []
kk_confidence: high
---
Every run goes in one piece. A Gradle pass does not prove the tests ran. With the emulator killed a second into a run, Gradle printed BUILD SUCCESSFUL and had run none of its 32 tests. With another copy of the app on the device, signed with another key, AGP logged `AndroidTestRunner failed` and `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, ran nothing and still passed. So after Gradle the script fails the run when `adb get-state` shows a test device went away, when the Gradle log holds `AndroidTestRunner failed`, and when `app/build/outputs/androidTest-results/connected/debug` holds fewer JUnit XML files than there are devices or one of them reports zero tests. It deletes that directory before Gradle starts, so an old run's results never count. Outside the script, read the test count, not the BUILD line.

`app/build.gradle.kts` sets `execution = "ANDROIDX_TEST_ORCHESTRATOR"`, so every test runs in a new app process. The `*RestartTest` classes need that to prove state survives a process restart.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-screenshot-references-on-tokkl-api36](../practice-screenshot-references-on-tokkl-api36.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4
<!-- kk:citations:end -->
