# Tokkl for Android

A native Android client for [Tokkl](https://github.com/e0ipso/tokkl) and Toggl Track.

There is no code yet. The product requirements are being collected in [docs/PRD.md](docs/PRD.md).

## Building

There is no Gradle project yet. These steps prepare a Linux x86_64 machine to build and test it. Everything installs under `$HOME` and needs no root.

| Component | Version | Path |
| --- | --- | --- |
| JDK | Eclipse Temurin 21.0.12.1+1 | `~/.local/share/jdk/21` |
| Android SDK Command-line Tools | 23.0 | `~/Android/Sdk/cmdline-tools/latest` |
| Android SDK Platform-Tools | 37.0.1 | `~/Android/Sdk/platform-tools` |
| Android Emulator | 37.2.12 | `~/Android/Sdk/emulator` |
| System image | `system-images;android-36;default;x86_64`, revision 2 | `~/Android/Sdk/system-images` |

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
  'cmdline-tools;latest' platform-tools emulator 'system-images;android-36;default;x86_64'
rm -rf "$tmp"
```

`--licenses` asks you to accept each SDK license, eight of them on 2026-10-17. These packages need only `android-sdk-license`.

`sdkmanager` 23.0 hands each command to the new `android` CLI. On first use that CLI downloads itself into `~/.android`, and it sends usage data to Google unless it gets `--no-metrics`. It reads default flags from `~/.androidrc`, so opt out once:

```sh
echo --no-metrics > ~/.androidrc
```

### Set up the shell

Add this to `~/.profile`, then log in again:

```sh
export JAVA_HOME="$HOME/.local/share/jdk/21"
export ANDROID_HOME="$HOME/Android/Sdk"
PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export PATH
```

Check it from a clean login shell:

```sh
env -i HOME="$HOME" bash -l -c 'java -version && sdkmanager --list_installed'
```

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

## License

MIT. See [LICENSE](LICENSE).
