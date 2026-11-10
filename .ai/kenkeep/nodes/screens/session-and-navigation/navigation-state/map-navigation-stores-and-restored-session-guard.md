---
type: map
title: Navigation stores and restored session guard
description: >-
  Route and session ViewModel stores survive configuration changes, restore
  saved state, and reject expired sessions or previous-account drafts.
tags:
  - android
  - navigation
  - credentials
kk_schema_version: 3
kk_id: map-navigation-stores-and-restored-session-guard
kk_derived_from: []
kk_relates_to:
  - map-connection-trust-store-pinning-and-prompts
  - map-navigation-route-serialization-and-feature-registration
  - map-external-navigation-requests-and-intent-consumption
kk_depends_on: []
kk_confidence: high
---
Each entry's view models live in a store that Navigation 3's `ViewModelStoreNavEntryDecorator` clears when the entry leaves the stack, and the view models several screens share live in the session's store, under a key no route has, of the same `ViewModelStoreProvider`. `NavigationStores`, a view model of MainActivity, holds that provider, so both outlive a configuration change, registers a separate session saved-state registry under the activity so shared view models can restore their `SavedStateHandle` and sign-out drops their drafts, and drops it after every sign-out, also one that finished while MainActivity was made again. Lifecycle's `rememberViewModelStoreProvider` would not clear its stores in that case, which is why the host keeps its own. The host starts at sign-in until a server and token are stored, and at the Timer page after that. The sign-in feature binds `SignInRoutes`, which says whether somebody is signed in (it reads the stores on the IO dispatcher), gives the sign-in route and tells one apart, so the host names no `SignInRoute`. A back stack restored from saved state can outlive its session, after a sign-out that finished in the background or a token that no longer decrypts, so when its top is not sign-in the host checks `isSignedIn()` before creating the restored session's view models and goes to sign-in if nobody is signed in. A restored sign-in destination discards saved session drafts before the next account can open a page.

Above every signed-in screen the host shows the session's trust prompt, or after a cancel the notice that reopens it.

<!-- kk:related:start -->
# Related

- Related: [map-connection-trust-store-pinning-and-prompts](../../../api-and-security/map-connection-trust-store-pinning-and-prompts.md)
- Related: [map-navigation-route-serialization-and-feature-registration](map-navigation-route-serialization-and-feature-registration.md)
- Related: [map-external-navigation-requests-and-intent-consumption](map-external-navigation-requests-and-intent-consumption.md)
<!-- kk:related:end -->
