# Tokkl for Android

A native Android client for [Tokkl](https://github.com/e0ipso/tokkl) and Toggl Track.

The app is a Gradle project with a sign-in screen and, behind a bottom navigation bar like the web app's on phones, a Timer page that shows the brand until the timer exists, a reports screen with the summary report, placeholder pages for projects, clients, tags and the trash, a settings screen and a Licences screen so far, joined by a navigation host. The product requirements live in [docs/PRD.md](docs/PRD.md).

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
| System images | `system-images;android-36;default;x86_64`, revision 2, and `system-images;android-30;default;x86_64`, revision 11 | `~/Android/Sdk/system-images` |

The Gradle wrapper downloads Gradle 9.8.1 and checks it against the SHA-256 sum in `gradle/wrapper/gradle-wrapper.properties`. `gradle/libs.versions.toml` sets Android Gradle Plugin 9.4.1, Kotlin 2.4.21 and Compose BOM 2026.09.00. The app compiles against API 37, which Compose UI 1.12 and androidx.core 1.19 require. It targets API 36, the API level of the main test emulator. `app/build.gradle.kts` pins Build-Tools 36.0.0, which is also the default and the minimum of Android Gradle Plugin 9.4.1.

Android Gradle Plugin 9.4 needs JDK 17 or later, and JDK 21 is an LTS release above that floor. The system images are the plain AOSP builds of Android 16 (API 36) and Android 11 (API 30), not Google APIs or Play Store builds. They keep SystemUI and the launcher, which tests of the notification, the Quick Settings tile and the widget need. The smaller Automated Test Device images remove both. "Run the emulator" says why each image is there.

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
  'system-images;android-30;default;x86_64' 'platforms;android-37.0' 'build-tools;36.0.0'
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

The emulator needs KVM, which usually means your user is in the `kvm` group. `emulator -accel-check` says whether KVM works. Create the virtual devices once:

```sh
avdmanager create avd -n tokkl-api36 -d medium_phone \
  -k 'system-images;android-36;default;x86_64'
avdmanager create avd -n tokkl-api30 -d medium_phone \
  -k 'system-images;android-30;default;x86_64'
```

`tokkl-api36` runs Android 16, the version the app targets, and the screenshot references come from it. `tokkl-api30` runs Android 11, so the code for versions before Android 12 runs on it. There Android keeps no night mode for the app, so the starting window follows the system's mode. The wallpaper colours switch is hidden, and `android:allowBackup` alone keeps the app out of backup. The app's code branches only at Android 12, so API 30 takes the same paths as every version from Android 8.0 (API 26), the oldest the app supports.

The API 26 and API 28 images came first and were dropped. Apps there draw with OpenGL, and qemu grew by about 110 MB per test on both, still climbing after 20 tests, so a full run would outgrow the 6 GB the script gives the emulator. On API 28 the `swiftshader` and `host` renderers reached 6 GB within 20 tests, and `lavapipe` crashed after about 20. The SDK repository has no plain AOSP image for API 36.1 or later, only Google APIs, Play Store and Wear OS ones, so no emulator here runs the `cross-platform-transfer` rules in `data_extraction_rules.xml`.

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

`./gradlew check` runs the same lint and unit tests, plus `:app:checkDeniedDependencies`. That task fails when the release runtime classpath holds a module from a group listed in `gradle/denied-dependency-groups.txt`, such as Google Play Services, Firebase or an analytics SDK. The failure names each module and the dependencies that pulled it in.

The instrumented tests, including the Compose UI tests, run against a real Tokkl server, never a fake one. One command runs them. It needs Docker, `curl`, `jq` and `openssl`:

```sh
scripts/instrumented-tests.sh
```

The script starts `ghcr.io/e0ipso/tokkl`, pinned by digest, on a free port of `127.0.0.1` with its database on a tmpfs. It creates an account with a random password and reads the account's API token. The password and the token go to each test device in a file under `/data/local/tmp` that only the shell user can read, and the tests read that file through UiAutomation. As instrumentation arguments they would show in `ps` on the build machine, in the arguments of the adb process that starts the tests. The script also puts `haproxy`, pinned by digest, in front of the same server as a TLS proxy, so the tests can trust a self-signed certificate by its fingerprint. It makes that certificate for each run with `openssl`. The certificate names `10.0.2.2` as an IP address, so the app's host name check stays on and passes. Its private key stays in the script's temporary directory, which only you can read. On a port of its own, the same proxy serves Tokkl over http under the path prefix `/tokkl`, as a reverse proxy serves it beside other sites, and answers 404 to every other path there, so a client that drops the prefix fails. `./gradlew connectedDebugAndroidTest` gets the three server URLs, the certificate's SHA-256 fingerprint, the email and the file's path as instrumentation arguments. The URLs are `http://10.0.2.2:<port>`, `https://10.0.2.2:<port>` and `http://10.0.2.2:<port>/tokkl`, at the address under which the emulator reaches the host. The script removes both containers, the key and the file when it finishes, also when a test fails. A run killed before its cleanup leaves its file behind, so before it copies its own, the script deletes the ones more than six hours old. It also fails when a test device goes away during the run, because Gradle can report success without running every test. Runs take turns: each holds a lock in `$XDG_RUNTIME_DIR` until its cleanup ends, and a run started meanwhile names the run that holds it and waits. Otherwise a second run would test on the first run's emulator, which the first run's cleanup stops.

If no device is attached, the script boots `tokkl-api36` as in the previous section, or the AVD that `TOKKL_TEST_AVD` names, with 2.5 GB of guest RAM, 4 cores and the `lavapipe` renderer, and stops it and the adb server at the end. When your systemd user manager is running, the emulator runs in a scope of its own with `OOMPolicy=continue` and at most 6 GB of memory. If the kernel's OOM killer ends qemu, the script fails and says so, and systemd leaves the session that started it alone. On an emulator it boots, the script also turns on `hide_error_dialogs` and closes open system dialogs, because a fresh headless boot often leaves "System UI isn't responding" in front of the app. It also sets the font scale to 1.0, because the AVD keeps the last scale a test set across boots. It turns on `show_ime_with_hard_keyboard` too, since otherwise the soft keyboard never opens on the headless emulator. On Android 12 and later it sets `debug.hwui.renderer` to `skiavk`, so apps draw with Vulkan: qemu kept about 100 MB after every app process that drew with OpenGL, and the test orchestrator starts one per test, so a full run took qemu to 11 GB instead of 3.5 GB. Below Android 12 HWUI aborted every app at its first frame with `skiavk`, so apps there draw with OpenGL. A full run on `tokkl-api30` took qemu to 5.5 GB, close to that 6 GB cap. It changes no setting on an emulator that was already running, and that emulator keeps running. Arguments go to Gradle, so one class runs with:

```sh
scripts/instrumented-tests.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest
```

Run the suite on `tokkl-api30` with:

```sh
TOKKL_TEST_AVD=tokkl-api30 scripts/instrumented-tests.sh
```

A second run waits for the first one's lock, so the two emulators never run at once.

Without the script, `./gradlew connectedDebugAndroidTest` fails the tests that need the server. To move to a newer Tokkl, put the digest from `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:<version>` into `IMAGE` in the script. `PROXY_IMAGE` takes the digest of `haproxy:<version>-alpine` the same way.

`gradle.properties` caps the Gradle heap at 2 GB and compiles Kotlin inside the Gradle daemon, so no separate Kotlin daemon starts. The Gradle daemon itself stays in memory after the build. Stop it when you finish:

```sh
./gradlew --stop
```

### Screenshot tests

`app/src/androidTest/java/com/github/e0ipso/tokkl/ui/screenshot/` captures sign-in with each method, the Timer page with its List and Calendar views, Settings, Licences, Reports, the http warning and the certificate prompt, in the light and the dark theme, at font scale 1.0 and 2.0. Each capture shrinks to a third in each direction and is compared with its reference image in `app/src/androidTest/assets/screenshots/`. The references come from `tokkl-api36`, because density and system fonts change the pixels. Each class carries `@SdkSuppress` for API 36, so on any other version the runner leaves the screenshot tests out. An assumption would not do, because the Android Gradle plugin reports a failed assumption as a failed test. Run all four classes with:

```sh
scripts/instrumented-tests.sh \
  -Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.ui.screenshot
```

A capture that differs from its reference, or has none, fails its test. The capture and a diff, with the differing pixels in magenta, land in `actual/` and `diff/` under `app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/<device>/screenshots/`. A change to how a screen looks updates its references in the same commit. Look at every diff and capture. When all of them show the intended change, copy them into the references and run the tests again:

```sh
cp app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/*/screenshots/actual/*.png \
  app/src/androidTest/assets/screenshots/
```

## Release builds

F-Droid builds the app from source and compares its unsigned APK with the signed APK on the GitHub release. It publishes the APK signed with the project's key only when the two match apart from the signature ([reproducible builds](https://f-droid.org/docs/Reproducible_Builds/)). People who sideload install the same APK from the GitHub release.

### Check that the release build is reproducible

```sh
scripts/reproducible-release.sh          # HEAD
scripts/reproducible-release.sh v0.1.0   # a tag or any other commit
```

The script clones the commit twice, runs `./gradlew --no-daemon assembleRelease` in each clone without the signing variables below, and compares the two unsigned APKs byte for byte. It builds committed work only. The clones sit at paths of different lengths. The second build runs on at most two CPU cores and gets its own Gradle user home, which reads downloaded dependencies from yours but redoes every transform. So an embedded path, output that depends on the core count, and a transform that differs from run to run all show up as differences. When the APKs differ, the script lists the zip entries that differ and keeps both APKs. On this machine the two builds take about four minutes and use up to about 700 MB under `build/`, which the script removes at the end. CI runs the script on pull requests to `main` and on pushes to `main`.

These keep the APK the same from build to build:

- `app/build.gradle.kts` pins Build-Tools 36.0.0. The wrapper pins Gradle 9.8.1 by checksum, and `gradle/libs.versions.toml` pins the Android Gradle Plugin, Kotlin and every library. CI builds with Temurin JDK 21, a version F-Droid's build server also offers.
- Every zip entry carries the fixed date 1981-01-01 and no extra timestamp field.
- The manifest records `platformBuildVersionName='17'` and `platformBuildVersionCode='37'`, which come from the `platforms;android-37.0` package, revision 2 here. Gradle cannot pin a platform revision, so a newer revision on F-Droid's server could change them.
- The Android Gradle Plugin writes the commit hash, and no path, into `META-INF/version-control-info.textproto`. F-Droid asks to keep that file and to build the release APK from a clean checkout of the tagged commit. So build the GitHub release APK once the release commit and its tag exist, never from a tree with uncommitted changes, such as one where the release step has just rewritten `gradle/version.properties`.
- The ART baseline profile, `assets/dexopt/baseline.prof` and `baseline.profm`, stays in the APK because it speeds up start-up and came out the same on 8 and on 2 cores. F-Droid's docs say it is sometimes unstable. If the script ever shows it differing, turn off the tasks whose names contain `ArtProfile`, as those docs describe.
- `dependenciesInfo` is off. Otherwise the Android Gradle Plugin adds the dependency list to the signing block of the signed APK, encrypted with a key only Google holds, and F-Droid fails a build whose APK carries that block.

### Sign the release APK

Release signing reads four environment variables and nothing else:

| Variable | Value |
| --- | --- |
| `TOKKL_RELEASE_KEYSTORE` | Path of the keystore file. Use an absolute path; a relative one counts from `app/`. |
| `TOKKL_RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `TOKKL_RELEASE_KEY_ALIAS` | Alias of the key in the keystore |
| `TOKKL_RELEASE_KEY_PASSWORD` | Key password, the same as the keystore password in a PKCS12 keystore |

With all four set, `./gradlew assembleRelease` writes the signed `app/build/outputs/apk/release/app-release.apk`. With none set, it writes `app-release-unsigned.apk` instead. With only some set, the build fails and names the missing ones. An empty variable counts as not set, which is what Actions passes for a secret that does not exist.

The owner creates the key once, outside the repo:

```sh
keytool -genkeypair -keystore ~/tokkl-release.p12 -storetype PKCS12 \
  -alias tokkl -keyalg RSA -keysize 4096 -validity 10000
```

keytool asks for the keystore password and for the name on the certificate. A PKCS12 keystore protects the key with the keystore password, so the key password is the same one. Android installs an update only when it is signed with the same key as the installed app, so back up the keystore and its password, for example in a password manager. `.gitignore` ignores `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `*.pk8`, `*.key` and `*.pem`, but keep the keystore outside the checkout anyway.

CI gets the key from four GitHub Actions secrets. Set them from the repo directory; `gh secret set` without `--body` asks for the value, so the passwords stay out of the shell history:

```sh
base64 -w0 ~/tokkl-release.p12 | gh secret set TOKKL_RELEASE_KEYSTORE_BASE64
gh secret set TOKKL_RELEASE_KEYSTORE_PASSWORD
gh secret set TOKKL_RELEASE_KEY_ALIAS --body tokkl
gh secret set TOKKL_RELEASE_KEY_PASSWORD
```

The release job writes the keystore to the runner's temporary directory and points `TOKKL_RELEASE_KEYSTORE` at it. No workflow has this step yet; the release workflow adds it once the repo is on GitHub:

```yaml
- name: Build the signed release APK
  env:
    TOKKL_RELEASE_KEYSTORE_BASE64: ${{ secrets.TOKKL_RELEASE_KEYSTORE_BASE64 }}
    TOKKL_RELEASE_KEYSTORE_PASSWORD: ${{ secrets.TOKKL_RELEASE_KEYSTORE_PASSWORD }}
    TOKKL_RELEASE_KEY_ALIAS: ${{ secrets.TOKKL_RELEASE_KEY_ALIAS }}
    TOKKL_RELEASE_KEY_PASSWORD: ${{ secrets.TOKKL_RELEASE_KEY_PASSWORD }}
  run: |
    export TOKKL_RELEASE_KEYSTORE="$RUNNER_TEMP/release.p12"
    printf '%s' "$TOKKL_RELEASE_KEYSTORE_BASE64" | base64 -d >"$TOKKL_RELEASE_KEYSTORE"
    ./gradlew assembleRelease
```

The Android Gradle Plugin signs the APK itself. Don't sign it again with `apksigner` from Build-Tools 35 or later: F-Droid's docs say its signature copy check fails on APKs signed that way.

F-Droid's `AllowedAPKSigningKeys` takes the SHA-256 fingerprint of the signing certificate in lower-case hex. This prints it from a signed APK:

```sh
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk | grep 'SHA-256'
```

Before any APK exists, keytool prints the same value from the keystore:

```sh
keytool -list -v -keystore ~/tokkl-release.p12 -alias tokkl \
  | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]'
```

F-Droid checks a release by copying the signature from the GitHub APK onto its own unsigned build with [apksigcopier](https://github.com/obfusk/apksigcopier). This runs the same check locally:

```sh
apksigcopier compare --unsigned app-release.apk app-release-unsigned.apk
```

## Commit messages

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/). `npm install` sets up a commit-msg hook that runs commitlint, which needs Node 22.12 or later. The hook lives in `.husky/_`, which git ignores, so run `npm install` once in every new clone or git worktree. Until you do, commits there skip the check. The hook rejects a ticket ID such as `TKA-0001` in the subject; put it in a `Refs: TKA-0001` trailer instead. It also rejects commits that credit an AI tool or a co-author (`Co-authored-by:`, "Generated with Claude Code" and the like). `npm test` checks these rules, and runs the hook against real commits and merges in a throwaway repository. The local hook never sees a commit made with `git revert` or `--no-verify`, so the commitlint job in `.github/workflows/ci.yml` also lints the commits of every pull request and push to `main`, and runs `npm test`.

## License

MIT. See [LICENSE](LICENSE).
