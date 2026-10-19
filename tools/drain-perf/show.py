#!/usr/bin/env python3
"""Show a bounded excerpt around a snapshot evidence reference, including gzip rows."""
import argparse
import gzip
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('reference', help='data-relative path:line, as stored in the CSV evidence column')
p.add_argument('--radius', type=int, default=2)
args = p.parse_args()
path, number = args.reference.rsplit(':', 1)
target = (ROOT / 'data' / path).resolve()
if not target.is_relative_to(ROOT / 'data'):
    p.error('reference must be under data/')
line = int(number)
opener = gzip.open if target.suffix == '.gz' else open
with opener(target, 'rt') as f:
    for i, text in enumerate(f, 1):
        if line - args.radius <= i <= line + args.radius:
            print(f'{i}: {text.rstrip()[:10000]}')
        if i > line + args.radius:
            break
