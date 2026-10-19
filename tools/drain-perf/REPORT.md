# Drain throughput investigation

The largest measured cost is serialized device work and its repetition. The coordinator
usually keeps work moving. Removing repeated full verification is the clearest immediate
saving; batching small checks is the larger opportunity. A daemon speedup is unverified.

This analysis preserves 87 child transcripts and 10,374 tool calls from four sessions.
Two tool calls lack a result. The principal sample is waves 6 and 7 in session
`d8ce5e9a-90ec-4035-9d07-f8489841ef5e`. Other captured work, including the start of wave 8,
is retained in the CSVs but excluded from those wave totals. At the initial inspection,
that session had 74 transcript files and 74 metadata files, explaining the stated 148
subagent files. The final snapshot includes later additions. See
[manifest](data/manifest.json), [subagents](generated/subagents.csv) and
[tool calls](generated/tool-calls.csv). All times below are UTC or elapsed seconds.

## Ranked causes and changes

Costs use different units where the evidence demands it. Worker minutes can overlap;
critical-path minutes cannot. Savings below are forecasts unless explicitly called an
observed duplicate. They overlap and must not be summed.

The measured cost ranking is below. These are nested views of the work, so the same test
can appear in a device, added-scope, coverage and integration cost.

| Cost rank | Observed cost | Interpretation |
| --- | ---: | --- |
| 1 | 854 wave 7 agent minutes in recognized device commands | Includes lock wait, setup, tests and teardown; 218.1 lock minutes are part of this work, not extra elapsed time |
| 2 | 469.2 agent minutes in eight sittings wholly devoted to added scope | Wave 7 grew from 25 to 44 completed tickets; includes their verification and integration |
| 3 | 151.4 agent minutes before first explicit edit | Upper bound on orientation, including baseline checks and some Bash edits |
| 4 | 142.3 agent minutes in separate Gradle commands | Compilation, unit tests and lint, with startup/configuration inseparable |
| 5 | 94.9 agent minutes in gate coverage | Authoring plus tests, overlapping the categories above |
| 6 | 67.5 agent minutes in retained-worker integration rounds | Conflict repair plus validation; partly avoidable, not all repeated work |
| 7 | 37.2 minutes of duplicate full-suite Gradle execution across waves 6 and 7 | The strongest demonstrated saving on identical code |

The change priorities below consider both cost and the evidence that it can be removed.

