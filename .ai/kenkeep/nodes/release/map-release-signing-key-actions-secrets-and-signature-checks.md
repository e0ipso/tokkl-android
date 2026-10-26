---
type: map
title: 'Release signing key, Actions secrets and signature checks'
description: >-
  The owner's PKCS12 key signs releases through four Actions secrets.
  Fingerprint and apksigcopier commands check what F-Droid checks.
tags:
  - release
  - fdroid
  - security
  - ci
kk_schema_version: 3
kk_id: map-release-signing-key-actions-secrets-and-signature-checks
kk_derived_from: []
kk_relates_to:
  - practice-reproducible-builds-project-key
  - practice-own-version-from-v0-1-0-tag
  - map-fdroid-metadata-release-artifacts
kk_depends_on: []
kk_confidence: high
---
The owner creates the release key once, outside the repo, and backs up the keystore and its password, for example in a password manager. Android installs an update only when it carries the same signature as the installed app, so losing the key ends updates. `.gitignore` ignores `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `*.pk8`, `*.key` and `*.pem`, but the keystore still belongs outside the checkout.

```sh
keytool -genkeypair -keystore ~/tokkl-release.p12 -storetype PKCS12 \
  -alias tokkl -keyalg RSA -keysize 4096 -validity 10000
```

With all four `TOKKL_RELEASE_*` variables set, `./gradlew assembleRelease` writes the signed `app/build/outputs/apk/release/app-release.apk`; with none, `app-release-unsigned.apk`. A relative `TOKKL_RELEASE_KEYSTORE` counts from `app/`, so use an absolute path. An empty variable counts as unset, which is what Actions passes for a missing secret.

CI reads four Actions secrets. Set them from the repo directory. `gh secret set` without `--body` prompts for the value, which keeps the passwords out of shell history:

```sh
base64 -w0 ~/tokkl-release.p12 | gh secret set TOKKL_RELEASE_KEYSTORE_BASE64
gh secret set TOKKL_RELEASE_KEYSTORE_PASSWORD
gh secret set TOKKL_RELEASE_KEY_ALIAS --body tokkl
gh secret set TOKKL_RELEASE_KEY_PASSWORD
```

The `release` job checks that all four exist before it creates a release. `android-release`, with read-only repository access, writes the keystore to the runner's temporary directory with private permissions, removes it when the build step ends, builds with `./gradlew --no-daemon --no-build-cache assembleRelease`, checks the result with `apksigner verify` and uploads only the signed APK. `android-publish` holds the write token.

F-Droid's `AllowedAPKSigningKeys` takes the certificate's SHA-256 in lower-case hex. Read it from a signed APK with `"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk | grep SHA-256`, or before any APK exists with `keytool -list -v -keystore ~/tokkl-release.p12 -alias tokkl | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]'`. F-Droid copies the signature from the GitHub APK onto its own unsigned build with apksigcopier. `apksigcopier compare --unsigned app-release.apk app-release-unsigned.apk` runs the same check locally.

<!-- kk:related:start -->
# Related

- Related: [practice-reproducible-builds-project-key](practice-reproducible-builds-project-key.md)
- Related: [practice-own-version-from-v0-1-0-tag](practice-own-version-from-v0-1-0-tag.md)
- Related: [map-fdroid-metadata-release-artifacts](map-fdroid-metadata-release-artifacts.md)
<!-- kk:related:end -->
