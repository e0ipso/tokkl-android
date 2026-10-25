---
type: map
title: History entry menu operations
description: >-
  Delete with Undo, duplicate and split actions use the queue and server
  capabilities.
tags:
  - ui
  - timer
  - offline
  - queue
kk_schema_version: 3
kk_id: map-history-entry-menu-operations
kk_derived_from: []
kk_relates_to:
  - map-cached-history-days-and-grouping
  - map-offline-entry-mutations-and-local-id-mapping
  - practice-check-capabilities-not-server-version
  - practice-match-web-app-behavior-account-timezone
kk_depends_on: []
kk_confidence: high
---
`history/EntryActions` is the history's entry menu (H3, H5). Delete goes through the queue, so the entry leaves the list at once, also offline, and a snackbar offers Undo, which calls `ChangeSender.undoDelete`. The snackbar with Undo stays for Material's long duration, which grows with Android's accessibility timeout. When `Capabilities.restore` is false, as on Toggl Track, Delete asks first and the message offers no Undo. Duplicate queues a create with the source's fields. Split (H6) cuts a stopped entry at a typed clock time, ported in `entry/SplitEntry.kt` from the web app's `splitHalves` and `snappedSplit`. A time that is not after the start falls on the next calendar day at the same clock time, for an overnight entry, and the cut moves to the grid of the time resolution. It queues two changes, as the web app posts them: a create of the new second half first, then the original ending at the cut, so a split works offline. The create records the server's entries near its start in `PendingChange.existingIds`, so the lookup for a create whose answer was lost never takes the source for the copy.

<!-- kk:related:start -->
# Related

- Related: [map-cached-history-days-and-grouping](../timer-history-calendar/map-cached-history-days-and-grouping.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [practice-check-capabilities-not-server-version](../../api-and-security/practice-check-capabilities-not-server-version.md)
- Related: [practice-match-web-app-behavior-account-timezone](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->
