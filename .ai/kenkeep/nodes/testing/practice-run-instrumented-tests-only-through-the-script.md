---
type: practice
title: Run instrumented tests only through scripts/instrumented-tests.sh
description: >-
  It starts a pinned Tokkl container behind a TLS proxy, seeds an account,
  passes test filters as a file and fails a run Gradle passed without tests.
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

The script starts `ghcr.io/e0ipso/tokkl` pinned by digest, Tokkl 1.5.0 today, and publishes it on a free port of 127.0.0.1, which the emulator reaches as `10.0.2.2`. The database sits on `--tmpfs /data:mode=1777`, because the image runs as an unprivileged user that cannot write to a default tmpfs. The script creates the account through `POST /api/app/setup` with a random `TOKKL_SETUP_CODE` and password, reads the API token from `GET /api/v9/me`, and passes `tokklBaseUrl`, `tokklEmail` and `tokklCredentialsFile` to Gradle as `-Pandroid.testInstrumentationRunnerArguments.*`. It removes the containers on exit, also on failure. README "Build and test" says how to move either image to a newer version.

In front of the same server the script runs haproxy, pinned by digest in `PROXY_IMAGE`, in the Tokkl container's network namespace. As a TLS proxy it serves a self-signed certificate that `openssl` makes for each run, with IP SANs for `10.0.2.2` and `127.0.0.1`, so host name checks pass. Its key stays in the script's temporary directory, and cleanup deletes it. The tests get `tokklTlsBaseUrl` and the certificate's `tokklTlsFingerprint`, never the key, through `TokklTestServer.tlsUrl` and `tlsFingerprint`. On another port the proxy serves Tokkl over http under the path prefix `/tokkl` and answers 404 to every other path there, so a client that drops the prefix fails. Tests reach it as `tokklPrefixedBaseUrl`, through `TokklTestServer.prefixedUrl`.

The proxy serves the same server on two more TLS ports. Port 8444 has a second self-signed certificate with the same names and subject and a key of its own, as after the server's key was regenerated. Tests reach it through `TokklTestServer.newKeyTlsUrl` and `newKeyTlsFingerprint`, as `NewKeyWhileSignedInTest` does. Port 8445 has a certificate that a CA made for the run signed, reached through `TokklTestServer.userCaTlsUrl`. On an emulator it boots, the script installs that CA as a user CA, the way Settings > Security > Install a certificate would. Only root may write `/data/misc/user/0/cacerts-added`, so it copies the CA there with `adb shell su 0`, which the userdebug AOSP images allow, under the old OpenSSL subject hash plus `.0`. Cleanup deletes it, and a CA left by a killed run has the same name, so the next run replaces it. Mark a test that needs the CA `@UserCaInstalled`, as `UserCaCertificateTest` is. On a device the script did not boot it installs nothing and leaves those tests out through the runner's `notAnnotation` argument, unless `TOKKL_TEST_THROWAWAY_EMULATOR=1` vouches that every attached device is an emulator thrown away after the run, as in CI. The script then installs the CA there too, and it refuses the variable for a device that is not an emulator or whose `su` does not run as root.

The tests run on the attached device or on `ANDROID_SERIAL`. With neither, apart from the emulators of other runs, the script boots the AVD named by `TOKKL_TEST_AVD`, by default `tokkl-api36`, or `tokkl-api31` for Android 12, sets it up as the emulator node describes, and kills it at the end. It changes no setting on a device it did not boot.

The password and the API token never go on a command line, because `ps` shows every process's arguments to every user. AGP passes each instrumentation argument on as a `java -D` option and as `adb shell am instrument -e`, so a secret given as `-P...` shows up there even when it stays off Gradle's own command line. `ORG_GRADLE_PROJECT_*` variables cannot carry them either, because the property names hold dots, which dash drops. So the script writes both values to a mode 600 properties file, pushes it to `/data/local/tmp` on each test device with mode 600 there too, and passes only its path as `tokklCredentialsFile`. `TokklTestServer` reads the file with `cat` through `DeviceShell`, which runs as the shell user through UiAutomation. Cleanup deletes the file and runs `sync`, without which the file came back on the next boot. A run killed with SIGKILL, or ended along with its session, skips that cleanup and leaves its file. So before pushing, the script deletes `tokkl-test-credentials-*.properties` files in `/data/local/tmp` that are more than 6 hours old, with toybox `find -mtime +6h -delete` because toybox has no `-mmin`, and syncs.

Extra arguments go to Gradle. One class runs with `-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest`, one test with `class=<class>#<method>`. A comma-separated `class=` or `package=` list works too. AGP hands the runner all its arguments in one comma-joined string and splits it at every comma, so a raw `class=A,B` ran only `A` and still passed. The script writes such a list to a `tokkl-test-filter-*` file beside the credentials file, one entry per line, and passes its path as `testFile`. It refuses a comma in any other runner argument, and a `testFile` of your own beside a list.

Every run goes in one piece. A Gradle pass does not prove the tests ran. With the emulator killed a second into a run, Gradle printed BUILD SUCCESSFUL and had run none of its 32 tests. With another copy of the app on the device, signed with another key, AGP logged `AndroidTestRunner failed` and `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, ran nothing and still passed. So after Gradle the script fails the run when `adb get-state` shows a test device went away, when the Gradle log holds `AndroidTestRunner failed`, and when `app/build/outputs/androidTest-results/connected/debug` holds fewer JUnit XML files than there are devices or one of them reports zero tests. It deletes that directory before Gradle starts, so an old run's results never count. Outside the script, read the test count, not the BUILD line.

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
