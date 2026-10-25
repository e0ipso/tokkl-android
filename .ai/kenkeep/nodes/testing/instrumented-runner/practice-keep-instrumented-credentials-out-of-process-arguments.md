---
type: practice
title: Keep instrumented credentials out of process arguments
description: >-
  Private device credential files and durable cleanup prevent passwords and
  tokens leaking through ps.
tags:
  - testing
  - android
  - security
kk_schema_version: 3
kk_id: practice-keep-instrumented-credentials-out-of-process-arguments
kk_derived_from:
  - scripts/instrumented-tests.sh
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4'
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
kk_depends_on: []
kk_confidence: high
---
The password and the API token never go on a command line, because `ps` shows every process's arguments to every user. AGP passes each instrumentation argument on as a `java -D` option and as `adb shell am instrument -e`, so a secret given as `-P...` shows up there even when it stays off Gradle's own command line. `ORG_GRADLE_PROJECT_*` variables cannot carry them either, because the property names hold dots, which dash drops. So the script writes both values to a mode 600 properties file, pushes it to `/data/local/tmp` on each test device with mode 600 there too, and passes only its path as `tokklCredentialsFile`. `TokklTestServer` reads the file with `cat` through `DeviceShell`, which runs as the shell user through UiAutomation. Cleanup deletes the file and runs `sync`, without which the file came back on the next boot. A run killed with SIGKILL, or ended along with its session, skips that cleanup and leaves its file. So before pushing, the script deletes `tokkl-test-credentials-*.properties` files in `/data/local/tmp` that are more than 6 hours old, with toybox `find -mtime +6h -delete` because toybox has no `-mmin`, and syncs.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](practice-use-the-instrumented-runner-and-its-device-selection.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4
<!-- kk:citations:end -->
