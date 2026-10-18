---
type: map
title: Tokkl colour tokens and Anybody font in ../tokkl
description: >-
  The colour tokens in ../tokkl/packages/web/src/styles.css are the palette's
  source of truth. Tokkl ships Anybody under the SIL OFL 1.1.
tags:
  - ui
  - tokkl
kk_schema_version: 3
kk_id: map-tokkl-colour-tokens-and-font
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - practice-tokkl-palette-wcag-english-strings
kk_depends_on: []
kk_confidence: medium
---
The source of truth for the app's colours is the colour tokens in `../tokkl/packages/web/src/styles.css`.

The light theme has a sand background, ink text, a sunset orange primary, an aqua secondary, and hot pink and lime for states. The dark theme has a navy background with the neon counterparts.

The app bundles the Anybody font. Tokkl already ships it under the SIL Open Font License 1.1, with the license at `../tokkl/packages/web/public/fonts/OFL.txt`.

In the app, `ui/theme/Color.kt` defines the Material 3 roles and the running and billable state colours, each with a `styles.css:<line> --token` comment naming its source. `TokklTheme` has dynamic colour off by default. `Theme.Tokkl` in `res/values` and `res/values-night` sets `android:windowBackground` to the scheme's background (`@color/window_background`), so launch shows no flash of another colour before Compose draws. When a token changes, update `Color.kt` and `window_background` together.

The font is upstream Anybody 1.114, the variable TTF with width and weight axes, at `res/font/anybody_wdth_wght.ttf`. `res/font` accepts only font files, so the OFL (`anybody_ofl.txt`) and the source note with commit, URL and SHA-256 (`anybody_font_source.txt`) live in `res/raw`. No code reads the source note, so `res/raw/keep.xml` marks it `tools:keep`. `ui/theme/Type.kt` defines `ClockTextStyle` and `TotalTextStyle` in Anybody.

The adaptive launcher icon is made from `../tokkl/docs/brand/tokkl-icon-v4-1024.png`. Its foreground and monochrome layers are 432 px PNGs in `res/drawable-xxxhdpi`, and `res/mipmap-anydpi/ic_launcher.xml` records the ImageMagick steps that built them.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
