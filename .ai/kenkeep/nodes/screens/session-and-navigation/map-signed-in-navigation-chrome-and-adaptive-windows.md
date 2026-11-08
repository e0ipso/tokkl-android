---
type: map
title: Signed-in navigation chrome and adaptive windows
description: 'Bar, rail, back-stack and width rules control signed-in page layout.'
tags:
  - android
  - ui
  - navigation
kk_schema_version: 3
kk_id: map-signed-in-navigation-chrome-and-adaptive-windows
kk_derived_from: []
kk_relates_to:
  - map-tokkl-colour-tokens-and-font
  - map-ui-components-and-compose-layout-gotchas
  - map-reports-summary-from-detailed-search
kk_depends_on: []
kk_confidence: high
---
Every signed-in screen sits in `SignedInFrame` with the bottom bar of `navhost/AppNavigation.kt` in the app on a compact window, as on Tokkl's web app on a phone (`docs/PRD.md` 5.11, B1 to B3). The bar, the rail and the More sheet show `TopLevelPages`, which `di/NavigationModule.kt` collects with `getAll()` from the `TopLevelDestination` each feature binds with `topLevelDestination(...)` in its Koin module: the page's route, label, icon, section hue, position and whether it sits on the More sheet. The pages stand in the order of their positions, which follow NAV in App.tsx in steps of ten, and the composables name a page by its `Route`. Each page's value, such as `tagsPage`, sits in the feature's `XEntries.kt`, not in the file of its Koin module: a top-level value declared below `module {}` in the same file is still null when the module's lambda runs, which on 2026-11-08 failed every unit test that loaded `appModules`. Opening a page from the bar or the sheet puts it right above Timer, so Back goes to Timer and Back from Timer leaves the app. A screen opened from a page, such as Licences from Settings, stacks above that page, and the bar keeps marking the page. Every signed-in page draws `TokklTopBar` with its title as its `Scaffold`'s top bar. The pages in the bar have no back arrow there. Licences, the project and client forms and the entry form pass `onBack` for one. While an entry runs, the Timer item carries a dot, and TalkBack reads its state as "Timer running". `SyncStatusBand` in `sync/` sits right above the bar (or at the bottom beside the rail) and shows how many changes the server has not received and when it last answered, from `LastAnswer`, the time of the last 2xx. `navhost/SignedInViewModel`, in the signed-in session's store, gives `SignedInHost` one state for all of this: the Timer view, the bar's marks, the sync status, the discarded changes, the short Stop's Undo and the trust prompt. `TokklNavHost` takes the features' entries, the top-level pages, the sign-out count, the capabilities and the `NavigationSession` checks as plain parameters, which default to what the Koin graph holds. No view model sits between them; `di/NavigationModule.kt` binds the `NavigationSession`, which reads the session and the Pomodoro notification off the main thread.

`ui/WindowWidth.kt`'s `windowWidth()` maps the Material 3 adaptive library's window size class to `COMPACT` (under 600dp), `MEDIUM` (from 600dp) or `EXPANDED` (from 840dp); a phone held sideways counts as expanded. Every class shows the same screens. Medium and expanded windows get `AppNavigationRail` with the same items in place of the bottom bar. On an expanded window in the Timer page's List view, the entry form opens beside the history (`showsEntryBeside`), and `SignedInPanes` keeps the Timer page and the form each in one place in the composition, so a rotation or a fold that changes the layout keeps their saved state. The Calendar view keeps the whole width, and wide content caps at `TokklSpacing.wideContentMaxWidth`, 1100dp, as the web app's `.page` does. `AdaptiveLayoutTest` forces a window with `DeviceConfigurationOverride.WindowSize`, which scales the density down when the window is larger than the device's, so those tests compare positions, not sizes.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-colour-tokens-and-font](../../design/map-tokkl-colour-tokens-and-font.md)
- Related: [map-ui-components-and-compose-layout-gotchas](../../design/map-ui-components-and-compose-layout-gotchas.md)
- Related: [map-reports-summary-from-detailed-search](../map-reports-summary-from-detailed-search.md)
<!-- kk:related:end -->
