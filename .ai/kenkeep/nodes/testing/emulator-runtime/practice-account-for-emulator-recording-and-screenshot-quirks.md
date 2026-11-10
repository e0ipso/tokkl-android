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
kk_relates_to:
  - practice-screenshot-references-on-tokkl-api36
kk_depends_on: []
kk_confidence: high
---
Use the Device panel tools for screenshots and recordings. The recording must retain the final screen long enough to include it in the video. The project runner owns test captures; a framework capture has returned black on `lavapipe` even when the framebuffer held the screen. The launch splash is drawn by the system before the app runs, so no test can capture it. On tokkl-api31 the splash showed its icon only for a start from the launcher. `LaunchSplashTest` checks the theme and drawables Android draws it from instead.

<!-- kk:related:start -->
# Related

- Related: [practice-screenshot-references-on-tokkl-api36](../practice-screenshot-references-on-tokkl-api36.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
<!-- kk:citations:end -->
