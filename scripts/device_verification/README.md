# Maintained device verification candidate

This package promotes the released TKA-0280 operational candidate into reviewable
repository code. It is a **candidate**, with host-only verification. It has not
run the SDK, Gradle, an emulator, a live systemd scope, a limit window or reclaim.
Do not replace an active lease's frozen cache helpers with these files.

The coordinator controls landing, adoption and the next integrated gate freeze.
The TKA-0259 captured-Stop lease uses its own immutable cache runtime. This
package can be considered only after that lease releases. No ready gate needs
to wait for it. Before a first expensive run, the coordinator must validate the
configured maintained controller with cheap owned live fixtures under a separate
authorization. Source conformance does not establish live integration.

## Source and configuration

`runtime/` is the single maintained implementation. `provenance/sources.json`
records the frozen source paths and SHA-256 values from
`~/.cache/sift-w10/watchdog-evidence-0280/runtime-0280` and its
`actual-base-helpers`. The latter supplies the actual BASE reclaim implementation,
rather than the unused helper copy with a stale log destination.

`controller.py.txt` and `local-reclaim-finite.py.txt` are review references. Never
execute them. `configuration-delta.json` declares the limited configuration
substitutions. No AST or source rewriter is shipped or used during preparation
or execution. Read-only conformance reverses the declared textual substitutions
in memory and compares every remaining byte with the frozen controller. It
also checks unchanged modules and reclaim logic. The historical lifecycle
analysis and adaptation guide are retained with their original uncertainty.

Unchanged implementation includes the event monitor, duration policy,
Session/holder protocol and Path(root) normalization, identity-aware cleanup,
both private shims, finite watchdog service, original planned-watchdog body and
conditional rollback body. Only reclaim's evidence directory changes to its
immutable snapshot directory. All control functions, outer exception/finally
ordering, preparation limits, fallback command and controller guard semantics
remain checked against the released source. Configuration supplies the project,
gate path, run identity, environment, finite plan and explicitly carried budget.

The guards retained include:

- Caller-visible ancestor capacity and the existing shared gate, 8192/5000 MiB
  cgroup floors, 5000 MiB physical floor, 60-second startup ramp and 300-second
  admission wait. Host available memory cannot substitute for ancestor headroom.
- Original 10G/12G/2G limits, fresh app current at most 6 GiB and host available
  at least 9 GiB before the previously authorized temporary 14G/15G/2G window.
  This package grants no permission to change these limits.
- Emulator OOM isolation, 6G/swap0 envelope, PartOf relationship, private runtime
  and TMP allocation, exact environment-prefix argv and holder placement.
- Up to three resamples within 250 ms against the unchanged anchor. Missing or
  unexplained evidence, harmful local/ancestor events and pressure still abort.
- Payload completion capture, quiescence, fresh final observation and certified
  closure. A known certified closed scope is retained explicitly as frozen final
  evidence. An unregistered vanished scope or watchdog is still unknown evidence.
- Bounded owned cleanup before conditional rollback/readback, followed by watch
  service cancellation and finally independent fallback cancellation only when
  safe. Unknown cleanup or restoration retains protection and reports recovery.

The watchdog service retains a single cgroup through its finite lifetime and
executes the unchanged watchdog every 60 seconds. Its independent forced fallback
remains armed before mutation. Normal oneshot disappearance is a reproduced
lifecycle mechanism, not permission to ignore ENOENT. Historical final watchdog
counters and exact removal time remain unreported; the old API36 abort is not
reclassified as a pass.

## Cheap checks

Run from the checked worktree:

```sh
python3 -B scripts/device_verification/preflight.py
python3 -B scripts/device_verification/test_host.py
```

Preflight reads files only. It never imports or runs the controller. The host
suite uses the actual maintained Session, sampler and event monitor against disk
fixtures; actual Python holder subprocesses with modeled cgroup membership;
the actual boot function with argument-recording shell stand-ins; and the actual
outer controller try/finally with modeled resource operations. It covers both
API launcher argument shapes on the host, not API31 device compatibility.
Protected runtime mutations are rejected by negative conformance tests.

The original 38 Session/holder/launcher, 24 monitor and 39 consistent-sampling
fixtures are retained, including the historical counterexample with explicitly
hypothetical settled reads. `provenance/host-fixtures.json` records their sources.
The hypothetical reads do not reconstruct missing historical samples. Tests
create scratch files only under owned disk cache, never the historical cache
directories or actual cgroup filesystem. They do not call live systemd, Gradle,
adb or emulator commands.

