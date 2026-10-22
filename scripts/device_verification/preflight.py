#!/usr/bin/env python3
"""Read-only conformance/configuration check. Never prepares or runs a workload."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from conformance import verify
from runtime.configuration import canonical, load, validate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path)
    args = parser.parse_args()
    try:
        value = verify()
        if args.config:
            config = validate(load(args.config))
            value.update(configuration_sha256=config['_sha256'], plan=config['plan'],
                         run_dir=config['run_dir'], lease_id=config['lease_id'])
        runtime = Path(__file__).parent/'runtime'
        manifest = {str(p.relative_to(runtime)): hashlib.sha256(p.read_bytes()).hexdigest()
                    for p in sorted(runtime.rglob('*')) if p.is_file() and '__pycache__' not in p.parts}
        value['runtime_sha256'] = hashlib.sha256(canonical(manifest)).hexdigest()
        print(json.dumps(value, sort_keys=True))
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(json.dumps({'conformant': False, 'error': str(error)}), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
