---
type: map
title: 'Planned stack: Compose, Glance, TileService, Room, WorkManager, OkHttp'
description: >-
  Proposed stack: Kotlin and Compose Material 3, Glance widget, TileService
  tile, Room cache and queue, WorkManager, OkHttp.
tags:
  - architecture
  - android
  - offline
kk_schema_version: 3
kk_id: map-planned-android-stack
kk_derived_from:
  - docs/PRD.md
kk_relates_to: []
kk_depends_on: []
kk_confidence: low
---
The PRD marks this stack as proposed.

- Kotlin and Jetpack Compose with Compose Material 3. Compose is not available from Java.
- Jetpack Glance for the home screen widget.
- A `TileService` for the Quick Settings tile.
- Room (SQLite) for the cache and the queue of unsent changes.
- WorkManager to send the queue when the network comes back.
- OkHttp and kotlinx.serialization for the API, with a trust manager that adds the pinned self-signed certificate (S3).
- A Kotlin port of three functions from `../tokkl`, each checked against the cases in the `.test.ts` file next to it: `parseDuration` in `packages/common/duration.ts`, `parseTimeOfDay` in `packages/web/src/lib/timeOfDay.ts` and `snapSpan` in `packages/common/snap.ts`. The time-of-day parser lives in the web package, not in `packages/common`.

Every dependency must be free software.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
