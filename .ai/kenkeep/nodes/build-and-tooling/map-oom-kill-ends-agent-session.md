---
type: map
title: An OOM kill inside an agent session's scope ends the whole session
description: >-
  Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one
  OOM-killed process stops the session and its background agents.
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

`/tmp` is a tmpfs, so files left there take RAM and swap. Stale files in `/tmp` and full swap pushed the machine into those OOM kills, so delete what a run leaves in `/tmp`.

When a session ends with no explanation, check `journalctl -k | grep 'Killed process'` and `journalctl --user | grep oom-kill` first. The daily T3 restart is a different cause, with its own node.

<!-- kk:related:start -->
# Related

- Related: [map-t3-daily-restart-stops-agent-sessions](map-t3-daily-restart-stops-agent-sessions.md)
- Related: [practice-emulator-plain-aosp-image-headless](../testing/practice-emulator-plain-aosp-image-headless.md)
<!-- kk:related:end -->
