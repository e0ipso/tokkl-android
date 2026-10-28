# kenkeep Index: build-and-tooling

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) to learn about: ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree. #build #gradle #agents #worktrees
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) to learn about: The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks. #git #commits #tooling
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) to learn about: sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out. #privacy #tooling #android
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) to learn about: Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes. #sift #agents #memory

## Components (what exists)
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) to learn about: Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000. #agents #machine #memory #emulator
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) to learn about: Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin. #android #tooling #agents #build
- Open [**Android stack: shipped UI, cache and native timer surfaces**](map-planned-android-stack.md) to learn about: Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built. #architecture #android #offline
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) to learn about: t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session. #agents #tooling #machine
- Open [**Gradle build settings: SDK levels, Kotlin version, memory and build cache**](map-gradle-build-sdk-levels-kotlin-and-memory.md) to learn about: minSdk 31, compileSdk from the dependencies, targetSdk from the emulator, Kotlin from the catalog, a small heap and the local build cache. #android #build #gradle

## By topic

### #agents
- Open [**Reuse drain checks by content and test configuration**](../testing/practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #android
- Open [**Let the runner own emulator boot and settings**](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #tooling
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #build
- Open [**Gradle build settings: SDK levels, Kotlin version, memory and build cache**](map-gradle-build-sdk-levels-kotlin-and-memory.md) — minSdk 31, compileSdk from the dependencies, targetSdk from the emulator, Kotlin from the catalog, a small heap and the local build cache.
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
### #gradle
- Open [**Gradle build settings: SDK levels, Kotlin version, memory and build cache**](map-gradle-build-sdk-levels-kotlin-and-memory.md) — minSdk 31, compileSdk from the dependencies, targetSdk from the emulator, Kotlin from the catalog, a small heap and the local build cache.
- Open [**Pass class and package lists through runner filter files**](../testing/instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md) — The script preserves comma-separated test selection despite AGP argument splitting.
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
### #machine
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #memory
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**Respect emulator slots and resource ownership**](../testing/emulator-runtime/practice-respect-emulator-slots-and-resource-ownership.md) — Scopes, global locks, slot locks and read-only AVDs isolate concurrent runtime work.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
### #architecture
- Open [**Android stack: shipped UI, cache and native timer surfaces**](map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
### #commits
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #emulator
- Open [**Let the runner own emulator boot and settings**](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #git
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #offline
- Open [**Background queue work and empty-queue cancellation**](../data-and-offline/change-queue/map-background-queue-work-and-empty-queue-cancellation.md) — Connected WorkManager delivery and serialized cancellation when the queue empties.
- Open [**Ordered entry sending and refusals**](../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
- Open [**Remote running conflicts and lost create responses**](../data-and-offline/change-queue/map-remote-running-conflicts-and-lost-create-responses.md) — Captured start conflict handling and duplicate prevention after uncertain POST delivery.
### #privacy
- Open [**Talk only to the configured server and use only free software**](../api-and-security/practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #sift
- Open [**Reuse drain checks by content and test configuration**](../testing/practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
### #worktrees
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.