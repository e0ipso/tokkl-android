---
type: map
title: Tokkl favorites storage and capability
description: >-
  Favorite starts share cached fields while preference writes use supported
  server capabilities.
tags:
  - ui
  - timer
  - api
kk_schema_version: 3
kk_id: map-tokkl-favorites-storage-and-capability
kk_derived_from: []
kk_relates_to:
  - practice-check-capabilities-not-server-version
  - map-timer-controls-and-cached-start-suggestions
kk_depends_on: []
kk_confidence: high
---
`favorites/` holds Tokkl's favorites (T5), up to Tokkl's `MAX_FAVORITES`, the same list the web app shows. They live in the `favorites` key of `/me/preferences` (the `Favorite` type in `../tokkl/packages/types/index.ts`) and are cached in Room, so they show and start offline. Adding, removing and moving one needs the server: `FavoritesServer.save` posts the `favorites` key alone to `POST /api/v9/me/preferences` and then replaces the cache, and no other change starts until it answers. `FavoritesModel.add` takes `onDone`, which gets null once the server has stored the list or the reason nothing was pinned. Without `Capabilities.favorites`, as on Toggl Track, the page shows no favorites control.

<!-- kk:related:start -->
# Related

- Related: [practice-check-capabilities-not-server-version](../../api-and-security/practice-check-capabilities-not-server-version.md)
- Related: [map-timer-controls-and-cached-start-suggestions](map-timer-controls-and-cached-start-suggestions.md)
<!-- kk:related:end -->