| Rank | Measured cost | Change and estimated saving | Evidence |
| --- | --- | --- | --- |
| 1 | Wave 7 has 203 local device invocation logs, 235 boots, 640.7 minutes inside their visible Gradle runs and 218.1 worker minutes waiting for the lock. | Batch the affected class union per sitting; retain targeted checks, and run a full suite once at close. Combining 50 to 100 small invocations saves about 20 to 40 minutes of outer setup at the observed 24-second median, plus avoided Gradle/install work. Budget **35 to 75 wave minutes**, unverified until trial. | [Device inventory](generated/device-runs.csv); [analysis](generated/analysis.json); `01-worker-contract.patch` |
| 2 | A second full suite ran on identical content in each of waves 6 and 7. The repeated close runs spent **10m42s and 26m29s inside Gradle**, respectively. | Reuse a passing content-and-environment receipt after cherry-pick or docs-only capture. The demonstrated saving is **37m11s of Gradle wall across the two waves**, plus setup. Wave 7 alone is about **27 minutes** including its measured outer work. | [Tree audit](data/tree-evidence.json); [w6 close](data/logs/sift-drain.7oM4Ky/gate-w6/close/instrumented.log); [w7 close](data/logs/sift-drain.7oM4Ky/gate-w7/close/instrumented-api36.log); `02-wave-gate.patch` |
| 3 | Wave 7 batch coverage took **94m53s**. It includes **52m58s** of device commands and **9m37s** of separate Gradle commands. Many deferred tests already existed as disposable worker tests. | Commit useful regression/acceptance tests with their feature; defer by exception with a reason and owner. Forecast **20 to 40 minutes less serialized gate time**, since authoring and some checks move into overlapping sittings. Net labor savings are unverified; do not claim the whole 95 minutes disappears. | Agent `a795371c5818f3de3` in [subagents](generated/subagents.csv); [waivers](data/logs/sift-drain.7oM4Ky/gate-w7/waived.txt:1); `01` and `02` patches |
| 4 | Wave 7's 20 sitting agents have **151.4 worker minutes before the first explicit edit**, median **7m17s**, and return **3,403,276 characters** of tool output before it. This includes bug baselines and some Bash edits. | Use the roughly 5 KB proposed contract instead of the 10,332-byte, 186-line template, plus a checked context manifest and changed-input reads. Forecast **20 to 40 worker minutes** saved, unverified. Source exploration still dominates the input volume. | [Original template](data/logs/sift-drain.7oM4Ky/template.txt); pre-edit columns in [subagents](generated/subagents.csv); `01-worker-contract.patch` |
| 5 | Three retained-worker integration rounds consume **67.5 worker minutes** after reassignment. H3 alone is 47.1 minutes and includes new device checks. These were stale-base landings, not all avoidable duplication. | Keep shared documentation out of competing sitting ownership, land stale bases in an integration worktree, and reuse receipts only if their checked inputs survive integration. Forecast **5 to 15 minutes per similar wave** from avoiding conflict repair, unverified; preserve the validation that found a real compile failure. | N1, H3 and T2c report/reassignment records in [messages](generated/messages.csv); [wave 7 event log](data/logs/sift-drain.7oM4Ky/wave7-plan.txt); `02-wave-gate.patch` |

Instrumentation is a required companion change, with no claimed direct saving. Patches
03 and 04 add runner stage timings and a locked, group-addressed event log. They make
later optimization decisions measurable and give resuming coordinators a reliable record.

The template shrink alone is a small input saving. Across 18 fresh wave 7 sittings, about
5 KB less contract text each is roughly 90 KB, compared with 3.4 million pre-edit output
characters. The 20 to 40 minute orientation forecast requires the context manifest to
avoid repeated exploration as well. A shorter template by itself does not justify it.

## Where wall time goes

Wave 7 runs from the first sitting at **00:55:49** to the final Gradle close result at
**17:05:09**, about **16h09m20s**. Its 22 agents account for **25h02m09s** of summed agent
wall time. Wave 6's 12 agents account for **7h51m07s**. These sums include coverage and
capture, and resumed workers' spans. They exclude coordinator wall time. See
[subagents](generated/subagents.csv), [close timestamps](data/close-file-times.json), and
[analysis](generated/analysis.json).

The initial wave 7 plan contains **25 distinct ticket IDs**. Its final shipped list has
**44 done IDs**, including all 25 originals and **19 additions**, a **76% increase**.
N1, T2a, D3, D4, C1, H4, RC and RH are wholly added sittings and sum to **469.2 worker
minutes**. Other sittings mix original and added tickets, so this does not assign all
scope-growth cost. Nor is it a counterfactual 469-minute wave saving; their work overlaps.
Report original versus added scope and resolved-ticket counts alongside throughput.
[Scope audit](data/scope-evidence.json), [initial/event plan](data/logs/sift-drain.7oM4Ky/wave7-plan.txt),
[shipped list](data/logs/sift-drain.7oM4Ky/gate-w7/shipped.txt).

The transcript-derived partition below assigns each instant of each child agent once.
Background commands use completion notifications, with device/build work taking precedence
over overlapping waits. It is an attributed agent-time partition, not CPU utilization.

| Attributed activity | Wave 6 worker minutes | Wave 7 worker minutes |
| --- | ---: | ---: |
| Recognized device commands, including their lock waits and setup | 200.3 | 854.0 |
| Separate Gradle commands | 51.3 | 142.3 |
| Other wait commands not already overlapped by recognized work | 8.0 | 100.6 |
| Read tool/command intervals | 1.7 | 25.1 |
| Coordination, edit, other explicit commands | 6.7 | 7.7 |
| Model, implementation, reasoning and unobserved intervals | 203.2 | 372.4 |

