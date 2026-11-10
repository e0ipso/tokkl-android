---
type: map
title: API test fixtures
description: >-
  TestApi supplies isolated client, transport and JSON helpers for JVM and
  device tests.
tags:
  - api
  - testing
kk_schema_version: 3
kk_id: map-api-test-fixtures
kk_derived_from: []
kk_relates_to:
  - map-typed-api-resources-and-request-contracts
  - map-shared-http-client-and-configured-server-security
kk_depends_on: []
kk_confidence: high
---
Unit tests build the client through `TestApi` in core:api's test fixtures: a `SharedHttpClient` over a `ConfiguredServer` and `ServerTrust` the test sets and changes, so no test leaves a server or trust behind. `api()` gives the app's `TokklApi`, `connector()` an `ApiConnector` over a client the test may derive, and `client()` a `TogglApiClient`, the fixtures' public wrapper of the internal transport, for a test that sends a request the app does not. The fixtures also give `ServerUrl.api`, `app` and `reports`, `SharedHttpClient.instance`, `apiConnector`, `TokklApi(client)`, `mePreferences(json)` and `WireJson`, and bring OkHttp and the JSON library with them. Its clients run on the calling thread (`Dispatchers.Unconfined`) by default, as the tests' unconfined scopes expect. Device tests take the same fixtures, the app's own client from Koin through `appHttp` and `appApiClient`, wrap one with `tokklApi()` to pass it to the app's code, and read the signed-in session's transport with `TestSession.client()`.

<!-- kk:related:start -->
# Related

- Related: [map-typed-api-resources-and-request-contracts](map-typed-api-resources-and-request-contracts.md)
- Related: [map-shared-http-client-and-configured-server-security](map-shared-http-client-and-configured-server-security.md)
<!-- kk:related:end -->
