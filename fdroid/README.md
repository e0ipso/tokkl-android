# F-Droid submission draft

`metadata/com.canpicasoft.tokkl.yml` is a local fdroiddata draft for the
owner to submit. Nothing has been submitted. The package ID comes from the
filename. Store text and images remain in `fastlane/metadata/android/`.

The only build listed is the signed `v0.4.0` release, version code 4000,
at source commit `58308bdf297462b7ffa1b63bf9fcdd2e695d582b`. It is the first
release built as `com.canpicasoft.tokkl`; v0.1.0 to v0.3.0 shipped as
`com.github.e0ipso.tokkl` and cannot be listed under this recipe. Its public APK is
[tokkl-android-0.4.0.apk](https://github.com/e0ipso/tokkl-android/releases/download/v0.4.0/tokkl-android-0.4.0.apk),
SHA-256 `4b80c322e796340658dda90544b641f85ede090ead71d41953e60882e54e642e`.
`AllowedAPKSigningKeys` contains the signing **certificate** SHA-256, distinct
from the APK hash and the public key hash. `apksigner verify --print-certs`
on that APK prints the same certificate. No private signing material is needed.

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

Checked on 2026-10-26 with fdroidserver 2d1e622, the way fdroiddata's CI
checks a merge request. `fdroid lint` passed, and `fdroid rewritemeta` left
the recipe unchanged. fdroiddata's CI fails a recipe that rewritemeta would
change, so run it after every edit. `fdroid checkupdates` read 0.4.0 / 4000
from the `v0.4.0` tag and reported the recipe up to date. `fdroid build --test
--on-server com.canpicasoft.tokkl:4000` in the
`registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie` image found
nothing in the source scan, built the APK with `gradlew-fdroid` and Gradle
9.8.1, copied the signature from the GitHub APK onto it, and reported "compared
built binary to supplied reference binary successfully" for the allowed
signer. Repeat these checks for every build added to the recipe.
