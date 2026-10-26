---
type: map
title: F-Droid metadata layout and release artifacts
description: >-
  Store text lives in fastlane/metadata/android/en-US/, the fdroiddata recipe in
  fdroid/metadata/com.canpicasoft.tokkl.yml. Check it in Docker before every new
  build.
tags:
  - fdroid
  - release
kk_schema_version: 3
kk_id: map-fdroid-metadata-release-artifacts
kk_derived_from:
  - docs/PRD.md
  - fdroid/README.md
kk_relates_to:
  - map-release-signing-key-actions-secrets-and-signature-checks
  - practice-reproducible-builds-project-key
  - map-app-id-license-android-versions
  - map-oom-kill-ends-agent-session
kk_depends_on: []
kk_confidence: high
---
Store text and images live in `fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt` (80 characters at most), `full_description.txt` (4000 at most), `changelogs/<versionCode>.txt`, `images/icon.png` and four `images/phoneScreenshots/`. `scripts/check-store-metadata.py` checks the two length limits. Commit a version's changelog before its release commit, so the tag F-Droid builds carries it.

The fdroiddata recipe is `fdroid/metadata/com.canpicasoft.tokkl.yml`. `fdroid/README.md` explains it and `test/fdroid-metadata.test.js` checks it. It lists one build, v0.4.0 (4000), the first release under the current app ID; v0.1.0 to v0.3.0 shipped as `com.github.e0ipso.tokkl` and cannot be listed. The Gradle build computes `versionCode`, so `UpdateCheckMode: Tags` uses `UpdateCheckData: gradle/version.properties|versionCode=(\d+)|.|versionName=(\S+)` to read the stored values at each tag. With `AutoUpdateMode: Version`, F-Droid adds new tags on its own. `Binaries` points at `tokkl-android-%v.apk` on the `v%v` GitHub release, and `AllowedAPKSigningKeys` holds the signing certificate's SHA-256.

fdroiddata's CI fails a recipe that `fdroid lint` flags or that `fdroid rewritemeta` would reformat. Lint accepts only the categories in fdroiddata's `config/categories.yml`, such as `Time Tracker` (not `Time`). rewritemeta moves the long `Binaries` URL to a line of its own and writes one-item lists as plain values. F-Droid's sdkmanager names the platform `platforms;android-37.0`; `platforms;android-37` fails the prebuild step.

The checks need no install on the host. Run `fdroid lint`, `fdroid rewritemeta` and `fdroid checkupdates` in `debian:trixie-slim` with the `fdroidserver` package and a git clone of fdroidserver, against a sparse clone of fdroiddata (`config`, `srclibs`, `tools`) with the recipe copied into `metadata/`. Run `fdroid build --test --on-server com.canpicasoft.tokkl:<versionCode>` in `registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie`, as the `fdroid build` job in fdroiddata's `.gitlab-ci.yml` does. That build scans the source, builds with `gradlew-fdroid`, copies the signature from the GitHub APK onto its own build and reports "compared built binary to supplied reference binary successfully" when they match. On 2026-10-26 all of these passed for v0.4.0. Repeat them for every build added to the recipe. Give the build container a memory cap and `--oom-score-adj 1000`, and keep the clones out of `/tmp`, which is RAM.

The store text names only Tokkl. On 2026-10-26 the owner decided not to mention Toggl Track there, because the app is meant for Tokkl, which is free software, so the recipe declares no NonFreeNet anti-feature. F-Droid's reviewers may still raise it.

Nothing is submitted yet. Submitting means a merge request to gitlab.com/fdroid/fdroiddata that adds the recipe, and it waits for the owner's go-ahead.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
[2] [fdroid/README.md](../../../../fdroid/README.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-release-signing-key-actions-secrets-and-signature-checks](map-release-signing-key-actions-secrets-and-signature-checks.md)
- Related: [practice-reproducible-builds-project-key](practice-reproducible-builds-project-key.md)
- Related: [map-app-id-license-android-versions](map-app-id-license-android-versions.md)
- Related: [map-oom-kill-ends-agent-session](../build-and-tooling/map-oom-kill-ends-agent-session.md)
<!-- kk:related:end -->
