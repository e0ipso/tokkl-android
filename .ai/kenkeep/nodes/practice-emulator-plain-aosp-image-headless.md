---
type: practice
title: 'Test on the plain AOSP API 36 emulator, headless, and stop it afterwards'
description: >-
  AVD tokkl-api36 uses the AOSP image, since ATD images drop SystemUI and the
  launcher. Boot it with -no-window -accel on and kill it after.
tags:
  - testing
  - android
  - emulator
  - energy
kk_schema_version: 3
kk_id: practice-emulator-plain-aosp-image-headless
kk_derived_from: []
kk_relates_to:
  - map-oom-kill-ends-agent-session
  - practice-test-real-tokkl-server-speed-regressions
  - map-jdk-android-sdk-and-agent-shells
  - practice-run-instrumented-tests-only-through-the-script
kk_depends_on: []
kk_confidence: high
---
The emulator AVD is `tokkl-api36`, built on the plain AOSP image `system-images;android-36;default;x86_64` (Android 16). It is not a Google APIs, Play Store or Automated Test Device (ATD) image. ATD images are smaller, but they remove SystemUI and the launcher, and the notification, Quick Settings tile and widget tests need both.

Boot it without a window: `emulator -avd tokkl-api36 -no-window -no-audio -no-boot-anim -no-snapshot -accel on`. With `-accel on` the emulator refuses to start when KVM is missing, instead of falling back to slow software emulation. `emulator -accel-check` says whether KVM works.

A fresh headless boot often leaves a "System UI isn't responding" dialog that holds window focus, so injected keys go to the dialog instead of the app. `adb shell settings put global hide_error_dialogs 1` stops new ones and `adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS` closes open ones. `scripts/instrumented-tests.sh` does both on an emulator it boots, so tests no longer close dialogs themselves.

qemu holds up to about 5 GB of RAM, and its `oom_score_adj` of 800 makes it the kernel OOM killer's first pick. `-memory 2048` on the command line does not shrink that, since the AVD's `config.ini` already sets `hw.ramSize=2G`. When the systemd user manager answers, the script starts the emulator with `systemd-run --user --scope -p OOMPolicy=continue` and makes the scope `PartOf` the caller's unit. An OOM kill then ends only qemu and the test run, and stopping the caller still stops the emulator. Without a user manager, as in CI, the emulator is a plain child process. An emulator started by hand from an agent's shell gets no scope of its own, so an OOM kill of qemu ends the whole agent session.

This machine is an always-on homelab server tuned for low idle power. When the tests finish, run `adb emu kill` and then `adb kill-server`, so neither the emulator nor the adb server keeps running. Run `adb shell sync` first when a change on the emulator's disk has to survive. A file the script deleted without it was back on the next boot. To look for leftovers, use `pgrep -x adb` and `pgrep -af qemu-system`. `pgrep -f adb` also matches `mariadbd` and the shell that runs the command.

`adb shell screenrecord` stopped with SIGINT has lost roughly the last 1 to 2 s of video. Hold the final screen at least that long before stopping a recording.

<!-- kk:related:start -->
# Related

- Related: [map-oom-kill-ends-agent-session](map-oom-kill-ends-agent-session.md)
- Related: [practice-test-real-tokkl-server-speed-regressions](practice-test-real-tokkl-server-speed-regressions.md)
- Related: [map-jdk-android-sdk-and-agent-shells](map-jdk-android-sdk-and-agent-shells.md)
- Related: [practice-run-instrumented-tests-only-through-the-script](practice-run-instrumented-tests-only-through-the-script.md)
<!-- kk:related:end -->
