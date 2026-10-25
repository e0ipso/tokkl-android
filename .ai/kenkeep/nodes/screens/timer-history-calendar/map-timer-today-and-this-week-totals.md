---
type: map
title: Timer Today and This week totals
description: >-
  Cached real segments contribute once using account timezone, week start and
  duration formatting.
tags:
  - ui
  - timer
  - cache
  - time
kk_schema_version: 3
kk_id: map-timer-today-and-this-week-totals
kk_derived_from:
  - 'b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0'
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-account-settings-source-and-account-time
  - practice-match-web-app-behavior-account-timezone
  - map-offline-entry-mutations-and-local-id-mapping
kk_depends_on: []
kk_confidence: medium
---
The Timer page places Today and This week beneath the timer controls and before the List/Calendar switch, within the scroll-away header. `timer/TimerTotals.kt` provides `timerPeriodTotals` from cached entries using the account timezone, configured week start and duration format.

Actual pause segments contribute to the totals; the cumulative pause-chain display is not added again. The cache retains one entry after local-to-server remapping, and the helper can exclude a mapped server twin when an ID map is supplied. An unresolved account timezone hides the row rather than substituting the device timezone.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities](../../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-account-settings-source-and-account-time](../../data-and-offline/map-account-settings-source-and-account-time.md)
- Related: [practice-match-web-app-behavior-account-timezone](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0
<!-- kk:citations:end -->
