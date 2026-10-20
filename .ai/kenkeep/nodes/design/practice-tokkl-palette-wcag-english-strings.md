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
kk_relates_to:
  - map-tokkl-colour-tokens-and-font
  - map-design-foundation-spacing-shapes-type-brand
kk_depends_on: []
kk_confidence: medium
---
Components come from Compose Material 3. Colour is always Tokkl's palette, never the wallpaper. Dynamic color is off and exists at most as an opt-in setting (G2). The theme is light, dark, or follows the system (G1).

Anybody, bundled with the app, sets the running clock, totals and headings. Everything else uses the system font.

Contrast meets WCAG AA as in Tokkl: 4.5:1 for text and 3:1 for non-text elements. Colour is never the only signal for a state. Every control has a TalkBack label, and layouts survive the largest font scale. `ThemeContrastTest` checks the theme's colour pairs. Draw chart marks on surface, not surface-2: in light, `--chart-other` falls under 3:1 on surface-2, which is why Tokkl's `styles.css` keeps its chart off surface-2. The reports chart's bar track is `chartBand`, Tokkl's `--chart-band`, from `TokklTheme.extraColors`, never `surfaceVariant`, which is surface-2. Tokkl's sixth principle applies as written. Any style that costs a readable number, a reachable control or a passing test gets dropped.

The owner's design brief, "radical graffiti surfer", is Tokkl's principle 6, "Loud look, plain function" (surf-shop graffiti), together with the Visual direction subsection that follows the principles in section 4 of `../tokkl/docs/PRD.md`. The Visual direction section of `docs/PRD.md` sets the app's spacing, type, wordmark, texture, type effects, motion and bundled assets, and `ui/theme` and `ui/` implement them.

On 2026-10-19 the owner chose the "Spectrum stickers" direction for Tokkl's web app and this app: the wordmark's saturated hues, ink outlines and hard offset shadows, and a hue for each top-level tab. `../tokkl/packages/web/src/styles.css` stays the source of truth, so the web changed first. Tokkl landed both on 2026-10-19 and ships them in v1.5.0: the Spectrum colour tokens in tokkl 524deaa (TT-0298) and the section hues and sticker panels in d012228 (TT-0299). The app mirrors the Spectrum colour tokens of 524deaa in `ui/theme/Color.kt` and `res/values*/colors.xml`. The sticker rules and tab hues of d012228 are not in the app yet (TKA-0186). When mirroring, take each value from `styles.css` at a recorded tokkl commit, never from the proposal, and update `window_background` and `wordmark_halo` with `Color.kt`.

The app is English only. All text lives in string resources. No other language is planned, and contributors can add translations.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-tokkl-colour-tokens-and-font](map-tokkl-colour-tokens-and-font.md)
- Related: [map-design-foundation-spacing-shapes-type-brand](map-design-foundation-spacing-shapes-type-brand.md)
<!-- kk:related:end -->
