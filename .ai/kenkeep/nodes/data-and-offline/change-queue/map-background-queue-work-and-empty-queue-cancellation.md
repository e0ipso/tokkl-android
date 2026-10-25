---
type: map
title: Background queue work and empty-queue cancellation
description: >-
  Connected WorkManager delivery and serialized cancellation when the queue
  empties.
tags:
  - offline
  - queue
  - android
kk_schema_version: 3
kk_id: map-background-queue-work-and-empty-queue-cancellation
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:1'
kk_relates_to:
  - practice-refresh-on-events-no-polling
  - map-ordered-entry-sending-and-refusals
kk_depends_on: []
kk_confidence: high
---
The sender drains when a change is queued, after each refresh and when a 429's wait ends. `cache/BackgroundSend` covers the network coming back, also with the app closed (O4). `ChangeQueue`'s `onQueued` enqueues one unique one-time WorkManager work, `send-queue`, with a connected-network constraint and `ExistingWorkPolicy.KEEP`, so nothing wakes the phone while the queue is empty. Its worker calls the same `ChangeSender.sendAndWait`, so the worker and the app never send at once. A transient failure retries with exponential backoff from 30 s. `ChangeSender.onEmptied` runs when a drain that sent something leaves the queue empty and calls `BackgroundSend.cancelWaiting`, which cancels a waiting retry through `BackgroundSendRuns`. Worker registration and completed cancellation share a lock, and the worker checks committed WorkManager state under that lock before joining. A worker that joined the drain ends on its own. The queue is checked after cancellation and work is rescheduled if another change arrived. Sign-out cancels the work.

<!-- kk:related:start -->
# Related

- Related: [practice-refresh-on-events-no-polling](../../api-and-security/practice-refresh-on-events-no-polling.md)
- Related: [map-ordered-entry-sending-and-refusals](map-ordered-entry-sending-and-refusals.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1
<!-- kk:citations:end -->
