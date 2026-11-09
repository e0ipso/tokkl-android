---
type: map
title: Sign-out ordering and account cleanup
description: >-
  Warn about unsent work and retire account tasks before clearing local session
  data.
tags:
  - android
  - credentials
  - offline
kk_schema_version: 3
kk_id: map-sign-out-ordering-and-account-cleanup
kk_derived_from: []
kk_relates_to:
  - map-offline-entry-mutations-and-local-id-mapping
  - map-account-cache-and-capabilities
  - map-api-token-store-and-backup-exclusion
kk_depends_on: []
kk_confidence: high
---
Settings signs out (S6). `settings/SignOutConfirmation` asks first, with the count, while the queue holds changes the server has not received, and signs out at once otherwise. `session/SignOut` runs one step for each `AccountDataOwner` that the Koin graph binds, off the main thread, phase by phase in `SignOutPhase` order. Each owner picks its phase, so core names no feature. Steps of one phase run one after another in no set order, so a component whose parts need an order runs them in one step, as `pomodoro/PomodoroSignOut` does for the phase alarm, the session notification and notification ID 3.

The phases are `FORGET_SESSION` (the `Session`: server, token and its Keystore key, so the app sends nothing new; and `reports/ExportAccount`, which ends every report export's lease, cancelling its download, and deletes the export files), `RETIRE_STATE` (`AccountTimerStates` retires the timer state source, so a late answer changes nothing), `STOP_WORK` (the Pomodoro alarm, ring and session notification, the timer event stream with its reconnects and sync service, the queue's background work, the running check and every call in flight through the shared client's `cancelAll()`, which `CallsSignOut` runs), `FINISH_REFRESH` (waits for a running refresh, which can start a drain), `FINISH_SENDING` (waits for the sender's drain), `CLEAR_CACHE` (the cache with the queue, which the notifications, widgets and shortcuts follow) and `CLEAR_DATA` (the timer state's store, the Pomodoro widget fields, the reminder and its settings, the running notification, the favorite shortcuts, the account settings, the capabilities, the Timer page's view, the Pomodoro defaults, the trust decisions, the session's trust prompt, the last answer and the discarded count). Sign-out does not end the session or pause on the server, so the user's other devices keep them. `ExportAccount` is also a `SignedInListener`: sign-in calls every bound listener once the new session is stored, so a sign-in over a refused token drops the exports of the account before it too. `SignOutTest` checks the phase order and the error handling; the device's `AppGraphTest` checks the phase of every owner, and the JVM `AppModulesTest` names every owner the graph binds.

A failed step does not stop the ones after it, `SignOut.run` returns false, and the next sign-out runs them all again. The theme settings and the sign-in cooldowns stay, because they belong to the phone and to a server, not to the account. A sign-out whose every step succeeded ends the account in `session/AccountLifetime`, which counts it in `signOuts`. `TokklNavHost` keys its navigation on that count and `NavigationStores` drops every entry and session view model when it changes, also after a sign-out that finished while `MainActivity` was made again, so the navigation starts over at sign-in with fresh view models and nothing a screen held in memory, such as the history's older days, survives. The Pomodoro session notification stays one object: sign-out retires it, and the next sign-in starts it under a new identity, so an old button acts on nothing.

<!-- kk:related:start -->
# Related

- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-account-cache-and-capabilities](../../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-api-token-store-and-backup-exclusion](../../api-and-security/map-api-token-store-and-backup-exclusion.md)
<!-- kk:related:end -->
