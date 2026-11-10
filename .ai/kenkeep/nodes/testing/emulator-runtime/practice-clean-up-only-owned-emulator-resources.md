---
type: practice
title: Clean up only owned emulator resources
description: >-
  Durable device cleanup and exact adb process matching prevent leaks and
  unrelated shutdowns.
tags:
  - testing
  - emulator
  - energy
kk_schema_version: 3
kk_id: practice-clean-up-only-owned-emulator-resources
kk_derived_from:
  - scripts/instrumented-tests.sh
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
  - map-oom-kill-ends-agent-session
kk_depends_on: []
kk_confidence: high
---
This machine is an always-on homelab server tuned for low idle power. Let the runner clean up its emulators and adb server. For interactive work, use the Device tools and close only resources this task opened. To inspect leftovers without stopping them, use `pgrep -x adb` and `pgrep -af qemu-system`. `pgrep -f adb` also matches `mariadbd` and the shell that runs the command.

An emulator the script boots gets its own `emulator-tmp` directory under the run's work directory through `ANDROID_TMP`, which the emulator's `System::getTempDir` reads on Linux instead of `/tmp/android-$USER`. The slot file names that directory and `emulator.scope` records the scope before launch. Cleanup waits for the emulator and stops its scope before it removes the directory, also after a failed start or INT/TERM. When it cannot confirm the shutdown, it keeps the run directory and reports a failure. Orphan recovery removes the temporary data only after launch completed, the recorded launcher PID is gone and the scope is inactive. It keeps an orphan from a host without systemd for a person to look at, never sweeps `/tmp/android-$USER` and never changes AVD images.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [map-oom-kill-ends-agent-session](../../build-and-tooling/map-oom-kill-ends-agent-session.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
<!-- kk:citations:end -->
