# Drain performance analysis

This directory is a read-only investigation of the Android drain, plus candidate patches.
The patches have not been applied to the skill or device runner. The checkout starts at
`51e21d5` on `perf/drain-efficiency`. See [REPORT.md](REPORT.md) for results and limits.

## Rebuild the committed dataset

Python 3.10 or later and its standard library are sufficient. No Gradle, emulator, container,
network, Claude installation or original `/tmp` directory is needed.

```sh
python3 tools/drain-perf/rebuild.py
python3 tools/drain-perf/analyze.py
python3 -m unittest discover -s tools/drain-perf -p test_analysis.py -v
```

`python3 tools/drain-perf/verify.py` runs the pinned audit, a second reconstruction,
sequential patch application and isolated logger CLI checks. Its transcript is
`generated/verification.txt`. The candidate logger CLI uses Linux's boot ID and `flock`.

`generated/tool-calls.csv` has one row per unique tool-use ID in each transcript, including
unpaired calls. `duration_s` pairs the tool-use timestamp with its tool-result timestamp.
For a background shell command this measures the launch acknowledgement, often 0.05 s.
`process_duration_s` instead uses the producer's task-completion notification when one is
available. It is a command interval, which can include several builds or tests. Missing
completion is blank, never zero. Completion delivery can lag process exit.

Other outputs:

| File | Meaning |
| --- | --- |
| `subagents.csv` | Every snapshotted child transcript, model, description, wall span, orientation proxy, phases and exclusive activity partition |
| `coordinators.csv` | The four parent transcript spans, kept separate from child cost |
| `executions.csv` | Recognized build, device and review commands, with loop/compound-command caveats |
| `messages.csv` | Sanitized text and task notifications, without thinking or signatures |
| `logs.csv` | Build durations, lock waits, test evidence and memory peaks in saved logs |
| `device-runs.csv` | Observed local device invocation logs; batching is one invocation with several boots/builds |
| `turnaround.csv` | Observed worker reports to the next new or retained-worker assignment; a gap need not be idle |
| `runlog.csv` | Legacy run-log events with source lines; not used as the timing authority |
| `summary.json`, `analysis.json` | Machine-readable aggregates |

Times are seconds and UTC. Activity intervals overlap across agents. Never add parent
and child durations, add two parallel agents to get wave elapsed time, or add a tool's
RPC duration to its background-process duration. The per-agent partition uses interval
union with device/build work ahead of overlapping wait calls. Its command recognizer is
conservative, so unmatched shell wrappers remain in `wait`, `read`, or `other`.
`model_or_unobserved` includes coding, reasoning, source reading and possible inactivity.
It is not a measurement of pure coding time.

`first_edit_delay_s` ends at the first explicit Write/Edit tool. Bash can edit files before
that point, and bug baselines can precede it. Treat this as an orientation upper bound.
Pre-edit output counts are original character/line counts returned by tools, not unique
file bytes or proof the model read each character. The original prompt size is separate.

Device log matches require a recognized execution referencing the literal path or label.
When one command matches several logs its total duration is not assigned to each run.
Blank durations and `suite_scope=unverified` are intentional. A filtered near-full run is
not counted as an unfiltered full run. Downloaded CI logs are excluded from local runs.

## Capture a future drain

Run the capture from a separate analysis checkout. Its output directory must be on durable
disk. Do not write into a running drain's worktrees, tracker or temporary state.

```sh
python3 tools/drain-perf/snapshot.py \
  --repo /absolute/path/to/live/repo \
  --transcripts /absolute/path/to/claude/project-transcripts \
  --sessions SESSION_ID [EARLIER_SESSION_ID ...] \
  --state /tmp/sift-drain.NEW \
  --worker-glob 'sift-w8-*' --worker-glob 'sift-w9-*' \
  --output /absolute/path/to/analysis-checkout/tools/drain-perf/data
```

The current `/tmp/sift-drain.7oM4Ky` and `sift-w6-*`/`sift-w7-*` discovery defaults are specific
to this investigation. Supply the future run's state path and worker globs as above.
The journal query starts at the earliest captured transcript date unless `--since` is set.
Summarize that run with `rebuild.py --waves 8 9` and `analyze.py --waves 8 9`, substituting
its actual waves. All captured calls/agents remain in the CSVs regardless of that filter.
For separate data directories, pass `rebuild.py --data NEW_DATA --output NEW_GENERATED`
and `analyze.py --data NEW_DATA --generated NEW_GENERATED`, plus the wave arguments.
`verify.py` pins counts and case studies from this investigation; use the unit checks
and adjust those snapshot assertions when auditing a different run.

The snapshot is a projection, not an unredacted transcript backup. It preserves source
paths, original line numbers and SHA-256 hashes, tool inputs with suspect lines omitted,
original output sizes, and selected timing/test output lines. It omits thinking, images,
attachments, raw device logcats, arbitrary tool-result bodies, and lines mentioning
credential values. Safe JUnit suite/case attributes are saved without properties, output,
failure messages or fixture bodies. `.env` is never opened. Inspect the projection before
sharing; redaction is conservative and is not a general secret detector.

`data/manifest.json` records per-file source hashes and capture commands. A live append
can finish during capture, so this is a set of per-file snapshots, not an atomic system
snapshot. A partial final JSONL line is ignored. Do not overwrite this investigation's
snapshot when collecting a new run; use a new branch or data directory.

The small supplemental audits include `data/tree-evidence.json`, made with `git ls-tree`
and `git rev-parse`, and `data/close-file-times.json`, made with `stat` and numeric `.rc`
files after the close finished. Neither is needed to pair transcripts. Filesystem birth
times have one-second precision in that audit; mtimes retain nanoseconds. `data/baselines/`
holds the five versioned source files used to generate patches.
`data/scope-evidence.json` enumerates the initial and final wave 7 ticket sets and the
eight wholly added sitting spans, with the extraction method recorded in that file.

An evidence reference such as `transcripts/SESSION/agent-ID.jsonl.gz:42` means line 42
after decompression, not line 42 of the original transcript. Each projected row also
records its original source line. Show a bounded excerpt with:

```sh
python3 tools/drain-perf/show.py 'transcripts/SESSION/agent-ID.jsonl.gz:42'
```

## Review the proposed changes

```sh
python3 tools/drain-perf/propose.py
```

This regenerates four patches from the frozen baselines. It never edits the installed
skill or runner. Review them together in numeric order in an expendable checkout. Patches
02 and 04 both change different sections of the skill entry point. The candidate logger
implementation under `proposals/` is executable for isolated fixture checks only.

The worker patch adds group/context assignment fields. The coordinator must populate
them when landing that patch. Keep upstream contract pins in sync and run the skill's
own tests in its source repository before adopting it. These proposals have Python and
shell syntax/fixture verification; no Android execution was attempted. The timing patch
reports combined Gradle/device time and supports opt-in Gradle `--profile`; it does not
pretend that a shell timer separates APK work from the instrumentation task.

No daemon/configuration-cache toggle, APK task exclusion, or persistent-emulator mode is
included. The report explains why those need a controlled experiment first.

`make_dashboard.py` regenerates the optional local `dashboard.html` from `analysis.json`.
The T3 headless preview was unavailable because of the host's AppArmor browser sandbox.
No host setup or service was changed for it.
