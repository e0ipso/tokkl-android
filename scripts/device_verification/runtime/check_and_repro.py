"""One fixed host sequence inside the existing gated, monitored job envelope.

No configurable argv, shell, timeouts or resource operations. This module is
copied and hashed with the frozen runtime; imports never execute a workload.
"""
from pathlib import Path
import json
import os
import re
import subprocess
import sys
import time

PROFILE = 'assemble-check-then-repro-v1'
LABEL = 'assemble-check'
SECONDS = 1800
COMMANDS = (
    ('assemble-check', ('./gradlew', '--no-daemon', 'assembleDebug', 'check', '--stacktrace')),
    ('reproducible-release', ('env', 'GRADLE_OPTS=-Dorg.gradle.workers.max=1', 'scripts/reproducible-release.sh')),
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def argv(directory):
    return ['/usr/bin/python3', '-B', str(Path(directory)/'check_and_repro.py'), '--execute']


def validate_grouped_job(config, job):
    if 'host_recipe' not in job:
        return False
    jobs = config['plan']
    require(job['host_recipe'] == PROFILE and len(jobs) == 2 and job is jobs[1]
            and 'plan_recipe' not in config and jobs[0].get('standalone') is False
            and job.get('standalone') is True and job.get('api') == 0
            and job.get('label') == LABEL and job.get('seconds') == SECONDS
            and job.get('args') == argv(config['run_dir']) and 'result_recipe' not in job,
            'unsupported finite check-and-repro recipe')
    return True


def binding(directory):
    # Imported lazily to avoid a configuration/recipe import cycle.
    from configuration import adopted_configuration, load
    from scope_completion import process_identity
    adoption = os.environ.get('TOKKL_VERIFICATION_ADOPTION')
    require(adoption, 'grouped host runner requires coordinator adoption')
    config = adopted_configuration(directory, adoption)
    manifest = load(directory/'runtime-manifest.json')
    require({'check_and_repro.py', 'configuration.py', 'scoped_results.py', 'scope_completion.py'} <= manifest.keys(),
            'grouped host runtime sources must be adopted')
    require(validate_grouped_job(config, config['plan'][-1]), 'grouped job missing')
    require(Path.cwd() == Path(config['project']), 'grouped host cwd differs from frozen project')
    expected = dict(config['environment'], PATH=str(directory/'shim')+':'+config['environment']['PATH'],
                    MEM_GATE_CGROUP_MB='5000', MEM_GATE_WAIT_S='300', TMPDIR=str(directory/'scratch'),
                    TOKKL_TEST_SLOTS='1', TOKKL_TEST_AVD='tokkl-api0')
    require(all(os.environ.get(key) == value for key, value in expected.items()),
            'grouped host environment differs from original job envelope')
    session = Path(os.environ.get('TOKKL_SCOPE_SESSION', ''))
    require(session.is_absolute() and session.resolve() == session and session.parent == directory
            and re.fullmatch('completion-'+LABEL+r'-[0-9]{14}', session.name),
            'grouped host runner requires its owned Session')
    request = load(session/'request.json')
    unit = 'tokkl-0273-'+LABEL+'-'+session.name.rsplit('-', 1)[-1]+'.scope'
    require(request['main_unit'] == unit and request['command_seconds'] == SECONDS+35
            and request['app'] == '/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice',
            'grouped host Session differs from finite job')
    registration = load(session/'scopes'/unit/'registered.json')
    scope = str(Path(request['app'])/unit)
    identity = process_identity(os.getpid())
    require(registration['nonce'] == request['nonce'] and registration['unit'] == unit
            and registration['scope'] == scope and identity is not None and identity['cgroup'] == scope,
            'grouped host process is outside its registered scope')
    return config, session


def run_sequence(session):
    """Fixed children inherit cwd/env/output/process group and the ONE outer timeout."""
    from scope_completion import child_exit
    path = session/'check-and-repro.json'
    # Exclusive initial receipt refuses replay. Later atomic writes retain actual
    # returncodes and leave an interrupted/running command explicitly incomplete.
    receipt = {'profile': PROFILE, 'commands': [dict(label=label, argv=list(command), status='not_run',
                returncode=None) for label, command in COMMANDS], 'exit': None}
    with path.open('x') as out:
        json.dump(receipt, out)
        out.flush()
        os.fsync(out.fileno())
    def record():
        pending = path.with_suffix('.writing')
        with pending.open('x') as out:
            json.dump(receipt, out)
            out.flush()
            os.fsync(out.fileno())
        os.replace(pending, path)
        fd = os.open(session, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
        print(json.dumps({'grouped_host_receipt': str(path), **receipt}), flush=True)
    record()
    for row in receipt['commands']:
        row.update(status='running', started_monotonic=time.monotonic())
        record()
        try:
            result = subprocess.run(row['argv'], check=False)
        except OSError as error:
            row.update(status='launch_failed', error=repr(error))
            receipt['exit'] = 125
            record()
            return 125
        row.update(status='exited', returncode=result.returncode, ended_monotonic=time.monotonic())
        status = child_exit(result.returncode)
        if status:
            receipt['exit'] = status
            record()
            return status
        record()
    receipt['exit'] = 0
    record()
    return 0


def main():
    require(sys.argv[1:] == ['--execute'], 'only the exact finite runner invocation is accepted')
    directory = Path(__file__).resolve().parent
    _, session = binding(directory)
    return run_sequence(session)


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, TypeError) as error:
        print('grouped host runner refused: '+str(error), file=sys.stderr)
        sys.exit(125)
