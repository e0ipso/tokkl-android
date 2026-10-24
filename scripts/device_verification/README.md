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

`reclaim_budget_bytes` is at most 32 GiB and grants no renewed authorization.
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

TKA-0309 raised this finite cumulative ceiling to 9 GiB. Existing explicit
amounts and the zero default remain unchanged. Cumulative accounting is separate from
the owner opening predicates: app current at most 6 GiB and host available at
least 9 GiB. Temporary limits remain 14G/15G/2G and rollback 10G/12G/2G.
The protected controller's refusal text still mentions a "finite 6GiB budget";
its actual predicate compares against the configured budget, not that stale text.

The historical TKA-0309 amendment carries 6191243264 actual reserved bytes and
five actual preparations. A reviewed 9663676416-byte budget leaves 3472433152 bytes. Root
must pin the actual prior ledger/hash and bind exactly two remaining allowances
in the new configuration/adoption, for seven lifetime preparations in that
amendment. The generic validator enforces one to four local plan allowances;
it does not enforce a lifetime counter or infer the two remaining slots from
history. This ceiling change grants no new resource authority. Reservation
identity, fresh whole deficit plus 128 MiB, whole inactive coverage, durable
write intent, no reset/refund/replay and all existing safety guards still apply.

TKA-0312 raised only the explicit ceiling to 12884901888 bytes (12 GiB).
Existing budgets, including 9 GiB, and zero defaults remain unchanged. This
finite amendment carries all 9289646080 actual reserved bytes, leaving
3595255808 bytes; the recorded fresh whole need is 981966848 bytes. Eight actual
preparations have been consumed. Root must pin the actual prior ledger/hash and
bind the two remaining local allowances, for a lifetime cap of ten. The generic
validator still enforces only one to four local allowances, not a lifetime cap.
No reset, refund, resource grant or activation follows from the ceiling change.
Opening 6/9 GiB, temporary 14G/15G/2G, rollback 10G/12G/2G, whole inactive-file
coverage, identity and all failure/cleanup guards remain unchanged. Changed
validator bytes require fresh actual applicability and matching root adoption
separately; host checks establish no workload or device result.

TKA-0314 raises only the explicit finite ceiling to 17179869184 bytes
(16 GiB). Existing budgets, including 12 GiB, and zero defaults stay unchanged.
The recorded amendment carries all 11858051072 reserved bytes, including the
failed pre-write attempt, leaving 5321818112 bytes. The observed fresh whole
need is 1340342272 bytes with 4556406784 bytes of inactive-file coverage; every
future request still needs fresh calculation and full coverage. Eleven actual
preparations are consumed; root binds three remaining allowances under its
finite lifetime cap of fourteen. The generic validator enforces local plan
allowances only. Carry the actual closed prior state unchanged, with its pinned
hash, even when its phase remains `prepared`; closure review is separate.

This cumulative amendment does not fix the anonymous-memory guard or authorize
activation. The 64 MiB guard, owner opening 6/9 GiB, temporary 14G/15G/2G,
rollback 10G/12G/2G, bounded hashing and all identity/failure/cleanup guards stay
unchanged. There is no reset, refund or automatic replacement run. The sole
corrected attempt and any later completed-run repetitions require separate root
review under the recorded finite amendment; another failure stops the series.
Fresh actual applicability and matching root adoption remain separate.

TKA-0322 raises only the explicit finite configuration ceiling to 34359738368
bytes (32 GiB). The 16 GiB and smaller budgets and zero defaults remain valid.
The closed corrected ledger retains all 16990552064 reserved bytes, leaving
17369186304 bytes under an explicit 32 GiB configuration. Its actual 17 consumed
preparations exhaust cap17 with zero allowances. That ledger and its hash remain
immutable. Root must separately review a finite remaining plan and assign its
preparation counts, fresh actual applicability, matching configuration and
adoption. The generic validator enforces one to four local plan allowances;
it does not enforce a lifetime counter or grant new slots from this ceiling.
Every stage still requires a fresh whole request and full inactive-file coverage.
Bounded hashing, owner opening predicates, resource limits and all reservation,
identity, failure and cleanup guards remain unchanged. This change grants no
execution, activation, reset, refund, replay or replacement run.

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

After scoped repairs, the final SDK/API36 full app and benchmark matrix, all 100
screenshot comparisons, authoritative assemble/check and reproducibility remain
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
Startup, helpers and every result profile other than the post-input policy below
keep that hash check for every pin, including the two Gradle indexes.
Validation uses the input-pinned telemetry JUnit parser to reject stale, empty, partial,
duplicate, failed or skipped results. Actual class/method identities must equal
the complete source inventory. Fresh unrequested benchmark execution refuses the
app-only exception; old unrequested benchmark files are not requested evidence.
The original resource stability, private TMP, exit and final-certificate gates
still apply before success. Result receipts retain recipe/configuration hashes,
exact cases, finalized counts and JUnit content hashes.

