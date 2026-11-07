---
type: practice
title: Fix detekt findings in code, and baseline only a fix that changes behaviour
description: >-
  check runs detekt 2 with ktlint's intellij_idea rules and the Compose rules on
  every module. Fix findings in code; a baseline entry needs a behaviour change
  as its reason.
tags:
  - build
  - gradle
  - kotlin
kk_schema_version: 3
kk_id: practice-fix-detekt-findings-in-code
kk_derived_from:
  - config/detekt/detekt.yml
  - build-logic/convention/src/main/kotlin/Detekt.kt
kk_relates_to:
  - map-gradle-build-sdk-levels-kotlin-and-memory
kk_depends_on: []
kk_confidence: high
---
`configureAndroid` in build-logic applies detekt 2.0.0-alpha.6 (`dev.detekt`) to every module, over the module's whole `src` directory, with ktlint's rules through `dev.detekt:detekt-rules-ktlint-wrapper` and the Compose rules of `io.nlopez.compose.rules:detekt` 0.6.8. Both are Apache-2.0. Detekt 2 is the line that reads Kotlin 2.4, and the Compose rules 0.6 build on it. `check` runs the plain `detekt` task, without type resolution, and the configuration cache keeps it.

`.editorconfig` sets `ktlint_code_style = intellij_idea`, ktlint's name for the style `kotlin.code.style=official` in `gradle.properties` means. Lines stop at 120 columns, arguments that do not fit go one per line, and imports follow IntelliJ's layout. `./gradlew detekt --auto-correct` rewrites the layout; run it again until a plain `./gradlew detekt` reports nothing, since one pass can leave work for the next.

`config/detekt/detekt.yml` holds only what differs from detekt's defaults, each with its reason: composables named like types and free of the magic number, length and complexity limits, test code free of the size limits, Room DAOs' annotated queries and view models' event functions within the function count, guard clauses for throws, no limit on returns, and the app's own CompositionLocals. Name a number with a constant (`HttpStatus` in `:core:api`, the clock and calendar units in `:core:time`, `Build.VERSION_CODES`), name a catch parameter `_` when the catch uses only its type, and import names one by one.

A module's `detekt-baseline.xml` holds the findings whose fix would change behaviour, each under a comment with its reason, such as an effect that would then call a newer lambda or a modifier that would move the touch target. Any other finding is fixed in code. To add an entry, set the extension's `baseline` in `Detekt.kt` for a moment, run the module's `detektBaseline`, put the reason above the new entry, and set it back. The convention reads a baseline only where the file exists.
