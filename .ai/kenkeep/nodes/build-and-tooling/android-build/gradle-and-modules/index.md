# kenkeep Index: build-and-tooling / android-build / gradle-and-modules

↑ Parent: [android-build](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Core and feature module boundaries**](map-core-and-feature-module-boundaries.md) to learn about: Layered core modules expose only contract dependencies; features own routes and platform components. #architecture #android #gradle
- Open [**Android SDK Kotlin Room and Gradle wrapper settings**](map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md) to learn about: SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have explicit owners. #android #build #gradle
- Open [**App composition and lifecycle hooks**](map-app-composition-and-lifecycle-hooks.md) to learn about: Feature-owned Koin definitions, ordered AppHooks and injected dispatchers keep app wiring small. #architecture #android #build
- Open [**Build conventions resources and Android API compatibility**](map-build-conventions-resources-and-android-api-compatibility.md) to learn about: Convention plugins configure modules; Animal Sniffer checks pure JVM code against Android 12 APIs. #android #build #gradle
- Open [**Gradle memory caches and checkout-local lint**](map-gradle-memory-caches-and-checkout-local-lint.md) to learn about: Bounded heap, in-process Kotlin, shared build cache and checkout-specific lint analysis govern host builds. #build #gradle #memory
- Open [**Shared test fixtures and module move verification**](map-shared-test-fixtures-and-module-move-verification.md) to learn about: Test placement preserves internal visibility and checks moved code without incremental compilation. #testing #architecture #gradle

## By topic

### #gradle
- Open [**Android SDK Kotlin Room and Gradle wrapper settings**](map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md) — SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have explicit owners.
- Open [**Build conventions resources and Android API compatibility**](map-build-conventions-resources-and-android-api-compatibility.md) — Convention plugins configure modules; Animal Sniffer checks pure JVM code against Android 12 APIs.
- Open [**Pass class and package lists through runner filter files**](../../../testing/instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md) — The script preserves comma-separated test selection despite AGP argument splitting.
### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../../../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #build
- Open [**Android SDK Kotlin Room and Gradle wrapper settings**](map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md) — SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have explicit owners.
- Open [**Build conventions resources and Android API compatibility**](map-build-conventions-resources-and-android-api-compatibility.md) — Convention plugins configure modules; Animal Sniffer checks pure JVM code against Android 12 APIs.
- Open [**Fix detekt findings in code, and baseline only a fix that changes behaviour**](../practice-fix-detekt-findings-in-code.md) — check runs detekt 2 with ktlint's intellij_idea rules and the Compose rules on every module. Fix findings in code; a baseline entry needs a behaviour change as its reason.
### #architecture
- Open [**Core and feature module boundaries**](map-core-and-feature-module-boundaries.md) — Layered core modules expose only contract dependencies; features own routes and platform components.
- Open [**Android stack: shipped UI, cache and native timer surfaces**](../map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
- Open [**App composition and lifecycle hooks**](map-app-composition-and-lifecycle-hooks.md) — Feature-owned Koin definitions, ordered AppHooks and injected dispatchers keep app wiring small.
### #memory
- Open [**An OOM kill inside an agent session's scope ends the whole session**](../../host-runtime/map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**Respect emulator slots and resource ownership**](../../../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md) — Scopes, global locks, slot locks and read-only AVDs isolate concurrent runtime work.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](../../host-runtime/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
### #testing
- Open [**Let the runner own emulator boot and settings**](../../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../../../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.