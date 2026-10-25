---
type: map
title: Saveable navigation stack and session guard
description: >-
  Destination names survive restoration, while the host checks the session and
  presents trust prompts.
tags:
  - android
  - ui
  - navigation
kk_schema_version: 3
kk_id: map-saveable-navigation-stack-and-session-guard
kk_derived_from: []
kk_relates_to:
  - map-connection-trust-store-pinning-and-prompts
kk_depends_on: []
kk_confidence: high
---
`TokklNavHost` is the whole navigation, with no navigation library: a back stack of `Destination`s held in `rememberSaveable`, so it survives configuration changes and process death. The stack is saved by name, and a name this version no longer has, such as an older version's `HOME`, is dropped. It starts at sign-in until a server and token are stored, and at the Timer page after that. A back stack restored from saved state can outlive its session, after a sign-out that finished in the background or a token that no longer decrypts, so when its top is not `SIGN_IN` the host checks `Session.isSignedIn()` once, off the main thread, and goes to sign-in if nobody is signed in. Above every signed-in screen it shows the session's trust prompt, or after a cancel the notice that reopens it.

<!-- kk:related:start -->
# Related

- Related: [map-connection-trust-store-pinning-and-prompts](../../api-and-security/map-connection-trust-store-pinning-and-prompts.md)
<!-- kk:related:end -->
