---
type: practice
title: Use the instrumented runner and its device selection
description: >-
  The maintained script supplies required server arguments and owns selected
  emulator lifetimes.
tags:
  - testing
  - android
  - emulator
  - docker
kk_schema_version: 3
kk_id: practice-use-the-instrumented-runner-and-its-device-selection
kk_derived_from:
  - scripts/instrumented-tests.sh
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4'
kk_relates_to:
  - practice-let-the-runner-own-emulator-boot-and-settings
  - practice-test-real-tokkl-server-speed-regressions
  - practice-gradle-no-daemon-in-parallel-worktrees
kk_depends_on: []
kk_confidence: high
---
Run the instrumented tests with `scripts/instrumented-tests.sh`. A plain `./gradlew connectedDebugAndroidTest` fails every test that reads `TokklTestServer`, on purpose: the server URLs and the account come from instrumentation arguments, and only the script passes them.

The tests run on the attached device or on `ANDROID_SERIAL`. With neither, apart from the emulators of other runs, the script boots the AVD named by `TOKKL_TEST_AVD`, by default `tokkl-api36`, or `tokkl-api31` for Android 12, sets it up as the emulator node describes, and kills it at the end. It changes no setting on a device it did not boot.

<!-- kk:related:start -->
# Related

- Related: [practice-let-the-runner-own-emulator-boot-and-settings](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
- Related: [practice-test-real-tokkl-server-speed-regressions](../practice-test-real-tokkl-server-speed-regressions.md)
- Related: [practice-gradle-no-daemon-in-parallel-worktrees](../../build-and-tooling/practice-gradle-no-daemon-in-parallel-worktrees.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4
<!-- kk:citations:end -->
