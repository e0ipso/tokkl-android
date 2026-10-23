# Benchmark noise diagnosis (TKA-0296)

Recorded 2026-10-23. This ticket closes with precise remaining uncertainty,
not a noise fix or stability claim. No benchmark/comparator mitigation was
supported or applied. The 10% threshold, intended measurement boundaries,
complete samples, actual Start/Stop behavior, retries and assertions remain
unchanged. No further local trial is authorized.

## Comparable historical CI samples

The saved TKA-0228 audit identifies four runs with equivalent Android, build
and benchmark inputs. Their common input fingerprint is
`c30b9a9d549c8ce39d2b13e046fa83eaaa5781b98e2a8ac9d4dd6a9d391237f9`.
Medians below are milliseconds, rounded to two decimals.

| Main CI run | Cold startup | Start | Stop |
|---|---:|---:|---:|
| [39832992839](https://github.com/e0ipso/tokkl-android/actions/runs/39832992839) | 1170.94 | 135.78 | 135.03 |
| [39833023579](https://github.com/e0ipso/tokkl-android/actions/runs/39833023579) | 1135.51 | 124.63 | 147.49 |
| [39833312191](https://github.com/e0ipso/tokkl-android/actions/runs/39833312191) | 1094.97 | 138.79 | 123.72 |
| [39833455038](https://github.com/e0ipso/tokkl-android/actions/runs/39833455038) | 1059.79 | 138.37 | 134.05 |

Across-run spreads, `(maximum - minimum) / minimum`, are respectively
10.49%, 11.36% and 19.21%. Start increased 11.36% between the second and
third runs. Spread is distinct from comparison against a chosen baseline.
The broader eight-archive audit contains 600 metric values; its JSON names
360 traces, but those historical traces were not retained. This establishes
variation, not its cause.

## What the newer traces establish

At source `c4cc561278416dc7190d129373cb6957602ff4d2`, completed benchmark
[job 120126278634](https://github.com/e0ipso/tokkl-android/actions/runs/39865417454/job/120126278634)
uploaded results artifact `12464162174` and reports artifact `12464062182`.
Downloaded sizes/SHA-256 matched GitHub metadata; all 45 referenced traces
were retained, with 15 startup values and 30 Start/Stop pairs. This closes
TKA-0301's actual-upload evidence gap, not the noise problem.

Nine selected traces yielded 15 exact metric-duration correlations and
12 guest scheduling partitions. Selected Start extrema spend 31.13 versus
123.44 ms in rendering `postAndWait`; startup extrema spend 150.73 versus
521.69 ms before `bindApplication`; a slow inspected Stop includes 80.87 ms
of guest waking/preemption delay. These delays lie inside the intended
measurement boundaries. Guest scheduler spans can include host vCPU
descheduling and do not independently measure physical host CPU execution.
No synchronized CI host telemetry establishes host contention, and the
historical across-run cause remains unknown. Existing full compilation,
stabilization and iteration counts did not justify a source mitigation.

## Successful bounded local Trial 2

The sole corrected Trial 2 ran once, after the earlier Trial 1 pre-write
failure, under the reviewed guarded controller and owned observer.
Product/APK source remained `14e193522057895d4ff43bbfb3a9f9992ca6b718`;
external maintained runtime source was
`7216da3963384f8933d1c0319ac9db1434bacec6`. Frozen configuration SHA-256:
`34646d9dded0b0b148d068921b2c289dd66081b31f6a425107653a450a295358`;
runtime manifest SHA-256:
`60a1614f64d32a1538ea56dd99a2b5907d7b579d65ec8c18079291c3f80631e5`.

Controller and job exited 0. The strict `benchmark-api-v1` contract passed:
10 API tests and two benchmark methods, zero failures/errors/skips,
15 startup plus 30 Start plus 30 Stop values, and 45 real traces. Medians
were 725.614398, 86.1327655 and 75.4692815 ms respectively. The actual full
`context.build` matched the declared expected Android API36 build
`BE2A.250530.026.D1` (the expectation came from CI, not a claimed prior local
observation). No baseline comparison was performed.

Four accepted APKs retained their exact post-run SHA-256 identities:

| APK | SHA-256 |
|---|---|
| `app-debug.apk` | `e9dde35f5215b7a77875c5cfefdb2397b7980ad48331a8f53cf38d2fa1ceb5d6` |
| `app-debug-androidTest.apk` | `630f56ba5217866759cb63b0a6a14bb8ae18262fcfe91a987f781c30f7cda7cf` |
| `app-benchmark.apk` | `4d313183f01e9fc06a930b931aa694713782a9bbec9b8c8546a35a2b1aa0c03b` |
| `macrobenchmark-benchmark.apk` | `6f0e90a635c45566075e4d2ab3d8eec2904c996ccbb81984c5ad40a8e9701c85` |

Installed-byte attestation remains unproven and is not an added acceptance
criterion. Local inputs are not claimed equivalent to the historical CI
inputs, and one local trial cannot establish repeatability or GitHub stability.

The observer was intentionally stopped and reaped: exit 3 remains
**UNKNOWN**, not a clean observer pass. Its 197 records (835,018 bytes)
cover the broad main-payload envelope; maximum gap was 2.000852 seconds,
with no unknown payload interval. The initial baseline-only record precedes
the workload. However, 25 payload-overlapping intervals flag swap I/O;
these count intervals, not independent swap events. Sampling is not atomic
physical-host profiling, and observer/Device-stream overhead is not isolated.
The swap-observed context prevents a quiet-host or repeatability claim and
does not prove the historical cause. The local series stops here.

Certified closure validated both Session chains and two post-closure
observations. Controller reaped/absent; owned workload, emulator and observer
scopes gone; fallback/watchdog inactive; original memory high/max/swap limits
restored to 10/12/2 GiB. Actual cumulative reservation is 13,107,466,240 bytes
of 17,179,869,184, leaving 4,072,402,944 bytes; 12/14 preparations consumed.
No refund, reset or further execution grant follows from those balances.

## Acceptance and remaining ownership

The written criteria permit precise uncertainty instead of an established
cause. No mitigation was applied, so conditional before/after mitigation
evidence is inapplicable. Retained host threshold check (one test, assertions
not reported), shell syntax and final trace correlations passed; the bounded
API36 contract above passed. This supports an evidence-only disposition,
without claiming noise resolved.

[TKA-0228's later baseline evidence](https://github.com/e0ipso/tokkl-android/actions/runs/39863641179)
established eligible green-main results at `8dffd56`. Offline replay against
`c4cc561` used the unchanged comparator and exited 0: startup -1.44%,
Start -6.30%, Stop -2.43%. Relevant runtime/main/build/benchmark/workflow/API-test
sources and JSON contexts matched; the full app tree differed and compiled
APK hash equivalence was not established. Both actual audited CI jobs had
selected older run `39209439121` without results and skipped comparison;
the later offline replay cannot retroactively establish CI enforcement.

**TKA-0228 retains actual later CI baseline consumption, unchanged 10%
comparison enforcement and several equivalent-input CI samples, including
honest reporting of residual noise.** The wave gate retains full applicable
integrated verification. API31 compatibility remains deferred to its dedicated
cycle, not waived or counted as passing. Missing baselines, partial samples,
local scoped success and offline replay do not substitute for those checks.

## Retained evidence

These absolute local links require access to the evidence host; they are not
public downloads. Public CI links above identify the runs/jobs, but artifact
retention is finite. This document preserves the material findings without
embedding raw samples. Existing archives and first failures remain retained.

- [Original eight-archive evidence](</home/e0ipso/.cache/sift-w11/benchmark-noise-0296/evidence.json>) and [commands](</home/e0ipso/.cache/sift-w11/benchmark-noise-0296/commands.json>).
- [Verified CI upload](</home/e0ipso/.cache/sift-w11/current-ci-benchmark-evidence/verified-upload-evidence.json>).
- [Guest trace findings](</home/e0ipso/.cache/sift-w11/benchmark-noise-0296/new-traces-host-retry/findings.json>) and [host/check results](</home/e0ipso/.cache/sift-w11/benchmark-noise-0296/new-traces-host-retry/sitting-final.json>).
- [Baseline qualification and offline replay](</home/e0ipso/.cache/sift-w11/current-ci-benchmark-evidence/first-green-baseline/cross-run-evidence.json>).
- [Trial 2 final receipt](</home/e0ipso/.cache/sift-w11/benchmark-noise-0296/quiet-trials-root-02/trial-02-inactive/TRIAL-02-FINAL-RECEIPT.json>), SHA-256 `7ccbda36f67fe4c531a578c57e5b136a54adc51e104e5f33b5462257f205d911`; includes exact commands/log paths, result/context/APK proofs, observer limitations and 32 linked evidence hashes, including certified closure.
- [Actual new ledger](</home/e0ipso/.cache/sift-w11/benchmark-noise-0296/quiet-trials-root-02/trial-02-inactive/runtime-snapshot-01/window-state-20261023072642.json>), SHA-256 `42e475aba457ec9c30e5e9bdcb40f9f687e68427c35881990292bea9a73e3aaf`; prior carry source was `43d2fa8a4ac4e949046bf91635e2d59f811bcc55f38d99652f63890a6527059e`.
- [Written acceptance mapping and closure hashes](</home/e0ipso/.cache/sift-w11/benchmark-noise-0296/current-plan-preparation/final-disposition-0296/criterion-mapping.json>).
