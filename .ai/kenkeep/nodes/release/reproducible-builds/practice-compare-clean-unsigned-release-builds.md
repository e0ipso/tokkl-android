---
type: practice
title: Compare clean unsigned release builds
description: >-
  Build the same committed tree in isolated clones and compare APK bytes, paths,
  timestamps and core-count effects.
tags:
  - release
  - fdroid
  - ci
kk_schema_version: 3
kk_id: practice-compare-clean-unsigned-release-builds
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-fdroid-metadata-release-artifacts
  - practice-own-version-from-v0-1-0-tag
  - practice-sign-releases-and-verify-optimized-apks
  - practice-pin-ci-actions-and-run-the-android-matrix
kk_depends_on: []
kk_confidence: medium
---
`scripts/reproducible-release.sh [commit]` clones the commit twice, builds an unsigned `assembleRelease` in each clone with `--no-daemon --no-build-cache` and compares the two APKs byte for byte. Without `--no-build-cache` the first build would take task outputs from earlier builds on the same machine instead of building from clean, as F-Droid does. It builds committed work only. The `tokkl.android.*` convention plugins in `build-logic` pin Build-Tools 36.0.0 for every module, the default and the minimum of AGP 9.4.1, so check the pin whenever AGP changes. `dependenciesInfo` is off, because AGP would otherwise put a dependency block, encrypted for Google, into the signing block, and F-Droid fails such an APK. AGP writes the commit hash into `META-INF/version-control-info.textproto`, so build a release APK only from a clean checkout of the tagged commit. Every zip entry carries the fixed date 1981-01-01 and no extra timestamp field. The manifest records `platformBuildVersionName='17'` and `platformBuildVersionCode='37'` from the `platforms;android-37.0` package, revision 2 here. Gradle cannot pin a platform revision, so a newer revision on F-Droid's server could change them. The ART baseline profile (`assets/dexopt/baseline.prof` and `baseline.profm`) stays in the APK because it speeds up start-up and came out the same on 8 and on 2 cores. If the script ever shows it differing, turn off the tasks whose names contain `ArtProfile`, as F-Droid's docs describe. The script's second build runs on at most two cores with its own Gradle user home, and the clones sit at paths of different lengths, so an embedded path, core-count-dependent output or an unstable transform all show up. When the APKs differ, it lists the differing zip entries and keeps both APKs. The two builds take about four minutes here and up to about 700 MB under `build/`, which the script removes.

<!-- kk:related:start -->
# Related

- Related: [map-fdroid-metadata-release-artifacts](../map-fdroid-metadata-release-artifacts.md)
- Related: [practice-own-version-from-v0-1-0-tag](../practice-own-version-from-v0-1-0-tag.md)
- Related: [practice-sign-releases-and-verify-optimized-apks](practice-sign-releases-and-verify-optimized-apks.md)
- Related: [practice-pin-ci-actions-and-run-the-android-matrix](practice-pin-ci-actions-and-run-the-android-matrix.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../../docs/PRD.md)
<!-- kk:citations:end -->
