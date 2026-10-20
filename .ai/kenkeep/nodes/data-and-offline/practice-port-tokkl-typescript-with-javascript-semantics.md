---
type: practice
title: Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases
description: >-
  The Kotlin ports copy JavaScript's whitespace, rounding, Date.UTC and join
  rules, return null for NaN, and reuse Tokkl's test cases verbatim.
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

`DurationTest`, `TimeOfDayTest`, `SnapTest` and `ZonedTimeTest` hold the cases of the matching `.test.ts` file verbatim, and their header names the tokkl commit they were copied at. To follow an upstream change, copy the cases again and update that commit. Since tokkl a5cadd1 (2026-10-18) Tokkl keeps the cases of `parseDuration`, `parseTimeOfDay` and `snapSpan` as JSON in `../tokkl/packages/common/fixtures/`, which its README offers to ports in other repos, and its `.test.ts` files only loop over them. So the next copy comes from those JSON files. Tokkl v1.4.1 also moved `parseTimeOfDay`, unchanged, from `packages/web/src/lib/timeOfDay.ts` to `packages/common/timeOfDay.ts`, so `TimeOfDay.kt`'s header names a path that no longer exists. `DurationJsSemanticsTest` pins the JavaScript traps in its own file, since they are not in `duration.test.ts` and a later diff against tokkl leaves them alone. Its expected values come from running the TypeScript file in Node 24, where `node file.mts` strips the types.

`formatDuration` uses JavaScript's double arithmetic, and `DurationTest` holds the `formatDuration` cases of `duration.test.ts` too. `jsToFixed` in `JsSemantics.kt` is `Number.prototype.toFixed`, which rounds the double's exact binary value: `(1.005).toFixed(2)` is `1.00` and `(2.55).toFixed(1)` is `2.5`. `Intl.NumberFormat` rounds the shortest decimal string of the double instead and gives `1.01` and `2.6` (Node 24.21). So `jsToFixed` ports a `toFixed` call only, never an `Intl.NumberFormat` one. For those, `jsShortestDecimal` gives the decimal that JavaScript's `String(x)` writes, the fewest significant digits that read back as the double, and `sharePercent` in `reports/ReportFormat.kt` rounds that decimal with halves away from zero. 15300 of 600000 then gives 2.6, as on the web app, where `toFixed` gives 2.5. Java promises the shortest decimal from `Double.toString` only from Java 19 on, so `jsShortestDecimal` tries each digit count and reads each candidate back with `parseDouble`.

`FormatTime.kt` ports `formatTime` from `../tokkl/packages/web/src/lib/settings.ts`. The 24-hour clock gives `09:05`, `00:00` at midnight and `12:00` at noon, and the 12-hour clock `9:05 AM`, `12:00 AM` and `12:00 PM`, with the hour unpadded. Seconds are dropped, never rounded. The web app picks the 12-hour clock when `/a/i` matches `timeofday_format`, so any `a`, as in `h:mm A`. `AccountSettings.twelveHour` reads only a string: a missing, null or non-string value means the 24-hour clock. The web app differs for a JSON `false`, because `RegExp.test` turns it into the text "false", which matches.

`ZonedTime.kt` ports `zonedToUtc` from `../tokkl/packages/common/time.ts`, which the entry form reaches through `AccountTime.instantAt` to turn a typed date and time in the account's zone into an instant. Use it, not `ZonedDateTime.of`. They differ in the hour that repeats when the clocks go back: `zonedToUtc` takes the later one, so 02:30 in Madrid on 2026-10-25 is 02:30 CET, where `ZonedDateTime.of` takes the earlier, CEST. A time in the spring gap lands after the gap. As with `Date.UTC`, fields out of range carry over, so minute 90 is half past the next hour.

`history/HistoryRows.kt` ports `similarityKey` and `rowsForDay` from `../tokkl/packages/web/src/components/EntryList.tsx`. The key joins the fields as `Array.join` does, a null as an empty string and the tags with commas, so a tag named "a,b" matches the tags "a" and "b", as on the web app.

Where the TypeScript throws a RangeError, `snapSpan` throws `IllegalArgumentException`. Within hours of the end of a JavaScript Date's range, near the year 275760, the TypeScript can also return an invalid Date. An `Instant` has no such value, so the port throws there too, and `SnapJsDateRangeTest` pins that edge.

`parseTimeZone` was checked against Intl in Node 24.21 (ICU 78.3, tz 2026c), but its results still depend on the device. A name resolves only if the device's tz database lists it or `INTL_ONLY_NAMES` maps it, and that database can be older than the server's. Under tz 2026c America/Edmonton stays at -06:00 all year, while the JDK 21 on this machine ships 2026b and still gives -07:00 in winter.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](practice-match-web-app-behavior-account-timezone.md)
<!-- kk:related:end -->