## Declared post-build Gradle index observations

TKA-0324 freezes one optional policy, `gradle-global-index-observation-v1`, into
the original configuration and its adoption. It is available only to a single
scoped-app API36 job. Benchmark/API, Catalog, grouped host jobs, compile-first
plans and every legacy configuration stay on the strict full-pin check.

The policy names exactly these two files, in this order:

`/home/e0ipso/.gradle/caches/modules-2/metadata-2.107/module-artifact.bin`
`/home/e0ipso/.gradle/caches/modules-2/metadata-2.107/module-metadata.bin`

`validate()`, `adopted_configuration()`, controller startup and every helper
still compare both indexes with the other pins. Drift there refuses the run.
Only the scoped-app post assessor may call `post_input_configuration()`. A
change to those two files is an observation. The original `input_pins` map and
the configuration hash are not rewritten, and a later run binds the new hashes
in a new configuration rather than editing the frozen one.

Every other source, tool, coordinate and artifact pin stays strict. Membership
of `modules-2/files-2.1` and `modules-2/metadata-2.107` is checked on its own,
because a pin map does not show a file that was added. Extra paths, wildcards,
aliases, symlinks, and missing, unstable or oversized indexes are refused, as
are a stale policy, adoption or carry and incomplete observation evidence.
Compilation failure, empty or incomplete JUnit, and existing scoped-result
failures stay unsuccessful. An accepted observation does not claim an unchanged
dependency graph, no network use, a cause for the index change, or that nothing
was added and then removed during the run.

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

## Guarded four-APK bootstrap

`benchmark-apk-bootstrap-v1` is a separate packaging-only plan recipe with
exactly one job, no result or host recipe and no additional job fields:

```json
{
  "plan_recipe": "benchmark-apk-bootstrap-v1",
  "plan": [{
    "api": 0, "label": "benchmark-apk-bootstrap",
    "standalone": true, "seconds": 1800,
    "args": [
      "./gradlew", "--no-daemon",
      ":app:assembleDebug", ":app:assembleDebugAndroidTest",
      ":app:assembleBenchmark", ":macrobenchmark:assembleBenchmark",
      "--stacktrace"
    ]
  }]
}
```

Every common configuration field and pin is still required. The Gradle wrapper
must be input-pinned; root must also pin all consumed source/build/dependency/tool
inputs. Alternate labels, API/mode/deadline, wrappers, task order, extra flags/tasks,
additional jobs and mixed profiles refuse. Old profile restrictions stay intact.

`compile_only.py` validates plan shape only. The unchanged controller supplies
scope, choom, admission gate, TERM/KILL timeout, Session, monitoring and cleanup.
Its standalone result requires exit zero, no stop reason and the existing
operational guards. The unchanged duration policy gives a 2235-second job envelope
and 2520-second fallback. Gate refusal, timeout, nonzero exit and missing final
evidence fail normally. A completed Session certifies final scope captures,
releases, closure and post-closure guards, not APK files, tests or benchmarks.
No artifact collector is added to the controller.

Before execution, root still needs fresh applicability, a reviewed finite config
and matching adoption. The 6191243264 carried bytes and five used preparations
cannot reset or be refunded. Root must allocate only remaining allowances in its
exact reviewed configuration. This profile grants no bytes, preparations, lease,
opening headroom or resource-policy change.

After a separately authorized bootstrap completes, root must perform independent
artifact verification and retain a bounded identity receipt. This procedure is
a separate review obligation, not an implemented artifact assessor:

1. Bind frozen config/runtime hashes, lease, checked head and full input pins to
   the sole exact job. Retain actual argv, start/end times, outer/payload exits,
   telemetry and hashes. Require zero exits, no stop reason, successful owned
   cleanup, rollback and original-limit readback. Check matching Session request,
   nonce, registered PID/start/cgroup identities, ready/final-capture/ack/released/
   closed chain and completed receipt with both successful post-closure checks.
   Missing, replayed, mutated or foreign evidence and resource/cleanup failures
   refuse. A completion file alone is insufficient.
2. Use exactly the four relative `APK_PATHS` below. Each must be a unique,
   nonempty regular canonical file inside the project, without symlink ancestors
   or inode aliases. Require the corresponding `output-metadata.json` beside each
   APK. Bound each APK to 256 MiB, metadata JSON to 1 MiB and ZIP directories to
   65536 unique safe members. Reject encryption, traversal, duplicate names,
   missing or empty `AndroidManifest.xml` or `classes.dex`, and bad CRCs. Stream
   bounded chunks with at most 256 MiB per member and 1 GiB unpacked across all
   four APKs; refuse excess before allocating it. A filename/signature is not proof.
