---
type: practice
title: Check server capabilities instead of requiring a server version
description: >-
  Probe optional Tokkl features by capability; snapping follows the latest
  supported contract, with no version selector.
tags:
  - api
  - toggl
  - compatibility
kk_schema_version: 3
kk_id: practice-check-capabilities-not-server-version
kk_derived_from:
  - docs/PRD.md
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:2'
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-timer-state-holder-and-sync-service
kk_depends_on: []
kk_confidence: high
---
Probe optional Tokkl features by capability, without a server-version selector. Snapping follows the latest supported Tokkl contract, the 1.6.1+ independent-endpoint rules; older snapping contracts are unsupported and have no compatibility probe. Device verification pins Tokkl 2.1.0, whose PATCH Stop accepts the captured timestamp and moves a short Stop to Trash for Undo. Unsupported timer behavior is covered by account fixtures without the timer capability. Missing `time_resolution_minutes`, as on Toggl Track, keeps the no-resolution behavior.

A missing `time_resolution_minutes` in `GET /api/v9/me` means there is no snapping to show. A 404 from a Tokkl-only endpoint such as `/api/app/trash` hides the features that use it. The `timerState` flag, decided by `GET /api/app/timer`, hides Pomodoro and Pause the same way, and keeps the sync service off, on Toggl Track and on Tokkl before 1.10.0 (see the cache node).

The same checks hide the Tokkl-only features against Toggl Track later: trash, undo of a delete, favorites, time resolution and the reports' "Is not" filters (`negatedFilters`). Missing optional features on older Tokkl servers get the same treatment; this does not promise support for their older snapping semantics.

`capabilities/` holds these checks as flags that features read, and the account settings refresh updates them. The history's Delete shows the pattern. With `Capabilities.restore` the entry leaves the list at once and a message offers Undo. Without it, as on Toggl Track, Delete asks first and the message offers no Undo. Write a new Tokkl-only feature the same way: one path for each answer of its flag, and no version check. A Tokkl-only page hides itself the way Trash does: the flag hides its navigation item, and a 404 on its own call clears the flag (`CapabilitiesSource.trashMissing()`), so the page goes away without a new sign-in.

The `favorites` flag follows Tokkl's `role` in `/me`. It also covers Tokkl's other preference keys that the app reads and writes, such as `timerView`, so against Toggl Track the Timer page's view stays on the phone. On Tokkl a missing `favorites` key means no favorites yet, not missing support. Some features probe as they go instead of through a flag: the history asks `GET /api/app/bounds`, and against a server without it pages back until a page comes back empty.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities → map-account-cache-storage-and-history-window](../data-and-offline/account-cache/map-account-cache-storage-and-history-window.md)
- Related: [map-account-cache-and-capabilities → map-account-refresh-steps-and-preference-readers](../data-and-offline/account-cache/map-account-refresh-steps-and-preference-readers.md)
- Related: [map-account-cache-and-capabilities → map-capability-probes-and-persisted-feature-flags](../data-and-offline/account-cache/map-capability-probes-and-persisted-feature-flags.md)
- Related: [map-timer-state-holder-and-sync-service → map-account-timer-state-lifetime](../data-and-offline/timer-state-and-sync/map-account-timer-state-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-online-timer-actions-and-native-control-failures](../data-and-offline/timer-state-and-sync/map-online-timer-actions-and-native-control-failures.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-event-stream-and-service-lifetime](../data-and-offline/timer-state-and-sync/map-timer-event-stream-and-service-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-state-acceptance-and-persistence](../data-and-offline/timer-state-and-sync/map-timer-state-acceptance-and-persistence.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
[2] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:2
<!-- kk:citations:end -->