The last row is where much coding and orientation happens. It cannot honestly be split
into pure coding and reading from the available timestamps. The pre-edit bound above is
a separate view, not an additional cost. Conservative shell recognition leaves some device
and build work in the other rows. Visible log build totals and explicit lock waits provide
independent checks. The lock total is a subset of time spent in device-associated commands
or waits; do not add it again. [Reconstruction code](rebuild.py),
[partition fields](generated/subagents.csv), [log totals](generated/logs.csv).

The implementation portion, from the first wave 7 dispatch to H4's return, lasts
**12h50m49s**. At least one implementation agent is present for all but **2m44s** of it,
even without counting coordinator work. Presence includes waiting, so it proves utilization
of agent slots, not productive coding. The median measured report-to-next-assignment gap
is **92.9 seconds** over 21 wave 7 reports. The apparent 77-minute J1 gap overlaps H1;
the apparent 111-minute C1 gap overlaps H4 and the review/fix gate. These are not coordinator
idle time. [Turnaround](generated/turnaround.csv), [subagent intervals](generated/subagents.csv).

Wave 7's gate is about **3h18m31s** from H4's return through the final close:

| Stage | Time | Overlap and interpretation |
| --- | ---: | --- |
| Codex review | 14m17s | Shell command `toolu_01PVD11uMAkkCsUqBfEc2dwa` |
| RC and RH review fixes | 27m35s and 27m59s | Run in parallel, about 28 elapsed minutes |
| Coverage | 94m53s | The largest serial gate stage |
| Knowledge capture | 23m49s | Runs alongside close checks in this actual run |
| API 36 then API 30 close | 52m17s | One sequential shell command; includes the duplicate API 36 run |
| Final assemble/check | About 74s | Follows device checks |

Gate handoffs and integration account for the remaining intervals. The proposed process
keeps capture overlap, which this coordinator already used. Claiming 24 minutes saved by
parallelizing capture would be wrong. Stage evidence is in [tool calls](generated/tool-calls.csv),
[subagents](generated/subagents.csv) and [close file times](data/close-file-times.json).

## Fixed device-run cost and Gradle

Matched wave 7 invocations with usable XML give a median **24.16 seconds outside Gradle
after subtracting the printed lock wait**, across 38 cleanly bounded examples. The observed
range is **15.81 to 46.36 seconds**. This includes boot, server/proxy setup, credential transfer,
cleanup and notification delay. It is not a measurement of boot alone.
[Matched rows](generated/device-runs.csv).

| Example | Command interval | Gradle elapsed | Sum of JUnit test times | What can be separated |
| --- | ---: | ---: | ---: | --- |
| H4 one keyboard-test mutation, `0162-no-imepadding` | 60.37s | 37s | 5.54s | 23.37s outside Gradle; 31.46s of Gradle is outside recorded test bodies |
| H2 small package reproduction, 2 tests | 40.28s | 19s | 0.89s | 21.28s outside Gradle; 18.11s of Gradle is outside test bodies |
| Coverage full API 36, 262 tests | 24m22.83s | 24m01s | 17m47.69s | 21.83s outside Gradle; 6m13.31s outside test bodies inside Gradle |
| Close full API 36, 262 tests | About 26m55s | 26m29s | 17m42.30s | Filesystem start/exit markers, rather than a separate tool call, bound this run |

JUnit time excludes runner/orchestrator overhead. The gap inside Gradle includes startup,
configuration, APK tasks, installation and orchestration; assigning it all to build or to
`--no-daemon` would be false. A normal invocation has **one** `connectedDebugAndroidTest`
Gradle task graph that also builds its APKs, not a distinct unconditional APK build command.
The saved script's `run_tests` function proves this. Many tasks are already `UP-TO-DATE`.
[Runner baseline](data/baselines/instrumented-tests.sh:521),
[JUnit projection](data/junit.jsonl), [device inventory](generated/device-runs.csv).

