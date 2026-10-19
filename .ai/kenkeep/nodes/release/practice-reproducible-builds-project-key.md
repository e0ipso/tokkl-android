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

`scripts/reproducible-release.sh [commit]` clones the commit twice, builds an unsigned `assembleRelease` in each clone and compares the two APKs byte for byte. It builds committed work only. `app/build.gradle.kts` pins Build-Tools 36.0.0, the default and the minimum of AGP 9.4.1, so check the pin whenever AGP changes. `dependenciesInfo` is off, because AGP would otherwise put a dependency block, encrypted for Google, into the signing block, and F-Droid fails such an APK. AGP writes the commit hash into `META-INF/version-control-info.textproto`, so build a release APK only from a clean checkout of the tagged commit. README "Release builds" lists the rest.

CI on GitHub copies Tokkl's setup. One `ci.yml` workflow runs on pull requests and pushes to `main`, with every action pinned to a commit SHA and its version in a comment. It has four jobs: `gradle` (`./gradlew assembleDebug check`), `reproducible-release` (`scripts/reproducible-release.sh`), `instrumented-tests` and `commitlint`. `instrumented-tests` runs once per `api-level` in its matrix, 36 and 30. It adds a udev rule so the emulator can use KVM on GitHub's Ubuntu runner, boots the plain AOSP x86_64 image with `ReactiveCircus/android-emulator-runner`, sets `debug.hwui.renderer` to `skiavk` from API 31 on, and runs `scripts/instrumented-tests.sh`. `.github/dependabot.yml` updates GitHub Actions, Gradle and npm dependencies weekly. The repo is on GitHub now. Its only CI run so far, at 2211a7f on 2026-10-19, passed all four jobs, before the API 30 leg existed, so that leg is unproven until its first green run.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-fdroid-metadata-release-artifacts](map-fdroid-metadata-release-artifacts.md)
- Related: [practice-own-version-from-v0-1-0-tag](practice-own-version-from-v0-1-0-tag.md)
- Related: [practice-run-instrumented-tests-only-through-the-script](../testing/practice-run-instrumented-tests-only-through-the-script.md)
<!-- kk:related:end -->
