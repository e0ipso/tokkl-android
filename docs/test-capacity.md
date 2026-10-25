# Local test capacity

Host `MemAvailable` does not include cgroup limits. A process can have free host
RAM while its ancestor is reclaiming at `memory.high`, before `memory.max` causes
an OOM. Swap is not additional admission headroom, especially for the emulator's
no-swap scope.

`scripts/mem-gate.sh CMD [ARGS...]` checks both the physical RAM floor and every
visible cgroup v2 ancestor, including the caller's scope. It requires at least
5000 MiB of host RAM and 5000 MiB of headroom below **both** `memory.high` and
`memory.max` in each ancestor. `max` means unlimited. The hierarchy root normally
has no memory controller files. Missing or malformed mandatory files elsewhere,
or a subtree-only cgroup mount hiding ancestors, fail closed with exit 2.

Commands containing `instrumented-tests.sh` require at least 8192 MiB of ancestor
headroom. This is an admission estimate for one emulator plus Gradle: the observed
qemu peak was 4467671040 bytes, about 4.16 GiB, Gradle allows a 2 GiB heap and
768 MiB metaspace, and the remaining approximately 1.09 GiB covers native/compiler
overhead and margin. It is not a new memory limit or a guarantee against competing
allocations. The emulator retains its own 6 GiB cap and zero swap. If observed
combined peaks exceed this estimate, increase the requested headroom.

The wrapper shares `$XDG_RUNTIME_DIR/tokkl-android-mem-gate.lock` with the drain.
It holds the lock through admission and for at least 60 seconds of startup, or
until the child exits. The child does not inherit the lock. Defaults are
`MEM_GATE_MIN_MB=5000`, `MEM_GATE_CGROUP_MB=5000`, `MEM_GATE_SETTLE_S=60`, and
`MEM_GATE_WAIT_S=300`. Floors and settling cannot be reduced. Increasing them is
supported. Lock acquisition and capacity waiting each have that bounded timeout;
unavailable capacity exits 75 without starting the command. The command's exit
status is returned. Diagnostics are JSON on stderr, including bytes available,
every ancestor, events, pressure, swap counters and the blocking limit.

Use the prescribed outer safety scope, with `--no-daemon` for Gradle. The gate
does not change cgroup limits or move the workload. `scripts/instrumented-tests.sh`
still owns device boot, slots, the boot lock, cleanup, the second-emulator 9000 MiB
host gate and the pinned server image. This local wrapper requires Linux cgroup v2
and Python 3. It does not change CI's emulator workflow.

For a read-only, single-sample preflight in the target workload's scope:

```sh
systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- \
  /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh \
  python3 scripts/memory-capacity.py --required-mb 8192
```

Exit 75 is evidence that capacity is unavailable, not a successful native run.
Do not repeatedly boot emulators after rejection. Check `memory.current`,
`memory.events`, `memory.pressure` and swap for the blocking ancestor. Historical
event counters alone do not prove current pressure; compare samples over the run.

## Shared shim activation after integration

The shared ignored gate is operational state. Do not activate this replacement
until the tracked scripts are integrated into the main checkout. The coordinator
can then preserve the existing shim for rollback and replace it with an exec into
the tracked wrapper. Do not nest the two gates: they hold the same startup lock.

```sh
cp /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh \
  /home/e0ipso/.cache/sift-w10/cgroup-capacity-0265/mem-gate.before-activation.sh
cat > /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh <<'SH'
#!/usr/bin/env bash
exec /home/e0ipso/workspace/apps/tokkl-android/scripts/mem-gate.sh "$@"
SH
chmod +x /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh
```

Rollback copies `mem-gate.before-activation.sh` back to the shared shim path.
Keep the `systemd-run`/`choom` prefix unchanged after activation. Start only one
owned build/device call at a time. Admission is a startup check, not an allocation
reservation; serialize owned runs and observe ancestors and emulator scopes
throughout device verification.

## Host checks

`npm test` runs the host suite. Use `bash -n` and ShellCheck on
the shell scripts. The Gradle CI authority is `./gradlew --no-daemon assembleDebug
check --stacktrace`, which includes Android lint, JVM tests and dependency policy.
Capacity rejection leaves those checks and the API 31/36 suites explicitly pending.
