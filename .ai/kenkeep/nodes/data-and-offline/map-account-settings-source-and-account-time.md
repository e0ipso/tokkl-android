---
type: map
title: 'Account settings: AccountSettingsSource, AccountTime and NoDeviceZoneTest'
description: >-
  Sign-in and each refresh store timezone, week start, resolution and duration
  format. AccountTime gives zone and dates, never the device's.
tags:
  - time
  - api
  - android
  - settings
kk_schema_version: 3
kk_id: map-account-settings-source-and-account-time
kk_derived_from: []
kk_relates_to:
  - practice-match-web-app-behavior-account-timezone
  - map-signin-session-settings-and-navigation-host
  - map-connection-trust-store-pinning-and-prompts
  - practice-refresh-on-events-no-polling
  - map-account-cache-and-capabilities
kk_depends_on: []
kk_confidence: high
---
`session/AccountSettings` holds the `GET /api/v9/me` fields that decide how the app shows and reads times: `timezone`, `beginning_of_week` and Tokkl's optional `time_resolution_minutes`. It holds no credential. Sign-in stores them from its own `/me`. A `/me` without `timezone` or `beginning_of_week`, or with a week start outside 0 to 6, fails sign-in as `SignInResult.NotTokkl`. Toggl Track and older Tokkl servers leave out `time_resolution_minutes` and do not snap, so it stays null for them. `durationFormat` comes from `duration_format` in `GET /api/v9/me/preferences` and, as in the web app, reads as improved when the key is missing, null or names another format. Screens write durations with `AccountSettings.formatDuration(Long)`. `defaultWorkspaceId` comes from `default_workspace_id` in `/me`. The change queue's start and create and the catalog pages take it from here, so they work offline, while `ReportsServer` still reads `/me` for it on its own. `twelveHour` comes from `timeofday_format` in the preferences: any `a` or `A`, as in `h:mm A`, means the 12-hour clock, and a missing, null or non-string value the 24-hour clock. Screens write clock times with it through `time/FormatTime.kt`. `collapseSimilar` comes from the preferences' `collapseTimeEntries` and decides whether the history groups similar entries of a day. It is true until a refresh reads the preferences and when they leave the key out, as in the web app. `AccountTime.instantAt` turns minutes after midnight on a date in the account's zone into an instant through the `zonedToUtc` port.

`session/AccountSettingsSource` is the one place screens read the settings from. `TokklApplication.accountSettings` builds it over `AccountSettingsStore`, which keeps them in the private preferences file `account_settings`, so screens have them offline and after a restart. `MainActivity.onStart` and each sign-in call `refresh()`, so a change made in the web app reaches the phone. After a change the app sent itself, callers use `refreshAfterChange()`, which waits for a refresh already running, since it may have read the server before the change, and then starts a new one. A refresh sends `GET /api/v9/me?with_related_data=true` and `GET /api/v9/me/preferences` once and hands both answers to its `onRefresh`, where the capabilities and the cache refresh from them. A second `refresh()` while one runs gets the running job instead of sending another request. A failed request or write keeps what is stored, failed preferences keep the stored duration format, and a refresh drops its answer when sign-in stored newer settings while the request ran.

`AccountSettings.time()` gives a `time/AccountTime`: the account's zone, a clock in that zone, `now()`, `today()`, `local(instant)`, `startOfWeek(date)` and `snapUnsent(start, stop)`. It returns null when `parseTimeZone` cannot resolve the name, as when the server's Intl knows a zone that the phone's tz database lacks. There is no fallback to the device's zone or to UTC. A screen without an `AccountTime` must show no clock times or dates, take no time input and name the zone it does not know. Durations, the running clock, Start and Stop need no zone and keep working.

`NoDeviceZoneTest`, a JVM test, reads the constant pool of every compiled app class through `ClassReferences` and fails on `ZoneId.systemDefault`, `TimeZone.getDefault` (java.util and ICU), `Clock.systemDefaultZone`, a `now()` without arguments on the java.time date and time types, and a `Calendar` or `GregorianCalendar` built without a zone. Its `ALLOWED` set is empty, and a class added there needs a comment saying why the account's zone will not do. Take the zone, the clock and the dates from `AccountTime` instead.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](practice-match-web-app-behavior-account-timezone.md)
- Related: [map-signin-session-settings-and-navigation-host](../screens/map-signin-session-settings-and-navigation-host.md)
- Related: [map-connection-trust-store-pinning-and-prompts](../api-and-security/map-connection-trust-store-pinning-and-prompts.md)
- Related: [practice-refresh-on-events-no-polling](../api-and-security/practice-refresh-on-events-no-polling.md)
- Related: [map-account-cache-and-capabilities](map-account-cache-and-capabilities.md)
<!-- kk:related:end -->
