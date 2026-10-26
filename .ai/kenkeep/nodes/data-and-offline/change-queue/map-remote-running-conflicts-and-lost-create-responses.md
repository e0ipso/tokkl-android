---
type: map
title: Remote running conflicts and lost create responses
description: >-
  Captured start conflict handling and duplicate prevention after uncertain POST
  delivery.
tags:
  - offline
  - api
  - queue
kk_schema_version: 3
kk_id: map-remote-running-conflicts-and-lost-create-responses
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:1'
kk_relates_to:
  - map-tokkl-toggl-api
  - map-api-client-shared-okhttp-serverurl-and-errors
  - practice-test-against-a-real-tokkl-server
kk_depends_on: []
kk_confidence: high
---
A queued start goes out as queued unless the server's running entry started after it. The sender reads the running entry first, one GET per start. When that entry is later, the start goes as a stopped entry that ends before the later timer (`ChangeStore.endBefore`), and the later timer keeps running. The sender's `switchSpan` places that end where the server stores a timer that a new start stopped: on Tokkl, both ends on the grid with the stop where the later timer's start snaps, so the two never overlap; on Toggl, the times as they are. Otherwise Tokkl stops its earlier timer itself when the start arrives (O3).

A create reaches the server once, though neither Tokkl nor Toggl takes an idempotency key on it. OkHttp sends a request again on a new connection when a reused connection fails after the request went out, so a POST whose answer was lost could create two entries. `TogglApiClient.send(..., resend = false)` wraps the body as one-shot (`SentOnce`), which OkHttp resends only when the request never left the phone, and the sender posts creates that way. Before sending a create or delete, the sender marks it `PendingChange.maybeSent` on disk. A failure that proves the server stored nothing, a 4xx or no route, clears the mark. Before posting a marked create again, the sender looks among the server's entries within an hour of its start for one that `couldHaveMade` it: same workspace, description, project, task and tags, and a start equal to the create's or to where the server snaps it. Tags compare as Tokkl matches names, trimmed and with ASCII letters in lower case, because an entry queued with the tag "work" comes back tagged "Work" when Tokkl has that tag. It skips the ids in `PendingChange.existingIds`, the server entries the phone held near that start when it queued the create, so the source of a duplicate is never taken for the copy. A marked delete keeps its mark whatever later tries get, and a 404 on a retry counts as sent. Catalog creates (projects, clients, tags, tasks) go through `catalog/CreateOnce.kt`'s `createOnce`, which sends with `resend = false` and, after a network failure, reads the list again and takes the matching item as the one the create stored, because Tokkl refuses a second copy as a duplicate name. When the read finds none, the create goes once more, and only once.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-toggl-api](../../api-and-security/map-tokkl-toggl-api.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors](../../api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [practice-test-against-a-real-tokkl-server](../../testing/practice-test-against-a-real-tokkl-server.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1
<!-- kk:citations:end -->
