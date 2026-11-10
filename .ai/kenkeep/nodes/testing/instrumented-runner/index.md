# kenkeep Index: testing / instrumented-runner

↑ Parent: [testing](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Use the instrumented runner and its device selection**](practice-use-the-instrumented-runner-and-its-device-selection.md) to learn about: The maintained script supplies required server arguments and owns selected emulator lifetimes. #testing #android #emulator #docker
- Open [**Use the pinned test server and TLS proxies**](practice-use-the-pinned-test-server-and-tls-proxies.md) to learn about: Disposable Tokkl, prefixed HTTP and independent TLS keys provide real endpoint and trust fixtures. #testing #android #docker #api
- Open [**Pass class and package lists through runner filter files**](practice-pass-class-and-package-lists-through-runner-filter-files.md) to learn about: The script preserves comma-separated test selection despite AGP argument splitting. #testing #android #gradle
- Open [**Keep instrumented credentials out of process arguments**](practice-keep-instrumented-credentials-out-of-process-arguments.md) to learn about: Private device credential files and durable cleanup prevent passwords and tokens leaking through ps. #testing #android #security
- Open [**Verify device test results and process isolation receipts**](practice-verify-device-test-results-and-process-isolation-receipts.md) to learn about: Device liveness, nonempty JUnit XML and orchestrated processes matter beyond Gradle success. #testing #android #emulator
- Open [**Run native permission probes alone with a fresh notification permission**](practice-run-native-permission-probes-alone-with-a-fresh-permission.md) to learn about: TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION=1 resets POST_NOTIFICATIONS before one probe method starts, on an API 33+ emulator the script boots. #testing #android #emulator #notification

## Components (what exists)
_None yet._

## By topic

### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #testing
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #emulator
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #docker
- Open [**Use the instrumented runner and its device selection**](practice-use-the-instrumented-runner-and-its-device-selection.md) — The maintained script supplies required server arguments and owns selected emulator lifetimes.
- Open [**Use the pinned test server and TLS proxies**](practice-use-the-pinned-test-server-and-tls-proxies.md) — Disposable Tokkl, prefixed HTTP and independent TLS keys provide real endpoint and trust fixtures.
### #api
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Typed API resources and request contracts**](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #gradle
- Open [**Android SDK Kotlin Room and Gradle wrapper settings**](../../build-and-tooling/android-build/gradle-and-modules/map-android-sdk-kotlin-room-and-gradle-wrapper-settings.md) — SDK levels, catalog Kotlin, generated Room schemas and wrapper checksums have explicit owners.
- Open [**Build conventions resources and Android API compatibility**](../../build-and-tooling/android-build/gradle-and-modules/map-build-conventions-resources-and-android-api-compatibility.md) — Convention plugins configure modules; Animal Sniffer checks pure JVM code against Android 12 APIs.
- Open [**Pass class and package lists through runner filter files**](practice-pass-class-and-package-lists-through-runner-filter-files.md) — The script preserves comma-separated test selection despite AGP argument splitting.
### #notification
- Open [**Running timer notification follows the cache's running entry**](../../data-and-offline/map-running-timer-notification-follows-the-cache.md) — notification/RunningNotificationSync posts, replaces and removes the ongoing notification from the cache and the held timer state. Stop works offline, Pause needs the server.
- Open [**Run native permission probes alone with a fresh notification permission**](practice-run-native-permission-probes-alone-with-a-fresh-permission.md) — TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION=1 resets POST_NOTIFICATIONS before one probe method starts, on an API 33+ emulator the script boots.
### #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](../../api-and-security/practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**Talk only to the configured server and use only free software**](../../api-and-security/practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Sign releases and verify optimized APKs**](../../release/reproducible-builds/practice-sign-releases-and-verify-optimized-apks.md) — Keep the project key outside the repository and exercise the optimized signed artifact, including upgrades.