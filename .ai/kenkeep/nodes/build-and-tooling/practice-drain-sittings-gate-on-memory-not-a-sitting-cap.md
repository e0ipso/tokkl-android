---
type: practice
title: 'Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap'
description: >-
  Drain Gradle and device runs start directly in the OOM-safe scope, one heavy
  job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer
  writes.
tags:
  - sift
  - agents
  - memory
kk_schema_version: 3
kk_id: practice-drain-sittings-gate-on-memory-not-a-sitting-cap
kk_derived_from: []
kk_relates_to:
  - practice-reuse-drain-checks-by-content-and-test-configuration
  - map-oom-kill-ends-agent-session
  - practice-choose-plain-aosp-images-and-compatible-renderers
  - practice-let-the-runner-own-emulator-boot-and-settings
kk_depends_on: []
kk_confidence: high
---
The owner wants every ready drain sitting dispatched, with no fixed cap on how many run at once. On 2026-10-28 he said `scripts/mem-gate.sh` is too conservative, so Gradle and device runs no longer go through a memory gate. Each Gradle command and each device run in a sitting starts directly inside the OOM-safe `systemd-run` scope that the OOM node gives, and only one heavy job runs at a time, so a second Gradle build or emulator waits for the first to finish:
```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- ./gradlew --no-daemon <tasks>
```
The same prefix goes before `scripts/instrumented-tests.sh`. The test script's own `TOKKL_TEST_BOOT_MEMORY_MB` gate for a second emulator still applies. The memory-gate wrappers, `scripts/mem-gate.sh` and `.ai/sift/drain/mem-gate.sh`, are not used for these runs.
T3 writes checkpoint refs under `refs/t3/orchestration-v2/checkpoints/` in the main repository while agents run. A new ref of that kind that shows up during a review is T3's, not a write by the reviewer, so it does not break a read-only review.

<!-- kk:related:start -->
# Related

- Related: [practice-reuse-drain-checks-by-content-and-test-configuration](../testing/practice-reuse-drain-checks-by-content-and-test-configuration.md)
- Related: [map-oom-kill-ends-agent-session](map-oom-kill-ends-agent-session.md)
- Related: [practice-choose-plain-aosp-images-and-compatible-renderers](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md)
- Related: [practice-let-the-runner-own-emulator-boot-and-settings](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
<!-- kk:related:end -->
