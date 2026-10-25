# kenkeep Index: testing

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`emulator-runtime/`](emulator-runtime/index.md) for more information on AOSP emulator images, ownership, rendering and capture; read before preparing a device run or investigating emulator resource use.
- Load [`instrumented-runner/`](instrumented-runner/index.md) for more information on instrumented server setup, credentials, selectors and run receipts; read before invoking or modifying the device test script.
- Load [`instrumented-test-authoring/`](instrumented-test-authoring/index.md) for more information on portable device tests, shared helpers and UI assertions; read before adding or changing Android regression and acceptance tests.

## Conventions (how we build)
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) to learn about: Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple. #testing #android #emulator #ui
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) to learn about: Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one. #testing #ci #performance
- Open [**Reuse drain checks by content and test configuration**](practice-reuse-drain-checks-by-content-and-test-configuration.md) to learn about: Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged. #sift #testing #agents

## Components (what exists)
_None yet._

## By topic

### #testing
- Open [**Let the runner own emulator boot and settings**](emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #agents
- Open [**Reuse drain checks by content and test configuration**](practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Gate drain sittings on free memory, not on a sitting count**](../build-and-tooling/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start through the memory gate; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
- Open [**T3 restarts daily and ends running agent sessions**](../build-and-tooling/map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #android
- Open [**Let the runner own emulator boot and settings**](emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #ci
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one.
- Open [**Isolate all known failing tests before full-suite validation**](../practice-isolate-all-known-failing-tests-before-full-suite-validation.md) — Inventory every failure, reproduce it in a focused run, and iterate there before validating the full suite.
- Open [**Keep release builds reproducible and signed with the project key**](../release/practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #emulator
- Open [**Let the runner own emulator boot and settings**](emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #performance
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one.
### #sift
- Open [**Reuse drain checks by content and test configuration**](practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Gate drain sittings on free memory, not on a sitting count**](../build-and-tooling/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start through the memory gate; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
### #ui
- Open [**Timer page layout and scroll-away header**](../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Timer List and Calendar preference sync**](../screens/timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Tokkl favorites storage and capability**](../screens/timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.