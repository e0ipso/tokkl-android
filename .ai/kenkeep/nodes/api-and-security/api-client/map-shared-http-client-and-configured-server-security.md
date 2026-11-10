---
type: map
title: Shared HTTP client and configured server security
description: >-
  One lazy OkHttp client enforces server origin, trust, URL normalization and
  bounded idle connections.
tags:
  - api
  - network
  - okhttp
  - security
kk_schema_version: 3
kk_id: map-shared-http-client-and-configured-server-security
kk_derived_from: []
kk_relates_to:
  - practice-talk-only-to-configured-server
  - practice-pin-self-signed-certs-warn-on-http
  - map-connection-trust-store-pinning-and-prompts
kk_depends_on: []
kk_confidence: high
---
`SharedHttpClient` is a class that Koin makes once, and its `instance` is the app's only `OkHttpClient`, so its connection pool, dispatcher and security settings apply to every request. It builds the client on first use, which loads the platform's CAs, so that happens off the main thread. `cancelAll()` cancels every call in flight for sign-out, and `dropIdleConnections()` empties the pool when MainActivity sees the network drop, on the main thread; it and `cancelAll()` do nothing before the client is built, so neither builds it. New security settings, such as the trust manager for a pinned self-signed certificate, go into its `build()`, never into a second client. `SingleHttpClientTest` reads the constant pool of every compiled app class, in every module, through `ClassReferences`, the class-file reader in `app/src/test` that `NoDeviceZoneTest` shares, and fails when any class but `SharedHttpClient` builds an HTTP client, so an import alias or a fully qualified name cannot hide one.

`ConfiguredServerGuard`, an interceptor on that client, throws `HostNotAllowedException` for any request whose scheme, host or port differs from the `ConfiguredServer`'s `current`. `ConfiguredServer` is a Koin single too. It reads the stored server through `StoredServer`, which `ServerConfigStore` implements, on every request, so every process sends only to the stored server from its first request on; nothing has to set it at start. It does so before OkHttp connects, so no credentials leave the phone, and `TogglApiClient` returns it as `ApiError.HostNotAllowed(origin)` with the origin as `scheme://host:port`. `current` is the stored server, or the URL being tried inside `signingIn`, which holds a `Mutex` so one sign-in runs at a time. With neither, every request is refused. The guard also refuses a plain `http://` server the user has not confirmed, and the trust manager refuses a self-signed certificate the user has not pinned (see the connection trust node). OkHttp's own redirects are off (`followRedirects(false)`, `followSslRedirects(false)`). The guard follows a redirect itself, and only to the same origin.

`ServerUrl.parse` accepts an http or https base URL that may carry a path, such as `https://example.com/tokkl`, and builds endpoints under `<base>/api/v9` and `<base>/reports/api/v3`. It refuses a URL with a user name or password, so the stored URL never holds a secret. It keeps only the scheme, host, port and path, so a query or fragment, as in a URL copied from the browser's address bar, is dropped, and so are empty path segments: `https://example.com/tokkl/` and `https://example.com/tokkl` are the same server. `ApiCredentials` builds the Basic header from `<api_token>:api_token` or `<email>:<password>`. Tokkl splits the decoded header at the first colon, so a password may contain colons and an email may not.

The connection pool keeps an idle connection for 4 seconds (`IDLE_CONNECTION_SECONDS`), not OkHttp's default 5 minutes. Tokkl runs on Node, which closes a keep-alive connection after 5 idle seconds, and OkHttp reuses a connection idle for under 10 seconds without checking it. A request sent once on such a closed connection failed, so a timer action a few seconds after the last request said there was no connection. With a 4 second pool the client drops the connection before the server does and opens a new one. `SharedHttpClient.sendingOnce`, in its companion, derives a client that does not retry; it shares the pool, interceptors and trust checks.

<!-- kk:related:start -->
# Related

- Related: [practice-talk-only-to-configured-server](../practice-talk-only-to-configured-server.md)
- Related: [practice-pin-self-signed-certs-warn-on-http](../practice-pin-self-signed-certs-warn-on-http.md)
- Related: [map-connection-trust-store-pinning-and-prompts](../map-connection-trust-store-pinning-and-prompts.md)
<!-- kk:related:end -->
