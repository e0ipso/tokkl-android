---
type: practice
title: Run JVM coroutine tests in virtual time, and never sleep
description: >-
  JVM tests use runTest and MainDispatcherRule from core:platform's test fixtures. Time passes by
  advancing the test scheduler, not by Thread.sleep; real waits stay only for a real server or thread.
tags:
  - testing
  - kotlin
  - coroutines
kk_schema_version: 3
kk_id: practice-run-coroutine-tests-in-virtual-time
kk_derived_from:
  - core/platform/src/testFixtures/java/com/github/e0ipso/tokkl/platform/MainDispatcherRule.kt
kk_relates_to:
  - map-gradle-build-sdk-levels-kotlin-and-memory
  - practice-isolate-all-known-failing-tests-before-full-suite-validation
kk_depends_on: []
kk_confidence: high
---
A JVM test that calls suspend code runs its body in `runTest` from kotlinx-coroutines-test, which the catalog pins to the kotlinx-coroutines version the AndroidX and Koin libraries bring. The library and JVM convention plugins put it on every module's `testImplementation`.

A view model test that stands a thread in for the main thread uses `MainDispatcherRule` from core:platform's test fixtures (`testImplementation(testFixtures(project(":core:platform")))`). Declare it as `@get:Rule val main = MainDispatcherRule()` and pass `main.scope` as the view model's scope and `main.dispatcher` where it takes an IO context. The rule sets `Dispatchers.Main` to its `StandardTestDispatcher` for the test. Nothing runs until the test lets it: `runCurrent()` runs what is due, `settle(millis)` lets that much virtual time pass, `awaitUntil(what) { condition }` lets time pass in 10 ms steps until the condition holds and fails after 5 s of virtual time, and `onMain { }` runs a suspend call on the main thread after what is queued there and returns its result. A tap helper calls `runCurrent()`, the tap, and `runCurrent()` again, as the main thread would run the work queued before and after it.

Never call `Thread.sleep`, and never poll with `System.nanoTime`. Where a test once waited "long enough" to show that nothing happened, `settle` the same time: production timeouts such as the `withTimeoutOrNull` in the timer's `settle` (`TimerTaps.kt`) then fire exactly when they would. A production parameter that only lets a test replace `delay`, such as the Pomodoro widget deadline's old `wait` or the timer stream's old `TimerSyncClock`, is a test seam: drop it and drive `delay` with the test scheduler, with a `Clock` that reads `testScheduler.currentTime`. `TimerSyncLifecycleTest` gives `TimerSync` a scope on an `UnconfinedTestDispatcher` and `now = { scheduler.currentTime }`, so each call takes effect before it returns and the watchdog and backoff run only as far as the test advances.

Virtual time cannot wait for another thread: inside `runTest`, a `withTimeout` around an answer from MockWebServer on `Dispatchers.IO`, or from a real executor, ends at once. Such a wait stays in `runBlocking`. `HistoryViewModelTest`, `CalendarViewModelTest`, `RunningEntryEditorTest`, `SyncStatusTest` and `CallCancellationTest` still wait with `withTimeout` there, and `ChangeSenderTest`'s Retry-After test checks a real one-second wait against the server, while its stop test waits 300 ms in `runBlocking` for a drain held on a real thread.