There are **148 and 402 visible single-use daemon notices** in local wave 6 and 7 logs,
respectively, and **149 and 403 visible BUILD summaries**. These are log observations,
not a proof of that many unique processes, because logs can be copied and quiet/failed
invocations can omit output. The transcript command recognizer finds **81 and 186 separate
Gradle commands**, plus **66 and 164 device command groups**. Groups can run loops. Wave 7
device logs contain **236 BUILD summaries**, including batch tasks. These definitions
avoid counting a command group as one Gradle process. [Executions](generated/executions.csv),
[logs](generated/logs.csv).

No retained log has Gradle profiling or timestamped startup/configuration events. Therefore
the startup tax, configuration tax and daemon/cache saving are **unverified**. Gradle's
suggestion to enable configuration cache is not a benchmark. The candidate timing patch
supports `TOKKL_TEST_PROFILE=1`; collect three unchanged and three changed-input runs under
a reserved quiet slot, inspect the local profile's startup/configuration/task times, then
compare compatible settings. Preserve the current 2 GB heap and in-process Kotlin policy.
Do not run `--stop` against another drain. Dynamic runner properties and worktree/cache
portability need validation before enabling configuration caching. No such experiment was
run during this investigation.

The API 30 close has **12 batches**, **194 tests**, and **3 failures**. Its saved file
creation/exit markers measure **25m23s, within about one second**, rather than the suggested
27 minutes. Its 12 visible Gradle runs total **20m24s**. The remainder includes initial
installation/listing, 12 boots and teardown. It exits 1. Dropping API 30 is an owner scope
decision recorded later in the drain; a replacement supported-API run still costs time.
Do not book 25 minutes of savings simply by relabeling that matrix entry.
[Batch log](data/logs/sift-drain.7oM4Ky/gate-w7/close/instrumented-api30.log:16),
[exit markers](data/close-file-times.json).

## Repetition, deferral and investigations

`ba0a100` and `cc8c067` have the **same entire Git tree**,
`cce1bbf94129566e10793559a0704f22f92ffb57`. The difference is commit identity after integration,
not docs. `cc8c067` to `51e21d5` changes docs/knowledge only; their code listings match.
Likewise wave 6 coverage's `d21f09e` and integrated `ec480ca` share an entire tree.
That is stronger evidence than comparing commit IDs. Device counts and success are backed
by saved XML/logs, not just worker reports. [Tree audit](data/tree-evidence.json),
[coverage full log](data/logs/sift-w7-coverage/full-api36.log:94),
[wave 6 coverage](data/logs/sift-w6-coverage/at-full-1.log), [JUnit](data/junit.jsonl).

There are **at least 3 identified unfiltered full-run attempts in wave 6 and 11 in wave 7**.
Wave 7 includes harness experiments and failures, not eleven redundant green closes.
Coverage has one full attempt and close has two APIs. The per-sitting invocation counts
are 26 D1, 25 H4, 20 N1, 18 H3, 15 R1a, 12 D2, 11 each P1/R1b, 9 H1, 8 T2c, 7 H2,
6 each C1/T1/coverage, 4 each D3/RC/T2a/T2b, 3 D4, and 2 RH. Some early failed startups
have no BUILD summary. See every path and scope certainty in [device-runs.csv](generated/device-runs.csv).
Without dirty-tree fingerprints, a repeated class name alone does not establish duplicate
verification on identical code. Most same-tree claims beyond the cases above remain unverified.

For TKA-0162, the **12 theme-package runs** are three `diag`, three `f36`, one loaded-host
run and five `accept` runs. They total **33m27s inside Gradle**, plus **4m28s lock wait** and
repeated outer setup. `0162-no-imepadding` and the final `verify-theme-default` are additional
checks. The final five accept runs alone spend **12m27s in Gradle** on the same revision
within one unedited loop. They were deliberate stability evidence. Use a bounded
reproduction/acceptance plan, for example three unchanged acceptance repetitions followed
by a stated escalation threshold, instead of open-ended reruns. Saving two final passes
would save about **5m50s including setup**, but the confidence tradeoff must be accepted.
[H4 inventory](generated/device-runs.csv), [H4 commands](generated/tool-calls.csv),
[accept log](data/logs/sift-w7-h4/0162-accept1.log:94).

