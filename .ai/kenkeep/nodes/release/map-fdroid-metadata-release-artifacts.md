---
type: map
title: F-Droid metadata layout and release artifacts
description: >-
  Store text and images live under fastlane/metadata/android/en-US/. F-Droid
  finds versions by git tag and the signed APK via Binaries.
tags:
  - fdroid
  - release
kk_schema_version: 3
kk_id: map-fdroid-metadata-release-artifacts
kk_derived_from:
  - docs/PRD.md
kk_relates_to: []
kk_depends_on: []
kk_confidence: medium
---
Store text and images go in the repo under `fastlane/metadata/android/en-US/`, which does not exist yet: `title.txt`, `short_description.txt` (80 characters at most), `full_description.txt` (4000 at most), `changelogs/<versionCode>.txt`, `images/icon.png` and `images/phoneScreenshots/`.

With `UpdateCheckMode: Tags`, F-Droid checks every git tag for the highest version code. By default it reads the version code from the Gradle build, but this build computes it, so the metadata needs `UpdateCheckData: gradle/version.properties|versionCode=(\d+)|.|versionName=(\S+)` to read the stored values instead.

`Binaries` points at a versioned APK URL, such as the GitHub release asset that semantic-release attaches. `AllowedAPKSigningKeys` holds the SHA-256 fingerprint of the signing certificate.

F-Droid's NonFreeNet anti-feature covers apps that promote or depend entirely on a proprietary network service. It does not apply while the app talks to Tokkl only. Once Toggl support lands, F-Droid's reviewers decide whether supporting Toggl counts as promoting it.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
