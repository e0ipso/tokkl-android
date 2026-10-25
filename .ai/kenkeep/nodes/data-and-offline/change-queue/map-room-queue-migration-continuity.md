---
type: map
title: Room queue migration continuity
description: >-
  Exported schemas and consecutive migrations preserve pending requests and Undo
  state.
tags:
  - offline
  - room
  - queue
kk_schema_version: 3
kk_id: map-room-queue-migration-continuity
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:1'
kk_relates_to:
  - map-offline-entry-mutations-and-local-id-mapping
  - map-account-cache-and-capabilities
kk_depends_on: []
kk_confidence: high
---
Version 5 adds `pending_changes.held` and `pending_changes.refusal`; version 6 adds `capturedStop`, `shortStop` and `short_stop_undo` without rewriting older queued requests. Versions 1 through 6 each have an exported schema in `app/schemas/`, with consecutive Room `AutoMigration` steps. Never drop the database file, since that loses queued changes. `CacheDatabaseMigrationTest` opens files written by earlier schema versions under the current version without room-testing, relying on Room's exported-schema validation.

<!-- kk:related:start -->
# Related

- Related: [map-offline-entry-mutations-and-local-id-mapping](map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-account-cache-and-capabilities](../map-account-cache-and-capabilities.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:1
<!-- kk:citations:end -->
