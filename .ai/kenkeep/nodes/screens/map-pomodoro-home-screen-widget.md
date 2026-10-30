---
type: map
title: Pomodoro home screen widget
description: >-
  A separate provider shows the server session and uses native countdowns and
  online-only session actions.
tags:
  - android
  - widget
  - pomodoro
  - battery
kk_schema_version: 3
kk_id: map-pomodoro-home-screen-widget
kk_derived_from: []
kk_relates_to:
  - map-home-screen-widget-cache-state-and-a-native-ticking-clock
  - map-timer-state-holder-and-sync-service
  - map-pomodoro-phase-alarms-and-rearming
kk_depends_on: []
kk_confidence: high
---
`widget/PomodoroWidget` shows the held server phase, number, corrected countdown or overtime and entry context, with Next and End. Its phase-colour band is static and carries phase words. Idle Start uses account defaults and remembered session fields, including fields learned from remote sessions. Sign-out or account replacement forgets them. Unsupported servers open the app instead of starting a local session. Start, Next and End need the server; captured actions retain account, phase and revision guards.

The launcher ticks the native clock. `WidgetSync` uses a separate redraw key and follower for this provider. One cancellable in-process wait follows the corrected phase end; when exact access is denied, one phase-guarded inexact alarm offers a best-effort redraw, which Android may delay. It never starts the ringing service. No session means no deadline or fallback work. The exact phase alarm and ringing service remain separate.

<!-- kk:related:start -->
# Related

- Related: [map-home-screen-widget-cache-state-and-a-native-ticking-clock](map-home-screen-widget-cache-state-and-a-native-ticking-clock.md)
- Related: [map-timer-state-holder-and-sync-service](../data-and-offline/map-timer-state-holder-and-sync-service.md)
- Related: [map-pomodoro-phase-alarms-and-rearming](../data-and-offline/map-pomodoro-phase-alarms-and-rearming.md)
<!-- kk:related:end -->
