---
type: map
title: 'Application ID, license and supported Android versions'
description: >-
  Application ID com.github.e0ipso.tokkl, MIT license, Android 12 (API 31) and
  later, one server and one account at a time.
tags:
  - release
  - fdroid
  - android
kk_schema_version: 3
kk_id: map-app-id-license-android-versions
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-fdroid-metadata-release-artifacts
  - practice-own-version-from-v0-1-0-tag
kk_depends_on: []
kk_confidence: high
---
The application ID is `com.github.e0ipso.tokkl`, the same ID as Tokkl's macOS and KDE companions. The license is MIT, like Tokkl. The repo is `github.com/e0ipso/tokkl-android`, which is public, as section 8.2 of `docs/PRD.md` says.

The app supports Android 12 (API 31) and later. The owner raised `minSdk` from 26 to 31 on 2026-10-19, and the PRD's Compatibility row says the same. Code may use any API from Android 12 on without a version check, and the tests run on API 36 and API 31 only. Phones come first. Layouts adapt to tablets and foldables, with no separate design and no Wear OS app.

On Android 16 and later the running-timer notification becomes a Live Update. Elsewhere it stays a regular ongoing notification, and it has to work without Live Updates because they depend on the device. On Android 13 and later the app asks for the notification permission when the first timer starts and says what is lost without it.

The app signs in to one server and one account at a time. Tokkl's admin pages, Toggl CSV import, JSON export and restore, and bulk edit stay in the web app.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-fdroid-metadata-release-artifacts](map-fdroid-metadata-release-artifacts.md)
- Related: [practice-own-version-from-v0-1-0-tag](practice-own-version-from-v0-1-0-tag.md)
<!-- kk:related:end -->
