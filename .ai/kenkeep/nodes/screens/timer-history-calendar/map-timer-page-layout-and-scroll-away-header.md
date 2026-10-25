---
type: map
title: Timer page layout and scroll-away header
description: >-
  Bounded Timer content and the shared scroll-away header adapt to short and
  wide windows.
tags:
  - ui
  - timer
  - android
kk_schema_version: 3
kk_id: map-timer-page-layout-and-scroll-away-header
kk_derived_from:
  - 'b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0'
kk_relates_to:
  - map-ui-components-and-compose-layout-gotchas
  - map-timer-controls-and-cached-start-suggestions
kk_depends_on: []
kk_confidence: medium
---
`timer/TimerScreen.kt` is the Timer page, the first page after sign-in. As on the web app's `TimerPage.tsx`, it has the timer, then a List and Calendar switch, then the entries in the chosen view, under the top bar with the page's title. The timer, the entry form and the history's entry menu have their own node. The list and the calendar each fill the rest of the page with a bounded height, so each scrolls on its own. On a window of compact height (`compactHeightWindow()`, which divides the height by the font scale), the top bar, the timer and the switch scroll away as the entries scroll on, through `ScrollAwayColumn`, and `TopBarStatusBand` keeps the status bar's colour. The Calendar view does the same at every window height, because on a phone held upright the timer, the favorites and the switch would leave the grid under two hours. On a phone held sideways (`shortWindow()`, under 480dp tall), the Calendar view opens with all of that and the calendar's toolbar already scrolled away, so the grid shows at least four hour rows at once. `LandscapeCalendarTest` checks this with a single swipe from the bottom of the page to the top of the window, so anything that makes the page's top taller than that swipe fails it.

<!-- kk:related:start -->
# Related

- Related: [map-ui-components-and-compose-layout-gotchas](../../design/map-ui-components-and-compose-layout-gotchas.md)
- Related: [map-timer-controls-and-cached-start-suggestions](../timer-and-entry-editing/map-timer-controls-and-cached-start-suggestions.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0
<!-- kk:citations:end -->
