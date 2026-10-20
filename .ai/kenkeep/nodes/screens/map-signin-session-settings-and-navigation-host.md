---
type: map
title: 'Sign-in, sign-out, the stored session, settings and the navigation host'
description: >-
  signin/ tries GET /me before storing anything, SignOut clears every account
  store, and TokklNavHost holds the back stack, top bar and bottom bar.
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
  - map-change-queue-sends-entry-changes-in-order
  - map-ui-components-and-compose-layout-gotchas
kk_depends_on: []
kk_confidence: high
---
`signin/` holds the sign-in screen. `SignInModel` checks the form before anything is sent. An attempt, in `SignIn.kt`, is one `GET /api/v9/me` with Basic auth inside `ConfiguredServer.signingIn`, so the host guard lets the URL being tried through. Only once the server accepts do the URL and the token go to `Session`, and the account settings from the same `/me` go to `AccountSettingsSource`. A `/me` without `timezone` or `beginning_of_week` fails as `SignInResult.NotTokkl`. With email and password, the app keeps the `api_token` from that response and writes the password nowhere. A 429 blocks sign-in for its `Retry-After`, or a default delay without one. `signin/SignInCooldownStore` keeps one deadline per server origin, so a new process still waits it out. The deadline is wall-clock time because Tokkl's lockout runs on the server's clock, and a phone reboot does not lift it. The screen draws the large brand on Tokkl's scene and the form on a surface card above it, with failures in an error banner. `SignInViewModel` keeps the form across configuration changes. The secrets stay in memory, never in saved instance state, and `toString` leaves them out.

`session/Session` is the signed-in account. `ServerConfigStore` keeps the base URL in its own private preferences file, `server_config`, and `ApiTokenStore` keeps the token. `settings/ThemeSettingsStore` keeps the theme mode, which follows the system by default, and the wallpaper colour switch in `theme_settings`. `AccountSettingsStore` (`account_settings`), `ServerTrustStore` (`server_trust`), `CapabilitiesStore` (`capabilities`) and `SignInCooldownStore` (`sign_in_cooldown`) keep their own preference files too, and so does `TimerViewStore` (`timer_view`), while `AccountCache` keeps the Room database `account_cache.db`. Each preference store writes with `commit()` and throws `IOException` when it fails, and its first call loads the file from disk. `TokklApplication` loads the theme settings once, before `MainActivity` draws its first frame.

Settings signs out (S6). `settings/SignOutModel` asks first, with the count, while the queue holds changes the server has not received, and signs out at once otherwise. `session/SignOut` runs the steps that `TokklApplication.signOut` lists, in order, off the main thread. The first forgets the session, the server and the token with its Keystore key, so the app counts as signed out and sends nothing new. Then it cancels the queue's background work and every call in flight (`SharedHttpClient.cancelAll()`), waits for the refresh and the sender's drain to end, and clears the cache with the queue, the account settings, the capabilities, the Timer page's view, the trust decisions and the session's trust prompt. A failed step does not stop the ones after it, and the next sign-out runs them all again. The theme settings and the sign-in cooldowns stay, because they belong to the phone and to a server, not to the account. `SignOut.finished` counts finished sign-outs. `MainActivity` drops its view models when it changes and keys `TokklNavHost` on it, so the navigation starts over at sign-in with fresh models and nothing a screen held in memory, such as the history's older days, survives.

`TokklNavHost` is the whole navigation, with no navigation library: a back stack of `Destination`s held in `rememberSaveable`, so it survives configuration changes and process death. The destinations are `SIGN_IN`, `TIMER`, `REPORTS`, `PROJECTS`, `CLIENTS`, `TAGS`, `TRASH`, `SETTINGS`, `LICENCES` and `ENTRY`, the entry form above the Timer page. The stack is saved by name, and a name this version no longer has, such as an older version's `HOME`, is dropped. It starts at sign-in until a server and token are stored, and at the Timer page after that. Above every signed-in screen it shows the session's trust prompt, or after a cancel the notice that reopens it.

Every signed-in screen sits in `SignedInFrame` with the bottom bar of `navigation/AppNavigation.kt`, as on Tokkl's web app on a phone (`docs/PRD.md` 5.11, B1 to B3). `TopLevelPage` follows the order of `NAV` in Tokkl's `App.tsx`: Timer, Reports, Projects and Settings in the bar, and Clients, Tags and Trash on the More item's `ModalBottomSheet`. Opening a page from the bar or the sheet puts it right above Timer, so Back goes to Timer and Back from Timer leaves the app. A screen opened from a page, such as Licences from Settings, stacks above that page, and the bar keeps marking the page. Every signed-in page draws `TokklTopBar` with its title as its `Scaffold`'s top bar. The pages in the bar have no back arrow there. Licences, the project and client forms and the entry form pass `onBack` for one. While an entry runs, the Timer item carries a dot, and TalkBack reads its state as "Timer running". Trash is still a placeholder page.

<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors](../api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [map-api-token-store-and-backup-exclusion](../api-and-security/map-api-token-store-and-backup-exclusion.md)
- Related: [map-tokkl-colour-tokens-and-font](../design/map-tokkl-colour-tokens-and-font.md)
- Related: [map-account-settings-source-and-account-time](../data-and-offline/map-account-settings-source-and-account-time.md)
- Related: [map-connection-trust-store-pinning-and-prompts](../api-and-security/map-connection-trust-store-pinning-and-prompts.md)
- Related: [map-account-cache-and-capabilities](../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-reports-summary-from-detailed-search](map-reports-summary-from-detailed-search.md)
- Related: [map-change-queue-sends-entry-changes-in-order](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md)
- Related: [map-ui-components-and-compose-layout-gotchas](../design/map-ui-components-and-compose-layout-gotchas.md)
<!-- kk:related:end -->
