---
type: practice
title: 'Show the wordmark only on sign-in and the launch splash, never in a header'
description: >-
  Owner rule of 2026-10-19: no wordmark in page headers or top bars, logo only
  as the launcher icon. The Timer page breaks it until TKA-0182.
tags:
  - ui
  - brand
  - design
kk_schema_version: 3
kk_id: practice-wordmark-only-on-sign-in-and-splash
kk_derived_from: []
kk_relates_to:
  - map-design-foundation-spacing-shapes-type-brand
  - map-tokkl-colour-tokens-and-font
  - map-timer-page-history-calendar-and-timer-view
kk_depends_on: []
kk_confidence: high
---
The wordmark appears only on the sign-in screen and on the launch splash, never in a page header or a top bar. The logo appears only as the launcher icon, never beside the wordmark (`docs/PRD.md`, Visual direction, Logo). The owner set the wordmark rule on 2026-10-19, and the PRD does not carry it yet.

The live tree breaks the rule in one place: the Timer page draws `Brand(BrandSize.MEDIUM)` as its heading, where the timer will go. TKA-0182 replaces it with the page's title in the page-title role and writes the rule into `docs/PRD.md`. TKA-0183 adds a launch splash that shows the wordmark in place of the launcher logo, through core-splashscreen. TKA-0123's shared top app bar never carries the wordmark. `BrandSize.SMALL` and `MEDIUM` still exist and have screenshot references, but no screen should put them in a header.

<!-- kk:related:start -->
# Related

- Related: [map-design-foundation-spacing-shapes-type-brand](map-design-foundation-spacing-shapes-type-brand.md)
- Related: [map-tokkl-colour-tokens-and-font](map-tokkl-colour-tokens-and-font.md)
- Related: [map-timer-page-history-calendar-and-timer-view](../screens/map-timer-page-history-calendar-and-timer-view.md)
<!-- kk:related:end -->
