---
type: practice
title: Keep release builds reproducible and signed with the project key
description: >-
  Reproducible builds let F-Droid publish the APK signed with the project key.
  The key lives in Actions secrets and CI pins actions by SHA.
tags:
  - release
  - fdroid
  - security
  - ci
kk_schema_version: 3
kk_id: practice-reproducible-builds-project-key
kk_derived_from:
  - docs/PRD.md
kk_relates_to: []
kk_depends_on: []
kk_confidence: medium
---
If the build is reproducible, F-Droid publishes the APK signed with the project's own key, and only when its own build matches. F-Droid's docs ask for pinned build-tools and no embedded timestamps. Shipping builds signed with the project's own key also keeps registration for Google's developer verification possible later.

The signing key lives in GitHub Actions secrets, never in the repo.

CI on GitHub copies Tokkl's setup. One `ci.yml` workflow runs on pull requests and pushes to `main`. Every action is pinned to a commit SHA with its version in a comment, and Dependabot updates them. Jobs cover the Gradle build, lint and unit tests, and instrumented tests on an emulator. GitHub's Ubuntu runners need a udev rule before the emulator can use KVM. Today `ci.yml` has two jobs, `gradle` (`./gradlew assembleDebug check`) and `commitlint`. The emulator job and the Dependabot config do not exist yet.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../docs/PRD.md)
<!-- kk:citations:end -->
