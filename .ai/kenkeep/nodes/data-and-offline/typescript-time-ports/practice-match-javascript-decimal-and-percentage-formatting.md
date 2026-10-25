---
type: practice
title: Match JavaScript decimal and percentage formatting
description: Binary toFixed and shortest-decimal Intl formatting require separate helpers.
tags:
  - time
  - formatting
  - tokkl
kk_schema_version: 3
kk_id: practice-match-javascript-decimal-and-percentage-formatting
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0'
kk_relates_to: []
kk_depends_on: []
kk_confidence: high
---
`formatDuration` uses JavaScript's double arithmetic, and `DurationTest` holds the `formatDuration` cases of `duration.test.ts` too. `jsToFixed` in `JsSemantics.kt` is `Number.prototype.toFixed`, which rounds the double's exact binary value: `(1.005).toFixed(2)` is `1.00` and `(2.55).toFixed(1)` is `2.5`. `Intl.NumberFormat` rounds the shortest decimal string of the double instead and gives `1.01` and `2.6` (Node 24.21). So `jsToFixed` ports a `toFixed` call only, never an `Intl.NumberFormat` one. For those, `jsShortestDecimal` gives the decimal that JavaScript's `String(x)` writes, the fewest significant digits that read back as the double, and `sharePercent` in `reports/ReportFormat.kt` rounds that decimal with halves away from zero. 15300 of 600000 then gives 2.6, as on the web app, where `toFixed` gives 2.5. Java promises the shortest decimal from `Double.toString` only from Java 19 on, so `jsShortestDecimal` tries each digit count and reads each candidate back with `parseDouble`.

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0
<!-- kk:citations:end -->
