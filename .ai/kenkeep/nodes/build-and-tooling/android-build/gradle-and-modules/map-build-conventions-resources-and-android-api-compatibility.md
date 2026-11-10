---
type: map
title: Build conventions resources and Android API compatibility
description: >-
  Convention plugins configure modules; Animal Sniffer checks pure JVM code
  against Android 12 APIs.
tags:
  - android
  - build
  - gradle
kk_schema_version: 3
kk_id: map-build-conventions-resources-and-android-api-compatibility
kk_derived_from: []
kk_relates_to:
  - map-core-and-feature-module-boundaries
  - map-android-sdk-kotlin-room-and-gradle-wrapper-settings
kk_depends_on: []
kk_confidence: high
---
The included build `build-logic` holds the convention plugins `tokkl.android.application`, `tokkl.android.library`, `tokkl.android.library.compose`, `tokkl.android.feature`, `tokkl.android.feature.screen` and `tokkl.jvm.library`, which set compileSdk, Build-Tools, minSdk, the app's targetSdk, Java 17, Compose, detekt and the lint input described below; the library plugin also sets each module's namespace from its Gradle path, the library and JVM plugins add the JUnit and coroutine test libraries (`addUnitTestLibraries` in `AndroidConventions.kt`), the feature plugin adds Koin and the instrumented test setup, and the screen feature plugin adds Compose with Material 3, Compose UI, the view model's Compose integration and Koin's. The ten features with routes and view models apply the screen plugin; `:feature:notification`, `:feature:tile` and `:feature:widget` apply the feature plugin with `tokkl.android.library.compose` and name the Compose libraries they draw with, and `:feature:shortcuts`, which draws nothing, applies the feature plugin alone. Each feature's build file names its core modules. `app/build.gradle.kts` keeps the version, release signing, R8, `dependenciesInfo` and `checkDeniedDependencies`. `:core:model`, `:core:api` and `:core:time` have no Android code and build with `tokkl.jvm.library`: Kotlin/JVM at Java 17, Android lint through `com.android.lint`, and detekt. They compile against the JDK, not android.jar, and Android lint has no minSdk for a Kotlin/JVM module, so its NewApi check stays silent there: on 2026-11-08 a call to `java.util.HexFormat` (API 34) passed lint in core:api and core:time, also with `checkDependencies` on the app, while the same call in core:platform failed. So `tokkl.jvm.library` also applies Animal Sniffer (`ru.vyarus.animalsniffer`, MIT) with the API 31 signature of gummy-bears (`com.toasttab.android:gummy-bears-api-31`, Apache-2.0): `animalsnifferMain` checks the main classes, `check` and `lint` run it, and a call that Android 12 lacks fails the build. Their unit tests are `test`, not `testDebugUnitTest`, and their lint reports are `build/reports/lint-results.*`. Every other module is an Android library, whose Kotlin compiles against android.jar. Kotlin packages kept their names. Each module's Android namespace is `com.github.e0ipso.tokkl.core.<name>` or `com.github.e0ipso.tokkl.feature.<name>`, so code reads that module's resources through its R class, such as `com.github.e0ipso.tokkl.core.ui.R`. A resource lives in the module whose code reads it. One that several modules read and no shared code does, such as the Pomodoro, date, template and timer control strings, sits in core:ui. `PackageLayersTest` and `ModuleLayersTest` in `app/src/test` keep packages and modules in layers, and `ModuleLayersTest` also fails on a cycle between modules.

<!-- kk:related:start -->
# Related

- Related: [map-core-and-feature-module-boundaries](map-core-and-feature-module-boundaries.md)
- Related: [map-android-sdk-kotlin-room-and-gradle-wrapper-settings](map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md)
<!-- kk:related:end -->
