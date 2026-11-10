# kenkeep Index: build-and-tooling / android-build

↑ Parent: [build-and-tooling](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`gradle-and-modules/`](gradle-and-modules/index.md) for more information on module boundaries, Koin composition, build conventions, Android compatibility and Gradle caches; read before changing dependencies, wiring or build settings.

## Conventions (how we build)
- Open [**Fix detekt findings in code, and baseline only a fix that changes behaviour**](practice-fix-detekt-findings-in-code.md) to learn about: check runs detekt 2 with ktlint's intellij_idea rules and the Compose rules on every module. Fix findings in code; a baseline entry needs a behaviour change as its reason. #build #gradle #kotlin

## Components (what exists)
- Open [**Android stack: shipped UI, cache and native timer surfaces**](map-planned-android-stack.md) to learn about: Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built. #architecture #android #offline

## By topic

### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #architecture
- Open [**Core and feature module boundaries**](gradle-and-modules/map-core-and-feature-module-boundaries.md) — Layered core modules expose only contract dependencies; features own routes and platform components.
- Open [**Android stack: shipped UI, cache and native timer surfaces**](map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
- Open [**App composition and lifecycle hooks**](gradle-and-modules/map-app-composition-and-lifecycle-hooks.md) — Feature-owned Koin definitions, ordered AppHooks and injected dispatchers keep app wiring small.
### #build
- Open [**Android SDK Kotlin Room and Gradle wrapper settings**](gradle-and-modules/map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md) — SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have explicit owners.
- Open [**Build conventions resources and Android API compatibility**](gradle-and-modules/map-build-conventions-resources-and-android-api-compatibility.md) — Convention plugins configure modules; Animal Sniffer checks pure JVM code against Android 12 APIs.
- Open [**Fix detekt findings in code, and baseline only a fix that changes behaviour**](practice-fix-detekt-findings-in-code.md) — check runs detekt 2 with ktlint's intellij_idea rules and the Compose rules on every module. Fix findings in code; a baseline entry needs a behaviour change as its reason.
### #gradle
- Open [**Android SDK Kotlin Room and Gradle wrapper settings**](gradle-and-modules/map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md) — SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have explicit owners.
- Open [**Build conventions resources and Android API compatibility**](gradle-and-modules/map-build-conventions-resources-and-android-api-compatibility.md) — Convention plugins configure modules; Animal Sniffer checks pure JVM code against Android 12 APIs.
- Open [**Pass class and package lists through runner filter files**](../../testing/instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md) — The script preserves comma-separated test selection despite AGP argument splitting.
### #kotlin
- Open [**Fix detekt findings in code, and baseline only a fix that changes behaviour**](practice-fix-detekt-findings-in-code.md) — check runs detekt 2 with ktlint's intellij_idea rules and the Compose rules on every module. Fix findings in code; a baseline entry needs a behaviour change as its reason.
- Open [**Run JVM coroutine tests in virtual time, and never sleep**](../../testing/practice-run-coroutine-tests-in-virtual-time.md) — JVM tests use runTest and MainDispatcherRule from core:platform's test fixtures. Time passes by advancing the test scheduler, not by Thread.sleep; real waits stay only for a real server or thread.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.