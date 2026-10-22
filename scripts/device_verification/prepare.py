#!/usr/bin/env python3
"""Create a fresh immutable-byte runtime snapshot after coordinator review.

Only copies files and writes configuration/manifest. Does not start a controller,
scope, timer, device, workload or change any resource limit. Never edits history.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys

from conformance import BASE, verify
from runtime.configuration import canonical, load, validate


def prepare(config):
    proof = verify()
    checked = validate(config)
    destination = Path(config['run_dir'])
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.mkdir(mode=0o700)  # No merge/overwrite of historical directories.
    (destination/'scratch').mkdir(mode=0o700)
    source = BASE/'runtime'
    manifest = {}
    for path in sorted(source.rglob('*')):
        if not path.is_file() or '__pycache__' in path.parts:
            continue
        relative = path.relative_to(source)
        target = destination/relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        manifest[str(relative)] = hashlib.sha256(path.read_bytes()).hexdigest()
    for name, value in [('run-config.json', config), ('runtime-manifest.json', manifest), ('preparation-proof.json', proof)]:
        with (destination/name).open('x') as out:
            out.write(canonical(value).decode()+'\n')
    return {'run_dir': str(destination), 'configuration_sha256': checked['_sha256'],
            'runtime_sha256': hashlib.sha256(canonical(manifest)).hexdigest(),
            'lease_id': config['lease_id'], 'adoption_required': True,
            'live_maintained_controller_verified': False, 'device_or_window_permission': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(prepare(load(args.config)), sort_keys=True))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(json.dumps({'prepared': False, 'error': str(error)}), file=sys.stderr)
        sys.exit(2)
