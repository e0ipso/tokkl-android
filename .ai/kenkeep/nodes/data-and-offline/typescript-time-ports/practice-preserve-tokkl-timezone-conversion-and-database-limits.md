---
type: practice
title: Preserve Tokkl timezone conversion and database limits
description: >-
  DST gap/fold behavior and device timezone availability constrain converted
  account times.
tags:
  - time
  - parsing
  - tokkl
kk_schema_version: 3
kk_id: practice-preserve-tokkl-timezone-conversion-and-database-limits
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0'
kk_relates_to:
  - practice-match-web-app-behavior-account-timezone
kk_depends_on: []
kk_confidence: high
---
`ZonedTime.kt` ports `zonedToUtc` from `../tokkl/packages/common/time.ts`, which the entry form reaches through `AccountTime.instantAt` to turn a typed date and time in the account's zone into an instant. Use it, not `ZonedDateTime.of`. They differ in the hour that repeats when the clocks go back: `zonedToUtc` takes the later one, so 02:30 in Madrid on 2026-10-25 is 02:30 CET, where `ZonedDateTime.of` takes the earlier, CEST. A time in the spring gap lands after the gap. As with `Date.UTC`, fields out of range carry over, so minute 90 is half past the next hour.

`parseTimeZone` was checked against Intl in Node 24.21 (ICU 78.3, tz 2026c), but its results still depend on the device. A name resolves only if the device's tz database lists it or `INTL_ONLY_NAMES` maps it, and that database can be older than the server's. Under tz 2026c America/Edmonton stays at -06:00 all year, while the JDK 21 on this machine ships 2026b and still gives -07:00 in winter.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](../practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0
<!-- kk:citations:end -->
