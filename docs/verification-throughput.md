# Verification throughput

This drain uses API36 for development device checks, including applicable scoped
checks and full wave gates. JVM, host, lint/static, screenshots, benchmarks and
reproducibility remain applicable. Android 12/API31 tests, benchmarks and
compatibility fixes follow all development waves in a dedicated compatibility
cycle. Deferred checks are pending, never passes. Shared-code compatibility fixes
require affected API36 checks again. Android 12 support and API31 CI coverage
remain unchanged.

The coordinator owns `.ai/sift/drain/state.md`, assignments, compatibility and ETA
ledgers, resource leases, integration and activation. This document proposes
additions to those ledgers; it does not edit them or grant a workload lease.

## Findings checked on 2026-10-22

- `/proc/meminfo` reported 16,104,872 KiB physical RAM. `app.slice` still had
  `memory.high=10737418240`, `memory.max=12884901888` and
  `memory.swap.max=2147483648`. Around 12 GiB host available did not mean enough
  cgroup headroom. The existing `scripts/memory-capacity.py` already checks the
  caller's visible ancestors, both high and max limits, pressure and event
  diagnostics. Keep it and the authorized memory gate unchanged.
- TKA-0273 has five paired attempts in RUNLOG, 206.27 minutes elapsed, 69.53
  minutes within assignments and 136.73 minutes between attempts. Assigned time
  includes tools and waiting. It cannot measure model generation separately.
- The retained audit window was 2026-10-21T14:14:04Z through
  2026-10-22T07:22:14Z, 17.136 hours. All 13 distinct recorded completions were
  `dx` tickets. This is a historical window, not a current completion rate.
- TKA-0280's retained fixtures reproduce disappearance of a normally finishing
  oneshot watchdog cgroup. Its released candidate uses a finite persistent exec
  service. Its report retains 170 operational host checks, 72 repository host
  tests and 37 real-systemd fixture expectations. Those historical passes do
  not certify a new controller or a completed API36 matrix.

Sources are the main checkout's `.ai/sift/RUNLOG.md`,
`~/.cache/sift-throughput-audit-20261022/metrics.json`, and
`~/.cache/sift-w10/watchdog-evidence-0280/{verification-report.json,ADAPTATION.md}`.
The intake snapshots and new verification logs live in
`~/.cache/sift-throughput-improvement-20261022/`. No system limits were changed
for this work. The approved server remains whatever the coordinator has frozen;
the independent TKA-0282 release audit owns any proposal to change it.

## Local telemetry adapter

`scripts/sift_telemetry.py` uses Python's standard library and never launches
Gradle, systemd, an emulator or another workload. Existing shared Sift helpers
provide legacy dispatch/return rows and identity-less phases; no shared check
telemetry interface was available at intake. This local adapter can be moved
upstream later without depending on T3 or Android for receipt storage.

Use a durable disk directory for receipts and logs, outside `/tmp`. Make an
explicit context JSON for each check:

```json
{
  "root": "/absolute/checked/worktree",
  "run_id": "drain-20261022",
  "wave": 10,
  "sitting_id": "gate",
  "ticket_ids": [],
  "attempt_id": "matrix-attempt-1",
  "check_name": "full-api36",
  "kind": "gate",
  "stage": "development",
  "inputs": ["scripts/instrumented-tests.sh", "app/build.gradle.kts"],
  "configuration": {
    "command": ["scripts/instrumented-tests.sh"],
    "scope": ["full-app", "full-benchmark"],
    "api": 36,
    "avd": "tokkl-api36",
    "server_image": "COPY_ACTUAL_DIGEST_PIN_FROM_EXECUTED_SCRIPT",
    "proxy_image": "COPY_ACTUAL_DIGEST_PIN_FROM_EXECUTED_SCRIPT",
    "environment": {},
    "toolchain": {},
    "resource_policy": "COPY_FROZEN_POLICY_AND_CONTROLLER_FINGERPRINT"
  }
}
```

The two illustrative input paths are **not** a full device fingerprint. Enumerate
every checked source, fixture, asset, Gradle/build/dependency configuration,
runner, script and controller input, plus APKs when reusing compiled artifacts.
Record actual SDK/image/AVD renderer and tool versions, environment, filters,
assertion/measurement criteria and resource policy in `configuration`. Include
the actual server and proxy image digests, not tags or an audit's proposed pin.
Do not put passwords or tokens in contexts, commands or logs. The adapter refuses
placeholder device pins and API31 development contexts.

```sh
python3 -B scripts/sift_telemetry.py fingerprint --context context.json
python3 -B scripts/sift_telemetry.py reuse --context context.json --ledger /disk/receipts
python3 -B scripts/sift_telemetry.py begin --context context.json --ledger /disk/receipts
```

`begin` returns a unique directory. Execute the approved check through the
unchanged resource gate and controller, under the coordinator's lease. This
adapter does not execute it. Then create a result JSON:

