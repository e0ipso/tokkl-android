---
type: map
title: 'Android stack: Compose, Room and OkHttp built; Glance, tile and WorkManager next'
description: >-
  Built: Compose Material 3, the Room cache and queue, OkHttp with pinning, the
  time ports. Not yet: Glance, the tile, WorkManager.
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
Section 6 of `docs/PRD.md` still calls this stack proposed and says nothing is built. Part of it is built now.

- Kotlin and Jetpack Compose with Compose Material 3. Built. Compose is not available from Java.
- Room (SQLite) for the cache and the queue of unsent changes. Built, through KSP, with `CacheDatabase` at version 2.
- OkHttp and kotlinx.serialization for the API, with a trust manager that adds the pinned self-signed certificate (S3). Built.
- Kotlin ports from `../tokkl`: `parseDuration` and `formatDuration` in `packages/common/duration.ts`, `parseTimeOfDay` in `packages/web/src/lib/timeOfDay.ts`, `snapSpan` in `packages/common/snap.ts`, and `formatTime` in `packages/web/src/lib/settings.ts`. The time-of-day parser and `formatTime` live in the web package, not in `packages/common`. Each port reuses the cases of the `.test.ts` file next to its source, except `formatTime`, which has none there, so `FormatTimeTest` holds cases of its own. Built, all in `com.github.e0ipso.tokkl.time`.
- Jetpack Glance for the home screen widget. In `gradle/libs.versions.toml` at 1.2.0, but no module uses it yet.
- WorkManager to send the queue when the network comes back. In the catalog at 2.12.0, unused until TKA-0037.
- A `TileService` for the Quick Settings tile. Not built.

Every dependency must be free software.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
