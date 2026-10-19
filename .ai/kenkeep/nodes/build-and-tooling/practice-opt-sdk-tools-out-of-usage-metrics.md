---
type: practice
title: Opt the Android SDK tools out of usage metrics with ~/.androidrc
description: >-
  sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given
  --no-metrics. A ~/.androidrc holding --no-metrics opts out.
tags:
  - privacy
  - tooling
  - android
kk_schema_version: 3
kk_id: practice-opt-sdk-tools-out-of-usage-metrics
kk_derived_from: []
kk_relates_to:
  - practice-talk-only-to-configured-server
  - map-jdk-android-sdk-and-agent-shells
kk_depends_on: []
kk_confidence: high
---
`sdkmanager` 23.0 hands each command to Google's new `android` CLI, and `sdkmanager --version` prints `(Android CLI)`. On first use that CLI downloads itself into `~/.android`, and it sends usage data to Google unless it gets `--no-metrics`.

The CLI reads default flags from `~/.androidrc`. Run `echo --no-metrics > ~/.androidrc` once on each machine before the first `sdkmanager` call.

<!-- kk:related:start -->
# Related

- Related: [practice-talk-only-to-configured-server](../api-and-security/practice-talk-only-to-configured-server.md)
- Related: [map-jdk-android-sdk-and-agent-shells](map-jdk-android-sdk-and-agent-shells.md)
<!-- kk:related:end -->
