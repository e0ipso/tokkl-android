---
type: map
title: 'Timer page: layout, the List history, the Calendar week and timerView'
description: >-
  timer/, history/ and calendar/: the page's scroll-away top, grouped days,
  older days from the server, the week grid and day strip, List or Calendar.
tags:
  - ui
  - history
  - calendar
  - offline
  - api
kk_schema_version: 3
kk_id: map-timer-page-history-calendar-and-timer-view
kk_derived_from: []
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-change-queue-sends-entry-changes-in-order
  - practice-check-capabilities-not-server-version
  - map-account-settings-source-and-account-time
  - map-timer-entry-form-and-entry-menu
  - map-ui-components-and-compose-layout-gotchas
kk_depends_on: []
kk_confidence: high
---
`timer/TimerScreen.kt` is the Timer page, the first page after sign-in. As on the web app's `TimerPage.tsx`, it has the timer, then a List and Calendar switch, then the entries in the chosen view, under the top bar with the page's title. The timer, the entry form and the history's entry menu have their own node. The list and the calendar each fill the rest of the page with a bounded height, so each scrolls on its own. On a window of compact height (`compactHeightWindow()`, which divides the height by the font scale), the top bar, the timer and the switch scroll away as the entries scroll on, through `ScrollAwayColumn`, and `TopBarStatusBand` keeps the status bar's colour. On a phone held sideways (`shortWindow()`, under 480dp tall), the Calendar view opens with all of that and the calendar's toolbar already scrolled away, so the grid shows at least four hour rows at once. `LandscapeCalendarTest` checks this with a single swipe from the bottom of the page to the top of the window, so anything that makes the page's top taller than that swipe fails it.

`timer/TimerViewStore` keeps the List or Calendar choice in Tokkl's `timerView` preference, as the web app does, so a choice made on one device opens on the others. A tap shows the new view at once and posts it to `POST /api/v9/me/preferences` without waiting. When that fails, as offline, the choice stays on the phone marked unsent and goes to the server with the next refresh, so the last choice to reach the server wins. Each refresh otherwise opens the server's `timerView`, and List when the key is missing or holds anything else. The one refresh right after a tap is the exception, since its preferences may predate the tap. The phone also keeps the choice in a private preferences file, so a cold start opens it before the refresh answers, and offline. `Capabilities.favorites` decides whether the server keeps Tokkl's preferences at all. Toggl Track does not, and there the choice stays on the phone. Tokkl itself accepts only `list` and `calendar` as values.

`history/` is the List view (`docs/PRD.md` H1). `HistoryDays` groups stopped entries under the day of their start in the account's timezone, newest first, as the web app's `EntryList.tsx` does. The running entry stays out, since the timer shows it. Within a day, `rowsForDay` in `history/HistoryRows.kt` puts entries with the same `similarityKey` (description, project, task, billable and sorted tags) in one row, placed where its newest member falls, with their count and total, and the row expands to its members. It does so only while the account's `collapseTimeEntries` preference is on. A row keeps the oldest member's id as its key, as `EntryList.tsx` does, so it keeps its key when a newer entry joins. A tap on an entry opens the entry form, and its menu duplicates or deletes it. An entry with a change the queue has not sent carries a "Not sent" label. `HistoryModel` shows the cached two weeks from the phone and pages older days from the server, 14 days a page (`PAGE_DAYS`), when the end of the list shows. On Tokkl, `GET /api/app/bounds` says where the account's history starts, so the list knows when no older day has entries. Against a server without it, the list pages until a page comes back empty, and after that loads more only on a tap. The cache's window moves with the date, and the days a refresh drops from it stay in the list as the cache last held them, with no request. Older days live in memory only.

`calendar/` is the Calendar view (K1). `WeekCalendar` draws the week from the account's first day of the week, one block per entry. On a compact width, a phone held upright, it shows the chosen day alone under a strip of its week's seven days with their totals. A tap on a strip day calls `CalendarModel.select`, and the arrows and a swipe move a day instead of a week. TalkBack reads each strip day as a tab with its date and total, and the chosen day and today are marked by a frame and a filled circle, not by colour alone. The day slides in from the side it comes from, unless the system's Remove animations has set the animator duration scale to 0, which `reducedMotion` reads. Today's week and the week before come from the cache, and `CalendarModel` loads any other week from the server. The server finds entries by their start, so a week asks for entries from `LONGEST_ENTRY` before it, 999 hours, Tokkl's longest stopped entry, and keeps those that overlap it. For the two cached weeks, the entries that start before the cache and run into it load once for both. Loaded weeks go stale when the app comes back to the foreground: `MainActivity.onStop` calls `wentToBackground()` unless the activity is changing configuration, and `onStart` calls `cameToForeground()`. An unsent entry's block shows the "Not sent" label where the whole label fits, and the unsent icon otherwise. `CalendarTimeZoneTest` runs the device in Los Angeles and the account in Tokyo to catch a block placed in the device's zone.

Both views write clock times through `time/FormatTime.kt` in the account's 12-hour or 24-hour format.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities](../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-change-queue-sends-entry-changes-in-order](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md)
- Related: [practice-check-capabilities-not-server-version](../api-and-security/practice-check-capabilities-not-server-version.md)
- Related: [map-account-settings-source-and-account-time](../data-and-offline/map-account-settings-source-and-account-time.md)
- Related: [map-timer-entry-form-and-entry-menu](map-timer-entry-form-and-entry-menu.md)
- Related: [map-ui-components-and-compose-layout-gotchas](../design/map-ui-components-and-compose-layout-gotchas.md)
<!-- kk:related:end -->
