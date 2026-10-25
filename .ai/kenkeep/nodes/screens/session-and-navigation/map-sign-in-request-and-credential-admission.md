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
`signin/` holds the sign-in screen. `SignInModel` checks the form before anything is sent. An attempt, in `SignIn.kt`, is one `GET /api/v9/me` with Basic auth inside `ConfiguredServer.signingIn`, so the host guard lets the URL being tried through. Only once the server accepts do the URL and the token go to `Session`, and the account settings from the same `/me` go to `AccountSettingsSource`. A `/me` without `timezone` or `beginning_of_week` fails as `SignInResult.NotTokkl`. With email and password, the app keeps the `api_token` from that response and writes the password nowhere. A 429 blocks sign-in for its `Retry-After`, or a default delay without one. `signin/SignInCooldownStore` keeps one deadline per server origin, so a new process still waits it out. The deadline is wall-clock time because Tokkl's lockout runs on the server's clock, and a phone reboot does not lift it. The screen draws the large brand on Tokkl's scene and the form on a surface card above it, with failures in an error banner. `SignInViewModel` keeps the form across configuration changes. The secrets stay in memory, never in saved instance state, and `toString` leaves them out.

<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors](../../api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [map-api-token-store-and-backup-exclusion](../../api-and-security/map-api-token-store-and-backup-exclusion.md)
- Related: [map-account-settings-source-and-account-time](../../data-and-offline/map-account-settings-source-and-account-time.md)
<!-- kk:related:end -->