3. Require one unfiltered metadata APK element, exact expected output filename,
   debug/debugAndroidTest/benchmark variant and application/version identities
   matching frozen build inputs. Immediately record each path/variant/member-set/
   SHA/bytes tuple at successful completion. Metadata versions remain metadata-
   reported, not binary-manifest, certificate or installed-byte attestation.
   Freeze this mapping so later path/variant/file/copy swaps and hash mismatches
   refuse.
4. Require APKs and metadata fresh within that exact prelaunch-to-completion
   interval. Verify unchanged stat identity, size, mtime/ctime and SHA before and
   after inspection/copy, plus unchanged full source/build/tool pins and reviewed
   config/runtime identity. Stale UP-TO-DATE output fails; never touch/retimestamp
   it. Missing, escaped, stale, swapped or changing artifacts/metadata refuse.
5. Retain copies and all four metadata files in a new exclusive owned evidence
   directory. Rehash each copy against its original tuple and bind those hashes
   to command/Session/cleanup evidence. Confirm exact owned process/scope/temp
   allocation cleanup while retaining evidence. Record checks, failures and
   bounds. Synthetic fixtures prove no real APK, installed identity,
   reproducibility, test, metric, trace or stability result.

Only a complete independent receipt and root review permit freezing four APK
hashes into the unchanged benchmark declaration. A subsequent trial needs a
fresh separate matching configuration/adoption. Do not defer benchmark inventory
validation, replace an active lease, infer a benchmark pass from packaging or
reuse historical applicability as proof for changed runtime bytes.

## Exact standalone assemble and check

`standalone-assemble-check-v1` is a separate compile-only plan recipe with
exactly one job. It admits the host check after device configurations have
closed, so this job may be first. The job is API 0, label `assemble-check`,
standalone, 1800 seconds, and this argv only:

```json
["./gradlew", "--no-daemon", "assembleDebug", "check", "--stacktrace"]
```

`compile_only.py` recognizes it through the same one-job hook as the packaging
bootstrap, and that hook still requires the Gradle wrapper in `input_pins`.
No result recipe, host recipe, post-input policy, extra job field or second
job is accepted. Alternate labels, API, mode, deadline, wrappers, task order,
flags and tasks are refused. Without this recipe, a standalone first job is
still refused. The packaging bootstrap, compile-first plan, grouped
check-and-repro sequence, scoped-app jobs and benchmark profiles keep their
own rules, including a later ordinary `assemble-check` job that is not first.

Every other common field, launcher pin and image digest remains required.
The landed post-input policy stays limited to one scoped-app API36 job with
no plan recipe. Declaring it here is refused. Adoption must still name exactly
that frozen policy, or none.

This recipe validates shape only. It grants no workload, resource, count,
budget, dependency acquisition, lease, adoption or device run. Preparation and
adoption stay separate coordinator acts. The unchanged controller supplies
scope, choom, the admission gate, TERM/KILL timeout, Session, monitoring and
cleanup. A standalone completion still requires exit zero and no stop reason
under the existing operational guards. The unchanged duration policy gives a
2235-second job envelope and a 2520-second fallback.

The unchanged controller may copy
`app/build/test-results/testDebugUnitTest` and
`app/build/reports/lint-results-debug.xml` when those files exist, because the
label is `assemble-check`. A completion record is not a JVM or lint count.
An all-zero `jvm_counts` object with no copied report is not a JVM result, and
a missing lint file is not a lint result. Root retains the actual report files
separately. Gate refusal, timeout, nonzero exit and missing final evidence fail
normally. A completed Session certifies final scope captures, releases, closure
and post-closure guards, not unit tests, lint or a device run.

## Exact benchmark/API diagnostic recipe

TKA-0307 adds `benchmark-api-v1` within the existing result-recipe hooks. Its
canonical job label is `benchmark-api`, API is 36, `standalone` is false and
`seconds` is exactly 1800. Its entire argv, in this order, is:

```json
[
  "scripts/instrumented-tests.sh",
  ":macrobenchmark:connectedBenchmarkAndroidTest",
  "-Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.benchmark,com.github.e0ipso.tokkl.api"
]
```

The launcher already converts this package pair to its runner testFile. No extra
task, class/method filter, exclusion, package reordering or testFile override is
accepted. The existing app-only and compile-first profiles retain their original
restrictions, including refusal of fresh unrequested benchmarks. Undeclared
device jobs still require the original unfiltered full argv and 5400-second floor.

After the coordinator freezes the source, four compiled APKs and actual API36
device build identity, generate the complete declaration by importing
`declare_benchmark_api(project, device_build)` from `runtime/scoped_results.py`.
Here `device_build` is the complete reviewed `context.build` object from the
benchmark output, including its fingerprint and integer `version.sdk` of 36.
This is a Python declaration function, not a new execution command. Put its
returned object in the job's `result_recipe`. Include every returned inventory,
source and APK row in `input_pins`, plus all other coordinator-required inputs.
The four required APK paths under the checked project are:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `app/build/outputs/apk/benchmark/app-benchmark.apk`
- `macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk`

