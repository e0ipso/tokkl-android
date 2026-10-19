---
type: practice
title: Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases
description: >-
  The Kotlin time ports copy JavaScript's whitespace, rounding and replace
  rules, return null for NaN, and reuse the .test.ts cases verbatim.
tags:
  - time
  - parsing
  - testing
  - tokkl
kk_schema_version: 3
kk_id: practice-port-tokkl-typescript-with-javascript-semantics
kk_derived_from: []
kk_relates_to:
  - practice-match-web-app-behavior-account-timezone
kk_depends_on: []
kk_confidence: high
---
The ports in `com.github.e0ipso.tokkl.time` (`Duration.kt` for `parseDuration` and `formatDuration`, `TimeOfDay.kt` for `parseTimeOfDay`, `Snap.kt` for `snapSpan`, and `TimeZone.kt`, which accepts the timezone names Intl accepts) behave like the TypeScript they port, including where Kotlin differs from JavaScript. `JsSemantics.kt` has the helpers. JavaScript's `\s` and `String.prototype.trim` use the ECMAScript WhiteSpace and LineTerminator characters, while Java's `\s` matches only ASCII and Kotlin's `trim()` uses yet another set. `Math.round` rounds halves up and `kotlin.math.round` rounds them to even. JavaScript's `replace(',', '.')` changes only the first comma, so the port calls `replaceFirst`.

A Long cannot hold NaN, Infinity or integers from 2^63 up, so where the TypeScript returns one of those the port returns null. `parseDuration` returns NaN for the unit `constructor`, because its unit lookup finds that key on `Object.prototype`, and Infinity once a number of 300-odd digits overflows a double.

`DurationTest` and `TimeOfDayTest` hold the cases of the matching `.test.ts` file verbatim, and their header names the tokkl commit they were copied at. To follow an upstream change, copy the cases again and update that commit. `DurationJsSemanticsTest` pins the JavaScript traps in its own file, since they are not in `duration.test.ts` and a later diff against tokkl leaves them alone. Its expected values come from running the TypeScript file in Node 24, where `node file.mts` strips the types.

`formatDuration` uses JavaScript's double arithmetic, and `DurationTest` holds the `formatDuration` cases of `duration.test.ts` too. `jsToFixed` in `JsSemantics.kt` is `Number.prototype.toFixed`, which rounds the double's exact binary value: `(1.005).toFixed(2)` is `1.00` and `(2.55).toFixed(1)` is `2.5`. `Intl.NumberFormat` rounds the shortest decimal string of the double instead and gives `1.01` and `2.6` (Node 24.21). So `jsToFixed` ports a `toFixed` call only, never an `Intl.NumberFormat` one. `sharePercent` in `reports/ReportFormat.kt` uses it for percentages that the web app writes with `Intl.NumberFormat`, and differs from the web app at halves until TKA-0148.

Where the TypeScript throws a RangeError, `snapSpan` throws `IllegalArgumentException`. Within hours of the end of a JavaScript Date's range, near the year 275760, the TypeScript can also return an invalid Date. An `Instant` has no such value, so the port throws there too, and `SnapJsDateRangeTest` pins that edge.

`parseTimeZone` was checked against Intl in Node 24.21 (ICU 78.3, tz 2026c), but its results still depend on the device. A name resolves only if the device's tz database lists it or `INTL_ONLY_NAMES` maps it, and that database can be older than the server's. Under tz 2026c America/Edmonton stays at -06:00 all year, while the JDK 21 on this machine ships 2026b and still gives -07:00 in winter.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->
