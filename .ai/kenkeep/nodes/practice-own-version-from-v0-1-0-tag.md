---
type: practice
title: 'Version the app on its own, starting from a hand-made v0.1.0 tag'
description: >-
  The app has its own v* tags and changelog, independent of Tokkl. The first
  release is a hand-made v0.1.0 tag; semantic-release counts on.
tags:
  - release
  - versioning
kk_schema_version: 3
kk_id: practice-own-version-from-v0-1-0-tag
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-fdroid-metadata-release-artifacts
kk_depends_on: []
kk_confidence: high
---
The app has its own version, independent of Tokkl's, with its own `v*` tags and changelog.

semantic-release does not support a first release below 1.0.0. So the first release is a hand-made `v0.1.0` tag, and semantic-release counts on from it. A commit marked as a breaking change moves the app to 1.0.0.

semantic-release runs on pushes to `main` with Tokkl's release rules and `v${version}` tags. It writes `CHANGELOG.md`, rewrites both lines of `gradle/version.properties`, and attaches the signed release APK to the GitHub release.

`gradle/version.properties` is the only place the version is set. It holds `versionName` (MAJOR.MINOR.PATCH) and the computed `versionCode`, which must equal `major * 1_000_000 + minor * 1_000 + patch`, so 0.1.0 is 1000. `app/build.gradle.kts` fails the build when the stored number breaks the formula. The file keeps the number because F-Droid reads it at each tag and cannot evaluate a formula. Keep each value on its own `key=value` line.

The app ships on F-Droid only, with no Play Store listing planned. The signed APK on each GitHub release serves anyone who sideloads.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
