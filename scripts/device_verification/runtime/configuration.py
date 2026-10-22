"""Read-only validation for the single frozen per-run JSON configuration.

Policy limits are deliberately not configuration knobs. Loading this module does
not execute a controller, start a scope, or write a cgroup or configuration file.
"""
import argparse
import ctypes
import errno
import hashlib
import json
import mmap
import os
from pathlib import Path
import re
import stat
import struct
if __package__:
    from .scoped_results import validate_recipe, validate_plan, COMPILE_ARGS, COMPILE_FIRST
else:
    from scoped_results import validate_recipe, validate_plan, COMPILE_ARGS, COMPILE_FIRST

MAX_BUDGET = 6 * 1024**3
CANONICAL_GATE = '/home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain/mem-gate.sh'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def _direct_alignment(fd):
    """Linux statx DIOALIGN, queried on the opened file, never guessed."""
    libc = ctypes.CDLL(None, use_errno=True)
    statx = getattr(libc, 'statx', None)
    if statx is None:
        raise OSError(errno.ENOTSUP, 'statx DIOALIGN is required for input hashing')
    result = ctypes.create_string_buffer(256)
    # struct statx is a fixed 256-byte Linux ABI. DIOALIGN fields are u32 at
    # offsets 152/156; AT_EMPTY_PATH binds the query to this descriptor.
    if statx(fd, b'', 0x1000, 0x2000, result):
        error = ctypes.get_errno()
        raise OSError(error, 'statx DIOALIGN failed')
    mask = struct.unpack_from('=I', result, 0)[0]
    memory, offset = struct.unpack_from('=II', result, 152)
    if not mask & 0x2000 or any(x <= 0 or x > 65536 or x & (x-1) for x in (memory, offset)):
        raise OSError(errno.ENOTSUP, 'supported bounded direct I/O alignment required')
    # Scope the guarantee to the ext4 host path tested here. Other filesystems
    # can advertise DIO alignment yet fall back to buffered I/O for some extents.
    filesystem = ctypes.create_string_buffer(256)
    if libc.fstatfs(fd, filesystem):
        error = ctypes.get_errno()
        raise OSError(error, 'input hashing fstatfs failed')
    if ctypes.c_long.from_buffer(filesystem).value != 0xEF53:
        raise OSError(errno.ENOTSUP, 'cache-neutral hashing requires ext4 with DIOALIGN')
    return memory, offset


def _identity(info):
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns


def sha(path):
    """Hash every byte using read-only direct I/O, without filling file cache.

    Unsupported files/alignment fail closed. A 64 KiB transfer plus at most
    64 KiB alignment padding bounds allocation. Existing cached pages remain.
    """
    if not hasattr(os, 'O_DIRECT') or not hasattr(os, 'preadv'):
        raise OSError(errno.ENOTSUP, 'direct I/O is required for input hashing')
    fd = os.open(path, os.O_RDONLY | os.O_DIRECT | os.O_NONBLOCK | os.O_CLOEXEC)
    try:
        before = os.fstat(fd)
        require(stat.S_ISREG(before.st_mode), 'input hash requires a regular file')
        memory, alignment = _direct_alignment(fd)
        digest = hashlib.sha256()
        with mmap.mmap(-1, 65536 + memory) as buffer:
            address = ctypes.addressof(ctypes.c_char.from_buffer(buffer))
            start = (-address) % memory
            view = memoryview(buffer)[start:start+65536]
            try:
                position = 0
                while position < before.st_size:
                    count = os.preadv(fd, [view], position)
                    remaining = before.st_size - position
                    if count <= 0 or count > min(65536, remaining):
                        raise OSError(errno.EIO, 'input hash truncated or grew during direct read')
                    digest.update(view[:count])
                    position += count
                    if position < before.st_size and count % alignment:
                        raise OSError(errno.EIO, 'unaligned short direct read before EOF')
            finally:
                view.release()
        require(_identity(before) == _identity(os.fstat(fd)) == _identity(os.stat(path)),
                'input changed during hashing: '+str(path))
        return digest.hexdigest()
    finally:
        os.close(fd)


def load(path):
    return json.loads(Path(path).read_text())


