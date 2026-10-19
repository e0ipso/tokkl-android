#!/usr/bin/env python3
"""Verify the offline investigation without touching a drain or running Android tools."""
import csv
import hashlib
import json
import gzip
import re
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = ROOT / 'tools/drain-perf'
OUTPUT = HERE / 'generated'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    OUTPUT.mkdir(exist_ok=True)
    with (OUTPUT / 'verification.txt').open('w') as log:
        def run(args, expected=0):
            log.write('$ ' + ' '.join(str(a) for a in args) + '\n')
            log.flush()
            result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
            log.write(result.stdout + result.stderr + f'\nexit={result.returncode}\n\n')
            log.flush()
            if result.returncode != expected:
                raise RuntimeError(f'{args[0]} exited {result.returncode}, expected {expected}')
            return result

        run([sys.executable, str(HERE / 'rebuild.py')])
        run([sys.executable, str(HERE / 'analyze.py')])
        run([sys.executable, '-m', 'unittest', 'discover', '-s', str(HERE), '-p', 'test_analysis.py', '-v'])
        repro = OUTPUT / 'repro'
        if repro.exists():
            shutil.rmtree(repro)
        run([sys.executable, str(HERE / 'rebuild.py'), '--output', str(repro)])
        run([sys.executable, str(HERE / 'analyze.py'), '--data', str(HERE / 'data'), '--generated', str(repro)])
        for p in sorted(repro.iterdir()):
            assert digest(p) == digest(OUTPUT / p.name), f'non-reproducible {p.name}'
        log.write('Rebuilt CSV/JSON files are byte-identical in a second output directory.\n')

        with (OUTPUT / 'subagents.csv').open() as f:
            agents = list(csv.DictReader(f))
        with (OUTPUT / 'tool-calls.csv').open() as f:
            calls = list(csv.DictReader(f))
        assert len(agents) == 87 and len(calls) == 10374
        assert len({(c['session'], c['agent'], c['call_id']) for c in calls}) == len(calls)
        assert all(float(c['duration_s']) >= 0 for c in calls if c['duration_s'])
        assert all(float(c['process_duration_s']) >= 0 for c in calls if c['process_duration_s'])
        for a in agents:
            assert abs(sum(json.loads(a['partition_json']).values()) - float(a['duration_s'])) < .03
        h4 = next(a for a in agents if a['agent'] == 'agent-abe5b37dcbf6bed70')
        assert abs(float(h4['duration_s']) - 8632.741) < .001
        analysis = json.loads((OUTPUT / 'analysis.json').read_text())
        assert analysis['7']['lock_wait_s'] == 13086
        assert analysis['7']['observed_device_log_count'] == 203
        assert len(analysis['6']['known_full_scope_logs']) >= 3
        assert len(analysis['7']['known_full_scope_logs']) >= 11
        trees = {r['ref']: r for r in json.loads((HERE / 'data/tree-evidence.json').read_text())}
        assert trees['ba0a100']['tree'] == trees['cc8c067']['tree']
        assert trees['d21f09e']['tree'] == trees['ec480ca']['tree']
        assert trees['cc8c067']['code_listing'] == trees['51e21d5']['code_listing']
        scope = json.loads((HERE / 'data/scope-evidence.json').read_text())
        assert len(scope['initial_scope_ids']) == 25 and len(scope['shipped_done_ids']) == 44
        assert set(scope['new_shipped_ids']) == set(scope['shipped_done_ids']) - set(scope['initial_scope_ids'])
        assert not scope['original_not_shipped']
        spans = {a['agent']: float(a['duration_s']) for a in agents}
        assert abs(sum(spans[a['agent']] for a in scope['selected_wholly_new_sittings']) - scope['selected_agent_wall_s']) < .01
        log.write('Count, nonnegative duration, exclusive interval, H4, lock, full-run and tree invariants pass.\n')

        manifest = json.loads((HERE / 'data/manifest.json').read_text())
        for src in manifest['sources']:
            assert digest(HERE / 'data' / src['saved']) == src['saved_sha256'], src['saved']
        log.write(f'{len(manifest["sources"])} saved evidence hashes match the manifest.\n')
        credential_patterns = [re.compile(r'gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}'),
                               re.compile(r'(?i)Bearer\s+[A-Za-z0-9._~+/-]{20,}'),
                               re.compile(r'https?://[^\s/:@]+:[^\s/@]+@')]
        for path in (HERE / 'data').rglob('*'):
            if not path.is_file():
                continue
            text = gzip.decompress(path.read_bytes()).decode() if path.suffix == '.gz' else path.read_text(errors='replace')
            assert not any(pattern.search(text) for pattern in credential_patterns), f'credential pattern in {path.name}'
        log.write('Conservative token/Bearer/URL-userinfo audit has no remaining pattern hits.\n')

        run([sys.executable, str(HERE / 'propose.py')])
        fixture = OUTPUT / 'patch-check'
        if fixture.exists():
            shutil.rmtree(fixture)
        fixture.mkdir()
        for filename, baseline in [
            ('.agents/skills/sift-drain/SKILL.md', 'sift-drain-SKILL.md'),
            ('.agents/skills/sift-drain/references/ticket-agent-prompt.md', 'ticket-agent-prompt.md'),
            ('.agents/skills/sift-drain/references/wave-gate.md', 'wave-gate.md'),
            ('.agents/skills/sift-drain/scripts/drain-log.sh', 'drain-log.sh'),
            ('scripts/instrumented-tests.sh', 'instrumented-tests.sh'),
        ]:
            path = fixture / filename
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(HERE / 'data/baselines' / baseline, path)
        for patch in sorted((HERE / 'patches').glob('*.patch')):
            directory = '--directory=' + str(fixture.relative_to(ROOT))
            run(['git', 'apply', '--check', directory, str(patch)])
            run(['git', 'apply', directory, str(patch)])
        for filename in ('scripts/instrumented-tests.sh', '.agents/skills/sift-drain/scripts/drain-log.sh'):
            run(['bash', '-n', str(fixture / filename)])
        for p in HERE.glob('*.py'):
            compile(p.read_text(), str(p), 'exec')
        candidate = fixture / '.agents/skills/sift-drain/scripts/drain-log-v2.py'
        compile(candidate.read_text(), str(candidate), 'exec')
        log.write('All Python sources compile; both patched shell scripts pass bash -n.\n')

        with tempfile.TemporaryDirectory(dir=OUTPUT) as tmp:
            ledger = pathlib.Path(tmp) / 'events.jsonl'
            base = [sys.executable, str(candidate), '--log', str(ledger)]
            # Interleave two real CLI groups without using a tracker detector or live path.
            for group, ticket in [('one', 'TKA-0001'), ('two', 'TKA-0002')]:
                run(base + ['--group', group, 'dispatch', ticket])
            for phase in ('orient', 'implement', 'verify', 'bookkeep'):
                for group in ('one', 'two'):
                    run(base + ['--group', group, 'phase', phase])
            for group, ticket in [('two', 'TKA-0002'), ('one', 'TKA-0001')]:
                run(base + ['--group', group, 'return', ticket, 'done'])
            r = run(base + ['--group', 'all', 'report'])
            assert all(not row['issues'] for row in json.loads(r.stdout))
            run(base + ['--group', 'incomplete', 'dispatch', 'TKA-0003'])
            run(base + ['--group', 'all', 'report'], expected=1)
        log.write('CLI interleaving smoke check passes; incomplete telemetry returns exit 1.\n')
        # Source files must remain the base version. This work only adds tools and knowledge.
        run(['git', 'diff', '--exit-code', '51e21d5', '--', 'scripts/instrumented-tests.sh', '.agents/skills/sift-drain'])
        log.write('Offline verification complete. No Android runner or build was executed.\n')
    print('Offline verification passed; see tools/drain-perf/generated/verification.txt')


if __name__ == '__main__':
    main()
