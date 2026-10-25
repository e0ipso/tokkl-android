---
type: practice
title: Reuse relays and throwaway server accounts
description: >-
  Shared relay helpers and a bounded failed-password budget keep real-server
  tests isolated.
tags:
  - testing
  - android
  - api
kk_schema_version: 3
kk_id: practice-reuse-relays-and-throwaway-server-accounts
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3'
kk_relates_to:
  - practice-use-the-pinned-test-server-and-tls-proxies
kk_depends_on: []
kk_confidence: high
---
Parse server times with `OffsetDateTime.parse`. Tokkl writes `+00:00` and Toggl `Z`, and `Instant.parse` refused the `+00:00` form on Android 11.

Reuse the helpers in `app/src/androidTest/java/com/github/e0ipso/tokkl/`:

- `LoopbackRelay` is the only relay. It owns the loop that relays the phone's 127.0.0.1 to a test server, with TLS passed through untouched. `watchConnections` gives each connection a `ConnectionWatcher` that sees every chunk sent and can hold back the server's answer. `holdNewConnections` accepts new connections and answers nothing, `dropHeld` and `cutRelayed` close held and relayed connections, and `relayTo` sends new connections to another server at the same local port.
- `HangingRelay` and `RequestCounter` build on it. `HangingRelay` can hang like a network that went quiet, cutting the connections it relayed, or hold back one answer with `holdAnswerTo` until `releaseAnswer`. `RequestCounter` counts the requests whose request line ends in a given path.
- `TestAccounts` invites throwaway accounts on the test server for tests that fill an account with data, and creates, reads and deletes their entries. The account the script creates stays empty.

Tokkl counts failed passwords per account and address, per account and per address, within 15 minutes. Every test reaches it from one address, whose counter stops at 100. `SignInRateLimitTest` uses a throwaway account and at most 11 attempts, 10 counted failures and the refused one, which leaves 90 for the rest of the run. Any new test that sends wrong passwords spends the same budget.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-pinned-test-server-and-tls-proxies](../instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3
<!-- kk:citations:end -->
