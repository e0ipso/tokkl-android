---
type: practice
title: 'Write instrumented tests that hold on API 31 and 36, with the shared helpers'
description: >-
  Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse
  LoopbackRelay, openBarPage, SessionRule, TestAccounts.
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
No test may skip itself with a JUnit assumption. AGP 9.4.1 runs the tests through its test engine 1.0.1, which records every result but a pass as a failure. A failed assumption then shows as a failed test in the JUnit XML and the HTML report, while the Gradle task passes. Engine 1.0.7, which AGP 9.5.0-alpha08 and later bring, records it as skipped. To leave a test out on some Android versions, use `@SdkSuppress`, as the screenshot classes do for API 36. To leave a test out where the device lacks something the script sets up, mark it with an annotation the script passes to the runner's `notAnnotation`, as `@UserCaInstalled` is.

When a test needs a device setting, a rule sets it and puts it back afterwards, as `SystemFontScale` does for the font scale and `SystemNightMode` for the night mode. Never assume the device already has it.

The tests run on tokkl-api36 and tokkl-api31, the oldest version the app installs on. A test that passes on one can fail on the other: below Android 15 a window is edge to edge only when the app asks, so a page hosted in a plain test activity lays out differently there.

Parse server times with `OffsetDateTime.parse`. Tokkl writes `+00:00` and Toggl `Z`, and `Instant.parse` refused the `+00:00` form on Android 11.

Host a screen in a window set up as `MainActivity`'s: call `WindowCompat.enableEdgeToEdge` and set `SOFT_INPUT_ADJUST_RESIZE` on the test activity's window before `setContent`, as `ProjectsServerTest.show()` does. On tokkl-api31, `createComposeRule`'s plain `ComponentActivity` kept the keyboard's insets from the page, so `imePadding` never shrank it. `performScrollTo` then left Save at the bottom of the window, under the snackbar of the step before, and a touch went to the snackbar. A semantics click skips hit testing, so it hid the bug. On such a page, wait until the keyboard takes up room before typing, wait for a control to keep still before tapping it, and wait out a snackbar (about 4 s) that covers the next control. `ClientsServerTest` and `TagsServerTest` still use a plain `createComposeRule` host.

A test cannot kill the app's process and then check what survived, because the instrumentation runs in that process and dies with it. Restart tests split into steps instead, and the orchestrator runs each step in a new process.

Reuse the helpers in `app/src/androidTest/java/com/github/e0ipso/tokkl/`:

- `LoopbackRelay` is the only relay. It owns the loop that relays the phone's 127.0.0.1 to a test server, with TLS passed through untouched. `watchConnections` gives each connection a `ConnectionWatcher` that sees every chunk sent and can hold back the server's answer. `holdNewConnections` accepts new connections and answers nothing, `dropHeld` and `cutRelayed` close held and relayed connections, and `relayTo` sends new connections to another server at the same local port.
- `HangingRelay` and `RequestCounter` build on it. `HangingRelay` can hang like a network that went quiet, cutting the connections it relayed, or hold back one answer with `holdAnswerTo` until `releaseAnswer`. `RequestCounter` counts the requests whose request line ends in a given path.
- `openBarPage(label)` in `ComposeWaits.kt` waits for a bottom bar item and taps it. `bottomBarItem` finds the item by its text and `Role.Tab`, because text and a click action alone also match a cached history row with that description.
- `SessionRule` sets the stored session before the activity starts, signed in to the test server or signed out for a test marked `@SignedOut`. It first clears every store and puts an empty in-memory test WorkManager in place, so the queue's background work runs only when a test's `TestDriver` says the network is there, and work one test scheduled never sends another test's queue. This applies to every test, so a change to it needs the full suite.
- `AirplaneMode` switches airplane mode through the shell user and waits until the client's requests fail, or get through again.
- `BackgroundTrip` sends `MainActivity` to the background with the home key and brings the same activity back.
- `TestAccounts` invites throwaway accounts on the test server for tests that fill an account with data, and creates, reads and deletes their entries. The account the script creates stays empty.
- `finishWithin` in `ComposeWaits.kt` runs a block on its own thread and fails after a timeout. `ActivityScenario`'s `close()` and `onActivity()` start with `waitForIdleSync()`, which has no timeout, so a main thread that never goes idle would hang the whole run.

Tokkl counts failed passwords per account and address, per account and per address, within 15 minutes. Every test reaches it from one address, whose counter stops at 100. `SignInRateLimitTest` uses a throwaway account and at most 11 attempts, 10 counted failures and the refused one, which leaves 90 for the rest of the run. Any new test that sends wrong passwords spends the same budget.

`performTouchInput` injects real touches on the screen. On a node clipped out of view, the touch lands in the screen's top-left corner, so the test taps or drags something else. Scroll the node into view first and check that the press point lies on what shows, as `CalendarServerTest.dragOnGrid` does: it swipes the Timer page's top away like a thumb before scrolling the grid, because a semantics scroll alone spends part of the grid's scroll on the page's top.

To catch clipped text in a Compose test, check `TextLayoutResult.didOverflowHeight`. Semantics hands out a layout redone at the full available width, so `didOverflowWidth` is true for any text narrower than its container. `LargestFontScaleTest` checks height for that reason. A single line of Compose text keeps the font's ascent above it and its descent below it whatever `lineHeight` the style sets, so a line height that is too short only shows between lines. `ClockAndTotalStylesFitTest` puts its samples in a column narrower than the text, so they wrap. For dialogs, `LargestFontScaleTest.assertDialogFits` checks that the dialog stays in its window and that its scrolling area, title and buttons do not overlap. A test there marked `@RefusedSession` starts signed in to a server whose requests wait for a trust answer.

<!-- kk:related:start -->
# Related

- Related: [practice-run-instrumented-tests-only-through-the-script](practice-run-instrumented-tests-only-through-the-script.md)
- Related: [practice-emulator-plain-aosp-image-headless](practice-emulator-plain-aosp-image-headless.md)
- Related: [practice-screenshot-references-on-tokkl-api36](practice-screenshot-references-on-tokkl-api36.md)
<!-- kk:related:end -->
