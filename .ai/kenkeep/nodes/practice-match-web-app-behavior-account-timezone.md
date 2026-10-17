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
kk_relates_to: []
kk_depends_on: []
kk_confidence: high
---
The app uses the same time parsing, favorites, grouping and undo as Tokkl's web app, in the account's timezone. Moving between the laptop and the phone then needs no relearning.

Read the timezone, first day of the week and `time_resolution_minutes` from `GET /api/v9/me`, and use the account timezone everywhere (S7). Show stopped entries as the server returns them, after it snaps them to `time_resolution_minutes` (T3).

Manual entry accepts times like `9`, `930`, `9:30` and `4pm`, entries across midnight, and durations like `1h 30m`, `90` and `1,5h` (M1). The parsing is a Kotlin port of the duration and time-of-day parsing in `../tokkl/packages/common`, checked against the same cases as the TypeScript tests. The port can drift from the TypeScript original. Sharing the test cases between the two repos would catch that.

Favorites are the same ones the web app shows, read from and written to `/me/preferences`, up to 9 of them.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
