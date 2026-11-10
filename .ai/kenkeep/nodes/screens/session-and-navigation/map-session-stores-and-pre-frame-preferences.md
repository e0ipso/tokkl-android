---
type: map
title: Session stores and pre-frame preferences
description: >-
  Private session stores separate credentials, account settings, trust and
  device preferences; their holders load at process start and write through
  suspend calls.
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
`session/Session` is the signed-in account. `ServerConfigStore` keeps the base URL in its own private preferences file, `server_config`, and `ApiTokenStore` keeps the token. `settings/ThemeSettingsStore` keeps the theme mode, which follows the system by default, and the wallpaper colour switch in `theme_settings`. `AccountSettingsStore` (`account_settings`), `ServerTrustStore` (`server_trust`), `CapabilitiesStore` (`capabilities`) and `SignInCooldownStore` (`sign_in_cooldown`) keep their own preference files too, and so does `TimerViewStore` (`timer_view`), while `AccountCache` keeps the Room database `account_cache.db`. File names, keys and encodings are the stores' own, so data from earlier versions reads as before. `ThemeSettingsStore` and `themeSettingsWithNightMode` sit in `:feature:settings`, whose Koin module binds them, since only the Settings page changes the theme; the `ThemeSettingsRepository` that MainActivity and the Pomodoro alarm read stays in core:ui and reads and writes through core:ui's `ThemeSettingsDisk`, which `ThemeSettingsStore` implements.

Each preference store opens its file through `storage/PreferencesFile` in core:platform. `ValueStore`, `ClearableStore` and the stores' own interfaces suspend: `PreferencesFile.read`, `commit` (which throws `IOException` when the write fails) and `write` (an `apply()` for a value whose lost write costs little) switch to the I/O dispatcher the store takes from Koin themselves, and `ApiTokenStore` does its file and Keystore work on it under one `Mutex` for every instance. Four stores keep reading and writing on the calling thread, through `PreferencesFile.preferences` and `commitHere`, because their callers cannot suspend: `ServerTrustStore` and `ServerConfigStore.storedNow`, which OkHttp's trust checks and host guard read on OkHttp's threads, and `TimerStateStore` and `PomodoroWidgetFieldsStore`, which the timer state's holder writes under its acceptance lock on OkHttp and I/O threads. None of them runs on the main thread.

The values the screens and the background read live in holders that load once, at process start, through `storage/LoadedState`: `AccountSettingsSource`, `CapabilitiesSource`, `SyncedPreference` (which `PomodoroDefaultsSetting`, `TimerViewSetting` and `ReminderSetting` extend), `LastAnswer`, `DiscardedChanges` and `ThemeSettingsRepository`. Each starts its load as it is made, in the app scope, and the store reads the disk off the main thread. Each is a `StartupLoad`, except `ThemeSettingsRepository`: core:ui does not depend on core:platform, where `StartupLoad` lives, so the settings feature's `ThemeSettingsLoad` waits for it. `TokklApplication` makes them all through `StartupLoads` before the app hooks run. Until a load ends, the holder's `StateFlow` holds its default. Code that can run before then, outside the screens, waits: `current()`, the `loaded` flows, the suspending capability checks (`offersTimerApi()` and the others) and `ServerSpans.ready()`, which the queue and the sender await before they snap a time. MainActivity composes its screens only once every load has ended, at once in a process that runs already, and its pre-draw listener (`platform/holdDrawingUntil`) keeps the splash screen and draws no frame until then, so no screen shows a default for a stored value. The Pomodoro alarm holds its first frame for the theme the same way. `LoadedState` takes the context its load runs on (the I/O dispatcher in the app), its `value` is read-only, and only its suspend `set`, which waits for the load, changes it; `ThemeSettingsRepository` takes its load context too. Writes suspend and update memory first: a change made on a screen shows before the call returns, then goes to disk. `Mutex`es order the writes, in place of the `synchronized` blocks the holders had. `SyncedPreference` takes the `AccountContext` of `CurrentAccount` when a change, a refresh or a send begins, and shows, stores and sends nothing once it has ended, which sign-out does first. `forget()` waits for a write on its way, so the delete that sign-out runs next is the file's last write.

`Session` keeps what its first read of the server and token found, with the client built from it, until `save` or `clear` changes the account, so the queue and the screens do not decrypt the token on every call. Its `api()`, `isSignedIn()`, `save` and `clear` suspend and do their disk and Keystore work on the IO dispatcher, and the first `api()` also builds the API's client there, since the shared HTTP client may load the platform's certificates on first use, and a Mutex lets one read or change of the stores run at a time, so a read never keeps what a change of account replaced.

<!-- kk:related:start -->
# Related

- Related: [map-api-token-store-and-backup-exclusion](../../api-and-security/map-api-token-store-and-backup-exclusion.md)
- Related: [map-tokkl-colour-tokens-and-font](../../design/map-tokkl-colour-tokens-and-font.md)
- Related: [map-account-settings-source-and-account-time](../../data-and-offline/map-account-settings-source-and-account-time.md)
- Related: [map-connection-trust-store-pinning-and-prompts](../../api-and-security/map-connection-trust-store-pinning-and-prompts.md)
- Related: [map-account-cache-and-capabilities](../../data-and-offline/map-account-cache-and-capabilities.md)
<!-- kk:related:end -->
