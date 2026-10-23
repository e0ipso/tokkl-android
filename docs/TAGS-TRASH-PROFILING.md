# Tags and Trash profiling proposal

TKA-0263, source checkpoint at integration
`aa38398c2286d28e50717d9f463af832d0945d79`, 2026-10-23.
Runtime **UNVERIFIED**. The source proposal is retained in private commit
`ac968fe9`. Root subsequently approved the five-file source implementation
and two benchmark-only network policy files. The overlay below is now
implemented for source review, with synthetic host canaries. It has not
been compiled, installed or executed on Android. No timing result, runnable
grant, frozen runtime, APK certification or ticket completion follows.

The private `40d6f3d1` checkpoint is preserved. A later source correction
lets eager seed mode freeze observed ready geometry/environment before its
measured windows; lazy replay validates that accepted baseline. This changes
only `CatalogProbe.kt` and this document. The target, four benchmark methods,
Full compilation, input protocol and original tests/references are unchanged.

The source-only sitting used
`/home/e0ipso/.t3/worktrees/tokkl-android/t3-77d7a373`, branch
`chore/implement-sift-tickets-20`. No build, SDK, device, container, install,
cache reclaim, snapshot preparation or adoption ran. The tracker remains
in `/home/e0ipso/workspace/apps/tokkl-android/.ai/sift`.

Main took this overlay at integration
`b2e4882f97d933686c3ca77fcbf824bd610b84fd`, with all seven driver files
byte for byte as in `9e9b36ac`. Their SHA-256 values are the `OVERLAY` pins
in `scripts/device_verification/runtime/catalog_profile.py`, so changing any
of them needs new pins. Landing compiled, installed and ran nothing. The frame
measurements on both revisions wait for the wave's final gate. Run the host
canaries with `python3 -B test/catalog_frame_summary_test.py`. `npm test`
leaves them out on purpose. The Catalog composite check accepts a historical
delta of exactly the seven driver files, the two verification helpers, this
document and that test file, and a Node wrapper would add a twelfth path.

## Evidence and interpretation

Compare eager `d16c97a5e98ab0747041b02b8dd9a058bc84c700` with lazy
`4b3891e69f3d5290de73570aa759eb1c3337914a`. Their build files,
`macrobenchmark/` and dependency catalog are identical. Their three changed
production files are `CatalogPage.kt`, `TagsScreen.kt` and `TrashScreen.kt`.
The other changes are the two catalog device test files. Preserve that
production difference when preparing the comparison.

[LongCatalogListTest.kt](../app/src/androidTest/java/com/github/e0ipso/tokkl/trash/LongCatalogListTest.kt)
lines 51-79 establish the datasets and account cleanup. Lines 81-128 mount
contents directly, collect asynchronous frame callbacks, and run three
five-swipe windows under the Compose test clock. The callback discards the
dropped-report count and has no drain barrier before clearing samples.
The source confirms the measurement limitations; it cannot reproduce a
release frame regression without execution.

Retained debug Tags medians are 1623.204 versus 3365.544 ms for completion
and 189.957 versus 485.231 ms for per-window delivered-frame p95. These
values are ticket evidence, not new measurements. The eager/lazy opening
medians were 1699.269/180.880 ms for Tags and 6299.164/166.152 ms for Trash.
Different travel, first-use work and host interference remain unresolved.

[BENCHMARK-NOISE.md](BENCHMARK-NOISE.md) records one complete later local
trial with 25 swap-observed intervals. It establishes neither quiet-host
repeatability nor a cause or mitigation. That series has stopped. The
existing CI comparator's 10% limit remains unchanged. No results proposed
here become a substitute CI baseline.

## Approved source ownership

The implementation adds these files. No existing production, dependency,
AA instrumentation, runtime, workflow or screenshot file changes:

- New `macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/CatalogFrameBenchmark.kt`
  and `CatalogProbe.kt`: four focused methods, real input, trace markers,
  output records and travel validation. Never invoke Startup/Timer methods.
- New `app/src/benchmark/java/com/github/e0ipso/tokkl/CatalogProfileActivity.kt`
  and `app/src/benchmark/AndroidManifest.xml`: a profile-only activity in the
  existing benchmark variant, mounting the actual contents directly. This
  source set was absent before this overlay. Access to the internal content
  functions requires target-module code; the external test APK cannot call
  them directly. Root approved this target source-set addition.
