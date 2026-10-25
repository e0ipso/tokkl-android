---
type: practice
title: Keep upstream golden cases with source-pinned ports
description: >-
  Replay upstream fixtures and preserve source-specific stop, calendar and
  history semantics.
tags:
  - time
  - testing
  - tokkl
kk_schema_version: 3
kk_id: practice-keep-upstream-golden-cases-with-source-pinned-ports
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0'
kk_relates_to:
  - practice-match-web-app-behavior-account-timezone
kk_depends_on: []
kk_confidence: high
---
`DurationTest`, `TimeOfDayTest`, `SnapTest` and `ZonedTimeTest` hold the cases of the matching `.test.ts` file verbatim, and their header names the tokkl commit they were copied at. To follow an upstream change, copy the cases again and update that commit. Since tokkl a5cadd1 (2026-10-18) Tokkl keeps the cases of `parseDuration`, `parseTimeOfDay` and `snapSpan` as JSON in `../tokkl/packages/common/fixtures/`, which its README offers to ports in other repos, and its `.test.ts` files only loop over them. Copy the shared JSON cases when refreshing a port. `TimeOfDayTest` holds `fixtures/timeOfDay.json` in upstream order, and `TimeOfDay.kt` names `packages/common/timeOfDay.ts`. `SnapTest` holds `fixtures/snap.json` and the `snapInstant` cases from `snap.test.ts` at the commit recorded in its header. `snapSpan` and `snapUnsent` return nullable spans: independently round endpoints to the nearest account wall-clock grid point, choose the later point on a tie, and return null below half a step or for coincident snapped endpoints. Grid points use their own UTC offsets across DST changes. `DurationJsSemanticsTest` pins the JavaScript traps in its own file, since they are not in `duration.test.ts` and a later diff against tokkl leaves them alone. Its expected values come from running the TypeScript file in Node 24, where `node file.mts` strips the types.

Ports outside `time/` follow the same rule, each with a header comment that names its source and tokkl commit and a test that replays the source's cases: `stopSpan` and `snapStop` in `time/Snap.kt` retain the legacy `stopEntryAt` predictor for migrated queued requests and switch reconciliation, with cases in `StopSpanTest`; new Stops use `AccountTime.snapCapturedStop`, the split in `entry/SplitEntry.kt` (`splitHalves` in `EntryEditor.tsx`, `snappedSplit` in `lib/settings.ts`, `SplitEntryTest`), the calendar drags in `calendar/CalendarDrag.kt` (`dragStep` and friends in `lib/calendarDay.ts`, `CalendarDragTest`), and the description suggestions in `entry/DescriptionSuggestions.kt` (`suggestionsFrom` in `TimerBar.tsx`).

`history/HistoryRows.kt` ports `similarityKey` and `rowsForDay` from `../tokkl/packages/web/src/components/EntryList.tsx`. The key joins the fields as `Array.join` does, a null as an empty string and the tags with commas, so a tag named "a,b" matches the tags "a" and "b", as on the web app.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](../practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0
<!-- kk:citations:end -->
