# Maintained device verification candidate

This package promotes the released TKA-0280 operational candidate into reviewable
repository code. It remains an inactive candidate. Bounded owned live fixtures
exercise its controller, Session, cgroups and watchdog service with harmless
payloads. They never run the SDK, Gradle, an emulator, a limit window or reclaim.
Do not replace an active lease's frozen cache helpers with these files.

The coordinator controls landing, adoption and the next integrated gate freeze.
The TKA-0259 captured-Stop lease used its own immutable cache runtime and has
released. Root separately authorized the fixtures described below. Their evidence
does not activate this package. The coordinator must accept the complete positive
and failed observations, then freeze matching inputs, runtime, configuration and
an exclusive lease before an expensive run.

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
conditional rollback body. Reclaim's evidence directory uses its immutable snapshot directory. The explicit
root-authorized preparation policy replaces the legacy 2 GiB request ceiling in
both controller and helper, with zero as the default. `preparation-delta.json`
records the reviewed helper changes. All remaining control functions, outer
exception/finally ordering, fallback command and guard semantics remain checked
against the released source. Configuration supplies the project, gate path, run
identity, environment, finite plan and explicitly carried budget.

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

The separately authorized live runner is `test_live.py`. Its four controller
cases cover success, payload nonzero exit, capture-exception cleanup and loss of
required scope evidence. Actual controller functions and its outer try/finally
run with real Session, holder processes and cgroups. Admission, preparation,
shared-window callbacks and destructive rollback remain modeled. A separate
persistent watchdog service runs the unchanged body in inactive `prepared`
phase across two real 60-second intervals. An independent real timer invokes a
harmless marker instead of the destructive fallback body. Every unit is uniquely
owned, capped at 128 MiB with swap disabled and choom 1000. The runner records
PID start identities, final counters and exact-unit cleanup.

Its default watchdog sampling scope is global. Root may separately authorize
`--cases --watchdog-scope owned` to prove only the service lifetime with actual
Session sampling its own service cgroup. That option refuses controller cases.
It establishes neither global resource cleanliness nor gate success. Future
monitor samples and assessments are persisted before an abort. Missing historical
failed samples remain missing. Do not rerun live fixtures without authorization.

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

`reclaim_budget_bytes` is at most 6 GiB and grants no renewed authorization.
Default zero permits no reclaim. `preparation_policy.max_request_bytes` defaults
to zero and is at most 4 GiB; `max_preparations` is at most four. Each distinct
planned job permits at most one full fresh deficit plus 128 MiB, covered entirely
by inactive file cache. Both entry points require matching root adoption and
bind the reservation to project, configuration, lease, state path and job index.
The helper persists an exclusive write intent before opening `memory.reclaim`.
Failed writes consume their reservation and cannot be replayed or refunded.
`carried_reclaim_bytes` must
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
receipt, plus an owned mode-0700 disk `scratch` directory. Execution refuses
missing, symlinked or unsafe scratch directories. Preparation refuses an
existing output directory and never launches a workload.
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

Host checks demonstrate source conformance, configuration rejection behavior,
launcher arguments, final-evidence protocol, preparation binding and modeled
cleanup ordering. See the handoff receipt for unique live case counts, failed
fixture attempts, the failed global-resource observation and the separately
bounded service-lifetime proof. A component proof cannot clear a global abort.
The new runner persists future failed monitor samples without reconstructing
missing historical evidence or assigning an unproven event origin.

`audit_plan.py --config /disk/run-config.json` reads the actual controller's
opening snapshot functions, checks the whole configured preparation request,
resolves configured tools and records SDK/JDK metadata, screenshot-reference
count and benchmark source fingerprints. This never admits or launches work.
Snapshots expire; root must reread conditions under the eventual exclusive lease.

The SDK/API36 full app and benchmark matrix, all 68 screenshot references,
authoritative assemble/check and reproducibility remain coordinator obligations.
The approved full plan uses a 90-minute API36 payload floor, one slot, followed
by authoritative assemble/check and reproducibility. Its separate finite budget
requires root adoption. Historical scoped usage remains immutable provenance.
The missing authoritative 10 percent benchmark baseline remains an owner
TKA-0228 limitation. No production throughput reduction is measured here.

The dedicated final-tree API31 compatibility cycle still includes TKA-0271,
TKA-0281 and all deferred coverage. After shared-code compatibility fixes, rerun
affected API36 checks and complete release verification. Development and
compatibility completion must be reported separately.