`SHA256SUMS` pins this reviewed package, including the configuration loader,
preflight and fixtures. Verify it before adoption. Future maintained edits need
an intentional manifest update, renewed host checks and review of changed safety
logic; never refresh hashes to suppress a conformance failure.

## Coordinator preparation and activation

Copy `example.config.json` outside the repository and fill every placeholder.
The example deliberately fails validation. Freeze one explicit JSON configuration
for the next integrated tree. Include the actual server and proxy image digests
from its executed `scripts/instrumented-tests.sh`, incorporating the coordinator's
TKA-0282 audit and TKA-0259 pin disposition. Never reuse the historical server pin
merely because the runtime originated on that version.

`input_pins` maps absolute paths to SHA-256 values. At minimum validation requires
the actual launcher, shared gate wrapper, tracked admission wrapper and capacity
probe. The coordinator must also pin every checked source, fixture, asset, build
and dependency input and compiled artifact used for reuse. An incomplete manifest
cannot be detected automatically. Record complete tool/image/configuration
fingerprints in the telemetry adapter alongside the controller's receipts.

Use a fresh disk `run_dir` outside the checked project. There are no default
executable plans. This candidate accepts sequential unfiltered API36 integrated
device jobs, with original benchmark finalization, plus guarded standalone
Gradle/reproducibility jobs. Scoped repairs continue to use the coordinator's
already frozen authorized path; adding new plan shapes requires explicit review
of their expected finalized counts. API31 execution is refused by this development
configuration. API31 compatibility remains a later dedicated cycle.

`reclaim_budget_bytes` is at most the previously authorized 6 GiB, not a renewed
authorization. Default zero permits no reclaim. `carried_reclaim_bytes` must
exactly match the pinned `prior_window` reservation ledger when continuing a
budget. Preserve previous finite budget records separately when the coordinator
explicitly authorizes a new budget. Do not reset a failed attempt's reservation,
reuse historical output directories, reclaim globally, or stop another workload.
No change to host resource arrangements is proposed by this package.

```sh
python3 -B scripts/device_verification/preflight.py --config /disk/run-config.json
```

After review and outside an active lease's configuration freeze, the coordinator
can prepare an immutable-byte snapshot:

```sh
python3 -B scripts/device_verification/prepare.py --config /disk/run-config.json
```

Preparation copies the one maintained implementation, never rewrites it. Each
snapshot receives `run-config.json`, `runtime-manifest.json` and a conformance
receipt. It refuses an existing output directory and never launches a workload.
An interrupted partial preparation remains on disk for inspection; use a fresh
directory rather than merging files into it.

The coordinator's adoption JSON must match the returned configuration/runtime
hashes and lease ID and record `approved_by` and
`cheap_live_controller_fixtures_verified: true`. That value must represent actual
new evidence, not the retained 0280 report. Without matching adoption, execution
refuses before controller state or resource changes. After cheap live verification
and explicit adoption, the coordinator's separate lease can invoke:

```sh
python3 -B /disk/NEW_RUN/controller.py --execute --adoption /disk/adoption.json
```

The snapshot rechecks input pins, server digest, carried ledger and its runtime
manifest before execution. It records run/lease identity, configuration hash and
the actual server/proxy digests in its state; each check retains argv, exit,
duration, finalized app/benchmark counts, device identity lines, resource samples,
completion directory and cleanup evidence. Pair it with telemetry begin/finish
receipts for explicit attempt identities and admission-wait measurements. The
controller has no automatic evidence-reuse bypass. Inventory passing receipts
before constructing a new plan and keep every required integrated obligation.

## Verification limits

This delivery demonstrates source conformance, configuration rejection behavior,
the actual launcher argument contract, final-evidence protocol and modeled outer
cleanup ordering on the host. The new configured maintained controller still
needs cheap owned live cgroup/controller fixtures. The actual SDK/API36 app and
benchmark matrix, all 68 screenshot references, JVM/lint/static/assemble checks
and reproducibility remain coordinator lease obligations. No current full-device
runtime or throughput improvement is measured here.

The dedicated final-tree API31 compatibility cycle still includes TKA-0271,
TKA-0281 and all deferred coverage. After shared-code compatibility fixes, rerun
affected API36 checks and complete release verification. Development and
compatibility completion must be reported separately.
