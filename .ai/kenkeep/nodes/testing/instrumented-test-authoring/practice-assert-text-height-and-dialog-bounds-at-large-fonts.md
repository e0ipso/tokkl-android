---
type: practice
title: Assert text height and dialog bounds at large fonts
description: >-
  Compose text and dialog acceptance must detect real wrapping, overflow and
  overlap.
tags:
  - testing
  - android
  - accessibility
kk_schema_version: 3
kk_id: practice-assert-text-height-and-dialog-bounds-at-large-fonts
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3'
kk_relates_to:
  - practice-screenshot-references-on-tokkl-api36
kk_depends_on: []
kk_confidence: high
---
To catch clipped text in a Compose test, check `TextLayoutResult.didOverflowHeight`. Semantics hands out a layout redone at the full available width, so `didOverflowWidth` is true for any text narrower than its container. `LargestFontScaleTest` checks height for that reason. A single line of Compose text keeps the font's ascent above it and its descent below it whatever `lineHeight` the style sets, so a line height that is too short only shows between lines. `ClockAndTotalStylesFitTest` puts its samples in a column narrower than the text, so they wrap. For dialogs, `LargestFontScaleTest.assertDialogFits` checks that the dialog stays in its window and that its scrolling area, title and buttons do not overlap. A test there marked `@RefusedSession` starts signed in to a server whose requests wait for a trust answer.

<!-- kk:related:start -->
# Related

- Related: [practice-screenshot-references-on-tokkl-api36](../practice-screenshot-references-on-tokkl-api36.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3
<!-- kk:citations:end -->
