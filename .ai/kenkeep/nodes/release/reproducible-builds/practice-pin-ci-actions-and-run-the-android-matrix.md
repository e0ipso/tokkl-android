---
type: practice
title: Pin CI actions and run the Android matrix
description: >-
  CI checks debug builds, reproducibility, commit messages and six instrumented
  shards on each supported test API.
tags:
  - release
  - ci
  - android
kk_schema_version: 3
kk_id: practice-pin-ci-actions-and-run-the-android-matrix
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
  - practice-use-the-pinned-test-server-and-tls-proxies
  - practice-keep-instrumented-credentials-out-of-process-arguments
  - practice-pass-class-and-package-lists-through-runner-filter-files
  - practice-verify-device-test-results-and-process-isolation-receipts
  - practice-compare-clean-unsigned-release-builds
kk_depends_on: []
kk_confidence: medium
---
CI on GitHub copies Tokkl's setup. One `ci.yml` workflow runs on pull requests and pushes to `main`, with every action pinned to a commit SHA and its version in a comment. It has four jobs: `gradle` (`./gradlew assembleDebug check`), `reproducible-release` (`scripts/reproducible-release.sh`), `instrumented-tests` and `commitlint`. The emulator job runs the script with `TOKKL_TEST_THROWAWAY_EMULATOR=1`, so the user CA tests run there too. `instrumented-tests` runs six shards per API level, 36 and 31. It adds a udev rule so the emulator can use KVM on GitHub's Ubuntu runner, boots the plain AOSP x86_64 image with `ReactiveCircus/android-emulator-runner` and `-gpu swiftshader_indirect`, sets `hide_error_dialogs`, `show_ime_with_hard_keyboard` and `debug.hwui.renderer` `skiavk`, and runs `scripts/instrumented-tests.sh`. `.github/dependabot.yml` updates GitHub Actions, Gradle and npm dependencies weekly. `gh run list` shows the latest.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../../testing/instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-use-the-pinned-test-server-and-tls-proxies](../../testing/instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md)
- Related: [practice-keep-instrumented-credentials-out-of-process-arguments](../../testing/instrumented-runner/practice-keep-instrumented-credentials-out-of-process-arguments.md)
- Related: [practice-pass-class-and-package-lists-through-runner-filter-files](../../testing/instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md)
- Related: [practice-verify-device-test-results-and-process-isolation-receipts](../../testing/instrumented-runner/practice-verify-device-test-results-and-process-isolation-receipts.md)
- Related: [practice-compare-clean-unsigned-release-builds](practice-compare-clean-unsigned-release-builds.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../../docs/PRD.md)
<!-- kk:citations:end -->
