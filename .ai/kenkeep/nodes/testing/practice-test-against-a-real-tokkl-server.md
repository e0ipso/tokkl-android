---
type: practice
title: Test against a real Tokkl server
description: >-
  Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake.
tags:
  - testing
  - ci
kk_schema_version: 3
kk_id: practice-test-against-a-real-tokkl-server
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-timer-state-holder-and-sync-service
  - practice-use-the-instrumented-runner-and-its-device-selection
  - practice-use-the-pinned-test-server-and-tls-proxies
  - map-ordered-entry-sending-and-refusals
  - map-offline-entry-mutations-and-local-id-mapping
kk_depends_on: []
kk_confidence: medium
---
The app has JVM unit tests, Compose UI tests, and instrumented tests on an emulator. Instrumented tests run against a real Tokkl server from the `ghcr.io/e0ipso/tokkl` image, never a fake one. `scripts/instrumented-tests.sh` starts that server and runs them. They run on tokkl-api36 and tokkl-api31 here, and CI runs them on API 36 and API 31 emulators.

The script pins Tokkl 1.10.0, the first release with the server-owned timer state, by tag and digest (`IMAGE` in the script, `ghcr.io/e0ipso/tokkl:1.10.0@sha256:207db4f2a6547fea35ad898570fef892f55ef3ab26b6f142bfc3e3e0e680ad2f`; the comment above it says how to take a new digest). It also starts Tokkl 1.8.1 (`LEGACY_IMAGE`) beside it, a server without `/api/app/timer`, for the tests that Pomodoro and Pause are hidden there while Start and Stop work. The script checks that the older server answers 404 to the timer state. `TimerCrossDeviceServerTest`, `TimerActionsOfflineServerTest` and `TimerSyncServiceLifecycleTest` play a second device with the API token of the same account. Some tests assert the server's stop behaviour. `stopSpan` in `time/Snap.kt` ports the stop rule of Tokkl 1.5.0 (tokkl 08802d4), which stops a running timer on the grid point nearest the stop, and the Stop server tests in `ChangeQueueServerTest` seed their times so that rule and the older one give different answers. Tokkl 1.6.1 (tokkl e3f15ff) changed the rule for every stopped entry: both ends move to the nearest grid point, a tie going later, and a span under half a step or with both ends on one point is not kept. A timer stopped that way goes to the trash with duration 0. `stopSpan` keeps the 1.5.0 contract on purpose until TKA-0259 and TKA-0261 settle it, as its doc comment says. Moving the image to a later release means checking those tests and `stopSpan` against the server's rule. Task 01 of plan 02 did that for 1.10.0 on 2026-10-28: the rule is the same as at 1.6.1 and 1.8.1, and the Stop assertions in `ChangeQueueServerTest` passed. New in 1.10.0, every v9 create, update, stop, delete, restore and purge settles the timer state, so a queued stop of the pomodoro's entry ends the session.

Tests prove that a tap on Start or Stop changes the state with a network that never answers.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-timer-state-holder-and-sync-service](../data-and-offline/map-timer-state-holder-and-sync-service.md)
- Related: [practice-use-the-instrumented-runner-and-its-device-selection](instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-use-the-pinned-test-server-and-tls-proxies](instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md)
- Related: [map-ordered-entry-sending-and-refusals](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
<!-- kk:related:end -->
