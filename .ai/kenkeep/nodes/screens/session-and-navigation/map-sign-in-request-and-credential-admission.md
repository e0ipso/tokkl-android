---
type: map
title: Sign-in request and credential admission
description: >-
  Validate configured-server credentials and required account settings before
  storing a session.
tags:
  - android
  - ui
  - credentials
kk_schema_version: 3
kk_id: map-sign-in-request-and-credential-admission
kk_derived_from: []
kk_relates_to:
  - map-api-client-shared-okhttp-serverurl-and-errors
  - map-api-token-store-and-backup-exclusion
  - map-account-settings-source-and-account-time
kk_depends_on: []
kk_confidence: high
---
`signin/` holds the sign-in screen. `SignInViewModel` checks the form before anything is sent. The view model takes the `SignIn` interface. `ServerSignIn`, in `SignIn.kt`, sends an attempt: one `GET /api/v9/me` with Basic auth inside `ConfiguredServer.signingIn`, so the host guard lets the URL being tried through. `SignInViewModelTest` answers `SignIn` in memory to check the form, and `ServerSignInTest` checks what an attempt sends and what each answer becomes against a MockWebServer. `ServerSignIn` runs each attempt in the application's scope and the view model only waits for its answer, so a sign-in the server accepted is stored also when the form goes meanwhile. Only once the server accepts do the URL and the token go to `Session`, and the account settings from the same `/me` go to `AccountSettingsSource`. A `/me` without `timezone` or `beginning_of_week` fails as `SignInResult.NotTokkl`. With email and password, the app keeps the `api_token` from that response and writes the password nowhere. A 429 blocks sign-in for its `Retry-After`, or a default delay without one. `signin/SignInCooldownStore` keeps one deadline per server origin, so a new process still waits it out. The deadline is wall-clock time because Tokkl's lockout runs on the server's clock, and a phone reboot does not lift it. The screen draws the large brand on Tokkl's scene and the form on a surface card above it, with failures in an error banner. `SignInViewModel` keeps the form across configuration changes. The secrets stay in memory, never in saved instance state, and `toString` leaves them out.

<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-execution-retries-and-errors](../../api-and-security/api-client/map-api-execution-retries-and-errors.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-test-fixtures](../../api-and-security/api-client/map-api-test-fixtures.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-shared-http-client-and-configured-server-security](../../api-and-security/api-client/map-shared-http-client-and-configured-server-security.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-typed-api-resources-and-request-contracts](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md)
- Related: [map-api-token-store-and-backup-exclusion](../../api-and-security/map-api-token-store-and-backup-exclusion.md)
- Related: [map-account-settings-source-and-account-time → map-account-settings-loading-and-refresh](../../data-and-offline/account-settings-and-time/map-account-settings-loading-and-refresh.md)
- Related: [map-account-settings-source-and-account-time → map-account-time-fields-and-validation](../../data-and-offline/account-settings-and-time/map-account-time-fields-and-validation.md)
<!-- kk:related:end -->
