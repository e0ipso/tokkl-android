---
type: map
title: Account timer state lifetime
description: >-
  AccountTimerStates owns intake, read-only timer state and retirement across
  sign-in and sign-out.
tags:
  - pomodoro
  - sync
  - offline
kk_schema_version: 3
kk_id: map-account-timer-state-lifetime
kk_derived_from: []
kk_relates_to:
  - map-account-cache-and-capabilities
  - map-timer-state-acceptance-and-persistence
  - map-online-timer-actions-and-native-control-failures
  - map-timer-event-stream-and-service-lifetime
kk_depends_on: []
kk_confidence: high
---
`AccountTimerStates` holds one source per account context of `session/CurrentAccount`, made on first use: the first of the process from the stored state, each later one from nothing. A sign-in hands the server, token and settings to core:session's `AccountTransition` (through its `signIn`, which `ServerSignIn` takes as `storeSignIn`; the app assembles it in `di/LifecycleModule.kt`), which stores the settings, calls `clearForSignIn` (retire the source, clear its store and the remembered Start fields) and then `CurrentAccount.begin`, before it stores the session, tells the `SignedInListener`s, lifts a refused token's queue pause and lets the timer sync follow, also when signing in again over a refused token. `AccountTransitionTest` pins that order. Only `AccountTimerStates` holds a source, and only it retires one. Readers take the read-only `CurrentTimerState` (the held state now, the account name and the controls) or its flows, `held`, `session`, `discardNotice` and `controls`; `withHeld` gives the account name and the held state together under the holder's lock. What feeds the state, `TimerActions`, the event stream and the account refresh's probe, takes the account's `TimerStateSource` through a `() -> TimerStateSource` that Koin fills with `AccountTimerStates::intake`, once per request or stream. `AccountTimerStates` is the one class every timer surface reads, as itself or through `CurrentTimerState` or `DiscardNotices`: it holds the source per account and gives the held state and session for the alarm and the widget, the too-short pomodoro notice, and the controls that put the cache's running entry and the pause chain together. Sign-out retires the source in its `RETIRE_STATE` phase, before it clears the cache and the store, so a late answer for the old account changes nothing.

<!-- kk:related:start -->
# Related

- Related: [map-account-cache-and-capabilities → map-account-cache-storage-and-history-window](../account-cache/map-account-cache-storage-and-history-window.md)
- Related: [map-account-cache-and-capabilities → map-account-refresh-steps-and-preference-readers](../account-cache/map-account-refresh-steps-and-preference-readers.md)
- Related: [map-account-cache-and-capabilities → map-capability-probes-and-persisted-feature-flags](../account-cache/map-capability-probes-and-persisted-feature-flags.md)
- Related: [map-timer-state-acceptance-and-persistence](map-timer-state-acceptance-and-persistence.md)
- Related: [map-online-timer-actions-and-native-control-failures](map-online-timer-actions-and-native-control-failures.md)
- Related: [map-timer-event-stream-and-service-lifetime](map-timer-event-stream-and-service-lifetime.md)
<!-- kk:related:end -->
