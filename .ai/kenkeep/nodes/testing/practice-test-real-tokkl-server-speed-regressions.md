---
type: practice
title: Test against a real Tokkl server and catch speed regressions in CI
description: >-
  Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake.
  Macrobenchmark fails a build slower than the previous one.
tags:
  - testing
  - ci
  - performance
kk_schema_version: 3
kk_id: practice-test-real-tokkl-server-speed-regressions
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - practice-run-instrumented-tests-only-through-the-script
  - map-change-queue-sends-entry-changes-in-order
kk_depends_on: []
kk_confidence: medium
---
The app has JVM unit tests, Compose UI tests, and instrumented tests on an emulator. Instrumented tests run against a real Tokkl server from the `ghcr.io/e0ipso/tokkl` image, never a fake one. `scripts/instrumented-tests.sh` starts that server and runs them. They run on tokkl-api36 and tokkl-api31 here, and CI runs them on API 36 and API 31 emulators.

The script pins Tokkl 1.5.0 by digest (`IMAGE` in the script; the comment above it says how to take a new digest), and some tests assert that version's behaviour. Tokkl 1.5.0 (tokkl 08802d4) stops a running timer on the grid point nearest the stop, and the app's `stopSpan` ports that rule. The Stop server tests in `ChangeQueueServerTest` seed their times so the 1.5.0 rule and the older one give different answers. Moving the image to a later release means checking those tests and `stopSpan` against its rule.

The PWA sets the speed bar. If starting a timer is slower here, the owner drifts back to the PWA. Tests prove that a tap on Start or Stop changes the state with a network that never answers. The `:macrobenchmark` module measures cold start (`StartupBenchmark`, `StartupTimingMetric`) and tap-to-state (`TimerTapBenchmark`, `TraceSectionMetric` around the async `TimerStart` and `TimerStop` sections that `timer/TapTrace.kt` opens on a tap and closes when the screen shows the new state). The benchmarks sign in to the server the script starts, so they run through it: `scripts/instrumented-tests.sh :macrobenchmark:connectedBenchmarkAndroidTest`. CI's `benchmarks` job runs them on an API 36 emulator and `scripts/compare-benchmarks.sh BASELINE_DIR CURRENT_DIR` fails it when a median is more than 20% and 34 ms above the last green run on main, or when a baseline metric is missing; with no baseline it only prints the medians. Two local runs on a shared host differed by 9.2% in their cold start medians, close to the gate, and no run on main has stored a baseline yet (TKA-0228, owner).

The goals are 200 ms from tap to new state and 1.5 s cold start on a mid-range phone. No real phone checks them, so they guide design but do not gate releases.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [practice-run-instrumented-tests-only-through-the-script → practice-keep-instrumented-credentials-out-of-process-arguments](instrumented-runner/practice-keep-instrumented-credentials-out-of-process-arguments.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-pass-class-and-package-lists-through-runner-filter-files](instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-use-the-instrumented-runner-and-its-device-selection](instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-use-the-pinned-test-server-and-tls-proxies](instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-verify-device-test-results-and-process-isolation-receipts](instrumented-runner/practice-verify-device-test-results-and-process-isolation-receipts.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-background-queue-work-and-empty-queue-cancellation](../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-cached-entity-picker-and-offline-creation](../data-and-offline/change-queue/map-cached-entity-picker-and-offline-creation.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-offline-entry-mutations-and-local-id-mapping](../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-ordered-entry-sending-and-refusals](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-queued-delete-and-short-stop-undo](../data-and-offline/change-queue/map-queued-delete-and-short-stop-undo.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-remote-running-conflicts-and-lost-create-responses](../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md)
- Related: [map-change-queue-sends-entry-changes-in-order → map-room-queue-migration-continuity](../data-and-offline/change-queue/map-room-queue-migration-continuity.md)
<!-- kk:related:end -->
