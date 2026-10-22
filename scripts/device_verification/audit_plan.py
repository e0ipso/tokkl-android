#!/usr/bin/env python3
"""Read-only full-plan/configuration and fresh preparation audit. Never admits work."""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import ast
import hashlib
import json
import os
import shutil
import sys

sys.dont_write_bytecode = True
B = Path(__file__).resolve().parent
sys.path[:0] = [str(B), str(B/'runtime')]
from conformance import verify
from configuration import load, validate
from duration_policy import window_bounds


def audit(config):
    proof = verify()
    checked = validate(config)
    source = (B/'runtime/controller.py').read_text()
    functions = [n for n in ast.parse(source).body if isinstance(n, ast.FunctionDef)
                 and n.name in ['now', 'limits', 'conditions', 'preparation_snapshot']]
    app = Path('/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice')
    original = {'memory.high': str(10*1024**3), 'memory.max': str(12*1024**3), 'memory.swap.max': str(2*1024**3)}
    namespace = dict(Path=Path, datetime=datetime, timezone=timezone, app=app, original=original)
    exec(compile(ast.Module(body=functions, type_ignores=[]), '<actual-controller-readonly-snapshot>', 'exec'), namespace)
    before = namespace['preparation_snapshot']()
    deficit = max(0, before['current']-6*1024**3, 9*1024**3-before['host_available'])
    request = deficit+128*1024**2 if deficit else 0
    policy = checked['preparation_policy']
    blockers = []
    if before['limits'] != original: blockers.append('original shared limits differ; no overwrite')
    if request > policy['max_request_bytes']: blockers.append('full opening request exceeds explicit per-preparation cap')
    if request > before['stat']['inactive_file']: blockers.append('inactive file does not cover the whole request')
    if request+checked['carried_reclaim_bytes'] > checked['reclaim_budget_bytes']: blockers.append('cumulative budget cannot cover opening request')
    target = app/'memory.reclaim'
    info = target.stat()
    reclaim_target = {'path': str(target), 'uid': info.st_uid, 'gid': info.st_gid,
                      'mode': oct(info.st_mode & 0o777), 'writable': os.access(target, os.W_OK)}
    if (info.st_uid, info.st_gid, info.st_mode & 0o777) != (1000, 1001, 0o200) or not reclaim_target['writable']:
        blockers.append('actual BASE helper reclaim-target permission guard would refuse')
    tools = {name: shutil.which(name, path=config['environment']['PATH']) for name in
             ['systemd-run', 'systemctl', 'choom', 'timeout', 'docker', 'openssl', 'java', 'adb', 'emulator', 'flock', 'git', 'node']}
    blockers += ['configured tool missing: '+name for name, path in tools.items() if not path]
    if not Path('/tmp/android-e0ipso').is_dir(): blockers.append('actual controller shared Android directory is absent')
    project = Path(config['project'])
    screenshots = sorted(project.glob('app/src/androidTest/assets/screenshots/**/*.png'))
    if len(screenshots) != 68: blockers.append('expected 68 unchanged screenshot references, found '+str(len(screenshots)))
    benchmarks = {}
    for path in project.glob('macrobenchmark/src/**/*.kt'):
        text = path.read_text()
        if 'iterations' in text:
            benchmarks[str(path.relative_to(project))] = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                                                        'iteration_lines': [l.strip() for l in text.splitlines() if 'iterations' in l],
                                                        'retry_lines': [l.strip() for l in text.splitlines() if 'repeat(' in l]}
    versions = {}
    sdk = Path(config['environment']['ANDROID_HOME'])
    for path in [Path(config['environment']['JAVA_HOME'])/'release', sdk/'emulator/source.properties',
                 sdk/'platform-tools/source.properties', sdk/'system-images/android-36/default/x86_64/source.properties',
                 Path.home()/'.android/avd/tokkl-api36.avd/config.ini']:
        versions[str(path)] = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'content': path.read_text()} if path.is_file() else {'unavailable': True}
    plan = [(x['api'], x['label'], x['args'], x['seconds'], x['standalone']) for x in config['plan']]
    return {'configuration_sha256': checked['_sha256'], 'proof': proof, 'observed_at': namespace['now'](),
            'opening_snapshot': before, 'fresh_full_deficit_bytes': deficit, 'fresh_request_bytes': request,
            'reclaim_target_readonly_metadata': reclaim_target,
            'preparation_policy': policy, 'reclaim_budget_bytes': checked['reclaim_budget_bytes'],
            'carried_reclaim_bytes': checked['carried_reclaim_bytes'], 'plan_bounds': window_bounds(plan),
            'tools': tools, 'versions': versions, 'screenshot_reference_count': len(screenshots),
            'benchmark_sources': benchmarks, 'blockers_at_observation': blockers,
            'admission_or_activation': False, 'note': 'snapshot only; conditions must be reread immediately before authorized work',
            'unverified': ['actual SDK/API36 full app and benchmark gate', 'metric/sample finalization',
                           'authoritative 10 percent historical baseline remains owner TKA-0228 limitation',
                           'release/reproducibility and final API31 compatibility']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(audit(load(args.config)), sort_keys=True))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(json.dumps({'audit_failed': str(error), 'admission_or_activation': False}), file=sys.stderr)
        sys.exit(2)
