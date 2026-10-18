---
type: practice
title: 'Use Tokkl''s palette, WCAG AA contrast and English string resources'
description: >-
  Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG
  AA contrast, colour never the only signal, text in resources.
tags:
  - ui
  - accessibility
  - i18n
kk_schema_version: 3
kk_id: practice-tokkl-palette-wcag-english-strings
kk_derived_from:
  - docs/PRD.md
kk_relates_to: []
kk_depends_on: []
kk_confidence: medium
---
Components come from Compose Material 3. Colour is always Tokkl's palette, never the wallpaper. Dynamic color is off and exists at most as an opt-in setting (G2). The theme is light, dark, or follows the system (G1).

Anybody, bundled with the app, sets the running clock, totals and headings. Everything else uses the system font.

Contrast meets WCAG AA as in Tokkl: 4.5:1 for text and 3:1 for non-text elements. Colour is never the only signal for a state. Every control has a TalkBack label, and layouts survive the largest font scale. Tokkl's sixth principle applies as written. Any style that costs a readable number, a reachable control or a passing test gets dropped.

The owner's design brief, "radical graffiti surfer", is Tokkl's principle 6, "Loud look, plain function" (surf-shop graffiti), together with the Visual direction subsection that follows the principles in section 4 of `../tokkl/docs/PRD.md`. The app's spacing scale, content width, type scale, shapes and wordmark values are drafted in the look-and-feel tickets TKA-0105 to TKA-0124. The code does not have them until those tickets land.

The app is English only. All text lives in string resources. No other language is planned, and contributors can add translations.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
