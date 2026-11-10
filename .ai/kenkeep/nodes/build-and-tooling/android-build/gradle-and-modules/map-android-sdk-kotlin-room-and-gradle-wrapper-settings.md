---
type: map
title: Android SDK Kotlin Room and Gradle wrapper settings
description: >-
  SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have
  explicit owners.
tags:
  - android
  - build
  - gradle
kk_schema_version: 3
kk_id: map-android-sdk-kotlin-room-and-gradle-wrapper-settings
kk_derived_from: []
kk_relates_to:
  - map-jdk-android-sdk-and-agent-shells
  - practice-choose-plain-aosp-images-and-compatible-renderers
  - practice-let-the-runner-own-emulator-boot-and-settings
  - practice-account-for-emulator-recording-and-screenshot-quirks
kk_depends_on: []
kk_confidence: high
---
`minSdk` is 31, Android 12, since the owner's decision of 2026-10-19. `TARGET_SDK` in build-logic's `AndroidConventions.kt` holds the targetSdk for the app and for the feature modules' test APKs. `compileSdk` is 37 because Compose UI 1.12 declares `minCompileSdk` 37 in its AAR metadata, and a lower value fails AGP's AAR metadata check. `targetSdk` stays at 36, the API level of the emulator `tokkl-api36`, so lint reports `OldTargetApi` and the project accepts that warning. Raise `targetSdk` together with the emulator image, not to silence lint.

The catalog pins AGP 9.5.0. AGP 9 compiles Kotlin itself and brings Kotlin Gradle plugin 2.2.10. The `kotlin-compose` plugin in `gradle/libs.versions.toml` depends on the Kotlin Gradle plugin of the catalog's `kotlin` version (2.4.21) and raises it, so the root `build.gradle.kts` has no Kotlin classpath entry. Change the Kotlin version in that one catalog entry.

Room 2.8.5 generates its code through KSP 2.3.12, and the Room Gradle plugin writes each database version's schema to `core/cache/schemas/`. Those JSON files are tracked. Room's auto-migrations read them, so commit the new schema file with every version bump of `CacheDatabase`.

The wrapper is Gradle 9.8.2. `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` pins its checksum, so change the sum whenever `distributionUrl` changes.

`.gitattributes` keeps `gradlew` at LF and `*.bat` at CRLF, as Gradle's init template does. A `gradlew` with CRLF does not run on Linux, whatever `core.autocrlf` says.

<!-- kk:related:start -->
# Related

- Related: [map-jdk-android-sdk-and-agent-shells](../../host-runtime/map-jdk-android-sdk-and-agent-shells.md)
- Related: [practice-choose-plain-aosp-images-and-compatible-renderers](../../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md)
- Related: [practice-let-the-runner-own-emulator-boot-and-settings](../../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
- Related: [practice-account-for-emulator-recording-and-screenshot-quirks](../../../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md)
<!-- kk:related:end -->
