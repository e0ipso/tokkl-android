# Tokkl for Android

A native Android client for [Tokkl](https://github.com/e0ipso/tokkl) and Toggl Track.

The app is a Gradle project with one launch screen so far. The product requirements live in [docs/PRD.md](docs/PRD.md).

## Building

These steps prepare a Linux x86_64 machine to build and test the app. Everything installs under `$HOME` and needs no root.

| Component | Version | Path |
| --- | --- | --- |
| JDK | Eclipse Temurin 21.0.12.1+1 | `~/.local/share/jdk/21` |
| Android SDK Command-line Tools | 23.0 | `~/Android/Sdk/cmdline-tools/latest` |
| Android SDK Platform-Tools | 37.0.1 | `~/Android/Sdk/platform-tools` |
| Android SDK Platform | `platforms;android-37.0`, revision 2 | `~/Android/Sdk/platforms/android-37.0` |
| Android SDK Build-Tools | 36.0.0 | `~/Android/Sdk/build-tools/36.0.0` |
| Android Emulator | 37.2.12 | `~/Android/Sdk/emulator` |
| System image | `system-images;android-36;default;x86_64`, revision 2 | `~/Android/Sdk/system-images` |

The Gradle wrapper downloads Gradle 9.8.1 and checks it against the SHA-256 sum in `gradle/wrapper/gradle-wrapper.properties`. `gradle/libs.versions.toml` sets Android Gradle Plugin 9.4.1, Kotlin 2.4.21 and Compose BOM 2026.09.00. The app compiles against API 37, which Compose UI 1.12 and androidx.core 1.19 require. It targets API 36, the API level of the test emulator. Android Gradle Plugin 9.4 uses Build-Tools 36.0.0 by default.

Android Gradle Plugin 9.4 needs JDK 17 or later, and JDK 21 is an LTS release above that floor. The system image is the plain AOSP build of Android 16 (API 36), not a Google APIs or Play Store build. It keeps SystemUI and the launcher, which tests of the notification, the Quick Settings tile and the widget need. The smaller Automated Test Device images remove both.

### Install the JDK

```sh
tmp=$(mktemp -d)
curl -fL -o "$tmp/jdk.tar.gz" \
  https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz
echo "ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94  $tmp/jdk.tar.gz" | sha256sum -c -
mkdir -p ~/.local/share/jdk/versions
tar -xzf "$tmp/jdk.tar.gz" -C ~/.local/share/jdk/versions
ln -sfn versions/jdk-21.0.12.1+1 ~/.local/share/jdk/21
```

To upgrade, unpack the new release next to the old one and point the `21` symlink at it.

### Install the Android SDK

The Android Studio download page gives a SHA-256 checksum for command-line tools 22.0 (build 15859902). Use the `sdkmanager` in that archive once to install everything else, including command-line tools 23.0.

```sh
curl -fL -o "$tmp/cmdline-tools.zip" \
  https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  $tmp/cmdline-tools.zip" | sha256sum -c -
unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
export JAVA_HOME=~/.local/share/jdk/21
"$tmp/cmdline-tools/bin/sdkmanager" --sdk_root="$HOME/Android/Sdk" --licenses
"$tmp/cmdline-tools/bin/sdkmanager" --sdk_root="$HOME/Android/Sdk" \
  'cmdline-tools;latest' platform-tools emulator 'system-images;android-36;default;x86_64' \
  'platforms;android-37.0' 'build-tools;36.0.0'
rm -rf "$tmp"
```

`--licenses` asks you to accept each SDK license, eight of them on 2026-10-17. These packages need only `android-sdk-license`.

`sdkmanager` 23.0 hands each command to the new `android` CLI. On first use that CLI downloads itself into `~/.android`, and it sends usage data to Google unless it gets `--no-metrics`. It reads default flags from `~/.androidrc`, so opt out once:

```sh
echo --no-metrics > ~/.androidrc
```

### Set up the shell

The variables live in one file, `~/.config/android-env.sh`:

```sh
export JAVA_HOME="$HOME/.local/share/jdk/21"
export ANDROID_HOME="$HOME/Android/Sdk"
for _android_env_dir in "$JAVA_HOME/bin" "$ANDROID_HOME/emulator" \
    "$ANDROID_HOME/platform-tools" "$ANDROID_HOME/cmdline-tools/latest/bin"; do
    case ":$PATH:" in
        *":$_android_env_dir:"*) ;;
        *) PATH="$_android_env_dir:$PATH" ;;
    esac
done
unset _android_env_dir
export PATH
```

Source it at the end of `~/.profile`, and in `~/.bashrc` above the check that returns early for non-interactive shells:

```sh
if [ -f "$HOME/.config/android-env.sh" ]; then
    . "$HOME/.config/android-env.sh"
fi
```

A bash login shell reads both files, and the loop skips a directory that is already on `PATH`, so none of these directories appears twice. Check it from a clean login shell:

```sh
env -i HOME="$HOME" bash -l -c 'java -version && sdkmanager --list_installed'
```

Claude Code's Bash tool resets `PATH` to the `PATH` of the Claude Code process, so agents never get the JDK and SDK directories from these files. That `PATH` does include `~/.local/bin`. On this machine `java`, `adb`, `sdkmanager`, `avdmanager` and `emulator` in `~/.local/bin` are symlinks to `~/.local/libexec/android-env/tool-wrapper`, which sources `android-env.sh` and runs the real tool. A symlink straight to the tool breaks `sdkmanager` and `avdmanager`, which look for the SDK relative to their own path. Agent sessions run by t3 also get `JAVA_HOME` and `ANDROID_HOME`, because their shells read the top of `~/.bashrc`. Shells under `claude -p` don't get them, so a Gradle build there needs `ANDROID_HOME` set by hand or `sdk.dir` in `local.properties`.

### Run the emulator

The emulator needs KVM, which usually means your user is in the `kvm` group. `emulator -accel-check` says whether KVM works. Create the virtual device once:

```sh
avdmanager create avd -n tokkl-api36 -d medium_phone \
  -k 'system-images;android-36;default;x86_64'
```

Boot it without a window and wait until Android finishes starting:

```sh
emulator -avd tokkl-api36 -no-window -no-audio -no-boot-anim -no-snapshot -accel on &
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
adb devices
```

With `-accel on` the emulator refuses to start when KVM is missing, instead of falling back to slow software emulation. Stop the emulator and the adb server when you finish:

```sh
adb emu kill
adb kill-server
```

### Build and test

Build the debug APK, run lint and run the JVM unit tests:

```sh
./gradlew assembleDebug lint testDebugUnitTest
```

The instrumented tests, including the Compose UI tests, run against a real Tokkl server, never a fake one. One command runs them. It needs Docker, `curl` and `jq`:

```sh
scripts/instrumented-tests.sh
```

The script starts `ghcr.io/e0ipso/tokkl`, pinned by digest, on a free port of `127.0.0.1` with its database on a tmpfs. It creates an account with a random password, reads the account's API token, and passes the server URL and the credentials to `./gradlew connectedDebugAndroidTest` as instrumentation arguments. The URL is `http://10.0.2.2:<port>`, the address under which the emulator reaches the host. The script removes the container when it finishes, also when a test fails. If no device is attached, it boots `tokkl-api36` as in the previous section and stops it and the adb server at the end. An emulator that was already running keeps running. Arguments go to Gradle, so one class runs with:

```sh
scripts/instrumented-tests.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest
```

Without the script, `./gradlew connectedDebugAndroidTest` fails the tests that need the server. To move to a newer Tokkl, put the digest from `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:<version>` into `IMAGE` in the script.

`gradle.properties` caps the Gradle heap at 2 GB and compiles Kotlin inside the Gradle daemon, so no separate Kotlin daemon starts. The Gradle daemon itself stays in memory after the build. Stop it when you finish:

```sh
./gradlew --stop
```

## Commit messages

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/). `npm install` sets up a commit-msg hook that runs commitlint, which needs Node 22.12 or later. The hook lives in `.husky/_`, which git ignores, so run `npm install` once in every new clone or git worktree. Until you do, commits there skip the check. The hook rejects a ticket ID such as `TKA-0001` in the subject; put it in a `Refs: TKA-0001` trailer instead. It also rejects commits that credit an AI tool or a co-author (`Co-authored-by:`, "Generated with Claude Code" and the like). `npm test` checks these rules, and runs the hook against real commits and merges in a throwaway repository.

## License

MIT. See [LICENSE](LICENSE).
