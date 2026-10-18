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

Manual entry accepts times like `9`, `930`, `9:30` and `4pm`, entries across midnight, and durations like `1h 30m`, `90` and `1,5h` (M1). The parsing is a Kotlin port of Tokkl's `parseDuration` in `../tokkl/packages/common/duration.ts` and `parseTimeOfDay` in `../tokkl/packages/web/src/lib/timeOfDay.ts`, checked against the cases in the `.test.ts` file next to each. The time-of-day parser lives in the web package, not in `packages/common`. Both ports live in `com.github.e0ipso.tokkl.time`, and their tests copy the TypeScript cases verbatim at a recorded tokkl commit (see the porting practice node). The ports can still drift when the TypeScript changes later. Sharing the test cases between the two repos would catch that.

Favorites are the same ones the web app shows, read from and written to `/me/preferences`, up to 9 of them.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
