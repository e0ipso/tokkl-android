#!/usr/bin/env python3
"""Freeze a credential-filtered, analysis-only projection of a Claude drain."""
import argparse
import gzip
import hashlib
import json
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

SESSIONS = [
    'd8ce5e9a-90ec-4035-9d07-f8489841ef5e',
    '7d03eac9-fabf-47b4-9438-5f343c193eb0',
    'bb655ca0-e9a3-4240-b4c5-8cc15b04f8d8',
    '7df98360-f53f-4512-bf4e-27ff0cb1d770',
]
SECRET = re.compile(r'(?i)(password|passwd|api[_-]?(?:token|key)|access[_-]?token|github_token|authorization|bearer\s|setup[_-]?code|private.key|BEGIN .*KEY|client_secret|gh[pousr]_[A-Za-z0-9]|github_pat_|curl\b.*(?:--user\b|\s-u\s)|https?://[^\s/:@]+:[^\s/@]+@)')
EVIDENCE = re.compile(r'(?i)(build (successful|failed)|\btime:|\bOK\s*\(|tests?|waiting for|got .* after|boot|daemon|configurat|elapsed|duration|agentid|total_tokens|total_duration|task|background|tokkl-api|\bcommit\b|sift|exit code|started|starting|finished|batch |peak|rss|qemu|oom|stoppedSessions|t3.service|t3-update)')


def clean(s):
    """Drop entire suspect lines; redact opaque strings even on retained lines."""
    lines = []
    in_key = False
    for line in str(s).splitlines():
        if '-----BEGIN ' in line:
            in_key = True
        if in_key or SECRET.search(line):
            lines.append('[credential-related line omitted]')
        else:
            # Keep git object names and image digests; omit long opaque values.
            if re.fullmatch(r'\s*[A-Za-z0-9_+/=-]{32,}\s*', line):
                line = '[opaque value omitted]'
            line = re.sub(r'(?<![a-fA-F0-9])[a-fA-F0-9]{32}(?![a-fA-F0-9])', '[32-hex value omitted]', line)
            lines.append(line)
        if '-----END ' in line:
            in_key = False
    return '\n'.join(lines)


def flatten(c):
    if isinstance(c, str):
        return c
    if isinstance(c, list):
        return '\n'.join(flatten(x) for x in c)
    if isinstance(c, dict):
        return c.get('text', '')  # Do not preserve images, attachments or signatures.
    return ''


def project_input(name, inp):
    out = {}
    for key in ('command', 'description', 'file_path', 'offset', 'limit', 'pattern', 'path', 'glob',
                'prompt', 'subagent_type', 'model', 'run_in_background', 'to', 'summary',
                'message', 'content', 'task_id', 'agent_id', 'block', 'timeout', 'timeout_ms'):
        if key in inp:
            val = inp[key]
            out[key] = clean(val) if isinstance(val, str) else val
    if name in ('Write', 'Edit', 'MultiEdit'):
        # Keep edit volume, not source bodies or embedded fixtures.
        out.pop('content', None)
        out['written_chars'] = sum(len(str(inp.get(k, ''))) for k in ('content', 'new_string'))
    return out


