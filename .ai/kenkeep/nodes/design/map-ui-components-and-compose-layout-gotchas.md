---
type: map
title: >-
  UI components: ErrorBanner, Scene, ScrollAwayColumn, and Compose layout
  gotchas
description: >-
  Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard
  and a dialog that lays itself out on every frame.
tags:
  - ui
  - android
  - accessibility
  - compose
kk_schema_version: 3
kk_id: map-ui-components-and-compose-layout-gotchas
kk_derived_from: []
kk_relates_to:
  - map-design-foundation-spacing-shapes-type-brand
  - map-signin-session-settings-and-navigation-host
kk_depends_on: []
kk_confidence: high
---
`ui/ErrorBanner.kt` shows an error as a banner: the warning icon `ic_warning.xml` beside danger text on the danger-soft surface, in the card shape with a 2dp frame in danger. The icon and the frame mark the error along with the colour, and the message is a polite live region, so TalkBack announces a new one. Sign-in, the catalog pages and their dialogs show server problems in it.

`ui/Scene.kt` draws the scene that Tokkl's `styles.css` puts behind its auth page, from the scene tokens: a sunset glow from the top left corner, the swell from the bottom right, and diagonal stripes on the background. Only the brand sits on the scene. Sign-in puts its form on a surface card above it, because the PRD keeps texture away from inputs and data.

On sign-in, each field's `SupportingLine` always takes the height of the tallest of its hint and every error the field can show, at the current width and font scale. Showing or clearing an error then never moves the fields below. The lines not shown are laid out invisible and left out of semantics.

Compose keeps a focused field in view when the keyboard opens only if the field was wholly inside the scroll area before and no other scroll was running. On tokkl-api36 at font scale 2.0, a password field that ended within 1dp of the bottom edge stayed under the keyboard. `keptAboveKeyboard()` in `signin/SignInScreen.kt` brings the focused field and its supporting line into view one frame after each change of the keyboard's height. A form low on a scrolling screen needs the same.

`ui/ScrollAwayColumn.kt` puts a top part over a body that takes the rest of the height. On a window of compact height, under 480dp as Material's window size classes count, such as a phone held sideways, the top scrolls away as soon as the body scrolls on and comes back once the body is back at its start. The Timer page's brand and view switch use it, and so does the week calendar inside the Calendar view. Nesting order matters: a `ScrollAwayColumn` inside the body goes after the outer top and comes back before it.

On Android 11 a Compose `Dialog` with `usePlatformDefaultWidth = false` keeps its window at WRAP_CONTENT height, and Android measured it twice in every pass, at 1079 and at 1080 px on a 1080 px screen. With the date range picker inside, the dialog laid itself out on every frame and the main thread never went idle. `FillScreenWindow()` in `reports/RangeControl.kt` sets the window to MATCH_PARENT, which ends it. Give any full-width dialog the same.

The bottom bar's labels share one font size through `SharedLabelSize` in `ui/theme/NavigationBar.kt`. `TextAutoSizeLayoutScope` lays out only the item's own text, so `SharedLabelSize` measures the other labels with a `TextMeasurer`.

<!-- kk:related:start -->
# Related

- Related: [map-design-foundation-spacing-shapes-type-brand](map-design-foundation-spacing-shapes-type-brand.md)
- Related: [map-signin-session-settings-and-navigation-host](../screens/map-signin-session-settings-and-navigation-host.md)
<!-- kk:related:end -->
