#!/usr/bin/env python3
"""Explicitly authorized <=10 minute owned live fixture suite, never a gate.

Real 128 MiB/swap0/choom1000 scopes, harmless payloads and real kernel sampling.
Shared-limit/reclaim/admission callbacks are modeled in the actual controller.
No Gradle, SDK, device, emulator, resource-window or reclaim operation is used.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import signal
import subprocess
import sys
import time
import uuid

sys.dont_write_bytecode = True
B = Path(__file__).resolve().parent
sys.path[:0] = [str(B/'runtime'), str(B)]
from conformance import verify
import scope_completion as sc
import event_monitor_0274 as em
import watchdog_lifetime as watch

APP = Path('/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice')
EXPECTED = {'memory.high': str(10*1024**3), 'memory.max': str(12*1024**3), 'memory.swap.max': str(2*1024**3)}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def limits():
    return {key: (APP/key).read_text().strip() for key in EXPECTED}


def wait_for(predicate, deadline, message):
    while time.monotonic() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(.1)
    raise RuntimeError(message)


def monitored_sample(path, capture, inspect):
    """Persist the actual read/assessment or exception before propagating it."""
    row = {'monotonic': time.monotonic(), 'tree': None}
    try:
        row['tree'] = capture()
        row['assessment'] = inspect(row['tree'])
    except BaseException as error:
        row['error'] = repr(error)
        raise
    finally:
        with path.open('a') as samples:
            samples.write(json.dumps(row)+'\n')
            samples.flush()
            os.fsync(samples.fileno())
    return row['tree'], row['assessment']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute-owned-fixtures', action='store_true', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cases', nargs='*', choices=['success', 'nonzero', 'capture', 'unknown-disappearance'],
                        default=['success', 'nonzero', 'capture', 'unknown-disappearance'])
    parser.add_argument('--watchdog-scope', choices=['global','owned'], default='global')
    args = parser.parse_args()
    require(args.watchdog_scope!='owned' or not args.cases, 'component-only disposition forbids new global controller cases')
    require(args.output.is_absolute() and not args.output.exists() and not args.output.is_relative_to('/tmp'),
            'fresh owned disk output directory required')
    require(limits() == EXPECTED, 'shared limits must remain original; no window is allowed')
    proof = verify()
    args.output.mkdir(mode=0o700, parents=True)
    begin = time.monotonic()
    deadline = begin+570
    token = uuid.uuid4().hex[:12]
    records, units, identities = [], {}, {}
    counters_before = em.capture(APP)
    result = {'source_proof': proof, 'shared_limits_before': limits(), 'started_monotonic': begin,
              'source_sha256': {str(p.relative_to(B)): hashlib.sha256(p.read_bytes()).hexdigest()
                                for p in sorted(B.rglob('*')) if p.is_file() and '__pycache__' not in p.parts},
              'modeled': ['shared limit/window callbacks', 'reclaim/preparation callbacks', 'memory admission callback',
                          'destructive fallback body replaced with a harmless marker'],
              'forbidden_actions': ['Gradle/JVM/SDK/device/emulator', 'shared limits/reclaim writes', 'service/network changes']}

    def save():
        result.update(records=records, owned_units=units, observed_identities=list(identities.values()))
        (args.output/'suite.json').write_text(json.dumps(result, indent=2)+'\n')

    def own(unit, command):
        require(not (APP/unit).exists() and unit not in units, 'refuse an existing or duplicate owned unit')
        units[unit] = {'command': command, 'created_monotonic': time.monotonic()}
        save()

    def observe():
        for unit, row in units.items():
            directory = APP/unit
            if directory.exists():
                tree = em.capture(directory)
                row['last_kernel_sample'] = tree
                for group in [directory]+[p for p in directory.rglob('*') if p.is_dir()]:
                    try:
                        for raw in (group/'cgroup.procs').read_text().split():
                            identity = sc.process_identity(int(raw))
                            if identity:
                                identities[(identity['pid'], identity['start_ticks'])] = identity
                    except FileNotFoundError:
                        pass  # Final protocol samples, not this inventory, certify closure.
        require(limits() == EXPECTED, 'shared limit interference during harmless fixtures')

    def command(argv, log, seconds=10):
        require(time.monotonic()+seconds < deadline, 'aggregate fixture deadline exhausted')
        with log.open('w') as out:
            completed = subprocess.run(argv, stdout=out, stderr=subprocess.STDOUT, timeout=seconds)
        return completed.returncode

    error = None
    try:
        for case in args.cases:
            unit = 'tokkl-0284-host-'+case+'-'+token+'.scope'
            argv = ['systemd-run', '--user', '--scope', '--quiet', '--collect', '--unit='+unit,
                    '-p', 'OOMPolicy=continue', '-p', 'MemoryMax=128M', '-p', 'MemorySwapMax=0',
                    '-p', 'RuntimeMaxSec=60s', '--', 'choom', '-n', '1000', '--',
                    sys.executable, '-B', str(B/'tests/live_controller_case.py'), case, str(args.output)]
            own(unit, argv)
            log = args.output/(case+'.log')
            require(time.monotonic()+60 < deadline, 'insufficient bounded fixture time')
            with log.open('w') as out:
                process = subprocess.Popen(argv, stdout=out, stderr=subprocess.STDOUT)
                stop = time.monotonic()+60
                while process.poll() is None and time.monotonic() < stop:
                    # Import exact fresh ownership records even if the harness
                    # fails before producing its result. No prefix-wide cleanup.
                    for path in args.output.glob('*-'+case+'/ownership.json'):
                        for row in json.loads(path.read_text()):
                            units.setdefault(row['unit'], row)
                    observe()
                    time.sleep(.1)
                require(process.poll() is not None, 'controller fixture exceeded its finite bound')
                code = process.returncode
            results = sorted(args.output.glob('*-'+case+'/result.json'))
            require(len(results) == 1, 'missing unique actual-controller result: '+case)
            record = json.loads(results[0].read_text())
            records.append({'case': case, 'exit': code, 'result': str(results[0]),
                            'result_sha256': hashlib.sha256(results[0].read_bytes()).hexdigest(), 'log': str(log)})
            save()
            require(code == 0 and all(record['checks'].values()), 'actual-controller fixture failed: '+case)

        # Actual maintained watchdog wrapper, unchanged watchdog body with an
        # inactive fixture state, and real service cgroup across two60s pulses.
        folder = args.output/'watchdog'
        folder.mkdir()
        state = folder/'state.json'
        state.write_text(json.dumps({'phase': 'prepared', 'heartbeat': time.time()})+'\n')
        import shutil
        shutil.copy2(B/'runtime/planned-watchdog.py', folder/'planned-watchdog.py')
        shutil.copy2(B/'runtime/window-rollback.py', folder/'window-rollback.py')
        unit = 'tokkl-0284-watch-'+token
        argv = watch.service_command(unit, {'fallback_seconds': 30}, state)
        interpreter = argv.index('/usr/bin/python3')
        argv[interpreter:interpreter] = ['--collect', '-p', 'MemoryMax=128M', '-p', 'MemorySwapMax=0',
                                       '-p', 'StandardOutput=append:'+str(folder/'watch.log'),
                                       '-p', 'StandardError=append:'+str(folder/'watch.log'),
                                       '--', '/usr/bin/choom', '-n', '1000', '--']
        name = unit+'-watch.service'
        own(name, argv)
        require(command(argv, folder/'arm.log') == 0, 'persistent watchdog did not start')
        directory = APP/name
        wait_for(lambda: directory.exists(), time.monotonic()+3, 'watchdog cgroup absent')
        inode = directory.stat().st_ino
        sample_app = directory if args.watchdog_scope=='owned' else APP
        capture_fn = (lambda root=directory, deadline=None: em.capture(root, deadline=deadline)) if args.watchdog_scope=='owned' else em.capture
        session = sc.Session(folder/'protocol', sample_app, 'tokkl-0284-never-started-'+token+'.scope', 180, capture_fn)
        anchor = session.capture()
        monitor = em.Monitor(anchor, sample_app, sample_app/session.main_unit,
                             sampler=lambda root, deadline: session.capture(root, deadline=deadline))
        marker = None
        if args.watchdog_scope=='global':
            # Independent real timer survives its short-lived systemd-run launcher.
            marker = folder/'fallback.json'
            timer = 'tokkl-0284-marker-'+token
            marker_code = "import json,os;from pathlib import Path;g=Path('/sys/fs/cgroup')/Path('/proc/self/cgroup').read_text().split('0::',1)[1].strip().lstrip('/');Path("+repr(str(marker))+").write_text(json.dumps({'pid':os.getpid(),'start_stat':Path('/proc/self/stat').read_text(),'cgroup':Path('/proc/self/cgroup').read_text(),'oom_score_adj':Path('/proc/self/oom_score_adj').read_text(),'final_events':(g/'memory.events').read_text(),'final_local_events':(g/'memory.events.local').read_text(),'memory_max':(g/'memory.max').read_text(),'swap_max':(g/'memory.swap.max').read_text()}))"
            fallback = ['systemd-run', '--user', '--collect', '--unit='+timer, '--on-active=3s',
                        '--timer-property=AccuracySec=1s', '-p', 'OOMPolicy=continue', '-p', 'MemoryMax=128M',
                        '-p', 'MemorySwapMax=0', '--', 'choom', '-n', '1000', '--', sys.executable, '-c', marker_code]
            own(timer+'.service', fallback)
            units[timer+'.timer'] = {'command': fallback, 'created_monotonic': time.monotonic()}
            require(command(fallback, folder/'arm-fallback.log') == 0, 'harmless independent timer did not arm')
            wait_for(lambda: marker.exists(), time.monotonic()+6, 'independent timer marker absent')
            fallback_record = json.loads(marker.read_text())
            require(fallback_record['oom_score_adj'].strip() == '1000', 'fallback lost OOM isolation')
            require(fallback_record['memory_max'].strip() == str(128*1024**2) and fallback_record['swap_max'].strip() == '0', 'fallback resource envelope changed')
            require(all(int(line.split()[1]) == 0 for line in fallback_record['final_events'].splitlines()
                        if line.split()[0] in ['high','max','oom','oom_kill','oom_group_kill']), 'fallback resource event')
            ticks=int(fallback_record['start_stat'].rsplit(')',1)[1].split()[19])
            identities[(fallback_record['pid'],ticks)]={'pid':fallback_record['pid'],'start_ticks':ticks,'source':str(marker)}
            wait_for(lambda: not (APP/(timer+'.service')).exists(), time.monotonic()+5, 'marker service not closed')
            # The original pre-marker monitor anchor is never reset. Any unexplained
            # hierarchical event from the transient marker still fails attribution.
        (folder/'baseline.json').write_text(json.dumps(anchor,indent=2)+'\n')
        pulses = []
        while time.monotonic() < min(deadline-30, begin+300):
            observe()
            tree, assessment = monitored_sample(folder/'samples.jsonl', session.capture,
                lambda tree: monitor.inspect(tree, owned=[directory], host_available=int(next(
                    x.split()[1] for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:')))*1024))
            require(not assessment['abort'], 'watchdog lifecycle monitor: '+str(assessment['abort']))
            log = folder/'watch.log'
            pulses = [json.loads(line) for line in log.read_text().splitlines() if line] if log.exists() else []
            ended = [row for row in pulses if row['event'] == 'watchdog-check-finished']
            require(directory.exists() and directory.stat().st_ino == inode, 'watchdog cgroup disappeared or changed identity')
            if len(ended) >= 2:
                require(all(row['exit'] == 0 for row in ended), 'unchanged watchdog body failed')
                (folder/'final-before-owned-stop.json').write_text(json.dumps(tree, indent=2)+'\n')
                break
            time.sleep(.5)
        require(len([r for r in pulses if r['event'] == 'watchdog-check-finished']) >= 2, 'two actual60s watchdog checks not completed')
        records.append({'case': 'persistent-watchdog-owned-lifetime' if args.watchdog_scope=='owned' else 'persistent-watchdog-and-independent-fallback', 'exit': 0,
                        'inode': inode, 'checks': 2, 'final_kernel_evidence': str(folder/'final-before-owned-stop.json'),
                        'fallback_marker': str(marker) if marker else None, 'sampling_scope':str(sample_app),'global_cleanliness_claimed':False,'live_body_phase': 'prepared; destructive callback not invoked'})
        save()
    except BaseException as exc:
        error = repr(exc)
    finally:
        cleanup = []
        # Only exact owned identities from this fresh invocation are eligible.
        for unit in reversed(list(units)):
            try:
                with (args.output/'cleanup.log').open('a') as out:
                    rc = subprocess.run(['systemctl', '--user', 'stop', unit], stdout=out, stderr=subprocess.STDOUT, timeout=3).returncode
                cleanup.append({'unit': unit, 'stop_exit': rc})
            except subprocess.TimeoutExpired:
                cleanup.append({'unit': unit, 'stop_exit': 124})
        end = min(time.monotonic()+10, deadline+25)
        while time.monotonic() < end and any((APP/u).exists() for u in units):
            time.sleep(.1)
        # Include payload and holder identities captured by the actual protocol.
        for path in args.output.glob('*/completion-*/scopes/*/*.json'):
            try:
                row = json.loads(path.read_text())
                for pid_key, ticks_key in [('pid', 'start_ticks'), ('holder_pid', 'holder_start_ticks'), ('payload_pid', 'payload_start_ticks')]:
                    if pid_key in row and ticks_key in row:
                        identities[(row[pid_key], row[ticks_key])] = {'pid': row[pid_key], 'start_ticks': row[ticks_key], 'source': str(path)}
            except (OSError, ValueError):
                pass
        remaining = []
        for identity in identities.values():
            fresh = sc.process_identity(identity['pid'])
            if fresh and fresh['state'] != 'Z' and fresh['start_ticks'] == identity['start_ticks']:
                remaining.append(fresh)
        result.update(error=error, cleanup=cleanup, scopes_absent=not any((APP/u).exists() for u in units),
                      remaining_owned_identities=remaining, shared_limits_after=limits(),
                      elapsed_seconds=time.monotonic()-begin, counters_before=counters_before,
                      counters_after=em.capture(APP), no_resource_mutation=True)
        result['passed'] = error is None and result['scopes_absent'] and not remaining and limits() == EXPECTED and result['elapsed_seconds'] <= 600
        save()
    print(json.dumps({'passed': result['passed'], 'error': error, 'elapsed_seconds': result['elapsed_seconds'],
                      'records': len(records), 'watchdog_scope':args.watchdog_scope,'output': str(args.output), 'scopes_absent': result['scopes_absent']}))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
