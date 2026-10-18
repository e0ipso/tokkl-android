---
type: map
title: 'Connection trust: ServerTrustStore, PinningTrustManager and the trust prompts'
description: >-
  The shared client refuses unconfirmed http and unpinned self-signed
  certificates. SessionTrustModel prompts on every screen but sign-in.
tags:
  - security
  - network
  - ui
  - dialogs
kk_schema_version: 3
kk_id: map-connection-trust-store-pinning-and-prompts
kk_derived_from: []
kk_relates_to:
  - practice-pin-self-signed-certs-warn-on-http
  - map-api-client-shared-okhttp-serverurl-and-errors
  - map-signin-session-settings-and-navigation-host
  - map-account-settings-source-and-account-time
kk_depends_on: []
kk_confidence: high
---
`api/ServerTrust` is what the user has agreed to for a server: one plain `http://` URL whose requests may carry the token or password unencrypted, and one pinned certificate, as host, port and the SHA-256 of its leaf. `session/ServerTrustStore` keeps both in the private preferences file `server_trust`, and a new confirmation or pin replaces the old one. `TokklApplication.onCreate` hands the store to `ConfiguredTrust`. Until then nothing is trusted. Nothing calls `ServerTrustStore.clear()` yet, and sign-out has to.

`SharedHttpClient` checks this on every request and every handshake, so no screen or background job can skip the question. `ConfiguredServerGuard` throws `CleartextNotConfirmedException` for an `http://` server the user has not confirmed, before OkHttp connects. `PinningTrustManager` asks the platform trust manager first, so system and user CAs and the network security config apply as before. When the platform refuses the chain, it accepts only the leaf whose SHA-256 matches the pin for that host and port. OkHttp still checks the host name against the leaf. `NoTrustAllTest` fails on any other trust manager or host name verifier in the app, its tests or the scripts. JVM certificate fixtures are `.crt` files in `app/src/test/resources/certificates`, because `.gitignore` ignores `*.pem`, and `PinningTrustManagerTest` fails when one holds a private key.

`TogglApiClient` turns the refusals into `ApiError.CleartextNotConfirmed` and `ApiError.UntrustedCertificate`, both `ApiError.TrustRefused`, and reports them to its `TrustListener`. For the signed-in session that listener is `trust/SessionTrustModel`, and `TokklNavHost` shows its prompt from `trust/TrustPrompts.kt` above every screen except sign-in, which handles its own. Accepting stores the pin or the confirmation and runs the account settings refresh again. Cancelling keeps the requests refused and shows `TrustRefusedNotice`, whose "Review connection" button opens the prompt again. The prompt strings keep their `sign_in_*` names although every screen shows them.

Neither trust dialog closes on a tap outside or on Back, and the http warning needs a tick before its continue button works. `TrustActions` puts Cancel and then the risky action in one slot, stacked full width as `TextButton`s, so the risky action is never the filled call to action. At 200% font, `AlertDialog`'s own button row in material3 1.4.0 put Cancel over a Continue that wrapped to two lines, and the touch targets overlapped. `LargestFontScaleTest` opens both prompts at the largest font scale and checks with `assertDialogFits` that the dialog's scrolling area, title and buttons do not overlap.

<!-- kk:related:start -->
# Related

- Related: [practice-pin-self-signed-certs-warn-on-http](practice-pin-self-signed-certs-warn-on-http.md)
- Related: [map-api-client-shared-okhttp-serverurl-and-errors](map-api-client-shared-okhttp-serverurl-and-errors.md)
- Related: [map-signin-session-settings-and-navigation-host](map-signin-session-settings-and-navigation-host.md)
- Related: [map-account-settings-source-and-account-time](map-account-settings-source-and-account-time.md)
<!-- kk:related:end -->
