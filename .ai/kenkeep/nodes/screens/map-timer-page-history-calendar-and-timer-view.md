---
type: map
title: 'Timer page: the List history, the Calendar week and the timerView choice'
description: >-
  timer/, history/ and calendar/: the cached two weeks, older days paged from
  the server, the week grid, and List or Calendar kept in Tokkl.
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
kk_depends_on: []
kk_confidence: high
---
`timer/TimerScreen.kt` is the Timer page, the first page after sign-in. As on the web app's `TimerPage.tsx`, it has the timer, then a List and Calendar switch, then the entries in the chosen view. The timer is not built yet (TKA-0039), so the brand still stands in its place. TKA-0182 replaces that with a page title.

`timer/TimerViewStore` keeps the List or Calendar choice in Tokkl's `timerView` preference, as the web app does, so a choice made on one device opens on the others. A tap shows the new view at once and posts it to `POST /api/v9/me/preferences` without waiting. When that fails, as offline, the choice stays on the phone marked unsent and goes to the server with the next refresh, so the last choice to reach the server wins. Each refresh otherwise opens the server's `timerView`, and List when the key is missing or holds anything else. The one refresh right after a tap is the exception, since its preferences may predate the tap. The phone also keeps the choice in a private preferences file, so a cold start opens it before the refresh answers, and offline. `Capabilities.favorites` decides whether the server keeps Tokkl's preferences at all. Toggl Track does not, and there the choice stays on the phone. Tokkl itself accepts only `list` and `calendar` as values.

`history/` is the List view (`docs/PRD.md` H1). `HistoryDays` groups stopped entries under the day of their start in the account's timezone, newest first, as the web app's `EntryList.tsx` does. The running entry stays out, since the timer shows it. An entry with a change the queue has not sent carries a "Not sent" label. `HistoryModel` shows the cached two weeks from the phone and pages older days from the server, 14 days a page (`PAGE_DAYS`), when the end of the list shows. On Tokkl, `GET /api/app/bounds` says where the account's history starts, so the list knows when no older day has entries. Against a server without it, the list pages until a page comes back empty, and after that loads more only on a tap. The cache's window moves with the date, and the days a refresh drops from it stay in the list as the cache last held them, with no request. Older days live in memory only.

`calendar/` is the Calendar view (K1). `WeekCalendar` draws the week from the account's first day of the week, one block per entry. On a compact window, a phone held upright, it shows the chosen day alone. Today's week and the week before come from the cache, and `CalendarModel` loads any other week from the server. The server finds entries by their start, so a week asks for entries from `LONGEST_ENTRY` before it, 999 hours, Tokkl's longest stopped entry, and keeps those that overlap it. For the two cached weeks, the entries that start before the cache and run into it load once for both. Loaded weeks go stale when the app comes back to the foreground: `MainActivity.onStop` calls `wentToBackground()` unless the activity is changing configuration, and `onStart` calls `cameToForeground()`. An unsent entry's block shows the "Not sent" label where the whole label fits, and the unsent icon otherwise. `CalendarTimeZoneTest` runs the device in Los Angeles and the account in Tokyo to catch a block placed in the device's zone.

Both views write clock times through `time/FormatTime.kt` in the account's 12-hour or 24-hour format.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities](../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-change-queue-sends-entry-changes-in-order](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md)
- Related: [practice-check-capabilities-not-server-version](../api-and-security/practice-check-capabilities-not-server-version.md)
- Related: [map-account-settings-source-and-account-time](../data-and-offline/map-account-settings-source-and-account-time.md)
<!-- kk:related:end -->
