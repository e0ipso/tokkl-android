---
type: practice
title: Honor account clock formatting in time ports
description: >-
  FormatTime matches the account 12-hour or 24-hour preference and server value
  semantics.
tags:
  - time
  - formatting
  - tokkl
kk_schema_version: 3
kk_id: practice-honor-account-clock-formatting-in-time-ports
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0'
kk_relates_to:
  - practice-match-web-app-behavior-account-timezone
kk_depends_on: []
kk_confidence: high
---
`FormatTime.kt` ports `formatTime` from `../tokkl/packages/web/src/lib/settings.ts`. The 24-hour clock gives `09:05`, `00:00` at midnight and `12:00` at noon, and the 12-hour clock `9:05 AM`, `12:00 AM` and `12:00 PM`, with the hour unpadded. Seconds are dropped, never rounded. The web app picks the 12-hour clock when `/a/i` matches `timeofday_format`, so any `a`, as in `h:mm A`. `AccountSettings.twelveHour` reads only a string: a missing, null or non-string value means the 24-hour clock. The web app differs for a JSON `false`, because `RegExp.test` turns it into the text "false", which matches.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](../practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0
<!-- kk:citations:end -->
