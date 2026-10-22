"""Extract actual controller functions AND actual outer try/finally. No window.

Only uniquely owned MemoryMax128M/Swap0/RuntimeMax20s fixture scopes are real.
Limit/timer/rollback callbacks are modeled, and assert the actual cleanup order.
"""
from pathlib import Path
from datetime import datetime,timezone
import ast,copy,json,os,re,shutil,signal,subprocess,sys,time,uuid,xml.etree.ElementTree as ET
from unittest.mock import patch
PACKAGE=Path(__file__).resolve().parent.parent;B=Path(__file__).resolve().parent;sys.path.insert(0,str(PACKAGE/'runtime'))
import scope_completion as sc
from capture_evidence import CaptureEvidence
import event_monitor_0274 as em
from controller_cleanup import install_controller,CleanupContext
from watchdog_lifetime import service_command
case=sys.argv[1];output=Path(sys.argv[2]);before=False
assert case in ['success','nonzero','capture','unknown-disappearance']
allowed=['success','nonzero','capture','unknown-disappearance']
assert case in allowed
run=output/(datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+case+('-before' if before else ''));run.mkdir();runtime=run/'runtime';runtime.mkdir();(run/'scratch').mkdir()
app=Path('/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice');actual_limits={n:(app/n).read_text().strip() for n in ['memory.high','memory.max','memory.swap.max']};assert actual_limits=={'memory.high':'10737418240','memory.max':'12884901888','memory.swap.max':'2147483648'}
model_original={'memory.high':'original-high','memory.max':'original-max','memory.swap.max':'original-swap'};model_temp={'memory.high':'temporary-high','memory.max':'temporary-max','memory.swap.max':'original-swap'};model_limits=dict(model_original)
source=(PACKAGE/'runtime/controller.py').read_text();nodes=ast.parse(source).body
selected=[n for n in nodes if isinstance(n,ast.FunctionDef) and n.name in ['now','snapshot','run_workload']];outer=next(n for n in nodes if isinstance(n,ast.Try) and any(isinstance(x,ast.ExceptHandler) and x.type and ast.unparse(x.type)=='BaseException' for x in n.handlers));outer_code=ast.get_source_segment(source,outer)
(run/'actual-outer-controller.py').write_text(outer_code+'\n');(run/'actual-workload.py').write_text('\n'.join(ast.get_source_segment(source,n) for n in selected)+'\n')
tag=uuid.uuid4().hex[:12];emunit='tokkl-emulator-9'+str(int(tag[:4],16))+'-'+tag[:8]+'.scope';work=runtime/'tokkl-instrumented-tests-1000-runs'/tag[:8];private=work/'emulator-tmp'
ns={'service_command':service_command,'Path':Path,'datetime':datetime,'timezone':timezone,'ET':ET,'json':json,'os':os,'re':re,'shutil':shutil,'signal':signal,'sys':sys,'time':time,'base':run,'project':run,'app':app,'runtime':runtime,'gate':str(B/'live_supervisor.sh'),'config':{'environment':{k:os.environ.get(k,'/fixture-unavailable') for k in ['PATH','JAVA_HOME','ANDROID_HOME']},'checked_head':'f64f73644ebbdf0b483ab574cb3db8eb6ba82766'},'original':model_original,'temporary':model_temp,'tag':tag,'state':{'tests':[],'records':[],'prepared_labels':['284-'+case]},'owned_scope':None,'EvidenceError':sc.EvidenceError,'_cleanup_fixture_seconds':.3 if case=='cleanup-timeout' else .5 if case=='foreign-intent' else 10,'_controller_error':None,'armed':False,'watchdog_armed':False,'unit':'fixture-fallback-'+tag,'state_path':run/'state.json','bounds':{'fallback_seconds':60},'result':1}
def modeled_limits():
 if case=='rollback-readback-raises' and any(r['name']=='immediate-rollback' for r in callbacks):raise OSError('injected rollback readback error')
 return dict(model_limits)
ns['limits']=modeled_limits
ns['save']=lambda:(run/'state.json').write_text(json.dumps(ns['state'],indent=2)+'\n')
ns['conditions']=lambda:{'limits':dict(model_limits),'baseline_pass':True,'host_pass':True}
ns['prepare']=lambda *a,**k:True
sessions=[];processes=[];commands=[];callbacks=[];injected=False;unrelated=None;control=(run/'control.log').open('w')
def alive_owned():
 return [unit for unit in [p[1] for p in processes] if (app/unit).exists()]
