---
type: practice
title: Pass class and package lists through runner filter files
description: >-
  The script preserves comma-separated test selection despite AGP argument
  splitting.
tags:
  - testing
  - android
  - gradle
kk_schema_version: 3
kk_id: practice-pass-class-and-package-lists-through-runner-filter-files
kk_derived_from:
  - scripts/instrumented-tests.sh
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4'
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
  - practice-gradle-no-daemon-in-parallel-worktrees
kk_depends_on: []
kk_confidence: high
---
Extra arguments go to Gradle. One class runs with `-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest`, one test with `class=<class>#<method>`. A comma-separated `class=` or `package=` list works too. AGP hands the runner all its arguments in one comma-joined string and splits it at every comma, so a raw `class=A,B` ran only `A` and still passed. The script writes such a list to a `tokkl-test-filter-*` file beside the credentials file, one entry per line, and passes its path as `testFile`. It refuses a comma in any other runner argument, and a `testFile` of your own beside a list.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-gradle-no-daemon-in-parallel-worktrees](../../build-and-tooling/practice-gradle-no-daemon-in-parallel-worktrees.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4
<!-- kk:citations:end -->
