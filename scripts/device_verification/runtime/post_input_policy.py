"""Declared post-build observation of two global Gradle indexes. Never writes.

One frozen scoped-app API36 job may declare this policy. Startup, every helper
and every other result profile still compare all original input pins. Only the
scoped-app post assessment may record the two reviewed module indexes as
observations; their original pins and the configuration identity never change.
"""
from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import re
import stat
if __package__:
    from .scoped_results import PROFILE as SCOPED_PROFILE
else:
    from scoped_results import PROFILE as SCOPED_PROFILE

PROFILE = 'gradle-global-index-observation-v1'
MODULES = Path('/home/e0ipso/.gradle/caches/modules-2')
ROOTS = ('files-2.1', 'metadata-2.107')
INDEXES = ('module-artifact.bin', 'module-metadata.bin')
INDEX_LIMIT = 8 * 1024**2
ENTRY_LIMIT = 262144
BEFORE = 'frozen post-input policy and original input pins'
AFTER = 'maintained scoped-app post assessment after workload closure'
NOT_INFERRED = ['unchanged selected dependency graph', 'absence of network activity',
                'cause of any index mutation', 'absence of transient additions']


def require(condition, message):
    if not condition:
        raise ValueError(message)


def _primitives():
    # configuration imports this module. Resolve its canonical encoding and
    # bounded hashing only on use, in either package or frozen-script mode.
    if __package__:
        from .configuration import canonical, sha
    else:
        from configuration import canonical, sha
    return canonical, sha


def digest(value):
    canonical, _ = _primitives()
    return hashlib.sha256(canonical(value)).hexdigest()


def reviewed():
    """The two reviewed dependency roots and the only two observable index paths."""
    roots = [str(MODULES/name) for name in ROOTS]
    return roots, [str(MODULES/ROOTS[1]/name) for name in INDEXES]


def policy_sha256(config):
    """Adoption binding. None keeps default and legacy adoptions unchanged."""
    return digest(config['post_input_policy']) if 'post_input_policy' in config else None


def _members(paths, roots):
    return sorted(path for path in paths if any(path.startswith(root+'/') for root in roots))


def declared(config):
    """Validate an optional frozen policy against its plan and original pins.

    Reads no files. Returns the two observable paths, or () without a policy.
    """
    if 'post_input_policy' not in config:
        return ()
    policy = config['post_input_policy']
    require(isinstance(policy, dict) and set(policy) == {'profile', 'job', 'indexes', 'dependency_membership'}
            and policy['profile'] == PROFILE, 'unsupported post-input policy')
    jobs = config['plan']
    require('plan_recipe' not in config and len(jobs) == 1,
            'post-input policy is limited to one scoped-app job; multi-job and mixed plans are refused')
    job = jobs[0]
    recipe = job.get('result_recipe')
    require(isinstance(recipe, dict) and recipe.get('profile') == SCOPED_PROFILE and job['api'] == 36
            and job['standalone'] is False and policy['job'] == job['label'],
            'post-input policy must bind its single scoped-app API36 job')
    roots, paths = reviewed()
    pins = config['input_pins']
    rows = policy['indexes']
    require(isinstance(rows, list) and len(rows) == len(paths)
            and all(isinstance(row, dict) and set(row) == {'path', 'sha256', 'bytes', 'device', 'inode'} for row in rows),
            'exactly two complete declared index records required')
    for row, path in zip(rows, paths):
        candidate = row['path']
        require(isinstance(candidate, str), 'declared index path must be text')
        require(not re.search(r'[*?\[\]{}]', candidate), 'wildcard post-input paths are refused: '+candidate)
        require(candidate == path, 'only the two reviewed Gradle module indexes, in order, may be observed: '+candidate)
        require(isinstance(row['sha256'], str) and re.fullmatch('[0-9a-f]{64}', row['sha256'])
                and pins.get(path) == row['sha256'], 'post-input policy is stale for the original index pin: '+path)
        require(all(type(row[key]) is int and row[key] > 0 for key in ['bytes', 'device', 'inode'])
                and row['bytes'] <= INDEX_LIMIT, 'bounded declared index size and file identity required: '+path)
    require(len({(row['device'], row['inode']) for row in rows}) == len(rows), 'aliased declared indexes')
    membership = policy['dependency_membership']
    require(isinstance(membership, dict) and set(membership) == {'roots', 'members', 'sha256'}
            and membership['roots'] == roots and type(membership['members']) is int
            and 0 < membership['members'] <= ENTRY_LIMIT and isinstance(membership['sha256'], str)
            and re.fullmatch('[0-9a-f]{64}', membership['sha256']),
            'exact declared dependency membership required')
    members = _members(pins, roots)
    require(len(members) == membership['members'] and digest(members) == membership['sha256'],
            'declared dependency membership differs from the original pins')
    return tuple(paths)


def _unaliased(path):
    for part in [Path(path), *Path(path).parents]:
        require(not part.is_symlink(), 'symlinked post-input path: '+str(part))


def _regular(path):
    """One bounded unaliased regular file at the exact path. Links are never followed."""
    _unaliased(path)
    try:
        info = os.lstat(path)
    except FileNotFoundError:
        raise ValueError('missing declared index: '+path) from None
    require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1,
            'declared index must be one unaliased regular file: '+path)
    require(0 < info.st_size <= INDEX_LIMIT, 'empty or oversized declared index: '+path)
    return info


def _identity(info):
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns


def before(config):
    """Opted-in startup and helper checks, after every original pin compared equal."""
    declared(config)
    for row in config['post_input_policy']['indexes']:
        info = _regular(row['path'])
        require((info.st_dev, info.st_ino, info.st_size) == (row['device'], row['inode'], row['bytes']),
                'declared index identity differs from the frozen policy: '+row['path'])
    membership(config)


def _scan(roots):
    """Bounded walk that never follows links. Every member must be a regular file."""
    found, pending, entries = [], list(roots), 0
    while pending:
        directory = pending.pop()
        require(stat.S_ISDIR(os.lstat(directory).st_mode), 'dependency directory must not be a link: '+directory)
        with os.scandir(directory) as listing:
            for entry in listing:
                entries += 1
                require(entries <= ENTRY_LIMIT, 'unbounded dependency membership scan')
                mode = entry.stat(follow_symlinks=False).st_mode
                if stat.S_ISDIR(mode):
                    pending.append(entry.path)
                else:
                    require(stat.S_ISREG(mode), 'dependency member must be a regular file: '+entry.path)
                    found.append(entry.path)
    return sorted(found)


def membership(config):
    """Exact endpoint membership, checked explicitly: flat pins never see additions."""
    roots, _ = reviewed()
    expected = _members(config['input_pins'], roots)
    try:
        _unaliased(MODULES)
        with os.scandir(MODULES) as listing:
            present = sorted(entry.name for entry in listing if entry.name.startswith(('files-', 'metadata-')))
        found = _scan(roots)
    except FileNotFoundError as error:
        missing = error.filename or str(MODULES)
        raise ValueError('missing dependency membership path: '+str(missing)) from None
    require(present == sorted(ROOTS), 'unreviewed Gradle dependency root present: '+', '.join(present))
    added, removed = sorted(set(found)-set(expected)), sorted(set(expected)-set(found))
    require(not added and not removed,
            f'dependency membership changed: {len(added)} added {added[:3]}, {len(removed)} removed {removed[:3]}')
    return {'roots': roots, 'members': len(found), 'sha256': digest(found)}


def _observe(row):
    """Bounded stable after-state of one declared index. Never writes."""
    _, sha = _primitives()
    path = row['path']
    require(path in reviewed()[1], 'only the two reviewed Gradle module indexes may be observed: '+str(path))
    first = _regular(path)
    require((first.st_dev, first.st_ino) == (row['device'], row['inode']),
            'declared index file identity changed: '+path)
    value = sha(path)
    last = _regular(path)
    require(_identity(first) == _identity(last), 'declared index changed while observed: '+path)
    return {'sha256': value, 'bytes': last.st_size, 'device': last.st_dev, 'inode': last.st_ino,
            'mtime_ns': last.st_mtime_ns, 'ctime_ns': last.st_ctime_ns}


def _evidence_complete(config, observed, evidence):
    """Refusal-time capture is complete only when both indexes were actually read."""
    try:
        expected = [row['path'] for row in config['post_input_policy']['indexes']]
    except (KeyError, TypeError, AttributeError):
        return False
    indexes = evidence.get('indexes') if isinstance(evidence, dict) else None
    return (isinstance(evidence, dict) and isinstance(observed, list) and isinstance(indexes, list)
            and evidence.get('profile') == PROFILE and evidence.get('status') == 'refused'
            and evidence.get('policy_sha256') == policy_sha256(config)
            and evidence.get('configuration_sha256') == config.get('_sha256')
            and evidence.get('before_source') == BEFORE and evidence.get('after_source') == AFTER
            and isinstance(evidence.get('observed_at'), str) and evidence.get('observed_at')
            and len(indexes) == len(observed) == len(expected) == 2
            and [row.get('after') for row in indexes] == list(observed)
            and all(isinstance(row, dict) and {'path', 'changed', 'before', 'after'} <= set(row)
                    and type(row.get('changed')) is bool and row['path'] == expected[i]
                    for i, row in enumerate(indexes)))


def observe(config, evidence):
    """First after-observation of both declared indexes, kept even on refusal."""
    require(isinstance(evidence, dict), 'incomplete post-input evidence')
    declared(config)
    require(isinstance(config.get('_sha256'), str) and re.fullmatch('[0-9a-f]{64}', config['_sha256']),
            'post-input observation requires the frozen configuration identity')
    policy = config['post_input_policy']
    evidence.update(profile=PROFILE, status='refused', policy_sha256=policy_sha256(config),
                    configuration_sha256=config['_sha256'], before_source=BEFORE, after_source=AFTER,
                    observed_at=datetime.now(timezone.utc).isoformat(), indexes=[])
    observed = []
    for row in policy['indexes']:
        after = _observe(row)
        evidence['indexes'].append({'path': row['path'], 'changed': after['sha256'] != row['sha256'],
                                    'before': {key: row[key] for key in ['sha256', 'bytes', 'device', 'inode']},
                                    'after': after})
        observed.append(after)
    return observed


def confirm(config, observed, evidence):
    """Exact endpoint membership and an unchanged second observation of each index.

    Status stays refused, with no confirmed_at, when this returns by exception.
    Those fields are the values captured at refusal time. accepted plus
    confirmed_at is the postclosure observation and is not a product result.
    """
    require(_evidence_complete(config, observed, evidence), 'incomplete post-input evidence')
    declared(config)
    evidence['dependency_membership'] = membership(config)
    require([_observe(row) for row in config['post_input_policy']['indexes']] == list(observed),
            'declared index changed during post validation')
    evidence.update(confirmed_at=datetime.now(timezone.utc).isoformat(), original_pins=len(config['input_pins']),
                    strict_pins=len(config['input_pins'])-len(observed), not_inferred=list(NOT_INFERRED),
                    status='accepted')