Benchmark recipe hashing reuses the same bounded direct-I/O primitive as
configuration input hashing, including every-byte digests, file identity checks
and refusal on unsupported I/O. A lazy import in `scoped_results.sha()` resolves
the existing configuration/recipe dependency in both package and frozen-script
imports; no shared module or duplicate reader is needed. This removes whole-APK
Python byte buffers during mandatory writer revalidation without skipping any
source/APK inventory or identity check. The 64 MiB aggregate anonymous-change
guard, resource limits and finite accounting remain unchanged.

TKA-0313 host allocation and capped-memory checks establish bounded hashing,
not attribution of Trial1's aggregate growth or resolution of benchmark noise.
Retain that failed attempt, its complete charge and certified closure. Fresh
actual applicability and matching root adoption remain separate requirements.

Declaration and validation read existing files only. They enumerate the complete
API package and its direct JUnit4 methods: three PinnedCertificateTest cases and
seven ServerSmokeTest cases. They require the reviewed benchmark/helper source
bytes, including StartupBenchmark.coldStart and TimerTapBenchmark.startAndStop,
15/30 iterations, Full compilation, five Stop attempts and the original
assertions. Added package sources, unsupported test shapes, changed benchmark
bytes, truncated inventories and missing pins require a newly reviewed profile.

Configuration validation rejects an alternate label or wrong mode before
preparation. Adoption binds that validated configuration and runtime hashes. The
unchanged controller derives each call from that plan and dispatches declared
result recipes to the assessor. A separate fixture tuple changed after adoption
and passed directly to the internal workload function is not that entry path.
The first attempt's failed direct-call assertion and logs remain historical
evidence; retry canaries test configuration rejection, the actual outer plan
loop and assessor identity failures instead of treating that injection as a
supported controller entry point.

A passing receipt requires both complete finalized JUnit inventories, with zero
missing, extra, duplicate, failed, errored or skipped cases. The pinned telemetry
parser checks each module separately, including aggregate counts and freshness.
Exactly one fresh benchmark JSON corpus must match the reviewed device build and
both benchmark identities. All 75 finite positive samples are required: 15 cold
start, 30 TimerStartFirstMs and 30 TimerStopFirstMs. Minimum, maximum and median
must agree with those samples. Iteration counts and metric membership are exact;
duplicate JSON keys, foreign cases and partial samples refuse.

Each of 45 indexed Perfetto references must resolve to its corresponding fresh,
nonempty, unique-content trace artifact. Extra, missing, duplicate, stale,
truncated or unsafe references/artifacts refuse. JSON/JUnit reads are bounded to
2 MiB each; each trace is bounded to 16 MiB stored and 64 MiB unpacked. Supported
containers are raw Perfetto protobuf or a ZIP with exactly one `Trace_output.pb`
member. ZIP CRC and complete outer packet/packet-field wire framing are checked.
This is container integrity validation, not trace-event or timing analysis.

The assessor rechecks frozen config, source/APK pins, runtime manifest, actual
command/API/device and cleanup evidence. The original controller's resource,
deadline, finalization and cleanup predicates remain required. Receipts retain
configuration/recipe/runtime hashes, APK hashes, JUnit identities, samples and
per-trace size/hash evidence. They explicitly report `full_app_gate: false` and
`comparison: "not performed"`. The unchanged 10 percent comparator must be run
separately against complete comparable results. A missing baseline or incomplete
evidence never establishes stability.

Host fixtures model external operations and use synthetic APK/trace bytes. They
grant no device or resource permission and do not prove installed APK identity.
The coordinator still owns fresh actual applicability, installed byte identity,
matching root adoption and separate allowance authorization. The closed phase
remains four preparations used, zero left, 3716812800 bytes reserved and
2725638144 remaining. This profile grants no reset, refund or additional run.

## Historical Catalog capture and offline assessment

`catalog-frame-api-v1` is a separate finite historical profile. Its only job has
label `catalog-frame-api`, API36, `standalone: false` and exactly 7200 payload
seconds. The existing configuration, controller, Session, cleanup, monitoring,
preparation, duration and accounting code is unchanged. This profile grants no
execution, preparation, count, byte budget or installed-byte identity. Existing
profiles keep their original selectors and acceptance rules.

The two approved `catalogRevision` values are the original eager
`d16c97a5e98ab0747041b02b8dd9a058bc84c700` and lazy
`4b3891e69f3d5290de73570aa759eb1c3337914a` bases. They differ from each
candidate's composite `checked_head` and `checked_tree`. Declaration checks the
whole committed historical delta, permits only the seven reviewed driver files,
two verification helpers, profiling documentation and host canary, and requires
a clean candidate. It fingerprints the historical production/build/API sources
and exact overlays. Git normalizes `gradlew.bat` to LF in its blob; the declared
CRLF checkout has its own exact input SHA256. All other tracked files, including
the 68 screenshot references, must retain their historical Git object identities.

