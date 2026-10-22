# Maintained device verification candidate

This package promotes the released TKA-0280 operational candidate into reviewable
repository code. Root adopted the TKA-0284 runtime on integrated `d7740f0`.
That full production stage closed with 540 app passes, 11 app failures and two
benchmark passes, without an operational abort. It does not establish a passing
product gate. The later scoped-result extension needs a fresh matching adoption.
Bounded owned live fixtures
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

Input fingerprint validation uses read-only Linux `O_DIRECT` in
`runtime/configuration.py`, with one reusable 64 KiB transfer buffer and at most
64 KiB alignment padding. `statx(STATX_DIOALIGN)` on the opened descriptor must
report supported memory and offset alignments. `fstatfs` must identify the ext4
family used by the host proof; other filesystems, including overlay and compressed
filesystems, remain unsupported even when they report alignment. This avoids
claiming cache neutrality for unproved filesystem fallback behavior.
Unsupported filesystems, missing
capabilities and read errors fail explicitly, with no buffered fallback. Empty
and odd-tail files retain exact digests; aligned short reads continue, while
unaligned short reads before EOF refuse. Descriptor and path identity, size,
mtime and ctime must match after the read to reject observed tampering.
Each digest still covers every byte.
There is no hash cache, pin omission or change to adoption/ledger checks. This
same function checks inputs, the previous ledger and frozen runtime at controller
startup and in both reclaim parent and writer before write intent. `preflight`
and `prepare` also reach this validator. Direct reads avoid populating file cache;
they do not evict existing pages or bound total interpreter RSS, metadata size,
other readers' cache use or I/O time. Direct reads may be slower.

The cache canary creates one owned cold 8 MiB-plus-17-byte sparse or regular file
at a time. The regular fixture uses direct writes. Read-only `mincore` measurements
must show zero resident pages before and after the actual hash, whose digest must
match independently generated bytes. No cache eviction advice or global cache
drop is used. Separate negative tests cover unsupported alignment/I/O, errors,
short reads, truncation, growth and path replacement.

The TKA-0304 owned-file baseline measured 8,392,704 resident bytes after each old
reader pass and zero after each direct pass. Regular-file times were about 9 ms
and 21 ms respectively. This finite host proof does not establish that hashing
caused the historical controller admission refusal. Root can omit redundant
standalone frozen validations and `preflight --config` rescans while retaining
prepare/audit and every mandatory actual controller/helper check. Existing
160-host/four-live/two-watchdog receipts do not cover this changed hash reader;
new matching review, applicability evidence and adoption remain required.

The host regression uses a 100,663,313-byte sparse file with a 64 MiB child
address-space cap and an independent `sha256sum` reference. It runs the actual
hash, adoption, controller bootstrap and both helper entry points. Controller
execution stops immediately after validated configuration; the parent stops
before its lock and the writer before intent. All resource operations remain
unexecuted. Exact original validator bytes must fail with `MemoryError` under
the same cap. Receipts include this exec's Linux `VmHWM` and `getrusage` max RSS
separately, since the latter can retain a pre-exec parent high-water mark.

Other whole-file hash consumers remain confined to package sources/manifests,
selected Kotlin sources, parser/JUnit outputs, benchmark source and tiny version
metadata. They are not the arbitrary large tool/image input-pin path. Source and
JUnit parsing still materialize their inputs, as before; this correction makes
no general claim about their memory use. The original admission's aggregate
anonymous growth does not identify which process allocated it. TKA-0294 proves
the old allocation risk and bounds this hash function, not sole historical cause.

Streaming still reads all pins each time. Quiet readiness observed before
validation cannot establish continuous quiet through preparation; the retained
Stage 3 readiness ended 55.046 seconds before preparation. Root must review
timing after validation and use fresh admission without changing guard anchors,
thresholds or baselines. A carried continuation must pin the actual previous
ledger and its SHA, retain 2,189,529,088 spent bytes against 6,442,450,944 total,
and allow three remaining preparations, not four new ones. These figures describe
that historical TKA-0294 stage, not a new budget. Arithmetic remaining
capacity is 4,252,921,856 bytes. Fresh configuration/runtime/adoption and an
exclusive coordinator lease are required; this host proof grants none of them.

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
to zero. It may be set to the authorized cumulative budget, or an explicitly
stricter caller bound; there is no independent 4 GiB ceiling. The controller and
both helper entry points still require the whole request to fit the actual
remaining cumulative budget. `max_preparations` is at most four. Each distinct
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

