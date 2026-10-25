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
Every signed-in screen sits in `SignedInFrame` with the bottom bar of `navigation/AppNavigation.kt` on a compact window, as on Tokkl's web app on a phone (`docs/PRD.md` 5.11, B1 to B3). Opening a page from the bar or the sheet puts it right above Timer, so Back goes to Timer and Back from Timer leaves the app. A screen opened from a page, such as Licences from Settings, stacks above that page, and the bar keeps marking the page. Every signed-in page draws `TokklTopBar` with its title as its `Scaffold`'s top bar. The pages in the bar have no back arrow there. Licences, the project and client forms and the entry form pass `onBack` for one. While an entry runs, the Timer item carries a dot, and TalkBack reads its state as "Timer running". `SyncStatusBand` in `sync/` sits right above the bar (or at the bottom beside the rail) and shows how many changes the server has not received and when it last answered, from `LastAnswer`, the time of the last 2xx.

`ui/WindowWidth.kt`'s `windowWidth()` maps the Material 3 adaptive library's window size class to `COMPACT` (under 600dp), `MEDIUM` (from 600dp) or `EXPANDED` (from 840dp); a phone held sideways counts as expanded. Every class shows the same screens. Medium and expanded windows get `AppNavigationRail` with the same items in place of the bottom bar. On an expanded window in the Timer page's List view, the entry form opens beside the history (`showsEntryBeside`), and `SignedInPanes` keeps the Timer page and the form each in one place in the composition, so a rotation or a fold that changes the layout keeps their saved state. The Calendar view keeps the whole width, and wide content caps at `TokklSpacing.wideContentMaxWidth`, 1100dp, as the web app's `.page` does. `AdaptiveLayoutTest` forces a window with `DeviceConfigurationOverride.WindowSize`, which scales the density down when the window is larger than the device's, so those tests compare positions, not sizes.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-colour-tokens-and-font](../../design/map-tokkl-colour-tokens-and-font.md)
- Related: [map-ui-components-and-compose-layout-gotchas](../../design/map-ui-components-and-compose-layout-gotchas.md)
- Related: [map-reports-summary-from-detailed-search](../map-reports-summary-from-detailed-search.md)
<!-- kk:related:end -->
