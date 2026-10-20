---
type: map
title: 'Android stack: Compose, Room, WorkManager and OkHttp built; Glance and tile next'
description: >-
  Built: Compose Material 3, the Room cache and queue, WorkManager's background
  send, OkHttp with pinning, the ports. Not yet: Glance, the tile.
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
kk_confidence: high
---
Section 6 of `docs/PRD.md` opens with what is built and what is not. Nothing in the stack is proposed any more.

- Kotlin and Jetpack Compose with Compose Material 3. Built. Compose is not available from Java.
- Room (SQLite) for the cache and the queue of unsent changes. Built, through KSP, with `CacheDatabase` at version 4.
- WorkManager to send the queue when the network comes back, also with the app closed. Built at 2.12.0 in `cache/BackgroundSend.kt`. Its manifest adds `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` to the app's permissions.
- OkHttp and kotlinx.serialization for the API, with a trust manager that adds the pinned self-signed certificate (S3). Built.
- Kotlin ports from `../tokkl`, all built. In `com.github.e0ipso.tokkl.time`: `parseDuration` and `formatDuration` from `packages/common/duration.ts`, `parseTimeOfDay` from `packages/common/timeOfDay.ts`, `snapSpan` from `packages/common/snap.ts`, `formatTime` from `packages/web/src/lib/settings.ts`, and `zonedToUtc` from `packages/common/time.ts`. In `history/HistoryRows.kt`: `similarityKey` and `rowsForDay` from `packages/web/src/components/EntryList.tsx`. `formatTime` and the grouping live in the web package. Each time port reuses the cases of its source's test file, except `formatTime`, which has none there, so `FormatTimeTest` holds cases of its own. The porting practice node says where the cases come from now.
- Jetpack Glance for the home screen widget. In `gradle/libs.versions.toml` at 1.2.0, but no module uses it yet.
- A `TileService` for the Quick Settings tile. Not built.
- The running-timer notification and the running entry's check every 15 minutes (PRD 6.3) are not built either.

Every dependency must be free software.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
