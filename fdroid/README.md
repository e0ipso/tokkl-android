# F-Droid submission draft

`metadata/com.canpicasoft.tokkl.yml` is a local fdroiddata draft for the
owner to submit. Nothing has been submitted. The package ID comes from the
filename. Store text and images remain in `fastlane/metadata/android/`.

The only build listed is the signed `v0.3.0` release, version code 3000,
at source commit `1cdf6298978cc8c47b82f98ab4e2b7cf9854d647`. Its public APK is
[tokkl-android-0.3.0.apk](https://github.com/e0ipso/tokkl-android/releases/download/v0.3.0/tokkl-android-0.3.0.apk),
SHA-256 `8130cee2e04f263a4c93f78e89f5ba4877051b671bd752d10801f682585937cf`.
`AllowedAPKSigningKeys` contains the signing **certificate** SHA-256, distinct
from the APK hash and the public key hash. `apksigner verify --print-certs`
on that APK prints the same certificate. No private signing material is needed.
There is no proven `v0.1.0` APK to use in a recipe.

v0.1.0 to v0.3.0 shipped as `com.github.e0ipso.tokkl`. The app ID changed to
`com.canpicasoft.tokkl` before submission, so `v0.3.0` cannot be built under
this recipe. Before submitting, point the build entry at the first release
built with the new ID, add its store changelog, and repeat the checks below.

Build from the unchanged tag with JDK 21, Gradle 9.8.1, AGP 9.4.1,
compile SDK 37 and Build-Tools 36.0.0. The tag pins Gradle and AGP; the draft
selects JDK 21 and installs the matching SDK packages. F-Droid's sdkmanager
names the platform `platforms;android-37.0`; `platforms;android-37` does not
exist there. `subdir: app` and `gradle: [yes]` select the
unflavoured release build. Leave the release signing environment unset to
produce the unsigned APK for comparison with the public binary. Keep the tag's
dependency versions and build settings, including disabled `dependenciesInfo`.
Do not re-sign the upstream binary or patch version values to make it match.

The [metadata reference](https://f-droid.org/docs/Build_Metadata_Reference/)
defines `UpdateCheckData` paths relative to the repository root, even with
`subdir: app`. The `.` reuses the version-code file for the version name.
Both regexes return their value in the first capture group. Tags are compared
by numeric version code. `AutoUpdateMode: Version` takes the selected tag
directly, so it needs no extra pattern. `Binaries` expands `%v` in both the
release tag and asset filename.

Checked on 2026-10-26, under the old ID, with fdroidserver 2d1e622, the way fdroiddata's CI
checks a merge request. `fdroid lint` passed, and `fdroid rewritemeta` left
the recipe unchanged. fdroiddata's CI fails a recipe that rewritemeta would
change, so run it after every edit. `fdroid checkupdates` read 0.3.0 / 3000
from the `v0.3.0` tag and reported the recipe up to date. `fdroid build --test
--on-server com.github.e0ipso.tokkl:3000` in the
`registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie` image found
nothing in the source scan, built the APK with `gradlew-fdroid` and Gradle
9.8.1, copied the signature from the GitHub APK onto it, and reported "compared
built binary to supplied reference binary successfully" for the allowed
signer. Repeat these checks for every build added to the recipe.