For the TKA-0295 continuation of the current TKA-0290 phase, root must pin the
actual previous ledger and SHA, retain zero reserved bytes and the one consumed
preparation, and configure only three remaining preparations. The existing
cumulative budget is 6 GiB. Root must replace the obsolete 4 GiB caller bound in
a fresh matching configuration/adoption, not edit an old snapshot or reset the
budget. Host fixtures admit a computed 4,352,847,872-byte request through actual
controller preparation, parent and writer validation, with resource operations
modeled and the real durable write intent exercised only in owned disk scratch.
They grant no resource permission or automatic continuation.

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

After scoped repairs, the final SDK/API36 full app and benchmark matrix, all 68
screenshot references, authoritative assemble/check and reproducibility remain
coordinator obligations. Prior full-stage receipts retain their own input pins
and outcomes; source changes do not inherit a full-gate pass.
The approved full plan uses a 90-minute API36 payload floor, one slot, followed
by authoritative assemble/check and reproducibility. Its separate finite budget
requires root adoption. Historical scoped usage remains immutable provenance.
The missing authoritative 10 percent benchmark baseline remains an owner
TKA-0228 limitation. No production throughput reduction is measured here.

## Declared scoped app recipes

TKA-0291 adds one result profile, `scoped-app-classes-v1`. It supports one to five
whole app classes on API36, selected with exactly one class-list argument. It
accepts no method/package mixture, benchmark task or arbitrary Gradle arguments.
The existing unfiltered full plan keeps its 90-minute floor, two-benchmark
predicate and outcome checks. Benchmark source assertions, 30 TimerTap and 15
startup iterations, five Stop attempts and the 10 percent comparison remain
unchanged. A scoped result is never a full-gate result.

After source workers submit their candidate and root freezes its exact bytes,
generate the declaration without starting a workload:

```sh
python3 -B scripts/device_verification/declare_recipe.py \
  --project /disk/checked-project \
  --classes com.github.e0ipso.tokkl.widget.WidgetServerTest
```

Put the emitted object in the device job's `result_recipe`. Its `classes` list
determines the exact ordered argv, for example:

```json
{
  "api": 36,
  "label": "widget-app",
  "seconds": 1800,
  "standalone": false,
  "args": [
    "scripts/instrumented-tests.sh",
    "-Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.widget.WidgetServerTest"
  ],
  "result_recipe": {"profile": "scoped-app-classes-v1", "classes": ["..."], "inventory": ["..."]}
}
```

The abbreviated declaration above deliberately fails validation. Use the complete
generated inventory. Each entry records class name, relative source path, SHA256,
supported class shape and every direct `@Test` method. Validation derives it again
from the pinned file and rejects a subset or edited inventory. This narrow Kotlin
reader masks comments and literals. It supports concrete direct JUnit4 classes
with ordinary zero-argument block methods. Inheritance, parameterization, custom
runners, aliases and conditional test annotations require another reviewed
profile; they cannot silently omit cases. The required baseline five classes
contain 30 cases and WidgetServerTest seven. Candidate edits may change those
inventories, so generate them again only after root freezes the submitted source.

Include each selected source and the actual `scripts/sift_telemetry.py` parser in
`input_pins`, along with every other source/build/tool/configuration input root
requires. The existing content-pin policy permits explicitly reviewed WIP with a
recorded base `checked_head`; it does not require changing commit identity to
pretend WIP is committed. A head alone never identifies WIP bytes. Preparation,
execution adoption and result validation must bind the same content configuration.
After a source or recipe change, root must issue a newly reviewed configuration
and matching adoption rather than refresh pins inside an active run.

Scoped result validation rereads the frozen JSON and input hashes, matches the
actual job/argv/API/deadline and requires exactly one recorded tokkl-api36 device.
It uses the input-pinned telemetry JUnit parser to reject stale, empty, partial,
duplicate, failed or skipped results. Actual class/method identities must equal
the complete source inventory. Fresh unrequested benchmark execution refuses the
app-only exception; old unrequested benchmark files are not requested evidence.
The original resource stability, private TMP, exit and final-certificate gates
still apply before success. Result receipts retain recipe/configuration hashes,
exact cases, finalized counts and JUnit content hashes.

