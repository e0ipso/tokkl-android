---
type: map
title: Cached history days and grouping
description: >-
  Account-local history groups and pages cached stopped entries with
  reconnect-aware retention.
tags:
  - ui
  - timer
  - cache
  - time
kk_schema_version: 3
kk_id: map-cached-history-days-and-grouping
kk_derived_from:
  - 'b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0'
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-account-settings-source-and-account-time
  - practice-match-web-app-behavior-account-timezone
  - map-shared-timer-and-entry-fields
kk_depends_on: []
kk_confidence: medium
---
`history/` is the List view (`docs/PRD.md` H1). `HistoryDays` groups stopped entries under the day of their start in the account's timezone, newest first, as the web app's `EntryList.tsx` does. The running entry stays out, since the timer shows it. Within a day, `rowsForDay` in `history/HistoryRows.kt` puts entries with the same `similarityKey` (description, project, task, billable and sorted tags) in one row, placed where its newest member falls, with their count and total, and the row expands to its members. It does so only while the account's `collapseTimeEntries` preference is on. A row keeps the oldest member's id as its key, as `EntryList.tsx` does, so it keeps its key when a newer entry joins. A tap on an entry opens the entry form, and its menu duplicates or deletes it. An entry with a change the queue has not sent carries a "Not sent" label. `HistoryModel` shows the cached two weeks from the phone and pages older days from the server, 14 days a page (`PAGE_DAYS`), when the end of the list shows. On Tokkl, `GET /api/app/bounds` says where the account's history starts, so the list knows when no older day has entries. Against a server without it, the list pages until a page comes back empty, and after that loads more only on a tap. The cache's window moves with the date, and the days a refresh drops from it stay in the list as the cache last held them, with no request. Older days live in memory only. An older entry edited through the form sits in the cache until the send and the refresh after it, and `HistoryModel` and `CalendarModel` keep the cache's last copy of it, so the edit survives the refresh that prunes it. When the network returns, `reconnected()` on both models loads again only what failed offline.

`ForegroundRefresh.networkChanged` forwards each online network update to the history through its `onOnline` callback while the activity is started. A cellular route can fail before Wi-Fi replaces it without an offline interval, so failed older days must hear that later online update even though the full account refresh stays debounced. `HistoryModel` also remembers an online update arriving during a page request: if that request fails as unreachable, it tries the same page once for the newer event. A successful page never loads another page because of that event, and a background network report loads nothing.

Both views write clock times through `time/FormatTime.kt` in the account's 12-hour or 24-hour format.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities](../../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-account-settings-source-and-account-time](../../data-and-offline/map-account-settings-source-and-account-time.md)
- Related: [practice-match-web-app-behavior-account-timezone](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
- Related: [map-shared-timer-and-entry-fields](../timer-and-entry-editing/map-shared-timer-and-entry-fields.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0
<!-- kk:citations:end -->
