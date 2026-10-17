---
type: practice
title: Talk only to the configured server and use only free software
description: >-
  No telemetry, analytics, crash reporting, ads or proprietary libraries. The
  app sends requests only to the configured server.
tags:
  - privacy
  - network
  - fdroid
  - security
kk_schema_version: 3
kk_id: practice-talk-only-to-configured-server
kk_derived_from:
  - docs/PRD.md
kk_relates_to: []
kk_depends_on: []
kk_confidence: high
---
Data stays between the phone and the server. The app has no telemetry, analytics, crash reporting or ads, and sends no request to any host other than the configured server. This carries Tokkl's first principle over and matches F-Droid's rules.

Every dependency must be free software. F-Droid builds the app from source with a free toolchain and rejects proprietary libraries such as Google Play Services and Firebase. So the app has no push through FCM.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
