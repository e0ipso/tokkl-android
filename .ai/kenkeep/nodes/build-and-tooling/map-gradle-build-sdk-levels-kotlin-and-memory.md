---
type: map
title: 'Gradle build settings: SDK levels, Kotlin version, memory and build cache'
description: >-
  minSdk 31, compileSdk from the dependencies, targetSdk from the emulator,
  Kotlin from the catalog, a small heap and the local build cache.
tags:
  - android
  - build
  - gradle
kk_schema_version: 3
kk_id: map-gradle-build-sdk-levels-kotlin-and-memory
kk_derived_from: []
kk_relates_to:
  - map-planned-android-stack
  - practice-emulator-plain-aosp-image-headless
  - map-jdk-android-sdk-and-agent-shells
kk_depends_on: []
kk_confidence: high
---
`minSdk` is 31, Android 12, since the owner's decision of 2026-10-19. `compileSdk` is 37 because Compose UI 1.12 declares `minCompileSdk` 37 in its AAR metadata, and a lower value fails AGP's AAR metadata check. `targetSdk` stays at 36, the API level of the emulator `tokkl-api36`, so lint reports `OldTargetApi` and the project accepts that warning. Raise `targetSdk` together with the emulator image, not to silence lint.

AGP 9 compiles Kotlin itself and brings Kotlin Gradle plugin 2.2.10. The `kotlin-compose` plugin in `gradle/libs.versions.toml` depends on the Kotlin Gradle plugin of the catalog's `kotlin` version (2.4.21) and raises it, so the root `build.gradle.kts` has no Kotlin classpath entry. Change the Kotlin version in that one catalog entry.

`gradle.properties` allows for the other services on this machine. `org.gradle.jvmargs` caps the daemon heap at `-Xmx2g` and metaspace at 768m, and `kotlin.compiler.execution.strategy=in-process` compiles Kotlin inside the Gradle daemon instead of a second long-lived Kotlin daemon.

`org.gradle.caching=true` turns on Gradle's local build cache in `~/.gradle/caches/build-cache-1`, which every checkout and worktree on the machine shares. Drain sittings each build in a new worktree from the same commit. On a fresh copy of 62beaf3, `assembleDebug assembleDebugAndroidTest testDebugUnitTest` took 1m 57s on an empty cache and 13s on a warm one, with every KSP, Kotlin compile and dex task taken from the cache. `checkDeniedDependencies` declares no outputs, so it runs on every `check`, and `connectedDebugAndroidTest` never uses the cache. `scripts/reproducible-release.sh` builds with `--no-build-cache`, so both of its builds compile from clean, as F-Droid's does.

Room 2.8.5 generates its code through KSP 2.3.12, and the Room Gradle plugin writes each database version's schema to `app/schemas/`. Those JSON files are tracked. Room's auto-migrations read them, so commit the new schema file with every version bump of `CacheDatabase`.

The wrapper is Gradle 9.8.1. `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` pins its checksum, so change the sum whenever `distributionUrl` changes.

`.gitattributes` keeps `gradlew` at LF and `*.bat` at CRLF, as Gradle's init template does. A `gradlew` with CRLF does not run on Linux, whatever `core.autocrlf` says.

<!-- kk:related:start -->
# Related

- Related: [map-planned-android-stack](map-planned-android-stack.md)
- Related: [practice-emulator-plain-aosp-image-headless](../testing/practice-emulator-plain-aosp-image-headless.md)
- Related: [map-jdk-android-sdk-and-agent-shells](map-jdk-android-sdk-and-agent-shells.md)
<!-- kk:related:end -->
