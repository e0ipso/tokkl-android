# kenkeep Index: testing / instrumented-runner

↑ Parent: [testing](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Use the instrumented runner and its device selection**](practice-use-the-instrumented-runner-and-its-device-selection.md) to learn about: The maintained script supplies required server arguments and owns selected emulator lifetimes. #testing #android #emulator #docker
- Open [**Use the pinned test server and TLS proxies**](practice-use-the-pinned-test-server-and-tls-proxies.md) to learn about: Disposable Tokkl, prefixed HTTP and independent TLS keys provide real endpoint and trust fixtures. #testing #android #docker #api
- Open [**Keep instrumented credentials out of process arguments**](practice-keep-instrumented-credentials-out-of-process-arguments.md) to learn about: Private device credential files and durable cleanup prevent passwords and tokens leaking through ps. #testing #android #security
- Open [**Pass class and package lists through runner filter files**](practice-pass-class-and-package-lists-through-runner-filter-files.md) to learn about: The script preserves comma-separated test selection despite AGP argument splitting. #testing #android #gradle
- Open [**Verify device test results and process isolation receipts**](practice-verify-device-test-results-and-process-isolation-receipts.md) to learn about: Device liveness, nonempty JUnit XML and orchestrated processes matter beyond Gradle success. #testing #android #emulator

## Components (what exists)
_None yet._

## By topic

### #android
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](../emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #testing
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](../emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #docker
- Open [**Use the instrumented runner and its device selection**](practice-use-the-instrumented-runner-and-its-device-selection.md) — The maintained script supplies required server arguments and owns selected emulator lifetimes.
- Open [**Use the pinned test server and TLS proxies**](practice-use-the-pinned-test-server-and-tls-proxies.md) — Disposable Tokkl, prefixed HTTP and independent TLS keys provide real endpoint and trust fixtures.
### #emulator
- Open [**Let the runner own emulator boot and settings**](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](../emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](../emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #api
- Open [**Trash capability and server operations**](../../screens/session-and-navigation/map-trash-capability-and-server-operations.md) — Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint handling.
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Taps change state on the phone at once and queue the change. The server is the source of truth, with a cache and queue, no sync engine.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #gradle
- Open [**Gradle build settings: SDK levels, Kotlin version, memory and build cache**](../../build-and-tooling/map-gradle-build-sdk-levels-kotlin-and-memory.md) — minSdk 31, compileSdk from the dependencies, targetSdk from the emulator, Kotlin from the catalog, a small heap and the local build cache.
- Open [**Pass class and package lists through runner filter files**](practice-pass-class-and-package-lists-through-runner-filter-files.md) — The script preserves comma-separated test selection despite AGP argument splitting.
- Open [**Build with --no-daemon in parallel worktrees and never run --stop**](../../build-and-tooling/practice-gradle-no-daemon-in-parallel-worktrees.md) — ./gradlew --stop ends every daemon of that Gradle version for the user, including one serving a build in another worktree.
### #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](../../api-and-security/practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**Talk only to the configured server and use only free software**](../../api-and-security/practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts**](../../api-and-security/map-connection-trust-store-pinning-and-prompts.md) — The shared client refuses unconfirmed http and unpinned self-signed certificates. SessionTrustModel prompts on every screen but sign-in.