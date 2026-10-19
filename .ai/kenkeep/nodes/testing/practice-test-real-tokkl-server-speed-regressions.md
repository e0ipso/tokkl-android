---
type: practice
title: Test against a real Tokkl server and catch speed regressions in CI
description: >-
  Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake.
  Macrobenchmark fails a build slower than the previous one.
tags:
  - testing
  - ci
  - performance
kk_schema_version: 3
kk_id: practice-test-real-tokkl-server-speed-regressions
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - practice-run-instrumented-tests-only-through-the-script
kk_depends_on: []
kk_confidence: medium
---
The app has JVM unit tests, Compose UI tests, and instrumented tests on an emulator. Instrumented tests run against a real Tokkl server from the `ghcr.io/e0ipso/tokkl` image, never a fake one. `scripts/instrumented-tests.sh` starts that server and runs them. They run on tokkl-api36, tokkl-api30 and tokkl-api26 here, and CI runs them on API 36 and API 30 emulators.

The PWA sets the speed bar. If starting a timer is slower here, the owner drifts back to the PWA. Tests prove that a tap on Start or Stop changes the state with a network that never answers. Jetpack Macrobenchmark is to run cold start (`StartupTimingMetric`) and tap-to-state (`TraceSectionMetric`) on the CI emulator and fail a build that is slower than the previous one. No benchmark module exists yet: `settings.gradle.kts` includes only `:app`.

The goals are 200 ms from tap to new state and 1.5 s cold start on a mid-range phone. No real phone checks them, so they guide design but do not gate releases.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [practice-run-instrumented-tests-only-through-the-script](practice-run-instrumented-tests-only-through-the-script.md)
<!-- kk:related:end -->
