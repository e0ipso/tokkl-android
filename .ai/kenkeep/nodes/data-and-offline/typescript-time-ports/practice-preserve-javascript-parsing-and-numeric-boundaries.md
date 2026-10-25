---
type: practice
title: Preserve JavaScript parsing and numeric boundaries
description: >-
  Match JavaScript whitespace and rounding while representing invalid numeric
  results safely.
tags:
  - time
  - parsing
  - tokkl
kk_schema_version: 3
kk_id: practice-preserve-javascript-parsing-and-numeric-boundaries
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0'
kk_relates_to:
  - practice-match-web-app-behavior-account-timezone
kk_depends_on: []
kk_confidence: high
---
The ports in `com.github.e0ipso.tokkl.time` (`Duration.kt` for `parseDuration` and `formatDuration`, `TimeOfDay.kt` for `parseTimeOfDay`, `Snap.kt` for `snapSpan`, and `TimeZone.kt`, which accepts the timezone names Intl accepts) behave like the TypeScript they port, including where Kotlin differs from JavaScript. `JsSemantics.kt` has the helpers. JavaScript's `\s` and `String.prototype.trim` use the ECMAScript WhiteSpace and LineTerminator characters, while Java's `\s` matches only ASCII and Kotlin's `trim()` uses yet another set. `Math.round` rounds halves up and `kotlin.math.round` rounds them to even. JavaScript's `replace(',', '.')` changes only the first comma, so the port calls `replaceFirst`.

A Long cannot hold NaN, Infinity or integers from 2^63 up, so where the TypeScript returns one of those the port returns null. `parseDuration` follows tokkl a4b825d, which returns null for anything over 999 hours on every branch and looks units up in a `Map`, so the unit `constructor` and a number of 300-odd digits now give null in both. Before a4b825d the TypeScript gave NaN and Infinity there; `DurationJsSemanticsTest` keeps those inputs as null cases.

Where the TypeScript throws a RangeError, `snapSpan` throws `IllegalArgumentException`. Within hours of the end of a JavaScript Date's range, near the year 275760, the TypeScript can also return an invalid Date. An `Instant` has no such value, so the port throws there too, and `SnapJsDateRangeTest` pins that edge.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](../practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:0
<!-- kk:citations:end -->
