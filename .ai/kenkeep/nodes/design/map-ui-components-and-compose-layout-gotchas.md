---
type: map
title: >-
  UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose
  layout gotchas
description: >-
  Shared ui/ pieces beyond the theme, plus fixes for a field under the
  keyboard, a dialog that never settles and the More sheet's navigation bar.
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
`ui/TopBar.kt` has `TokklTopBar`, the bar at the top of every signed-in page, as a `Scaffold`'s `topBar`. It shows the page's title through `PageTitle`, which TalkBack reads as a heading, on surface, `TopBarHeight` tall or taller at large font scales, with a strip of the texture glow and a 1dp border along its bottom. A page opened from another, such as Licences, a project or client form or the entry form, passes `onBack` for a 48dp back button whose icon lines up with the screen margin. The pages of the bottom bar have none. The bar never shows the wordmark and does not change as the page scrolls, so it has no motion for reduced motion to stop. `TopBarStatusBand` draws the bar's surface under the status bar alone, for a page whose bar scrolls away inside its content, as the Timer page's does on a window of compact height.

`ui/ErrorBanner.kt` shows an error as a banner: the warning icon `ic_warning.xml` beside danger text on the danger-soft surface, in the card shape with a 2dp frame in danger. The icon and the frame mark the error along with the colour, and the message is a polite live region, so TalkBack announces a new one. Sign-in, the catalog pages and their dialogs show server problems in it.

`ui/Scene.kt` draws the scene that Tokkl's `styles.css` puts behind its auth page, from the scene tokens: a sunset glow from the top left corner, the swell from the bottom right, and diagonal stripes on the background. Only the brand sits on the scene. Sign-in puts its form on a surface card above it, because the PRD keeps texture away from inputs and data.

On sign-in, each field's `SupportingLine` always takes the height of the tallest of its hint and every error the field can show, at the current width and font scale. Showing or clearing an error then never moves the fields below. The lines not shown are laid out invisible and left out of semantics.

Compose keeps a focused field in view when the keyboard opens only if the field was wholly inside the scroll area before and no other scroll was running. On tokkl-api36 at font scale 2.0, a password field that ended within 1dp of the bottom edge stayed under the keyboard. `keptAboveKeyboard()` in `signin/SignInScreen.kt` brings the focused field and its supporting line into view one frame after each change of the keyboard's height. A form low on a scrolling screen needs the same.

`ui/ScrollAwayColumn.kt` puts a top part over a body that takes the rest of the height. On a window of compact height the top scrolls away as soon as the body scrolls on and comes back once the body is back at its start. `compactHeightWindow()` decides that: the window's height divided by the font scale is under 480dp, Material's start of medium heights. So a phone held sideways counts, and so does a phone held upright at the largest font scale, where text takes twice the room. `shortWindow()` checks the height alone, for a phone held sideways only. The Timer page's top bar, timer and view switch use the column, and so does the week calendar inside the Calendar view. Nesting order matters: a `ScrollAwayColumn` inside the body goes after the outer top and comes back before it. With `startScrolledAway` the top starts scrolled away the first time the column shows, and a column in the body that first shows then starts with its own top scrolled away too.

A Compose `Dialog` with `usePlatformDefaultWidth = false` keeps its window at WRAP_CONTENT height. Android 11 measured it twice in every pass, at 1079 and at 1080 px on a 1080 px screen, and with the date range picker inside the dialog laid itself out on every frame and the main thread never went idle. Android 12 still never went idle there. `FillScreenWindow()` in `ui/FillScreenWindow.kt` sets the window to MATCH_PARENT, which ends it. Call it first inside any full-width dialog, as the reports' range and filter dialogs, the entry form's date dialog and the picker dialog of `EntryFieldsEditor` do.

`ModalBottomSheet` opens in a window of its own, and that window keeps Android's navigation bar contrast enforcement, which paints a translucent band over a three-button navigation bar. `SheetWindowEdgeToEdge()` in `navigation/AppNavigation.kt` turns it off for the More sheet, so the sheet's own colour shows behind the navigation bar, as every screen's does. Any other sheet needs the same.

The bottom bar's labels share one font size through `SharedLabelSize` in `ui/theme/NavigationBar.kt`. `TextAutoSizeLayoutScope` lays out only the item's own text, so `SharedLabelSize` measures the other labels with a `TextMeasurer`.

<!-- kk:related:start -->
# Related

- Related: [map-design-foundation-spacing-shapes-type-brand](map-design-foundation-spacing-shapes-type-brand.md)
- Related: [map-signin-session-settings-and-navigation-host → map-saveable-navigation-stack-and-session-guard](../screens/session-and-navigation/map-saveable-navigation-stack-and-session-guard.md)
- Related: [map-signin-session-settings-and-navigation-host → map-session-stores-and-pre-frame-preferences](../screens/session-and-navigation/map-session-stores-and-pre-frame-preferences.md)
- Related: [map-signin-session-settings-and-navigation-host → map-sign-in-request-and-credential-admission](../screens/session-and-navigation/map-sign-in-request-and-credential-admission.md)
- Related: [map-signin-session-settings-and-navigation-host → map-sign-out-ordering-and-account-cleanup](../screens/session-and-navigation/map-sign-out-ordering-and-account-cleanup.md)
- Related: [map-signin-session-settings-and-navigation-host → map-signed-in-navigation-chrome-and-adaptive-windows](../screens/session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md)
- Related: [map-signin-session-settings-and-navigation-host → map-trash-capability-and-server-operations](../screens/session-and-navigation/map-trash-capability-and-server-operations.md)
<!-- kk:related:end -->
