---
type: map
title: Queued delete and short-stop Undo
description: Undo operations preserve ordering and restore stopped zero-length entries.
tags:
  - offline
  - queue
  - cache
kk_schema_version: 3
kk_id: map-queued-delete-and-short-stop-undo
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:1'
kk_relates_to:
  - map-offline-entry-mutations-and-local-id-mapping
  - map-ordered-entry-sending-and-refusals
kk_depends_on: []
kk_confidence: high
---
`ChangeSender.undoDelete` takes a delete back (H3). A delete still in the queue leaves it with no request. One the server has, or may have, comes back through `POST /api/app/time_entries/{id}/restore`, so call it only when `Capabilities.restore` is true. Deletes go out holding the lock that `undoDelete` holds, so Undo never decides while a delete is on its way.

`ChangeQueue.undoShortStop` queues `ChangeKind.RESTORE` behind the captured Stop and shows its stopped zero-length row immediately, including offline. `short_stop_undo` persists the offer after the Stop leaves the queue and across process restarts. Dismissing the offer leaves the queued Stop and server Trash intact.

<!-- kk:related:start -->
# Related

- Related: [map-offline-entry-mutations-and-local-id-mapping](map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-ordered-entry-sending-and-refusals](map-ordered-entry-sending-and-refusals.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1
<!-- kk:citations:end -->