def snapshot(args):
    dest = args.output.resolve()
    dest.mkdir(parents=True, exist_ok=True)
    manifest = {'captured_at': datetime.now(timezone.utc).isoformat(), 'sources': [],
                'projection': 'No thinking, binary, env files or full tool output. Suspect lines dropped.'}
    first_timestamp = None

    def save_bytes(rel, data, source, original=None):
        p = dest / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
        manifest['sources'].append({'saved': str(rel), 'source': str(source),
                                    'original_sha256': hashlib.sha256(original if original is not None else data).hexdigest(),
                                    'saved_sha256': hashlib.sha256(data).hexdigest()})

    for sid in args.sessions:
        files = [args.transcripts / (sid + '.jsonl')]
        files += sorted((args.transcripts / sid / 'subagents').glob('*.jsonl'))
        for src in files:
            if not src.exists():
                continue
            original = src.read_bytes()  # Freeze once, even while the live session appends.
            rows = []
            meta = src.with_suffix('.meta.json')
            if meta.exists():
                rows.append({'kind': 'meta', 'data': json.loads(meta.read_text())})
            for lineno, line in enumerate(original.splitlines(), 1):
                try:
                    d = json.loads(line)
                except (ValueError, UnicodeDecodeError):
                    continue  # A live final line can be incomplete.
                msg = d.get('message', {})
                attachment = d.get('attachment', {})
                if d.get('type') == 'attachment' and attachment.get('type') == 'queued_command' and attachment.get('commandMode') == 'task-notification':
                    msg = {'role': 'notification', 'content': attachment.get('prompt', '')}
                    d['timestamp'] = attachment.get('timestamp', d.get('timestamp'))
                c = msg.get('content', [])
                if not d.get('timestamp') or not c:
                    continue
                if first_timestamp is None or d['timestamp'] < first_timestamp:
                    first_timestamp = d['timestamp']
                row = {k: d[k] for k in ('timestamp', 'type', 'uuid', 'parentUuid', 'agentId', 'sessionId', 'cwd') if k in d}
                row.update(source=str(src), source_line=lineno, role=msg.get('role'), model=msg.get('model'), blocks=[])
                if isinstance(c, str):
                    row['blocks'] = [{'type': 'text', 'text': clean(c), 'chars': len(c)}]
                else:
                    for b in c:
                        typ = b.get('type')
                        if typ == 'tool_use':
                            row['blocks'].append({'type': typ, 'id': b['id'], 'name': b['name'], 'input': project_input(b['name'], b.get('input', {}))})
                        elif typ == 'tool_result':
                            output = flatten(b.get('content', ''))
                            # Preserve lines relevant to timings and test outcomes, not arbitrary cat output.
                            lines = [{'line': n, 'text': clean(t)} for n, t in enumerate(output.splitlines(), 1) if EVIDENCE.search(t)]
                            row['blocks'].append({'type': typ, 'id': b['tool_use_id'], 'is_error': b.get('is_error', False),
                                                  'chars': len(output), 'lines': len(output.splitlines()), 'evidence': lines})
                        elif typ == 'text':
                            row['blocks'].append({'type': typ, 'text': clean(b.get('text', '')), 'chars': len(b.get('text', ''))})
                # Empty thinking rows still timestamp non-tool/model time, but no content is kept.
                rows.append(row)
            raw = ''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in rows).encode()
            rel = pathlib.Path('transcripts') / sid / (src.stem + '.jsonl.gz')
            save_bytes(rel, gzip.compress(raw, mtime=0), src, original)

    root = args.repo.resolve()
    evidence_files = [root / '.ai/sift/RUNLOG.md']
    v2_log = root / '.ai/sift/RUNLOG.v2.jsonl'
    if v2_log.exists():
        evidence_files.append(v2_log)
    state = args.state
    evidence_files += [p for p in state.rglob('*') if p.is_file() and p.suffix in ('.txt', '.md', '.log', '.peak', '.json')
                       and not any('results' in part or 'logcat' in part for part in p.parts)]
    patterns = args.worker_glob or ['sift-w6-*', 'sift-w7-*']
    for pat in patterns:
        for folder in pathlib.Path('/tmp').glob(pat):
            if folder.is_dir():
                evidence_files += [p for p in folder.rglob('*') if p.is_file() and p.suffix in ('.log', '.peak')
                                   and not any('results' in part or 'logcat' in part for part in p.parts)]
    for src in sorted(set(evidence_files)):
        if src.stat().st_size > 4_000_000:
            continue
        original = src.read_bytes()
        rel = pathlib.Path('logs') / (str(src.relative_to('/tmp')) if src.is_relative_to('/tmp') else src.name)
        save_bytes(rel, (clean(original.decode(errors='replace')) + '\n').encode(), src, original)

    junit = []
    folders = [state] + [p for pattern in patterns for p in pathlib.Path('/tmp').glob(pattern) if p.is_dir()]
    for folder in folders:
        for src in sorted(folder.rglob('*.xml')):
            try:
                tree = ET.parse(src)
            except (ET.ParseError, OSError):
                continue
            for suite in tree.iter('testsuite'):
                safe = {k: v for k, v in suite.attrib.items() if k in ('name', 'tests', 'failures', 'errors', 'skipped', 'time', 'timestamp')}
                tests = [{k: v for k, v in case.attrib.items() if k in ('name', 'classname', 'time')}
                         | {'status': 'failed' if case.find('failure') is not None else 'skipped' if case.find('skipped') is not None else 'passed'}
                         for case in suite.findall('testcase')]
                junit.append({'source': str(src), 'suite': safe, 'cases': tests})
    save_bytes(pathlib.Path('junit.jsonl'), ''.join(json.dumps(x) + '\n' for x in junit).encode(), 'safe JUnit attributes only')

    since = args.since or (first_timestamp[:10] if first_timestamp else '7 days ago')
    for name, command in [
        ('user-events.log', ['journalctl', '--user', '--since=' + since, '--no-pager', '-o', 'short-iso', '--grep=oom|t3-update|t3.service|stoppedSessions', '--case-sensitive=no']),
        ('kernel-oom.log', ['journalctl', '-k', '--since=' + since, '--no-pager', '-o', 'short-iso', '--grep=Killed process|oom-kill|out of memory', '--case-sensitive=no']),
        ('history.txt', ['git', '-C', str(root), 'log', '--format=%H %cI %s%n%b', 'main']),
        ('runlog-report.txt', ['bash', str(root / '.agents/skills/sift-drain/scripts/drain-log.sh'), 'report']),
    ]:
        r = subprocess.run(command, cwd=root, capture_output=True, text=True)
        save_bytes(pathlib.Path('host') / name, (clean(r.stdout + r.stderr) + '\n').encode(), ' '.join(command))
        manifest.setdefault('commands', []).append({'command': command, 'exit_code': r.returncode})
    (dest / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Snapshotted {len(manifest["sources"])} sources into {dest}')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--transcripts', type=pathlib.Path, default=pathlib.Path.home() / '.claude/projects/-home-e0ipso-workspace-apps-tokkl-android')
    p.add_argument('--repo', type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[2])
    p.add_argument('--output', type=pathlib.Path, default=pathlib.Path(__file__).resolve().parent / 'data')
    p.add_argument('--sessions', nargs='+', default=SESSIONS)
    p.add_argument('--state', type=pathlib.Path, default=pathlib.Path('/tmp/sift-drain.7oM4Ky'))
    p.add_argument('--worker-glob', action='append', help='wave log glob relative to /tmp, repeatable')
    p.add_argument('--since', help='journal start, defaults to earliest transcript date')
    snapshot(p.parse_args())