Generate a recipe with the pure declaration function
`runtime.catalog_profile.declare(project, args, device_build, reviewed_tool_inputs, accepted_seed=None)`.
The returned object is the entire `result_recipe`; truncate or alter none of it.
`device_build` is the reviewed complete benchmark `context.build`. Four already
existing local APKs use the same paths as the benchmark/API recipe. Their hashes
identify local artifacts and do not attest installed bytes.

`reviewed_tool_inputs` has exactly `java`, `java_release`, `android_jar`, `adb`,
`aapt2`, `gradle_launcher` and `resolved_dependencies`. Each value has an absolute
canonical `path` and its `sha256`. Java and SDK paths must match the configured
environment; the launcher JAR must match the historical wrapper version. The
dependency input is bounded strict JSON with schema
`catalog-resolved-dependencies-v1`, `build_sources_sha256` and nonempty `files`.
The build hash covers the canonical declaration's ordered source rows ending in
`.kts`, `.toml`, `.properties` or `.jar`, plus both Gradle launchers. Each file
row has a distinct `artifact`, `path` and `sha256`. These are reviewed retained
build inputs, never a request to resolve or install dependencies. Put every
source/APK row, tool and dependency file in the configuration's `input_pins`.

The launcher/task/package prefix is exactly:

```json
[
  "scripts/instrumented-tests.sh",
  ":macrobenchmark:connectedBenchmarkAndroidTest",
  "-Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.catalogprofile,com.github.e0ipso.tokkl.api"
]
```

Only the reviewed ordered `catalog*` runner arguments are admitted. Seed and
replay are distinct declarations; a seeded fixture is an observed output until
a separate replay freezes its exact transport and hash. Credentials, launcher
URLs, `additionalTestOutputDir`, extra tasks, shell wrappers and every alternate
class/package/exclusion/testFile filter are caller-forbidden. Configuration and
adoption canaries exercise these refusals before a payload call, rather than
injecting an unreachable tuple into a workload function after adoption.

The final driver binding is source correction
`9e9b36ac362880ab44374415990cebae9dd346cc`, following preserved `40d6f3d1`.
Seed appends exactly `catalogRunId`, `catalogRevision`, `catalogSeed=true` and
`catalogServerImage`, in that order, with the eager base and pinned 1.8.1 image.
It omits all three previously unknown environment/viewport expectations and both
fixture arguments. Lazy replay appends exactly `catalogRunId`, `catalogRevision`,
`catalogEnvironmentSha256`, `catalogTagsViewport`, `catalogTrashViewport`,
`catalogFixture` and `catalogFixtureSha256`, in that order. Eager replay, lazy
reseeding, `catalogSeed=false`, partial expectations and mixed shapes refuse.

Before its first measured window for each workload, the seed freezes the actual
ready environment and Float viewport. The unchanged window schema gains no
invented fields. The two `<run>-<revision>-<workload>-baseline.json` records use
`catalog-workload-baseline-v1`; the combined `<run>-<revision>-baseline.json`
uses `catalog-baseline-v1`. Exact `environment_serialized`, its UTF-8 hash,
`viewport_serialized`, `viewport_argument`, fixture/run/revision/mode and
`source_phase` must agree across the records and observations. Either first or
steady may supply the first ready observation. Later methods, ready snapshots,
settled gestures and terminal anchors must retain the same serialization and
geometry. The combined record can exist before the final method finishes; its
existence alone never accepts a seed.

After a complete raw seed, the pure
`make_seed_reference(run_dir, adoption_path)` returns
`catalog-accepted-seed-v1`. Supply it as the replay recipe's `accepted_seed` and
merge every reference `inputs` pin into the replay configuration. The reference
binds the adopted seed configuration, certified receipt, controller state and
complete source/runtime/original/retained evidence. Replay revalidates all ten
API cases, four methods, forty windows/traces, observations, cleanup and the
unchanged capture receipt before using its exact fixture transport and combined
`replay_arguments`. Failed, partial, changed, foreign and cyclic references
refuse. No new driver parameter or observation job is introduced. An accepted
raw seed authorizes no execution or paired performance conclusion.

There are two result stages:

1. The unchanged controller calls the scoped hook immediately after the device
   payload and cleanup. This stage validates ten API cases and four Catalog
   methods, zero failures/errors/skips, forty indexed windows, forty distinct
   nonempty traces, 22 opening/ready pairs, four terminal anchors and identical
   per-method fixture bytes. It checks actual driver sidecar schemas, generation
   cursors, handler barriers, listener closure, injected/received events, clock
   records, environment, viewport, fixture origin declarations and seed cleanup.
   It inspects both original AGP artifacts and retained copies. The existing
   collector dereferences symlinks and hard links during `copytree`, so inspecting
   only copied files would erase source alias provenance. Catalog refuses these
   aliases without changing that protected collector.
