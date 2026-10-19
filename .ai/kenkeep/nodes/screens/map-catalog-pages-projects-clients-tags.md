---
type: map
title: 'Catalog pages: projects, clients and tags changed on the server at once'
description: >-
  catalog/ is the shared model and UI of the Projects, Clients and Tags pages:
  no queue, refreshAfterChange, the server's reason on a 4xx.
tags:
  - ui
  - api
  - catalog
kk_schema_version: 3
kk_id: map-catalog-pages-projects-clients-tags
kk_derived_from: []
kk_relates_to:
  - map-api-client-shared-okhttp-serverurl-and-errors
  - map-account-cache-and-capabilities
  - map-change-queue-sends-entry-changes-in-order
kk_depends_on: []
kk_confidence: high
---
`catalog/` holds what the Projects, Clients and Tags pages share (`docs/PRD.md` P2 to P4). `CatalogModel` loads a page's items from the account's default workspace each time the page opens and sends each change at once. Nothing is queued: offline, or with the server out of reach, nothing changes and the page says why. After each change the server accepts, the page calls `AccountSettingsSource.refreshAfterChange()`, so the offline cache catches up and the picker, the history and the reports show the change. `ManagedWorkspace` takes the workspace from the stored account settings, or from `GET /api/v9/me` when the stored settings predate that field.

`ServerProblem` turns an `ApiError` into what a page shows. A 502, 503 or 504 counts as the server being out of reach, because a reverse proxy answers those while Tokkl is down. A 4xx whose body is a JSON string becomes `Refused` with the server's own reason, such as "Name has already been taken". `CatalogPage` is the scrolling frame with a snackbar that names the accepted change in the web app's words, such as "Created Website". `ConfirmDialog` asks before a change that touches more than the item, as the web app's `ConfirmModal` does. Its Cancel and confirm buttons stack at full width, because `AlertDialog`'s own button row put one over the other at 200% font scale.

`projects/`, `clients/` and `tags/` each hold a page's screen, model, view model and server calls. Projects and clients are created, edited, archived and restored. Tags are created, renamed and deleted.

<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors](../api-and-security/map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [map-account-cache-and-capabilities](../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-change-queue-sends-entry-changes-in-order](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md)
<!-- kk:related:end -->
