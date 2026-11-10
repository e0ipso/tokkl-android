---
type: map
title: Ordered entry sending and refusals
description: >-
  Oldest-first HTTP delivery, retry boundaries, held refusals and
  reauthentication.
tags:
  - offline
  - api
  - queue
kk_schema_version: 3
kk_id: map-ordered-entry-sending-and-refusals
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:1'
kk_relates_to:
  - map-tokkl-toggl-api
  - map-api-client-shared-okhttp-serverurl-and-errors
  - practice-refresh-on-events-no-polling
kk_depends_on: []
kk_confidence: high
---
`cache/ChangeSender` sends the queue oldest first. A create is a POST, an update a full PUT, a captured Stop a PATCH to `.../time_entries/{id}/stop` with the captured `stop` timestamp, a restore a POST to `/api/app/time_entries/{id}/restore`, and a delete a DELETE. A pre-migration Stop with no captured instant retains its original PUT request. No network, a 5xx, a 408 or a 429 stops the drain and keeps the change. A legacy Stop's 409 counts as already done. A captured Stop's 409 or 404 counts as already done only after its exact predicted span and deletion state match the server entry or Trash row; an unverifiable refusal is held for attention. A 404 on an update or legacy Stop discards every queued change to that entry only when a read confirms the entry is gone, and counts the discarded changes for the notice. A 404 for a missing project or another related entity is held instead. A 400 or other permanent 4xx keeps the change and server reason in the needs-attention list. The user can edit, resend or discard it; later changes to the same entry wait, while unrelated entries can drain. A 401 or 403 pauses the entire queue for reauthentication, preserving the account and its data. One exception: when a try's connection failed after the request left the phone (`ApiError.worthAnotherTryAtOnce()`: a network failure that is neither a timeout nor one that left nothing on the server, such as a pooled connection Tokkl closed after Node's 5 s keep-alive), the drain tries that change once more at once on a new connection, at most once per change per drain, before it stops. After a 429 the next try waits for `Retry-After`, or 60 s without it (`RetryAfter`). The wait lives in memory and the queue on disk.

One coroutine owns the drains, with no lock. `drain()` is internal to core:cache, for that coroutine and the module's own tests; every other caller, the instrumented tests included, goes through `send()` or `sendAndWait()`. `send()`, which the account refresh's steps call too, and the 429 retry hand it a request through an unbounded channel with `trySend`; `sendAndWait()` and sign-out's `stop()` send one with a `CompletableDeferred` and await it. The coroutine drains, takes the requests that came meanwhile, and drains again until the queue is quiet, so a call during a drain, or during its `emptied` and `sent` events, gets another pass before any waiter returns. `sendAndWait()` returns the last pass's outcome. `stop()` waits for the drains asked for before it, then cancels the 429 retry and forgets the wait and the paused sign-in. `ChangeSenderTest` pins these orderings. `ChangeSenderEvents.sent` fires once per drain that sent anything and calls `AccountRefreshRequests.refreshAfterChange()`, never `refresh()`. A refresh already running may have read the server before the send, and `refreshAfterChange` waits for it and starts a new one.

A refresh already reading when the queue sends cannot overwrite the send with its stale entry history. `ServerCacheRefresh.refresh` snapshots cached rows and pending entry IDs in a transaction before the history request, then compares rows inside the refresh write transaction. Changed, added, remapped and removed entries keep their current cache state. Initially pending IDs stay protected even when a Stop send leaves the stopped row unchanged or a delete leaves it absent; existing ID mappings extend that protection to server IDs assigned during the read. Remaining pending changes still overlay in queue order. A later uncontended refresh accepts server state and normal history pruning. This adds no schema state and does not make Start or Stop wait for a request.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-toggl-api](../../api-and-security/map-tokkl-toggl-api.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-execution-retries-and-errors](../../api-and-security/api-client/map-api-execution-retries-and-errors.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-test-fixtures](../../api-and-security/api-client/map-api-test-fixtures.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-shared-http-client-and-configured-server-security](../../api-and-security/api-client/map-shared-http-client-and-configured-server-security.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-typed-api-resources-and-request-contracts](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md)
- Related: [practice-refresh-on-events-no-polling](../../api-and-security/practice-refresh-on-events-no-polling.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1
<!-- kk:citations:end -->
