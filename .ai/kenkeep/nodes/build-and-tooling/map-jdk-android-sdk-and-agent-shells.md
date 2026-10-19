---
type: map
title: 'JDK and Android SDK on this machine, and how agent shells reach them'
description: >-
  Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java,
  adb and the SDK tools through wrappers in ~/.local/bin.
tags:
  - android
  - tooling
  - agents
  - build
kk_schema_version: 3
kk_id: map-jdk-android-sdk-and-agent-shells
kk_derived_from: []
kk_relates_to: []
kk_depends_on: []
kk_confidence: high
---
On this machine the Temurin JDK 21 lives in `~/.local/share/jdk/21` and the Android SDK in `~/Android/Sdk`. One file, `~/.config/android-env.sh`, sets `JAVA_HOME`, `ANDROID_HOME` and `PATH`. `~/.profile` sources it, and so does the top of `~/.bashrc`, above the check that returns early for non-interactive shells. README.md "Building" lists the versions and install commands.

Claude Code's Bash tool resets `PATH` to the `PATH` of the Claude Code process, so agent shells never get the JDK and SDK directories, even when `JAVA_HOME` and `ANDROID_HOME` are set. That `PATH` does include `~/.local/bin`. There, `java`, `adb`, `sdkmanager`, `avdmanager` and `emulator` are symlinks to `~/.local/libexec/android-env/tool-wrapper`, which sources `android-env.sh` and runs the real tool. Symlinking a tool directly breaks `sdkmanager` and `avdmanager`, because they find the SDK from their own path.

A shell that reads neither file, such as one under `claude -p`, has no `JAVA_HOME` or `ANDROID_HOME`. A Gradle build there needs `ANDROID_HOME` set by hand or `sdk.dir` in `local.properties`, which `.gitignore` already ignores.
