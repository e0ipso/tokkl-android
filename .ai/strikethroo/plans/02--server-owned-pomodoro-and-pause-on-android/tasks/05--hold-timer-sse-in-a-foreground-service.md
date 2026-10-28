---
id: 5
group: "timer-state"
dependencies: [3, 4]
status: "pending"
created: 2026-10-28
skills:
  - android-services
  - okhttp
complexity_score: 7
complexity_notes: "The stream, cancellation and reconnect lifecycle must be one service implementation to avoid duplicate streams or account leaks. State storage, API actions, UI and device acceptance are already separate tasks; the transport and lifecycle criteria below bound this remaining integration."
execution_profile: "complex-architecture"
---
# Hold timer SSE in a foreground service

## Objective

Keep server timer changes reaching the signed-in phone in the background through one capability-gated SSE foreground service.

## Skills Required

Android foreground-service lifecycle and OkHttp streaming I/O.

## Acceptance Criteria

- [ ] A `specialUse` foreground service declares its permission and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, and posts a silent low-importance notification on its own channel.
- [ ] It runs only with a signed-in account and `timerState` capability, starts at app open and after sign-in, and is started from the existing boot/package-replacement receiver. Sign-out, capability loss and account replacement cancel the stream and all scheduled reconnects.
- [ ] `GET /api/app/timer/events` uses the shared client, existing trust/pinning and account Authorization header, honors configured URL prefixes and has no stream read timeout. No second HTTP client, SSE dependency or short polling loop is added.
- [ ] The parser accepts `event: timer` blocks containing the contract's single JSON `data:` line, ignores comments, counts keep-alives as liveness and feeds valid states to the holder. Fragmented reads do not break framing.
- [ ] About 60 seconds without a data/comment heartbeat closes the dead stream. Disconnects reconnect with capped exponential backoff; network return reconnects immediately without opening duplicate calls. Offline state and alarms remain available.
- [ ] HTTP 401/403 stops reconnecting and invokes the existing re-sign-in path. Shutdown and account changes prevent a late callback from publishing another account's state.
- [ ] Add `TimerEventStreamTest` and `TimerSyncLifecycleTest` for custom framing, watchdog/backoff and lifecycle decisions. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.timerstate.TimerEventStreamTest' --tests 'com.github.e0ipso.tokkl.timerstate.TimerSyncLifecycleTest' :app:lintDebug`; expect nonzero tests, zero failures and no new lint errors.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Build on the account holder, task 3's credential/error integration and task 4's capability flow. Extend timeout configuration at the shared-client request/call boundary, keeping `SingleHttpClientTest` valid. Retain the existing ringing service as a separate `mediaPlayback` service.

## Input Dependencies

Hard dependencies on tasks 3 and 4 for the account API/auth adapter and capability lifecycle. No soft dependencies. Task 12 owns API 31/API 36 start-path and Doze acceptance.

## Output Artifacts

Timer SSE service, parser/reconnect controller, silent notification channel, manifest entries, app/sign-in/network/receiver/sign-out wiring and focused transport/lifecycle checks.

## Implementation Notes

<details>
<summary>Stream and lifecycle procedure</summary>

1. Inspect `SharedHttpClient`, `NetworkWatch`, `ForegroundRefresh`, `TokklApplication`, the manifest and existing Pomodoro rearm receiver. List actual service-start entry points before changing them.
2. Implement a single owner for the active call and reconnect work. Inject time/scheduling into the custom reconnect controller so its watchdog and capped delays can be checked without real sleeps. Keep the last valid state on transport failures.
3. Parse SSE line by line from the response body, dispatch at a block boundary and refresh liveness on comments, including `: keep-alive`. Decode only timer events. A malformed/truncated state must not replace a valid held state; recover through the stream's ordinary failure/reconnect path.
4. Set the streaming timeout through existing shared HTTP facilities, adding a narrow request option if needed. Do not call `OkHttpClient.Builder` or `newBuilder()` in a new client module to get different security behavior.
5. Use foreground starts while the app is visible and the supported boot/update paths. Catch a platform refusal on an exempt start without crashing the receiver; the next app open can restart the stream, and the persisted exact alarm remains scheduled. Do not add a battery workaround or another transport.
6. Retire stream/retry work before clearing account stores, following sign-out's existing ordering. Stop retries on 401/403 and reuse the app's sign-in error path. Keep ordinary account refresh and the legacy-server RunningCheck behavior intact unless a later consumer no longer needs it.
7. Run the acceptance checks in the host's OOM-safe scope. Device evidence of boot, update, restart and Doze is deliberately collected by task 12 after all surfaces are migrated.

</details>
