from pathlib import Path
from datetime import datetime,timezone
import fcntl,json,os,subprocess,sys
state_path=Path(sys.argv[1]);lock=state_path.with_suffix('.lock');app=Path('/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice');original={'memory.high':'10737418240','memory.max':'12884901888','memory.swap.max':'2147483648'};temporary={'memory.high':'15032385536','memory.max':'16106127360','memory.swap.max':'2147483648'}
def read():return {k:(app/k).read_text().strip() for k in original}
def save(d):
 p=state_path.with_suffix('.writing');p.write_text(json.dumps(d,indent=2)+'\n')
 with p.open('rb') as f:os.fsync(f.fileno())
 os.replace(p,state_path)
with lock.open('a') as f:
 fcntl.flock(f,fcntl.LOCK_EX);d=json.loads(state_path.read_text());current=read();event={'time':datetime.now(timezone.utc).isoformat(),'entry':current,'caller':os.environ.get('INVOCATION_ID','normal-controller')}
 if current==original:event['outcome']='already_original';status=0
 elif current==temporary and d.get('phase') in ['applying','applied','testing']:
  cmd=['/usr/bin/systemctl','--user','set-property','--runtime','app.slice','MemoryHigh=10G','MemoryMax=12G'];r=subprocess.run(cmd,capture_output=True,text=True);event.update(command=cmd,exit=r.returncode,stdout=r.stdout,stderr=r.stderr,after=read());status=0 if r.returncode==0 and event['after']==original else 74;event['outcome']='restored' if status==0 else 'restore_failed'
 else:event['outcome']='interference_no_overwrite';status=73
 d.setdefault('rollback_events',[]).append(event)
 if status==0:d['phase']='restored'
 save(d);print(json.dumps(event));sys.exit(status)
