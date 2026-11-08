---
type: map
title: Session stores and pre-frame preferences
description: >-
  Private session stores separate credentials, account settings, trust and
  device preferences.
tags:
  - android
  - credentials
  - storage
kk_schema_version: 3
kk_id: map-session-stores-and-pre-frame-preferences
kk_derived_from: []
kk_relates_to:
  - map-api-token-store-and-backup-exclusion
  - map-tokkl-colour-tokens-and-font
  - map-account-settings-source-and-account-time
  - map-connection-trust-store-pinning-and-prompts
  - map-account-cache-and-capabilities
kk_depends_on: []
kk_confidence: high
---
`session/Session` is the signed-in account. `ServerConfigStore` keeps the base URL in its own private preferences file, `server_config`, and `ApiTokenStore` keeps the token. `settings/ThemeSettingsStore` keeps the theme mode, which follows the system by default, and the wallpaper colour switch in `theme_settings`. `AccountSettingsStore` (`account_settings`), `ServerTrustStore` (`server_trust`), `CapabilitiesStore` (`capabilities`) and `SignInCooldownStore` (`sign_in_cooldown`) keep their own preference files too, and so does `TimerViewStore` (`timer_view`), while `AccountCache` keeps the Room database `account_cache.db`. Each preference store opens its file through `storage/PreferencesFile` in core:platform, which opens it on first use and writes with `commit()`, throwing `IOException` when the write fails; the first call loads the file from disk. File names, keys and encodings are the stores' own, so data from earlier versions reads as before. `ThemeSettingsStore` and `themeSettingsWithNightMode` sit in `:feature:settings`, whose Koin module binds them, since only the Settings page changes the theme; the `ThemeSettingsRepository` that MainActivity and the Pomodoro alarm read stays in core:ui. `Session` keeps what its first read of the server and token found, with the client built from it, until `save` or `clear` changes the account, so the queue and the screens do not decrypt the token on every call. `TokklApplication` loads the theme settings once, before `MainActivity` draws its first frame.

<!-- kk:related:start -->
# Related

- Related: [map-api-token-store-and-backup-exclusion](../../api-and-security/map-api-token-store-and-backup-exclusion.md)
- Related: [map-tokkl-colour-tokens-and-font](../../design/map-tokkl-colour-tokens-and-font.md)
- Related: [map-account-settings-source-and-account-time](../../data-and-offline/map-account-settings-source-and-account-time.md)
- Related: [map-connection-trust-store-pinning-and-prompts](../../api-and-security/map-connection-trust-store-pinning-and-prompts.md)
- Related: [map-account-cache-and-capabilities](../../data-and-offline/map-account-cache-and-capabilities.md)
<!-- kk:related:end -->
