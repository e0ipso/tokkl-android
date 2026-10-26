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

semantic-release runs in the `release` job of `.github/workflows/ci.yml` on pushes to `main`, once `gradle`, `reproducible-release`, `instrumented-tests` and `commitlint` pass. It follows Tokkl's rules: `feat` makes a minor release; `fix`, `perf`, `refactor` and `revert` a patch; a breaking change (`!` or a `BREAKING CHANGE:` footer) a major. `docs`, `style`, `test`, `build`, `ci` and `chore` release nothing, and neither does the scope `no-release` unless the commit breaks something. It commits `CHANGELOG.md` and both lines of `gradle/version.properties` as `chore(release): VERSION [skip ci]`, then creates the `vVERSION` tag and a GitHub release. Nothing goes to npm. `android-release` checks out the new tag and builds the signed APK from that clean commit, and `android-publish` attaches `tokkl-android-VERSION.apk` to the release. They run in the same workflow because a tag pushed with `GITHUB_TOKEN` starts no other workflow. When either fails, re-run the failed jobs of the original run; the release exists before its APK does. AGP embeds the commit hash, so an APK built from any tree other than the clean tag cannot match F-Droid's build. Tags v0.1.0 to v0.3.0 exist as of 2026-10-26.

`scripts/semantic-release.js` refuses to run without the hand-made starting tag. Node 24.15 or later runs the tooling. `npm run release -- --dry-run` previews the next release without changing files, but still needs push access. `npm test` performs a complete release against a throwaway local remote. `package.json` overrides `conventional-changelog-writer` to v9 for `@semantic-release/release-notes-generator` 14, which still asks for v8, because the Conventional Commits preset v10 needs v9. Drop the override once the generator depends on v9.

`gradle/version.properties` is the only place the version is set. It holds `versionName` (MAJOR.MINOR.PATCH) and the computed `versionCode`, which must equal `major * 1_000_000 + minor * 1_000 + patch`, so 0.1.0 is 1000. `app/build.gradle.kts` fails the build when the stored number breaks the formula. The file keeps the number because F-Droid reads it at each tag and cannot evaluate a formula. Keep each value on its own `key=value` line.

The app ships on F-Droid only, with no Play Store listing planned. The signed APK on each GitHub release serves anyone who sideloads.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-fdroid-metadata-release-artifacts](map-fdroid-metadata-release-artifacts.md)
<!-- kk:related:end -->
