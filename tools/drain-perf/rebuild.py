#!/usr/bin/env python3
"""Rebuild drain timing CSVs from snapshot.py's immutable evidence projection."""
import argparse
import csv
import gzip
import json
import pathlib
import re
import statistics
from collections import Counter, defaultdict
from datetime import datetime, timezone


def epoch(s):
    return datetime.fromisoformat(s.replace('Z', '+00:00')).timestamp()


def seconds(s):
    return sum(float(n) * {'h': 3600, 'm': 60, 's': 1}[u]
               for n, u in re.findall(r'(\d+(?:\.\d+)?)\s*([hms])', s))


def csv_write(path, rows, fields=None):
    fields = fields or list(dict.fromkeys(k for r in rows for k in r))
    with path.open('w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


def union(intervals):
    merged = []
    for a, b in sorted((a, b) for a, b in intervals if b >= a):
        if merged and a <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(b, merged[-1][1]))
        else:
            merged.append((a, b))
    return merged


def execution(cmd):
    """Conservative command recognition. Do not count mentions inside reads or heredocs."""
    kept, delimiter = [], None
    for line in cmd.splitlines():
        if delimiter:
            if line.strip() == delimiter:
                delimiter = None
            continue
        m = re.search(r'<<-?\s*[\'"\\]?(\w+)[\'" ]?', line)
        if m:
            kept.append(line[:m.start()])
            delimiter = m[1]
        else:
            kept.append(line)
    prefix = '\n'.join(kept)
    if 'COMMAND=\'codex exec ' in prefix and 'sh -c "$COMMAND"' in prefix:
        return 'review', prefix
    lead = r'(?:^|&&|;|\n|\(|\|\|)\s*(?:(?:do|then)\s+)?(?:timeout\s+\d+\s+)?(?:[A-Z_]+=\S+\s+)*(?:bash\s+)?'
    for kind, pattern in [
        ('device', lead + r'(?:\./)?scripts/instrumented-tests\.sh(?=\s|$)'),
        ('device', lead + r'/tmp/sift-\S*/(?:run-it\d*|run-suite|runseq|load-run|run-class(?:es)?|(?:int-)?run-chain|run)\.sh(?=\s|$)'),
        ('gradle', lead + r'(?:\./|\S*/)?gradlew(?=\s|$)'),
        ('review', lead + r'codex\s+exec\b(?![^\n]*--help)'),
    ]:
        m = re.search(pattern, prefix)
        if m:
            return kind, prefix[m.start():]
    for variable in re.finditer(r'\b([A-Z]+)=/tmp/sift-\S*/run-class\.sh\s*;', prefix):
        if re.search(r'\$' + variable[1] + r'\s+\S+', prefix):
            return 'device', prefix
    return '', ''


def category(name, inp):
    if name in ('Write', 'Edit', 'MultiEdit', 'NotebookEdit'):
        return 'edit'
    if name in ('Read', 'Grep', 'Glob', 'LS'):
        return 'read'
    if name == 'Agent':
        return 'dispatch'
    if name in ('TaskOutput', 'Monitor'):
        return 'wait'
    if name in ('Bash', 'Shell'):
        cmd = inp.get('command', '')
        kind, _ = execution(cmd)
        if kind:
            return kind
        if re.search(r'\b(until|sleep|wait)\b', cmd):
            return 'wait'
        if re.search(r'\bgit\s+(?:-C\s+\S+\s+)?(?:commit|cherry-pick|merge|rebase|worktree|add|status|diff|log)\b|drain-log.sh|ticket-(?:check|archive)', cmd):
            return 'coordination'
        if re.search(r'(?:^|&&|;|\n)\s*(?:cat|sed|head|tail|wc|rg|grep|find|ls)\b|read-context.sh', cmd):
            return 'read'
        if re.search(r'(?:^|&&|;|\n)\s*(?:python\S*|node|perl|sed -i|cat\s*>|cp)\b', cmd):
            return 'code_or_inspect'
        if re.search(r'\bnpm\s+(ci|install|test)|shellcheck|bash -n', cmd):
            return 'other_verification'
    return 'other'