2. Separately authorized offline work can later produce exports and receipts.
   The public pure function `assess_comparison(declaration_path)` checks them.
   It launches no processor, shell wrapper, Android workload or sampler. The
   read-only CLI is:

   ```sh
   python3 -B scripts/device_verification/runtime/catalog_profile.py \
     --offline-declaration /disk/catalog-offline-declaration.json
   ```

A raw pass certifies only operational/product/raw-capture completeness.
Its receipt retains `capture_started`, configuration/recipe/runtime hashes and
original/retained artifact hashes. `evidence_complete` and `comparative_valid`
remain false, offline assessment is `PENDING`, and matched work and quiet host
remain `UNKNOWN`. Missing exports do not turn a raw pass into a comparison.
Actual TKA-0263 acceptance and fresh coordinator adoption remain separate.

The later `catalog-offline-declaration-v1` lives at the common evidence root. It
has `reviewed_by`, complete relative-file `pins`, canonical absolute
`external_pins` for original capture inputs, a `host` identity with `host_id`,
`boot_id` and `runtime_id`, ordered eager/lazy `captures`, `corpus`, `processor`,
`queries` and `quiet_authority`. Every read must have a frozen pin. Keep original
AGP capture directories until assessment; changed or missing originals refuse.
Each capture supplies `revision`, `run`, `adoption`, `controller_state`, the exact
certified `started`, `fixture_origin` and `quiet_receipt`. It revalidates the
adopted configuration and raw result and requires equality with the original
certified capture receipt. Refreezing an export declaration cannot replace raw
evidence or move its freshness boundary.

The driver `catalog-corpus-v1` schema remains unchanged. Its `overlay_sha256` is
the SHA256 of canonical sorted compact JSON mapping the seven paths to their
reviewed hashes. Its revision entries still contain `revision`, `benchmarks`,
`windows` and `analysis`; the assessor adds `commands` beside each analysis
entry's existing `trace_sha256`, `directory` and seven `csv_sha256` values.
These command receipts are assessor evidence, not fields invented in driver
window or phase sidecars. The processor descriptor binds `path`, `sha256`,
`version`, `source`, `source_sha256`, `source_version` and `source_url`.

Every trace requires all seven literal driver SQL exports. Each
`catalog-export-command-v1` receipt records `trace`, `trace_sha256`,
`processor_sha256`, `query_sha256`, exact `argv`, `stdout`, `stdout_sha256`,
retained empty `stderr`, zero `exit`, wall-clock `started`/`finished` and
`wrapper`. The exact command is the declared processor path followed by
`query -f <literal-query-path> <immutable-trace-path>`. A CSV hash alone cannot
establish derivation. Headers, row arity, integer cells and native `[NULL]`
encoding are strict. Aliases, reused trace bytes, changed files, stale execution,
foreign query/trace/tool hashes, missing commands and failed exports refuse.

The referenced `catalog-bounded-export-v1` receipt binds the reviewed wrapper
`source`/`source_sha256`, exact `command_receipt`, singleton actual `cpu` affinity,
`wall_seconds`/`wall_limit_seconds`, cumulative `cpu_seconds`/`cpu_limit_seconds`,
and successful `closed`/`cleanup_complete` outcomes. Limits are finite at most
60 wall and 30 CPU seconds. Absence of optional cpuset files is not a criterion.
This validates retained wrapper evidence and never runs or authorizes a wrapper.

`catalog-fixture-origin-v1` binds the fixture hash, owned server identity, exact
server/proxy images, source receipt and credential-handling source hashes,
confirmed 204 account deletion and 401/403 deleted authentication. It must state
that credentials were not retained and that the historical diagnostic used
1.5.0. Both new sides use the common declared 1.8.1 condition. A seed image
argument alone does not prove the actual owned server's identity.

`catalog-quiet-receipt-v1` binds the externally reviewed `quiet_authority` hash,
configuration/revision/host identities, exact workload monotonic intervals,
threshold document and input hashes. Continuous coverage, closed observation,
`PASS`, and empty gap/breach/unknown lists are required. The external owner
defines existing thresholds and sampling; this profile adds neither. Memory
admission does not establish quiet host evidence.

Only complete offline evidence can set `comparative_valid: true`. Comparison
matches ordered achieved anchors after each gesture and at terminal anchors,
actual input timing, fixture/environment/viewport, identical tools/dependencies
and driver overlays. FrameTimeline uniquely paired phase-contained frames form
the deadline denominator. Callback totals never do. Boundary, unfinished,
unpaired and expected-only frames stay separate. Per-window samples and
distributions retain guest Main/RenderThread scheduled CPU, runnable delay,
overruns, deadline misses and wall time. This is not physical host CPU or an
across-session repeatability claim. Absolute pixel travel, composed-row count,
dropped-report phase attribution and late callback boundary ambiguity remain
explicit unknowns; the independent opening/bounded-row criterion is unverified.

