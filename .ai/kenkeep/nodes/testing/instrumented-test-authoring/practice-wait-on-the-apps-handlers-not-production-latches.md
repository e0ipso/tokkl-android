---
type: practice
title: Wait on the app's own handlers, never on a latch in production code
description: >-
  Device tests wait for receiver and service work by wrapping the handler in
  the Koin graph, or on an effect the app already shows. Production has no
  test latches or test-only branches.
tags:
  - testing
  - android
kk_schema_version: 3
kk_id: practice-wait-on-the-apps-handlers-not-production-latches
kk_derived_from:
  - app/src/androidTest/java/com/github/e0ipso/tokkl/pomodoro/PomodoroWorkWaits.kt
  - feature/pomodoro/src/main/java/com/github/e0ipso/tokkl/pomodoro/PomodoroButtons.kt
kk_relates_to:
  - practice-isolate-session-and-background-state-with-shared-rules
  - map-pomodoro-phase-alarms-and-rearming
kk_depends_on: []
kk_confidence: high
---
Production code holds nothing that only a test sets or reads: no static `CountDownLatch`, no null check for a direct `onReceive` call, no comment that says "the test waits on this". Until 2026-11-08 the Pomodoro receivers and the ringing service had such latches; they are gone.

A test waits on what the app already does. Most checks wait for an effect: the alarm in `AlarmManager.nextAlarmClock`, the server's or the held timer state, `app.pomodoroRinging.state`, a notification, or a toast through `TimerActionToasts`. A check that nothing happened, such as an old button that must do nothing, needs to know the work finished. For that, a receiver or service hands its work to an interface it resolves from Koin, and `PomodoroWorkWaits` in `app/src/androidTest` puts a counting wrapper around the app's own binding with `GlobalContext.get().declare(..., allowOverride = true)`, then puts the original back:

- `ringingButtons(count) { ... }` and `sessionButtons(count) { ... }` wait until that many presses of `PomodoroRingingButtons` or `PomodoroSessionButtons` have finished, toast included.
- `RingChecks` counts the ringing service's `PomodoroRingCheck` calls. Open it before the service starts, because a running service keeps the check it resolved.

Work that only a protected broadcast starts, such as BOOT_COMPLETED, runs through its handler: `PomodoroPhaseAlarmRearmTest` calls `app.pomodoroAlarmRearm.rearm(Intent.ACTION_BOOT_COMPLETED)`. A broadcast the app may send itself goes out with `sendOrderedBroadcast` and a result receiver, which hears when the receiver's `goAsync` work has finished, as `PomodoroWidgetRedrawTest` does.

The same goes for `@VisibleForTesting`. Before making a member public for a device test, see whether the test can use the public API, as `PomodoroPhaseAlarmRearmTest` now cancels the alarm through `app.pomodoroAlarms.cancel()`, or spell out a stored or platform name as a literal.