def validate(config, *, fresh=True):
    require(config.get('schema_version') == 1, 'unsupported configuration schema')
    require(config.get('stage') == 'development', 'this candidate supports API36 development only; API31 stays deferred')
    for key in ['run_id', 'lease_id', 'budget_authorization']:
        require(isinstance(config.get(key), str) and config[key].strip(), 'missing '+key)
    require(re.fullmatch(r'[a-z0-9][a-z0-9-]{0,47}', config['run_id']), 'unsafe run identity')
    for key in ['project', 'run_dir', 'gate']:
        require(isinstance(config.get(key), str) and Path(config[key]).is_absolute()
                and Path(config[key]).resolve() == Path(config[key]), 'absolute canonical '+key+' required')
    base, project = Path(config['run_dir']), Path(config['project'])
    require(config['gate'] == CANONICAL_GATE, 'shared admission gate must remain unchanged')
    require(project.is_dir(), 'checked project missing')
    require(not base.is_relative_to(Path('/tmp')) and not project.is_relative_to(base), 'unsafe output location')
    require(not base.is_relative_to(project), 'run evidence must be outside checked project')
    if fresh:
        require(not base.exists(), 'fresh unused run directory required; never overwrite a historical ledger')
    require(re.fullmatch(r'[0-9a-f]{40}', config.get('checked_head', '')), 'full checked commit required')
    environment = config.get('environment')
    require(isinstance(environment, dict) and set(environment) == {'PATH', 'JAVA_HOME', 'ANDROID_HOME'},
            'explicit PATH/JAVA_HOME/ANDROID_HOME only')
    require(all(isinstance(v, str) and v and '\n' not in v for v in environment.values()), 'invalid environment')
    require(all(Path(p).is_absolute() for p in environment['PATH'].split(':')), 'PATH must contain absolute directories only')
    for key in ['JAVA_HOME', 'ANDROID_HOME']:
        require(Path(environment[key]).is_absolute(), 'absolute tool path required')
    for key in ['reclaim_budget_bytes', 'carried_reclaim_bytes']:
        require(type(config.get(key)) is int and 0 <= config[key] <= MAX_BUDGET, 'invalid finite '+key)
    require(config['carried_reclaim_bytes'] <= config['reclaim_budget_bytes'], 'carried reservation exceeds authorized budget')
    policy = config.get('preparation_policy', {'max_request_bytes': 0, 'max_preparations': 4})
    require(isinstance(policy, dict) and set(policy) == {'max_request_bytes', 'max_preparations'},
            'explicit bounded preparation policy required')
    require(type(policy['max_request_bytes']) is int
            and 0 <= policy['max_request_bytes'] <= config['reclaim_budget_bytes'],
            'request bound must fit the authorized cumulative budget; zero disables writes')
    require(type(policy['max_preparations']) is int and 1 <= policy['max_preparations'] <= 4,
            'one to four distinct preparations required')
    require(config['reclaim_budget_bytes'] == 0 or policy['max_request_bytes'] > 0,
            'nonzero budget requires an explicit preparation request cap')
    require('prior_window' in config, 'explicit prior_window required; null only for a newly authorized budget')
    if config['prior_window'] is not None:
        prior = Path(config['prior_window'])
        require(prior.is_absolute() and prior.is_file(), 'prior reservation ledger missing')
        require(sha(prior) == config.get('prior_window_sha256'), 'prior ledger changed')
        require(config['carried_reclaim_bytes'] == load(prior)['reclaim_reserved_bytes'],
                'carried reservation must match prior ledger, never reset on retry')
    else:
        require(config['carried_reclaim_bytes'] == 0, 'nonzero carry requires prior ledger')
    jobs = config.get('plan')
    require(isinstance(jobs, list) and 0 < len(jobs) <= 4, 'one to four finite sequential jobs required')
    require(len(jobs) <= policy['max_preparations'], 'plan exceeds preparation count allowance')
    validate_plan(config)
    labels = set()
    for job in jobs:
        require(re.fullmatch('[a-z][a-z0-9-]{0,47}', job.get('label', '')), 'unsafe job label')
        require(job['label'] not in labels, 'duplicate job labels/preparations prohibited')
        labels.add(job['label'])
        require(type(job.get('seconds')) is int and 0 < job['seconds'] <= 7200, 'finite job timeout required')
        require(type(job.get('standalone')) is bool, 'explicit standalone boolean required')
        args = job.get('args')
        require(isinstance(args, list) and args and all(isinstance(x, str) and x and '\n' not in x for x in args), 'argv list required')
        require('--stop' not in args, 'stopping another Gradle daemon is prohibited')
        if not job['standalone']:
            require(job['api'] == 36 and args[0] == 'scripts/instrumented-tests.sh', 'device jobs must use authorized script on API36')
            if 'result_recipe' in job:
                require(isinstance(job['result_recipe'], dict), 'explicit scoped recipe required')
            else:
                require(args == ['scripts/instrumented-tests.sh', ':macrobenchmark:connectedBenchmarkAndroidTest', '--continue']
                        and not job['label'].startswith('tap-scoped'),
                        'undeclared scoped command; use the explicit source-verified recipe')
                require(job['seconds'] >= 5400, 'retain the observed full-suite 90-minute deadline minimum')
        else:
            require('result_recipe' not in job, 'standalone host job cannot use device result recipe')
            require(job['api'] == 0, 'host jobs must use api zero')
            require((args == ['./gradlew', '--no-daemon', 'assembleDebug', 'check', '--stacktrace']
                     and job['label'] == 'assemble-check')
                    or (args == ['env', 'GRADLE_OPTS=-Dorg.gradle.workers.max=1', 'scripts/reproducible-release.sh']
                        and job['label'] == 'reproducible-release')
                    or (args == COMPILE_ARGS and config.get('plan_recipe') == COMPILE_FIRST and job is jobs[0]),
                    'only guarded Gradle or the established reproducibility command is accepted')
    pins = config.get('input_pins')
    require(isinstance(pins, dict) and pins, 'explicit checked input pins required')
    required = {str(project/'scripts/instrumented-tests.sh'), config['gate'],
                '/home/e0ipso/workspace/apps/tokkl-android/scripts/mem-gate.sh',
                '/home/e0ipso/workspace/apps/tokkl-android/scripts/memory-capacity.py'}
    require(required <= pins.keys(), 'launcher/shared admission inputs must be pinned')
    for path, expected in pins.items():
        require(Path(path).is_absolute() and re.fullmatch('[0-9a-f]{64}', expected), 'invalid input pin')
        require(sha(path) == expected, 'checked input changed: '+path)
    for job in jobs:
        if 'result_recipe' in job:
            validate_recipe(project, job, pins)
    launcher = (project/'scripts/instrumented-tests.sh').read_text()
    for key, variable in [('server_image', 'IMAGE'), ('proxy_image', 'PROXY_IMAGE')]:
        match = re.search(r"^readonly "+variable+r"='([^']+)'$", launcher, re.M)
        require(match and re.fullmatch(r'[^\s]+@sha256:[0-9a-f]{64}', match[1]), 'actual launcher image digest missing')
        require(config.get(key) == match[1], 'configured '+key+' differs from the actual launcher')
    require(config.get('slots') == 1, 'one sequential emulator path only')
    return dict(config, preparation_policy=policy, _sha256=hashlib.sha256(canonical(config)).hexdigest())


