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
kk_relates_to: []
kk_depends_on: []
kk_confidence: high
---
The app has its own version, independent of Tokkl's, with its own `v*` tags and changelog.

semantic-release does not support a first release below 1.0.0. So the first release is a hand-made `v0.1.0` tag, and semantic-release counts on from it. A commit marked as a breaking change moves the app to 1.0.0.

semantic-release runs on pushes to `main` with Tokkl's release rules and `v${version}` tags. It writes `CHANGELOG.md` and the version into the Gradle build, derives `versionCode` from the version, and attaches the signed release APK to the GitHub release.

The app ships on F-Droid only, with no Play Store listing planned. The signed APK on each GitHub release serves anyone who sideloads.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