Synthetic fixtures exercise validators, including a complete pair and refrozen
negative receipts. They establish no real server, Android, trace derivation,
quiet-host or performance result. All results retain
`runtime_acceptance: UNVERIFIED`, and no automatic no-regression conclusion is
drawn. Renewed actual applicability, frozen artifacts, finite accounting and
matching root adoption remain the coordinator's separate work.

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


## Finite assemble/check and reproducibility recipe

The private `assemble-check-then-repro-v1` host recipe preserves a two-job plan:
one existing API36 device job, then one standalone API0 job labeled
`assemble-check`. It requires exactly 1800 seconds for the entire host sequence,
with no `plan_recipe` override. The host job is declared as follows, replacing
only `/disk/NEW_RUN` with the frozen `run_dir`:

```json
{
  "api": 0,
  "label": "assemble-check",
  "standalone": true,
  "seconds": 1800,
  "host_recipe": "assemble-check-then-repro-v1",
  "args": ["/usr/bin/python3", "-B", "/disk/NEW_RUN/check_and_repro.py", "--execute"]
}
```

The frozen runner executes exactly these children in order:

```sh
./gradlew --no-daemon assembleDebug check --stacktrace
env GRADLE_OPTS=-Dorg.gradle.workers.max=1 scripts/reproducible-release.sh
```

The children inherit the original job's cwd, environment, output and process
group. The runner adds no per-child timeout, gate, scope or resource operation.
The existing shared gate, owned scope, Session holder, continuous capture and
descendant event monitoring surround the whole sequence. One existing `timeout`
sets 1800 seconds for both children together, including runner startup and pin
validation. Original cleanup/final certification, controller deadline, fallback
and watchdog policy remain unchanged. A nonzero first exit stops the sequence
before reproducibility. A nonzero second exit fails the job. Signal exits retain
the raw negative child returncode and propagate the usual 128-plus-signal status.

The Session directory's `check-and-repro.json` receipt records each exact argv,
start/end monotonic times, actual returncode and status. `not_run`, `running`
and `launch_failed` never mean passed; an interrupted receipt can have no final
exit. Each update is also flushed to the job log. The controller independently
records the outer job exit, including timeout 124, plus its original monitoring,
JVM/lint collection and cleanup evidence. Both child exits must be zero before
the runner returns zero; resource/certification failures still fail the job.
An exclusive initial receipt rejects replay in the same Session.

Both `gradlew` and `scripts/reproducible-release.sh` must be input-pinned, as must
all other required build/tool inputs. Preparation copies and hashes the runner
with the runtime; adoption binds the complete configuration and runtime manifest.
Execution revalidates adoption, command-source pins, frozen helper bytes, original
job environment/cwd and registered Session cgroup. Missing bindings refuse before
children start. Alternate wrappers, commands, profiles, positions, deadlines and
first-host plans under this grouped profile remain unsupported. Three jobs with
two allowances still refuse.

This recipe grants no preparation, budget, reset, refund, lease or activation.
The original current phase remains 6 GiB, four lifetime preparations, zero bytes
reserved/reclaimed and two preparations consumed, leaving two. Old snapshots and
ledgers remain immutable. New host canaries use owned tiny executables and modeled
resources only. The candidate stays private/inactive until the coordinator obtains
fresh actual cheap-fixture applicability and matching root adoption; no SDK,
Gradle, device, reproducibility workload or shared-resource operation is authorized
by these host checks. Final integrated checks and API31 compatibility remain the
coordinator's separate obligations.

## Exact final-gate source profiles

TKA-0332 adds separate profiles in runtime/final_gate_profiles.py. Legacy
scoped-class and development recipes retain their API36 restrictions. Only
compatibility admits API31, and only the exact diagnostic/final profiles below.
This source change grants no invocation, preparation, budget, retry or adoption.

| Profile | API | Exact selection | Payload seconds |
| --- | --- | --- | --- |
| screenshot-api36-v1 | 36 | screenshot package, capture or compare | 1800 |
| native-permission-method-v1 | 36 | one of four reviewed optional-source methods | 1800 |
| widget-clock-api31-v1 | 31 | original WidgetServerTest clock method | 1800 |
| benchmark-api31-v1 | 31 | ten API cases and two production benchmarks | 1800 |
| final-app-bench-v1 | 31 or 36 | complete applicable app inventory and two production benchmarks | 5400 to 7200 |

