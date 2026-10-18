---
type: practice
title: Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases
description: >-
  The Kotlin time parsers copy JavaScript's whitespace, rounding and replace
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
The time parsers in `com.github.e0ipso.tokkl.time` (`Duration.kt` for `parseDuration`, `TimeOfDay.kt` for `parseTimeOfDay`) behave like the TypeScript they port, including where Kotlin differs from JavaScript. `JsSemantics.kt` has the helpers. JavaScript's `\s` and `String.prototype.trim` use the ECMAScript WhiteSpace and LineTerminator characters, while Java's `\s` matches only ASCII and Kotlin's `trim()` uses yet another set. `Math.round` rounds halves up and `kotlin.math.round` rounds them to even. JavaScript's `replace(',', '.')` changes only the first comma, so the port calls `replaceFirst`.

A Long cannot hold NaN, Infinity or integers from 2^63 up, so where the TypeScript returns one of those the port returns null. `parseDuration` returns NaN for the unit `constructor`, because its unit lookup finds that key on `Object.prototype`, and Infinity once a number of 300-odd digits overflows a double.

`DurationTest` and `TimeOfDayTest` hold the cases of the matching `.test.ts` file verbatim, and their header names the tokkl commit they were copied at. To follow an upstream change, copy the cases again and update that commit. `DurationJsSemanticsTest` pins the JavaScript traps in its own file, since they are not in `duration.test.ts` and a later diff against tokkl leaves them alone. Its expected values come from running the TypeScript file in Node 24, where `node file.mts` strips the types.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->
