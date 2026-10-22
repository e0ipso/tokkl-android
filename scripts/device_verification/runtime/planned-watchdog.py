from pathlib import Path
import json,os,signal,subprocess,sys,time
p=Path(sys.argv[1]);d=json.loads(p.read_text());forced='--force' in sys.argv
if d.get('phase') not in ['applying','applied','testing']:sys.exit(0)
if not forced and time.time()-d.get('heartbeat',time.time())<=90:sys.exit(0)
b=p.parent;log=b/(p.stem+'-watchdog.log')
with log.open('a') as out:
 out.write(json.dumps({'time':time.time(),'forced':forced,'owned_scope':d.get('owned_scope'),'controller_pid':d.get('controller_pid')})+'\n');out.flush()
 # Freeze the controller first so it cannot launch the next workload while rollback runs.
 pid=d.get('controller_pid')
 if pid:
  try:os.kill(pid,signal.SIGSTOP)
  except ProcessLookupError:pass
 scope=d.get('owned_scope')
 if scope:
  subprocess.run(['systemctl','--user','kill','--kill-whom=all','--signal=TERM',scope],stdout=out,stderr=subprocess.STDOUT)
  deadline=time.monotonic()+45
  while time.monotonic()<deadline:
   r=subprocess.run(['systemctl','--user','is-active',scope],capture_output=True,text=True)
   if r.stdout.strip() not in ['active','activating','deactivating']:break
   time.sleep(1)
  else:subprocess.run(['systemctl','--user','kill','--kill-whom=all','--signal=KILL',scope],stdout=out,stderr=subprocess.STDOUT)
 r=subprocess.run(['/usr/bin/python3',str(b/'window-rollback.py'),str(p)],stdout=out,stderr=subprocess.STDOUT)
 if pid:
  try:os.kill(pid,signal.SIGKILL)
  except ProcessLookupError:pass
 sys.exit(r.returncode)
