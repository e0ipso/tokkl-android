"""Read-only cgroup-v2 event attribution for the finite gate window.

Only an exactly accounted unrelated local high event can be cleared. Kernel
hierarchical counters are checked against the sum of local subtree counters.
A lost scope, inaccessible counter or reset is never exculpatory. Accounting
ambiguity permits only finite resampling; unresolved evidence still aborts.
"""
from pathlib import Path
from datetime import datetime, timezone
import os
import time

KEYS = ('high', 'max', 'oom', 'oom_kill', 'oom_group_kill')
ROOT = Path('/sys/fs/cgroup')

def counter(text):
    values = dict(line.split() for line in text.splitlines())
    return {k: int(values[k]) for k in KEYS}

def under(path, parent):
    return path == parent or path.startswith(parent.rstrip('/') + '/')

def capture(root=ROOT/"user.slice", *, deadline=None):
    started = time.monotonic()
    stamp = datetime.now(timezone.utc).isoformat()
    rows, errors = {}, []
    def check_deadline():
        if deadline is not None and time.monotonic() >= deadline:
            raise TimeoutError('consistent-read sampling deadline exhausted')
    def read(path):
        check_deadline()
        return path.read_text()
    def visit(path):
        try:
            check_deadline()
            if path != ROOT:
                inode = path.stat().st_ino
                before = counter(read(path/'memory.events'))
                local = counter(read(path/'memory.events.local'))
                after = counter(read(path/'memory.events'))
                rows[str(path)] = {'inode': inode, 'time': stamp, 'local': local,
                    'events': after, 'stable': before == after,
                    'current': int(read(path/'memory.current')),
                    'high': read(path/'memory.high').strip(),
                    'max': read(path/'memory.max').strip(),
                    'pressure': read(path/'memory.pressure').strip()}
            with os.scandir(path) as entries:
                children = []
                for entry in entries:
                    check_deadline()
                    if entry.is_dir(follow_symlinks=False):
                        children.append(Path(entry.path))
            for child in children:
                visit(child)
                if deadline is not None and time.monotonic() >= deadline:
                    break
        except (OSError, ValueError, KeyError) as error:
            errors.append({'path': str(path), 'error': type(error).__name__ + ': ' + str(error)})
    visit(root)
    # Root has no memory.events or memory.events.local by kernel design.
    return {'time': stamp, 'monotonic': started, 'duration': time.monotonic()-started,
            'root': str(root), 'root_counters': '/sys/fs/cgroup root counters not exposed by cgroup v2; unrelated system.slice excluded',
            'rows': rows, 'errors': errors}

