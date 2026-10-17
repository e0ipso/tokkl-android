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
  - practice-test-real-tokkl-server-speed-regressions
  - map-jdk-android-sdk-and-agent-shells
kk_depends_on: []
kk_confidence: high
---
The emulator AVD is `tokkl-api36`, built on the plain AOSP image `system-images;android-36;default;x86_64` (Android 16). It is not a Google APIs, Play Store or Automated Test Device (ATD) image. ATD images are smaller, but they remove SystemUI and the launcher, and the notification, Quick Settings tile and widget tests need both.

Boot it without a window: `emulator -avd tokkl-api36 -no-window -no-audio -no-boot-anim -no-snapshot -accel on`. With `-accel on` the emulator refuses to start when KVM is missing, instead of falling back to slow software emulation. `emulator -accel-check` says whether KVM works.

This machine is an always-on homelab server tuned for low idle power. When the tests finish, run `adb emu kill` and then `adb kill-server`, so neither the emulator nor the adb server keeps running.

<!-- kk:related:start -->
# Related

- Related: [practice-test-real-tokkl-server-speed-regressions](practice-test-real-tokkl-server-speed-regressions.md)
- Related: [map-jdk-android-sdk-and-agent-shells](map-jdk-android-sdk-and-agent-shells.md)
<!-- kk:related:end -->
