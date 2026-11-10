---
type: map
title: Account refresh steps and preference readers
description: >-
  One account refresh loads related data, probes capabilities, fills the cache
  and hands each preference to its reader.
tags:
  - api
  - cache
  - settings
kk_schema_version: 3
kk_id: map-account-refresh-steps-and-preference-readers
kk_derived_from: []
kk_relates_to:
  - map-account-settings-source-and-account-time
  - map-timer-state-holder-and-sync-service
  - map-account-cache-storage-and-history-window
  - map-capability-probes-and-persisted-feature-flags
kk_depends_on: []
kk_confidence: high
---
One refresh feeds all of this. `AccountRefresh.refresh`, called when the app comes to the foreground or the network returns (see the refresh practice node), after sign-in and after an accepted trust prompt, sends `GET /api/v9/me?with_related_data=true` and `GET /api/v9/me/preferences` once. Its steps, which the app's `lifecycle/AppRefreshSteps` runs in order as the one `AccountRefreshSteps` (it spans the core modules and the Timer feature, so the app holds it), refresh the capabilities with the trash and timer probes, then the cache through `ServerCacheRefresh.refresh(api, me, preferences, flags)`, which adds `GET /api/v9/me/time_entries` for the history window, then give the preferences to the Timer view, the Pomodoro defaults and the reminder, send the change queue, since the server has just answered, and let the timer sync follow the capabilities. A new step goes into that list, not into a listener. A feature that needs `/me` data takes it from this refresh instead of sending its own request. `MePreferences`, in core:api, keeps the answer and gives each key by the JSON type its reader expects, and each reader reads only its own key, such as `duration_format`, `timeofday_format` and `collapseTimeEntries` for the settings, `timerView` for the Timer page's view and `favorites` for the cache, so a malformed key fails that reader alone.

<!-- kk:related:start -->
# Related

- Related: [map-account-settings-source-and-account-time → map-account-settings-loading-and-refresh](../account-settings-and-time/map-account-settings-loading-and-refresh.md)
- Related: [map-account-settings-source-and-account-time → map-account-time-fields-and-validation](../account-settings-and-time/map-account-time-fields-and-validation.md)
- Related: [map-timer-state-holder-and-sync-service → map-account-timer-state-lifetime](../timer-state-and-sync/map-account-timer-state-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-online-timer-actions-and-native-control-failures](../timer-state-and-sync/map-online-timer-actions-and-native-control-failures.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-event-stream-and-service-lifetime](../timer-state-and-sync/map-timer-event-stream-and-service-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-state-acceptance-and-persistence](../timer-state-and-sync/map-timer-state-acceptance-and-persistence.md)
- Related: [map-account-cache-storage-and-history-window](map-account-cache-storage-and-history-window.md)
- Related: [map-capability-probes-and-persisted-feature-flags](map-capability-probes-and-persisted-feature-flags.md)
<!-- kk:related:end -->
