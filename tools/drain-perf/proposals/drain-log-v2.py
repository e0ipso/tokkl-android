#!/usr/bin/env python3
"""Candidate parallel drain logger. This file is not installed in the live skill."""
import argparse
import fcntl
import json
import os
import pathlib
import re
import time
from datetime import datetime, timezone

PHASES = ('orient', 'implement', 'verify', 'bookkeep')


def validate(event, operands, prefix):
    ticket = re.compile(re.escape(prefix) + r'-\d{4,}\Z')
    if event == 'dispatch':
        if not operands or not all(ticket.fullmatch(t) for t in operands):
            raise ValueError('dispatch requires ticket IDs')
        return {'tickets': operands}
    if event == 'phase':
        if len(operands) != 1 or operands[0] not in PHASES:
            raise ValueError('phase requires orient, implement, verify or bookkeep')
        return {'phase': operands[0]}
    if not operands or len(operands) % 2 or any(not ticket.fullmatch(t) for t in operands[::2]):
        raise ValueError('return requires ticket/status pairs')
    if not all(re.fullmatch(r'[a-z][a-z -]{0,39}', s) for s in operands[1::2]):
        raise ValueError('invalid status')
    return {'statuses': dict(zip(operands[::2], operands[1::2]))}


def report(rows):
    groups = {}
    for row in rows:
        groups.setdefault(row['group'], []).append(row)
    out = []
    for group, events in groups.items():
        dispatches = [e for e in events if e['event'] == 'dispatch']
        returns = [e for e in events if e['event'] == 'return']
        seen = [e['phase'] for e in events if e['event'] == 'phase']
        issues = []
        if len(dispatches) != 1:
            issues.append('expected exactly one dispatch')
        if seen != list(PHASES):
            issues.append('missing, duplicate or out-of-order phase stamps')
        if not returns:
            issues.append('no return')
        boots = {e['boot_id'] for e in events}
        if len(boots) != 1:
            issues.append('cross-boot duration unavailable')
        statuses = {}
        for e in returns:
            statuses.update(e['statuses'])
        if dispatches and set(statuses) != set(dispatches[0]['tickets']):
            issues.append('return membership differs from dispatch')
        duration = None
        phases = {}
        if len(dispatches) == 1 and returns and len(boots) == 1:
            first = dispatches[0]['monotonic_ns']
            last = returns[-1]['monotonic_ns']
            duration = (last - first) / 1e9
            if duration < 0:
                issues.append('negative duration')
                duration = None
            stamps = [e for e in events if e['event'] == 'phase']
            if seen == list(PHASES):
                for i, e in enumerate(stamps):
                    stop = stamps[i + 1]['monotonic_ns'] if i + 1 < len(stamps) else last
                    phases[e['phase']] = (stop - e['monotonic_ns']) / 1e9
                if any(t < 0 for t in phases.values()):
                    issues.append('phase outside lifetime')
                    phases = {}
        out.append({'group': group, 'duration_s': duration, 'phases_s': phases,
                    'statuses': statuses, 'issues': issues})
    return out


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--log', type=pathlib.Path, required=True)
    p.add_argument('--prefix', default='TKA')
    p.add_argument('--group', required=True)
    p.add_argument('event', choices=('dispatch', 'phase', 'return', 'report'))
    p.add_argument('operands', nargs='*')
    args = p.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,95}', args.group):
        p.error('group must be a short identifier')
    if args.event == 'report':
        with args.log.open() as f:
            fcntl.flock(f, fcntl.LOCK_SH)
            rows = [json.loads(line) for line in f if line.strip()]
        print(json.dumps(report(rows), indent=2))
        if any(row['issues'] for row in report(rows)):
            raise SystemExit(1)
        return
    try:
        details = validate(args.event, args.operands, args.prefix)
    except ValueError as e:
        p.error(str(e))
    with args.log.open('a') as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        # Timestamp inside the lock so append order is also clock order.
        row = {'schema': 2, 'group': args.group, 'event': args.event, 'pid': os.getpid(),
               'utc': datetime.now(timezone.utc).isoformat(), 'epoch_ns': time.time_ns(),
               'monotonic_ns': time.monotonic_ns(),
               'boot_id': pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip(), **details}
        f.write(json.dumps(row) + '\n')
        f.flush()
        os.fsync(f.fileno())


if __name__ == '__main__':
    main()
