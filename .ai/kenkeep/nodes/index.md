---
okf_version: '0.1'
---
# kenkeep Index

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`api-and-security/`](api-and-security/index.md) for more information on the Tokkl and Toggl APIs, the shared OkHttp client, certificate trust, token storage and request rules; read before adding a request or endpoint, or changing trust, credentials or privacy.
- Load [`build-and-tooling/`](build-and-tooling/index.md) for more information on the JDK, Android SDK, Gradle build, Android stack, commit hook and agent host limits on this machine; read before building, committing, adding a dependency or running a long job.
- Load [`data-and-offline/`](data-and-offline/index.md) for more information on account settings and time, the Room cache, the change queue and the TypeScript ports; read before changing what the phone stores or sends, or how it reads and writes times.
- Load [`design/`](design/index.md) for more information on Tokkl's theme, palette, brand rules and shared UI components; read before changing how anything looks or reads to TalkBack.
- Load [`release/`](release/index.md) for more information on the app ID, versioning, reproducible signed builds, CI jobs and F-Droid metadata; read before touching versions, signing, CI or store text.
- Load [`screens/`](screens/index.md) for more information on sign-in, the navigation host and bottom bar, the Timer page, Reports and the catalog pages; read before changing a screen, its model or navigation.
- Load [`testing/`](testing/index.md) for more information on instrumented, screenshot and real-server tests on the plain AOSP emulators; read before writing or running any test on a device or emulator.

## Conventions (how we build)
- Open [**Reuse drain checks by content and test configuration**](practice-reuse-drain-checks-by-content-and-test-configuration.md) to learn about: Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged. #sift #testing #agents

## Components (what exists)
_None yet._

## By topic

### #agents
- Open [**T3 restarts daily and ends running agent sessions**](build-and-tooling/map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](build-and-tooling/map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**An OOM kill inside an agent session's scope ends the whole session**](build-and-tooling/map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop, so one OOM-killed process stops the session and its background agents.
### #sift
- Open [**Reuse drain checks by content and test configuration**](practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
### #testing
- Open [**Write instrumented tests that hold from API 26 to 36, with the shared helpers**](testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse the relays, AirplaneMode, BackgroundTrip, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](testing/practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container, seeds an account, passes test filters as a file and splits runs below Android 12 into checked batches.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
