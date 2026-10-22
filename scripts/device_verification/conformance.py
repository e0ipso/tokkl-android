"""Read-only proof of the maintained candidate against the frozen TKA-0280 code.

This compares bytes and declared configuration substitutions. It never rewrites
an AST or source file and never imports/executes the controller or its helpers.
"""
import hashlib
import json
from pathlib import Path

BASE = Path(__file__).resolve().parent
BOOTSTRAP = """# Frozen TKA-0280 controller with explicit configuration. No host work on import.
import sys
if __name__ != '__main__' or '--execute' not in sys.argv:
    raise SystemExit('Execution disabled: use read-only preflight and coordinator adoption')
from configuration import execution_configuration
config = execution_configuration(__file__)
"""


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(base=BASE):
    base = Path(base)
    for line in (base/'SHA256SUMS').read_text().splitlines():
        expected, name = line.split('  ', 1)
        path = Path(name)
        require(not path.is_absolute() and '..' not in path.parts, 'unsafe source manifest path')
        require(sha(base/path) == expected, 'maintained package differs from its reviewed source manifest: '+name)
    sources = json.loads((base/'provenance/sources.json').read_text())
    for name, row in sources.items():
        if row.get('policy') == 'byte-identical':
            require(sha(base/'runtime'/name) == row['sha256'], 'protected runtime changed: '+name)
    reference = base/'provenance/controller.py.txt'
    require(sha(reference) == sources['controller.py']['sha256'], 'frozen controller source changed')
    original = reference.read_text()
    source = (base/'runtime/controller.py').read_text()
    require(source.startswith(BOOTSTRAP), 'execution/adoption bootstrap changed')
    source = source[len(BOOTSTRAP):]
    for delta in reversed(json.loads((base/'provenance/configuration-delta.json').read_text())):
        require(source.count(delta['after']) == delta['occurrences'], 'configuration delta differs: '+delta['after'])
        source = source.replace(delta['after'], delta['before'])
    require(source == original[original.index('from pathlib import Path'):],
            'controller differs outside reviewed configuration substitutions')
    reference = base/'provenance/local-reclaim-finite.py.txt'
    require(sha(reference) == sources['local-reclaim-finite.py']['sha256'], 'frozen actual BASE reclaim source changed')
    original = reference.read_text()
    old = next(line.split(';')[0] for line in original.splitlines() if line.startswith('base=Path('))
    configured = (base/'runtime/local-reclaim-finite.py').read_text()
    new = 'base=Path(__file__).resolve().parent'
    require(configured.count(new) == 1 and configured.replace(new, old, 1) == original,
            'reclaim differs beyond its evidence directory')
    return {'conformant': True, 'unchanged_modules': [n for n, v in sources.items() if v.get('policy') == 'byte-identical'],
            'controller': sha(base/'runtime/controller.py'),
            'historical_watchdog_sha256': sources['watchdog_lifetime.py']['sha256'],
            'live_maintained_controller_verified': False, 'device_or_window_permission': False}
