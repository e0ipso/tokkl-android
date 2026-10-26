---
type: practice
title: Use the pinned test server and TLS proxies
description: >-
  Disposable Tokkl, prefixed HTTP and independent TLS keys provide real endpoint
  and trust fixtures.
tags:
  - testing
  - android
  - docker
  - api
kk_schema_version: 3
kk_id: practice-use-the-pinned-test-server-and-tls-proxies
kk_derived_from:
  - scripts/instrumented-tests.sh
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4'
kk_relates_to:
  - practice-let-the-runner-own-emulator-boot-and-settings
  - practice-test-real-tokkl-server-speed-regressions
kk_depends_on: []
kk_confidence: high
---
The script starts `ghcr.io/e0ipso/tokkl` pinned by digest, Tokkl 1.8.1, and publishes it on a free port of 127.0.0.1, which the emulator reaches as `10.0.2.2`. The database sits on `--tmpfs /data:mode=1777`, because the image runs as an unprivileged user that cannot write to a default tmpfs. The script creates the account through `POST /api/app/setup` with a random `TOKKL_SETUP_CODE` and password, reads the API token from `GET /api/v9/me`, and passes `tokklBaseUrl`, `tokklEmail` and `tokklCredentialsFile` to Gradle as `-Pandroid.testInstrumentationRunnerArguments.*`. It removes the containers on exit, also on failure. To move to a newer Tokkl, put the digest from `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:<version>` into `IMAGE` in the script. `PROXY_IMAGE` takes the digest of `haproxy:<version>-alpine` the same way.

In front of the same server the script runs haproxy, pinned by digest in `PROXY_IMAGE`, in the Tokkl container's network namespace. As a TLS proxy it serves a self-signed certificate that `openssl` makes for each run, with IP SANs for `10.0.2.2` and `127.0.0.1`, so host name checks pass. Its key stays in the script's temporary directory, and cleanup deletes it. The tests get `tokklTlsBaseUrl` and the certificate's `tokklTlsFingerprint`, never the key, through `TokklTestServer.tlsUrl` and `tlsFingerprint`. On another port the proxy serves Tokkl over http under the path prefix `/tokkl` and answers 404 to every other path there, so a client that drops the prefix fails. Tests reach it as `tokklPrefixedBaseUrl`, through `TokklTestServer.prefixedUrl`.

The proxy serves the same server on two more TLS ports. Port 8444 has a second self-signed certificate with the same names and subject and a key of its own, as after the server's key was regenerated. Tests reach it through `TokklTestServer.newKeyTlsUrl` and `newKeyTlsFingerprint`, as `NewKeyWhileSignedInTest` does. Port 8445 has a certificate that a CA made for the run signed, reached through `TokklTestServer.userCaTlsUrl`. On an emulator it boots, the script installs that CA as a user CA, the way Settings > Security > Install a certificate would. Only root may write `/data/misc/user/0/cacerts-added`, so it copies the CA there with `adb shell su 0`, which the userdebug AOSP images allow, under the old OpenSSL subject hash plus `.0`. Cleanup deletes it, and a CA left by a killed run has the same name, so the next run replaces it. Mark a test that needs the CA `@UserCaInstalled`, as `UserCaCertificateTest` is. On a device the script did not boot it installs nothing and leaves those tests out through the runner's `notAnnotation` argument, unless `TOKKL_TEST_THROWAWAY_EMULATOR=1` vouches that every attached device is an emulator thrown away after the run, as in CI. The script then installs the CA there too, and it refuses the variable for a device that is not an emulator or whose `su` does not run as root.

<!-- kk:related:start -->
# Related

- Related: [practice-let-the-runner-own-emulator-boot-and-settings](../emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md)
- Related: [practice-test-real-tokkl-server-speed-regressions](../practice-test-real-tokkl-server-speed-regressions.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:4
<!-- kk:citations:end -->