def command(cmd,name,refresh=False):
 global model_limits
 row={'name':name,'command':cmd,'alive_owned':alive_owned(),'time':time.monotonic()};callbacks.append(row)
 if name=='apply-window':model_limits=dict(model_temp)
 if name=='immediate-rollback' and case=='rollback-raises':raise OSError('injected conditional rollback error')
 if name=='cancel-watchdog' and case=='watchdog-cancel-raises':raise OSError('injected watchdog cancellation error')
 if name=='immediate-rollback':
  row['cleanup_report']=copy.deepcopy(ns['state'].get('exception_cleanup'));model_limits=dict(model_original)
 if name=='cancel-fallback':row['cleanup_report']=copy.deepcopy(ns['state'].get('exception_cleanup'))
 ns['save']();return 1 if name=='cancel-fallback' and case=='fallback-cancel-fails' else 0
ns['command']=command
payload=B/'live_payload.py'
unknown_proc=None;unknown_unit=None;unknown_before=None
ownership=[]
def own(unit,command):
 assert not (app/unit).exists(),'fixture refuses an existing unit'
 ownership.append({'unit':unit,'command':command,'created_monotonic':time.monotonic()})
 temp=run/'ownership.writing';temp.write_text(json.dumps(ownership,indent=2)+'\n');os.replace(temp,run/'ownership.json')

def both_ready():
 return bool(sessions and (sessions[0].path/'scopes'/ns['owned_scope']/'payload.json').exists() and (sessions[0].path/'scopes'/emunit/'payload.json').exists())
def captured(*args,**kwargs):
 global injected
 if case=='initial-capture' and not processes:raise RuntimeError('injected initial capture before Popen')
 if case=='sampler' and injected and isinstance(kwargs.get('root'),Path):raise AttributeError('injected sampler failure after normalized boundary')
 if case=='unknown-disappearance' and sessions and (sessions[0].path/'scopes'/ns['owned_scope']/'payload.json').exists() and not injected:
  actual_stat=Path.stat
  def disappearing_stat(path,*a,**kw):
   global injected
   if path==app/unknown_unit and not injected:
    injected=True
    subprocess.run(['systemctl','--user','stop',unknown_unit],stdout=control,stderr=subprocess.STDOUT,timeout=3)
    unknown_proc.wait(timeout=3)
   return actual_stat(path,*a,**kw)
  with patch.object(Path,'stat',disappearing_stat):tree=em.capture(*args,**kwargs)
 else:tree=em.capture(*args,**kwargs)
 if both_ready() and not injected:
  if case in ['capture','abort-error','cleanup-timeout','foreign-intent','pid-reuse','stubborn-child','overlay-error','slot-overlay','rollback-raises','rollback-readback-raises','watchdog-cancel-raises','fallback-cancel-fails','allocation-reuse','allocation-symlink']:
   injected=True
   context=ns.get('_active_cleanup')
   if context:context.observe()
   if case=='cleanup-timeout':
    sessions[0].abort=lambda *a,**k:None;context._signal_identity=lambda *a,**k:None
   if case=='allocation-symlink':
    work.rename(work.with_name(work.name+'-original'));work.symlink_to(run/'foreign-uncreated')
   if case=='allocation-reuse':
    work.rename(work.with_name(work.name+'-original'));work.mkdir();(work/'foreign-sentinel').write_text('must survive controller cleanup')
   if case=='overlay-error':context._remove_tree=lambda *a,**k:(_ for _ in ()).throw(OSError('injected overlay removal failure'))
   if case=='abort-error':sessions[0].abort=lambda *a,**k:(_ for _ in ()).throw(OSError('injected abort persistence error'))
   if case=='foreign-intent':sc.write(sessions[0].path/'intents'/'foreign.scope.json',{'nonce':'wrong','unit':'foreign.scope'})
   if case=='pid-reuse':
    ident=sc.process_identity(unrelated.pid);stale=dict(ident,start_ticks=ident['start_ticks']+1);context._signal_identity(stale,signal.SIGKILL)
   raise RuntimeError('injected capture while both payloads alive')
  if case=='sampler':
   injected=True;tree['rows'][str(app)]['stable']=False
 return tree
