---
type: map
title: 'Tokkl and Toggl API endpoints, auth and Tokkl-only extensions'
description: >-
  Tokkl speaks a subset of Toggl API v9 at /api/v9 and Reports v3 at
  /reports/api/v3 with Basic auth, plus Tokkl-only fields and endpoints.
tags:
  - api
  - toggl
  - network
kk_schema_version: 3
kk_id: map-tokkl-toggl-api
kk_derived_from:
  - docs/PRD.md
kk_relates_to: []
kk_depends_on: []
kk_confidence: high
---
Tokkl implements a subset of the Toggl Track API v9 at `https://<server>/api/v9` and of the Reports API v3 at `https://<server>/reports/api/v3`. A client that accepts a base URL needs no other change to talk to it. The server URL may carry a path, such as `https://example.com/tokkl`. Toggl Track's API lives at `api.track.toggl.com`.

Both APIs use HTTP Basic auth with `<api_token>:api_token` or `<email>:<password>`. The API token is the default sign-in (S1). Failed auth returns 403. Tokkl returns 429 with `Retry-After` after too many failed password attempts.

Tokkl adds `role` and `time_resolution_minutes` to `GET /api/v9/me`, and the server snaps stopped entries to that resolution. Tokkl-only endpoints outside the Toggl API cover the trash (`GET/DELETE /api/app/trash`, `DELETE /api/app/trash/{id}`) and undoing a delete (`POST /api/app/time_entries/{id}/restore`). Favorites are a Tokkl key in `/me/preferences`, up to 9 of them.

The first version targets Tokkl only. Toggl Track comes later by pointing the app at Toggl's base URL, and that version hides the Tokkl-only features.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