def parse_transcripts(data):
    agents, calls, messages = [], [], []
    for src in sorted((data / 'transcripts').rglob('*.jsonl.gz')):
        with gzip.open(src, 'rt') as f:
            rows = [json.loads(line) for line in f]
        meta = next((r['data'] for r in rows if r.get('kind') == 'meta'), {})
        records = [r for r in rows if r.get('timestamp')]
        if not records:
            continue
        sid = src.parent.name
        aid = src.name.split('.jsonl')[0]
        coordinator = not aid.startswith('agent-')
        description = meta.get('description', 'coordinator' if coordinator else '')
        wm = re.search(r'(?i)(?:\bW|Wave\s+)(\d+)', description)
        wave = int(wm[1]) if wm else ''
        stage = 'coordinator' if coordinator else ('coverage' if 'coverage' in description.lower() else
                 'capture' if 'capture' in description.lower() else 'sitting' if wave else 'other')
        uses, results = {}, {}
        notifications = {}
        for r in records:
            if r.get('role') == 'notification':
                for b in r.get('blocks', []):
                    m = re.search(r'<tool-use-id>([^<]+)', b.get('text', ''))
                    if m:
                        notifications.setdefault(m[1], r)
        for lineno, r in enumerate(rows, 1):
            if not r.get('timestamp'):
                continue
            ts = epoch(r['timestamp'])
            for b in r.get('blocks', []):
                if b['type'] == 'tool_use':
                    uses.setdefault(b['id'], (r, b, lineno))
                elif b['type'] == 'tool_result':
                    results.setdefault(b['id'], (r, b, lineno))
                elif b['type'] == 'text':
                    messages.append(dict(session=sid, agent=aid, timestamp=r['timestamp'], epoch=ts,
                                         role=r.get('role'), text=b['text'], chars=b.get('chars', 0),
                                         evidence=f'{src.relative_to(data)}:{lineno}', source_line=r['source_line']))
        own = []
        for uid, (r, b, lineno) in uses.items():
            result = results.get(uid)
            end = epoch(result[0]['timestamp']) if result else None
            start = epoch(r['timestamp'])
            inp = b.get('input', {})
            evidence = '\n'.join(x['text'] for x in result[1].get('evidence', [])) if result else ''
            asynchronous = bool(re.search(r'Command running in background with ID:|moved to the background', evidence))
            notification = notifications.get(uid)
            process_end = epoch(notification['timestamp']) if notification else (end if not asynchronous else None)
            row = dict(session=sid, agent=aid, description=description, wave=wave, stage=stage,
                       call_id=uid, tool=b['name'], category=category(b['name'], inp),
                       start=r['timestamp'], end=result[0]['timestamp'] if result else '',
                       duration_s=round(end - start, 3) if end is not None else '',
                       start_epoch=start, end_epoch=end if end is not None else '',
                       asynchronous=asynchronous,
                       process_end_epoch=process_end if process_end is not None else '',
                       process_duration_s=round(process_end - start, 3) if process_end is not None else '',
                       completion_source='task_notification' if notification else 'tool_result' if not asynchronous else 'unobserved',
                       command=inp.get('command', ''), path=inp.get('file_path', inp.get('path', '')),
                       input_chars=len(json.dumps(inp)), output_chars=result[1].get('chars', 0) if result else '',
                       output_lines=result[1].get('lines', 0) if result else '',
                       error=result[1].get('is_error', False) if result else '',
                       input_json=json.dumps(inp, ensure_ascii=False), result_evidence=evidence,
                       evidence=f'{src.relative_to(data)}:{lineno}',
                       result_evidence_ref=f'{src.relative_to(data)}:{result[2]}' if result else '',
                       original_source=r['source'], original_line=r['source_line'])
            own.append(row)
        own.sort(key=lambda r: r['start_epoch'])
        start, end = min(epoch(r['timestamp']) for r in records), max(epoch(r['timestamp']) for r in records)
        first_edit = next((r['start_epoch'] for r in own if r['category'] == 'edit'), None)
        # Bash edits before the first Write/Edit are ambiguous; report this bound explicitly.
        before = [r for r in own if first_edit is not None and r['start_epoch'] < first_edit]
        for call in own:
            call['before_first_explicit_edit'] = first_edit is not None and call['start_epoch'] < first_edit
            inp = call['command'] + ' ' + call['path']
            call['context_tags'] = '|'.join(label for label, pattern in (
                ('kenkeep', r'\.ai/kenkeep|kenkeep/ENTRY'),
                ('tickets', r'\.ai/sift/(?:open|archive)|TKA-\d+--'),
                ('contract', r'ticket-agent-prompt|template\.txt'),
                ('instructions', r'AGENTS\.md|CLAUDE\.md|SKILL\.md'),
                ('tracker_spec', r'\.ai/sift/README'),
            ) if re.search(pattern, inp))
        prompt = next((r for r in messages if r['session'] == sid and r['agent'] == aid and r['role'] == 'user'), {})
        stamps = []
        for c in own:
            for m in re.finditer(r'drain-log\.sh[\'" ]+(?:--group\s+\S+\s+)?phase\s+(orient|implement|verify|bookkeep)', c['command']):
                stamps.append((c['start_epoch'], m[1]))
        phases = defaultdict(float)
        for i, (ts, phase) in enumerate(stamps):
            stop = stamps[i + 1][0] if i + 1 < len(stamps) else end
            phases[phase] += max(0, stop - ts)
        # Exclusive agent-wall partition: union each tool interval then fill model/unobserved time.
        boundaries = sorted({start, end} | {max(start, min(end, t)) for c in own for t in (c['start_epoch'], c['process_end_epoch']) if t != ''})
        partition = defaultdict(float)
        priority = {'device': 9, 'gradle': 8, 'review': 7, 'wait': 6, 'edit': 5, 'read': 4,
                    'coordination': 3, 'code_or_inspect': 2, 'other_verification': 2, 'dispatch': 1, 'other': 0}
        for a, b in zip(boundaries, boundaries[1:]):
            active = [c['category'] for c in own if c['process_end_epoch'] != '' and c['start_epoch'] <= a and c['process_end_epoch'] >= b]
            cat = max(active, key=lambda c: priority.get(c, 0)) if active else 'model_or_unobserved'
            partition[cat] += b - a
        agents.append(dict(session=sid, agent=aid, description=description, wave=wave, stage=stage,
                           model=next((r.get('model') for r in records if r.get('model')), meta.get('model', '')),
                           start=datetime.fromtimestamp(start, timezone.utc).isoformat(),
                           end=datetime.fromtimestamp(end, timezone.utc).isoformat(),
                           start_epoch=start, end_epoch=end, duration_s=round(end - start, 3),
                           first_edit_delay_s=round(first_edit - start, 3) if first_edit else '',
                           pre_edit_calls=len(before), pre_edit_output_chars=sum(c['output_chars'] or 0 for c in before),
                           pre_edit_output_lines=sum(c['output_lines'] or 0 for c in before),
                           prompt_chars=prompt.get('chars', 0), tools=len(own), unmatched_calls=sum(c['end'] == '' for c in own),
                           phases_json=json.dumps({k: round(v, 3) for k, v in phases.items()}, sort_keys=True),
                           partition_json=json.dumps({k: round(v, 3) for k, v in partition.items()}, sort_keys=True),
                           evidence=f'{src.relative_to(data)}:1'))
        calls.extend(own)
    return agents, calls, messages


