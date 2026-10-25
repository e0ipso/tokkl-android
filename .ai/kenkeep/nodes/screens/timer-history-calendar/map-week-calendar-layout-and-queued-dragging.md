---
type: map
title: Week calendar layout and queued dragging
description: >-
  Adaptive week blocks, accessible navigation and snap-grid dragging operate on
  cached entries.
tags:
  - ui
  - timer
  - cache
  - accessibility
kk_schema_version: 3
kk_id: map-week-calendar-layout-and-queued-dragging
kk_derived_from:
  - 'b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0'
kk_relates_to:
  - map-offline-entry-mutations-and-local-id-mapping
  - map-account-settings-source-and-account-time
  - map-ui-components-and-compose-layout-gotchas
  - practice-match-web-app-behavior-account-timezone
kk_depends_on: []
kk_confidence: medium
---
`calendar/` is the Calendar view (K1). `WeekCalendar` draws the week from the account's first day of the week, one block per entry. On a compact width, a phone held upright, it shows the chosen day alone under a strip of its week's seven days with their totals. A tap on a strip day calls `CalendarModel.select`, and the arrows and a swipe move a day instead of a week. TalkBack reads each strip day as a tab with its date and total, and the chosen day and today are marked by a frame and a filled circle, not by colour alone. The day slides in from the side it comes from, unless the system's Remove animations has set the animator duration scale to 0, which `reducedMotion` reads. Today's week and the week before come from the cache, and `CalendarModel` loads any other week from the server. The server finds entries by their start, so a week asks for entries from `LONGEST_ENTRY` before it, 999 hours, Tokkl's longest stopped entry, and keeps those that overlap it. For the two cached weeks, the entries that start before the cache and run into it load once for both. Loaded weeks go stale when the app comes back to the foreground: `MainActivity.onStop` calls `wentToBackground()` unless the activity is changing configuration, and `onStart` calls `cameToForeground()`. A long press then a drag creates an entry on empty space (K2), moves a block, or moves one end from the block's top or bottom edge, in steps of `dragStep` in `calendar/CalendarDrag.kt`: the smallest multiple of the time resolution that is at least 15 minutes, a port of `../tokkl/packages/web/src/lib/calendarDay.ts`. The running entry's block is not draggable. Each drag goes to the change queue. An unsent entry's block shows the "Not sent" label where the whole label fits, and the unsent icon otherwise. `CalendarTimeZoneTest` runs the device in Los Angeles and the account in Tokyo to catch a block placed in the device's zone.

Both views write clock times through `time/FormatTime.kt` in the account's 12-hour or 24-hour format.

<!-- kk:related:start -->
# Related

- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-account-settings-source-and-account-time](../../data-and-offline/map-account-settings-source-and-account-time.md)
- Related: [map-ui-components-and-compose-layout-gotchas](../../design/map-ui-components-and-compose-layout-gotchas.md)
- Related: [practice-match-web-app-behavior-account-timezone](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0
<!-- kk:citations:end -->