ns['capture']=CaptureEvidence(captured,run/'failure-evidence')
class FixtureMonitor(em.Monitor):
 def inspect(self,*args,**kwargs):
  global injected
  if both_ready() and not injected and case=='inspect':injected=True;raise LookupError('injected inspect while both payloads alive')
  if both_ready() and not injected and case=='expected-resource':
   injected=True;result=super().inspect(*args,**kwargs);result['abort']='owned or ancestor local event: injected genuine-event fixture';return result
  return super().inspect(*args,**kwargs)
ns['Monitor']=FixtureMonitor
class FixtureSession(sc.Session):
 def __init__(self,*args,**kwargs):super().__init__(*args,**kwargs,hold_seconds=5,close_seconds=1);sessions.append(self)
 def finalize(self,*args,**kwargs):
  if case=='finalize-interrupt':raise KeyboardInterrupt('injected finalization interrupt')
  if case in ['finalize','nonzero-finalize']:raise RuntimeError('injected finalize with emulator standin alive')
  if case=='missing-final':raise sc.EvidenceError('injected missing final evidence')
  return super().finalize(*args,**kwargs)
ns['Session']=FixtureSession
class Proxy:
 TimeoutExpired=subprocess.TimeoutExpired;STDOUT=subprocess.STDOUT
 def Popen(self,cmd,**kwargs):
  cmd=list(cmd);unit=next(x.split('=',1)[1] for x in cmd if x.startswith('--unit='));assert unit.startswith('tokkl-0273-284-') and not (app/unit).exists()
  i=cmd.index('--');cmd[i:i]=['-p','MemoryMax=128M','-p','MemorySwapMax=0','-p','RuntimeMaxSec=20s'];commands.append(cmd)
  own(unit,cmd);proc=subprocess.Popen(cmd,**kwargs);processes.append((proc,unit))
  if case not in ['success','nonzero','initial-capture','unknown-disappearance']:
   private.mkdir(parents=True);(private/'owned-overlay').write_text('disposable owned overlay')
   session=sessions[0];sc.write(session.path/'intents'/(emunit+'.json'),{'nonce':session.request['nonce'],'unit':emunit})
   emcmd=['/usr/bin/systemd-run','--user','--scope','--quiet','--unit='+emunit,'-p','OOMPolicy=continue','-p','MemoryMax=128M','-p','MemorySwapMax=0','-p','RuntimeMaxSec=20s','--','choom','-n','1000','--','env',('ANDROID_TMP='+str(private) if case!='slot-overlay' else 'FIXTURE_NO_TMP=1'),sys.executable,'-B',str(PACKAGE/'runtime/scope_completion.py'),'hold','--session',str(session.path),'--unit',emunit,'--',sys.executable,'-B',str(payload),'15','0'];commands.append(emcmd)
   own(emunit,emcmd);ep=subprocess.Popen(emcmd,stdout=control,stderr=subprocess.STDOUT);processes.append((ep,emunit))
   end=time.monotonic()+4
   while not both_ready() and time.monotonic()<end:
    if proc.poll() is not None or ep.poll() is not None:raise RuntimeError('fixture scope startup failed')
    time.sleep(.01)
   assert both_ready(),'both owned holder payload records required before injection'
   if case=='slot-overlay':(runtime/'tokkl-instrumented-tests-1000-slot-1.lock').write_text('PID '+str(proc.pid)+', fixture in '+emunit+', work directory '+str(work)+'\n')
   if case=='stubborn-child':
    while not (run/'stubborn.pid').exists() and time.monotonic()<end:time.sleep(.01)
    assert (run/'stubborn.pid').exists(),'stubborn owned descendant must exist before exception'
  if case=='unknown-disappearance':
   end=time.monotonic()+4
   while not (sessions[0].path/'scopes'/unit/'payload.json').exists() and time.monotonic()<end:time.sleep(.01)
   assert (sessions[0].path/'scopes'/unit/'payload.json').exists()
  return proc
 def run(self,cmd,**kwargs):
  assert cmd[:3]==['systemctl','--user','kill'] and cmd[-1] in [u for _,u in processes],'refusing nonfixture unit action'
  kwargs['timeout']=2;return subprocess.run(cmd,**kwargs)
