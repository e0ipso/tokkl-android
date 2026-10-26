---
type: map
title: 'Application ID, license and supported Android versions'
description: >-
  Application ID com.canpicasoft.tokkl, MIT license, Android 12 (API 31) and
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
The application ID is `com.canpicasoft.tokkl`, chosen on 2026-10-26 before the F-Droid submission; v0.1.0 to v0.3.0 shipped as `com.github.e0ipso.tokkl`, so those installs do not upgrade to it. The code and its namespace keep the `com.github.e0ipso.tokkl` package, which is also the ID of Tokkl's macOS and KDE companions. The license is MIT, like Tokkl. The repo is `github.com/e0ipso/tokkl-android`, which is public, as section 8.2 of `docs/PRD.md` says.

The app supports Android 12 (API 31) and later. The owner raised `minSdk` from 26 to 31 on 2026-10-19, and the PRD's Compatibility row says the same. Code may use any API from Android 12 on without a version check, and the tests run on API 36 and API 31 only. Phones come first. Layouts adapt to tablets and foldables, with no separate design and no Wear OS app.

The PRD plans the running-timer notification as a Live Update on Android 16 and later, and a regular ongoing notification elsewhere, which has to work without Live Updates because they depend on the device. The built notification is the regular one on every version; the Live Update is not built. On Android 13 and later the app is to ask for the notification permission when the first timer starts and say what is lost without it (TKA-0058); until then the notification shows only where the user allowed notifications in Settings.

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
