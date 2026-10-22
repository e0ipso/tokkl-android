"""Keep the unchanged watchdog check in one finite service cgroup.

Periodic oneshots remove/recreate their cgroup while the Session samples the
whole tree. A sleeping parent retains that evidence until controller cleanup.
The independent forced fallback timer remains unchanged.
"""
from pathlib import Path
import argparse,json,subprocess,sys,time
INTERVAL_SECONDS=60
CLEANUP_MARGIN_SECONDS=120
MAX_LIFETIME_SECONDS=86400

def service_command(unit,bounds,state_path):
    fallback=bounds['fallback_seconds']
    if type(fallback) is not int or not 0<fallback<=MAX_LIFETIME_SECONDS-CLEANUP_MARGIN_SECONDS:
        raise ValueError('invalid finite watchdog duration')
    lifetime=fallback+CLEANUP_MARGIN_SECONDS
    state_path=Path(state_path)
    if not state_path.is_absolute():raise ValueError('watchdog state path must be absolute')
    return ['systemd-run','--user','--unit='+unit+'-watch','--property=Type=exec',
        '--property=OOMPolicy=continue','--property=RuntimeMaxSec='+str(lifetime)+'s',
        '/usr/bin/python3',str(Path(__file__).resolve()),'--state',str(state_path),'--seconds',str(lifetime)]

def run(state_path,seconds,*,clock=time.monotonic,sleep=time.sleep,invoke=None,report=None):
    if type(seconds) is not int or not 0<seconds<=MAX_LIFETIME_SECONDS:raise ValueError('invalid finite watchdog lifetime')
    state_path=Path(state_path)
    if not state_path.is_absolute():raise ValueError('watchdog state path must be absolute')
    script=state_path.parent/'planned-watchdog.py'
    if invoke is None:
        def invoke(remaining):
            return subprocess.run(['/usr/bin/python3',str(script),str(state_path)],timeout=remaining).returncode
    if report is None:report=lambda record:print(json.dumps(record),flush=True)
    start=clock();deadline=start+seconds;due=start+INTERVAL_SECONDS
    report({'event':'watchdog-service-started','monotonic':start,'deadline':deadline})
    while due<deadline:
        sleep(max(0,due-clock()))
        remaining=deadline-clock()
        if remaining<=0:break
        report({'event':'watchdog-check-start','monotonic':clock()})
        try:code=invoke(remaining)
        except subprocess.TimeoutExpired:
            report({'event':'watchdog-check-timeout','monotonic':clock()});return 124
        report({'event':'watchdog-check-finished','monotonic':clock(),'exit':code})
        if code:return code
        due=max(due+INTERVAL_SECONDS,clock())
    sleep(max(0,deadline-clock()))
    report({'event':'watchdog-lifetime-exhausted','monotonic':clock()})
    return 124

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--state',type=Path,required=True);parser.add_argument('--seconds',type=int,required=True)
    args=parser.parse_args();raise SystemExit(run(args.state,args.seconds))