ns['subprocess']=Proxy()
for node in selected:exec(compile(ast.Module(body=[node],type_ignores=[]),str(PACKAGE/'runtime/controller.py'),'exec'),ns)
if not before:install_controller(ns)
# Nonzero finalization must preserve actual seven while the second scope is alive.
exitcode=7 if case in ['nonzero','nonzero-finalize'] else 0
sleep=.15 if case in ['finalize','finalize-interrupt','nonzero-finalize','missing-final','success','nonzero'] else 15
ns['plan']=[(0,'284-'+case,[sys.executable,'-B',str(payload),str(sleep),str(exitcode),str(run/'stubborn.pid') if case=='stubborn-child' else 'plain'],8,True)]
if case=='pid-reuse':
 unrelated_unit='tokkl-0280-unrelated-'+tag+'.scope'
 unrelated_cmd=['systemd-run','--user','--scope','--quiet','--unit='+unrelated_unit,'-p','OOMPolicy=continue','-p','MemoryMax=128M','-p','MemorySwapMax=0','-p','RuntimeMaxSec=20s','--','choom','-n','1000','--',sys.executable,'-B',str(payload),'20','0'];commands.append(unrelated_cmd);unrelated=subprocess.Popen(unrelated_cmd,stdout=control,stderr=subprocess.STDOUT)
 end=time.monotonic()+2
 while time.monotonic()<end:
  ident=sc.process_identity(unrelated.pid)
  if ident and ident['cgroup']==str(app/unrelated_unit):break
  time.sleep(.01)
 assert ident and ident['cgroup']==str(app/unrelated_unit)
if case=='unknown-disappearance':
 unknown_unit='tokkl-0284-unknown-'+tag+'.scope'
 unknown_cmd=['systemd-run','--user','--scope','--quiet','--unit='+unknown_unit,'-p','OOMPolicy=continue','-p','MemoryMax=128M','-p','MemorySwapMax=0','-p','RuntimeMaxSec=20s','--','choom','-n','1000','--',sys.executable,'-B',str(payload),'15','0']
 commands.append(unknown_cmd);own(unknown_unit,unknown_cmd);unknown_proc=subprocess.Popen(unknown_cmd,stdout=control,stderr=subprocess.STDOUT)
 end=time.monotonic()+3
 while not (app/unknown_unit).exists() and time.monotonic()<end:time.sleep(.01)
 assert (app/unknown_unit).exists(),'owned unknown fixture did not start'
 unknown_before=em.capture(app/unknown_unit)
status=None;unexpected=None;outcome=None
try:
 exec(compile(outer_code,str(PACKAGE/'runtime/controller.py')+':outer','exec'),ns)
 status=ns['result'];outcome=copy.deepcopy(ns['state']);rollback=next(r for r in callbacks if r['name']=='immediate-rollback');cancel=[r for r in callbacks if r['name']=='cancel-fallback'];cleanup_report=outcome.get('exception_cleanup',{})
 failure_expected=case in ['cleanup-timeout','abort-error','foreign-intent','overlay-error','rollback-raises','rollback-readback-raises','watchdog-cancel-raises','fallback-cancel-fails','allocation-reuse','allocation-symlink']
 normal=case in ['success','nonzero']
 checks={'original_caps_unchanged':{n:(app/n).read_text().strip() for n in actual_limits}==actual_limits,'modeled_original_restored':(model_limits==model_temp if case=='rollback-raises' else model_limits==model_original),'preserves_failure_status':status==(0 if case=='success' else 1),'owned_cleanup_before_rollback':bool(rollback.get('cleanup_report') and not rollback['cleanup_report']['success']) if case in ['cleanup-timeout','abort-error','foreign-intent','overlay-error','allocation-reuse','allocation-symlink'] else not rollback['alive_owned'],'fallback_cancellation_safe':(bool(cancel and not cancel[0]['alive_owned'] and outcome.get('fallback_retained')) if case=='fallback-cancel-fails' else not cancel) if failure_expected else bool(cancel and not cancel[0]['alive_owned']),'remaining_reported_on_failure':bool(outcome.get('protection_errors') or cleanup_report.get('remaining') or cleanup_report.get('errors')) if failure_expected else True,'no_false_certificate':bool(sessions and (sessions[0].path/'completed.json').exists()) if normal else not any((s.path/'completed.json').exists() for s in sessions),'overlay_removed_before_rollback':not work.exists() if not failure_expected else True,'original_exception_preserved':not outcome.get('error') if normal or case in ['expected-resource','missing-final'] else (bool(outcome.get('error')) or bool(outcome.get('tests',[{}])[0].get('stop_reason')) if case=='unknown-disappearance' else bool(outcome.get('error'))),'pid_reuse_not_signaled':unrelated.poll() is None if unrelated else True}
 if case=='unknown-disappearance':
  checks['actual_missing_scope_aborts']=bool(outcome.get('tests') and outcome['tests'][0].get('stop_reason'))
  checks['unknown_scope_baseline_was_live']=bool(unknown_before and unknown_before['rows'])
 if case=='nonzero-finalize':checks['payload_exit7_preserved']=cleanup_report.get('launcher_exit')==7 and any(r.get('ready.json',{}).get('payload_status')==7 for r in cleanup_report.get('payload_outcomes',{}).values())
 if case=='pid-reuse':checks['stale_start_identity_detected']=any(row.get('skipped_reused_pid')==unrelated.pid for row in cleanup_report.get('ownership_notes',[]))
 if case=='allocation-symlink':checks['foreign_symlink_untouched']=work.is_symlink() and work.readlink()==run/'foreign-uncreated'
 if case=='allocation-reuse':checks['foreign_allocation_untouched']=(work/'foreign-sentinel').read_text()=='must survive controller cleanup'
 if case=='sampler':checks['actual_sampler_attribute_error']='AttributeError' in outcome.get('error','')
 if not before and case not in ['success','nonzero','initial-capture']:
  attempts=[json.loads(line) for line in (sessions[0].path/'controller-cleanup-attempts.jsonl').read_text().splitlines()]
  checks['same_cleanup_deadline_repeated']=len(attempts)>=2 and len({row['deadline'] for row in attempts})==1
  checks['aggregate_cleanup_bounded']=attempts[-1]['started']+attempts[-1]['elapsed_seconds']-attempts[0]['started']<=ns['_cleanup_fixture_seconds']+.15
 if case=='cleanup-timeout':checks['finite_deadline']=cleanup_report.get('elapsed_seconds',999)<.7 and cleanup_report.get('deadline_exhausted')
