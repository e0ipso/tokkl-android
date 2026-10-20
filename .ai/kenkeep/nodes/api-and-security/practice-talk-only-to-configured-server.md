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
kk_relates_to:
  - map-api-client-shared-okhttp-serverurl-and-errors
kk_depends_on: []
kk_confidence: high
---
Data stays between the phone and the server. The app has no telemetry, analytics, crash reporting or ads, and sends no request to any host other than the configured server. This carries Tokkl's first principle over and matches F-Droid's rules. `ConfiguredServerGuard` on the app's single OkHttp client refuses every other host, and `SingleHttpClientTest` fails when code builds a second client. The app's own manifest asks for `INTERNET` and `ACCESS_NETWORK_STATE`, which lets the picker say when the phone is offline and lets the background send wait for a network. A comment beside it names both requirements. WorkManager's library manifest merges in `ACCESS_NETWORK_STATE` again, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`, so the installed app holds those five platform permissions. androidx.core adds a signature permission of the app's own, `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Check the merged manifest, not `app/src/main/AndroidManifest.xml`, before saying what the app asks for. None of them reaches another host.

Every dependency must be free software. F-Droid builds the app from source with a free toolchain and rejects proprietary libraries such as Google Play Services and Firebase. So the app has no push through FCM.

`:app:checkDeniedDependencies` enforces this. It fails when the release runtime classpath holds a module from a group listed in `gradle/denied-dependency-groups.txt`, or from a group below one, and prints the path that pulled it in. Each line of that file reads `<group> # <reason>`. `check` depends on the task, and CI runs `./gradlew assembleDebug check`.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors](map-api-client-shared-okhttp-serverurl-and-errors.md)
<!-- kk:related:end -->
