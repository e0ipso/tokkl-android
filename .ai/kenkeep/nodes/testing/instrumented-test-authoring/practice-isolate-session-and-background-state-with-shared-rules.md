---
type: practice
title: Isolate session and background state with shared rules
description: >-
  SessionRule retires prior account observers and work before composing the next
  test.
tags:
  - testing
  - android
  - offline
kk_schema_version: 3
kk_id: practice-isolate-session-and-background-state-with-shared-rules
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3'
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
kk_depends_on: []
kk_confidence: high
---
Reuse the helpers in `app/src/androidTest/java/com/github/e0ipso/tokkl/`:

- `SessionRule` sets the stored session before the activity starts, signed in to the test server or signed out for a test marked `@SignedOut`. It first clears every store and puts an empty in-memory test WorkManager in place, so the queue's background work runs only when a test's `TestDriver` says the network is there, and work one test scheduled never sends another test's queue. Process-start widget and shortcut observers may already have loaded the account before the rule runs. `signOut()` waits for `app.accountSettings.forget()` to finish an in-flight refresh, clears its stored settings, forgets loaded capabilities and clears their store, then clears the cache and the Pomodoro defaults store. This handoff prevents stale account data from surviving into the next test. Order the rule before the Compose rule. This applies to every test, so a change to it needs the full suite.
- `AirplaneMode` switches airplane mode through the shell user and waits until the client's requests fail, or get through again.
- `BackgroundTrip` sends `MainActivity` to the background with the home key and brings the same activity back.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3
<!-- kk:citations:end -->
