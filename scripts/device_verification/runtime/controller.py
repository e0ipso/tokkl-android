# Frozen TKA-0280 controller with explicit configuration. No host work on import.
import sys
if __name__ != '__main__' or '--execute' not in sys.argv:
    raise SystemExit('Execution disabled: use read-only preflight and coordinator adoption')
from configuration import execution_configuration
config = execution_configuration(__file__)
from pathlib import Path
from scope_completion import Session, EvidenceError
from event_monitor_0274 import capture,Monitor
from duration_policy import window_bounds
from datetime import datetime,timezone
import json,os,re,shutil,signal,subprocess,sys,time,xml.etree.ElementTree as ET
base=Path(__file__).resolve().parent;project=Path(config['project']);app=Path('/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice');runtime=Path(os.environ.get('XDG_RUNTIME_DIR','/run/user/1000'));gate=config['gate'];original={'memory.high':'10737418240','memory.max':'12884901888','memory.swap.max':'2147483648'};temporary={'memory.high':'15032385536','memory.max':'16106127360','memory.swap.max':'2147483648'};tag=datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S');state_path=base/('window-state-'+tag+'.json');unit='tokkl-verification-'+config['run_id']+'-'+tag;state={'final_matrix_plan_budget_bytes':config['reclaim_budget_bytes'],'project':str(project),'controller_pid':os.getpid(),'phase':'prepared','timer_unit':unit,'records':[],'tests':[]};armed=False;owned_scope=None
watchdog_armed=False
os.environ.update(config['environment'])
from controller_cleanup import install_controller
from watchdog_lifetime import service_command
install_controller(globals())
_controller_error=None
def now():return datetime.now(timezone.utc).isoformat()
def limits():return {n:(app/n).read_text().strip() for n in original}
def conditions():
 c=int((app/'memory.current').read_text());h=int(next(x.split()[1] for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:')))*1024
 return {'time':now(),'current':c,'host_available':h,'baseline_pass':c<=6442450944,'host_pass':h>=9663676416,'limits':limits()}
def save():
 p=state_path.with_suffix('.writing');p.write_text(json.dumps(state,indent=2)+'\n')
 with p.open('rb') as f:os.fsync(f.fileno())
 os.replace(p,state_path)
def command(cmd,name,refresh=False):
 global state
 log=base/(name+'-'+tag+'.log')
 with log.open('w') as out:r=subprocess.run(cmd,stdout=out,stderr=subprocess.STDOUT,cwd=project)
 if refresh:state=json.loads(state_path.read_text())
 rec={'time':now(),'command':cmd,'exit':r.returncode,'log':str(log)};state['records'].append(rec);save();return r.returncode
def preparation_snapshot():
 st={k:int(v) for k,v in (x.split() for x in (app/'memory.stat').read_text().splitlines())}
 c=conditions();c.update(swap=int((app/'memory.swap.current').read_text()),events={k:int(v) for k,v in (x.split() for x in (app/'memory.events').read_text().splitlines())},stat={k:st[k] for k in ['anon','file','shmem','inactive_file','active_file','file_dirty']});return c
state['configuration_sha256']=config['_sha256'];state['run_id']=config['run_id'];state['lease_id']=config['lease_id'];state['server_image']=config['server_image'];state['proxy_image']=config['proxy_image'];state['reclaim_budget_bytes']=config['reclaim_budget_bytes'];state['reclaim_reserved_bytes']=config['carried_reclaim_bytes'];state['preparations']=[];state['prepared_labels']=[]
once=base/(config['plan'][0]['label']+'-plan-once.json')
with once.open('x') as f:f.write(json.dumps({'controller_pid':os.getpid(),'state':str(state_path),'started':now()})+'\n')
def prepare(label,required_mb,opening=False):
 if len(state['preparations'])>=config['preparation_policy']['max_preparations'] or any(r['label']==label for r in state['preparations']):
  state['blocked']='preparation count or repeated job refused';save();return False
 before=preparation_snapshot();expected=original if opening else temporary
 rec={'label':label,'opening':opening,'before':before,'required_mb':required_mb,'write_count':0};state['preparations'].append(rec);state['heartbeat']=time.time();save()
 if before['limits']!=expected:rec['blocked']='limits differ from expected; no overwrite';save();return False
 target=6442450944 if opening else min(int(before['limits'][k]) for k in ['memory.high','memory.max'])-required_mb*1048576
 host_floor=9663676416 if opening else 5000*1048576
 deficit=max(0,before['current']-target,host_floor-before['host_available']);rec.update(target_current=target,host_floor=host_floor,deficit=deficit);save()
 if deficit and os.environ.get('NO_RECLAIM')=='1':
  rec['blocked']='repeated workload cache admission prohibited';save();return False
 if deficit:
  cap=config['preparation_policy']['max_request_bytes']
  amount=deficit+134217728
  if amount>cap or amount>before['stat']['inactive_file'] or state['reclaim_reserved_bytes']+amount>state['reclaim_budget_bytes']:
   rec['blocked']='single-write size, inactive file coverage or finite 6GiB budget unavailable';save();return False
  state['reclaim_reserved_bytes']+=amount;rec.update(requested=amount,write_count=1)
  cmd=['/usr/bin/python3',str(base/'local-reclaim-finite.py'),str(amount),'--state',str(state_path),'--preparation',str(len(state['preparations'])-1)];log=base/(label+'-admission-'+tag+'.log');rec.update(command=cmd,log=str(log));save()
  with log.open('w') as out:r=subprocess.run(cmd,stdout=out,stderr=subprocess.STDOUT,cwd=project)
  rec['exit']=r.returncode
  try:rec['helper_result']=json.loads(log.read_text().splitlines()[-1])
  except (ValueError,IndexError):pass
  after=preparation_snapshot();rec['after']=after;rec['actual_current_reduction']=before['current']-after['current'];rec['swap_delta']=after['swap']-before['swap'];rec['shmem_delta']=after['stat']['shmem']-before['stat']['shmem'];rec['event_deltas']={k:after['events'][k]-before['events'][k] for k in ['high','max','oom','oom_kill']};state['heartbeat']=time.time();save()
  if r.returncode or rec['swap_delta']>0 or any(rec['event_deltas'].values()):rec['blocked']='reclaim failed or unsafe metrics';save();return False
 else:after=preparation_snapshot();rec['after']=after
 if after['limits']!=expected or after['current']>target or after['host_available']<host_floor:
  rec['blocked']='fresh conditions still unavailable after bounded preparation';save();return False
 rec['admitted']=True;state['prepared_labels'].append(label);save();return True
state['prior_window']=config['prior_window'];state['reclaim_carried_forward_bytes']=config['carried_reclaim_bytes']
entry=conditions();state['entry']=entry;save()
if entry['limits']!=original:state['blocked']='original limits differ; no overwrite';save();sys.exit(73)
if not prepare(config['plan'][0]['label'],8192,opening=True):state['blocked']='opening preparation did not admit first workload';save();print(json.dumps({'state':str(state_path),'blocked':state['blocked']}));sys.exit(75)

def snapshot(path):
 r={'path':str(path)}
 for n in ['memory.current','memory.peak','memory.high','memory.max','memory.events','memory.events.local','memory.pressure','memory.swap.current','memory.swap.max']:
  try:r[n]=(path/n).read_text().strip()
  except FileNotFoundError:r[n]='unavailable'
 return r
def run_workload(api, label, args, seconds, standalone=False):
 global owned_scope
 name=label+('-api'+str(api) if api else '');log=base/(name+'-'+tag+'.log');telemetry=base/(name+'-'+tag+'-memory.jsonl');owned_scope='tokkl-0273-'+label+'-'+tag+'.scope';cmd=['systemd-run','--user','--scope','--quiet','--unit='+owned_scope,'-p','OOMPolicy=continue','--','choom','-n','1000','--','env','PATH='+config['environment']['PATH'],'JAVA_HOME='+config['environment']['JAVA_HOME'],'ANDROID_HOME='+config['environment']['ANDROID_HOME'],'MEM_GATE_CGROUP_MB='+('5000' if standalone else '8192'),'MEM_GATE_WAIT_S=300','TMPDIR='+str(base/'scratch'),'TOKKL_TEST_AVD=tokkl-api'+str(api),gate,'timeout','--signal=TERM','--kill-after=30s',str(seconds)+'s']+args
 completion=Session(base/('completion-'+name+'-'+tag),app,owned_scope,seconds,capture);cmd,command_env=completion.wrap_command(cmd,gate)
 event_baseline=completion.capture();monitor=Monitor(event_baseline,app,app/owned_scope,sampler=lambda root,deadline:completion.capture(root,deadline=deadline));paths=set();tmp_paths=set();scope_paths={};em_initial={};observed_env={};stop_reason=None;pre_shared={x.name for x in Path('/tmp/android-e0ipso').iterdir()};start=time.monotonic();start_wall=time.time()
 (base/(name+'-'+tag+'-baseline.json')).write_text(json.dumps(event_baseline,indent=2)+'\n')
 with log.open('w') as out,telemetry.open('w') as samples:
  state['owned_scope']=owned_scope; save()
  proc=subprocess.Popen(cmd,stdout=out,stderr=subprocess.STDOUT,cwd=project,env=command_env)
  state['workload_pid']=proc.pid; state['active_log']=str(log); state['active_command']=cmd; state['active_deadline']=time.time()+seconds+390; save()
  while proc.poll() is None:
   sample={'time':now(),'host_available':next(x for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:')),'parent':snapshot(app),'workload':snapshot(app/owned_scope),'ancestors':[snapshot(x) for x in [app.parent,app.parent.parent,app.parent.parent.parent]],'emulators':[],'private_allocations':[]};why=None
   for slot in runtime.glob('tokkl-instrumented-tests-1000-slot-*.lock'):
    try:text=slot.read_text()
    except FileNotFoundError:continue
    if str(project) not in text:continue
    w=re.search(r'work directory ([^,\n]+)',text);t=re.search(r'temporary emulator data ([^,\n]+)',text);u=re.search(r'in (tokkl-emulator-[^,\s]+\.scope)',text)
    if w:paths.add(w[1])
    if t:tmp_paths.add(t[1])
    if u and u[1] not in scope_paths:
     result=subprocess.run(['systemctl','--user','show',u[1],'-p','ControlGroup','--value'],capture_output=True,text=True)
     if result.returncode==0 and result.stdout.strip():scope_paths[u[1]]=Path('/sys/fs/cgroup')/result.stdout.strip().lstrip('/')
   for emunit,cp in scope_paths.items():
    if not cp.exists():continue
    row=snapshot(cp);row['unit']=emunit
    # Local and hierarchical events are assessed together below.
    sample['emulators'].append(row)
    try:pids=(cp/'cgroup.procs').read_text().split()
    except FileNotFoundError:pids=[]
    for pid in pids:
     try:env=Path('/proc',pid,'environ').read_bytes().split(b'\0');temp=next((x.split(b'=',1)[1].decode() for x in env if x.startswith(b'ANDROID_TMP=')),None)
     except (FileNotFoundError,PermissionError,ProcessLookupError):continue
     if temp:observed_env[pid]={'ANDROID_TMP':temp,'unit':emunit}
   for path in tmp_paths:
    p=Path(path)
    if p.exists():
     entries=[]
     for x in p.rglob('*'):
      try:s=x.lstat()
      except FileNotFoundError:continue
      if x.is_file():entries.append({'name':str(x.relative_to(p)),'size':s.st_size,'allocated':s.st_blocks*512})
     sample['private_allocations'].append({'path':path,'files':entries,'allocated':sum(x['allocated'] for x in entries)})
   tree=None;assessment=None
   try:
    tree=completion.capture();assessment=monitor.inspect(tree,owned=list(scope_paths.values())+completion.protected_scopes(),host_available=int(sample['host_available'].split()[1])*1024);why=assessment['abort']
    if not why:completion.accept(monitor.previous,assessment)
   except (EvidenceError,OSError,ValueError,KeyError,TypeError) as error:
    why='scope completion evidence: '+repr(error);assessment={'abort':why,'evidence_errors':[repr(error)]}
   sample['event_tree']=tree;sample['event_assessment']=assessment;sample['event_baseline']=event_baseline if time.monotonic()-start<10 else None;sample['scope_completion_directory']=str(completion.path)
   if limits()!=temporary:why='window limit interference'
   samples.write(json.dumps(sample)+'\n');samples.flush(); state['heartbeat']=time.time(); save()
   if why or time.monotonic()-start>seconds+360:
    stop_reason=why or 'owned workload deadline';completion.abort(stop_reason);subprocess.run(['systemctl','--user','kill','--kill-whom=all','--signal=TERM',owned_scope],stdout=out,stderr=subprocess.STDOUT);break
   time.sleep(2)
  cleanup_deadline=time.monotonic()+45
  try:exit_code=proc.wait(timeout=max(.001,cleanup_deadline-time.monotonic()))
  except subprocess.TimeoutExpired:exit_code=124;stop_reason=stop_reason or 'owned scope did not finish cleanup within 45 seconds'
  try:
   if stop_reason:completion.abort(stop_reason)
   else:completion.finalize(exit_code,monitor,lambda:int(next(x.split()[1] for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:')))*1024,deadline=cleanup_deadline)
  except (EvidenceError,OSError,ValueError,KeyError,TypeError) as error:
   stop_reason=stop_reason or 'final scope evidence: '+repr(error);completion.abort(stop_reason)
   subprocess.run(['systemctl','--user','kill','--kill-whom=all','--signal=TERM',owned_scope],stdout=out,stderr=subprocess.STDOUT)
  try:completion.await_cleanup(deadline=cleanup_deadline)
  except (EvidenceError,OSError,ValueError,KeyError,TypeError) as error:stop_reason=stop_reason or 'scope cleanup evidence: '+repr(error)
 text=log.read_text(errors='replace');result_root=project/'app/build/outputs/androidTest-results/connected/debug';copied=base/(name+'-'+tag+'-results');counts={'tests':0,'failures':0,'errors':0,'skipped':0};failures=[]
 if not standalone and result_root.exists():
  shutil.copytree(result_root,copied)
  for xml in copied.rglob('TEST-*.xml'):
   if xml.stat().st_mtime<start_wall:continue
   root=ET.parse(xml).getroot()
   for case in root.iter('testcase'):
    counts['tests']+=1
    for field in ['failure','error','skipped']:
     values=case.findall(field)
     if values:counts[{'failure':'failures','error':'errors','skipped':'skipped'}[field]]+=1
     if field in ['failure','error']:
      for item in values:failures.append({'class':case.get('classname'),'test':case.get('name'),'type':field,'message':item.get('message','')[:1000]})
 cleanup={'work_directories_removed':all(not Path(x).exists() for x in paths),'private_android_tmp_removed':all(not Path(x).exists() for x in tmp_paths),'emulator_scopes_absent':all(not x.exists() for x in scope_paths.values()),'new_shared_android_files':sorted({x.name for x in Path('/tmp/android-e0ipso').iterdir()}-pre_shared),'work_directories':sorted(paths),'private_android_tmp':sorted(tmp_paths),'observed_android_tmp_env':observed_env}
 rec={'label':label,'standalone':standalone,'api_requested':api,'command':cmd,'exit':exit_code,'log':str(log),'telemetry':str(telemetry),'results':str(copied) if copied.exists() else None,'counts':counts,'failures':failures,'device_lines':[x for x in text.splitlines() if re.search(r'emulator-[0-9]+ runs .* on API',x)],'boot_lines':[x for x in text.splitlines() if x.startswith('Booting tokkl-')],'cleanup':cleanup,'stop_reason':stop_reason,'duration_seconds':time.monotonic()-start};state['tests'].append(rec);save();owned_scope=None
 if label=='assemble-check':
  jvm_root=project/'app/build/test-results/testDebugUnitTest';jvm_dest=base/(label+'-'+tag+'-jvm-results');jvm_counts={'tests':0,'failures':0,'errors':0,'skipped':0}
  if jvm_root.exists():
   shutil.copytree(jvm_root,jvm_dest)
   for x in jvm_dest.glob('TEST-*.xml'):
    jr=ET.parse(x).getroot()
    for k in jvm_counts:jvm_counts[k]+=int(jr.get(k,0))
  rec['jvm_counts']=jvm_counts;rec['jvm_results']=str(jvm_dest)
  lint=project/'app/build/reports/lint-results-debug.xml'
  if lint.exists():
   target=base/(label+'-'+tag+'-lint.xml');shutil.copy2(lint,target);lc={}
   for issue in ET.parse(target).getroot().iter('issue'):lc[issue.get('severity')]=lc.get(issue.get('severity'),0)+1
   rec['lint']={'path':str(target),'counts':lc}
  save()
 if not standalone:
  for suffix,subdir in [('benchmark-results','macrobenchmark/build/outputs/androidTest-results/connected/benchmark'),('benchmark-data','macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark')]:
   src=project/subdir
   if src.exists():
    dest=base/(label+'-'+tag+'-'+suffix);shutil.copytree(src,dest);rec[suffix]=str(dest)
  save()
 benchmark_counts={'tests':0,'failures':0,'errors':0,'skipped':0};benchmark_cases=[]
 if not standalone:
  benchmark_dir=Path(rec.get('benchmark-results','/nonexistent'))
  for xml in benchmark_dir.rglob('TEST-*.xml'):
   if xml.stat().st_mtime<start_wall:continue
   for case in ET.parse(xml).getroot().iter('testcase'):
    benchmark_cases.append({'class':case.get('classname'),'test':case.get('name')})
    benchmark_counts['tests']+=1
    for kind in ['failure','error','skipped']:
     if case.findall(kind):benchmark_counts[{'failure':'failures','error':'errors','skipped':'skipped'}[kind]]+=1
  rec['benchmark_counts']=benchmark_counts;rec['benchmark_cases']=benchmark_cases;save()
 stable=not stop_reason and counts['tests']>0 and bool(rec['device_lines']) and bool(tmp_paths) and bool(observed_env) and all(v['ANDROID_TMP'] in tmp_paths for v in observed_env.values()) and cleanup['work_directories_removed'] and cleanup['private_android_tmp_removed'] and cleanup['emulator_scopes_absent'] and not cleanup['new_shared_android_files']
 if not standalone:stable=stable and benchmark_counts['tests']==(1 if label.startswith('tap-scoped') else 2) and not any(benchmark_counts[k] for k in ['failures','errors','skipped'])
 if standalone:stable=not stop_reason
 return stable and exit_code==0 and counts['failures']==0 and counts['errors']==0,stable
plan=[(job['api'],job['label'],job['args'],job['seconds'],job['standalone']) for job in config['plan']]
bounds=window_bounds(plan);state['duration_bounds']=bounds;save()
result=1
try:
 timer_cmd=['systemd-run','--user','--unit='+unit,'--on-active='+str(bounds['fallback_seconds'])+'s','--timer-property=AccuracySec=1s','--property=Type=oneshot','--property=OOMPolicy=continue','/usr/bin/python3',str(base/'planned-watchdog.py'),str(state_path),'--force']
 if command(timer_cmd,'arm-fallback')!=0:raise RuntimeError('fallback timer could not be armed')
 armed=True
 if command(['systemctl','--user','is-active',unit+'.timer'],'verify-fallback')!=0:raise RuntimeError('fallback timer is not active')
 pre=conditions();state['before_apply']=pre;save();assert pre['limits']==original and pre['baseline_pass'] and pre['host_pass'],'fresh owner preconditions fail before apply'
 state['phase']='applying';save()
 if command(['systemctl','--user','set-property','--runtime','app.slice','MemoryHigh=14G','MemoryMax=15G'],'apply-window')!=0:raise RuntimeError('temporary limit change failed')
 assert limits()==temporary,'temporary limit readback differs';state['phase']='applied';save()
 pre=conditions();state['before_first_workload']=pre;save();assert pre['limits']==temporary and pre['baseline_pass'] and pre['host_pass'],'fresh owner preconditions fail before first workload'
 state['phase']='testing';state['heartbeat']=time.time();save()
 watch_cmd=service_command(unit,bounds,state_path)
 if command(watch_cmd,'arm-watchdog')!=0:raise RuntimeError('watchdog could not be armed')
 watchdog_armed=True
 if command(['systemctl','--user','is-active',unit+'-watch.service'],'verify-watchdog')!=0:raise RuntimeError('watchdog is not active')
 state['checked_base']=config['checked_head'];state['plan']=[{'api':api,'label':label,'args':args,'seconds':seconds,'standalone':standalone} for api,label,args,seconds,standalone in plan];save()
 result=0
 for api,label,args,seconds,standalone in plan:
  state['next_label']=label;state['heartbeat']=time.time();save()
  if label not in state['prepared_labels'] and not prepare(label,5000 if standalone else 8192):
   state['sequence_stopped_at']=label;state['blocked']='bounded preparation refused next workload';save();result=75;break
  success,stable=run_workload(api,label,args,seconds,standalone)
  if not settle_active_workload(success,stable):success=False;stable=False
  if not success:
   result=1;state['failed_labels']=state.get('failed_labels',[])+[label];save()
   if not stable:
    state['sequence_stopped_at']=label;save();break
except BaseException as error:
 _controller_error=error;state['error']=repr(error);state['error_type']=type(error).__name__;result=1
 # Cleanup precedes even error-state persistence, which may itself fail.
 try:state['exception_cleanup']=cleanup_active_workload(error)
 except BaseException as cleanup_error:state['exception_cleanup']={'success':False,'error':repr(cleanup_error),'remaining':'not reported; cleanup boundary failed'}
 try:save()
 except BaseException as persistence_error:
  state['exception_cleanup']['success']=False;state['error_persistence_failure']=repr(persistence_error)
  sys.stderr.write('Original controller error: '+repr(error)+'; persistence failed: '+repr(persistence_error)+'\n')
finally:
 try:cleanup_result=cleanup_active_workload(_controller_error)
 except BaseException as cleanup_error:cleanup_result={'success':False,'error':repr(cleanup_error),'remaining':'not reported; cleanup boundary failed'}
 state['exception_cleanup']=cleanup_result
 try:save()
 except BaseException as persistence_error:
  cleanup_result['success']=False;state['cleanup_persistence_error']=repr(persistence_error)
  sys.stderr.write('Cleanup persistence failed: '+repr(persistence_error)+'\n')
 # Cleanup always runs first; rollback cannot be skipped by a timer-stop error.
 rollback_rc=None;protection_errors=[];rollback_recorded=False
 try:
  rollback_rc=command(['/usr/bin/python3',str(base/'window-rollback.py'),str(state_path)],'immediate-rollback',refresh=True)
  state=json.loads(state_path.read_text());rollback_recorded=True
 except BaseException as rollback_error:
  protection_errors.append('conditional rollback: '+repr(rollback_error));result=1
 try:state['final_limits']=limits()
 except BaseException as readback_error:
  state['final_limits']='not reported';protection_errors.append('limit readback: '+repr(readback_error));result=1
 safe=cleanup_result['success'] and rollback_rc==0 and rollback_recorded and state['final_limits']==original
 watch_cancelled=not watchdog_armed
 if safe and watchdog_armed:
  try:
   watch_rc=command(['systemctl','--user','stop',unit+'-watch.service'],'cancel-watchdog');watch_cancelled=watch_rc==0
   if not watch_cancelled:protection_errors.append('watchdog cancellation exit '+str(watch_rc))
  except BaseException as timer_error:protection_errors.append('watchdog cancellation: '+repr(timer_error))
 fallback_cancelled=not armed
 if safe and watch_cancelled and armed:
  try:
   fallback_rc=command(['systemctl','--user','stop',unit+'.timer'],'cancel-fallback');fallback_cancelled=fallback_rc==0
   if not fallback_cancelled:protection_errors.append('fallback cancellation exit '+str(fallback_rc))
  except BaseException as timer_error:protection_errors.append('fallback cancellation: '+repr(timer_error))
 state['fallback_cancelled']=armed and fallback_cancelled;state['fallback_retained']=armed and not fallback_cancelled
 state['watchdog_retained']=watchdog_armed and not watch_cancelled;state['protection_errors']=protection_errors
 state['manual_recovery_required']=not safe or bool(protection_errors)
 if not safe or protection_errors:result=1
 try:save()
 except BaseException as persistence_error:
  result=1;sys.stderr.write('Final cleanup/rollback state persistence failed: '+repr(persistence_error)+'\n')

print(json.dumps({'state':str(state_path),'result':result,'final_limits':state.get('final_limits'),'tests':[{k:r[k] for k in ['api_requested','exit','counts','stop_reason']} for r in state.get('tests',[])]}));sys.exit(result)
