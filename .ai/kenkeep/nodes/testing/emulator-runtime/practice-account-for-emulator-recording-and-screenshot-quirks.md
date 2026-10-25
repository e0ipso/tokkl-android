---
type: practice
title: Account for emulator recording and screenshot quirks
description: >-
  Hold the final video frame and distinguish framebuffer captures from launch
  splash failures.
tags:
  - testing
  - android
  - emulator
kk_schema_version: 3
kk_id: practice-account-for-emulator-recording-and-screenshot-quirks
kk_derived_from:
  - scripts/instrumented-tests.sh
  - README.md
kk_relates_to:
  - practice-screenshot-references-on-tokkl-api36
kk_depends_on: []
kk_confidence: high
---
`adb shell screenrecord` stopped with SIGINT has lost roughly the last 1 to 2 s of video. Hold the final screen at least that long before stopping a recording.

For a still capture of the whole screen, use `adb exec-out screencap -p`. `UiAutomation.takeScreenshot` has come back black on `lavapipe` while `screencap` showed the screen. The launch splash is drawn by the system before the app runs, so no test can capture it. On tokkl-api31 the splash showed its icon only for a start from the launcher. `LaunchSplashTest` checks the theme and drawables Android draws it from instead.

<!-- kk:related:start -->
# Related

- Related: [practice-screenshot-references-on-tokkl-api36](../practice-screenshot-references-on-tokkl-api36.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] [README.md](../../../../../README.md)
<!-- kk:citations:end -->
