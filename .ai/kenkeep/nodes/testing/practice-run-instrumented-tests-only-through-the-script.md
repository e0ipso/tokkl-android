---
type: practice
title: Run instrumented tests only through scripts/instrumented-tests.sh
description: >-
  It starts a pinned Tokkl container, seeds an account, passes test filters as
  a file and splits runs below Android 12 into checked batches.
tags:
  - testing
  - android
  - emulator
  - docker
kk_schema_version: 3
kk_id: practice-run-instrumented-tests-only-through-the-script
kk_derived_from:
  - scripts/instrumented-tests.sh
kk_relates_to:
  - practice-test-real-tokkl-server-speed-regressions
  - practice-emulator-plain-aosp-image-headless
  - practice-gradle-no-daemon-in-parallel-worktrees
  - practice-screenshot-references-on-tokkl-api36
kk_depends_on: []
kk_confidence: high
---
Run the instrumented tests with `scripts/instrumented-tests.sh`. A plain `./gradlew connectedDebugAndroidTest` fails every test that reads `TokklTestServer`, on purpose: the server URLs and the account come from instrumentation arguments, and only the script passes them.

The script starts `ghcr.io/e0ipso/tokkl` pinned by digest, Tokkl 1.3.1 today, and publishes it on a free port of 127.0.0.1, which the emulator reaches as `10.0.2.2`. The database sits on `--tmpfs /data:mode=1777`, because the image runs as an unprivileged user that cannot write to a default tmpfs. The script creates the account through `POST /api/app/setup` with a random `TOKKL_SETUP_CODE` and password, reads the API token from `GET /api/v9/me`, and passes `tokklBaseUrl`, `tokklEmail` and `tokklCredentialsFile` to Gradle as `-Pandroid.testInstrumentationRunnerArguments.*`. It removes the containers on exit, also on failure. README "Build and test" says how to move either image to a newer version.

In front of the same server the script runs haproxy, pinned by digest in `PROXY_IMAGE`, in the Tokkl container's network namespace. As a TLS proxy it serves a self-signed certificate that `openssl` makes for each run, with IP SANs for `10.0.2.2` and `127.0.0.1`, so host name checks pass. Its key stays in the script's temporary directory, and cleanup deletes it. The tests get `tokklTlsBaseUrl` and the certificate's `tokklTlsFingerprint`, never the key, through `TokklTestServer.tlsUrl` and `tlsFingerprint`. On another port the proxy serves Tokkl over http under the path prefix `/tokkl` and answers 404 to every other path there, so a client that drops the prefix fails. Tests reach it as `tokklPrefixedBaseUrl`, through `TokklTestServer.prefixedUrl`.

The tests run on the attached device or on `ANDROID_SERIAL`. With neither, the script boots the AVD named by `TOKKL_TEST_AVD`, by default `tokkl-api36`, sets it up as the emulator node describes, and kills it at the end. It changes no setting on a device it did not boot.

The password and the API token never go on a command line, because `ps` shows every process's arguments to every user. AGP passes each instrumentation argument on as a `java -D` option and as `adb shell am instrument -e`, so a secret given as `-P...` shows up there even when it stays off Gradle's own command line. `ORG_GRADLE_PROJECT_*` variables cannot carry them either, because the property names hold dots, which dash drops. So the script writes both values to a mode 600 properties file, pushes it to `/data/local/tmp` on each test device with mode 600 there too, and passes only its path as `tokklCredentialsFile`. `TokklTestServer` reads the file with `cat` through `DeviceShell`, which runs as the shell user through UiAutomation. Cleanup deletes the file and runs `sync`, without which the file came back on the next boot. A run killed with SIGKILL, or ended along with its session, skips that cleanup and leaves its file. So before pushing, the script deletes `tokkl-test-credentials-*.properties` files in `/data/local/tmp` that are more than 6 hours old, with toybox `find -mtime +6h -delete` because toybox has no `-mmin`, and syncs.

Extra arguments go to Gradle. One class runs with `-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest`, one test with `class=<class>#<method>`. A comma-separated `class=` or `package=` list works too. AGP hands the runner all its arguments in one comma-joined string and splits it at every comma, so a raw `class=A,B` ran only `A` and still passed. The script writes such a list to a `tokkl-test-filter-*` file beside the credentials file, one entry per line, and passes its path as `testFile`. It refuses a comma in any other runner argument, and a `testFile` of your own beside a list.

Below Android 12, a run with no runner argument on an emulator the script boots goes in batches, because qemu keeps memory after every OpenGL process there. The script has the runner list the tests first, which leaves out what `@SdkSuppress` leaves out. Then it runs whole classes, at most 20 tests a batch, the script's `BATCH_TESTS`, each batch on a freshly booted emulator against the same server, and keeps each batch's results in `app/build/outputs/androidTest-results/batches/<n>/`. The run fails when a batch fails and when a batch ran a different number of tests than the runner listed. On API 26 AGP once passed a batch it could not install, `INSTALL_FAILED_ALREADY_EXISTS`, with no test run.

After Gradle, the script runs `adb get-state` on every test device and fails, naming the device, when one went away. AGP's test task can pass after a device dies. A run with a filter, or on a device the script did not boot, runs as one, with no count check. There and outside the script, read the test count, not the BUILD line.

`app/build.gradle.kts` sets `execution = "ANDROIDX_TEST_ORCHESTRATOR"`, so every test runs in a new app process. The `*RestartTest` classes need that to prove state survives a process restart.

<!-- kk:related:start -->
# Related

- Related: [practice-test-real-tokkl-server-speed-regressions](practice-test-real-tokkl-server-speed-regressions.md)
- Related: [practice-emulator-plain-aosp-image-headless](practice-emulator-plain-aosp-image-headless.md)
- Related: [practice-gradle-no-daemon-in-parallel-worktrees](../build-and-tooling/practice-gradle-no-daemon-in-parallel-worktrees.md)
- Related: [practice-screenshot-references-on-tokkl-api36](practice-screenshot-references-on-tokkl-api36.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../scripts/instrumented-tests.sh)
<!-- kk:citations:end -->
