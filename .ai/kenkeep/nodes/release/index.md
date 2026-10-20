# kenkeep Index: release

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Version the app on its own, starting from a hand-made v0.1.0 tag**](practice-own-version-from-v0-1-0-tag.md) to learn about: The app has its own v* tags and changelog, independent of Tokkl. The first release is a hand-made v0.1.0 tag; semantic-release counts on. #release #versioning
- Open [**Keep release builds reproducible and signed with the project key**](practice-reproducible-builds-project-key.md) to learn about: Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA. #release #fdroid #security #ci

## Components (what exists)
- Open [**F-Droid metadata layout and release artifacts**](map-fdroid-metadata-release-artifacts.md) to learn about: Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries. #fdroid #release
- Open [**Application ID, license and supported Android versions**](map-app-id-license-android-versions.md) to learn about: Application ID com.github.e0ipso.tokkl, MIT license, Android 12 (API 31) and later, one server and one account at a time. #release #fdroid #android

## By topic

### #release
- Open [**F-Droid metadata layout and release artifacts**](map-fdroid-metadata-release-artifacts.md) — Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries.
- Open [**Application ID, license and supported Android versions**](map-app-id-license-android-versions.md) — Application ID com.github.e0ipso.tokkl, MIT license, Android 12 (API 31) and later, one server and one account at a time.
- Open [**Keep release builds reproducible and signed with the project key**](practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #fdroid
- Open [**F-Droid metadata layout and release artifacts**](map-fdroid-metadata-release-artifacts.md) — Store text and images live under fastlane/metadata/android/en-US/. F-Droid finds versions by git tag and the signed APK via Binaries.
- Open [**Application ID, license and supported Android versions**](map-app-id-license-android-versions.md) — Application ID com.github.e0ipso.tokkl, MIT license, Android 12 (API 31) and later, one server and one account at a time.
- Open [**Keep release builds reproducible and signed with the project key**](practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #android
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](../testing/practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container behind a TLS proxy, seeds an account, passes test filters as a file and fails a run Gradle passed without tests.
### #ci
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](../testing/practice-test-real-tokkl-server-speed-regressions.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one.
- Open [**Keep release builds reproducible and signed with the project key**](practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](../api-and-security/practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**Talk only to the configured server and use only free software**](../api-and-security/practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](../api-and-security/map-connection-trust-store-pinning-and-prompts.md) — The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in.
### #versioning
- Open [**Version the app on its own, starting from a hand-made v0.1.0 tag**](practice-own-version-from-v0-1-0-tag.md) — The app has its own v* tags and changelog, independent of Tokkl. The first release is a hand-made v0.1.0 tag; semantic-release counts on.