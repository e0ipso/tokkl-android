# Proposal: captured-time Stop for offline clients

Extend `PATCH /api/v9/workspaces/:wid/time_entries/:id/stop` so an offline client can apply the Stop it captured on the phone and receive the same short-entry Trash/Undo behavior as the web app. This is a proposal; Tokkl 1.7.0 does not implement this contract.

## Request

Keep an empty-body PATCH as the web-compatible form: stop the addressed running entry at server receipt time. For a captured Stop, send:

```json
{
  "at": "2026-10-20T12:10:00.987Z",
  "stop_request_id": "stable-unique-id-for-this-queued-stop",
  "fields": {
    "description": "Focus session",
    "project_id": 12,
    "task_id": null,
    "billable": false,
    "tag_ids": [3, 8]
  }
}
```

`at` is a required ISO instant when `stop_request_id` is supplied. Validate it as a finite timestamp, then truncate fractional precision toward the start of the UTC second (for example, `.987Z` becomes `12:10:00Z`); reject missing, malformed, or out-of-range instants with the ordinary 4xx validation response. `stop_request_id` is an opaque client-generated idempotency key, stable across retries and unique per stop. `fields` is optional; when present it is a complete editable-field snapshot, validated by the same rules as an entry update. Apply those fields and Stop in one transaction. When omitted, preserve the entry's current editable fields. Reject a reused key with a different request payload as a conflict.

Resolve the workspace membership and entry ownership exactly as other writes do, and require the entry to belong to the URL workspace. Return not-found for an entry the user does not own or cannot access, and reject a stopped entry as a conflict unless this request is a recognized replay. This operation changes only the addressed entry; it must not stop or otherwise alter a newer timer on this or another client.

## Stop and response behavior

Run the existing `stopEntryAt` transaction with the captured, whole-second `at`. Preserve its account-timezone snapping (including nearest-point ties and fractional-hour DST behavior), future-start clamp, short-span handling, and 999-hour cap. Return HTTP 200 with the ordinary serialized time-entry object, including `server_deleted_at` when the span is too short. A short Stop is stored in Trash with `start == stop` at the snapped start and duration zero. The existing restore endpoint clears its deletion marker and leaves that stopped zero-length entry stopped, matching the web Stop/Undo flow.

Persist the request key, a canonical request hash, and the first response body transactionally with the stop. A retry with the same key and payload returns that stored HTTP 200 response unchanged, including its original `server_deleted_at`, even if the entry has since been edited or remains in Trash. It must not re-run snapping, retime the entry, restore it, or stop a later timer. A same-key/different-payload retry returns conflict. The client can therefore retry after a lost response and still learn that the entry was trashed; it invokes the existing restore endpoint only when the user chooses Undo.

Without `at` and `stop_request_id`, preserve current web receipt-time semantics and response shape. Do not require this extension for ordinary Toggl clients.

## Current behavior and reproduction

Tokkl v1.7.0 ignores the PATCH Stop body and always supplies `new Date()` to `stopEntryAt`; ordinary updates reject a short changed span, while Stop trashes a short span at its snapped start with zero duration ([`togglV9.ts:358-361`](../../tokkl/packages/api/src/routes/togglV9.ts#L358), [`timeEntries.ts:223-232,248-258,383-389`](../../tokkl/packages/api/src/models/timeEntries.ts#L223)). DELETE only marks an entry deleted and restore leaves an otherwise unopposed running entry running ([`timeEntries.ts:393-410`](../../tokkl/packages/api/src/models/timeEntries.ts#L393)). The web TimerBar offers Undo when Stop returns `server_deleted_at` and calls restore ([`TimerBar.tsx:286-292`](../../tokkl/packages/web/src/components/TimerBar.tsx#L286)).

A real-HTTP reproduction against Tokkl v1.7.0 used its Express app and model with a disposable in-memory database: `node --test /home/e0ipso/.cache/sift-w10/snap/server-contract.test.mts` — exit 0, 1 test passed, 0 failed. The request/response transcript and cleanup confirmation are in [`server-contract-02.log`](/home/e0ipso/.cache/sift-w10/snap/server-contract-02.log); test source: [`server-contract.test.mts`](/home/e0ipso/.cache/sift-w10/snap/server-contract.test.mts). At 60-minute resolution, a running entry starting `2026-10-20T12:00:00Z` returned 400 for PUT Stop at `12:10Z`; DELETE then restore returned `stop: null` and negative duration. PATCH with both `at` and `stop` set to `12:10Z` ignored them and stored receipt-time `2026-10-21T07:00:00+00:00` (duration 68400). An immediate web-style PATCH instead returned an equal snapped start and stop, duration 0, and `server_deleted_at`; restore kept `stop` non-null and duration 0.

## Proposed server test matrix

- Delayed captured short Stop: submits a timestamp earlier than receipt time, returns the captured-time result, trashes it at the snapped start with zero duration, and restore preserves stopped zero duration.
- Kept span: captured Stop produces the expected snapped start and stop while preserving all submitted editable fields atomically; omitted fields remain unchanged.
- Half-step ties and fractional-hour DST: captured timestamps match `snapSpan` behavior in the account timezone, including both tie direction and a fractional-hour zone across a DST transition.
- Future start: captured `at` before the entry start clamps to the start before snapping.
- 999-hour cap: a captured Stop beyond the cap returns a stopped span capped at exactly 999 hours.
- Lost response: repeat identical request after the first response is lost, including when the first result is trashed; assert identical stored response and no retiming/restoration. Reusing its key with changed payload conflicts.
- Newer timer: start another timer after the captured Stop time, then deliver the old Stop; assert the newer timer remains running and unchanged.
- Validation/access: malformed `at`, missing `at` with an idempotency key, invalid field snapshot, wrong workspace, non-owner, and non-member follow ordinary write validation and access behavior; an unrecognized request against an already-stopped entry conflicts.

Android parent TKA-0259 remains blocked until this server contract is implemented and actual end-to-end Stop/Undo parity is verified. This proposal makes no implementation-complete claim.
