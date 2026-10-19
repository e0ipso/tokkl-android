---
type: practice
title: 'Test on plain AOSP emulators, headless, and stop them afterwards'
description: >-
  tokkl-api36, -api30 and -api26 use plain AOSP images, since ATD drops
  SystemUI. Only the script boots them, headless and capped.
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
The AVDs use the plain AOSP images, `system-images;android-<level>;default;x86_64`, never a Google APIs, Play Store or Automated Test Device (ATD) image. ATD images are smaller, but they remove SystemUI and the launcher, and the notification, Quick Settings tile and widget tests need both. `tokkl-api36` runs Android 16, the version the app targets, and its captures are the screenshot references. `tokkl-api30` runs Android 11 and covers the code paths before Android 12. `tokkl-api26` runs Android 8.0, the oldest version the app supports, and catches a call to an API newer than the device. There is no API 28 AVD: there the `swiftshader` and `host` renderers reached 6 GB within 20 tests, and `lavapipe` crashed after about 20. The SDK repository has no plain AOSP image for API 36.1 or later. README "Run the emulator" has the `avdmanager` commands.

Let `scripts/instrumented-tests.sh` boot the emulator, picked with `TOKKL_TEST_AVD`. It runs `emulator -avd <avd> -memory 2560 -cores 4 -gpu lavapipe -no-window -no-audio -no-boot-anim -no-snapshot -accel on`. The emulator raises any smaller `-memory` to 2560 MB for an API 34 or later image, so asking for 2048 saves nothing. With `-accel on` the emulator refuses to start when KVM is missing, instead of falling back to slow software emulation. `emulator -accel-check` says whether KVM works.

qemu keeps about 100 MB each time an app process that drew with OpenGL ends, with `lavapipe` and with `swiftshader`. The test orchestrator starts a process for every test, so one full run took qemu to 11 GB. A process that draws with Vulkan leaves about 5 MB. So on Android 12 and later the script runs `setprop debug.hwui.renderer skiavk` after boot, and a full run on tokkl-api36 peaks at about 3.5 GB. Below Android 12 HWUI aborts every app at its first frame with `skiavk`: the API 28 image has no Vulkan, and the API 30 image's Vulkan lacks `VK_KHR_external_semaphore_fd`. Apps there draw with OpenGL, and a full run there goes in batches, each on a fresh emulator. CI's emulator step sets the same property from API 31 on.

When the systemd user manager answers, the emulator runs in a scope of its own with `OOMPolicy=continue`, `MemoryMax=6G` and `MemorySwapMax=0`, and the scope is `PartOf` the caller's unit. An OOM kill then ends only qemu and the test run, and stopping the caller still stops the emulator. Without a user manager, as in CI, the emulator is a plain child process with no cap. Runs take turns through a `flock` on `$XDG_RUNTIME_DIR/tokkl-instrumented-tests-<uid>.lock`, and a waiting run prints the PID, start time and directory of the run that holds it. Without the lock a second run would test on the first run's emulator, which the first run's cleanup stops.

Never start an emulator or an adb server by hand for a test run, not even to poll for readiness. The lock covers only runs of the script, an emulator started by hand gets no scope or memory cap, and an OOM kill of it ends the whole agent session. The script stops the adb server at the end only when it was not running before, so a server started by hand stays up and later runs reuse it.

A fresh headless boot often leaves a "System UI isn't responding" dialog that holds window focus, so injected keys go to the dialog instead of the app. `adb shell settings put global hide_error_dialogs 1` stops new ones and `adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS` closes open ones. The script does both on an emulator it boots, so tests no longer close dialogs themselves. It sets `font_scale` to 1.0 there too: the AVD keeps its font scale across boots, and `SystemFontScale` puts back whatever it read, so one run left at 2.0 made every later class run at 200%. It also sets `show_ime_with_hard_keyboard` to 1, because without it focusing a field never opened the soft keyboard on the headless emulator. It changes no setting on a device it did not boot. CI's emulator step sets `hide_error_dialogs` and `show_ime_with_hard_keyboard` itself.

This machine is an always-on homelab server tuned for low idle power. After a run outside the script, run `adb emu kill` and then `adb kill-server`, so neither the emulator nor the adb server keeps running. Run `adb shell sync` first when a change on the emulator's disk has to survive. A file the script deleted without it was back on the next boot. To look for leftovers, use `pgrep -x adb` and `pgrep -af qemu-system`. `pgrep -f adb` also matches `mariadbd` and the shell that runs the command.

`adb shell screenrecord` stopped with SIGINT has lost roughly the last 1 to 2 s of video. Hold the final screen at least that long before stopping a recording.

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
