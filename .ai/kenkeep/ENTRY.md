---
schema_version: 3
nodes_hash: 'sha256:11c7bda24c34eb9a602360d2bd0ce2453ad1db7cc0f6c6877cc225d740286d80'
node_count: 22
---
# kenkeep

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

## Branches
_None._

## Conventions (how we build)
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](nodes/practice-run-npm-install-in-every-clone-and-worktree.md) to learn about: The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks. #git #commits #tooling
- Open [**Talk only to the configured server and use only free software**](nodes/practice-talk-only-to-configured-server.md) to learn about: No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server. #privacy #network #fdroid #security
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](nodes/practice-test-real-tokkl-server-speed-regressions.md) to learn about: Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one. #testing #ci #performance
- Open [**Test on the plain AOSP API 36 emulator, headless, and stop it afterwards**](nodes/practice-emulator-plain-aosp-image-headless.md) to learn about: AVD tokkl-api36 uses the AOSP image, since ATD images drop SystemUI and the launcher. Boot it with -no-window -accel on and kill it after. #testing #android #emulator #energy
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](nodes/practice-gradle-no-daemon-in-parallel-worktrees.md) to learn about: ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree. #build #gradle #agents #worktrees
- Open [**Check server capabilities instead of requiring a server version**](nodes/practice-check-capabilities-not-server-version.md) to learn about: No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only endpoint hides the features that depend on it. #api #toggl #compatibility
- Open [**Keep release builds reproducible and signed with the project key**](nodes/practice-reproducible-builds-project-key.md) to learn about: Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA. #release #fdroid #security #ci
- Open [**Match Tokkl web app behavior in the account timezone**](nodes/practice-match-web-app-behavior-account-timezone.md) to learn about: Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me. #api #ui #time
- Open [**Never make Start or Stop wait for the server**](nodes/practice-never-wait-for-server-on-start-stop.md) to learn about: Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine. #offline #network #api
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](nodes/practice-opt-sdk-tools-out-of-usage-metrics.md) to learn about: sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out. #privacy #tooling #android
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](nodes/practice-pin-self-signed-certs-warn-on-http.md) to learn about: Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning. #security #network
- Open [**Refresh on events, never on a short polling timer**](nodes/practice-refresh-on-events-no-polling.md) to learn about: Refresh when the app opens, after a change and when the network returns. No short polling, so Toggl's base URL later fits its quotas. #network #toggl #battery
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](nodes/practice-tokkl-palette-wcag-english-strings.md) to learn about: Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources. #ui #accessibility #i18n
- Open [**Version the app on its own, starting from a hand-made v0.1.0 tag**](nodes/practice-own-version-from-v0-1-0-tag.md) to learn about: The app has its own v* tags and changelog, independent of Tokkl. The first release is a hand-made v0.1.0 tag; semantic-release counts on. #release #versioning

## Components (what exists)
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](nodes/map-jdk-android-sdk-and-agent-shells.md) to learn about: Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin. #android #tooling #agents #build
- Open [**Planned stack: Compose, Glance, TileService, Room, WorkManager, OkHttp**](nodes/map-planned-android-stack.md) to learn about: Proposed stack: Kotlin and Compose Material 3, Glance widget, TileService tile, Room cache and queue, WorkManager, OkHttp. #architecture #android #offline
- Open [**Application ID, license and supported Android versions**](nodes/map-app-id-license-android-versions.md) to learn about: Application ID com.github.e0ipso.tokkl, MIT license, Android 8.0 (API 26) and later, one server and one account at a time. #release #fdroid #android
- Open [**F-Droid metadata layout and release artifacts**](nodes/map-fdroid-metadata-release-artifacts.md) to learn about: Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries. #fdroid #release
- Open [**Gradle build settings: SDK levels, Kotlin version source and memory**](nodes/map-gradle-build-sdk-levels-kotlin-and-memory.md) to learn about: compileSdk follows the dependencies, targetSdk matches the emulator, the catalog sets Kotlin, and the Gradle heap stays small. #android #build #gradle
- Open [**T3 restarts daily and ends running agent sessions**](nodes/map-t3-daily-restart-stops-agent-sessions.md) to learn about: t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session. #agents #tooling #machine
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](nodes/map-tokkl-toggl-api.md) to learn about: Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints. #api #toggl #network
- Open [**Tokkl colour tokens and Anybody font in ../tokkl**](nodes/map-tokkl-colour-tokens-and-font.md) to learn about: The colour tokens in ../tokkl/packages/web/src/styles.css are the palette's source of truth. Tokkl ships Anybody under the SIL OFL 1.1. #ui #tokkl
