# kenkeep Index: testing

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`emulator-runtime/`](emulator-runtime/index.md) for more information on AOSP emulator images, ownership, rendering and capture; read before preparing a device run or investigating emulator resource use.
- Load [`instrumented-runner/`](instrumented-runner/index.md) for more information on instrumented server setup, credentials, selectors and run receipts; read before invoking or modifying the device test script.
- Load [`instrumented-test-authoring/`](instrumented-test-authoring/index.md) for more information on portable device tests, shared helpers and UI assertions; read before adding or changing Android regression and acceptance tests.

## Conventions (how we build)
- Open [**Test against a real Tokkl server**](practice-test-against-a-real-tokkl-server.md) to learn about: Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. #testing #ci
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) to learn about: Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple. #testing #android #emulator #ui
- Open [**Reuse drain checks by content and test configuration**](practice-reuse-drain-checks-by-content-and-test-configuration.md) to learn about: Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged. #sift #testing #agents
- Open [**Keep tests that protect app quality**](practice-keep-tests-that-protect-app-quality.md) to learn about: Keep tests that can reveal app regressions; drop checks of upstream, language features, test helpers or trivia. #testing #quality
- Open [**Run JVM coroutine tests in virtual time, and never sleep**](practice-run-coroutine-tests-in-virtual-time.md) to learn about: JVM tests use runTest and MainDispatcherRule from core:platform's test fixtures. Time passes by advancing the test scheduler, not by Thread.sleep; real waits stay only for a real server or thread. #testing #kotlin #coroutines

## Components (what exists)
_None yet._

## By topic

### #testing
- Open [**Let the runner own emulator boot and settings**](emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #agents
- Open [**Reuse drain checks by content and test configuration**](practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](../build-and-tooling/host-runtime/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
- Open [**T3 restarts daily and ends running agent sessions**](../build-and-tooling/host-runtime/map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #ci
- Open [**Compare clean unsigned release builds**](../release/reproducible-builds/practice-compare-clean-unsigned-release-builds.md) — Build the same committed tree in isolated clones and compare APK bytes, paths, timestamps and core-count effects.
- Open [**Release signing key, Actions secrets and signature checks**](../release/map-release-signing-key-actions-secrets-and-signature-checks.md) — The owner's PKCS12 key signs releases through four Actions secrets. Fingerprint and apksigcopier commands check what F-Droid checks.
- Open [**Test against a real Tokkl server**](practice-test-against-a-real-tokkl-server.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake.
### #coroutines
- Open [**API execution retries and errors**](../api-and-security/api-client/map-api-execution-retries-and-errors.md) — Cancellable reads, application-owned writes, send-once mutations and typed response failures.
- Open [**Run JVM coroutine tests in virtual time, and never sleep**](practice-run-coroutine-tests-in-virtual-time.md) — JVM tests use runTest and MainDispatcherRule from core:platform's test fixtures. Time passes by advancing the test scheduler, not by Thread.sleep; real waits stay only for a real server or thread.
### #emulator
- Open [**Let the runner own emulator boot and settings**](emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #kotlin
- Open [**Fix detekt findings in code, and baseline only a fix that changes behaviour**](../build-and-tooling/android-build/practice-fix-detekt-findings-in-code.md) — check runs detekt 2 with ktlint's intellij_idea rules and the Compose rules on every module. Fix findings in code; a baseline entry needs a behaviour change as its reason.
- Open [**Run JVM coroutine tests in virtual time, and never sleep**](practice-run-coroutine-tests-in-virtual-time.md) — JVM tests use runTest and MainDispatcherRule from core:platform's test fixtures. Time passes by advancing the test scheduler, not by Thread.sleep; real waits stay only for a real server or thread.
### #quality
- Open [**Keep tests that protect app quality**](practice-keep-tests-that-protect-app-quality.md) — Keep tests that can reveal app regressions; drop checks of upstream, language features, test helpers or trivia.
### #sift
- Open [**Reuse drain checks by content and test configuration**](practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](../build-and-tooling/host-runtime/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
### #ui
- Open [**Timer page layout and scroll-away header**](../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Signed-in navigation chrome and adaptive windows**](../screens/session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](../screens/session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.