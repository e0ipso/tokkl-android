---
type: practice
title: 'Gate drain sittings on free memory, not on a sitting count'
description: >-
  Drain Gradle and device runs start through the memory gate; no fixed cap on
  sittings. T3 checkpoint refs are not reviewer writes.
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
  - practice-emulator-plain-aosp-image-headless
kk_depends_on: []
kk_confidence: high
---
The owner wants every ready drain sitting dispatched, with no fixed cap on how many run at once. Memory is the throttle instead. Each Gradle command and each device run in a sitting starts through `.ai/sift/drain/mem-gate.sh` in the main checkout, inside the OOM-safe `systemd-run` scope that the OOM node gives. The gate starts its command only once `MemAvailable` reaches `MEM_GATE_MIN_MB` (default 5000), and one start at a time across every sitting: it holds `$XDG_RUNTIME_DIR/tokkl-android-mem-gate.lock` until the command has run for `MEM_GATE_SETTLE_S` (default 60 s) or exited, so the next start sees the memory this one took. `.ai/sift` is ignored by git, so drain worktrees do not have the script; call it by the main checkout's path:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- \
  /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh ./gradlew --no-daemon <tasks>
```

The same prefix goes before `scripts/instrumented-tests.sh`. The test script's own `TOKKL_TEST_BOOT_MEMORY_MB` gate for a second emulator applies on top of it.

T3 writes checkpoint refs under `refs/t3/orchestration-v2/checkpoints/` in the main repository while agents run. A new ref of that kind that shows up during a review is T3's, not a write by the reviewer, so it does not break a read-only review.

<!-- kk:related:start -->
# Related

- Related: [practice-reuse-drain-checks-by-content-and-test-configuration](../testing/practice-reuse-drain-checks-by-content-and-test-configuration.md)
- Related: [map-oom-kill-ends-agent-session](map-oom-kill-ends-agent-session.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-account-for-emulator-recording-and-screenshot-quirks](../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-choose-plain-aosp-images-and-compatible-renderers](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-clean-up-only-owned-emulator-resources](../testing/emulator-runtime/practice-clean-up-only-owned-emulator-resources.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-let-the-runner-own-emulator-boot-and-settings](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-respect-emulator-slots-and-resource-ownership](../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md)
<!-- kk:related:end -->
