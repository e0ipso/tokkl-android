---
type: map
title: T3 restarts daily and ends running agent sessions
description: >-
  t3-update.timer fires daily with up to 1h delay and restarts t3.service even
  without an update, which stops every agent session.
tags:
  - agents
  - tooling
  - machine
kk_schema_version: 3
kk_id: map-t3-daily-restart-stops-agent-sessions
kk_derived_from: []
kk_relates_to:
  - map-jdk-android-sdk-and-agent-shells
kk_depends_on: []
kk_confidence: high
---
The user unit `t3-update.timer` has `OnCalendar=daily`, `RandomizedDelaySec=1h` and `Persistent=true`. It starts `t3-update.service`, a oneshot that runs `t3 update` and then `systemctl --user restart t3.service` through `ExecStartPost`. The restart happens even when T3 is already current. T3 stops all running agent sessions when the service stops, and the stop logs `stoppedSessions: <count>`.

An agent run or background agent still going between about 00:00 and 01:00 local time is cut off this way. A missed timer fires at the next boot. A run that stops in that window was interrupted, not failed, so start it again.

<!-- kk:related:start -->
# Related

- Related: [map-jdk-android-sdk-and-agent-shells](map-jdk-android-sdk-and-agent-shells.md)
<!-- kk:related:end -->
