---
type: map
title: 'Sign-in, the stored session, theme settings and the navigation host'
description: >-
  signin/ tries GET /me before storing anything, session/ and settings/ keep
  their own preference files, TokklNavHost is a saveable back stack.
tags:
  - android
  - ui
  - credentials
  - navigation
kk_schema_version: 3
kk_id: map-signin-session-settings-and-navigation-host
kk_derived_from: []
kk_relates_to:
  - map-api-client-shared-okhttp-serverurl-and-errors
  - map-api-token-store-and-backup-exclusion
  - map-tokkl-colour-tokens-and-font
  - map-account-settings-source-and-account-time
  - map-connection-trust-store-pinning-and-prompts
  - map-account-cache-and-capabilities
  - map-reports-summary-from-detailed-search
kk_depends_on: []
kk_confidence: high
---
`signin/` holds the sign-in screen. `SignInModel` checks the form before anything is sent. An attempt, in `SignIn.kt`, is one `GET /api/v9/me` with Basic auth inside `ConfiguredServer.signingIn`, so the host guard lets the URL being tried through. Only once the server accepts do the URL and the token go to `Session`, and the account settings from the same `/me` go to `AccountSettingsSource`. A `/me` without `timezone` or `beginning_of_week` fails as `SignInResult.NotTokkl`. With email and password, the app keeps the `api_token` from that response and writes the password nowhere. A 429 blocks sign-in for its `Retry-After`, or a default delay without one. `signin/SignInCooldownStore` keeps one deadline per server origin, so a new process still waits it out. The deadline is wall-clock time because Tokkl's lockout runs on the server's clock, and a phone reboot does not lift it. `SignInViewModel` keeps the form across configuration changes. The secrets stay in memory, never in saved instance state, and `toString` leaves them out.

`session/Session` is the signed-in account. `ServerConfigStore` keeps the base URL in its own private preferences file, `server_config`, and `ApiTokenStore` keeps the token. `settings/ThemeSettingsStore` keeps the theme mode, which follows the system by default, and the wallpaper colour switch in `theme_settings`. `AccountSettingsStore` (`account_settings`), `ServerTrustStore` (`server_trust`), `CapabilitiesStore` (`capabilities`) and `SignInCooldownStore` (`sign_in_cooldown`) keep their own preference files too, and `AccountCache` keeps the Room database `account_cache.db`. There is no sign-out yet. It has to clear the token, the server, the account settings, `ServerTrustStore`, `CapabilitiesStore` and `AccountCache`. Each preference store writes with `commit()` and throws `IOException` when it fails, and its first call loads the file from disk. `TokklApplication` loads the theme settings once, before `MainActivity` draws its first frame.

`TokklNavHost` is the whole navigation: a back stack of `Destination`s held in `rememberSaveable`, so it survives configuration changes and process death. The destinations are `SIGN_IN`, `HOME`, `SETTINGS`, `LICENCES` and `REPORTS`. Home is still a placeholder that shows the `Brand` and links to the others. The app uses no navigation library. It starts at sign-in until a server and token are stored, and at the home screen after that. Above every destination except sign-in it shows the session's trust prompt, or after a cancel the notice that reopens it.

<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors](map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [map-api-token-store-and-backup-exclusion](map-api-token-store-and-backup-exclusion.md)
- Related: [map-tokkl-colour-tokens-and-font](map-tokkl-colour-tokens-and-font.md)
- Related: [map-account-settings-source-and-account-time](map-account-settings-source-and-account-time.md)
- Related: [map-connection-trust-store-pinning-and-prompts](map-connection-trust-store-pinning-and-prompts.md)
- Related: [map-account-cache-and-capabilities](map-account-cache-and-capabilities.md)
- Related: [map-reports-summary-from-detailed-search](map-reports-summary-from-detailed-search.md)
<!-- kk:related:end -->
