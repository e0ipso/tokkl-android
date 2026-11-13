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
  - practice-choose-plain-aosp-images-and-compatible-renderers
  - practice-respect-emulator-slots-and-resource-ownership
  - practice-let-the-runner-own-emulator-boot-and-settings
  - practice-clean-up-only-owned-emulator-resources
  - practice-account-for-emulator-recording-and-screenshot-quirks
kk_depends_on: []
kk_confidence: high
---
Claude sessions started by T3 run in user scopes with `OOMPolicy=stop`. If the kernel kills
any process in that scope, systemd stops the whole scope, including the agent. Isolate every
Gradle command and `scripts/instrumented-tests.sh` in a separate scope, with the build chosen
before the agent in a machine-wide OOM and at the owner's required lowest CPU priority:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- nice -n 19 ./gradlew --no-daemon <tasks>
```

Use the same prefix for the instrumented runner. Start only one owned build/device job at
a time. The runner gives its emulator a separate `tokkl-emulator-<port>-<random>.scope`
with `OOMPolicy=continue`, `MemoryMax=6G` and `MemorySwapMax=0`, linked to the caller's
scope with `PartOf`. An emulator started by hand lacks this isolation.

For an unexplained session exit, check `journalctl -k` for `Killed process` and
`journalctl --user` for `oom-kill`. The daily T3 restart is a separate cause. `/tmp` is
a tmpfs on this host, so remove temporary files owned by completed runs.

## Optional memory admission wrapper

`scripts/mem-gate.sh CMD [ARGS...]` checks physical RAM and every visible cgroup v2 ancestor,
including its own scope. It needs Linux cgroup v2 and Python 3. The ignored drain shim at
`.ai/sift/drain/mem-gate.sh` delegates to this tracked wrapper; do not nest them because they
share a startup lock. [Drain runs use the direct scope command above](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md).
The wrapper does not change limits, move workloads or change CI.

Admission requires 5000 MiB of host `MemAvailable` and 5000 MiB below **both** `memory.high`
and `memory.max` in each ancestor. `max` is unlimited; the hierarchy root normally lacks
controller files. Missing or malformed mandatory files elsewhere, or a subtree-only mount
hiding ancestors, fail closed with exit 2. Host free RAM alone can hide cgroup pressure, and
swap does not add admission headroom.

An `instrumented-tests.sh` argument raises ancestor headroom to at least 8192 MiB. This
estimate covers the observed 4.16 GiB qemu peak, Gradle's 2 GiB heap and 768 MiB metaspace,
and about 1.09 GiB of native/compiler overhead and margin. It is not a limit or a reservation;
increase headroom if measured peaks exceed it, and observe ancestors and emulator scopes
during the run. The runner retains device boot, slot and cleanup ownership, its second-emulator
9000 MiB host gate, and the pinned server image.

The wrapper holds `$XDG_RUNTIME_DIR/tokkl-android-mem-gate.lock` through admission and at least
60 seconds of startup, or until the child exits. The child does not inherit it. Settings are:

| Variable | Default | Constraint |
| --- | --- | --- |
| `MEM_GATE_MIN_MB` | `5000` | Host floor, can increase only |
| `MEM_GATE_CGROUP_MB` | `5000` | Ancestor floor, can increase only; instrumented runs require at least `8192` |
| `MEM_GATE_SETTLE_S` | `60` | Startup settling, can increase only |
| `MEM_GATE_WAIT_S` | `300` | Bounded wait for each of lock acquisition and capacity |

Unavailable capacity exits 75 without starting the command; otherwise the wrapper returns
the child's exit status. JSON diagnostics on stderr include host bytes, ancestor limits,
events, pressure and swap counters. Inspect the blocking ancestor's `memory.current`,
`memory.events`, `memory.pressure` and swap across the run. Historical counters alone do not
prove current pressure. Do not repeatedly boot after rejection; checks remain pending.

For a single read-only capacity sample inside the target safety scope:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- \
  python3 scripts/memory-capacity.py --required-mb 8192
```

Host checks for this tooling are `npm test`, `bash -n` and ShellCheck. The Gradle CI command
is `./gradlew --no-daemon assembleDebug check --stacktrace`; it includes lint, JVM tests and
dependency policy. Capacity rejection does not verify it or the API 31/36 device suites.

<!-- kk:related:start -->
# Related

- Related: [map-t3-daily-restart-stops-agent-sessions](map-t3-daily-restart-stops-agent-sessions.md)
- Related: [practice-choose-plain-aosp-images-and-compatible-renderers](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md)
- Related: [practice-respect-emulator-slots-and-resource-ownership](../../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md)
- Related: [practice-let-the-runner-own-emulator-boot-and-settings](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
- Related: [practice-clean-up-only-owned-emulator-resources](../../testing/emulator-runtime/practice-clean-up-only-owned-emulator-resources.md)
- Related: [practice-account-for-emulator-recording-and-screenshot-quirks](../../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md)
<!-- kk:related:end -->
