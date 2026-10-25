---
type: map
title: An OOM kill inside an agent session's scope ends the whole session
description: >-
  Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle
  and the test script in a scope of their own with choom 1000.
tags:
  - agents
  - machine
  - memory
  - emulator
kk_schema_version: 3
kk_id: map-oom-kill-ends-agent-session
kk_derived_from: []
kk_relates_to:
  - map-t3-daily-restart-stops-agent-sessions
  - practice-emulator-plain-aosp-image-headless
kk_depends_on: []
kk_confidence: high
---
Each Claude session that T3 starts runs in a systemd user scope named `t3code-claude-<id>.scope` with `OOMPolicy=stop`. When the kernel OOM killer ends any process in that scope, systemd stops the scope, and with it the session and every background agent it started. The user journal then logs `t3code-claude-<id>.scope: Failed with result 'oom-kill'`. The usual victim is `qemu-system-x86`, whose `oom_score_adj` of 800 puts it first in line. `scripts/instrumented-tests.sh` gives the emulator a scope of its own for this reason, `tokkl-emulator-<port>-<random>.scope` with `OOMPolicy=continue`, `MemoryMax=6G` and `MemorySwapMax=0`. When qemu reached that cap, the kernel logged `Memory cgroup out of memory` for the emulator's scope and killed only qemu. An emulator started by hand from an agent's shell has none of this.

`claude` itself runs with `oom_score_adj` 800, and so does every process it starts, Gradle included. In a machine-wide OOM the kernel picks among them. On 2026-10-19 between 22:58 and 23:02 it killed three `claude` processes on this 16 GB machine, and the drain session ended. So an agent runs every Gradle command and `scripts/instrumented-tests.sh` as:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- <command>
```

The command then runs in a `run-u<n>.scope` of its own under `app.slice`, outside the session's scope, with `oom_score_adj` 1000, so the kernel kills the build first and the session survives. The test script's emulator scope then takes that scope as the caller it is `PartOf`.

`/tmp` is a tmpfs, so files left there take RAM and swap. Stale files in `/tmp` and full swap pushed the machine into those OOM kills, so delete what a run leaves in `/tmp`.

When a session ends with no explanation, check `journalctl -k | grep 'Killed process'` and `journalctl --user | grep oom-kill` first. The daily T3 restart is a different cause, with its own node.

<!-- kk:related:start -->
# Related

- Related: [map-t3-daily-restart-stops-agent-sessions](map-t3-daily-restart-stops-agent-sessions.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-account-for-emulator-recording-and-screenshot-quirks](../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-choose-plain-aosp-images-and-compatible-renderers](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-clean-up-only-owned-emulator-resources](../testing/emulator-runtime/practice-clean-up-only-owned-emulator-resources.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-let-the-runner-own-emulator-boot-and-settings](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-respect-emulator-slots-and-resource-ownership](../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md)
<!-- kk:related:end -->