H4 lasts **8,632.741 seconds, 2h23m53s**, in its own transcript. The legacy report says
**8,736 seconds, 2h25m36s**, including coordinator turnaround. Its alleged 35m42s orientation
and 41m20s bookkeeping mix C1's interleaved phase rows into H4. H4's own first explicit edit
is about **8m54s** after launch, and its final bookkeeping stamp precedes its return by
**44.66s**. It stamps `implement` at 12:40, after it has already implemented and tested.
Even group IDs cannot repair a late stamp. [Legacy report](data/host/runlog-report.txt:809),
agent `abe5b37dcbf6bed70` in [subagents](generated/subagents.csv),
its four stamp commands in [tool-calls.csv](generated/tool-calls.csv).

The H4 `pgrep -f "run-it.sh 0162-f36"` polling command runs for 2,400 seconds in the
background after a 600-second blocking wait. The actual three-test command finishes in
492.86 seconds. The polling pattern appears in its own command line, so self-matching
is a plausible explanation, **unverified without the old process state**. Do not count
40 minutes as lost wave time: the agent continues other work. About 107 seconds of the
initial blocking wait follows the test task's completion. Use task-ID completion events.
Evidence is calls `toolu_01UswtLqVo4YeSxC2LXi3PuD` and
`toolu_01XBMb9yZzyVjpQKkTu4oLaN`, the command containing `0162-f36-$i` in
[tool-calls.csv](generated/tool-calls.csv). The latter starts 12:04:39.404 and its notification
ends 12:12:52.264. The former starts 12:04:43.583 and first returns after 600.141 seconds.

Coverage's [22-line waiver list](data/logs/sift-drain.7oM4Ky/gate-w7/waived.txt) explicitly
asks it to adopt worker scratch tests for history, preferences, catalog CRUD, reports and
calendar review fixes. The worker contract explicitly orders new tests deferred. This
proves duplicate authoring/transfer work exists, not that all of it is waste. Keeping the
working tests avoids context reload and preserves the exact acceptance evidence. A
controlled next-wave comparison should track disposable tests promoted unchanged, minutes
spent adapting them, and test bugs found by independent coverage. Keep an independent
gap review; it need not write every ordinary feature test.

The coordinator repeats post-E2 build/check for **78.27 seconds**, post-D1 compile/JVM
checks for **67.75 seconds**, and N1 integration compilation for **47.95 seconds**. The
first two integrated other work; N1 found a real removed-resource compile failure. These
are not demonstrated waste. Apply receipts to matching inputs, not a blanket ban on
integration checks. [Tool calls](generated/tool-calls.csv), [wave event log](data/logs/sift-drain.7oM4Ky/wave7-plan.txt).

## Parallelism, losses and autonomy

The emulator lock is the measured queueing constraint. Individual waits reach **835s**
for T2c and **808s** for T2b. More device-heavy sittings would increase contention.
**Two active sittings, preferably one device-heavy and one JVM/docs sitting, is the best
supported starting policy**, not a measured optimum. A third device lane is unsupported
by the evidence. Compare wave makespan and lock minutes per completed ticket before
changing it. Memory peaks in H4 reach **3.66 GiB** of qemu high-water memory, while some
recorded available-memory samples are over 11 GB. Those samples cannot prove the whole
machine, two drains and full swap were safe at every instant. There is no continuous
host-memory trace or one/two/three-lane counterfactual. Current throughput being memory
capped is **unverified**. [Wait/peak logs](generated/logs.csv), [run plan](data/logs/sift-drain.7oM4Ky/wave7-plan.txt).

