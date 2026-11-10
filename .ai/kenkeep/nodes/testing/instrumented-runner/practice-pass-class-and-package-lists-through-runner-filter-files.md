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
Extra arguments go to Gradle. One class runs with `-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest`, one test with `class=<class>#<method>`. A comma-separated `class=` or `package=` list works too. AGP hands the runner all its arguments in one comma-joined string and splits it at every comma, so a raw `class=A,B` ran only `A` and still passed. The script writes every `class=` or `package=` entry, single or in a list, to a `tokkl-test-filter-*` file beside the credentials file, one entry per line, and passes its path as `testFile`. It refuses a comma in any other runner argument, and a `testFile` of your own beside a filter.

A test APK fails a class it does not hold, and the app and each feature module with a `src/androidTest` directory have test APKs of their own. So the script gives each entry only to the modules whose test sources hold it: a class to the module that declares it, a package to every module with sources in it. It refuses an entry that no module holds before it takes a slot, and runs Gradle once for each module the filter names, each with its own filter file.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-gradle-no-daemon-in-parallel-worktrees](../../build-and-tooling/host-runtime/practice-gradle-no-daemon-in-parallel-worktrees.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4
<!-- kk:citations:end -->
