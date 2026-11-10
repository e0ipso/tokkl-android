# kenkeep Index: api-and-security / api-client

↑ Parent: [api-and-security](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**Typed API resources and request contracts**](map-typed-api-resources-and-request-contracts.md) to learn about: TokklApi resource groups fix request shapes, typed answers and module boundaries. #api #network
- Open [**API execution retries and errors**](map-api-execution-retries-and-errors.md) to learn about: Cancellable reads, application-owned writes, send-once mutations and typed response failures. #api #network #coroutines
- Open [**API test fixtures**](map-api-test-fixtures.md) to learn about: TestApi supplies isolated client, transport and JSON helpers for JVM and device tests. #api #testing
- Open [**Shared HTTP client and configured server security**](map-shared-http-client-and-configured-server-security.md) to learn about: One lazy OkHttp client enforces server origin, trust, URL normalization and bounded idle connections. #api #network #okhttp #security

## By topic

### #api
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
- Open [**Typed API resources and request contracts**](map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Ordered entry sending and refusals**](../../data-and-offline/change-queue/map-ordered-entry-sending-and-refusals.md) — Oldest-first HTTP delivery, retry boundaries, held refusals and reauthentication.
### #network
- Open [**Typed API resources and request contracts**](map-typed-api-resources-and-request-contracts.md) — TokklApi resource groups fix request shapes, typed answers and module boundaries.
- Open [**Tokkl and Toggl API endpoints, auth and Tokkl-only extensions**](../map-tokkl-toggl-api.md) — Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
- Open [**Never make Start or Stop wait for the server**](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md) — Start and Stop change state on the phone at once and queue the change. Pause, Resume and session actions wait for the server. No sync engine.
### #coroutines
- Open [**API execution retries and errors**](map-api-execution-retries-and-errors.md) — Cancellable reads, application-owned writes, send-once mutations and typed response failures.
- Open [**Run JVM coroutine tests in virtual time, and never sleep**](../../testing/practice-run-coroutine-tests-in-virtual-time.md) — JVM tests use runTest and MainDispatcherRule from core:platform's test fixtures. Time passes by advancing the test scheduler, not by Thread.sleep; real waits stay only for a real server or thread.
### #okhttp
- Open [**Shared HTTP client and configured server security**](map-shared-http-client-and-configured-server-security.md) — One lazy OkHttp client enforces server origin, trust, URL normalization and bounded idle connections.
### #security
- Open [**Pin self-signed certificates by fingerprint and warn before plain HTTP**](../practice-pin-self-signed-certs-warn-on-http.md) — Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept any cert, and put plain HTTP behind a stronger warning.
- Open [**Talk only to the configured server and use only free software**](../practice-talk-only-to-configured-server.md) — No telemetry, analytics, crash reporting, ads or proprietary libraries. The app sends requests only to the configured server.
- Open [**Sign releases and verify optimized APKs**](../../release/reproducible-builds/practice-sign-releases-and-verify-optimized-apks.md) — Keep the project key outside the repository and exercise the optimized signed artifact, including upgrades.
### #testing
- Open [**Let the runner own emulator boot and settings**](../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](../../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.