Use the source-only declare(project, profile, device_build, ...) function to
inspect a declaration, then job_spec(recipe) for the exact label/argv/deadline.
Declaration reads artifacts; it does not freeze, prepare or authorize execution.
Root must supply the reviewed same-API build and independently frozen complete
source/build/dependency/tool/APK pins. Runtime/API/argv/source/phase changes refuse.
All new profiles retain strict post-input validation. The optional Gradle-index
observation policy still belongs exclusively to its existing scoped API36 profile.

runtime/final_gate_inventory.json binds all 162 current androidTest Kotlin
sources to 150 concrete classes and their exact methods. The source-bound
inventories contain 697 API31 and 806 API36 identities, not measured device
counts. They explicitly expand 19 inherited classes and the four named
PomodoroDefaultsUiTest parameter sets. JUnit4 MessageFormat renders the integral
float values as font=1 and font=2 in case names. SDK-inapplicable identities are listed as
filtered out, never accepted as JUnit skips. Ordinary inventories exclude both
optional native probe classes. An added, changed or removed source requires a
new review of the table and its fixed digest. Full36 requires 100 reference names;
full31 excludes the 100 API36-only screenshot identities through the reviewed SDK
guards. The full selector excludes only CatalogFrameBenchmark's four separate
experiments, retaining all applicable app cases, Startup.coldStart and
TimerTap.startAndStop. Catalog source/helper bytes remain bound. Production
benchmark results retain the 75 positive finite values and 45 complete distinct
traces, source iterations, assertions, retries and device build context.

The screenshot profile binds all four concrete subclass variants and 25 inherited
method/basename mappings. Capture requires exactly the reviewed 68 old reference
names and 32 missing names. Compare requires exactly 100 names. Every reference
digest is input-pinned. Scale3, channel tolerance8 and maximum20 differing pixels
stay unchanged. Capture is a sole job. Complete capture evidence can report
capture_complete=true with 32 or more assertion failures, but the result, payload
and controller stay failed. PNG acceptance and a new frozen comparison remain
separate coordinator work. No reference assets are changed here.

Before JUnit assessment the controller copies only fresh canonical screenshot
actual/diff files from the exact app additional-output root into an exclusive
`app-additional-output/connected/<device>/screenshots/` tree.
Its sibling JSON receipt records origin, relative path, SHA256, size, freshness
and copy errors. Partial retained files and original failures survive refusal.
Symlinks, escapes, stale/duplicate/unstable files, invalid PNG framing/CRC/compressed rows,
unrecorded files and missing required actual/diff outputs refuse. Passing cases
may emit no images. A complete 100-case XML does not imply 100 fresh captures.

Each native selector is one job/preparation/emulator lifetime. The controller
derives its literal reset flag only from that exact profile, sets0 on every
other job and clears an inherited flag during execution setup. It checks exactly
one reset line for the actual owned emulator and exactly one finalized selected
case; source inclusion, SDK guards and native-dialog assertions remain bound.

Immediately before instrumentation, the maintained launcher emits one
`TOKKL_DEVICE_BUILD` JSON line for the emulator it booted. A single owned-serial
getprop request supplies brand, device, fingerprint, build ID, model, build type,
SDK and codename. The line also names that serial and AVD and records the host
observation time in nanoseconds. Attached devices emit no owned build record.
For screenshot, native and widget routes the assessor requires exactly one
bounded, duplicate-key-free record, an observation within this job's fresh log,
the actual owned serial/API/AVD and an exact match with the reviewed recipe's
device_build. Missing, stale, future, duplicate, malformed or mismatched metadata
refuses. Clean comparison/native/widget results may pass only with all other
required evidence and a zero payload exit. Capture counts, original failures
and retained images survive metadata refusal; capture assertion failures and
nonzero payloads remain failed. Benchmark/API and full profiles keep their
existing actual build context checks through benchmark JSON. Matching synthetic
fixtures prove source behavior only. This emitter has not been run on a device;
fresh actual applicability and coordinator review/adoption remain required.

audit_plan.py now checks exact reference phase/name sets and records SDK/AVD
metadata for APIs present in the plan. A host reference audit may describe the
68+32-pending tree; it never certifies screenshot comparison. The audit with a
configuration still reads live resources and is outside this source-only sitting.

Run focused modeled fixtures before the full host suite. TOKKL_HOST_TEST_CACHE
only selects the host runner's disk scratch parent. It is not a runtime setting.
No SDK, Gradle, device, live-resource or baseline execution was performed for
this change. Preparation helpers are exercised only against disposable modeled
projects by the pure host suite. Fresh matching actual cheap-live applicability,
independent root review, complete input freeze/adoption/lease, PNG review and
all actual device/release/baseline criteria remain pending. Historical failures
and unknowns remain valid. Existing accounting remains26/32 preparations used
and31,838,445,568 of34,359,738,368 bytes carried, with2,521,292,800 remaining.
Six remaining slots are arithmetic only. The minimum nine final preparations,
eleven with both API31 diagnostics, still exceeds that capacity.