For compile-first scoped verification, declare top-level
`"plan_recipe": "compile-then-scoped-app-v1"`. This permits exactly two jobs.
The first must be standalone API0, label `assemble-check`, with this exact argv:

```sh
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest check --stacktrace
```

The second must be the declared API36 scoped app job. Root selected 1800-second
payload deadlines for both current jobs. A nonzero compile records its actual
exit and fails the prerequisite. The existing outer sequence then stops before
the second preparation or device job. `not_run_labels` identifies that job as
NOT RUN. Cleanup and rollback still occur. Outside this exact declared profile,
the first job must remain a device job, the additional APK build task is refused,
and the full plan keeps its original ordinary assertion-failure continuation.

Every one of the scoped cases must pass. An unchanged failing widget clock makes
the whole 30-case job fail; the coordinator may separately analyze the other 29
outcomes, but this contract supplies no waiver or partial job pass. One 30-case
invocation already includes all seven widget cases and can supply the first
widget diagnostic observation without a duplicate device run.

Choose finite scoped deadlines from observed runtime and progress; these values
are not an automatic renewal. Follow the same read-only preflight, preparation
and explicit root adoption procedure. Adoption's configuration digest includes
the entire plan recipe, selected source inventory and argv. No new resource
budget, lease or adoption is granted by declaring recipes or preparing disk files.

The result extension has host coverage using actual configuration/adoption
validation, maintained `run_workload`, Session/final-counter protocol and real XML
parsing, with launch/cgroup membership/counters modeled on disk. It does not rerun
the 0284 live fixtures or reclassify the earlier UNKNOWN global event. Nine
protected modules and preparation/reclaim logic remain unchanged by this extension.

## Failed capture diagnostics

TKA-0292 replayed all 89 retained monitoring frames from the compile/scoped stage
through the actual Session and monitor. All original decisions match. The four
abort reads contain real unrelated `t3.service` local high deltas 12/13/13/14,
against user.slice hierarchical deltas 11/12/13/13. Its cgroup identity is stable.
Only one intermediate read accounts exactly; the next changes again. The existing
three resamples therefore correctly fail closed. Exact per-file read/event/kernel
propagation timing is UNKNOWN. This is no finding about the earlier 0284 origin.

`CaptureEvidence` adds diagnostics only. It calls the unchanged reader, returns
the same tree and rethrows the same reader exception. Five immutable serialized
copies retain the most recent raw capture attempts. They hold no raw-tree
references. Each is capped at 262144 bytes, for at most 1310720 retained payload
bytes, plus the deque/bytes object overhead reported by `memory()`. This is a
buffer bound, not a process RSS limit. Serialization has transient encoder and
bounded output buffers. An oversized or unencodable frame becomes an explicit
diagnostic-gap record; it never changes the tree given to the monitor.

Only failures write files. Raw reader exceptions and existing controller abort
boundaries persist available frames, Session-view/assessment context or the exact
exception. Context is capped at 524288 bytes. Each file is at most 1839104 bytes;
at most four files are attempted per controller, including failed writes, with no
refund. File, directory and parent directory are fsynced. Diagnostic write errors
are returned as diagnostic errors and never clear or replace the original abort.
The monitor, Session, resource guards, retry limits/anchor and cleanup are unchanged.

These are available-frame diagnostics, not new per-file kernel timing. They do
not recover unretained history, justify a retry, activate a resource window or
declare global cleanliness. A root-approved fresh snapshot/adoption is required
to use them. The failed stage's 3564269568 reserved bytes and two preparations
remain consumed. Any continuation must preserve that ledger and its two remaining
slots. Successful compile receipts remain retained observations; missing historical
tool/classpath/artifact fingerprints cannot be filled by hashing current files.

The dedicated final-tree API31 compatibility cycle still includes TKA-0271,
TKA-0281 and all deferred coverage. After shared-code compatibility fixes, rerun
affected API36 checks and complete release verification. Development and
compatibility completion must be reported separately.
