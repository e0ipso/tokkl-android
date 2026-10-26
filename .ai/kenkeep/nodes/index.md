---
okf_version: '0.1'
---
# kenkeep Index

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
- Load [`api-and-security/`](api-and-security/index.md) for more information on the Tokkl and Toggl APIs, the shared OkHttp client, certificate trust, token storage and request rules; read before adding a request or endpoint, or changing trust, credentials or privacy.
- Load [`build-and-tooling/`](build-and-tooling/index.md) for more information on the JDK, Android SDK, Gradle build, Android stack, commit hook and agent host limits on this machine; read before building, committing, adding a dependency or running a long job.
- Load [`data-and-offline/`](data-and-offline/index.md) for more information on account settings and time, the Room cache, the change queue, the running timer's notification and the TypeScript ports; read before changing what the phone stores or sends, or how it reads and writes times.
- Load [`design/`](design/index.md) for more information on Tokkl's theme, palette, brand rules and shared UI components; read before changing how anything looks or reads to TalkBack.
- Load [`release/`](release/index.md) for more information on the app ID, versioning, reproducible signed builds, CI jobs and F-Droid metadata; read before touching versions, signing, CI or store text.
- Load [`screens/`](screens/index.md) for more information on sign-in and sign-out, the navigation host with its bar, rail and window sizes, the Timer page, the timer and entry form, Reports, Trash and the catalog pages; read before changing a screen, its model or navigation.
- Load [`testing/`](testing/index.md) for more information on instrumented, screenshot and real-server tests on the plain AOSP emulators; read before writing or running any test on a device or emulator.

## Conventions (how we build)
- Open [**Isolate all known failing tests before full-suite validation**](practice-isolate-all-known-failing-tests-before-full-suite-validation.md) to learn about: Inventory every failure, reproduce it in a focused run, and iterate there before validating the full suite. #testing #ci #agents

## Components (what exists)
_None yet._

## By topic

### #agents
- Open [**Reuse drain checks by content and test configuration**](testing/practice-reuse-drain-checks-by-content-and-test-configuration.md) — Commit IDs and cherry-picks do not invalidate checks when all checked inputs and the test environment are unchanged.
- Open [**Gate drain sittings on free memory, not on a sitting count**](build-and-tooling/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start through the memory gate; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
- Open [**T3 restarts daily and ends running agent sessions**](build-and-tooling/map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
### #ci
- Open [**Keep release builds reproducible and signed with the project key**](release/practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
- Open [**Release signing key, Actions secrets and signature checks**](release/map-release-signing-key-actions-secrets-and-signature-checks.md) — The owner's PKCS12 key signs releases through four Actions secrets. Fingerprint and apksigcopier commands check what F-Droid checks.
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](testing/practice-test-real-tokkl-server-speed-regressions.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one.
### #testing
- Open [**Let the runner own emulator boot and settings**](testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
