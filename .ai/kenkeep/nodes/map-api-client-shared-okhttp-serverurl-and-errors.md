---
type: map
title: 'API client: one OkHttpClient, ServerUrl, TogglApiClient and ApiError'
description: >-
  The api package holds the app's only OkHttpClient, base URL handling, JSON
  calls, sendNoContent for empty bodies and typed errors.
tags:
  - api
  - network
  - okhttp
kk_schema_version: 3
kk_id: map-api-client-shared-okhttp-serverurl-and-errors
kk_derived_from: []
kk_relates_to:
  - map-tokkl-toggl-api
  - practice-talk-only-to-configured-server
  - practice-pin-self-signed-certs-warn-on-http
  - map-connection-trust-store-pinning-and-prompts
kk_depends_on: []
kk_confidence: high
---
The HTTP code lives in `app/src/main/java/com/github/e0ipso/tokkl/api/`. `SharedHttpClient.instance` is the app's only `OkHttpClient`, so its connection pool, dispatcher and security settings apply to every request. New security settings, such as the trust manager for a pinned self-signed certificate, go into its `build()`, never into a second client. `SingleHttpClientTest` reads the constant pool of every compiled app class through `ClassReferences`, the class-file reader in `app/src/test` that `NoDeviceZoneTest` shares, and fails when any class but `SharedHttpClient` builds an HTTP client, so an import alias or a fully qualified name cannot hide one.

`ConfiguredServerGuard`, an interceptor on that client, throws `HostNotAllowedException` for any request whose scheme, host or port differs from `ConfiguredServer.current`. It does so before OkHttp connects, so no credentials leave the phone, and `TogglApiClient` returns it as `ApiError.HostNotAllowed(origin)` with the origin as `scheme://host:port`. `current` is the stored server, or the URL being tried inside `ConfiguredServer.signingIn`. With neither, every request is refused. The guard also refuses a plain `http://` server the user has not confirmed, and the trust manager refuses a self-signed certificate the user has not pinned (see the connection trust node). OkHttp's own redirects are off (`followRedirects(false)`, `followSslRedirects(false)`). The guard follows a redirect itself, and only to the same origin.

`ServerUrl.parse` accepts an http or https base URL that may carry a path, such as `https://example.com/tokkl`, and builds endpoints under `<base>/api/v9` and `<base>/reports/api/v3`. It refuses a URL with a user name or password, so the stored URL never holds a secret. `ApiCredentials` builds the Basic header from `<api_token>:api_token` or `<email>:<password>`. Tokkl splits the decoded header at the first colon, so a password may contain colons and an email may not.

`TogglApiClient.get` and `send` decode a 2xx body with a kotlinx.serialization deserializer, and an empty body fails to decode. Calls that expect no content use `sendNoContent`, which treats any 2xx as success and ignores the body. Tokkl's DELETE routes for time entries, tasks, clients and tags answer 200 with an empty body, while the project DELETE answers 200 with the project id as JSON (`../tokkl/packages/api/src/routes/togglV9.ts`). `sendNoContent` handles both.

Every call returns `ApiResult.Success` or `ApiResult.Failure(ApiError)`. 403 maps to `AuthFailed`, 429 to `RateLimited` with the `Retry-After` seconds (null when the header is missing or not a whole number), and any other status outside 2xx to `UnexpectedStatus`. `Network` wraps the `IOException` of a request that got no response. `InvalidResponse` is a 2xx body that is not the expected JSON. `TrustRefused` covers `CleartextNotConfirmed` and `UntrustedCertificate`, which the client also reports to its `TrustListener`.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-toggl-api](map-tokkl-toggl-api.md)
- Related: [practice-talk-only-to-configured-server](practice-talk-only-to-configured-server.md)
- Related: [practice-pin-self-signed-certs-warn-on-http](practice-pin-self-signed-certs-warn-on-http.md)
- Related: [map-connection-trust-store-pinning-and-prompts](map-connection-trust-store-pinning-and-prompts.md)
<!-- kk:related:end -->