- `scripts/catalog-frame-summary.py` and the explicitly authorized
  `test/catalog_frame_summary_test.py` host canaries for fixture bounds/hash,
  actual input, phase accounting, FrameTimeline pairing, clock mapping,
  travel mismatch and missing evidence.
- `macrobenchmark/src/benchmark/AndroidManifest.xml` and
  `macrobenchmark/src/benchmark/res/xml/catalog_profile_network_security.xml`.
  Root approved these after cached Macrobenchmark 1.5.0 proved its inherited
  cleartext policy permits only localhost. The new policy retains localhost
  and adds only 127.0.0.1 and 10.0.2.2 for disposable fixture preparation.
  The app's shipped network policy is untouched.

The two new Kotlin driver files deliberately declare package
`com.github.e0ipso.tokkl.catalogprofile`, although their approved source
paths remain under `benchmark/`. Existing CI selects the package
`com.github.e0ipso.tokkl.benchmark`; keeping the new class outside it avoids
silently expanding ordinary CI and its exact two-case runtime recipe.
The explicit class for a later reviewed method filter is
`com.github.e0ipso.tokkl.catalogprofile.CatalogFrameBenchmark`.

Use the existing dependencies and benchmark build type. Do not edit
app `src/main`, existing Compose tests, screenshot assets, stable keys, AA's
accessibility sources/dependencies, instrumented runner, controller,
workflow, comparator or runtime profiles. If the existing dependencies
cannot implement a requirement, return the concrete limitation to root.

Apply exactly the same reviewed overlay bytes to both historical revisions.
Record base commit, overlay commit and per-file hashes separately; the
overlay must not copy current production files into either historical tree.
`app/build.gradle.kts` lines 122-134 already define release-derived,
debug-signed, profileable benchmark builds. The macrobenchmark module is
self-instrumenting and runs in another process. Use `CompilationMode.Full()`
as the existing startup/tap benchmarks do. Record the historical toolchain
and resolved dependencies; an environment incompatibility stops the stage
instead of silently upgrading one side.

## One unchanged driver and fixture

Prepare disposable real-server fixtures outside measured intervals using
the account protocol in [TestAccounts.kt](../app/src/androidTest/java/com/github/e0ipso/tokkl/TestAccounts.kt).
Do not import or alter that androidTest helper. Propose the current script's
Tokkl 1.8.1 pin on both sides, SHA-256
`70106ba2a6bc5b23c11f443f33bf2c7fb76d3af75b0335461047c4d27bd52e9b`.
The old diagnostic used Tokkl 1.5.0, so this is a declared difference from
that diagnostic. Server compatibility must pass before measurement; do not
mix pins or change one side after failure. The rendered fixture is one retained server response
replayed identically into the target activity on both revisions, with no
network or mutable account refresh inside a window.

Require exactly 500 Tags, `Measured tag 000` through `Measured tag 499`,
and exactly 1000 Trash entries, `Measured trash 0` through `Measured trash 999`.
Trash entries start at `2026-10-19T08:00:00Z`, stop 300 seconds later,
and are then deleted. Account timezone is Europe/Madrid. Retain response
order, IDs and all rendered fields; derive the ordered anchor list from the
actual response, since Trash lists newest deletions first. Verify unique
IDs, counts and names before measuring. Fixture hashes must match across
revisions. The 1000-entry limit is `TrashServer.LIST_LIMIT`.

The activity reads the retained non-secret fixture before rendering. A
gzip/Base64 String Intent extra transfers it from the helper, with a
64 KiB encoded and 512 KiB decoded cap and a required decoded SHA-256.
Both sides fail closed on a limit/hash failure. No account credential
travels in an Intent or artifact. Use the exact existing calls:
`TagsContent(TagsState(data = TagsData(tags)), false, emptyList(), TagsActions())`
and `TrashContent(TrashState(entries = entries), false, null, TrashActions())`.
Null Trash settings deliberately reproduce the earlier content workload;
account-formatting performance remains outside this comparison.

Use a plain ComponentActivity with edge-to-edge, light TokklTheme, font
scale 1.0, empty cached Tags and no navigation frame, keyboard or overlays.
Match actual pixel viewport, density, orientation, status/navigation insets,
locale, renderer and 60 Hz refresh on the same API36 AOSP device configuration.
Record actual values before every cell and reject mismatches. Use the
existing animation policy unchanged on both sides; never disable scroll
physics or introduce a Compose test clock. The overlay may report viewport
and phase state outside the content bounds but must add no visible UI.

