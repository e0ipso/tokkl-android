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
The modules are listed in `settings.gradle.kts`. `:app` is only the shell: `TokklApplication`, `MainActivity`, the startup and lifecycle observers, the Koin assembly in `di` and the navigation host in `navhost`. The core libraries are `:core:api`, `:core:time`, `:core:ui`, `:core:data`, `:core:shared` and `:core:navigation`, which holds the routes, the `Navigator` and the `FeatureEntries` interface through which each feature adds its screens. The features are `:feature:signin`, `:feature:timer` (the Timer page with history, calendar, entry form and timer templates), `:feature:pomodoro`, `:feature:reports`, `:feature:projects`, `:feature:clients`, `:feature:tags`, `:feature:trash`, `:feature:settings` (with Licences), `:feature:sync`, `:feature:notification` (with the reminder), `:feature:shortcuts`, `:feature:tile` and `:feature:widget`. A feature depends on core modules only, never on another feature, and binds its own `xFeatureModule` Koin module, which `di/AppModules.kt` lists. Each feature declares its manifest components under their full class names in its own manifest. The included build `build-logic` holds the convention plugins `tokkl.android.application`, `tokkl.android.library`, `tokkl.android.library.compose` and `tokkl.android.feature`, which set compileSdk, Build-Tools, minSdk, Java 17, Compose and the lint input described below; the feature plugin also sets the namespace and the Compose, lifecycle, Koin and JUnit libraries, and each feature's build file names its core modules. `app/build.gradle.kts` keeps the version, release signing, R8, `dependenciesInfo` and `checkDeniedDependencies`. Every module is an Android library, not a JVM one, so its Kotlin compiles against android.jar and a call to a JDK API the phone lacks fails the build. Kotlin packages kept their names. Each module's Android namespace is `com.github.e0ipso.tokkl.core.<name>` or `com.github.e0ipso.tokkl.feature.<name>`, so code reads that module's resources through its R class, such as `com.github.e0ipso.tokkl.core.ui.R`. A resource that several features read sits in core:ui or core:shared. `PackageLayersTest` and `ModuleLayersTest` in `app/src/test` keep packages and modules in layers. Fakes that several modules' tests share are test fixtures (`src/testFixtures`) of core:api, core:ui and core:data. The instrumented tests stay in `app/src/androidTest` and drive the whole app; what they read of a feature's internals is public with `@VisibleForTesting`.

After code moves between modules, Kotlin's incremental compilation can skip a file that now reads another module's `internal` declaration, and the build passes. On 2026-11-06 it hid such an error in the app's unit tests. Check a move with `-Pkotlin.incremental=false` and `--rerun` after each compile task.

`minSdk` is 31, Android 12, since the owner's decision of 2026-10-19. `compileSdk` is 37 because Compose UI 1.12 declares `minCompileSdk` 37 in its AAR metadata, and a lower value fails AGP's AAR metadata check. `targetSdk` stays at 36, the API level of the emulator `tokkl-api36`, so lint reports `OldTargetApi` and the project accepts that warning. Raise `targetSdk` together with the emulator image, not to silence lint.

AGP 9 compiles Kotlin itself and brings Kotlin Gradle plugin 2.2.10. The `kotlin-compose` plugin in `gradle/libs.versions.toml` depends on the Kotlin Gradle plugin of the catalog's `kotlin` version (2.4.21) and raises it, so the root `build.gradle.kts` has no Kotlin classpath entry. Change the Kotlin version in that one catalog entry.

`gradle.properties` allows for the other services on this machine. `org.gradle.jvmargs` caps the daemon heap at `-Xmx2g` and metaspace at 768m, and `kotlin.compiler.execution.strategy=in-process` compiles Kotlin inside the Gradle daemon instead of a second long-lived Kotlin daemon.

`org.gradle.caching=true` turns on Gradle's local build cache in `~/.gradle/caches/build-cache-1`, which every checkout and worktree on the machine shares. Drain sittings each build in a new worktree from the same commit. On a fresh copy of 62beaf3, `assembleDebug assembleDebugAndroidTest testDebugUnitTest` took 1m 57s on an empty cache and 13s on a warm one, with every KSP, Kotlin compile and dex task taken from the cache. `checkDeniedDependencies` declares no outputs, so it runs on every `check`, and `connectedDebugAndroidTest` never uses the cache. `scripts/reproducible-release.sh` builds with `--no-build-cache`, so both of its builds compile from clean, as F-Droid's does.

Lint's analysis cannot be relocated between checkouts. It stores this module's files relative to path variables but files outside it, such as `gradle/libs.versions.toml`, by absolute path, and AGP 9.4.1 passes lint no `--path-variables`. Taken from the shared cache, one worktree's analysis made another worktree's lint report cite the first worktree's files. So the convention plugins in `build-logic` add the checkout's absolute path as an input of every `AndroidLintAnalysisTask` in every module, and the analysis comes from the cache only for the checkout that ran it. Remove that input only once AGP can relocate lint's analysis.

`org.gradle.configuration-cache=true` turns on Gradle's configuration cache. Every usual command, `connectedDebugAndroidTest` through the script included, reuses its entry on a second run: AGP reads the instrumentation runner arguments only when the tests run, so the new ports, fingerprints and filter file of each script run do not invalidate it.

Room 2.8.5 generates its code through KSP 2.3.12, and the Room Gradle plugin writes each database version's schema to `core/data/schemas/`. Those JSON files are tracked. Room's auto-migrations read them, so commit the new schema file with every version bump of `CacheDatabase`.

The wrapper is Gradle 9.8.2. `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` pins its checksum, so change the sum whenever `distributionUrl` changes.

`.gitattributes` keeps `gradlew` at LF and `*.bat` at CRLF, as Gradle's init template does. A `gradlew` with CRLF does not run on Linux, whatever `core.autocrlf` says.

<!-- kk:related:start -->
# Related

- Related: [map-planned-android-stack](map-planned-android-stack.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-account-for-emulator-recording-and-screenshot-quirks](../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-choose-plain-aosp-images-and-compatible-renderers](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-clean-up-only-owned-emulator-resources](../testing/emulator-runtime/practice-clean-up-only-owned-emulator-resources.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-let-the-runner-own-emulator-boot-and-settings](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
- Related: [practice-emulator-plain-aosp-image-headless → practice-respect-emulator-slots-and-resource-ownership](../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md)
- Related: [map-jdk-android-sdk-and-agent-shells](map-jdk-android-sdk-and-agent-shells.md)
<!-- kk:related:end -->
