---
type: practice
title: >-
  Record screenshot references on tokkl-api36, in the commit that changes the
  look
description: >-
  Four classes compare every screen with PNGs in androidTest/assets/screenshots.
  Only tokkl-api36 renders them, and captures carry no ripple.
tags:
  - testing
  - android
  - emulator
  - ui
kk_schema_version: 3
kk_id: practice-screenshot-references-on-tokkl-api36
kk_derived_from: []
kk_relates_to:
  - practice-run-instrumented-tests-only-through-the-script
  - practice-emulator-plain-aosp-image-headless
  - map-design-foundation-spacing-shapes-type-brand
kk_depends_on: []
kk_confidence: high
---
`app/src/androidTest/java/com/github/e0ipso/tokkl/ui/screenshot/ScreenshotTest.kt` holds the abstract `ScreenshotTest` and one subclass per theme and font scale: `LightScreenshotTest`, `DarkScreenshotTest`, `LightLargestFontScreenshotTest` and `DarkLargestFontScreenshotTest`. Each class captures 16 screens, so there are 64 references: sign-in with each method, the Timer page's List and Calendar views, Settings, Licences, the Summary, Detailed and Weekly report tabs, Projects, Clients, Tags, the More sheet, the brand's small and medium sizes, the http warning and the certificate prompt. `ReferenceImages` shrinks each capture to a third in each direction, averaging 3x3 blocks, and compares it with its PNG in `app/src/androidTest/assets/screenshots/`. A pixel counts as different when any channel differs by more than 8, and up to 20 such pixels pass. The README section "Screenshot tests" has the run command and the copy step.

Record and compare the references only on the AVD `tokkl-api36` (medium_phone, 420 dpi, the android-36 `default` x86_64 image at revision 2), because density and system fonts change the pixels. Two runs on freshly booted tokkl-api36 emulators matched pixel for pixel. Nobody has compared the CI emulator's captures with the references yet (TKA-0087). A change to how a screen looks updates its references in the same commit, after a look at every diff and capture.

Keep the captures deterministic. The reports capture uses a fixed week and a fixed `Clock`, the trust prompts a fixed URL and certificate, and no capture includes the status bar. Click with `performSemanticsAction(SemanticsActions.OnClick)` before capturing the same screen, because `performClick` is a touch and its ripple shows in the capture. Signed-in pages draw inside `SignedInFrame` with fixed data, so the bottom bar is part of their captures.

`captureToImage` copies a node from the activity's window, or from its dialog. Anything in a window of its own, such as the More sheet's `ModalBottomSheet`, is missing from a capture of the compose root. The More sheet test copies the sheet's window with `PixelCopy` instead, which can copy a window from Android 14 on.

<!-- kk:related:start -->
# Related

- Related: [practice-run-instrumented-tests-only-through-the-script](practice-run-instrumented-tests-only-through-the-script.md)
- Related: [practice-emulator-plain-aosp-image-headless](practice-emulator-plain-aosp-image-headless.md)
- Related: [map-design-foundation-spacing-shapes-type-brand](../design/map-design-foundation-spacing-shapes-type-brand.md)
<!-- kk:related:end -->