## Window protocol and achieved work

Four cells per revision: Tags first-scroll, Tags steady-state, Trash
first-scroll and Trash steady-state. Each cell has exactly 10 measured
windows, so the proposed pair totals 80. Missing, failed or rejected windows
stay in the record and prevent a completed comparison; no automatic retries
or replacement samples are proposed.

For every first-scroll window, restart the target process, preload fixture,
render at the top and await the first hardware frame commit within four seconds.
Do not scroll to collect setup anchors. This preserves deferred first-scroll
composition. Record render-request to first-ready-draw duration separately
as opening evidence; the frame window starts after readiness.

For steady-state, use a fresh process per cell, render the same fixture,
then traverse every row to the final anchor and back to the initial anchor
once before the 10 windows. Both implementations must expose the same
ordered rows in this warmup. Between windows return to the initial anchor
using input without recreating content. Warmed means prior code/data/row
exposure, not a promise that LazyColumn retains every row composition.
Cap each full warmup or reset traversal at 600 explicit drags or 180 seconds,
whichever comes first; failure stops the cell. Tag and Trash bounds differ,
so record their actual traversal totals separately.

Derive a fixed gesture rectangle from the matched content viewport: center
X, Y from 80% to 20% of the unobscured list height. Freeze the resulting
absolute pixel coordinates identically for each workload/revision. Each
measured window injects five upward gestures of 150 ms. Use UiAutomation
MotionEvents, not default UiAutomator/Compose swipes: DOWN at 0 ms,
MOVE every 10 ms, UP at 150 ms, with linear Y interpolation and one
monotonic downTime per gesture. Record every requested/actual event time,
coordinate and injection result. Compute achieved input velocity from the
actual event series; reject nonmonotonic events or a failed injection.

After each gesture await 50 ms without a draw, observed by Choreographer
callbacks and capped at two seconds, before starting the next. This is an
observed quiet-draw condition, not proof that every animation has finished.
Record the achieved anchors and reject mismatched work. Cap the measured window at
15 seconds and retain timeout evidence. These waits belong to wall time;
they do not become frame durations. Obtain anchor bounds outside measured
input spans and trace the observation overhead separately.

Identify rows by exact unique fixture text/ID mapping, never by semantics
node count. Record initial/final first and last visible row IDs, ordered
indices, clipped bounds, screenshot and hierarchy. At every gesture boundary
record a common visible anchor's Y displacement. Accumulate pixel travel
through overlapping anchors; where no overlap exists, mark pixel travel
unknown and retain index travel and endpoint bounds. Do not invent a
LazyColumn scroll offset for an eager Column. Record output displacement
per unit time separately from injected finger velocity.

Compare the same window ordinals and phases. An equal-work claim requires
matching end-anchor indices and offsets within one pixel, complete travel
evidence and matching actual input timing within one refresh period.
Otherwise report the achieved difference and frame evidence as diagnostic,
and return a revised motion proposal to root. Do not retune one revision,
truncate its frames, or infer smoothness from a longer fling. Confirm the
final dataset anchor separately, outside timing, with the bounded traversal.

## Frames, boundaries and analysis

Use Macrobenchmark real-time FrameTimingMetric and retain a Perfetto trace
for every window. Cross-process markers must share the device monotonic
clock and name run/revision/workload/phase/window. Collect target-process
FrameTimeline expected/actual pairs within those markers. Report frame CPU
duration, overrun and deadline misses with explicit denominators, plus main
thread CPU execution and runnable delay from scheduler trace data. Guest
scheduling is not proof of physical host execution.

Keep an auxiliary target Window.FrameMetrics listener on a dedicated
HandlerThread. Copy metrics on callback, including dropped-report counts,
intended-vsync timestamp and total duration. Post and acknowledge a handler
barrier before opening and after closing each phase. Never clear a shared
list to define a boundary: retain the full event stream and partition by
timestamp. Late callbacks remain attributable; boundary-crossing frames,
unpaired FrameTimeline records, missing callbacks and trace loss are counted
explicitly. A barrier alone cannot prove no future callback will arrive.

