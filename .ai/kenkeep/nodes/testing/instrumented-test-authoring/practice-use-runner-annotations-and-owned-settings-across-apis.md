---
type: practice
title: Use runner annotations and owned settings across APIs
description: >-
  SdkSuppress and test rules make API-specific coverage and device settings
  explicit.
tags:
  - testing
  - android
  - emulator
kk_schema_version: 3
kk_id: practice-use-runner-annotations-and-owned-settings-across-apis
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3'
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
  - practice-let-the-runner-own-emulator-boot-and-settings
  - practice-screenshot-references-on-tokkl-api36
kk_depends_on: []
kk_confidence: high
---
No test may skip itself with a JUnit assumption. AGP 9.4.1 runs the tests through its test engine 1.0.1, which records every result but a pass as a failure. A failed assumption then shows as a failed test in the JUnit XML and the HTML report, while the Gradle task passes. Engine 1.0.7, which AGP 9.5.0-alpha08 and later bring, records it as skipped. To leave a test out on some Android versions, use `@SdkSuppress`, as the screenshot classes do for API 36. To leave a test out where the device lacks something the script sets up, mark it with an annotation the script passes to the runner's `notAnnotation`, as `@UserCaInstalled` is.

When a test needs a device setting, a rule sets it and puts it back afterwards, as `SystemFontScale` does for the font scale and `SystemNightMode` for the night mode. Never assume the device already has it.

The tests run on tokkl-api36 and tokkl-api31, the oldest version the app installs on. A test that passes on one can fail on the other: below Android 15 a window is edge to edge only when the app asks, so a page hosted in a plain test activity lays out differently there.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-let-the-runner-own-emulator-boot-and-settings](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
- Related: [practice-screenshot-references-on-tokkl-api36](../practice-screenshot-references-on-tokkl-api36.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3
<!-- kk:citations:end -->
