---
type: practice
title: Run instrumented tests only through scripts/instrumented-tests.sh
description: >-
  The script starts a pinned Tokkl container, seeds an account and passes it to
  the tests. Plain connectedDebugAndroidTest fails them.
tags:
  - testing
  - android
  - emulator
  - docker
kk_schema_version: 3
kk_id: practice-run-instrumented-tests-only-through-the-script
kk_derived_from: []
kk_relates_to:
  - practice-test-real-tokkl-server-speed-regressions
  - practice-emulator-plain-aosp-image-headless
  - practice-gradle-no-daemon-in-parallel-worktrees
kk_depends_on: []
kk_confidence: high
---
Run the instrumented tests with `scripts/instrumented-tests.sh`. A plain `./gradlew connectedDebugAndroidTest` fails every test that reads `TokklTestServer`, on purpose: the server URL and the account come from instrumentation arguments, and only the script passes them.

The script starts `ghcr.io/e0ipso/tokkl` pinned by digest and publishes it on a free port of 127.0.0.1, which the emulator reaches as `10.0.2.2`. The database sits on `--tmpfs /data:mode=1777`, because the image runs as an unprivileged user that cannot write to a default tmpfs. The script creates the account through `POST /api/app/setup` with a random `TOKKL_SETUP_CODE` and password, reads the API token from `GET /api/v9/me`, and passes `tokklBaseUrl`, `tokklEmail`, `tokklPassword` and `tokklApiToken` to Gradle as `-Pandroid.testInstrumentationRunnerArguments.*`. It removes the container on exit, also after a failure. To move to a newer Tokkl, take the index digest from `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:<version>`.

The tests run on the attached device or on `ANDROID_SERIAL`. With neither, the script boots the AVD named by `TOKKL_TEST_AVD` (default `tokkl-api36`) without a window and kills it at the end. It stops the adb server only when the server was not running before.

Extra arguments go to Gradle. One class runs with `scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest`.

`app/build.gradle.kts` sets `execution = "ANDROIDX_TEST_ORCHESTRATOR"`, so every test runs in a new app process. `ApiTokenStoreRestartTest` needs that to prove the token survives a process restart, and it fails when its two steps share a process.

To catch clipped text in a Compose test, check `TextLayoutResult.didOverflowHeight`. Semantics hands out a layout redone at the full available width, so `didOverflowWidth` is true for any text narrower than its container (see `LargestFontScaleTest`).

<!-- kk:related:start -->
# Related

- Related: [practice-test-real-tokkl-server-speed-regressions](practice-test-real-tokkl-server-speed-regressions.md)
- Related: [practice-emulator-plain-aosp-image-headless](practice-emulator-plain-aosp-image-headless.md)
- Related: [practice-gradle-no-daemon-in-parallel-worktrees](practice-gradle-no-daemon-in-parallel-worktrees.md)
<!-- kk:related:end -->