Per window output delivered callbacks, sum of dropped reports, paired
FrameTimeline count, unpaired count, boundary count, CPU/frame-duration and
overrun p50/p90/p95/p99/max, deadline-miss numerator/denominator, wall time,
travel and trace path/hash. A dropped-report count is missing callback
evidence, not a count of janky frames. Use nearest-rank percentiles with
sample counts. Report both pooled distributions and all per-window values;
do not call the median of ten p95s a pooled p95. Unavailable metrics stay
unknown. A zero-frame or lossy trace cannot count as successful measurement.

Compare paired window deltas, within-cell spread and confidence intervals
separately for first-scroll and steady-state, with Trash as the control.
Ten samples are a minimum, not a guarantee of statistical resolution.
Describe unresolved variation; do not choose a production fix without a
reproduced implementation defect and matched post-fix proposal/results.
Opening measurements must retain the lazy benefit in the same conditions;
do not treat the old debug opening values as release certification.

## Quiet-host rejection and finite scheduling

At the source checkpoint no execution allocation existed for TKA-0263. The
global ledger then retained 13,107,466,240 reserved bytes of 16 GiB,
4,072,402,944 bytes remaining, and 12/14 preparations used. The two unused
preparations belonged to AA's initial verification. Do not borrow them, reset
the ledger, reclaim cache, or use a prior adoption for this proposal.

After source review and a separate explicit root allocation, propose one
serial pair, with no overlapping AA, Gradle, emulator or benchmark job.
All Gradle/device commands continue through the existing memory gate and
`scripts/instrumented-tests.sh`; no direct connected-test shortcut. The
script also runs selected app API tests and validates nonempty results.
Use only the existing API package plus the four new benchmark methods;
record actual API counts rather than promise a fixed count from old source.
No whole suite, API31 matrix or unrelated benchmark is part of this pair.

Proposed stage caps, subject to root's resource review: source/fixture
validation 5 minutes, two build stages at 20 minutes each, four serial
revision/workload sessions at 15 minutes each, offline analysis 10 minutes,
and owned cleanup 5 minutes. Global wall cap 120 minutes including setup,
with 80 measured windows maximum, two target builds and zero reruns.
Use eager Tags, lazy Tags, lazy Trash, eager Trash order to reverse the
revision order for the control. Retain the order as a limitation; this
finite pair alone cannot establish across-session repeatability.

Use an owned read-only host observer before setup and throughout every
session, with monotonic and UTC timestamps. Proposed diagnostic admission
requires 30 seconds of continuous quiet samples, every second, with no gap
over 2 seconds. Maintain the same thresholds through measured intervals:
zero positive pswpin/pswpout deltas, no cgroup swap growth or memory-event
increments, host and cgroup memory PSI full avg10 <= 0.1%, some <= 1%, CPU
PSI some avg10 <= 5%, host CPU busy <= 70%, iowait <= 2%, and MemAvailable
>= 9 GiB. Record raw /proc/stat, vmstat, pressure and cgroup values and the
exact busy/iowait denominator, with matched observer overhead on both sides.
Missing/reset counters, a breached interval, observer failure/unknown exit
or missing coverage reject quiet-host interpretation for the affected pair.
Keep rejected data. Stop after a breach; do not wait indefinitely or replace
the window. These stricter diagnostic rules add interpretation checks and
never relax existing memory gates, controller admission or global budgets.

At the source checkpoint there was a concrete admission mismatch. In
`scripts/device_verification/runtime/scoped_results.py` lines 19-28,
`BENCHMARK_ARGS` selects the entire benchmark/API package pair and
`BENCHMARK_CASES` requires exactly coldStart and startAndStop. The README's
"Exact benchmark/API diagnostic recipe" also fixes 1800 seconds and refuses
method filters; undeclared jobs retain the unfiltered 5400-second floor.
Neither path admits these four focused methods. Reusing benchmark-api-v1
would execute unrelated workloads and violate this proposal's scope.

TKA-0323 then added the `catalog-frame-api-v1` profile in
`scripts/device_verification/runtime/catalog_profile.py`. Its one job has
label `catalog-frame-api` and runs on API36, not standalone, for exactly
7200 payload seconds. It selects the catalogprofile and API packages. The
profile validates configurations and results only. It grants no execution,
preparation, count or byte budget.

Root approved the target benchmark source-set addition. A run of these exact
four methods within the stated caps still needs fresh applicability, frozen
artifact declarations, finite accounting amendments and a matching root
adoption. This sitting adds no runtime profile or framework change and
proposes no bypass. This document authorizes no benchmark run.

