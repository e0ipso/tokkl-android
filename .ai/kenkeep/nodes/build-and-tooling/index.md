# kenkeep Index: build-and-tooling

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) to learn about: ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree. #build #gradle #agents #worktrees
- Open [**Gate drain sittings on free memory, not on a sitting count**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) to learn about: Drain Gradle and device runs start through the memory gate; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes. #sift #agents #memory
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) to learn about: The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks. #git #commits #tooling
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) to learn about: sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out. #privacy #tooling #android

## Components (what exists)
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) to learn about: Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin. #android #tooling #agents #build
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) to learn about: Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000. #agents #machine #memory #emulator
- Open [**Android stack: shipped UI, cache and native timer surfaces**](map-planned-android-stack.md) to learn about: Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built. #architecture #android #offline
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) to learn about: t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session. #agents #tooling #machine
- Open [**Gradle build settings: SDK levels, Kotlin version, memory and build cache**](map-gradle-build-sdk-levels-kotlin-and-memory.md) to learn about: minSdk 31, compileSdk from the dependencies, targetSdk from the emulator, Kotlin from the catalog, a small heap and the local build cache. #android #build #gradle

## By topic

### #agents
- Open [**Device verification runtime: maintained code and explicit adoption**](../testing/map-device-verification-runtime-maintained-code-and-explicit-adoption.md) — scripts/device_verification maintains the controller; execution requires a frozen matching configuration, adoption and lease.
- Open [**Reuse drain checks by content and test configuration**](../testing/practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #android
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Android stack: shipped UI, cache and native timer surfaces**](map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
### #tooling
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**Device verification runtime: maintained code and explicit adoption**](../testing/map-device-verification-runtime-maintained-code-and-explicit-adoption.md) — scripts/device_verification maintains the controller; execution requires a frozen matching configuration, adoption and lease.
### #build
- Open [**Gradle build settings: SDK levels, Kotlin version, memory and build cache**](map-gradle-build-sdk-levels-kotlin-and-memory.md) — minSdk 31, compileSdk from the dependencies, targetSdk from the emulator, Kotlin from the catalog, a small heap and the local build cache.
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
### #gradle
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
- Open [**Gradle build settings: SDK levels, Kotlin version, memory and build cache**](map-gradle-build-sdk-levels-kotlin-and-memory.md) — minSdk 31, compileSdk from the dependencies, targetSdk from the emulator, Kotlin from the catalog, a small heap and the local build cache.
### #machine
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #memory
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**Gate drain sittings on free memory, not on a sitting count**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start through the memory gate; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
- Open [**Test on plain AOSP emulators, headless, and stop them afterwards**](../testing/practice-emulator-plain-aosp-image-headless.md) — tokkl-api36 and tokkl-api31 use plain AOSP images, since ATD drops SystemUI. Only the script boots them, headless and capped.
### #architecture
- Open [**Android stack: shipped UI, cache and native timer surfaces**](map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
### #commits
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #emulator
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](../testing/practice-run-instrumented-tests-only-through-the-script.md) — The script pins Tokkl 1.8.1, seeds accounts, configures TLS and test filters, and rejects empty device runs.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
### #git
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #offline
- Open [**RunningCheck: guarded background current-entry refresh**](../data-and-offline/map-runningcheck-guarded-background-current-entry-refresh.md) — One connected 15-minute worker follows cached running state and protects unsent edits, newer writes and sign-out.
- Open [**Android stack: shipped UI, cache and native timer surfaces**](map-planned-android-stack.md) — Compose, Room, WorkManager, OkHttp, Glance, the Quick Settings tile and favorite shortcuts are built.
- Open [**Running timer notification follows the cache's running entry**](../data-and-offline/map-running-timer-notification-follows-the-cache.md) — notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache, with no foreground service.
### #privacy
- Open [**Talk only to the configured server and use only free software**](../api-and-security/practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #sift
- Open [**Reuse drain checks by content and test configuration**](../testing/practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Gate drain sittings on free memory, not on a sitting count**](practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start through the memory gate; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
### #worktrees
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.