Only the current boot's user journal was available, starting 09:22 local time. The saved
journal shows one generic app.slice OOM event at 10:13 local time; it does not identify
this drain as the victim. The saved kernel query has no entries. The wave 6 event log
reports a 00:37 restart and 00:39 resumption, but independent historical journal evidence
is unavailable. **Session-loss cost is not measurable from this snapshot.** Do not treat
the earlier sessions' gaps or all time before TKA-0132 as OOM/restart losses. Retained
transcript spans are in [coordinators.csv](generated/coordinators.csv), with the available
[user](data/host/user-events.log) and [kernel](data/host/kernel-oom.log) query output.
The available boot range is saved in [journal availability](data/host/journal-availability.txt).

Autonomous resumption needs a durable checkpoint after every return containing the wave,
group/agent IDs, base, owned paths, integrated commits, verification receipts and next gate
step. Store it and logs on disk, outside RAM-backed `/tmp`. Await task IDs instead of
spawning log tails or process-name watchers. Allow ordinary choices within ticket Direction;
ask the owner only for scope decisions. These changes reduce re-discovery after interruption;
the saving is currently **unverified**. Do not change maintenance services during a drain.

## Accept, defer or reject the proposed candidates

| Candidate | Decision |
| --- | --- |
| Targeted sitting tests; one full suite per final code/environment identity | Accept. Runner changes and genuine full reproductions are explicit exceptions. Patch 01/02. |
| Retain tests with their feature; coverage deferral by exception | Accept for trial. Preserve independent gap review. Patch 01/02. |
| Lean contract and checked context digest | Accept for trial; never replace required instructions or ticket reads with a summary. Patch 01. |
| Group IDs, mandatory truthful phase stamps, locked append and explicit incomplete reports | Accept. Patch 04; historical interleaving cannot be repaired reliably. |
| Stage timers plus opt-in Gradle profiling | Accept. Patch 03; report combined Gradle/device time honestly. |
| Keep one emulator/server alive through all coding in a sitting | Reject as the default. It holds scarce memory/lock time while the agent edits; older API memory growth also required fresh boots. Batch adjacent checks through the existing filter support instead. A bounded multi-filter session could be measured later. |
| Skip APK builds when nothing changed | Reject task exclusions for now. Gradle already skips unchanged tasks, and dirty tests/resources can invalidate the APK. Measure profiling before introducing a second artifact-validity system. |
| Retained Gradle daemon or configuration cache | Defer. No startup/configuration breakdown or safe shared-host comparison exists. Keep the no-stop rule and memory limits. |
| More than two concurrent sittings | Defer. The observed emulator queue is already 218 worker minutes; measure a mixed-lane trial first. |

## Verification and remaining uncertainty

The user-approved durable practice is captured through `kk-add` as
[`practice-reuse-drain-checks-by-content-and-test-configuration`](../../.ai/kenkeep/nodes/practice-reuse-drain-checks-by-content-and-test-configuration.md).
It records the content/configuration condition for reusing verification. The field draft
is retained in [proposals/kenkeep-note.json](proposals/kenkeep-note.json).
The regenerated knowledge index contains 41 nodes; [kenkeep lint](generated/kenkeep-lint.txt)
reports zero findings in every category.

The offline analysis and proposal fixture checks are the only executed tests for this
change. The verification log records dataset reconstruction, timing/parallel-attribution
invariants, sequential patch application in a disposable directory, shell/Python syntax,
and content checks. No emulator, Gradle build, container, service action, push, or live
tracker edit was performed. No proposed speedup was benchmarked. See
[verification log](generated/verification.txt) for exact commands and results.

Future reports need producer-generated run/group IDs and timestamped begin/end events for
orientation, implementation, each Gradle/profile task, lock wait, boot, server setup,
test execution, teardown, report receipt and next dispatch. Record source/test/environment
fingerprints and a durable evidence path with exit codes and expected/actual counts.
Emit phase transitions automatically where possible; fail the telemetry audit when required
stamps are missing or late. Save scheduler/restart/OOM events with the checkpoint rather
than expecting a later boot's journal to reconstruct them.
