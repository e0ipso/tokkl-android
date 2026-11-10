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

The runner pins Tokkl 2.1.0 by tag and digest in `IMAGE`; the sibling pinned-server note describes its TLS proxies and capability probes. It does not start a separate legacy server. `TimerCrossDeviceServerTest`, `TimerActionsOfflineServerTest` and `TimerSyncServiceLifecycleTest` play a second device with the same account's API token. Before changing the image pin, check the server assertions in `ChangeQueueServerTest` and the local Stop prediction in `core/time/src/main/java/com/github/e0ipso/tokkl/time/Snap.kt`. `stopSpan` deliberately retains the Android Stop contract documented there: it predicts a stop on the grid point nearest the stop time. The server rounds both ends and may discard a short span, so reconciliation must handle a different server result. Entry mutations on the server also settle its timer state, including ending a Pomodoro session when its entry is stopped.

Tests prove that a tap on Start or Stop changes the state with a network that never answers.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-timer-state-holder-and-sync-service → map-account-timer-state-lifetime](../data-and-offline/timer-state-and-sync/map-account-timer-state-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-online-timer-actions-and-native-control-failures](../data-and-offline/timer-state-and-sync/map-online-timer-actions-and-native-control-failures.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-event-stream-and-service-lifetime](../data-and-offline/timer-state-and-sync/map-timer-event-stream-and-service-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-state-acceptance-and-persistence](../data-and-offline/timer-state-and-sync/map-timer-state-acceptance-and-persistence.md)
- Related: [practice-use-the-instrumented-runner-and-its-device-selection](instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-use-the-pinned-test-server-and-tls-proxies](instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md)
- Related: [map-ordered-entry-sending-and-refusals](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
<!-- kk:related:end -->
