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
kk_depends_on: []
kk_confidence: high
---
`signin/` holds the sign-in screen. `SignInModel` checks the form before anything is sent. An attempt, in `SignIn.kt`, is one `GET /api/v9/me` with Basic auth inside `ConfiguredServer.signingIn`, so the host guard lets the URL being tried through. Only once the server accepts do the URL and the token go to `Session`. With email and password, the app keeps the `api_token` from that response and writes the password nowhere. `SignInViewModel` keeps the form across configuration changes. The secrets stay in memory, never in saved instance state, and `toString` leaves them out.

`session/Session` is the signed-in account. `ServerConfigStore` keeps the base URL in its own private preferences file, `server_config`, and `ApiTokenStore` keeps the token. `settings/ThemeSettingsStore` keeps the theme mode, which follows the system by default, and the wallpaper colour switch in `theme_settings`. Both stores write with `commit()` and throw `IOException` when it fails, and their first call loads the file from disk. `TokklApplication` loads the theme settings once, before `MainActivity` draws its first frame.

`TokklNavHost` is the whole navigation: a back stack of `Destination`s held in `rememberSaveable`, so it survives configuration changes and process death. The app uses no navigation library. It starts at sign-in until a server and token are stored, and at the home screen after that.

<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors](map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [map-api-token-store-and-backup-exclusion](map-api-token-store-and-backup-exclusion.md)
- Related: [map-tokkl-colour-tokens-and-font](map-tokkl-colour-tokens-and-font.md)
<!-- kk:related:end -->
