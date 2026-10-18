---
type: map
title: 'Gradle build settings: SDK levels, Kotlin version source and memory'
description: >-
  compileSdk follows the dependencies, targetSdk matches the emulator, the
  catalog sets Kotlin, and the Gradle heap stays small.
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
`compileSdk` is 37 because Compose UI 1.12 declares `minCompileSdk` 37 in its AAR metadata, and a lower value fails AGP's AAR metadata check. `targetSdk` stays at 36, the API level of the emulator `tokkl-api36`, so lint reports `OldTargetApi` and the project accepts that warning. Raise `targetSdk` together with the emulator image, not to silence lint.

AGP 9 compiles Kotlin itself and brings Kotlin Gradle plugin 2.2.10. The `kotlin-compose` plugin in `gradle/libs.versions.toml` depends on the Kotlin Gradle plugin of the catalog's `kotlin` version (2.4.21) and raises it, so the root `build.gradle.kts` has no Kotlin classpath entry. Change the Kotlin version in that one catalog entry.

`gradle.properties` allows for the other services on this machine. `org.gradle.jvmargs` caps the daemon heap at `-Xmx2g` and metaspace at 768m, and `kotlin.compiler.execution.strategy=in-process` compiles Kotlin inside the Gradle daemon instead of a second long-lived Kotlin daemon.

The wrapper is Gradle 9.8.1. `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` pins its checksum, so change the sum whenever `distributionUrl` changes.

`.gitattributes` keeps `gradlew` at LF and `*.bat` at CRLF, as Gradle's init template does. A `gradlew` with CRLF does not run on Linux, whatever `core.autocrlf` says.

<!-- kk:related:start -->
# Related

- Related: [map-planned-android-stack](map-planned-android-stack.md)
- Related: [practice-emulator-plain-aosp-image-headless](practice-emulator-plain-aosp-image-headless.md)
- Related: [map-jdk-android-sdk-and-agent-shells](map-jdk-android-sdk-and-agent-shells.md)
<!-- kk:related:end -->