## Identity, artifacts and closure

The source driver has two exact argument shapes for a later reviewed route.
Both require `catalogRunId` (unique 1-32 character ASCII identity) and
`catalogRevision` (the historical base, distinct from the composite HEAD).
Launcher/AGP-owned non-catalog arguments remain separate.

- **Eager seed:** `catalogRevision=d16c97a5e98ab0747041b02b8dd9a058bc84c700`,
  `catalogSeed=true`, and `catalogServerImage` equal to the 1.8.1 pin above.
  Omit `catalogEnvironmentSha256`, `catalogTagsViewport` and
  `catalogTrashViewport` together, and omit both fixture arguments.
- **Lazy replay:** `catalogRevision=4b3891e69f3d5290de73570aa759eb1c3337914a`,
  `catalogFixture` and `catalogFixtureSha256` from the accepted seed's
  retained gzip/Base64 transport, plus all three exact values from its
  `replay_arguments`: `catalogEnvironmentSha256`, `catalogTagsViewport`
  and `catalogTrashViewport`. Omit `catalogSeed` and `catalogServerImage`.

Partial, mixed or unknown catalog arguments refuse before fixture setup.
Lazy cannot independently seed; eager replay is outside this canonical pair.
One cached baseline binds all four methods to the identical run, revision,
output directory and catalog argument values; a change refuses rather than
resetting the baseline or seeding again. The same fixture bytes, IDs and
response-array order are reused. Across separate instrumentation processes,
root/TKA-0323 must bind lazy replay to the accepted eager receipt; a caller's
hash declaration alone does not establish that provenance.

Seed setup has a five-minute limit,
  no retries and 10-second network operation timeouts. Its receipt records
  the owned email/user/workspace and cleanup status, never credentials.
  A failure before user-ID discovery leaves cleanup UNKNOWN with the email
  for root inspection. Successful export requires user DELETE 204 and
  subsequent authentication refusal 401/403. The declared image still needs
  the guarded route's actual container-pin evidence.

Seed does not need guessed bounds or a separate calibration job. At the
first `ready` observation of each workload, before that workload's measured
windows, validate fixture identity, nonempty anchors, font scale 1.0,
60 Hz and hidden IME, then freeze actual viewport coordinates. The first
workload also freezes the exact compact environment JSON shared by both.
JUnit method order is unspecified; either first-scroll or steady-state may
provide that workload's first ready observation. All later ready, snapshot
and settle observations must match the frozen environment serialization and
per-workload viewport serialization, including across methods and target
process restarts. A mismatch never replaces the baseline. Gestures derive
only from these observed, frozen bounds.

Replay checks the compact environment's UTF-8 SHA-256 against the seed
value before input, requires its observed viewport coordinates to equal
the supplied seed coordinates, and retains the existing 0.01-pixel check.
It then enforces the same immutable serialization checks within its run.
Raw `ready-*.json` is retained before validation so a refusal keeps evidence.
The ordinary window/opening/fixture formats are unchanged.

Two new workload receipts are written once as
`<run>-<revision>-<workload>-baseline.json`, schema
`catalog-workload-baseline-v1`. Fields are `run`, `revision`, `mode`,
`fixture_sha256`, `workload`, `source_phase`, `environment`,
`environment_serialized`, `environment_sha256`, `viewport`,
`viewport_serialized` and `viewport_argument`. Serialization strings are
the exact observed `JSONObject`/`JSONArray.toString()` values; viewport
arguments are the frozen finite Float coordinates joined with colons.
After both workloads have been observed, write exactly one
`<run>-<revision>-baseline.json`, schema `catalog-baseline-v1`, with
`run`, `revision`, `mode`, `fixture_sha256`, `workloads.tags`,
`workloads.trash`, and the three-field `replay_arguments` object above.
Existing files refuse overwrite. An incomplete run without that combined
receipt cannot establish a complete accepted baseline.

TKA-0323 implements this external contract as `catalog-frame-api-v1`.
The contract must accept only these seed/replay shapes,
validate both workload receipts and the combined receipt against raw ready
observations, fixture/run/revision and window identities, and bind replay
arguments to the accepted seed. Synthetic negatives should cover partial
arguments, lazy reseeding, changed run/parameters, replaced baselines,
mutated serialization/hash/geometry, incomplete receipts and mismatched
replay, through the validated entry path. This document grants no execution
authority.