except BaseException as error:unexpected=repr(error);checks={'harness_completed':False}
finally:
 # Recovery belongs only to this harness's exact freshly recorded units.
 recovery=[]
 for proc,unit in processes:
  if (app/unit).exists():
   cmd=['systemctl','--user','stop',unit]
   try:r=subprocess.run(cmd,stdout=control,stderr=subprocess.STDOUT,timeout=3);recovery.append({'command':cmd,'exit':r.returncode})
   except subprocess.TimeoutExpired:recovery.append({'command':cmd,'exit':124})
  try:proc.wait(timeout=3)
  except subprocess.TimeoutExpired:recovery.append({'unit':unit,'wait':'timed out'})
 if unrelated is not None:
  subprocess.run(['systemctl','--user','stop',unrelated_unit],stdout=control,stderr=subprocess.STDOUT,timeout=3);unrelated.wait(timeout=2)
 if unknown_proc is not None:
  if (app/unknown_unit).exists():subprocess.run(['systemctl','--user','stop',unknown_unit],stdout=control,stderr=subprocess.STDOUT,timeout=3)
  unknown_proc.wait(timeout=3)
 control.close()
 if case=='overlay-error':
  checks['overlay_failure_evidence']=bool(cleanup_report.get('directories') and not cleanup_report['directories'][0]['removed'])
 # Harness-owned disposable leftovers are removed only after recording controller behavior.
 if work.is_symlink():work.unlink()
 elif work.exists():shutil.rmtree(work)
 if work.with_name(work.name+'-original').exists():shutil.rmtree(work.with_name(work.name+'-original'))
 checks['fixture_scopes_absent']=not alive_owned();checks['real_limits_restored_unchanged']={n:(app/n).read_text().strip() for n in actual_limits}==actual_limits
 result={'case':case,'before':before,'controller':str(PACKAGE/'runtime/controller.py'),'result':status,'unexpected':unexpected,'checks':checks,'commands':commands,'callbacks':callbacks,'state':outcome,'harness_recovery':recovery,'all_fixture_scopes_absent':not alive_owned(),'run':str(run),'unknown_scope':unknown_unit,'unknown_before':unknown_before,'modeled':['shared limits','preparation/reclaim','memory admission','watchdog/fallback callbacks'],'live':['actual maintained workload and outer controller code','systemd 128MiB scopes','OOMPolicy continue and choom1000','Session/holder','kernel cgroup samples','identity cleanup']}
 (run/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'case':case,'before':before,'checks':checks,'unexpected':unexpected,'result':str(run/'result.json')}))
raise SystemExit(0 if all(checks.values()) else 1)
