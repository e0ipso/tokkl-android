---
type: practice
title: 'Test on plain AOSP emulators, headless, and stop them afterwards'
description: >-
  tokkl-api36 and tokkl-api31 use plain AOSP images, since ATD drops SystemUI.
  Only the script boots them, headless and capped.
tags:
  - testing
  - android
  - emulator
  - energy
  - memory
kk_schema_version: 3
kk_id: practice-emulator-plain-aosp-image-headless
kk_derived_from:
  - scripts/instrumented-tests.sh
  - README.md
kk_relates_to:
  - map-oom-kill-ends-agent-session
  - practice-test-real-tokkl-server-speed-regressions
  - map-jdk-android-sdk-and-agent-shells
  - practice-run-instrumented-tests-only-through-the-script
kk_depends_on: []
kk_confidence: high
---
The AVDs use the plain AOSP images, `system-images;android-<level>;default;x86_64`, never a Google APIs, Play Store or Automated Test Device (ATD) image. ATD images are smaller, but they remove SystemUI and the launcher, and the notification, Quick Settings tile and widget tests need both. `tokkl-api36` runs Android 16, the version the app targets, and its captures are the screenshot references. `tokkl-api31` runs Android 12, the oldest version the app supports, and catches a call to an API newer than the device. The owner raised `minSdk` to 31 on 2026-10-19, so the tokkl-api30 and tokkl-api26 AVDs and their images are gone, and the script refuses an AVD below API 31. The SDK repository has no plain AOSP image for API 36.1 or later. README "Run the emulator" has the `avdmanager` commands.

Let `scripts/instrumented-tests.sh` boot the emulator, picked with `TOKKL_TEST_AVD`. It runs `emulator -avd <avd> -memory 2560 -cores 4 -gpu <renderer> -no-window -no-audio -no-boot-anim -no-snapshot -accel on`. The renderer is `lavapipe` for API 36, which drew the screenshot references, and `swiftshader_indirect` for any older image, read from the AVD's `config.ini`. The emulator raises any smaller `-memory` to 2560 MB for an API 34 or later image, so asking for 2048 saves nothing. With `-accel on` the emulator refuses to start when KVM is missing, instead of falling back to slow software emulation. `emulator -accel-check` says whether KVM works.

qemu keeps about 100 MB each time an app process that drew with OpenGL ends, with `lavapipe` and with `swiftshader`. The test orchestrator starts a process for every test, so one full run took qemu to 11 GB. A process that draws with Vulkan leaves about 5 MB. So the script runs `setprop debug.hwui.renderer skiavk` after every boot, and a full run peaks at about 3.5 GB on tokkl-api36 and about 4 GB on tokkl-api31. With `lavapipe` on the API 31 image, HWUI aborted every app at its first frame under `skiavk`, because that Vulkan lacks `VK_KHR_external_semaphore_fd`. SwiftShader's Vulkan has it, which is why images below 36 boot with `swiftshader_indirect`. Every run goes in one piece, with no batches. CI's emulator step sets `skiavk` on both API levels.

When the systemd user manager answers, the emulator runs in a scope of its own with `OOMPolicy=continue`, `MemoryMax=6G` and `MemorySwapMax=0`, and the scope is `PartOf` the caller's unit. An OOM kill then ends only qemu and the test run, and stopping the caller still stops the emulator. Without a user manager, as in CI, the emulator is a plain child process with no cap. Runs take turns through a `flock` on `$XDG_RUNTIME_DIR/tokkl-instrumented-tests-<uid>.lock`, and a waiting run prints the PID, start time and directory of the run that holds it. Without the lock a second run would test on the first run's emulator, which the first run's cleanup stops.

Never start an emulator or an adb server by hand for a test run, not even to poll for readiness. The lock covers only runs of the script, an emulator started by hand gets no scope or memory cap, and an OOM kill of it ends the whole agent session. The script stops the adb server at the end only when it was not running before, so a server started by hand stays up and later runs reuse it.

A fresh headless boot often leaves a "System UI isn't responding" dialog that holds window focus, so injected keys go to the dialog instead of the app. `adb shell settings put global hide_error_dialogs 1` stops new ones and `adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS` closes open ones. The script does both on an emulator it boots, so tests no longer close dialogs themselves. It sets `font_scale` to 1.0 there too: the AVD keeps its font scale across boots, and `SystemFontScale` puts back whatever it read, so one run left at 2.0 made every later class run at 200%. It also sets `show_ime_with_hard_keyboard` to 1, because without it focusing a field never opened the soft keyboard on the headless emulator. It changes no setting on a device it did not boot. CI's emulator step sets `hide_error_dialogs` and `show_ime_with_hard_keyboard` itself.

This machine is an always-on homelab server tuned for low idle power. After a run outside the script, run `adb emu kill` and then `adb kill-server`, so neither the emulator nor the adb server keeps running. Run `adb shell sync` first when a change on the emulator's disk has to survive. A file the script deleted without it was back on the next boot. To look for leftovers, use `pgrep -x adb` and `pgrep -af qemu-system`. `pgrep -f adb` also matches `mariadbd` and the shell that runs the command.

`adb shell screenrecord` stopped with SIGINT has lost roughly the last 1 to 2 s of video. Hold the final screen at least that long before stopping a recording.

For a still capture of the whole screen, use `adb exec-out screencap -p`. `UiAutomation.takeScreenshot` has come back black on `lavapipe` while `screencap` showed the screen. The launch splash is drawn by the system before the app runs, so no test can capture it. On tokkl-api31 the splash showed its icon only for a start from the launcher. `LaunchSplashTest` checks the theme and drawables Android draws it from instead.

<!-- kk:related:start -->
# Related

- Related: [map-oom-kill-ends-agent-session](../build-and-tooling/map-oom-kill-ends-agent-session.md)
- Related: [practice-test-real-tokkl-server-speed-regressions](practice-test-real-tokkl-server-speed-regressions.md)
- Related: [map-jdk-android-sdk-and-agent-shells](../build-and-tooling/map-jdk-android-sdk-and-agent-shells.md)
- Related: [practice-run-instrumented-tests-only-through-the-script](practice-run-instrumented-tests-only-through-the-script.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../scripts/instrumented-tests.sh)
[2] [README.md](../../../../README.md)
<!-- kk:citations:end -->
