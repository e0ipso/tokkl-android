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
  - map-design-foundation-spacing-shapes-type-brand
kk_depends_on: []
kk_confidence: medium
---
The source of truth for the app's colours is the colour tokens in `../tokkl/packages/web/src/styles.css`.

The light theme has a sand background, ink text, a sunset orange primary, an aqua secondary, and hot pink and lime for states. The dark theme has a navy background with the neon counterparts.

The app bundles the Anybody font. Tokkl already ships it under the SIL Open Font License 1.1, with the license at `../tokkl/packages/web/public/fonts/OFL.txt`.

In the app, `ui/theme/Color.kt` defines the Material 3 roles and the running and billable state colours, each with a comment naming its token in the first `:root` block of `styles.css`. Find a token by name, not by line. `TokklTheme` has dynamic colour off by default. `Theme.Tokkl` in `res/values` and `res/values-night` sets `android:windowBackground` to the scheme's background (`@color/window_background`), so launch shows no flash of another colour before Compose draws. When a token changes, update `Color.kt` and `window_background` together. On Android 12 (API 31) and later, `syncApplicationNightMode` in `settings/ApplicationNightMode.kt` passes the stored theme mode to `UiModeManager.setApplicationNightMode` on every start and every change, off the main thread: light is `MODE_NIGHT_NO`, dark `MODE_NIGHT_YES` and system `MODE_NIGHT_AUTO`. Android picks `res/values` or `res/values-night` for the starting window by that mode, so a cold start shows the chosen theme's colours. `MainActivity` declares `android:configChanges="uiMode"`, so the sync does not recreate it. Below Android 12 the starting window still follows the system's dark mode, and the window theme never knows the wallpaper's colours. So `MainActivity` still repaints the window background and picks the system bar icon colours from the chosen theme each time the setting changes. Wallpaper colours are an opt-in switch on the settings screen, shown only on Android 12 (API 31) and later.

The font is upstream Anybody 1.114, the variable TTF with width and weight axes, at `res/font/anybody_wdth_wght.ttf`. `res/font` accepts only font files, so the OFL (`anybody_ofl.txt`) and the source note with commit, URL and SHA-256 (`anybody_font_source.txt`) live in `res/raw`. No code reads the source note, so `res/raw/keep.xml` marks it `tools:keep`. `ui/theme/Type.kt` gives Anybody its own `Font` entries for weights 800 and 900 at width 85 and sizes the Material type roles from Tokkl's web scale.

The adaptive launcher icon is made from `../tokkl/docs/brand/tokkl-icon-v4-1024.png`. Its foreground and monochrome layers are 432 px PNGs in `res/drawable-xxxhdpi`, and `res/mipmap-anydpi/ic_launcher.xml` records the ImageMagick steps that built them.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
