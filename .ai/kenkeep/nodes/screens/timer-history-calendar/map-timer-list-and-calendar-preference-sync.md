---
type: map
title: Timer List and Calendar preference sync
description: >-
  View selection persists locally and writes the supported server preference
  without blocking navigation.
tags:
  - ui
  - timer
  - offline
kk_schema_version: 3
kk_id: map-timer-list-and-calendar-preference-sync
kk_derived_from:
  - 'b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0'
kk_relates_to:
  - practice-check-capabilities-not-server-version
  - map-account-cache-and-capabilities
kk_depends_on: []
kk_confidence: medium
---
`timer/TimerViewStore` keeps the List or Calendar choice in Tokkl's `timerView` preference, as the web app does, so a choice made on one device opens on the others. A tap shows the new view at once and posts it to `POST /api/v9/me/preferences` without waiting. When that fails, as offline, the choice stays on the phone marked unsent and goes to the server with the next refresh, so the last choice to reach the server wins. Each refresh otherwise opens the server's `timerView`, and List when the key is missing or holds anything else. The one refresh right after a tap is the exception, since its preferences may predate the tap. The phone also keeps the choice in a private preferences file, so a cold start opens it before the refresh answers, and offline. `Capabilities.favorites` decides whether the server keeps Tokkl's preferences at all. Toggl Track does not, and there the choice stays on the phone. Tokkl itself accepts only `list` and `calendar` as values.

<!-- kk:related:start -->
# Related

- Related: [practice-check-capabilities-not-server-version](../../api-and-security/practice-check-capabilities-not-server-version.md)
- Related: [map-account-cache-and-capabilities](../../data-and-offline/map-account-cache-and-capabilities.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] b3f87464-06de-4fac-8d0a-ff4dd64ea60f:map:0
<!-- kk:citations:end -->
