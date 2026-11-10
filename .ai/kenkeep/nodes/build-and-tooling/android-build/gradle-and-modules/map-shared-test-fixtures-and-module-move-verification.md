---
type: map
title: Shared test fixtures and module move verification
description: >-
  Test placement preserves internal visibility and checks moved code without
  incremental compilation.
tags:
  - testing
  - architecture
  - gradle
kk_schema_version: 3
kk_id: map-shared-test-fixtures-and-module-move-verification
kk_derived_from: []
kk_relates_to:
  - map-core-and-feature-module-boundaries
  - practice-respect-emulator-slots-and-resource-ownership
  - practice-clean-up-only-owned-emulator-resources
kk_depends_on: []
kk_confidence: high
---
Fakes that several modules' tests share are test fixtures (`src/testFixtures`) of core:api (through `java-test-fixtures`), core:ui, core:platform (`InMemoryValueStore`, and `MainDispatcherRule` for coroutine tests in virtual time), core:session (`CountingRefresh`, `capabilitiesOf`, `accountSettingsOf` and `accountSettingsIn`) and core:timerstate (`ServerTimerStates` and `FixedTimerState`). The instrumented tests that need the app, MainActivity, the test server, the session rule, Koin or the screenshots stay in `app/src/androidTest`. A test that draws one screen's composables, or reads one store, and needs none of that lives in its feature's `src/androidTest` and runs in that feature's own test APK, under the same runner, orchestrator and targetSdk, so it reads the feature's `internal` declarations. What the app's tests read of a feature must be public, since Kotlin has no package-private. A pure unit the app's tests use, such as an intent builder, a formatter or a notification's `build`, is public with KDoc and no `@VisibleForTesting`. A name that is a contract with stored data or the platform, such as a file name, a Keystore alias or a notification channel id, stays private, and the test spells the literal out, so a rename fails it. Device tests observe ringing through the platform audio, vibration and activity services, and wrap handlers in their test Koin graph; production keeps no observation-only fields or callbacks. `:core:testing` holds what both kinds share: `AccessibilityTestRunner`, `StepAwareShardingFilter`, the accessibility compose rules, `DeviceShell`, `SystemFontScale`, `SystemDisplaySize` and the Pomodoro length card matchers. Only androidTest source sets depend on it, so the architecture tests leave it out of the module graph.

After code moves between modules, Kotlin's incremental compilation can skip a file that now reads another module's `internal` declaration, and the build passes. On 2026-11-06 it hid such an error in the app's unit tests. Check a move with `-Pkotlin.incremental=false` and `--rerun` after each compile task.

<!-- kk:related:start -->
# Related

- Related: [map-core-and-feature-module-boundaries](map-core-and-feature-module-boundaries.md)
- Related: [practice-respect-emulator-slots-and-resource-ownership](../../../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md)
- Related: [practice-clean-up-only-owned-emulator-resources](../../../testing/emulator-runtime/practice-clean-up-only-owned-emulator-resources.md)
<!-- kk:related:end -->
