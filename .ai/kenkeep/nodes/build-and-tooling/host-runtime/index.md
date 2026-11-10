# kenkeep Index: build-and-tooling / host-runtime

↑ Parent: [build-and-tooling](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) to learn about: ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree. #build #gradle #agents #worktrees
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) to learn about: sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out. #privacy #tooling #android
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) to learn about: Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes. #sift #agents #memory

## Components (what exists)
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) to learn about: Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin. #android #tooling #agents #build
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) to learn about: Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000. #agents #machine #memory #emulator
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) to learn about: t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session. #agents #tooling #machine

## By topic

### #agents
- Open [**Reuse drain checks by content and test configuration**](../../testing/practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #tooling
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #build
- Open [**Android SDK Kotlin Room and Gradle wrapper settings**](../android-build/gradle-and-modules/map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md) — SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have explicit owners.
- Open [**Build conventions resources and Android API compatibility**](../android-build/gradle-and-modules/map-build-conventions-resources-and-android-api-compatibility.md) — Convention plugins configure modules; Animal Sniffer checks pure JVM code against Android 12 APIs.
- Open [**Fix detekt findings in code, and baseline only a fix that changes behaviour**](../android-build/practice-fix-detekt-findings-in-code.md) — check runs detekt 2 with ktlint's intellij_idea rules and the Compose rules on every module. Fix findings in code; a baseline entry needs a behaviour change as its reason.
### #machine
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #memory
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**Respect emulator slots and resource ownership**](../../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md) — Scopes, global locks, slot locks and read-only AVDs isolate concurrent runtime work.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
### #emulator
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #gradle
- Open [**Android SDK Kotlin Room and Gradle wrapper settings**](../android-build/gradle-and-modules/map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md) — SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have explicit owners.
- Open [**Build conventions resources and Android API compatibility**](../android-build/gradle-and-modules/map-build-conventions-resources-and-android-api-compatibility.md) — Convention plugins configure modules; Animal Sniffer checks pure JVM code against Android 12 APIs.
- Open [**Pass class and package lists through runner filter files**](../../testing/instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md) — The script preserves comma-separated test selection despite AGP argument splitting.
### #privacy
- Open [**Talk only to the configured server and use only free software**](../../api-and-security/practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #sift
- Open [**Reuse drain checks by content and test configuration**](../../testing/practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
### #worktrees
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.