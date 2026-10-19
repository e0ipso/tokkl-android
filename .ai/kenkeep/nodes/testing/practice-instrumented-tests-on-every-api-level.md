---
type: practice
title: 'Write instrumented tests that hold from API 26 to 36, with the shared helpers'
description: >-
  Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse the
  relays, AirplaneMode, BackgroundTrip, TestAccounts.
tags:
  - testing
  - android
  - emulator
kk_schema_version: 3
kk_id: practice-instrumented-tests-on-every-api-level
kk_derived_from: []
kk_relates_to:
  - practice-run-instrumented-tests-only-through-the-script
  - practice-emulator-plain-aosp-image-headless
  - practice-screenshot-references-on-tokkl-api36
kk_depends_on: []
kk_confidence: high
---
No test may skip itself with a JUnit assumption. AGP 9.4.1 runs the tests through its test engine 1.0.1, which records every result but a pass as a failure. A failed assumption then shows as a failed test in the JUnit XML and the HTML report, while the Gradle task passes. Engine 1.0.7, which AGP 9.5.0-alpha08 and later bring, records it as skipped. To leave a test out on some Android versions, use `@SdkSuppress`, as the screenshot classes do for API 36.

When a test needs a device setting, a rule sets it and puts it back afterwards, as `SystemFontScale` does for the font scale and `SystemNightMode` for the night mode. Never assume the device already has it.

Parse server times with `OffsetDateTime.parse`. Tokkl writes `+00:00` and Toggl `Z`, and `Instant.parse` on API 30 refuses the `+00:00` form. Use `WindowInsetsCompat`, never `WindowInsets.Type`: that class came with Android 11, and `LargestFontScaleTest` crashed on it below API 30. `AirplaneMode` runs `cmd connectivity airplane-mode`, which needs Android 10, so the tests that use it cannot pass on tokkl-api26 until TKA-0174 lands.

A test cannot kill the app's process and then check what survived, because the instrumentation runs in that process and dies with it. Restart tests split into steps instead, and the orchestrator runs each step in a new process.

Reuse the helpers in `app/src/androidTest/java/com/github/e0ipso/tokkl/`:

- `LoopbackRelay` relays the phone's 127.0.0.1 to a test server. With `watchRequests` it hands a watcher every chunk the app sends, so a test can count requests.
- `HangingRelay` can hang like a network that went quiet, cutting the connections it relayed, or hold back one answer until `releaseAnswer`.
- `AirplaneMode` switches airplane mode through the shell user and waits until the client's requests fail, or get through again.
- `BackgroundTrip` sends `MainActivity` to the background with the home key and brings the same activity back.
- `TestAccounts` invites throwaway accounts on the test server for tests that fill an account with data, and creates, reads and deletes their entries. The account the script creates stays empty.
- `finishWithin` in `ComposeWaits.kt` runs a block on its own thread and fails after a timeout. `ActivityScenario`'s `close()` and `onActivity()` start with `waitForIdleSync()`, which has no timeout, so a main thread that never goes idle would hang the whole run.

Tokkl counts failed passwords per account and address, per account and per address, within 15 minutes. Every test reaches it from one address, whose counter stops at 100. `SignInRateLimitTest` uses a throwaway account and at most 11 attempts, 10 counted failures and the refused one, which leaves 90 for the rest of the run. Any new test that sends wrong passwords spends the same budget.

To catch clipped text in a Compose test, check `TextLayoutResult.didOverflowHeight`. Semantics hands out a layout redone at the full available width, so `didOverflowWidth` is true for any text narrower than its container. `LargestFontScaleTest` checks height for that reason. A single line of Compose text keeps the font's ascent above it and its descent below it whatever `lineHeight` the style sets, so a line height that is too short only shows between lines. `ClockAndTotalStylesFitTest` puts its samples in a column narrower than the text, so they wrap. For dialogs, `LargestFontScaleTest.assertDialogFits` checks that the dialog stays in its window and that its scrolling area, title and buttons do not overlap. A test there marked `@RefusedSession` starts signed in to a server whose requests wait for a trust answer.

<!-- kk:related:start -->
# Related

- Related: [practice-run-instrumented-tests-only-through-the-script](practice-run-instrumented-tests-only-through-the-script.md)
- Related: [practice-emulator-plain-aosp-image-headless](practice-emulator-plain-aosp-image-headless.md)
- Related: [practice-screenshot-references-on-tokkl-api36](practice-screenshot-references-on-tokkl-api36.md)
<!-- kk:related:end -->
