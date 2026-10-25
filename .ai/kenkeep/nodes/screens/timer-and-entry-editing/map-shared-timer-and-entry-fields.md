---
type: map
title: Shared timer and entry fields
description: >-
  Responsive field rows, picker dialogs and accessibility are shared between the
  timer and entry form.
tags:
  - ui
  - timer
  - accessibility
kk_schema_version: 3
kk_id: map-shared-timer-and-entry-fields
kk_derived_from: []
kk_relates_to:
  - map-timer-controls-and-cached-start-suggestions
kk_depends_on: []
kk_confidence: high
---
`entry/EntryFieldsEditor` is the field row the timer and the entry form share: the description, a project and task button and a tags button that both open `picker/EntityPicker` in a full-screen dialog, and billable. It wraps as the web app's bar does, with the description at least `DescriptionMinWidth`, 280dp, wide. While the description has focus it keeps what is typed whatever its input says, and it hands the text on when focus leaves or the keyboard's Done is pressed. A picked field is drawn in primary and an empty one in the outline colour, and TalkBack reads an empty one as "Project: none" or "Tags: none".

<!-- kk:related:start -->
# Related

- Related: [map-timer-controls-and-cached-start-suggestions](map-timer-controls-and-cached-start-suggestions.md)
<!-- kk:related:end -->
