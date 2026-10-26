---
type: practice
title: Keep release builds reproducible and signed with the project key
description: >-
  Reproducible builds let F-Droid publish the APK signed with the project key.
  The key lives in Actions secrets and CI pins actions by SHA.
tags:
  - release
  - fdroid
  - security
  - ci
kk_schema_version: 3
kk_id: practice-reproducible-builds-project-key
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-fdroid-metadata-release-artifacts
  - practice-own-version-from-v0-1-0-tag
  - practice-run-instrumented-tests-only-through-the-script
kk_depends_on: []
kk_confidence: medium
---
If the build is reproducible, F-Droid publishes the APK signed with the project's own key, and only when its own build matches. F-Droid's docs ask for pinned build-tools and no embedded timestamps. Shipping builds signed with the project's own key also keeps registration for Google's developer verification possible later.

The signing key lives in GitHub Actions secrets, never in the repo.

Release signing reads four environment variables, `TOKKL_RELEASE_KEYSTORE`, `TOKKL_RELEASE_KEYSTORE_PASSWORD`, `TOKKL_RELEASE_KEY_ALIAS` and `TOKKL_RELEASE_KEY_PASSWORD`. With none set, `assembleRelease` builds an unsigned APK. With only some set, the build fails and names the missing ones. A PKCS12 keystore protects the key with the keystore password, so both passwords are the same. The Android Gradle Plugin signs the APK. Never sign it again with `apksigner` from Build-Tools 35 or later, because F-Droid's docs say its signature copy check fails on APKs signed that way.

`scripts/reproducible-release.sh [commit]` clones the commit twice, builds an unsigned `assembleRelease` in each clone with `--no-daemon --no-build-cache` and compares the two APKs byte for byte. Without `--no-build-cache` the first build would take task outputs from earlier builds on the same machine instead of building from clean, as F-Droid does. It builds committed work only. `app/build.gradle.kts` pins Build-Tools 36.0.0, the default and the minimum of AGP 9.4.1, so check the pin whenever AGP changes. `dependenciesInfo` is off, because AGP would otherwise put a dependency block, encrypted for Google, into the signing block, and F-Droid fails such an APK. AGP writes the commit hash into `META-INF/version-control-info.textproto`, so build a release APK only from a clean checkout of the tagged commit. Every zip entry carries the fixed date 1981-01-01 and no extra timestamp field. The manifest records `platformBuildVersionName='17'` and `platformBuildVersionCode='37'` from the `platforms;android-37.0` package, revision 2 here. Gradle cannot pin a platform revision, so a newer revision on F-Droid's server could change them. The ART baseline profile (`assets/dexopt/baseline.prof` and `baseline.profm`) stays in the APK because it speeds up start-up and came out the same on 8 and on 2 cores. If the script ever shows it differing, turn off the tasks whose names contain `ArtProfile`, as F-Droid's docs describe. The script's second build runs on at most two cores with its own Gradle user home, and the clones sit at paths of different lengths, so an embedded path, core-count-dependent output or an unstable transform all show up. When the APKs differ, it lists the differing zip entries and keeps both APKs. The two builds take about four minutes here and up to about 700 MB under `build/`, which the script removes.

CI on GitHub copies Tokkl's setup. One `ci.yml` workflow runs on pull requests and pushes to `main`, with every action pinned to a commit SHA and its version in a comment. It has five jobs: `gradle` (`./gradlew assembleDebug check`), `reproducible-release` (`scripts/reproducible-release.sh`), `instrumented-tests`, `benchmarks` (the macrobenchmarks and their 20%-and-34-ms gate, see the speed node) and `commitlint`. Both emulator jobs run the script with `TOKKL_TEST_THROWAWAY_EMULATOR=1`, so the user CA tests run there too. `instrumented-tests` runs once per `api-level` in its matrix, 36 and 31. It adds a udev rule so the emulator can use KVM on GitHub's Ubuntu runner, boots the plain AOSP x86_64 image with `ReactiveCircus/android-emulator-runner` and `-gpu swiftshader_indirect`, sets `hide_error_dialogs`, `show_ime_with_hard_keyboard` and `debug.hwui.renderer` `skiavk`, and runs `scripts/instrumented-tests.sh`. `.github/dependabot.yml` updates GitHub Actions, Gradle and npm dependencies weekly. As of 2026-10-20, `origin/main` is still at 2211a7f, and the CI runs at that commit on 2026-10-19 passed the four jobs it had then, with the instrumented tests on API 36 only. Nothing since has been pushed, so the API 31 leg, the `benchmarks` job, the build and configuration caches and the tests of waves 8 and 9 have no CI run yet. `gh run list` shows the latest.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-fdroid-metadata-release-artifacts](map-fdroid-metadata-release-artifacts.md)
- Related: [practice-own-version-from-v0-1-0-tag](practice-own-version-from-v0-1-0-tag.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-keep-instrumented-credentials-out-of-process-arguments](../testing/instrumented-runner/practice-keep-instrumented-credentials-out-of-process-arguments.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-pass-class-and-package-lists-through-runner-filter-files](../testing/instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-use-the-instrumented-runner-and-its-device-selection](../testing/instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-use-the-pinned-test-server-and-tls-proxies](../testing/instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-verify-device-test-results-and-process-isolation-receipts](../testing/instrumented-runner/practice-verify-device-test-results-and-process-isolation-receipts.md)
<!-- kk:related:end -->
