---
schema_version: 3
nodes_hash: 'sha256:7b86b7da78776b320d7bd34e2cc8581e3cd5ae287f4b98007183af6dccbe08b9'
node_count: 19
---
# kenkeep Graph

Total nodes: 19

## map-app-id-license-android-versions

- **kind:** map
- **title:** Application ID, license and supported Android versions
- **path:** map-app-id-license-android-versions.md
- **tags:** release, fdroid, android
- **derived_from:** docs/PRD.md

## map-fdroid-metadata-release-artifacts

- **kind:** map
- **title:** F-Droid metadata layout and release artifacts
- **path:** map-fdroid-metadata-release-artifacts.md
- **tags:** fdroid, release
- **derived_from:** docs/PRD.md

## map-jdk-android-sdk-and-agent-shells

- **kind:** map
- **title:** JDK and Android SDK on this machine, and how agent shells reach them
- **path:** map-jdk-android-sdk-and-agent-shells.md
- **tags:** android, tooling, agents, build

## map-planned-android-stack

- **kind:** map
- **title:** Planned stack: Compose, Glance, TileService, Room, WorkManager, OkHttp
- **path:** map-planned-android-stack.md
- **tags:** architecture, android, offline
- **derived_from:** docs/PRD.md

## map-tokkl-colour-tokens-and-font

- **kind:** map
- **title:** Tokkl colour tokens and Anybody font in ../tokkl
- **path:** map-tokkl-colour-tokens-and-font.md
- **tags:** ui, tokkl
- **derived_from:** docs/PRD.md

## map-tokkl-toggl-api

- **kind:** map
- **title:** Tokkl and Toggl API endpoints, auth and Tokkl-only extensions
- **path:** map-tokkl-toggl-api.md
- **tags:** api, toggl, network
- **derived_from:** docs/PRD.md

## practice-check-capabilities-not-server-version

- **kind:** practice
- **title:** Check server capabilities instead of requiring a server version
- **path:** practice-check-capabilities-not-server-version.md
- **tags:** api, toggl, compatibility
- **derived_from:** docs/PRD.md

## practice-emulator-plain-aosp-image-headless

- **kind:** practice
- **title:** Test on the plain AOSP API 36 emulator, headless, and stop it afterwards
- **path:** practice-emulator-plain-aosp-image-headless.md
- **tags:** testing, android, emulator, energy
- **relates_to:** practice-test-real-tokkl-server-speed-regressions, map-jdk-android-sdk-and-agent-shells

## practice-match-web-app-behavior-account-timezone

- **kind:** practice
- **title:** Match Tokkl web app behavior in the account timezone
- **path:** practice-match-web-app-behavior-account-timezone.md
- **tags:** api, ui, time
- **derived_from:** docs/PRD.md

## practice-never-wait-for-server-on-start-stop

- **kind:** practice
- **title:** Never make Start or Stop wait for the server
- **path:** practice-never-wait-for-server-on-start-stop.md
- **tags:** offline, network, api
- **derived_from:** docs/PRD.md

## practice-opt-sdk-tools-out-of-usage-metrics

- **kind:** practice
- **title:** Opt the Android SDK tools out of usage metrics with ~/.androidrc
- **path:** practice-opt-sdk-tools-out-of-usage-metrics.md
- **tags:** privacy, tooling, android
- **relates_to:** practice-talk-only-to-configured-server, map-jdk-android-sdk-and-agent-shells

## practice-own-version-from-v0-1-0-tag

- **kind:** practice
- **title:** Version the app on its own, starting from a hand-made v0.1.0 tag
- **path:** practice-own-version-from-v0-1-0-tag.md
- **tags:** release, versioning
- **derived_from:** docs/PRD.md

## practice-pin-self-signed-certs-warn-on-http

- **kind:** practice
- **title:** Pin self-signed certificates by fingerprint and warn before plain HTTP
- **path:** practice-pin-self-signed-certs-warn-on-http.md
- **tags:** security, network
- **derived_from:** docs/PRD.md

## practice-refresh-on-events-no-polling

- **kind:** practice
- **title:** Refresh on events, never on a short polling timer
- **path:** practice-refresh-on-events-no-polling.md
- **tags:** network, toggl, battery
- **derived_from:** docs/PRD.md

## practice-reproducible-builds-project-key

- **kind:** practice
- **title:** Keep release builds reproducible and signed with the project key
- **path:** practice-reproducible-builds-project-key.md
- **tags:** release, fdroid, security, ci
- **derived_from:** docs/PRD.md

## practice-run-npm-install-in-every-clone-and-worktree

- **kind:** practice
- **title:** Run npm install in every new clone or worktree to turn on the commit hook
- **path:** practice-run-npm-install-in-every-clone-and-worktree.md
- **tags:** git, commits, tooling

## practice-talk-only-to-configured-server

- **kind:** practice
- **title:** Talk only to the configured server and use only free software
- **path:** practice-talk-only-to-configured-server.md
- **tags:** privacy, network, fdroid, security
- **derived_from:** docs/PRD.md

## practice-test-real-tokkl-server-speed-regressions

- **kind:** practice
- **title:** Test against a real Tokkl server and catch speed regressions in CI
- **path:** practice-test-real-tokkl-server-speed-regressions.md
- **tags:** testing, ci, performance
- **derived_from:** docs/PRD.md

## practice-tokkl-palette-wcag-english-strings

- **kind:** practice
- **title:** Use Tokkl's palette, WCAG AA contrast and English string resources
- **path:** practice-tokkl-palette-wcag-english-strings.md
- **tags:** ui, accessibility, i18n
- **derived_from:** docs/PRD.md
