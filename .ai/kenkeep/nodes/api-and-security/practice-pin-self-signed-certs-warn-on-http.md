---
type: practice
title: Pin self-signed certificates by fingerprint and warn before plain HTTP
description: >-
  Trust a self-signed cert only by its pinned SHA-256 fingerprint, never accept
  any cert, and put plain HTTP behind a stronger warning.
tags:
  - security
  - network
kk_schema_version: 3
kk_id: practice-pin-self-signed-certs-warn-on-http
kk_derived_from:
  - docs/PRD.md
kk_relates_to:
  - map-api-client-shared-okhttp-serverurl-and-errors
  - map-connection-trust-store-pinning-and-prompts
kk_depends_on: []
kk_confidence: high
---
The app connects to any Tokkl server, however it is set up. The Tokkl server itself listens on plain HTTP, and HTTPS comes from a reverse proxy or `tailscale serve` in front of it. Real servers use a public certificate, a certificate from `tailscale cert`, a self-signed certificate, or Tokkl's default of plain HTTP on port 8080.

HTTPS with a publicly trusted certificate or a CA the user installed in Android works (S2). For a self-signed certificate the user reads a disclaimer and trusts it. The app shows the certificate's SHA-256 fingerprint, trusts that certificate only, and asks again when it changes. It never accepts any certificate without a check (S3). The app pins the certificate itself, separate from Android's trust store. Two instrumented tests hold both paths on a real server: `UserCaCertificateTest` signs in through a certificate that a user-installed CA signed, with no prompt and no pin, and `NewKeyWhileSignedInTest` swaps the server's key under a signed-in session and expects the changed-certificate prompt with the new fingerprint, then a connection once the user trusts it.

Plain `http://` works only after a warning stronger than the self-signed one. The warning says the token travels unencrypted unless the network encrypts it, as Tailscale does (S4). Android 9 and later block cleartext unless the app opts in, and Android 7 and later trust only system CAs by default. So `res/xml/network_security_config.xml` permits cleartext and trusts system and user-added CAs. The self-signed pin stays out of that file and goes into the trust manager of `SharedHttpClient`, the app's only `OkHttpClient`.

The app keeps the token or password encrypted with a key in the Android Keystore (S5). Client certificates (mTLS) are out of scope.

<!-- kk:citations:start -->
# Citations

[1] [docs/PRD.md](../../../../docs/PRD.md)
<!-- kk:citations:end -->
<!-- kk:related:start -->
# Related

- Related: [map-api-client-shared-okhttp-serverurl-and-errors](map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [map-connection-trust-store-pinning-and-prompts](map-connection-trust-store-pinning-and-prompts.md)
<!-- kk:related:end -->
