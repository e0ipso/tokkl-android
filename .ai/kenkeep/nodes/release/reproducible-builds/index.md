# kenkeep Index: release / reproducible-builds

↑ Parent: [release](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Compare clean unsigned release builds**](practice-compare-clean-unsigned-release-builds.md) to learn about: Build the same committed tree in isolated clones and compare APK bytes, paths, timestamps and core-count effects. #release #fdroid #ci
- Open [**Pin CI actions and run the Android matrix**](practice-pin-ci-actions-and-run-the-android-matrix.md) to learn about: CI checks debug builds, reproducibility, commit messages and six instrumented shards on each supported test API. #release #ci #android
- Open [**Sign releases and verify optimized APKs**](practice-sign-releases-and-verify-optimized-apks.md) to learn about: Keep the project key outside the repository and exercise the optimized signed artifact, including upgrades. #release #fdroid #security

## Components (what exists)
_None yet._

## By topic

### #release
- Open [**Compare clean unsigned release builds**](practice-compare-clean-unsigned-release-builds.md) — Build the same committed tree in isolated clones and compare APK bytes, paths, timestamps and core-count effects.
- Open [**F-Droid metadata layout and release artifacts**](../map-fdroid-metadata-release-artifacts.md) — Store text lives in fastlane/metadata/android/en-US/, the fdroiddata recipe in fdroid/metadata/com.canpicasoft.tokkl.yml. Check it in Docker before every new build.
- Open [**Release signing key, Actions secrets and signature checks**](../map-release-signing-key-actions-secrets-and-signature-checks.md) — The owner's PKCS12 key signs releases through four Actions secrets. Fingerprint and apksigcopier commands check what F-Droid checks.
### #ci
- Open [**Compare clean unsigned release builds**](practice-compare-clean-unsigned-release-builds.md) — Build the same committed tree in isolated clones and compare APK bytes, paths, timestamps and core-count effects.
- Open [**Release signing key, Actions secrets and signature checks**](../map-release-signing-key-actions-secrets-and-signature-checks.md) — The owner's PKCS12 key signs releases through four Actions secrets. Fingerprint and apksigcopier commands check what F-Droid checks.
- Open [**Test against a real Tokkl server**](../../testing/practice-test-against-a-real-tokkl-server.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake.
### #fdroid
- Open [**Sign releases and verify optimized APKs**](practice-sign-releases-and-verify-optimized-apks.md) — Keep the project key outside the repository and exercise the optimized signed artifact, including upgrades.
- Open [**Release signing key, Actions secrets and signature checks**](../map-release-signing-key-actions-secrets-and-signature-checks.md) — The owner's PKCS12 key signs releases through four Actions secrets. Fingerprint and apksigcopier commands check what F-Droid checks.
- Open [**F-Droid metadata layout and release artifacts**](../map-fdroid-metadata-release-artifacts.md) — Store text lives in fastlane/metadata/android/en-US/, the fdroiddata recipe in fdroid/metadata/com.canpicasoft.tokkl.yml. Check it in Docker before every new build.
### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](../../api-and-security/practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**Talk only to the configured server and use only free software**](../../api-and-security/practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Sign releases and verify optimized APKs**](practice-sign-releases-and-verify-optimized-apks.md) — Keep the project key outside the repository and exercise the optimized signed artifact, including upgrades.