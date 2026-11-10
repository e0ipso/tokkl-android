---
type: practice
title: Sign releases and verify optimized APKs
description: >-
  Keep the project key outside the repository and exercise the optimized signed
  artifact, including upgrades.
tags:
  - release
  - fdroid
  - security
kk_schema_version: 3
kk_id: practice-sign-releases-and-verify-optimized-apks
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-fdroid-metadata-release-artifacts
  - practice-own-version-from-v0-1-0-tag
  - practice-compare-clean-unsigned-release-builds
kk_depends_on: []
kk_confidence: medium
---
If the build is reproducible, F-Droid publishes the APK signed with the project's own key, and only when its own build matches. F-Droid's docs ask for pinned build-tools and no embedded timestamps. Shipping builds signed with the project's own key also keeps registration for Google's developer verification possible later.

The release build enables R8 code optimization and resource shrinking with `proguard-android-optimize.txt` and dependency consumer rules. `app/proguard-rules.pro` retains the canonical names of the two Glance widget implementations because their provider registry persists those names across upgrades. It also retains public no-argument constructors of the app's Glance `ActionCallback` implementations. Glance creates them reflectively; retaining the class name alone left widget Start failing with `NoSuchMethodException` in release validation. Debug instrumentation runs `connectedDebugAndroidTest` and does not validate the optimized APK. Exercise the actual release artifact on API 31 and 36, including an in-place upgrade and retained queued work, and record its identity and any coverage limits.

The signing key lives in GitHub Actions secrets, never in the repo.

Release signing reads four environment variables, `TOKKL_RELEASE_KEYSTORE`, `TOKKL_RELEASE_KEYSTORE_PASSWORD`, `TOKKL_RELEASE_KEY_ALIAS` and `TOKKL_RELEASE_KEY_PASSWORD`. With none set, `assembleRelease` builds an unsigned APK. With only some set, the build fails and names the missing ones. A PKCS12 keystore protects the key with the keystore password, so both passwords are the same. The Android Gradle Plugin signs the APK. Never sign it again with `apksigner` from Build-Tools 35 or later, because F-Droid's docs say its signature copy check fails on APKs signed that way.

<!-- kk:related:start -->
# Related

- Related: [map-fdroid-metadata-release-artifacts](../map-fdroid-metadata-release-artifacts.md)
- Related: [practice-own-version-from-v0-1-0-tag](../practice-own-version-from-v0-1-0-tag.md)
- Related: [practice-compare-clean-unsigned-release-builds](practice-compare-clean-unsigned-release-builds.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../../docs/PRD.md)
<!-- kk:citations:end -->
