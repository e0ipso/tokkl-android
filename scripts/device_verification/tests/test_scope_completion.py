import copy,json,math,os,shutil,subprocess,sys,tempfile,time,unittest
from pathlib import Path
import scope_completion as sc
from event_monitor_0274 import KEYS,Monitor

BASE=Path(__file__).resolve().parent
MAIN='tokkl-test-main.scope';EMU='tokkl-emulator-5554-abcdef12.scope'
class CompletionTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(dir=BASE);self.root=Path(self.tmp.name)/'cgroups';self.app=self.root/'app';self.app.mkdir(parents=True)
        self.procs=[];self.files=[];self.local={};self.total=dict.fromkeys(KEYS,0);self.errors=[]
        self.session=sc.Session(Path(self.tmp.name)/'protocol',self.app,MAIN,1,self.raw_capture,identity_reader=self.identity,members_reader=self.members,hold_seconds=1,close_seconds=.5)
        self.monitor=Monitor(self.session.capture(),self.app,self.app/MAIN,sampler=lambda root,deadline:self.session.capture(root,deadline=deadline))
    def tearDown(self):
        for proc in self.procs:
            if proc.poll() is None:proc.terminate()
            try:proc.wait(timeout=3)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=1)
        for f in self.files:f.close()
        self.tmp.cleanup()
    def identity(self,pid):
        result=sc.process_identity(pid)
        if result is not None and result['state']!='Z':
            for scope in self.app.iterdir():
                marker=scope/'member.pid'
                if marker.exists() and marker.read_text()==str(pid):result['cgroup']=str(scope);break
        return result
    def members(self,scope):
        marker=scope/'member.pid';result={int(marker.read_text())}
        if (scope/'extra-member').exists():result.add(999999999)
        return result
    def raw_capture(self,**kwargs):
        rows={};paths=[self.root,self.app]+[p for p in self.app.iterdir() if p.is_dir()]
        for p in paths:
            local=copy.deepcopy(self.local.get(str(p),dict.fromkeys(KEYS,0)))
            event=copy.deepcopy(self.total if p in [self.root,self.app] else local)
            rows[str(p)]={'inode':p.stat().st_ino,'time':'fixture','stable':True,'events':event,'local':local,'current':100,'high':'1000000000000','max':'2000000000000','pressure':'full avg10=0 total=0'}
        return {'root':str(self.root),'rows':rows,'errors':copy.deepcopy(self.errors),'time':'fixture','monotonic':time.monotonic(),'duration':0.}
    def spawn(self,unit=MAIN,code='pass',flags=()):
        scope=self.app/unit;scope.mkdir()
        for flag in flags:(scope/flag).touch()
        log=Path(self.tmp.name)/(unit+'.log');out=log.open('w');self.files.append(out)
        proc=subprocess.Popen([sys.executable,'-B',str(BASE/'fixture_holder.py'),str(self.session.path),unit,code],stdout=out,stderr=subprocess.STDOUT,env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
        self.procs.append(proc);return proc
    def ready(self,unit=MAIN):
        p=self.session.path/'scopes'/unit/'ready.json';end=time.monotonic()+2
        while not p.exists():
            self.assertLess(time.monotonic(),end,'no ready record');time.sleep(.005)
        return sc.load(p)
    def inspect(self):
        tree=self.session.capture();result=self.monitor.inspect(tree,owned=self.session.protected_scopes(),host_available=10*1024**3);return tree,result
    def release(self):
        tree,result=self.inspect();self.assertIsNone(result['abort']);self.session.accept(self.monitor.previous,result);return tree
    def complete(self,code=0):
        self.release();self.assertEqual(self.procs[-1].wait(timeout=2),code)
        return self.session.finalize(code,self.monitor,lambda:10*1024**3)
    def test_success_captures_final_events_before_verified_closure(self):
        self.spawn();self.ready();self.assertEqual(self.complete(),0)
        self.assertTrue((self.session.path/'completed.json').exists())
        self.assertFalse((self.app/MAIN).exists());self.assertEqual(set(self.session.retired),{MAIN})
    def test_nonzero_payload_status_is_preserved_after_final_capture(self):
        self.spawn(code='raise SystemExit(7)');self.ready();self.assertEqual(self.complete(7),7)
    def test_holder_stays_live_after_payload_exit_until_capture_ack(self):
        p=self.spawn();ready=self.ready();self.assertEqual(ready['exit_code'],0)
        self.assertIsNone(p.poll());self.assertTrue((self.app/MAIN).exists())
        self.assertFalse((self.session.path/'scopes'/MAIN/'ack.json').exists());self.complete()
    def test_stale_pre_completion_sample_never_acknowledges(self):
        self.spawn();ready=self.ready();tree,result=self.inspect();tree['monotonic']=ready['ready_monotonic']-.001
        self.session.accept(tree,result);self.assertEqual(self.session.certified,{})
        self.assertFalse((self.session.path/'scopes'/MAIN/'ack.json').exists())
    def test_missing_final_row_is_fatal_even_with_exit_zero(self):
        self.spawn();self.ready();tree,result=self.inspect();del tree['rows'][str(self.app/MAIN)]
        with self.assertRaisesRegex(sc.EvidenceError,'missing/ambiguous'):self.session.accept(tree,result)
        with self.assertRaisesRegex(sc.EvidenceError,'missing final'):self.session.finalize(0,self.monitor,lambda:10*1024**3)
    def test_ambiguous_final_row_never_acknowledges(self):
        self.spawn();self.ready();tree,result=self.inspect();tree['rows'][str(self.app/MAIN)]['stable']=False
        with self.assertRaisesRegex(sc.EvidenceError,'ambiguous'):self.session.accept(tree,result)
    def test_live_disappearance_remains_a_genuine_monitor_abort(self):
        self.spawn();self.ready();self.inspect();shutil.rmtree(self.app/MAIN)
        _,result=self.inspect();self.assertIn('disappeared',result['abort'])
    def test_missing_final_protocol_record_is_not_success(self):
        self.spawn();self.ready();(self.session.path/'scopes'/MAIN/'ready.json').unlink()
        tree,result=self.inspect();self.session.accept(tree,result)
        with self.assertRaisesRegex(sc.EvidenceError,'missing final'):self.session.finalize(0,self.monitor,lambda:10*1024**3)
    def test_hold_timeout_is_finite_and_cleans_up(self):
        self.session.request['hold_seconds']=.1;(self.session.path/'request.json').write_text(json.dumps(self.session.request))
        p=self.spawn();self.assertEqual(p.wait(timeout=2),125);self.assertFalse((self.app/MAIN).exists())
        self.assertIn('timed out',sc.load(self.session.path/'scopes'/MAIN/'failure.json')['error'])
    def test_payload_timeout_terminates_only_owned_payload(self):
        self.session.request['command_seconds']=.1;(self.session.path/'request.json').write_text(json.dumps(self.session.request))
        p=self.spawn(code='import time;time.sleep(20)');self.assertEqual(p.wait(timeout=3),125)
        child=sc.load(self.session.path/'scopes'/MAIN/'payload.json')['pid'];identity=sc.process_identity(child)
        self.assertTrue(identity is None or identity['state']=='Z');self.assertFalse((self.app/MAIN).exists())
    def test_nonquiescent_descendants_do_not_produce_final_ready(self):
        self.session.request['hold_seconds']=.1;(self.session.path/'request.json').write_text(json.dumps(self.session.request))
        p=self.spawn(flags=['extra-member']);self.assertEqual(p.wait(timeout=2),125)
        self.assertFalse((self.session.path/'scopes'/MAIN/'ready.json').exists())
    def test_missing_release_proof_fails_closed(self):
        self.spawn(flags=['drop-release']);self.ready();tree,result=self.inspect()
        with self.assertRaisesRegex(sc.EvidenceError,'closure not verified'):self.session.accept(tree,result)
    def test_tampered_final_acknowledgement_is_rejected(self):
        p=self.spawn();self.ready();folder=self.session.path/'scopes'/MAIN
        sc.write(folder/'final.json',{'ready_digest':'wrong','snapshot_monotonic':time.monotonic()})
        sc.write(folder/'ack.json',{'nonce':'wrong','holder':[1,1],'final_digest':'wrong'})
        self.assertEqual(p.wait(timeout=2),125);self.assertTrue((folder/'failure.json').exists())
    def test_ready_identity_mismatch_never_acknowledges(self):
        self.spawn();self.ready();p=self.session.path/'scopes'/MAIN/'ready.json';data=sc.load(p);data['pid']+=1;p.write_text(json.dumps(data));tree,result=self.inspect()
        with self.assertRaisesRegex(sc.EvidenceError,'identity mismatch'):self.session.accept(tree,result)
    def test_actual_scope_identity_change_never_acknowledges(self):
        self.spawn();self.ready();tree,result=self.inspect();tree['rows'][str(self.app/MAIN)]['inode']+=1
        with self.assertRaisesRegex(sc.EvidenceError,'identity changed'):self.session.accept(tree,result)
    def test_owned_ancestor_and_all_max_oom_events_prevent_release(self):
        self.spawn();self.ready()
        for path in [str(self.app/MAIN),str(self.app),str(self.root)]:
            for key in KEYS:
                with self.subTest(path=path,key=key):
                    tree=self.session.capture();tree['rows'][path]['local'][key]+=1
                    for p in tree['rows']:
                        if path==p or path.startswith(p+'/'):tree['rows'][p]['events'][key]+=1
                    monitor=Monitor(self.monitor.previous,self.app,self.app/MAIN)
                    result=monitor.inspect(tree,owned=self.session.protected_scopes(),host_available=10*1024**3)
                    self.assertIsNotNone(result['abort'])
                    with self.assertRaisesRegex(sc.EvidenceError,'monitor rejected'):self.session.accept(tree,result)
        self.assertFalse((self.session.path/'scopes'/MAIN/'ack.json').exists())
    def test_late_hierarchical_high_after_release_is_not_lost(self):
        self.spawn();self.ready();self.release();self.procs[-1].wait(timeout=2);self.total['high']+=1
        with self.assertRaisesRegex(sc.EvidenceError,'resource guard'):self.session.finalize(0,self.monitor,lambda:10*1024**3)
    def test_late_hierarchical_oom_after_release_is_fatal(self):
        self.spawn();self.ready();self.release();self.procs[-1].wait(timeout=2);self.total['oom_kill']+=1
        with self.assertRaisesRegex(sc.EvidenceError,'max/OOM'):self.session.finalize(0,self.monitor,lambda:10*1024**3)
    def test_retirement_does_not_discard_enoent_errors(self):
        self.spawn();self.ready();self.release();self.procs[-1].wait(timeout=2)
        self.errors=[{'path':str(self.app/MAIN),'error':'FileNotFoundError: fixture'}]
        with self.assertRaisesRegex(sc.EvidenceError,'inaccessible'):self.session.finalize(0,self.monitor,lambda:10*1024**3)
    def test_recreated_retired_scope_is_not_certified(self):
        self.spawn();self.ready();self.release();self.procs[-1].wait(timeout=2);(self.app/MAIN).mkdir()
        with self.assertRaisesRegex(sc.EvidenceError,'reappeared'):self.session.capture()
    def test_launcher_status_must_match_verified_payload_status(self):
        self.spawn();self.ready();self.release();self.procs[-1].wait(timeout=2)
        with self.assertRaisesRegex(sc.EvidenceError,'exit status mismatch'):self.session.finalize(1,self.monitor,lambda:10*1024**3)
    def test_certified_frozen_rows_are_explicit_and_do_not_reset_counters(self):
        self.spawn();self.ready();self.release();self.procs[-1].wait(timeout=2)
        rows=copy.deepcopy(self.session.retired[MAIN]['rows']);tree=self.session.capture()
        self.assertEqual(tree['rows'][str(self.app/MAIN)],rows[str(self.app/MAIN)])
        self.assertIn(str(self.app/MAIN),tree['scope_completion']['frozen_final_rows'])
        self.assertNotIn(str(self.app/MAIN),tree['scope_completion']['raw_rows'])
    def test_unrelated_events_still_require_exact_attribution_after_retirement(self):
        self.spawn();self.ready();self.release();self.procs[-1].wait(timeout=2)
        other=self.app/'other.scope';other.mkdir();self.local[str(other)]=dict.fromkeys(KEYS,0);self.local[str(other)]['high']=2;self.total['high']=2
        self.assertEqual(self.session.finalize(0,self.monitor,lambda:10*1024**3),0)
    def test_nested_emulator_finishes_before_waiting_script_without_deadlock(self):
        marker=self.session.path/('fixture-closed-'+EMU)
        code='from pathlib import Path;import time;end=time.monotonic()+3\nwhile not Path('+repr(str(marker))+').exists():\n assert time.monotonic()<end\n time.sleep(.01)'
        stop=self.session.path/'fixture-stop-emulator'
        emucode='from pathlib import Path;import time;end=time.monotonic()+3\nwhile not Path('+repr(str(stop))+').exists():\n assert time.monotonic()<end\n time.sleep(.01)'
        self.spawn(code=code);self.spawn(EMU,code=emucode)
        payload_path=self.session.path/'scopes'/EMU/'payload.json';end=time.monotonic()+2
        while not payload_path.exists():
            self.assertLess(time.monotonic(),end);time.sleep(.005)
        def child_identity(pid):
            identity=sc.process_identity(pid)
            if identity is not None:identity['cgroup']=str(self.app/EMU)
            return identity
        sc.record_shutdown_request(self.session.path,['-s','emulator-5554','emu','kill'],identity_reader=child_identity)
        stop.touch();self.ready(EMU);self.release();self.ready(MAIN);self.release()
        for p in self.procs:self.assertEqual(p.wait(timeout=2),0)
        self.assertEqual(self.session.finalize(0,self.monitor,lambda:10*1024**3),0);self.assertEqual(set(self.session.retired),{MAIN,EMU})
    def test_unsupported_or_unregistered_launch_intent_prevents_completion(self):
        self.spawn();self.ready();self.release();self.procs[-1].wait(timeout=2)
        sc.write(self.session.path/'intents'/(EMU+'.json'),{'nonce':self.session.request['nonce'],'unit':EMU})
        with self.assertRaisesRegex(sc.EvidenceError,'launch intent'):self.session.finalize(0,self.monitor,lambda:10*1024**3)
    def test_policy_rejects_unbounded_deadlines(self):
        for value in [0,-1,math.inf,math.nan,True,21]:
            with self.subTest(value=value),self.assertRaises(sc.EvidenceError):sc.Session(Path(self.tmp.name)/'invalid',self.app,MAIN,1,self.raw_capture,hold_seconds=value)
    def test_added_normal_completion_bound_fits_existing_duration_allowance(self):
        self.assertLessEqual(30+sc.HOLD_SECONDS+sc.CLOSE_SECONDS+2+2*.25,60)

    def test_abort_cleans_up_owned_subprocesses_without_acknowledging(self):
        self.spawn(code='import time;time.sleep(20)')
        end=time.monotonic()+2
        while not (self.session.path/'scopes'/MAIN/'payload.json').exists():
            self.assertLess(time.monotonic(),end);time.sleep(.005)
        self.session.abort('fixture resource failure');self.session.await_cleanup()
        self.assertEqual(self.procs[-1].wait(timeout=2),125)
        self.assertFalse((self.session.path/'scopes'/MAIN/'ack.json').exists())
    def test_payload_holders_have_a_shared_absolute_job_deadline(self):
        self.assertGreater(self.session.request['controller_deadline'],time.monotonic())
        self.assertLessEqual(self.session.request['controller_deadline']-time.monotonic(),361)
    def test_actual_poll_then_exit_race_loses_unheld_final_scope(self):
        scope=self.app/'unheld.scope';scope.mkdir();gate=Path(self.tmp.name)/'finish'
        code='from pathlib import Path;import time,shutil;p=Path('+repr(str(gate))+')\nwhile not p.exists():time.sleep(.005)\nshutil.rmtree('+repr(str(scope))+')'
        proc=subprocess.Popen([sys.executable,'-c',code]);self.procs.append(proc)
        monitor=Monitor(self.raw_capture(),self.app,scope)
        self.assertIsNone(proc.poll())
        gate.touch();self.assertEqual(proc.wait(timeout=2),0)
        result=monitor.inspect(self.raw_capture(),host_available=10*1024**3)
        self.assertIn('disappeared',result['abort'])

    def test_unexpected_emulator_exit_is_not_hidden_by_live_holder(self):
        self.spawn(EMU);self.ready(EMU);tree,result=self.inspect()
        with self.assertRaisesRegex(sc.EvidenceError,'shutdown-request'):self.session.accept(tree,result)
    def test_shutdown_after_emulator_payload_already_exited_is_not_success(self):
        self.spawn(EMU);self.ready(EMU)
        sc.record_shutdown_request(self.session.path,['-s','emulator-5554','emu','kill'])
        tree,result=self.inspect()
        with self.assertRaisesRegex(sc.EvidenceError,'verified live shutdown'):self.session.accept(tree,result)

    def test_harmful_pressure_prevents_final_release(self):
        self.spawn();self.ready();tree=self.session.capture();tree['monotonic']=self.monitor.previous['monotonic']+31
        for row in tree['rows'].values():row['pressure']='full avg10=0 total=620000'
        result=self.monitor.inspect(tree,owned=self.session.protected_scopes(),host_available=4999*1048576)
        self.assertIn('sustained',result['abort'])
        with self.assertRaisesRegex(sc.EvidenceError,'monitor rejected'):self.session.accept(tree,result)
        self.assertFalse((self.session.path/'scopes'/MAIN/'ack.json').exists())
    def test_ancestor_budget_prevents_final_release(self):
        self.spawn();self.ready();tree=self.session.capture();tree['rows'][str(self.app)]['current']=1000000000000
        result=self.monitor.inspect(tree,owned=self.session.protected_scopes(),host_available=10*1024**3)
        self.assertIn('budget',result['abort'])
        with self.assertRaisesRegex(sc.EvidenceError,'monitor rejected'):self.session.accept(tree,result)

class LauncherTests(unittest.TestCase):
    def test_emulator_scope_keeps_original_oom_and_private_environment_policy(self):
        args=['--user','--scope','--quiet','--collect','--unit='+EMU,'-p','OOMPolicy=continue','-p','MemoryMax=6G','-p','MemorySwapMax=0','--','choom','-n','1000','--','env','ANDROID_TMP=/private','emulator','-avd','pinned']
        out=sc.wrap_emulator(args,{},Path('/fixture/session'));split=args.index('--')+1;oom=out.index('1000')
        self.assertEqual(out[:split+4],args[:split+4]);self.assertEqual(out[oom+2],sys.executable)
        self.assertEqual(out[-6:],args[-6:]);self.assertIn('OOMPolicy=continue',out);self.assertIn('MemoryMax=6G',out);self.assertIn('MemorySwapMax=0',out)
    def test_non_emulator_service_commands_are_unchanged(self):
        args=['--user','--on-active=60s','/usr/bin/python3','watchdog.py'];self.assertEqual(sc.wrap_emulator(args,{},Path('/fixture')),args)
    def test_unsupported_owned_launcher_fails_before_execution(self):
        with self.assertRaises(sc.EvidenceError):sc.wrap_emulator(['--user','--scope','--unit='+EMU,'--','emulator'],{},Path('/fixture'))
    def test_main_wrapping_preserves_gate_timeout_and_appends_private_shim(self):
        with tempfile.TemporaryDirectory(dir=BASE) as tmp:
            session=sc.Session(Path(tmp)/'session',Path(tmp)/'app',MAIN,900,lambda:None)
            args=['systemd-run','--user','--scope','--unit='+MAIN,'-p','OOMPolicy=continue','--','choom','-n','1000','--','env','PATH=/original','MEM_GATE_CGROUP_MB=8192','gate','timeout','--kill-after=30s','900s','workload']
            cmd,env=session.wrap_command(args,'gate');self.assertIn('MEM_GATE_CGROUP_MB=8192',cmd);self.assertIn('timeout',cmd);self.assertEqual(cmd[-4:],args[-4:])
            self.assertTrue(next(x for x in cmd if x.startswith('PATH=')).endswith(':/original'));self.assertEqual(env['TOKKL_SCOPE_SESSION'],str(session.path));self.assertEqual(cmd[0],'/usr/bin/systemd-run')

if __name__=='__main__':unittest.main(verbosity=2)
