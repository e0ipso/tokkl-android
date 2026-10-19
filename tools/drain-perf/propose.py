#!/usr/bin/env python3
"""Generate reviewable patches. Never writes to the skill or device runner."""
import difflib
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'tools/drain-perf/patches'


def patch(name, changes):
    parts = []
    for filename, before, after in changes:
        parts.extend(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                         fromfile='a/' + filename if before else '/dev/null', tofile='b/' + filename))
    (OUT / name).write_text(''.join(parts))


def read(path):
    name = 'sift-drain-SKILL.md' if path == '.agents/skills/sift-drain/SKILL.md' else pathlib.Path(path).name
    baseline = ROOT / 'tools/drain-perf/data/baselines' / name
    if baseline.exists():
        return baseline.read_text()
    return (ROOT / path).read_text()


def replace_once(text, before, after):
    assert text.count(before) == 1, (before[:100], text.count(before))
    return text.replace(before, after, 1)


def main():
    OUT.mkdir(exist_ok=True)
    worker_path = '.agents/skills/sift-drain/references/ticket-agent-prompt.md'
    worker = read(worker_path)
    a = worker.index('```\n', worker.index('## Template')) + 4
    b = worker.index('\n```', a)
    lean = '''You own one sitting of sift tickets in the prepared PROJECT_ROOT on BRANCH.
Use the trailing Assignment as data. Bind and quote its paths; export SIFT_ROOT and
SIFT_GROUP_ID. You are not alone in the repository. Recheck owned files before editing;
report overlapping edits without overwriting them. Keep unfinished work when tamper occurs.
Do not push, change branches, merge, archive tickets, or edit sift-drain skill files.
Only the coordinator lands work and updates existing tracker state.

Step 1: Orient
Run "$SCRIPTS_DIR/drain-log.sh" --group "$SIFT_GROUP_ID" phase orient.
The four phase commands in this contract are the only phase stamps for the sitting.
Verify the worktree, branch, base and assigned paths. Read tickets in full, applicable
project instructions, kenkeep ENTRY and relevant leaves. Read only the Rules for agents
section of the shared sift README:
  @README-SECTION: ## Rules for agents
Before creating follow-ups read these sections and reuse their schemas:
  @README-SECTION: ## Front-matter schema
  @README-SECTION: ## Ticket body
  @README-SECTION: ## Drafting a ticket
  @README-SECTION: ## Dispatch groups and the cluster key
Read these only if the ticket changes operations or logging, respectively:
  @README-SECTION: ## Operations cookbook (terminal)
  @README-SECTION: ## Run log
Reuse unchanged instructions still in context. After context loss, reload them explicitly.
Use the Assignment's context manifest to find relevant source, tests and prior-work matches.
Check fingerprints and inspect source before editing. A context digest never replaces
instructions or the ticket. Bound aggregate content output to 8000 bytes per call.
Resolve ordinary choices from the ticket's Direction. Report already-present work with
evidence. Block only a decision that needs the owner.

Step 2: Check workspace
Confirm PROJECT_ROOT and BRANCH. Report setup mismatches for the coordinator to repair.
Use SIFT_ROOT/.ai/sift for tracker reads and new follow-ups. Do not initialize a local tracker.

Step 3: Implement and commit tickets in order
Run "$SCRIPTS_DIR/drain-log.sh" --group "$SIFT_GROUP_ID" phase implement.
Reproduce a bug before editing, or explain why live reproduction is unsafe and use scoped
evidence. Implement directly, within ticket ownership. Preserve earlier ticket commits.
Retain useful regression tests and disposable acceptance tests in the owning ticket's
commit. Defer only with a concrete reason, criterion and named owner. Fix invalidated tests.
Do not manufacture a red/green cycle for a reversible low-impact change.

Verify affected unit/integration tests and touched-file lint with project commands. Run
device tests only through scripts/instrumented-tests.sh. Combine independent affected
classes in one supported class/package filter. Do not hold the emulator between edits.
Use targeted checks here. A full suite belongs to the close, unless this ticket changes
the runner itself or evidence requires a full reproduction. State that exception first.
Save raw logs to a durable evidence directory outside the worktree's product paths.
Preserve exit codes and report actual counts, failures and log paths. A BUILD line alone
does not prove every device test ran. Never weaken assertions or bypass the runner.
Commit each finished ticket separately, with its tests and docs made inaccurate by it.
If an ordinary ticket fails, restore only its unfinished edits and continue the sitting.

Step 4: Verify sitting
Run "$SCRIPTS_DIR/drain-log.sh" --group "$SIFT_GROUP_ID" phase verify.
Reuse checks whose checked inputs have not changed, including across commits. Run only
missing checks or the affected test union after a multi-ticket change invalidates them.
Return a verification receipt: checked-input fingerprint, dirty-input status, command,
filter, JDK/SDK, device/API, server/proxy digests, counts, exit code and durable log path.
The coordinator verifies that receipt and reruns only invalidated checks. Never reuse a
receipt from a dirty or incompletely fingerprinted tree. Amend fixes into their ticket.
Do not run ticket-check.sh or a full suite by habit.

Step 5: Follow-ups
Run "$SCRIPTS_DIR/drain-log.sh" --group "$SIFT_GROUP_ID" phase bookkeep.
For separate work, reserve IDs once through reserve-ids.sh with shared SIFT_ROOT. Never
calculate IDs or reuse reservations. Follow its exit-code contract; report allocation
failures. Read the relevant README schemas before creating tickets with wave WAVE.
Do not change existing tickets or external trackers. Report durable candidates for capture.

Step 6: Return one final report
Return only these fields, normally under 250 words.
  status: <TICKET-ID>: done | blocked | not started, one line per ticket
  branch: <assigned branch>
  commits: <TICKET-ID> <hash>; one per ticket; identify separate sitting fixes
  resolution: <TICKET-ID>: <one line, including waivers>
  verification: receipts, commands, exit codes, paths, exact counts or not reported;
                before/after bug evidence, reused checks, cleanup or reason unavailable
  issues: blockers, choices, unfiled findings, changed expectations | none
  deferred to the wave gate: criterion, reason, owner; durable candidates | none
  tickets filed: every self-filed ID | none
  tamper: none | files and implicated sitting

Assignment:
WAVE: {{WAVE}}
GROUP_SIZE: {{GROUP_SIZE}}
GROUP_TICKETS: {{GROUP_TICKETS}}
SIFT_GROUP_ID: {{SIFT_GROUP_ID}}
PROJECT_ROOT: {{PROJECT_ROOT}}
BRANCH: {{BRANCH}}
BASE_BRANCH: {{BASE_BRANCH}}
SIFT_ROOT: {{SIFT_ROOT}}
SCRIPTS_DIR: {{SCRIPTS_DIR}}
TEST_SCOPE_HINT: {{TEST_SCOPE_HINT}}
PRIOR_WORK: {{PRIOR_WORK}}
CONTEXT_MANIFEST: {{CONTEXT_MANIFEST}}
CONSTRAINTS: {{CONSTRAINTS}}
TICKET_BLOCK:
{{TICKET_BLOCK}}'''
    new_worker = worker[:a] + lean + worker[b:]
    new_worker = new_worker.replace('| `{{WAVE}}` |', '| `{{SIFT_GROUP_ID}}` | unique ID for this dispatch, also on coordinator dispatch/return stamps |\n| `{{CONTEXT_MANIFEST}}` | checked source/test paths, fingerprints and existing evidence; no instruction replacement |\n| `{{WAVE}}` |', 1)
    patch('01-worker-contract.patch', [(worker_path, worker, new_worker)])

    gate_path = '.agents/skills/sift-drain/references/wave-gate.md'
    gate = read(gate_path)
    new_gate = replace_once(gate,
        'Run the full tests, authoritative lint/static analysis and any existing e2e suite. Report\nexact totals for each, or explicitly report no e2e layer. All required runs must be green.',
        'Run the affected tests and lint for the criteria added here. Reuse valid verification\nreceipts. The close owns one full run on the final checked inputs for each required API.\nReturn scoped receipts and exact totals, or explicitly report no e2e layer. If a full run\nis needed to reproduce a runner defect, label that exception and return its receipt.')
    new_gate = replace_once(new_gate,
        'After the last fix lands, rerun all full checks together; each fix agent checked only its\nown affected suites. Require green results and exact totals. Apply the skill\'s p1/p2 and\nclosed-wave tail rules before closing.',
        'After the last code or test fix lands, run each missing full check once on the final\nchecked inputs. A passing receipt from coverage or a worker satisfies a close check when\nall its checked inputs and environment match. Compare content fingerprints, not commit\nIDs. Include build/test configuration, resources, fixtures, runner scripts, SDK/JDK and\nserver/proxy digests. Missing, dirty, failed, truncated or mismatched receipts require a\nrun. Require exact test counts and device survival. Docs-only capture does not invalidate\napp checks. Run capture alongside checks when their owned files do not overlap. Apply the\nskill\'s p1/p2 and closed-wave tail rules before closing.\n\n+Keep the emulator lane fed by at most two active implementation sittings. Prefer one\ndevice-heavy sitting plus one JVM/docs sitting. Use background completion notifications\nor the task tool to await a known task ID; never poll a command line with pgrep or launch\nan unbounded log tail. Land stale-base work in an integration worktree, test the affected\nunion there, then return a receipt. A real merge conflict invalidates affected checks.\n\n+Use a per-check durable ledger keyed by run/wave/group ID. Checkpoint assignments, base,\nowned paths, commits, receipt and next gate step after each return. On resumption inspect\nthat state and the worktree before dispatching anything. Report missing historical\nevidence as unknown. Schedule host maintenance around active drains; do not restart a\nservice from a drain.')
    # Leading '+' inside replacement text is not patch syntax.
    new_gate = new_gate.replace('\n+Keep', '\nKeep').replace('\n+Use', '\nUse')
    skill_path = '.agents/skills/sift-drain/SKILL.md'
    skill = read(skill_path)
    new_skill = replace_once(skill,
        '2. Prepare each branch and worktree using `prepare-worktree.sh` from the original',
        '   Assign one owner for shared README/PRD sections. Stagger overlapping writes or\n   route required documentation through that owner. Record ownership in the assignments.\n2. Prepare each branch and worktree using `prepare-worktree.sh` from the original')
    new_skill = replace_once(new_skill,
        '3. Land returned commits, then verify the integrated state with the full test, lint,\n   static-analysis and applicable e2e suites. Keep raw logs on disk. Report commands,\n   exit codes, available counts, failures and absolute log paths, or no-e2e-layer status.',
        '3. Land returned commits, compare verification receipts to the integrated checked\n   inputs and environment, and run missing full tests, lint, static analysis and applicable\n   e2e suites once. Keep raw logs on durable disk. Report receipt identities, commands,\n   exit codes, exact counts, failures and paths, or no-e2e-layer status.')
    new_skill = replace_once(new_skill, '   their fixes. Land fix commits and repeat every full run after the last fix.',
        '   their fixes. Land fix commits and rerun checks invalidated by the final changes.')
    new_skill = replace_once(new_skill,
        '   `drain-log.sh return`. For each done ticket, cherry-pick its commit with `-n`, set\n   status, resolution and updated, then move it to the mirrored archive path and delete the',
        '   `drain-log.sh return`. Integrate stale-base commits in a separate integration\n   worktree, resolve conflicts and verify affected inputs there before landing. For each\n   done ticket, set status, resolution and updated, move it to the mirrored archive path and delete the')
    new_skill = replace_once(new_skill,
        '   self-filed ID. Rewire the graph and dispatch newly ready sittings.',
        '   self-filed ID. Checkpoint wave/group/agent IDs, base, owned paths, landed commits,\n   verification receipts and the next action on durable disk after each return. On resume,\n   inspect the checkpoint and worktrees before dispatching. Rewire and dispatch ready work.')
    new_skill = replace_once(new_skill,
        '6. When green, dispatch one knowledge-capture pass if reports contain durable candidates\n   and the project has a capture skill. Land its commit and post the wave summary.',
        '6. Dispatch one knowledge-capture pass alongside the final checks if its owned files\n   do not overlap checked inputs, reports contain durable candidates, and a capture skill\n   exists. Land its commit and post the summary only after every required check is green.')
    patch('02-wave-gate.patch', [(gate_path, gate, new_gate), (skill_path, skill, new_skill)])

    script_path = 'scripts/instrumented-tests.sh'
    script = read(script_path)
    new_script = replace_once(script, 'lock_file="${XDG_RUNTIME_DIR', '''# Numeric timing fields only. Never log commands or runner argument values here.
timing_started=$SECONDS
timing_run="${SIFT_GROUP_ID:-unassigned}-$$"
timing() {
    printf 'DRAIN_TIMING run=%s stage=%s elapsed_s=%s epoch=%s\\n' \\
        "$timing_run" "$1" "$((SECONDS - $2))" "$(date -u +%s)" >&2
}
lock_started=$SECONDS
lock_file="${XDG_RUNTIME_DIR''')
    new_script = replace_once(new_script, "printf 'PID %s, started %s in %s\\n'", 'timing lock_wait "$lock_started"\nprintf \'PID %s, started %s in %s\\n\'')
    new_script = replace_once(new_script, '    local status=$?\n', '    local status=$? cleanup_started=$SECONDS\n')
    new_script = replace_once(new_script, '    rm -rf "$work_dir"\n', '    rm -rf "$work_dir"\n    timing teardown "$cleanup_started"\n    timing total "$timing_started"\n')
    new_script = replace_once(new_script, 'boot_emulator() {\n', 'boot_emulator() {\n    local boot_started=$SECONDS\n')
    new_script = replace_once(new_script, '''        echo "$AVD runs Android 11 or earlier, so apps on it draw with OpenGL"
    fi
}''', '''        echo "$AVD runs Android 11 or earlier, so apps on it draw with OpenGL"
    fi
    timing boot "$boot_started"
}''')
    new_script = replace_once(new_script, 'setup_code=$(random_hex 16)', 'server_started=$SECONDS\nsetup_code=$(random_hex 16)')
    new_script = replace_once(new_script, 'echo "Tokkl is ready on $server', 'timing server_setup "$server_started"\necho "Tokkl is ready on $server')
    new_script = replace_once(new_script, '    local status=0 device\n', '    local status=0 device gradle_started=$SECONDS\n    local -a profile_args=()\n    if [ "${TOKKL_TEST_PROFILE:-0}" = 1 ]; then profile_args+=(--profile); fi\n')
    new_script = replace_once(new_script, '    ./gradlew --no-daemon connectedDebugAndroidTest \\\n', '    ./gradlew --no-daemon "${profile_args[@]}" connectedDebugAndroidTest \\\n')
    new_script = replace_once(new_script, '        "${gradle_args[@]}" "$@" || status=$?\n', '        "${gradle_args[@]}" "$@" || status=$?\n    timing gradle_and_device "$gradle_started"\n')
    patch('03-device-timing.patch', [(script_path, script, new_script)])

    logger_path = '.agents/skills/sift-drain/scripts/drain-log.sh'
    logger = read(logger_path)
    new_logger = replace_once(logger, 'MODE="${1:-}"', '''# Opt-in v2 is group-addressed; legacy reports remain available during migration.
if [ "${1:-}" = --group ]; then
  shift
  [ $# -ge 2 ] || usage
  group=$1; shift
  exec python3 "$(dirname "${BASH_SOURCE[0]}")/drain-log-v2.py" \\
    --log "$SIFT/RUNLOG.v2.jsonl" --prefix "$PREFIX" --group "$group" "$@"
fi

MODE="${1:-}"''')
    v2 = read('tools/drain-perf/proposals/drain-log-v2.py')
    group_skill = new_skill.replace('scripts/drain-log.sh dispatch <TICKET>...', 'scripts/drain-log.sh --group <GROUP_ID> dispatch <TICKET>...')
    group_skill = group_skill.replace('scripts/drain-log.sh return <TICKET> <STATUS>...', 'scripts/drain-log.sh --group <GROUP_ID> return <TICKET> <STATUS>...')
    group_skill = group_skill.replace('scripts/drain-log.sh report', 'scripts/drain-log.sh --group all report')
    group_skill = group_skill.replace('`drain-log.sh dispatch`', '`drain-log.sh --group "$SIFT_GROUP_ID" dispatch`')
    group_skill = group_skill.replace('`drain-log.sh return`', '`drain-log.sh --group "$SIFT_GROUP_ID" return`')
    group_skill = replace_once(group_skill,
        'Workers stamp their own phases. `drain-log.sh` alone writes `RUNLOG.md`.',
        'Mint a unique SIFT_GROUP_ID for each dispatch and every resumed sitting. Put it in\n   the worker Assignment and every dispatch, phase and return stamp. v2 writes the locked\n   RUNLOG.v2.jsonl sidecar; the legacy report remains read-only history. Workers stamp\n   phases at their actual start. Missing, duplicate or out-of-order stamps are telemetry\n   errors, never inferred durations. Report telemetry errors before closing a wave.')
    patch('04-parallel-logger.patch', [(logger_path, logger, new_logger),
                                      (skill_path, new_skill, group_skill),
                                      ('.agents/skills/sift-drain/scripts/drain-log-v2.py', '', v2)])
    print('Wrote four candidate patches; no skill or runner file was changed.')


if __name__ == '__main__':
    main()