The target's explicit components require DUMP permission, and its control
receiver also checks a 256-bit per-launch nonce. The helper adopts only
DUMP while sending ordered control broadcasts and drops that identity in
`finally`. Fixture transport is non-secret; account credentials stay in
the script's private file and helper memory. Input uses one touchscreen
finger with explicit pressure, coordinates and actual monotonic event times.
The target retains received events, including coalesced history.

Each window exports an acknowledged finite frame/touch fence. Final windows
remove the listener before draining its handler; intermediate windows retain
the stream, and the reader reassembles late callbacks by process generation
before timestamp partitioning. Dropped-report phase attribution remains
UNKNOWN. Wall time includes phase close acknowledgement and excludes the
subsequent artifact transfer. CPU interval sums from guest scheduler data
are distinct from the harness's `frameDurationCpuMs` rendering metric.

`python3 -B scripts/catalog-frame-summary.py --queries OWNED_DIRECTORY`
writes seven read-only SQL files and executes no trace processor. Core
table names are supported by the cached Macrobenchmark query and the
[Perfetto core table documentation](https://perfetto.dev/docs/analysis/sql-tables).
The later reviewed analysis step must export each query's CSV from each
actual trace, retaining command exits and hashes. Unsupported tables or
missing data fail closed; no surrogate frame data is generated.

`--corpus FILE` takes a `catalog-corpus-v1` JSON object with `overlay_sha256`
and an ordered `revisions` array for the exact eager/lazy pair. Each revision
contains `revision`, `benchmarks` (benchmarkData JSON paths), `windows`
(40 window sidecar paths), and `analysis`. The latter maps each trace
filename to `directory`, `trace_sha256`, and a `csv_sha256` map keyed by
the seven query names. Paths resolve relative to the corpus file.
The reader requires all 80 trace references, unique bytes, matching CSV
hashes, ten windows per method, valid clocks and matched input/end anchors.
CSV hashes bind the submitted exports; they do not independently prove
that a trace processor produced them. Root retains that command provenance.
Output includes per-window and pooled distributions, paired p95 deltas and
an explicitly exploratory paired bootstrap interval, with no automatic
regression verdict. Host quietness, opening advantage, actual compilation
and final acceptance remain separate root-reviewed evidence.

The opening sidecars use the same target probe on both revisions. They
record render-request to first-commit phase clocks and unmerged row-text
exposure before scrolling. They preserve the old tests rather than add an
eager-incompatible lazy assertion to the independent driver. Absolute pixel
travel stays UNKNOWN; per-gesture row IDs, indices and unclipped/clipped
bounds remain the achieved-work evidence. Endpoint or input mismatches
reject the comparison instead of adjusting one revision's work.

The later review packet must identify both base commits, identical overlay,
fixture and motion hashes, toolchain/dependency versions, target and helper
APK hashes, Full compilation evidence, script/server pins, device build and
display values, runtime/configuration/lease identity, stage caps and actual
commands. Reuse existing certification only for unchanged inputs; the
TKA-0219 receipt is not certification of new APKs.

Retain raw command stdout/stderr and exit codes outside the checkout,
JUnit XML, benchmark JSON, fixture manifest without secrets, per-window
event/anchor records, phase markers, raw frame callbacks, all 80 proposed
traces, observer intervals, rejection decisions and a hashed artifact index.
Validate every referenced trace exists and every window has a disposition.
Report missing evidence before drawing a performance conclusion.

Delete only owned disposable accounts, fixture grants/files, credentials,
containers, emulator and observer scopes through their existing cleanup.
Confirm account/workspace removal, temporary device files absent and scopes
reaped, slot released and original cgroup limits restored with post-closure
observations. An unknown cleanup or observer result remains unknown and
prevents a clean closure claim. Preserve logs, traces and rejected samples.

Reuse the unaffected TKA-0219 evidence in
`/home/e0ipso/.cache/sift-w10/lazy/verification.txt`: 119 filtered device
cases including 68 unchanged references, two largest-font stable-position/
semantics-order cases, 15 unit cases, lint zero errors with three unchanged
warnings. No source in those checks changes here. New driver compilation,
live motion/travel, frame accounting, quiet-host comparison, opening benefit,
and any conditional regression fix remain pending actual evidence. Every
TKA-0263 runtime acceptance criterion stays open at this source checkpoint.
