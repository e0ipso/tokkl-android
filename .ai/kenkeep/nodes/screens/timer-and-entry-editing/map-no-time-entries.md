---
type: map
title: No-time entries
description: >-
  Tokkl no-time entries have a day and duration and can be created and edited
  offline.
tags:
  - entries
  - calendar
  - offline
kk_schema_version: 3
kk_id: map-no-time-entries
kk_derived_from:
  - '771b5c9e-92ac-4792-b45b-2e479b3c3dba:map:0'
kk_relates_to:
  - map-entry-form-and-account-time-validation
  - map-week-calendar-layout-and-queued-dragging
  - map-cached-history-days-and-grouping
  - map-history-entry-menu-operations
  - map-offline-entry-mutations-and-local-id-mapping
  - practice-check-capabilities-not-server-version
  - practice-match-web-app-behavior-account-timezone
kk_depends_on: []
kk_confidence: high
---
A no-time entry is an all-day Tokkl time entry with a day and duration rather than user-entered start and end times. The entry form offers a No times switch when the server supports the feature and the entry is not running.

The Timer list shows its duration without a time range, and the calendar places it in an All day row instead of the hour grid. Duplicate preserves its no-time status, Split is unavailable, and changes use the offline queue.

<!-- kk:related:start -->
# Related

- Related: [map-entry-form-and-account-time-validation](map-entry-form-and-account-time-validation.md)
- Related: [map-week-calendar-layout-and-queued-dragging](../timer-history-calendar/map-week-calendar-layout-and-queued-dragging.md)
- Related: [map-cached-history-days-and-grouping](../timer-history-calendar/map-cached-history-days-and-grouping.md)
- Related: [map-history-entry-menu-operations](map-history-entry-menu-operations.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [practice-check-capabilities-not-server-version](../../api-and-security/practice-check-capabilities-not-server-version.md)
- Related: [practice-match-web-app-behavior-account-timezone](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 771b5c9e-92ac-4792-b45b-2e479b3c3dba:map:0
<!-- kk:citations:end -->