class Monitor:
    def __init__(self, baseline, app, workload, *, sampler=None, clock=None, retry_limit=3, retry_seconds=.25):
        if type(retry_limit) is not int or not 0 <= retry_limit <= 3:
            raise ValueError('at most three resamples are permitted')
        if isinstance(retry_seconds, bool) or not isinstance(retry_seconds, (int, float)) or not 0 < retry_seconds <= .25:
            raise ValueError('resampling budget must be positive and at most 250ms')
        self.sampler = sampler or (lambda root, deadline: capture(Path(root), deadline=deadline))
        self.clock = clock or time.monotonic
        self.retry_limit, self.retry_seconds = retry_limit, retry_seconds
        self.previous = baseline
        self.app = str(app)
        self.workload = str(workload)
        self.pressure_since = None

    def _inspect_once(self, prior, current, owned=(), host_available=None):
        protected = [self.workload] + [str(p) for p in owned]
        ancestors = [p for p in current['rows'] if under(self.app, p)]
        def relevant(p):
            return p in ancestors or any(under(p, own) for own in protected)
        result = {'time': current['time'], 'origins': [], 'abort': None,
                  'evidence_errors': current['errors'], 'protected': protected,
                  'ancestors': ancestors}
        def abort(reason):
            result['abort'] = reason
            return result
        if prior['errors'] or current['errors']:
            return abort('inaccessible or disappearing cgroup evidence')
        if self.app not in ancestors:
            return abort('app.slice evidence missing')
        for p in ancestors:
            row = current['rows'][p]
            if any(row[k] != 'max' and row['current'] >= int(row[k]) for k in ('high','max')):
                return abort('ancestor budget reached: ' + p)
        deltas = {}
        racing = []
        for p, row in current['rows'].items():
            old = prior['rows'].get(p)
            if old and old['inode'] != row['inode']:
                return abort('cgroup identity changed: ' + p)
            if not row['stable'] or (old and not old['stable']):
                racing.append(p)
            deltas[p] = {kind: {k: row[kind][k] - (old[kind][k] if old else 0)
                                  for k in KEYS} for kind in ('events', 'local')}
            if any(v < 0 for d in deltas[p].values() for v in d.values()):
                return abort('counter reset: ' + p)
        disappeared = set(prior['rows']) - set(current['rows'])
        # Missing relevant scopes matter even without a hierarchical increment.
        if any(relevant(p) for p in disappeared):
            return abort('owned or ancestor scope disappeared: ' + ','.join(sorted(disappeared)))
        for p, delta in deltas.items():
            if not any(delta['local'].values()):
                continue
            result['origins'].append({'path': p, 'inode': current['rows'][p]['inode'],
                'time': current['time'], 'local_delta': delta['local'], 'relevant': relevant(p),
                'new_scope': p not in prior['rows']})
            if any(delta['local'][key] for key in KEYS if key != 'high'):
                return abort('local max/OOM safeguard: ' + p)
            if relevant(p) and any(delta['local'].values()):
                return abort('owned or ancestor local event: ' + p)
        for p in ancestors + [p for p in current['rows'] if any(under(p, own) for own in protected)]:
            delta = deltas[p]['events']
            if any(delta[k] for k in KEYS if k != 'high'):
                return abort('hierarchical max/OOM safeguard: ' + p)
            if delta['high'] and any(under(p, own) for own in protected):
                return abort('owned hierarchical high event: ' + p)
        # avg10 is telemetry only. Guard sustained full stalls coupled to physical
        # pressure, alongside the script's existing boot/device/hang deadlines.
        elapsed = current['monotonic'] - prior['monotonic']
        def full_total(row):
            value = next((word.split('=')[1] for line in row['pressure'].splitlines()
                       if line.startswith('full ') for word in line.split() if word.startswith('total=')), None)
            if value is None:
                raise ValueError('missing full PSI total')
            return int(value)
        try:
            stall = max((full_total(current['rows'][p])-full_total(prior['rows'][p]))
                        / (elapsed * 1000000) for p in ancestors)
        except (KeyError, ValueError, StopIteration, ZeroDivisionError):
            return abort('missing pressure evidence')
        if host_available is None:
            return abort('missing physical memory evidence')
        if stall < 0:
            return abort('pressure counter reset')
        harmful = stall >= .01 and host_available < 5000 * 1048576
        self.pressure_since = (self.pressure_since or prior['monotonic']) if harmful else None
        result.update(full_stall_fraction=stall, host_available=host_available,
                      harmful_pressure_since=self.pressure_since)
        if self.pressure_since is not None and current['monotonic'] - self.pressure_since >= 30:
            return abort('sustained full memory stalls and low physical headroom')
        if racing:
            return abort('ambiguous concurrent counter snapshot: ' + ','.join(racing))
        for p in ancestors + [p for p in current['rows'] if any(under(p, own) for own in protected)]:
            delta = deltas[p]['events']
            if delta['high'] or any(deltas[q]['local']['high'] for q in deltas if under(q, p)):
                if any(under(lost, p) for lost in disappeared):
                    return abort('unattributed high event with vanished descendant: ' + p)
                sources = [q for q in deltas if under(q, p)]
                if sum(deltas[q]['local']['high'] for q in sources) != delta['high']:
                    return abort('ambiguous hierarchical/local high accounting: ' + p)
                if any(relevant(q) and deltas[q]['local']['high'] for q in sources):
                    return abort('relevant high origin: ' + p)
        return result

    @staticmethod
    def _counter_view(snapshot):
        return {p: (row['inode'], row['stable'], row['events'], row['local'])
                for p, row in snapshot['rows'].items()}

    @staticmethod
    def _retry_integrity(previous, current):
        if current['errors']:
            return 'inaccessible or disappearing cgroup evidence during resampling'
        if previous.get('root') != current.get('root'):
            return 'capture root changed during resampling'
        if set(previous['rows']) - set(current['rows']):
            return 'scope disappeared during resampling'
        if current['monotonic'] <= previous['monotonic']:
            return 'non-increasing sample timestamp during resampling'
        for path, old in previous['rows'].items():
            row = current['rows'][path]
            if row['inode'] != old['inode']:
                return 'cgroup identity changed during resampling: ' + path
            if any(row[kind][key] < old[kind][key]
                   for kind in ('events', 'local') for key in KEYS):
                return 'counter reset during resampling: ' + path
        return None

    def inspect(self, current, owned=(), host_available=None):
        # Keep the original anchor for every read: never rebase away an event.
        prior = self.previous
        started = self.clock()
        deadline = started + self.retry_seconds
        observed, reads = current, []
        original_reason = None
        owned = tuple(owned)
        def finish(result, reason=None):
            self.previous = observed
            if reason is not None:
                result['abort'] = reason
            result['sampling'] = {'reads': reads, 'retry_limit': self.retry_limit,
                'budget_seconds': self.retry_seconds, 'elapsed_seconds': self.clock()-started,
                'recovered': bool(original_reason and result['abort'] is None),
                'initial_ambiguity': original_reason,
                'atomic_snapshot_claimed': False}
            return result
        for attempt in range(self.retry_limit + 1):
            try:
                result = self._inspect_once(prior, observed, owned, host_available)
            except (OSError, ValueError, KeyError, TypeError) as error:
                result = {'time': observed.get('time'), 'origins': [],
                    'abort': 'missing or malformed snapshot evidence',
                    'evidence_errors': [{'error': type(error).__name__ + ': ' + str(error)}]}
                reads.append({'snapshot': observed, 'assessment_abort': result['abort']})
                return finish(result)
            reads.append({'snapshot': observed, 'assessment_abort': result['abort']})
            # Check every observed frame, not just the final accepted frame.
            if attempt:
                integrity = self._retry_integrity(reads[-2]['snapshot'], observed)
                if integrity:
                    return finish(result, integrity)
            retryable = result['abort'] and result['abort'].startswith((
                'ambiguous concurrent counter snapshot:',
                'ambiguous hierarchical/local high accounting:'))
            if result['abort'] and not retryable:
                return finish(result)
            if not original_reason and result['abort'] is None:
                return finish(result)
            if retryable and original_reason is None:
                original_reason = result['abort']
            if attempt and self.clock() >= deadline:
                return finish(result, original_reason + '; consistent-read time budget exhausted')
            if attempt and result['abort'] is None and self._counter_view(reads[-2]['snapshot']) == self._counter_view(observed):
                # Two agreeing full-tree counter reads, exact attribution against
                # the unchanged anchor, and no intervening genuine event.
                return finish(result)
            if not current.get('root') or not prior.get('root') or current.get('root') != prior.get('root'):
                return finish(result, original_reason + '; capture root unavailable or changed')
            if any(not row['stable'] for row in prior['rows'].values()):
                return finish(result, original_reason + '; baseline was unstable; cannot safely rebase')
            if attempt == self.retry_limit or self.clock() >= deadline:
                return finish(result, original_reason + '; consistent-read attempts/time exhausted')
            try:
                observed = self.sampler(current['root'], deadline)
            except (OSError, ValueError, KeyError) as error:
                result['evidence_errors'] = result['evidence_errors'] + [
                    {'path': current['root'], 'error': type(error).__name__ + ': ' + str(error)}]
                return finish(result, 'inaccessible resampling evidence')
