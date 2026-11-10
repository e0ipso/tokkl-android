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
The Trash page (`trash/`) calls Tokkl-only endpoints through `ServerTrashRepository` over `TokklApi.trash`: `GET` and `DELETE` on `/api/app/trash` (one entry or all) and `POST /api/app/time_entries/{id}/restore`. `ServerTrashRepository.list` maps each `TrashEntry` row to the page's `DeletedEntry` (`trash/DeletedEntry.kt`), with typed instants, duration and project id, so the view model and the screen hold no wire type and read no timestamp text. The server alone holds the trash, so offline the page says it needs the server and every action is off. The page shows only while `Capabilities.trash` is on: the bar and the More sheet hide it otherwise, and the host goes back to Timer when the flag turns off. A trash call that gets a 404, or a body that is not a list, calls `CapabilitiesSource.trashMissing()`, which turns off the trash and restore flags as the sign-in probe would. A new Tokkl-only page should hide itself the same way, from a capability flag that its own failed call can clear.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities → map-account-cache-storage-and-history-window](../../data-and-offline/account-cache/map-account-cache-storage-and-history-window.md)
- Related: [map-account-cache-and-capabilities → map-account-refresh-steps-and-preference-readers](../../data-and-offline/account-cache/map-account-refresh-steps-and-preference-readers.md)
- Related: [map-account-cache-and-capabilities → map-capability-probes-and-persisted-feature-flags](../../data-and-offline/account-cache/map-capability-probes-and-persisted-feature-flags.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-execution-retries-and-errors](../../api-and-security/api-client/map-api-execution-retries-and-errors.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-api-test-fixtures](../../api-and-security/api-client/map-api-test-fixtures.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-shared-http-client-and-configured-server-security](../../api-and-security/api-client/map-shared-http-client-and-configured-server-security.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors → map-typed-api-resources-and-request-contracts](../../api-and-security/api-client/map-typed-api-resources-and-request-contracts.md)
<!-- kk:related:end -->
