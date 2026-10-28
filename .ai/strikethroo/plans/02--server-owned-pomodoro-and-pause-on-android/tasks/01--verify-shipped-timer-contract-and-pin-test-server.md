---
id: 1
group: "server-contract"
dependencies: []
status: "pending"
created: 2026-10-28
skills:
  - api-contracts
  - bash
complexity_score: 5
execution_profile: "complex-architecture"
---
# Verify the shipped timer contract and pin the test server

## Objective

Establish the released server contract this plan will implement and move the real-server test runner to its immutable image.

## Skills Required

API contract inspection and Bash test-runner configuration.

## Acceptance Criteria

- [ ] Verify that a published `ghcr.io/e0ipso/tokkl` release contains server plan 3. The sibling changelog identifies 1.9.0 as the candidate; registry availability and its source tag must be checked rather than assumed.
- [ ] Reconcile this plan against the release's API table, `TimerState` types, routes and SSE behavior. Record the version, source revision, image index digest and any contract corrections in the plan before downstream work starts.
- [ ] `scripts/instrumented-tests.sh` pins that release by tag and index digest. Preserve its server setup, proxies, credential-file handling, device selection and run receipts.
- [ ] Check the Stop assertions identified by `testing/practice-test-against-a-real-tokkl-server.md` against the release. Preserve ordinary queued Start/Stop behavior and document any unresolved stop-rule mismatch instead of silently changing its semantics.
- [ ] `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:1.9.0` succeeds, or the verified replacement tag is used, and its digest matches `IMAGE`. `bash -n scripts/instrumented-tests.sh` exits 0.
- [ ] Run `TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest,com.github.e0ipso.tokkl.cache.ChangeQueueServerTest --no-daemon` in the host's OOM-safe scope. Expect a nonzero executed-test count, zero failures and the runner's successful device receipts. Resolve or explicitly gate a contract mismatch before marking this task complete.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

The shipped contract wins over the 2026-10-27 planning snapshot. Read `../tokkl/docs/PRD.md`, `../tokkl/packages/types/index.ts`, the timer routes and `packages/api/src/services/timerState.ts` at the matching release. Server plan 3 now resides under the sibling's `.ai/strikethroo/archive/03--server-owned-timer-state-and-cross-device-sync/`.

## Input Dependencies

No task dependencies. External prerequisite: the server release and container image must be published. Missing publication blocks execution, not task generation.

## Output Artifacts

Verified contract and release evidence in this plan, the updated `IMAGE` and comment in `scripts/instrumented-tests.sh`, and focused server-check results.

## Implementation Notes

<details>
<summary>Release verification and pinning procedure</summary>

1. Inspect the sibling repository without changing its checkout. Read release-tagged files with `git -C ../tokkl show <tag>:<path>` when its working tree differs from the release. Compare nullable fields, phase and transition spellings, revision requirements, refusal bodies, Basic authentication and SSE framing with this plan.
2. Verify the published image using `docker buildx imagetools inspect`; copy the index digest, not a platform-specific digest. If the candidate release is unavailable or lacks plan 3, stop and record the external blocker. Do not invent a digest or switch to an unversioned image.
3. Correct factual differences in the plan while preserving approved product choices. An incompatible contract that requires a new product decision must be surfaced before implementation.
4. Change only the Tokkl image pin and its explanatory comment. Check the live `time/Snap.kt` and Stop server assertions, including the documented TKA-0259/TKA-0261 distinction. Do not fold a separate stop-rule redesign into this task.
5. Use `systemd-run --user --scope --quiet -p OOMPolicy=continue -- choom -n 1000 -- env TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh ... --no-daemon` for the focused device run. The script owns emulator and server lifetime. Keep credentials out of process arguments and retained evidence. Preserve the JUnit files before later runs overwrite their output directory.

</details>
