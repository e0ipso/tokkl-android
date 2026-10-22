#!/usr/bin/env python3
"""Read complete current class sources and emit an INACTIVE recipe declaration."""
from pathlib import Path
import argparse
import json
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent/'runtime'))
from scoped_results import declare

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, required=True)
    parser.add_argument('--classes', nargs='+', required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(declare(args.project, args.classes), sort_keys=True))
    except (OSError, ValueError, TypeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