def parse_logs(data):
    logs = []
    for p in sorted((data / 'logs').rglob('*')):
        if not p.is_file():
            continue
        text = p.read_text(errors='replace')
        builds = [dict(status=m[1], seconds=seconds(m[2]), line=text[:m.start()].count('\n') + 1)
                  for m in re.finditer(r'BUILD (SUCCESSFUL|FAILED) in ([^\n]+)', text)]
        waits = [int(x) for x in re.findall(r'Got .* after (\d+) s', text)]
        test_lines = [f'{i}:{line}' for i, line in enumerate(text.splitlines(), 1)
                      if re.search(r'\d+ tests? (?:completed|started)|\bOK\s*\(|\bTime:|Batch \d|batch \d|Starting \d+ tests|\d+ tests.*failed', line)]
        boot = [f'{i}:{line}' for i, line in enumerate(text.splitlines(), 1) if 'boot' in line.lower() and len(line) < 500]
        if builds or waits or p.suffix == '.peak' or test_lines or boot:
            wave = re.search(r'(?:sift-w|gate-w|/w)(\d+)', str(p))
            logs.append(dict(path=str(p.relative_to(data)), wave=int(wave[1]) if wave else '',
                             build_count=len(builds), build_s=sum(b['seconds'] for b in builds),
                             builds_json=json.dumps(builds), lock_wait_s=sum(waits), wait_count=len(waits),
                             daemon_messages=text.count('single-use Daemon'),
                             tests='\n'.join(test_lines), boot_lines='\n'.join(boot),
                             peak_kib=(re.search(r'peak: (\d+) kB', text)[1] if re.search(r'peak: (\d+) kB', text) else ''),
                             exit_codes=' '.join(re.findall(r'\bexit[=:]\s*(-?\d+)', text))))
    return logs


