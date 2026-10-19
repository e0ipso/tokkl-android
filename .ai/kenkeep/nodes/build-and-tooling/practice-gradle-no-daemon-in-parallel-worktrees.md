---
type: practice
title: Build with --no-daemon in parallel worktrees and never run --stop
description: >-
  ./gradlew --stop ends every daemon of that Gradle version for the user,
  including one serving a build in another worktree.
tags:
  - build
  - gradle
  - agents
  - worktrees
kk_schema_version: 3
kk_id: practice-gradle-no-daemon-in-parallel-worktrees
kk_derived_from: []
kk_relates_to:
  - map-jdk-android-sdk-and-agent-shells
  - practice-run-npm-install-in-every-clone-and-worktree
kk_depends_on: []
kk_confidence: high
---
Gradle daemons are shared by every project of the same user and Gradle version, through the registry in `~/.gradle/daemon/<version>`. `./gradlew --stop` therefore stops all of them, including the daemon of a build running in another git worktree, and that build dies. Never run `--stop` while another build may be running. When more than one worktree builds at the same time, run each build as `./gradlew --no-daemon <task>` so it gets its own single-use process and shares no daemon.

<!-- kk:related:start -->
# Related

- Related: [map-jdk-android-sdk-and-agent-shells](map-jdk-android-sdk-and-agent-shells.md)
- Related: [practice-run-npm-install-in-every-clone-and-worktree](practice-run-npm-install-in-every-clone-and-worktree.md)
<!-- kk:related:end -->
