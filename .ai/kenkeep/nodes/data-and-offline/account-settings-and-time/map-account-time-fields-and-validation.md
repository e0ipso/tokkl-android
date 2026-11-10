---
type: map
title: Account time fields and validation
description: >-
  Account settings define timezone, week start, duration and clock formatting;
  unresolved account zones disable time input.
tags:
  - time
  - settings
  - android
kk_schema_version: 3
kk_id: map-account-time-fields-and-validation
kk_derived_from: []
kk_relates_to:
  - practice-match-web-app-behavior-account-timezone
  - map-signed-in-navigation-chrome-and-adaptive-windows
  - map-saveable-navigation-stack-and-session-guard
  - map-account-settings-loading-and-refresh
kk_depends_on: []
kk_confidence: high
---
`session/AccountSettings` holds the `GET /api/v9/me` fields that decide how the app shows and reads times: `timezone`, `beginning_of_week` and Tokkl's optional `time_resolution_minutes`. It holds no credential. Sign-in stores them from its own `/me`. A `/me` without `timezone` or `beginning_of_week`, or with a week start outside 0 to 6, fails sign-in as `SignInResult.NotTokkl`. Toggl Track and older Tokkl servers leave out `time_resolution_minutes` and do not snap, so it stays null for them. `durationFormat` comes from `duration_format` in `GET /api/v9/me/preferences` and, as in the web app, reads as improved when the key is missing, null or names another format. Screens write durations with `AccountSettings.formatDuration(Long)`. `defaultWorkspaceId` comes from `default_workspace_id` in `/me`. The change queue's start and create and the catalog pages take it from here, so they work offline, while `ServerReportsRepository` still reads `/me` for it on its own. `twelveHour` comes from `timeofday_format` in the preferences: any `a` or `A`, as in `h:mm A`, means the 12-hour clock, and a missing, null or non-string value the 24-hour clock. Screens write clock times with it through `formatTime` in `time/TimeOfDay.kt`. `collapseSimilar` comes from the preferences' `collapseTimeEntries` and decides whether the history groups similar entries of a day. It is true until a refresh reads the preferences and when they leave the key out, as in the web app. `AccountTime.instantAt` turns minutes after midnight on a date in the account's zone into an instant through the `zonedToUtc` port.

`AccountSettings.time()` gives a `time/AccountTime`: the account's zone, a clock in that zone, `now()`, `today()`, `local(instant)`, `startOfWeek(date)` and `snapUnsent(start, stop)`. It returns null when `parseTimeZone` cannot resolve the name, as when the server's Intl knows a zone that the phone's tz database lacks. There is no fallback to the device's zone or to UTC. A screen without an `AccountTime` must show no clock times or dates, take no time input and name the zone it does not know. Durations, the running clock, Start and Stop need no zone and keep working.

`NoDeviceZoneTest`, a JVM test, reads the constant pool of every compiled app class through `ClassReferences` and fails on `ZoneId.systemDefault`, `TimeZone.getDefault` (java.util and ICU), `Clock.systemDefaultZone`, a `now()` without arguments on the java.time date and time types, and a `Calendar` or `GregorianCalendar` built without a zone. Its `ALLOWED` set is empty, and a class added there needs a comment saying why the account's zone will not do. Take the zone, the clock and the dates from `AccountTime` instead.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](../practice-match-web-app-behavior-account-timezone.md)
- Related: [map-signed-in-navigation-chrome-and-adaptive-windows](../../screens/session-and-navigation/map-signed-in-navigation-chrome-and-adaptive-windows.md)
- Related: [map-saveable-navigation-stack-and-session-guard → map-external-navigation-requests-and-intent-consumption](../../screens/session-and-navigation/navigation-state/map-external-navigation-requests-and-intent-consumption.md)
- Related: [map-saveable-navigation-stack-and-session-guard → map-navigation-route-serialization-and-feature-registration](../../screens/session-and-navigation/navigation-state/map-navigation-route-serialization-and-feature-registration.md)
- Related: [map-saveable-navigation-stack-and-session-guard → map-navigation-stores-and-restored-session-guard](../../screens/session-and-navigation/navigation-state/map-navigation-stores-and-restored-session-guard.md)
- Related: [map-account-settings-loading-and-refresh](map-account-settings-loading-and-refresh.md)
<!-- kk:related:end -->
