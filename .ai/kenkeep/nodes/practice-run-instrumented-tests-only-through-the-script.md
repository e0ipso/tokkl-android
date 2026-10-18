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

The script starts `ghcr.io/e0ipso/tokkl` pinned by digest and publishes it on a free port of 127.0.0.1, which the emulator reaches as `10.0.2.2`. The database sits on `--tmpfs /data:mode=1777`, because the image runs as an unprivileged user that cannot write to a default tmpfs. The script creates the account through `POST /api/app/setup` with a random `TOKKL_SETUP_CODE` and password, reads the API token from `GET /api/v9/me`, and passes `tokklBaseUrl`, `tokklEmail` and `tokklCredentialsFile` to Gradle as `-Pandroid.testInstrumentationRunnerArguments.*`. It removes the containers on exit, also after a failure. To move to a newer Tokkl, take the index digest from `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:<version>`.

In front of the same server the script runs haproxy, pinned by digest in `PROXY_IMAGE`, as a TLS proxy in the Tokkl container's network namespace. It makes a self-signed certificate for each run with `openssl`. The certificate has IP SANs for `10.0.2.2` and `127.0.0.1`, so host name checks pass. Its key stays in the script's temporary directory, and cleanup deletes it. The tests get `tokklTlsBaseUrl` and the certificate's `tokklTlsFingerprint`, never the key, through `TokklTestServer.tlsUrl` and `tlsFingerprint`. The certificate prompt and pinning tests use it. To move to a newer haproxy, take the index digest of `haproxy:<version>-alpine` the same way.

The tests run on the attached device or on `ANDROID_SERIAL`. With neither, the script boots the AVD named by `TOKKL_TEST_AVD` (default `tokkl-api36`) without a window and kills it at the end. It stops the adb server only when the server was not running before. On an emulator it booted, it also sets `hide_error_dialogs` to 1 and sends `CLOSE_SYSTEM_DIALOGS`, so tests carry no dialog workaround of their own. It sets `font_scale` to 1.0 there too: the AVD keeps its font scale across boots, and `SystemFontScale` puts back whatever it read, so one run left at 2.0 made every later class run at 200%. It also sets `show_ime_with_hard_keyboard` to 1, because without it focusing a field never opened the soft keyboard there: the keyboard reported visible with a height of 0. It changes no setting on a device it did not boot.

The password and the API token never go on a command line, because `ps` shows every process's arguments to every user. AGP passes each instrumentation argument on as a `java -D` option and as `adb shell am instrument -e`, so a secret given as `-P...` shows up there even when it stays off Gradle's own command line. `ORG_GRADLE_PROJECT_*` variables are no way around it either. The property names hold dots, which bash cannot export and dash, which runs `gradlew`, drops. So the script writes both values to a mode 600 properties file, pushes it to `/data/local/tmp` on each test device with mode 600 there too, and passes only its path as `tokklCredentialsFile`. `TokklTestServer` reads the file with `cat` through `DeviceShell`, which runs as the shell user through UiAutomation. Cleanup deletes the file and runs `sync` before the emulator is killed. Without `sync` the deleted file was back on the next boot.

After Gradle, the script runs `adb get-state` on every test device and fails, naming the device, when one went away. AGP's test task can pass after a device dies. With qemu killed a second into a run, Gradle printed BUILD SUCCESSFUL and had run none of the 32 tests. A run outside the script has no such check, so read the test count, not the BUILD line.

Extra arguments go to Gradle. One class runs with `scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest`. Run one class per invocation. With the orchestrator on, a comma-separated `class=` or `package=` list has run only its first entry, so check the test count.

`app/build.gradle.kts` sets `execution = "ANDROIDX_TEST_ORCHESTRATOR"`, so every test runs in a new app process. `ApiTokenStoreRestartTest` needs that to prove the token survives a process restart, and it fails when its two steps share a process.

To catch clipped text in a Compose test, check `TextLayoutResult.didOverflowHeight`. Semantics hands out a layout redone at the full available width, so `didOverflowWidth` is true for any text narrower than its container (see `LargestFontScaleTest`). A single line of Compose text keeps the font's ascent above it and its descent below it whatever `lineHeight` the style sets, so a line height that is too short only shows between lines. `ClockAndTotalStylesFitTest` puts its samples in a column narrower than the text, so they wrap. For dialogs, `LargestFontScaleTest.assertDialogFits` checks that the dialog stays in its window and that its scrolling area, title and buttons do not overlap. A test there marked `@RefusedSession` starts signed in to a server whose requests wait for a trust answer.

<!-- kk:related:start -->
# Related

- Related: [practice-test-real-tokkl-server-speed-regressions](practice-test-real-tokkl-server-speed-regressions.md)
- Related: [practice-emulator-plain-aosp-image-headless](practice-emulator-plain-aosp-image-headless.md)
- Related: [practice-gradle-no-daemon-in-parallel-worktrees](practice-gradle-no-daemon-in-parallel-worktrees.md)
<!-- kk:related:end -->
