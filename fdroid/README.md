# F-Droid submission draft

`metadata/com.github.e0ipso.tokkl.yml` is a local fdroiddata draft for the
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

Build from the unchanged tag with JDK 21, Gradle 9.8.1, AGP 9.4.1,
compile SDK 37 and Build-Tools 36.0.0. The tag pins Gradle and AGP; the draft
selects JDK 21 and installs the matching SDK packages. F-Droid's builder must
provide Gradle 9.8.1 and an apt source with `openjdk-21-jdk-headless` before
this draft can be accepted. `subdir: app` and `gradle: [yes]` select the
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

Validation is deferred until backlog implementation is complete. At that gate,
run `node --test test/fdroid-metadata.test.js`, then copy the recipe to an
isolated fdroiddata checkout and run `fdroid lint com.github.e0ipso.tokkl` and
`fdroid checkupdates com.github.e0ipso.tokkl`. Confirm that a check restricted to
the `v0.3.0` source reports 0.3.0 / 3000; an unrestricted future check may find a
newer release. Check reproducibility against the public APK in the matching
F-Droid build environment before owner submission. None of those checks has
been run for this draft.
