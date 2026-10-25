---
type: map
title: Cached entity picker and offline creation
description: >-
  Shared project, task and tag selection uses cached data and explicit offline
  creation limits.
tags:
  - offline
  - cache
  - ui
kk_schema_version: 3
kk_id: map-cached-entity-picker-and-offline-creation
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:1'
kk_relates_to:
  - map-shared-timer-and-entry-fields
  - map-account-cache-and-capabilities
kk_depends_on: []
kk_confidence: high
---
`picker/EntityPicker` is the project, task and tag picker that the timer and the entry form share through `entry/EntryFieldsEditor` (P1). It reads only the cache, so search and selection work offline. Offline it says that creating a project, client or task needs a connection, and the manifest holds `ACCESS_NETWORK_STATE` for that message and for the background send's network wait. A tag typed by name joins the entry by name, and the server creates it when the queue sends the change. The picker's strings live in `picker_strings.xml`.

<!-- kk:related:start -->
# Related

- Related: [map-shared-timer-and-entry-fields](../../screens/timer-and-entry-editing/map-shared-timer-and-entry-fields.md)
- Related: [map-account-cache-and-capabilities](../map-account-cache-and-capabilities.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1
<!-- kk:citations:end -->
