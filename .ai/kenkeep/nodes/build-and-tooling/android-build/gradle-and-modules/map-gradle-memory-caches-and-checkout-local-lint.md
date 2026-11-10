---
type: map
title: Gradle memory caches and checkout-local lint
description: >-
  Bounded heap, in-process Kotlin, shared build cache and checkout-specific lint
  analysis govern host builds.
tags:
  - build
  - gradle
  - memory
kk_schema_version: 3
kk_id: map-gradle-memory-caches-and-checkout-local-lint
kk_derived_from: []
kk_relates_to:
  - map-jdk-android-sdk-and-agent-shells
  - practice-respect-emulator-slots-and-resource-ownership
  - practice-let-the-runner-own-emulator-boot-and-settings
kk_depends_on: []
kk_confidence: high
---
`gradle.properties` allows for the other services on this machine. `org.gradle.jvmargs` caps the daemon heap at `-Xmx2g` and metaspace at 768m, and `kotlin.compiler.execution.strategy=in-process` compiles Kotlin inside the Gradle daemon instead of a second long-lived Kotlin daemon.

`org.gradle.caching=true` turns on Gradle's local build cache in `~/.gradle/caches/build-cache-1`, which every checkout and worktree on the machine shares. Drain sittings each build in a new worktree from the same commit. On a fresh copy of 62beaf3, `assembleDebug assembleDebugAndroidTest testDebugUnitTest` took 1m 57s on an empty cache and 13s on a warm one, with every KSP, Kotlin compile and dex task taken from the cache. `checkDeniedDependencies` declares no outputs, so it runs on every `check`, and `connectedDebugAndroidTest` never uses the cache. `scripts/reproducible-release.sh` builds with `--no-build-cache`, so both of its builds compile from clean, as F-Droid's does.

Lint's analysis cannot be relocated between checkouts. It stores this module's files relative to path variables but files outside it, such as `gradle/libs.versions.toml`, by absolute path, and AGP 9.4.1 passes lint no `--path-variables`. Taken from the shared cache, one worktree's analysis made another worktree's lint report cite the first worktree's files. So the convention plugins in `build-logic` add the checkout's absolute path as an input of every lint analysis task in every module, and the analysis comes from the cache only for the checkout that ran it. They find those tasks by name, `lintAnalyze<Variant>` and `lintVitalAnalyze<Variant>`, because their class, `AndroidLintAnalysisTask`, is internal to AGP. With AGP 9.4.1 the names matched the same 167 tasks as the class on 2026-11-08; check that again when AGP changes. Remove that input only once AGP can relocate lint's analysis.

`org.gradle.configuration-cache=true` turns on Gradle's configuration cache. Every usual command, `connectedDebugAndroidTest` through the script included, reuses its entry on a second run: AGP reads the instrumentation runner arguments only when the tests run, so the new ports, fingerprints and filter file of each script run do not invalidate it.

<!-- kk:related:start -->
# Related

- Related: [map-jdk-android-sdk-and-agent-shells](../../host-runtime/map-jdk-android-sdk-and-agent-shells.md)
- Related: [practice-respect-emulator-slots-and-resource-ownership](../../../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md)
- Related: [practice-let-the-runner-own-emulator-boot-and-settings](../../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
<!-- kk:related:end -->
