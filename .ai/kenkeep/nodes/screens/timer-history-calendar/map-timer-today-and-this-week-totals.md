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
The Timer page places Today and This week beneath the timer controls and before the List/Calendar switch, within the scroll-away header. `timerstate/TimerTotals.kt` in `:core:timerstate` provides `timerPeriodTotals` from cached entries using the account timezone, configured week start and duration format. `timer/TimerTotalsState.kt` turns them into the row's state and ticks it (`timerTotals`): each whole second while an entry runs, otherwise at the account's midnight. `TimerPageViewModel.totals` shares that flow while the page shows, apart from the page's other state, so only the row redraws each second, and `TimerTotals` draws the state it gets.

Actual pause segments contribute to the totals; the cumulative pause-chain display is not added again. The cache retains one entry after local-to-server remapping, and the helper can exclude a mapped server twin when a map from `LocalEntryId` to `EntryId` is supplied, as the widget reads it from `AccountCache.serverIdsOf`. An unresolved account timezone hides the row rather than substituting the device timezone.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities → map-account-cache-storage-and-history-window](../../data-and-offline/account-cache/map-account-cache-storage-and-history-window.md)
- Related: [map-account-cache-and-capabilities → map-account-refresh-steps-and-preference-readers](../../data-and-offline/account-cache/map-account-refresh-steps-and-preference-readers.md)
- Related: [map-account-cache-and-capabilities → map-capability-probes-and-persisted-feature-flags](../../data-and-offline/account-cache/map-capability-probes-and-persisted-feature-flags.md)
- Related: [map-account-settings-source-and-account-time → map-account-settings-loading-and-refresh](../../data-and-offline/account-settings-and-time/map-account-settings-loading-and-refresh.md)
- Related: [map-account-settings-source-and-account-time → map-account-time-fields-and-validation](../../data-and-offline/account-settings-and-time/map-account-time-fields-and-validation.md)
- Related: [practice-match-web-app-behavior-account-timezone](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0
<!-- kk:citations:end -->
