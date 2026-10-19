#!/usr/bin/env python3
"""Derive conservative cost summaries, device inventory and coordinator gaps."""
import csv
import argparse
import json
import pathlib
import re
import statistics
from collections import Counter, defaultdict
from datetime import datetime

from rebuild import csv_write, epoch, union

ROOT = pathlib.Path(__file__).resolve().parent
DATA = ROOT / 'data'
GENERATED = ROOT / 'generated'


def read(name):
    with (GENERATED / name).open() as f:
        return list(csv.DictReader(f))


def main(waves=(6, 7), data=None, generated=None):
    global DATA, GENERATED
    if data:
        DATA = data.resolve()
    if generated:
        GENERATED = generated.resolve()
    calls, agents, messages = read('tool-calls.csv'), read('subagents.csv'), read('messages.csv')
    logs = read('logs.csv')
    junit = [json.loads(line) for line in (DATA / 'junit.jsonl').read_text().splitlines()]
    junit_files = defaultdict(list)
    for row in junit:
        junit_files[row['source']].append(row)

    # Physical logs with script startup/device task markers, excluding summaries and review output.
    runs = []
    for log in logs:
        path = log['path']
        if not path.endswith('.log') or any(s in path for s in ('review', 'it-results', 'jvm', '/ci-')):
            continue
        text = (DATA / path).read_text()
        if not ('Booting tokkl-api' in text or 'Running tests on devices:' in text):
            continue
        # Relative directory and label locate the command without trusting a reported duration.
        saved = pathlib.Path(path)
        label = saved.stem
        directory = str(saved.parent).removeprefix('logs/')
        original = '/tmp/' + directory + '/' + saved.name
        candidates = [c for c in calls if c['category'] == 'device' and
                      (original in c['command'] or (('/tmp/' + directory) in c['command'] and
                       re.search(r'(?<![\w-])' + re.escape(label) + r'(?![\w-])', c['command'])))]
        unique = candidates[0] if len(candidates) == 1 else None
        classes = re.findall(r'^([\w.$]+Test(?:#[\w]+)?)(?: >|\s*>|\s+\d)', text, re.M)
        # XML copies often use results-/res-/label-results folders. Keep ambiguous matches visible.
        matching_xml = [(p, rows) for p, rows in junit_files.items()
                        if directory in p and label in p and ('androidTest' in p or any(r['suite'].get('name', '').startswith('com.github.e0ipso') for r in rows))]
        # Whole close suite and coverage have stable, unambiguous folder names.
        if 'gate-w7/close/instrumented-api36' in path:
            matching_xml = [(p, rr) for p, rr in junit_files.items() if 'gate-w7/close/it-results-api36/TEST' in p]
        if 'sift-w7-coverage/full-api36.log' in path:
            matching_xml = [(p, rr) for p, rr in junit_files.items() if 'sift-w7-coverage/full-api36-results-xml/TEST' in p]
        jr = [r for _, rr in matching_xml for r in rr]
        gradle_s = float(log['build_s'])
        test_s = sum(float(r['suite'].get('time', 0)) for r in jr) if len(matching_xml) == 1 else None
        full = 'batched_full' if 'The runner listed' in text else 'unverified'
        scope = ''
        if unique:
            scope_match = re.findall(r'InstrumentationRunnerArguments\.(class|package|notClass)=([^\s;]+)', unique['command'])
            scope = ';'.join(k + '=' + v for k, v in scope_match)
            if scope_match:
                full = 'filtered'
            elif re.search(r'instrumented-tests.sh(?:\s+--[a-z-]+)*\s*>|run-suite.sh\s+\S+\s+(?:tokkl-api\d+|-)\s*;', unique['command']):
                full = 'full'
        if 'full-api36.log' in path or ('gate-w' in path and '/close/instrumented' in path):
            full = 'batched_full' if 'The runner listed' in text else 'full'
        runtime = float(unique['process_duration_s']) if unique and unique['process_duration_s'] else None
        runs.append(dict(log=path, wave=log['wave'], directory=directory, label=label, scope=scope,
                         suite_scope=full, gradle_builds=log['build_count'], gradle_s=gradle_s,
                         lock_wait_s=float(log['lock_wait_s']),
                         boots=text.count('Booting tokkl-api'),
                         batch_count=len(re.findall(r'^Batch \d+ of \d+:', text, re.M)),
                         tests=sum(int(r['suite'].get('tests', 0)) for r in jr) if len(matching_xml) == 1 else '',
                         junit_test_s=round(test_s, 3) if test_s is not None else '',
                         gradle_minus_junit_s=round(gradle_s - test_s, 3) if test_s is not None else '',
                         process_s=runtime if runtime is not None else '',
                         outer_minus_gradle_lock_s=round(runtime - gradle_s - float(log['lock_wait_s']), 3) if runtime is not None else '',
                         matched_calls=len(candidates), call_id=unique['call_id'] if unique else '',
                         evidence=unique['evidence'] if unique else '', junit_sources=';'.join(p for p, _ in matching_xml)))
    # A single command can run multiple logged invocations. Its whole duration cannot
    # be assigned to each log. Keep the match but omit per-run wall arithmetic.
    matches = Counter(r['call_id'] for r in runs if r['call_id'])
    for row in runs:
        if row['call_id'] and matches[row['call_id']] > 1:
            row['process_s'] = row['outer_minus_gradle_lock_s'] = ''
        if row['outer_minus_gradle_lock_s'] != '' and row['outer_minus_gradle_lock_s'] < 0:
            row['process_s'] = row['outer_minus_gradle_lock_s'] = ''
    csv_write(GENERATED / 'device-runs.csv', runs)

    # Worker-return -> first later Agent dispatch. This is an observed gap, not necessarily idle.
    dispatches = sorted([c for c in calls if c['stage'] == 'coordinator' and
                         (c['tool'] == 'Agent' or (c['tool'] == 'SendMessage' and 'Assignment:' in c['input_json']))], key=lambda c: c['start'])
    gaps = []
    for a in agents:
        if a['wave'] not in {str(w) for w in waves}:
            continue
        reports = [m for m in messages if m['agent'] == a['agent'] and m['session'] == a['session']
                   and m['role'] == 'assistant' and re.match(r'\s*(?:\*\*)?status:', m['text'], re.I)]
        # Only returns with observed report text. An arbitrary last message is not a return.
        for i, report in enumerate(reports):
            t = epoch(report['timestamp'])
            next_dispatch = next((c for c in dispatches if epoch(c['start']) >= t), None)
            if next_dispatch:
                gaps.append(dict(agent=a['agent'], description=a['description'], wave=a['wave'], report=i + 1,
                                 returned=report['timestamp'], next_dispatch=next_dispatch['start'],
                                 gap_s=round(epoch(next_dispatch['start']) - t, 3),
                                 next_description=json.loads(next_dispatch['input_json']).get('description', 'retained-worker assignment'),
                                 report_evidence=report['evidence'], dispatch_evidence=next_dispatch['evidence']))
    csv_write(GENERATED / 'turnaround.csv', gaps)

    summary = {}
    for wave in map(str, waves):
        aa = [a for a in agents if a['wave'] == wave]
        rr = [r for r in runs if r['wave'] == wave]
        spans = union([(float(a['start_epoch']), float(a['end_epoch'])) for a in aa])
        partition = Counter()
        for a in aa:
            partition.update(json.loads(a['partition_json']))
        orient = [float(a['first_edit_delay_s']) for a in aa if a['stage'] == 'sitting' and a['first_edit_delay_s']]
        agent_intervals = [(float(a['start_epoch']), float(a['end_epoch'])) for a in aa if a['stage'] == 'sitting']
        device_intervals = [(epoch(c['start']), float(c['process_end_epoch'])) for c in calls
                            if c['wave'] == wave and c['category'] == 'device' and c['process_end_epoch']]
        summary[wave] = dict(agent_count=len(aa), agent_wall_s=round(sum(float(a['duration_s']) for a in aa), 3),
                             worker_presence_union_s=round(sum(b - a for a, b in spans), 3),
                             first_dispatch=min((a['start'] for a in aa), default=''), last_agent_end=max((a['end'] for a in aa), default=''),
                             partition_s={k: round(v, 3) for k, v in partition.items()},
                             pre_first_edit_s=round(sum(orient), 3), median_first_edit_s=round(statistics.median(orient), 3) if orient else None,
                             pre_first_edit_output_chars=sum(int(a['pre_edit_output_chars']) for a in aa if a['stage'] == 'sitting'),
                             observed_device_log_count=len(rr), boots=sum(r['boots'] for r in rr),
                             lock_wait_s=sum(r['lock_wait_s'] for r in rr),
                             known_full_scope_logs=[r['log'] for r in rr if 'full' in r['suite_scope']],
                             device_command_union_s=round(sum(b - a for a, b in union(device_intervals)), 3),
                             device_logs_by_sitting=dict(Counter(r['directory'] for r in rr)))
    (GENERATED / 'analysis.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--waves', nargs='+', type=int, default=[6, 7])
    parser.add_argument('--data', type=pathlib.Path)
    parser.add_argument('--generated', type=pathlib.Path)
    args = parser.parse_args()
    main(args.waves, args.data, args.generated)