def runlog_rows(data):
    p = data / 'logs/RUNLOG.md'
    rows = []
    if p.exists():
        for i, line in enumerate(p.read_text().splitlines(), 1):
            c = [s.strip() for s in line.split('|')[1:-1]]
            if len(c) == 6 and c[0] in ('dispatch', 'phase', 'return'):
                rows.append(dict(zip(('event', 'ticket', 'phase', 'utc', 'epoch', 'status'), c), evidence=f'logs/RUNLOG.md:{i}'))
    return rows


def run(args):
    data, output = args.data.resolve(), args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    agents, calls, messages = parse_transcripts(data)
    logs = parse_logs(data)
    csv_write(output / 'subagents.csv', [a for a in agents if a['stage'] != 'coordinator'])
    csv_write(output / 'coordinators.csv', [a for a in agents if a['stage'] == 'coordinator'])
    csv_write(output / 'tool-calls.csv', calls)
    csv_write(output / 'logs.csv', logs)
    csv_write(output / 'runlog.csv', runlog_rows(data))
    v2_log = data / 'logs/RUNLOG.v2.jsonl'
    if v2_log.exists():
        rows = [json.loads(line) for line in v2_log.read_text().splitlines() if line.strip()]
        if rows:
            csv_write(output / 'runlog-v2.csv', rows)
    csv_write(output / 'messages.csv', messages)
    # A tool call is not an OS process timer. Export execution candidates separately.
    execs = []
    for c in calls:
        if c['tool'] not in ('Bash', 'Shell'):
            continue
        kind, snippet = execution(c['command'])
        if kind:
            execs.append({k: c[k] for k in ('session', 'agent', 'description', 'wave', 'stage', 'call_id', 'start', 'end', 'duration_s', 'process_duration_s', 'process_end_epoch', 'asynchronous', 'completion_source', 'command', 'result_evidence', 'evidence')} | {'kind': kind})
    csv_write(output / 'executions.csv', execs)
    totals = {}
    for wave in args.waves:
        aa = [a for a in agents if a['wave'] == wave]
        ee = [c for c in execs if c['wave'] == wave]
        totals[str(wave)] = dict(agents=len(aa), agent_wall_s=sum(a['duration_s'] for a in aa),
                                stages=dict(Counter(a['stage'] for a in aa)), executions=dict(Counter(c['kind'] for c in ee)),
                                category_tool_seconds={k: round(sum(c['duration_s'] or 0 for c in calls if c['wave'] == wave and c['category'] == k), 3) for k in sorted({c['category'] for c in calls if c['wave'] == wave})})
    summary = dict(subagents=len(agents) - sum(a['stage'] == 'coordinator' for a in agents), tool_calls=len(calls),
                   unpaired_calls=sum(c['end'] == '' for c in calls), waves=totals)
    (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    root = pathlib.Path(__file__).resolve().parent
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--data', type=pathlib.Path, default=root / 'data')
    p.add_argument('--output', type=pathlib.Path, default=root / 'generated')
    p.add_argument('--waves', nargs='+', type=int, default=[6, 7], help='waves to summarize; all calls remain in CSVs')
    run(p.parse_args())
