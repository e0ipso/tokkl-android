---
type: practice
title: 'Show the wordmark only on sign-in and the launch splash, never in a header'
description: >-
  Owner rule of 2026-10-19, now in the PRD: no wordmark in page headers or top
  bars, logo only as the launcher icon. Pages show their title.
tags:
  - ui
  - brand
  - design
kk_schema_version: 3
kk_id: practice-wordmark-only-on-sign-in-and-splash
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-design-foundation-spacing-shapes-type-brand
  - map-tokkl-colour-tokens-and-font
  - map-timer-page-history-calendar-and-timer-view
  - map-ui-components-and-compose-layout-gotchas
kk_depends_on: []
kk_confidence: high
---
The wordmark appears only on the sign-in screen and on the launch splash, never in a page header or a top bar. The logo appears only as the launcher icon, never beside the wordmark. The owner set the wordmark rule on 2026-10-19, and the Wordmark item of the Visual direction in `docs/PRD.md` carries it.

The live tree keeps the rule. Sign-in is the only screen that calls `Brand`, with `BrandSize.LARGE`. Every signed-in page, the Timer page included, shows its title in the page-title role in `TokklTopBar`, which never draws the wordmark. `BrandSize` has only `LARGE`; the sm and md sizes were removed, so do not add a smaller wordmark for a header.

The launch splash comes from the window theme, not from a screen. `Theme.Tokkl` in `res/values` and `res/values-night` sets the platform's `android:windowSplashScreenAnimatedIcon` to `@drawable/splash_wordmark` and the splash background to `@color/window_background`, with no icon background and no branding image. The app runs on Android 12 and later only, so it needs no core-splashscreen, and nothing holds the splash past the app's first frame. Android draws the icon on a 288dp square and shows the 192dp circle in its middle, so `splash_wordmark.xml` places the wordmark by fractions of that square, 178dp wide. `drawable-night/splash_wordmark.xml` bakes in the 1dp halo in `@color/wordmark_halo` the way `ui/Brand.kt` draws it at run time, because the artwork's navy outline matches the dark background. `LaunchSplashTest` checks those resources, since no test can capture the splash itself.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-design-foundation-spacing-shapes-type-brand](map-design-foundation-spacing-shapes-type-brand.md)
- Related: [map-tokkl-colour-tokens-and-font](map-tokkl-colour-tokens-and-font.md)
- Related: [map-timer-page-history-calendar-and-timer-view → map-cached-history-days-and-grouping](../screens/timer-history-calendar/map-cached-history-days-and-grouping.md)
- Related: [map-timer-page-history-calendar-and-timer-view → map-timer-list-and-calendar-preference-sync](../screens/timer-history-calendar/map-timer-list-and-calendar-preference-sync.md)
- Related: [map-timer-page-history-calendar-and-timer-view → map-timer-page-layout-and-scroll-away-header](../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md)
- Related: [map-timer-page-history-calendar-and-timer-view → map-timer-today-and-this-week-totals](../screens/timer-history-calendar/map-timer-today-and-this-week-totals.md)
- Related: [map-timer-page-history-calendar-and-timer-view → map-week-calendar-layout-and-queued-dragging](../screens/timer-history-calendar/map-week-calendar-layout-and-queued-dragging.md)
- Related: [map-ui-components-and-compose-layout-gotchas](map-ui-components-and-compose-layout-gotchas.md)
<!-- kk:related:end -->