```json
{
  "exit": 0,
  "outcome": "passed",
  "finalized": true,
  "required_evidence_complete": true,
  "admission_wait_seconds": 12.5,
  "logs": ["/disk/run/check.log"],
  "junit": ["/disk/run/TEST-device.xml"],
  "artifacts": ["/disk/run/resource.jsonl", "/disk/run/metrics.json"]
}
```

```sh
python3 -B scripts/sift_telemetry.py finish --directory /disk/receipts/CHECK_ID --result result.json
```

A device pass requires fresh, consistent finalized JUnit with unique test
identities. Host checks can supply `counts` with `tests`, `failures`, `errors` and
`skipped`. Required metric samples, screenshots, performance thresholds, actual
device identity and clean resource/cleanup certificates remain the controller's
responsibility. `required_evidence_complete` records that explicit disposition;
the adapter does not infer it from exit zero. List those artifacts for hashing.

For interruption, missing historical evidence or a finite deadline, use
`interrupted`, `unknown` or `deadline` and preserve the actual exit and logs.
An unmeasured admission wait is `null`, allowed only for non-passing outcomes.
Record a healthy suite that exceeds a deadline as `deadline` with its observed
progress; call it a hang only with separate evidence of lost progress. Keep
assertions, measurements and thresholds unchanged.

Each immutable start/result pair records run/wave/sitting/ticket/attempt/check
identities, wall and monotonic timing, explicit input/configuration fingerprints,
exits, finalized counts, admission waits, and hashes of durable logs/artifacts.
Monotonic elapsed time is unavailable across a reboot. Atomic exclusive
publication prevents two writers from silently replacing a receipt.

The adapter samples the **telemetry caller's** visible ancestor headroom,
pressure, swap and events before and after. These are diagnostic observations,
not admission grants or workload certificates. For a workload in another scope,
retain the controller's complete before/after scope graph, local/hierarchical
counters, pressure, identities and finalization records as artifacts. Missing
paths, counter resets and unavailable local events remain explicit uncertainty.
The adapter never attributes a historical event to a process or accepts a
missing required resource certificate.

Reuse requires matching explicit content, execution configuration, check scope
and stage, a complete passing receipt and unchanged retained artifacts. Checkout
paths, cherry-picks and unrelated documentation do not invalidate it. An omitted
input cannot be detected automatically; review the manifest before scheduling.
Repeated checks with different metrics/configuration are not duplicates.

## Timing and ETA reporting

```sh
python3 -B scripts/sift_telemetry.py report \
  --runlog /absolute/main/.ai/sift/RUNLOG.md \
  --tracker /absolute/main/.ai/sift --ledger /disk/receipts \
  --remaining-development 37 --compatibility-hours 8 24
```

Legacy import pairs each ticket independently, reports superseded dispatches,
orphan returns and incomplete attempts, and leaves phases unattributed.
Distinct done returns count only when the live archive marks the ticket done.
Recent 20/40 completion rates use calendar elapsed time through now, including
overlap and gaps. The optional development projection divides remaining work by
those rates. It is low confidence and needs coordinator adjustment for effort,
gate critical paths, new failures and external dependencies. Compatibility hours
are explicit planning input, reported after development, not derived passes.
The coordinator's ETA ledger remains authoritative.

New checks separate `product`, `tooling`, `gate`, `dependency_wait` and
`resource_wait`, as well as development and compatibility. Represent a wait as
its own begin/finish span with outcome `observed`. Group elapsed durations use
the union of overlapping intervals; do not sum groups or per-check admission
waits into calendar time. An absent result remains incomplete. Neither historical
nor new receipts measure model generation unless a future provider supplies
actual generation spans.

## Compatibility ledger sidecar for coordinator review

- TKA-0271 and TKA-0281 retain unresolved API31 full-suite failures and their
  original criteria, logs and WIP. Do not apply their diagnostic changes or run
  their old API31 plans during development. API36 defects require API36 evidence.
- TKA-0266 still needs its applicable API36 gate disposition. The historical
  six-change residue's identities and outcomes remain unknown. Add future
  queue/attempt identities and durable evidence; new runs cannot recover them.
- After all development waves, run full applicable final-tree API31 app, server,
  native/lifecycle and benchmark checks. Fix compatibility issues, rerun affected
  API36 checks after shared-code changes, then finish release verification.
- Report development completion separately from compatibility completion.
  Intake planning was 3-8 hours for Wave 10 development, 36-84 hours for remaining
  development and 8-24 hours for compatibility afterward, all low confidence and
  excluding external waits. Recalculate these from current evidence rather than
  treating them as a countdown.

Measured here: the historical timing breakdown and conservative reuse behavior
in host fixtures. Expected benefits: fewer repeated matching checks and earlier
controller failure detection. No reduction in full-device runtime, failed
production runs or elapsed ticket throughput has yet been measured.
