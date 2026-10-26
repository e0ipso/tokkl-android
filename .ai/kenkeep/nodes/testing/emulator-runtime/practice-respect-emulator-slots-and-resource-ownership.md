---
type: practice
title: Respect emulator slots and resource ownership
description: >-
  Scopes, global locks, slot locks and read-only AVDs isolate concurrent runtime
  work.
tags:
  - testing
  - emulator
  - memory
kk_schema_version: 3
kk_id: practice-respect-emulator-slots-and-resource-ownership
kk_derived_from:
  - scripts/instrumented-tests.sh
kk_relates_to:
  - map-oom-kill-ends-agent-session
  - practice-test-real-tokkl-server-speed-regressions
  - practice-use-the-instrumented-runner-and-its-device-selection
kk_depends_on: []
kk_confidence: high
---
When the systemd user manager answers, the emulator runs in a scope of its own with `OOMPolicy=continue`, `MemoryMax=6G` and `MemorySwapMax=0`, and the scope is `PartOf` the caller's unit. An OOM kill then ends only qemu and the test run, and stopping the caller still stops the emulator. Without a user manager, as in CI, the emulator is a plain child process with no cap. Up to `TOKKL_TEST_SLOTS` runs (default 2) test at once, each on an emulator of its own. All lock files share the prefix `$XDG_RUNTIME_DIR/tokkl-instrumented-tests-<uid>`. Each run locks a slot file (`-slot-<n>.lock`) from its start until its cleanup ends, and a run that finds every slot taken prints the PID, start time and directory of the runs that hold them and waits. A run boots its emulator on a free console port and gives Gradle its serial as `ANDROID_SERIAL`, so Gradle never installs on another run's emulator. Runs boot one at a time under `-boot.lock`. While another run holds a slot, a run waits before booting until `TOKKL_TEST_BOOT_MEMORY_MB` (default 9000) is available. A second emulator takes about 3.3 GB at once, and its Gradle more. With the earlier 5000 MB default the machine stalled under memory pressure (`/proc/pressure/memory` "full" above 80% for minutes), and the emulator's hang detector ended qemu once a thread made no progress for about 15 s. The shell reports that as a segmentation fault, so a "segfault" of qemu under load means memory pressure, not an emulator bug; `report_emulator_log` prints the hang, the memory available and the pressure.

Two emulators cannot both write to one AVD's disk, so with more than one slot every emulator boots with `-read-only` and drops what the run wrote to the AVD. Each emulator also gets its own `XDG_RUNTIME_DIR`, because netsimd is shared otherwise and dies with the first run's scope, taking the other emulator with it. With `TOKKL_TEST_SLOTS=1` runs take turns and keep writing to the AVD. Give every run the same slot count: an emulator that writes to an AVD stops a `-read-only` one of the same AVD from booting.

A run that tests on a device it did not boot, an attached phone or CI's emulator, waits until no other run tests and then tests alone: it holds `$XDG_RUNTIME_DIR/tokkl-instrumented-tests-<uid>.lock` alone, which every other run holds shared. A copy of the script from before the slots takes that file alone, so it still takes turns with these runs. With `TOKKL_TEST_AVD` set, a run fails instead of testing on an attached device that is not an emulator of that AVD or runs another API level. A killed run skips its cleanup, so each run keeps its temporary files in a work directory under `$XDG_RUNTIME_DIR/tokkl-instrumented-tests-<uid>-runs/`, which its slot file names. Once a run holds its slot, it stops every `tokkl-emulator-*.scope` and removes every `tokkl-android-tests-*` container and work directory that no slot holder names.

<!-- kk:related:start -->
# Related

- Related: [map-oom-kill-ends-agent-session](../../build-and-tooling/map-oom-kill-ends-agent-session.md)
- Related: [practice-test-real-tokkl-server-speed-regressions](../practice-test-real-tokkl-server-speed-regressions.md)
- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
<!-- kk:citations:end -->
