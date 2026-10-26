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
  - practice-run-instrumented-tests-only-through-the-script
  - map-change-queue-sends-entry-changes-in-order
kk_depends_on: []
kk_confidence: medium
---
The app has JVM unit tests, Compose UI tests, and instrumented tests on an emulator. Instrumented tests run against a real Tokkl server from the `ghcr.io/e0ipso/tokkl` image, never a fake one. `scripts/instrumented-tests.sh` starts that server and runs them. They run on tokkl-api36 and tokkl-api31 here, and CI runs them on API 36 and API 31 emulators.

The script pins Tokkl 1.8.1 by digest (`IMAGE` in the script; the comment above it says how to take a new digest), and some tests assert the server's stop behaviour. `stopSpan` in `time/Snap.kt` ports the stop rule of Tokkl 1.5.0 (tokkl 08802d4), which stops a running timer on the grid point nearest the stop, and the Stop server tests in `ChangeQueueServerTest` seed their times so that rule and the older one give different answers. Tokkl 1.6.1 (tokkl e3f15ff) changed the rule for every stopped entry: both ends move to the nearest grid point, a tie going later, and a span under half a step or with both ends on one point is not kept. A timer stopped that way goes to the trash with duration 0. `stopSpan` keeps the 1.5.0 contract on purpose until TKA-0259 and TKA-0261 settle it, as its doc comment says. Moving the image to a later release means checking those tests and `stopSpan` against the server's rule.

Tests prove that a tap on Start or Stop changes the state with a network that never answers.

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
