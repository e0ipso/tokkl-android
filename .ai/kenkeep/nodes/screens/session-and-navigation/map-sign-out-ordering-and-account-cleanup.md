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
Settings signs out (S6). `settings/SignOutModel` asks first, with the count, while the queue holds changes the server has not received, and signs out at once otherwise. `session/SignOut` runs the steps that `TokklApplication.signOut` lists, in order, off the main thread. The first forgets the session, the server and the token with its Keystore key, so the app counts as signed out and sends nothing new. Then it cancels the queue's background work and every call in flight (`SharedHttpClient.cancelAll()`), waits for the refresh and the sender's drain to end, and clears the cache with the queue, the account settings, the capabilities, the Timer page's view, the trust decisions and the session's trust prompt. A failed step does not stop the ones after it, and the next sign-out runs them all again. The theme settings and the sign-in cooldowns stay, because they belong to the phone and to a server, not to the account. `SignOut.finished` counts finished sign-outs. `MainActivity` drops its view models when it changes and keys `TokklNavHost` on it, so the navigation starts over at sign-in with fresh models and nothing a screen held in memory, such as the history's older days, survives.

<!-- kk:related:start -->
# Related

- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [map-account-cache-and-capabilities](../../data-and-offline/map-account-cache-and-capabilities.md)
- Related: [map-api-token-store-and-backup-exclusion](../../api-and-security/map-api-token-store-and-backup-exclusion.md)
<!-- kk:related:end -->
