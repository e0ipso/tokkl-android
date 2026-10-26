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
The source of truth for the app's colours is the colour tokens in `../tokkl/packages/web/src/styles.css`. `ui/theme/Color.kt` mirrors the Spectrum palette of tokkl 524deaa (v1.5.0, TT-0298), and its header comment records that commit. When `styles.css` changes again, take the new values from a recorded tokkl commit and update that comment.

The light theme has an off-white `--bg` (#FFFDF7), ink text, an orange primary, a teal secondary, a yellow `--selected`, and hot pink and lime accents. The dark theme has a navy `--bg` (#08061F) with brighter counterparts.

`styles.css` splits primary in two, and so does the app. `--primary` is a fill with `--on-primary` ink on it, and text in the primary colour reads `--primary-text`, because the light `--primary` is only 3.40:1 on surface. Material 3 draws text in its primary role, so the Material `primary` role is `--primary-text`. The `--primary` fill is `TokklTheme.extraColors.primaryFill` (with `onPrimaryFill`), which `TokklButton` in `ui/Buttons.kt` and the week strip's today badge use; paint any other `--primary` fill from it, never from `colorScheme.primary`. With wallpaper colours on, `TokklTheme` replaces `primaryFill` with the wallpaper scheme's primary through `withPrimaryFill` and keeps every other extra colour. `secondaryContainer` is `--selected`, the web's active segmented button fill, with `--on-selected` on it. Each role in `colorScheme` that has no token of its own says in a comment which token it borrows.

The app bundles the Anybody font. Tokkl already ships it under the SIL Open Font License 1.1, with the license at `../tokkl/packages/web/public/fonts/OFL.txt`.

In the app, `ui/theme/Color.kt` defines the Material 3 roles and the running and billable state colours, each with a comment naming its token in the first `:root` block of `styles.css`. Find a token by name, not by line. `TokklTheme` has dynamic colour off by default. `Theme.Tokkl` in `res/values` and `res/values-night` sets `android:windowBackground` to the scheme's background (`@color/window_background`), so launch shows no flash of another colour before Compose draws. The same theme draws the launch splash on that background, with the wordmark as its icon (see the wordmark practice node). When a token changes, update `Color.kt` and `window_background` together, and `wordmark_halo` with `wordmarkHalo`. `syncApplicationNightMode` in `settings/ApplicationNightMode.kt` passes the stored theme mode to `UiModeManager.setApplicationNightMode` on every start and every change, off the main thread: light is `MODE_NIGHT_NO`, dark `MODE_NIGHT_YES` and system `MODE_NIGHT_AUTO`. Android picks `res/values` or `res/values-night` for the starting window by that mode, so a cold start shows the chosen theme's colours. `MainActivity` declares `android:configChanges="uiMode"`, so the sync does not recreate it. The window theme never knows the wallpaper's colours, so `MainActivity`'s `WindowFollowsTheme` still repaints the window background and picks the system bar icon colours from the chosen theme each time the setting changes. Wallpaper colours are an opt-in switch on the settings screen. Since `minSdk` 31 every device can use them, so the switch has no version check.

The font is upstream Anybody 1.114, the variable TTF with width and weight axes, at `res/font/anybody_wdth_wght.ttf`. `res/font` accepts only font files, so the OFL (`anybody_ofl.txt`) and the source note with commit, URL and SHA-256 (`anybody_font_source.txt`) live in `res/raw`. No code reads the source note, so `res/raw/keep.xml` marks it `tools:keep`. `ui/theme/Type.kt` gives Anybody its own `Font` entries for weights 800 and 900 at width 85 and sizes the Material type roles from Tokkl's web scale.

The adaptive launcher icon is the owner's clock O logo on plum. Its foreground and monochrome layers in `res/mipmap-mdpi` to `res/mipmap-xxxhdpi` are byte-for-byte copies of the `android/res` files in Tokkl's icon pack. They already carry the adaptive icon padding, so copy them as they are, with no inset, resize or generator step. The one `res/mipmap-anydpi/ic_launcher.xml` names the source, and the app needs no legacy PNG icons, which no release from API 26 on loads. The background layer is `ic_launcher_background`, #291329 in `res/values/colors.xml`, the wordmark's dark plum, which is not a palette token. `LauncherIconTest` compares the launcher's foreground with `R.mipmap.ic_launcher_foreground`. The F-Droid `fastlane/metadata/android/en-US/images/icon.png` is the same pack's `store/google-play-512.png`.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [practice-tokkl-palette-wcag-english-strings](practice-tokkl-palette-wcag-english-strings.md)
- Related: [map-design-foundation-spacing-shapes-type-brand](map-design-foundation-spacing-shapes-type-brand.md)
<!-- kk:related:end -->
