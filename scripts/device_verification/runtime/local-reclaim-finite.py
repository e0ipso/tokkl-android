from pathlib import Path
import datetime,fcntl,json,os,subprocess,sys,time
base=Path(__file__).resolve().parent; app=Path('/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice')
from preparation_policy import helper_request,reserve_write
request=helper_request(base,sys.argv[1:])
if request['write']:
 reserve_write(request)
 fd=os.open(app/'memory.reclaim',os.O_WRONLY)
 try:os.write(fd,(sys.argv[2]+' swappiness=0').encode())
 finally:os.close(fd)
 sys.exit(0)
amount=request['amount']
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'); dest=base/('local-reclaim-'+stamp+'.json');raw=dest.with_suffix('.log')
def snap():
 stat={k:int(v) for k,v in (x.split() for x in (app/'memory.stat').read_text().splitlines())}
 return {'time':datetime.datetime.now(datetime.timezone.utc).isoformat(),'current':int((app/'memory.current').read_text()),'stat':stat,'swap':int((app/'memory.swap.current').read_text()),'events':{k:int(v) for k,v in (x.split() for x in (app/'memory.events').read_text().splitlines())},'local_events':{k:int(v) for k,v in (x.split() for x in (app/'memory.events.local').read_text().splitlines())},'pressure':(app/'memory.pressure').read_text(),'host_available':int(next(x.split()[1] for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:')))*1024,'limits':{n:(app/n).read_text().strip() for n in ['memory.high','memory.max','memory.swap.max']}}
def unsafe(a,b):
 reasons=[]
 if any(b[kind][k]>a[kind][k] for kind in ['events','local_events'] for k in ['high','max','oom','oom_kill']):reasons.append('local or hierarchical memory event increased')
 if b['swap']>a['swap']:reasons.append('swap increased')
 if abs(b['stat']['anon']-a['stat']['anon'])>67108864:reasons.append('anonymous delta exceeded 64MiB helper/concurrency allowance')
 return reasons
lock=open('/run/user/1000/tokkl-android-mem-gate.lock','a');fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
s=(app/'memory.reclaim').stat();assert s.st_uid==1000 and s.st_gid==1001 and (s.st_mode&0o777)==0o200 and os.access(app/'memory.reclaim',os.W_OK)
before=snap();assert before['limits'] in [{'memory.high':'10737418240','memory.max':'12884901888','memory.swap.max':'2147483648'},{'memory.high':'15032385536','memory.max':'16106127360','memory.swap.max':'2147483648'}]
assert before['stat']['inactive_file']>=amount,'inactive file cache does not cover requested write'
cmd=['/usr/bin/python3',str(Path(__file__).resolve()),'--write',str(amount),'--state',str(request['state_path']),'--preparation',str(request['preparation'])];record={'command':cmd,'target':str(app/'memory.reclaim'),'payload':str(amount)+' swappiness=0','requested':amount,'before':before,'samples':[]};start=time.monotonic();reason=[]
with raw.open('w') as out:
 p=subprocess.Popen(cmd,stdout=out,stderr=subprocess.STDOUT)
 while p.poll() is None:
  row=snap();record['samples'].append(row);reason=unsafe(before,row)
  if reason or time.monotonic()-start>20:
   if not reason:reason=['20 second runtime bound']
   p.terminate()
   try:p.wait(timeout=3)
   except subprocess.TimeoutExpired:p.kill();p.wait(timeout=3)
   break
  time.sleep(.2)
 rc=p.wait()
time.sleep(.5);after=snap();reason+=unsafe(before,after);record.update(exit=rc,elapsed_seconds=time.monotonic()-start,after=after,stop_reasons=sorted(set(reason)),actual_current_reduction=before['current']-after['current'],raw_log=str(raw));dest.write_text(json.dumps(record,indent=2)+'\n');print(json.dumps({'evidence':str(dest),'exit':rc,'requested':amount,'actual_current_reduction':record['actual_current_reduction'],'before_current':before['current'],'after_current':after['current'],'anon_delta':after['stat']['anon']-before['stat']['anon'],'file_delta':after['stat']['file']-before['stat']['file'],'shmem_delta':after['stat']['shmem']-before['stat']['shmem'],'swap_delta':after['swap']-before['swap'],'host_available':after['host_available'],'stop_reasons':record['stop_reasons']}));sys.exit(rc if rc else 76 if reason else 0)
