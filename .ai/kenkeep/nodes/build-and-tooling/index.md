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

## Components (what exists)
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) to learn about: Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin. #android #tooling #agents #build
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) to learn about: Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents. #agents #machine #memory #emulator
- Open [**Android stack: Compose, Room and OkHttp built; Glance, tile and WorkManager next**](map-planned-android-stack.md) to learn about: Built: Compose Material 3, the Room cache and queue, OkHttp with pinning, the time ports. Not yet: Glance, the tile, WorkManager. #architecture #android #offline
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) to learn about: t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session. #agents #tooling #machine
- Open [**Gradle build settings: SDK levels, Kotlin version source and memory**](map-gradle-build-sdk-levels-kotlin-and-memory.md) to learn about: compileSdk follows the dependencies, targetSdk matches the emulator, the catalog sets Kotlin, and the Gradle heap stays small. #android #build #gradle

## By topic

### #agents
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents.
### #android
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Write instrumented tests that hold from API 26 to 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse the relays, AirplaneMode, BackgroundTrip, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](../testing/practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container, seeds an account, passes test filters as a file and splits runs below Android 12 into checked batches.
### #tooling
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #build
- Open [**Gradle build settings: SDK levels, Kotlin version source and memory**](map-gradle-build-sdk-levels-kotlin-and-memory.md) — compileSdk follows the dependencies, targetSdk matches the emulator, the catalog sets Kotlin, and the Gradle heap stays small.
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
### #gradle
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
- Open [**Gradle build settings: SDK levels, Kotlin version source and memory**](map-gradle-build-sdk-levels-kotlin-and-memory.md) — compileSdk follows the dependencies, targetSdk matches the emulator, the catalog sets Kotlin, and the Gradle heap stays small.
### #machine
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents.
- Open [**T3 restarts daily and ends running agent sessions**](map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #architecture
- Open [**Android stack: Compose, Room and OkHttp built; Glance, tile and WorkManager next**](map-planned-android-stack.md) — Built: Compose Material 3, the Room cache and queue, OkHttp with pinning, the time ports. Not yet: Glance, the tile, WorkManager.
### #commits
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #emulator
- Open [**Write instrumented tests that hold from API 26 to 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse the relays, AirplaneMode, BackgroundTrip, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](../testing/practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container, seeds an account, passes test filters as a file and splits runs below Android 12 into checked batches.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
### #git
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #memory
- Open [**Test on plain AOSP emulators, headless, and stop them afterwards**](../testing/practice-emulator-plain-aosp-image-headless.md) — tokkl-api36, -api30 and -api26 use plain AOSP images, since ATD drops SystemUI. Only the script boots them, headless and capped.
- Open [**An OOM kill inside an agent session's scope ends the whole session**](map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents.
### #offline
- Open [**Offline cache and capabilities: AccountCache, HistoryWindow, CapabilitiesSource**](../data-and-offline/map-account-cache-and-capabilities.md) — One refresh reads /me with related data and /me/preferences, then fills the Room cache and the capability flags that screens read offline.
- Open [**Change queue: ChangeQueue, ChangeSender, PendingChange and the offline picker**](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md) — Entry changes land in the cache and a Room queue at once. ChangeSender sends them oldest first, stop as a full PUT, and refreshes after.
- Open [**Never make Start or Stop wait for the server**](../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
### #privacy
- Open [**Talk only to the configured server and use only free software**](../api-and-security/practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.
### #worktrees
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.