---
type: practice
title: Choose plain AOSP images and compatible renderers
description: >-
  API 36 references and API 31 compatibility use launcher-capable AOSP images
  and bounded renderers.
tags:
  - testing
  - android
  - emulator
kk_schema_version: 3
kk_id: practice-choose-plain-aosp-images-and-compatible-renderers
kk_derived_from:
  - scripts/instrumented-tests.sh
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
  - map-jdk-android-sdk-and-agent-shells
kk_depends_on: []
kk_confidence: high
---
The AVDs use the plain AOSP images, `system-images;android-<level>;default;x86_64`, never a Google APIs, Play Store or Automated Test Device (ATD) image. ATD images are smaller, but they remove SystemUI and the launcher, and the notification, Quick Settings tile and widget tests need both. `tokkl-api36` runs Android 16, the version the app targets, and its captures are the screenshot references. `tokkl-api31` runs Android 12, the oldest version the app supports, and catches a call to an API newer than the device. The owner raised `minSdk` to 31 on 2026-10-19, so the tokkl-api30 and tokkl-api26 AVDs and their images are gone, and the script refuses an AVD below API 31. The SDK repository has no plain AOSP image for API 36.1 or later. Create each AVD once with `avdmanager create avd -n tokkl-api36 -d medium_phone -k 'system-images;android-36;default;x86_64'`, and the same with `tokkl-api31` and `android-31`. A full run on tokkl-api31 took 15 minutes, and qemu peaked at 4.0 GB.

Let `scripts/instrumented-tests.sh` boot the emulator, picked with `TOKKL_TEST_AVD`. It runs `emulator -avd <avd> -memory 2560 -cores 4 -gpu <renderer> -no-window -no-audio -no-boot-anim -no-snapshot -accel on`. The renderer is `lavapipe` for API 36, which drew the screenshot references, and `swiftshader_indirect` for any older image, read from the AVD's `config.ini`. The emulator raises any smaller `-memory` to 2560 MB for an API 34 or later image, so asking for 2048 saves nothing. With `-accel on` the emulator refuses to start when KVM is missing, instead of falling back to slow software emulation. `emulator -accel-check` says whether KVM works.

qemu keeps about 100 MB each time an app process that drew with OpenGL ends, with `lavapipe` and with `swiftshader`. The test orchestrator starts a process for every test, so one full run took qemu to 11 GB. A process that draws with Vulkan leaves about 5 MB. So the script runs `setprop debug.hwui.renderer skiavk` after every boot, and a full run peaks at about 3.5 GB on tokkl-api36 and about 4 GB on tokkl-api31. With `lavapipe` on the API 31 image, HWUI aborted every app at its first frame under `skiavk`, because that Vulkan lacks `VK_KHR_external_semaphore_fd`. SwiftShader's Vulkan has it, which is why images below 36 boot with `swiftshader_indirect`. Every run goes in one piece, with no batches. CI's emulator step sets `skiavk` on both API levels.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [map-jdk-android-sdk-and-agent-shells](../../build-and-tooling/map-jdk-android-sdk-and-agent-shells.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
<!-- kk:citations:end -->
