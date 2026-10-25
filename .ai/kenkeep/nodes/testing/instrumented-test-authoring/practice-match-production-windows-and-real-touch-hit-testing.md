---
type: practice
title: Match production windows and real touch hit testing
description: >-
  Edge-to-edge, keyboard insets, stable controls and visible press points
  prevent false UI results.
tags:
  - testing
  - android
  - ui
kk_schema_version: 3
kk_id: practice-match-production-windows-and-real-touch-hit-testing
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3'
kk_relates_to:
  - practice-let-the-runner-own-emulator-boot-and-settings
kk_depends_on: []
kk_confidence: high
---
Host a screen in a window set up as `MainActivity`'s: call `WindowCompat.enableEdgeToEdge` and set `SOFT_INPUT_ADJUST_RESIZE` on the test activity's window before `setContent`, as `ProjectsServerTest.show()` does. On tokkl-api31, `createComposeRule`'s plain `ComponentActivity` kept the keyboard's insets from the page, so `imePadding` never shrank it. `performScrollTo` then left Save at the bottom of the window, under the snackbar of the step before, and a touch went to the snackbar. A semantics click skips hit testing, so it hid the bug. On such a page, wait until the keyboard takes up room before typing, wait for a control to keep still before tapping it, and wait out a snackbar (about 4 s) that covers the next control. `ClientsServerTest` and `TagsServerTest` still use a plain `createComposeRule` host.

Reuse the helpers in `app/src/androidTest/java/com/github/e0ipso/tokkl/`:

- `openBarPage(label)` in `ComposeWaits.kt` waits for a bottom bar item and taps it. `bottomBarItem` finds the item by its text and `Role.Tab`, because text and a click action alone also match a cached history row with that description.
- `finishWithin` in `ComposeWaits.kt` runs a block on its own thread and fails after a timeout. `ActivityScenario`'s `close()` and `onActivity()` start with `waitForIdleSync()`, which has no timeout, so a main thread that never goes idle would hang the whole run.

`performTouchInput` injects real touches on the screen. On a node clipped out of view, the touch lands in the screen's top-left corner, so the test taps or drags something else. Scroll the node into view first and check that the press point lies on what shows, as `CalendarServerTest.dragOnGrid` does: it swipes the Timer page's top away like a thumb before scrolling the grid, because a semantics scroll alone spends part of the grid's scroll on the page's top.

<!-- kk:related:start -->
# Related

- Related: [practice-let-the-runner-own-emulator-boot-and-settings](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3
<!-- kk:citations:end -->
