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
  - README.md
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
  - map-oom-kill-ends-agent-session
kk_depends_on: []
kk_confidence: high
---
This machine is an always-on homelab server tuned for low idle power. After a run outside the script, run `adb emu kill` and then `adb kill-server`, so neither the emulator nor the adb server keeps running. Run `adb shell sync` first when a change on the emulator's disk has to survive. A file the script deleted without it was back on the next boot. To look for leftovers, use `pgrep -x adb` and `pgrep -af qemu-system`. `pgrep -f adb` also matches `mariadbd` and the shell that runs the command.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [map-oom-kill-ends-agent-session](../../build-and-tooling/map-oom-kill-ends-agent-session.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] [README.md](../../../../../README.md)
<!-- kk:citations:end -->
