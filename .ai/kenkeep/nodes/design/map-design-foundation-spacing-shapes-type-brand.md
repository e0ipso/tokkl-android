---
type: map
title: 'Design foundation: spacing, shapes, type roles, option rows, brand and insets'
description: >-
  ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles,
  option rows and Brand, plus the edge-to-edge and keyboard setup.
tags:
  - ui
  - theme
  - accessibility
  - android
kk_schema_version: 3
kk_id: map-design-foundation-spacing-shapes-type-brand
kk_derived_from: []
kk_relates_to:
  - map-tokkl-colour-tokens-and-font
  - practice-tokkl-palette-wcag-english-strings
kk_depends_on: []
kk_confidence: high
---
`ui/theme/Spacing.kt` is Tokkl's spacing scale, `--space-1` to `--space-8` (4 to 48dp), read as `TokklTheme.spacing`. Every gap, padding and margin on a screen is one of its values or named rhythms such as `betweenGroups`, `inGroup` and `underTopBar`. `Modifier.screenContent()` lays out a screen's single column: a 16dp margin on each side when the width the screen gets is under 600dp, 24dp from there, and at most 600dp of content, centred. It reads the available width, not the window's, so it needs a bounded width.

`ui/theme/Shape.kt` passes Tokkl's corners to `MaterialTheme`: `--radius-md`, 10dp, for fields and menus, and `--radius-lg`, 16dp, for cards, sheets and dialogs. Shapes set corners only, and a card or dialog draws Tokkl's 2dp frame (`FrameWidth`) itself. `ui/theme/Type.kt` sizes Material's roles from Tokkl's web scale. Display, headline and title roles are Anybody at width 85, and body and label roles keep the system font. Anybody has its own `Font` entries for weights 800 and 900, because the variable font's default instance is Thin and a role asking for 900 would get 800. The named styles include `ClockTextStyle` (36sp), `PageTitleTextStyle` and `TotalTextStyle` (28sp) and `DayTotalTextStyle` (22sp); the number styles use tabular figures.

`ui/PageTitle.kt` sets a screen's title in the page-title role, in capitals that are drawn and not read: TalkBack gets the string resource as written, in sentence case, as a heading. Keep page title strings in sentence case. Every section heading takes the section-heading role. `ui/Buttons.kt` has `TokklButton`, the filled button for a screen's one call to action, and `TokklGhostButton`, text inside Tokkl's 2dp frame with no fill. Both trust prompt choices are ghost buttons. Each is at least 48dp high.

`ui/OptionRow.kt` has `RadioOptionRow` and `CheckboxOptionRow`, which sign-in, Settings, the reports range and the http warning use. The whole row is the touch target, at least 48dp high, `selectable` or `toggleable` with the radio button or checkbox role, and 12dp sits between control and label. The control inside gets a null handler, so TalkBack reads one node. Put the radio rows of one choice in a parent with `selectableGroup`.

`ui/Brand.kt` draws the owner's clock O wordmark alone, in one size, `BrandSize.LARGE`, 260dp wide, the lg of Tokkl's `Brand.tsx`. The sm and md sizes were removed because the wordmark has no screen outside sign-in and the splash. The clock in place of the "o" is a letter of the wordmark, not the logo, and the logo appears only as the launcher icon. The brand is one semantics node, read once as "Tokkl". In dark mode a 1dp halo in `--wordmark-halo` traces the wordmark. The images come from Tokkl's clock master at tokkl df6711b, cropped by the rule of Tokkl's `WORDMARK_CROP` to 2059x769, and `res/raw/brand_images_source.txt` records the ImageMagick steps. Each width ships once, as `res/drawable-nodpi/tokkl_wordmark_<width>px.png`. `brand_images.xml` in `res/values` and in each `values-<density>` folder points `tokkl_wordmark_lg` at the width for that density, so no size draws scaled down from a larger one. Brand.kt asks only for those alias names. With no PNG in a drawable density folder, lint's IconDuplicates and IconDensities checks have nothing to flag.

`MainActivity` calls `WindowCompat.enableEdgeToEdge` and sets the system bar icon colours itself in `showSystemBarIconsFor`. androidx.activity 1.13's `enableEdgeToEdge` reapplies its first call's styles after every configuration change, which gave dark mode the wrong status bar icons. With `SystemBarStyle.auto` it also turns on the system's navigation bar contrast enforcement, which paints a translucent band over a three-button navigation bar; no scrim colour causes that band. `ScrollingScreenInsets` in `ui/theme/ScreenInsets.kt` keeps the status bar and the sides outside a scrolling screen, and the screen puts `navigationBarsPadding` inside its scroll, so text scrolls under the transparent navigation bar and the last line can still scroll clear of it. `MainActivity` declares `android:windowSoftInputMode="adjustResize"`, so Android does not pan the window when the keyboard opens, and a form makes room with `imePadding`.

`licences/LicenceReflow.kt` joins the OFL's hard-wrapped lines at display time and leaves out its dash rules, which the screen draws as dividers. `res/raw/anybody_ofl.txt` stays byte-identical to Tokkl's `OFL.txt`, so never rewrap the file itself.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-colour-tokens-and-font](map-tokkl-colour-tokens-and-font.md)
- Related: [practice-tokkl-palette-wcag-english-strings](practice-tokkl-palette-wcag-english-strings.md)
<!-- kk:related:end -->
