---
type: map
title: Trash capability and server operations
description: >-
  Tokkl-only Trash routes, cached rows and explicit unsupported-endpoint
  handling.
tags:
  - android
  - ui
  - api
kk_schema_version: 3
kk_id: map-trash-capability-and-server-operations
kk_derived_from: []
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-api-client-shared-okhttp-serverurl-and-errors
kk_depends_on: []
kk_confidence: high
---
The Trash page (`trash/`) calls Tokkl-only endpoints through `TrashServer`: `GET` and `DELETE` on `/api/app/trash` (one entry or all) and `POST /api/app/time_entries/{id}/restore`. The server alone holds the trash, so offline the page says it needs the server and every action is off. The page shows only while `Capabilities.trash` is on: the bar and the More sheet hide it otherwise, and the host goes back to Timer when the flag turns off. A trash call that gets a 404, or a body that is not a list, calls `CapabilitiesSource.trashMissing()`, which turns off the trash and restore flags as the sign-in probe would. A new Tokkl-only page should hide itself the same way, from a capability flag that its own failed call can clear.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities](../../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors](../../api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md)
<!-- kk:related:end -->
