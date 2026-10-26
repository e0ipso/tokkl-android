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
On this machine the Temurin JDK 21 lives in `~/.local/share/jdk/21` and the Android SDK in `~/Android/Sdk`. One file, `~/.config/android-env.sh`, sets `JAVA_HOME`, `ANDROID_HOME` and `PATH`. `~/.profile` sources it, and so does the top of `~/.bashrc`, above the check that returns early for non-interactive shells. Everything installs under `$HOME` with no root. The versions are Temurin JDK 21.0.12.1+1, command-line tools 23.0, platform-tools 37.0.1, `platforms;android-37.0` revision 2, build-tools 36.0.0, emulator 37.2.12, and the system images `system-images;android-36;default;x86_64` revision 2 and `system-images;android-31;default;x86_64` revision 5. Google publishes a SHA-256 only for command-line tools 22.0 (build 15859902), so that archive's `sdkmanager` installs everything else, 23.0 included. These packages need only `android-sdk-license` from `--licenses`:

```sh
tmp=$(mktemp -d)
curl -fL -o "$tmp/jdk.tar.gz" \
  https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz
echo "ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94  $tmp/jdk.tar.gz" | sha256sum -c -
mkdir -p ~/.local/share/jdk/versions
tar -xzf "$tmp/jdk.tar.gz" -C ~/.local/share/jdk/versions
ln -sfn versions/jdk-21.0.12.1+1 ~/.local/share/jdk/21
curl -fL -o "$tmp/cmdline-tools.zip" \
  https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  $tmp/cmdline-tools.zip" | sha256sum -c -
unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
export JAVA_HOME=~/.local/share/jdk/21
"$tmp/cmdline-tools/bin/sdkmanager" --sdk_root="$HOME/Android/Sdk" --licenses
"$tmp/cmdline-tools/bin/sdkmanager" --sdk_root="$HOME/Android/Sdk" \
  'cmdline-tools;latest' platform-tools emulator 'system-images;android-36;default;x86_64' \
  'system-images;android-31;default;x86_64' 'platforms;android-37.0' 'build-tools;36.0.0'
rm -rf "$tmp"
```

To upgrade the JDK, unpack the new release next to the old one and point the `21` symlink at it. `android-env.sh` adds `$JAVA_HOME/bin`, `$ANDROID_HOME/emulator`, `$ANDROID_HOME/platform-tools` and `$ANDROID_HOME/cmdline-tools/latest/bin` to `PATH`, skipping any directory already there, so a login shell that reads both `~/.profile` and `~/.bashrc` gets each once. `env -i HOME="$HOME" bash -l -c 'java -version && sdkmanager --list_installed'` checks it from a clean login shell.

Claude Code's Bash tool resets `PATH` to the `PATH` of the Claude Code process, so agent shells never get the JDK and SDK directories, even when `JAVA_HOME` and `ANDROID_HOME` are set. That `PATH` does include `~/.local/bin`. There, `java`, `adb`, `sdkmanager`, `avdmanager` and `emulator` are symlinks to `~/.local/libexec/android-env/tool-wrapper`, which sources `android-env.sh` and runs the real tool. Symlinking a tool directly breaks `sdkmanager` and `avdmanager`, because they find the SDK from their own path.

A shell that reads neither file, such as one under `claude -p`, has no `JAVA_HOME` or `ANDROID_HOME`. A Gradle build there needs `ANDROID_HOME` set by hand or `sdk.dir` in `local.properties`, which `.gitignore` already ignores.