def adopted_configuration(directory, adoption_path):
    """Bind both controller and helper entry points to the same root adoption."""
    directory = Path(directory).resolve()
    config = validate(load(directory/'run-config.json'), fresh=False)
    require(Path(config['run_dir']) == directory, 'controller/helper must use its frozen run snapshot')
    scratch = directory/'scratch'
    require(scratch.is_dir() and not scratch.is_symlink() and scratch.stat().st_uid == os.getuid()
            and scratch.stat().st_mode & 0o777 == 0o700, 'private disk scratch directory missing or unsafe')
    manifest = load(directory/'runtime-manifest.json')
    for name, expected in manifest.items():
        require(not Path(name).is_absolute() and '..' not in Path(name).parts, 'unsafe runtime manifest path')
        require(sha(directory/name) == expected, 'frozen runtime changed: '+name)
    adoption = load(adoption_path)
    require(adoption.get('configuration_sha256') == config['_sha256']
            and adoption.get('runtime_sha256') == hashlib.sha256(canonical(manifest)).hexdigest()
            and adoption.get('lease_id') == config['lease_id'] and adoption.get('approved_by'),
            'explicit coordinator adoption must match configuration, runtime and lease')
    require(adoption.get('cheap_live_controller_fixtures_verified') is True,
            'coordinator must first verify the configured controller with cheap owned live fixtures')
    return config


def execution_configuration(controller):
    """Execution is a separate coordinator adoption, never a preflight effect."""
    parser = argparse.ArgumentParser()
    parser.add_argument('--execute', action='store_true', required=True)
    parser.add_argument('--adoption', type=Path, required=True)
    args = parser.parse_args()
    directory = Path(controller).resolve().parent
    config = adopted_configuration(directory, args.adoption)
    # Per-run environment has one source. Existing floors, ramp and wait remain
    # enforced by the unchanged gate and controller, never lowered here.
    for name in ['FIX_RUN', 'CARRIED_RECLAIM', 'PRIOR_WINDOW', 'NO_RECLAIM',
                 'MEM_GATE_MIN_MB', 'MEM_GATE_CGROUP_MB', 'MEM_GATE_WAIT_S',
                 'MEM_GATE_SETTLE_S', 'TOKKL_TEST_SLOTS', 'TOKKL_TEST_AVD', 'ANDROID_SERIAL']:
        os.environ.pop(name, None)
    os.environ['TOKKL_TEST_SLOTS'] = str(config['slots'])
    os.environ['TOKKL_VERIFICATION_ADOPTION'] = str(args.adoption.resolve())
    return config
