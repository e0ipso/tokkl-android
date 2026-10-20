---
type: practice
title: Check server capabilities instead of requiring a server version
description: >-
  No minimum Tokkl version. A missing /me field or a 404 from a Tokkl-only
  endpoint hides the features that depend on it.
tags:
  - api
  - toggl
  - compatibility
kk_schema_version: 3
kk_id: practice-check-capabilities-not-server-version
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-account-cache-and-capabilities
kk_depends_on: []
kk_confidence: high
---
The app sets no minimum server version and has no version coupling with Tokkl. It checks what the server can do instead.

A missing `time_resolution_minutes` in `GET /api/v9/me` means there is no snapping to show. A 404 from a Tokkl-only endpoint such as `/api/app/trash` hides the features that use it.

The same checks hide the Tokkl-only features against Toggl Track later: trash, undo of a delete, favorites and time resolution. Older Tokkl servers get the same treatment.

`capabilities/` holds these checks as flags that features read, and the account settings refresh updates them. The history's Delete shows the pattern. With `Capabilities.restore` the entry leaves the list at once and a message offers Undo. Without it, as on Toggl Track, Delete asks first and the message offers no Undo. Write a new Tokkl-only feature the same way: one path for each answer of its flag, and no version check.

The `favorites` flag follows Tokkl's `role` in `/me`. It also covers Tokkl's other preference keys that the app reads and writes, such as `timerView`, so against Toggl Track the Timer page's view stays on the phone. On Tokkl a missing `favorites` key means no favorites yet, not missing support. Some features probe as they go instead of through a flag: the history asks `GET /api/app/bounds`, and against a server without it pages back until a page comes back empty.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities](../data-and-offline/map-account-cache-and-capabilities.md)
<!-- kk:related:end -->
