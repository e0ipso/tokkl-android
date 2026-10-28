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

Tokkl adds `role` and `time_resolution_minutes` to `GET /api/v9/me`, and the server snaps stopped entries to that resolution. Tokkl-only endpoints outside the Toggl API cover the trash (`GET/DELETE /api/app/trash`, `DELETE /api/app/trash/{id}`), undoing a delete (`POST /api/app/time_entries/{id}/restore`) and `GET /api/app/bounds`, the account's first and last day with an entry, which tells the history where it ends. Favorites are a Tokkl key in `/me/preferences`, up to 9 of them. So is `timerView`, the Timer page's List or Calendar choice, which Tokkl accepts only as `list` or `calendar`. Clients write preferences with `POST /api/v9/me/preferences`.

A change Tokkl refuses comes back as a 4xx whose body is a JSON string with the reason, such as "Name has already been taken". The tests also call `POST /api/app/setup` to create the first account and `POST /api/app/report`, the web app's own report call.

Report requests take the web app's filter fields: `project_ids`, `client_ids` and `tag_ids`, where a `null` in the list stands for none (no project, a project without a client, no tags), and `billable` as true or false. Tokkl reads them the same way on its v3 endpoints and on `/api/app/report`, and Toggl Track also takes `null` for "without". Tokkl also takes `exclude_project_ids`, `exclude_client_ids` and `exclude_tag_ids`, the web app's "Is not"; Toggl Track's Reports API v3 has no negation.

Tokkl trims each tag name a create sends and matches it to an existing tag with SQLite's `COLLATE NOCASE`, which folds ASCII letters only, so an entry created with "work" comes back tagged "Work". The instrumented tests pin Tokkl 1.10.0. Tokkl 1.5.0 stopped a running timer on the grid point nearest the stop; Tokkl 1.3.1 kept the snapped start and rounded the duration up.

Tokkl 1.10.0 added the server-owned timer state; 1.9.0 and earlier answer 404 on all of it. `GET /api/app/timer` returns the state: the running entry in the v9 format, the pause chain (`pause.paused`, `pause.seconds`), the Pomodoro session (`pomodoro`, with `phase`, `transition`, `planned_end`, `discarded_pomodoro`), `server_time` and a `revision`. `GET /api/app/timer/events` streams it as Server-Sent Events: a `timer` event per state and a `keep-alive` event every 25 seconds. Each user may hold 10 streams. The actions are `POST /api/app/timer/pause`, `/resume`, `/discard` and `/api/app/pomodoro`, `/next`, `/end`. Resume, discard, next and end take a `revision`; pause and start do not; all but pause need a JSON object body, so send at least `{}`. Every refusal is a 409 whose body is the current state. Any other error body is a JSON string, a non-integer revision is a 400, and a length outside 1 to 180 (work), 1 to 60 (rest) or 1 to 12 (long rest every) is a 400. The revision goes up when the settled state differs from the last one, which includes a v9 edit of the running entry and a rename of its project, task, client or tag, so a client holding an older revision gets a 409. A bad credential on the stream gets 403 before any event, and a credential that stops working during a stream ends it with no status. Times in `server_time`, `phase` and `discarded_pomodoro` are UTC with milliseconds and `Z`; `running_entry` keeps the v9 format. `discarded_pomodoro` repeats in every later state until the state changes, so a client dedupes by `entry_id`. The contract lives in Tokkl's `docs/PRD.md` section 6.2 at tag `v1.10.0`.

The first version targets Tokkl only. Toggl Track comes later by pointing the app at Toggl's base URL, and that version hides the Tokkl-only features.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
