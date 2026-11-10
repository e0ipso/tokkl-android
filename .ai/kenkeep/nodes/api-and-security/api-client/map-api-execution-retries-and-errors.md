---
type: map
title: API execution retries and errors
description: >-
  Cancellable reads, application-owned writes, send-once mutations and typed
  response failures.
tags:
  - api
  - network
  - coroutines
kk_schema_version: 3
kk_id: map-api-execution-retries-and-errors
kk_derived_from: []
kk_relates_to:
  - map-timer-state-holder-and-sync-service
  - map-typed-api-resources-and-request-contracts
kk_depends_on: []
kk_confidence: high
---
`ApiTransport`'s calls, and so every `TokklApi` method, suspend. Each switches to the client's IO dispatcher and runs `Call.execute` there through `executeCancellable` (`ApiTransport.kt`), which cancels the OkHttp call when the coroutine is cancelled, so a page that goes stops its reads at once. The call runs on that thread, not on OkHttp's dispatcher, so the dispatcher's limit of five calls per host never queues a request behind the timer event stream, and the exceptions that tell whether a request left the phone stay those of `execute`. A cancelled call throws the cancellation instead of returning an `ApiResult`. The catalog pages and the trash send each change through core:session's `ServerChanges`, which runs it in the application scope and starts the cache refresh once the server accepts it, so the change and its refresh land when the page goes meanwhile; the page awaits the answer and shows it while it is still there. Other writes the user sent from a screen (the timer templates through `FavoritesServer`, sign-in through `SignIn`, the timer actions through `TimerActions` and Undo through `DeleteUndoer`) run in the application's scope, as `ServerChanges` does, and the caller only waits for the answer, so they land and their answer is recorded when the screen goes meanwhile. A read, such as `TimerActions.read`, runs in the caller's coroutine and stops with it. `NonCancellable` stays only around bounded clean-up, such as an export's staged file. The queue, the refresh and the preference sends run in the application scope, which nothing cancels. `ApiCallCancellation` stays for the CSV export and the event stream, which an account change or the stream's silence cancels from outside.

`ApiTransport.get` and `send` decode a 2xx body with a kotlinx.serialization deserializer, and an empty body fails to decode. Calls that expect no content use `sendNoContent`, which treats any 2xx as success and ignores the body. Tokkl's DELETE routes for time entries, tasks, clients and tags answer 200 with an empty body, while the project DELETE answers 200 with the project id as JSON (`../tokkl/packages/api/src/routes/togglV9.ts`). `sendNoContent` handles both.

When a connection OkHttp reused fails after the request went out, OkHttp sends the request again on a new connection, so a POST whose answer was lost can create its object twice. `send(..., resend = false)` sends through `SharedHttpClient.sendingOnce`, a client derived with `retryOnConnectionFailure(false)` that shares the pool, interceptors and trust checks, and the caller gets the failure instead. `TokklApi` sends that way every time-entry write (create, update, stop and delete), the restore of a queued short Stop, and every catalog create.

`sendRefusable` serves the timer actions, which answer a refusal with a 409 whose body has the same type as their 2xx answer, the timer state. It sends once (`resend = false`) and returns `Refusable(value, refused)`: `refused` is true for a 409 body that decodes. A 409 whose body is a JSON string becomes `ApiError.UnexpectedStatus` with that reason. A request tagged `StreamingRead` reads its body with no read timeout, for the timer event stream; `StreamingReads` lifts that one timeout and nothing else.

Every call returns `ApiResult.Success` or `ApiResult.Failure(ApiError)`. `ApiResult.kt` has `map`, `flatMap` for the next call, `fold`, `getOrElse`, `onSuccess` and `onFailure`, so a caller passes a failure on with `getOrElse { return it }` instead of a `when`. 403 maps to `AuthFailed`, 429 to `RateLimited` with the `Retry-After` seconds (null when the header is missing or not a whole number), and any other status outside 2xx to `UnexpectedStatus(status, message)`. `message` is the server's reason when the body is a JSON string, as Tokkl sends for a change it refuses, such as "Name has already been taken". The catalog pages show that reason for a 4xx. `Network` wraps the `IOException` of a request that got no response. `InvalidResponse` is a 2xx body that is not the expected JSON. `TrustRefused` covers the `TrustQuestion`s `CleartextNotConfirmed` and `UntrustedCertificate`, and `CertificateHostMismatch`. The client also reports each to its `TrustListener`.

<!-- kk:related:start -->
# Related

- Related: [map-timer-state-holder-and-sync-service → map-account-timer-state-lifetime](../../data-and-offline/timer-state-and-sync/map-account-timer-state-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-online-timer-actions-and-native-control-failures](../../data-and-offline/timer-state-and-sync/map-online-timer-actions-and-native-control-failures.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-event-stream-and-service-lifetime](../../data-and-offline/timer-state-and-sync/map-timer-event-stream-and-service-lifetime.md)
- Related: [map-timer-state-holder-and-sync-service → map-timer-state-acceptance-and-persistence](../../data-and-offline/timer-state-and-sync/map-timer-state-acceptance-and-persistence.md)
- Related: [map-typed-api-resources-and-request-contracts](map-typed-api-resources-and-request-contracts.md)
<!-- kk:related:end -->
