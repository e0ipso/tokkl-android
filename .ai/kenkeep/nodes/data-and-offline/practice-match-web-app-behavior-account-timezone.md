---
type: practice
title: Match Tokkl web app behavior in the account timezone
description: >-
  Use the web app's time parsing, favorites, grouping and undo, in the account
  timezone read from GET /api/v9/me.
tags:
  - api
  - ui
  - time
kk_schema_version: 3
kk_id: practice-match-web-app-behavior-account-timezone
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - practice-port-tokkl-typescript-with-javascript-semantics
  - map-account-settings-source-and-account-time
kk_depends_on: []
kk_confidence: high
---
The app uses the same time parsing, favorites, grouping and undo as Tokkl's web app, in the account's timezone. Moving between the laptop and the phone then needs no relearning.

Read the timezone, first day of the week and `time_resolution_minutes` from `GET /api/v9/me`, and use the account timezone everywhere (S7). Resolve its name with `time.parseTimeZone`, never `ZoneId.of`. Tokkl accepts what JavaScript's Intl accepts, such as `europe/madrid` in any case and ICU-only names like `IST`, and rejects some names `ZoneId.of` takes, such as `Z` or `GMT+5`. A name it cannot resolve gives null. Never fall back to the device's zone. Screens take the zone, the clock and the dates from `AccountTime`, and `NoDeviceZoneTest` fails on any read of the device's zone (see the account settings node). Show stopped entries as the server returns them, after it snaps them to `time_resolution_minutes` (T3). The phone snaps entries it has not sent yet in the account timezone, so their times do not change when the server answers. History and queue code calls `snapUnsent` in `time/Snap.kt`, never `snapSpan` directly. The server cuts both ends to whole seconds before it snaps (`snapToResolution` in `../tokkl/packages/api/src/models/timeEntries.ts`), and `snapUnsent` does the same. With no `time_resolution_minutes` in `/me` it returns the span unchanged. Never pass it the running entry, whose clock is not snapped.

The entry form accepts times like `9`, `930`, `9:30` and `4pm`, entries across midnight, and durations like `1h 30m`, `90` and `1,5h` (M1). An end earlier on the clock than the start falls on the next day, so 23:00 to 01:00 crosses midnight, as in Toggl's manual mode. For 12-hour users a start without AM or PM is the one nearer to now, and an end without them the first one after the start. The parsing is a Kotlin port of Tokkl's `parseDuration` in `../tokkl/packages/common/duration.ts` and `parseTimeOfDay`, now in `../tokkl/packages/common/timeOfDay.ts`. Both ports live in `com.github.e0ipso.tokkl.time`, and their tests copy Tokkl's cases verbatim at a recorded tokkl commit. The ports can drift when the TypeScript changes. Tokkl now shares its cases as JSON fixtures, which the porting practice node covers.

Favorites are the same ones the web app shows, read from and written to `/me/preferences`, up to 9 of them.

Write durations in the account's `duration_format` and clock times in its `timeofday_format`, both from `GET /api/v9/me/preferences`, through the `formatDuration` and `formatTime` ports. The history groups entries by day as the web app's `EntryList.tsx` does, and within a day it puts similar entries in one row when the account's `collapseTimeEntries` preference is on, which it is when the key is missing. Delete offers Undo as the web app does, where the server can restore an entry. The Timer page's List or Calendar choice lives in Tokkl's `timerView` preference, so it follows the user from one device to the other.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [practice-port-tokkl-typescript-with-javascript-semantics](practice-port-tokkl-typescript-with-javascript-semantics.md)
- Related: [map-account-settings-source-and-account-time](map-account-settings-source-and-account-time.md)
<!-- kk:related:end -->
