"""Actual maintained run_workload + real Session protocol on disk only.
External launch, cgroup membership and counters are modeled. No payload runs.
Adapted from the released0286 fixture; no old source/config/budget is imported.
"""
from pathlib import Path
from contextlib import ExitStack
from unittest.mock import patch
import ast,copy,json,os,re,shutil,subprocess,sys,time,types,xml.etree.ElementTree as ET
import scope_completion as sc
from capture_evidence import CaptureEvidence
from event_monitor_0274 import Monitor,KEYS
from scoped_results import assess_scoped,declare,COMPILE_FIRST,COMPILE_ARGS
from scoped_results import declare_benchmark_api,BENCHMARK_ARGS,BENCHMARK_LABEL,API_CLASSES,BENCHMARK_SOURCE,BENCHMARK_SOURCES,APK_PATHS
from runtime.configuration import validate,adopted_configuration,CANONICAL_GATE,sha
from prepare import prepare
from test_candidate import config,PACKAGE

CLASSES = ['com.github.e0ipso.tokkl.cache.BackgroundSendServerTest',
           'com.github.e0ipso.tokkl.notification.RunningNotificationServerTest',
           'com.github.e0ipso.tokkl.tile.RunningTileServerTest',
           'com.github.e0ipso.tokkl.timer.TimerServerTest',
           'com.github.e0ipso.tokkl.widget.WidgetServerTest']
BENCHMARK_CASES = [['com.github.e0ipso.tokkl.benchmark.StartupBenchmark','coldStart'],
                   ['com.github.e0ipso.tokkl.benchmark.TimerTapBenchmark','startAndStop']]
DEVICE_BUILD = {'brand':'Android','device':'emu64x','fingerprint':'HOST-MODEL-ONLY/API36',
                'id':'host-fixture','model':'host-fixture','type':'userdebug','version':{'sdk':36,'codename':'REL'}}


def baseline(project):
    for name in CLASSES:
        relative = 'app/src/androidTest/java/'+name.replace('.', '/')+'.kt'
        path = project/relative;path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(subprocess.check_output(['git','show','d7740f0:'+relative],cwd=PACKAGE.parent.parent))
    for name in ['scripts/instrumented-tests.sh','scripts/sift_telemetry.py']:
        path=project/name;path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(subprocess.check_output(['git','show','d7740f0:'+name],cwd=PACKAGE.parent.parent))


class WorkloadFixture:
    def __init__(self,root,scenario='valid',legacy=False,full=False,widget=False,compile_first=False,benchmark_api=False,post_input=False):
        self.root=Path(root);self.root.mkdir();self.base=self.root/'evidence'
        self.project=self.root/'project';self.project.mkdir();baseline(self.project)
        self.runtime=self.root/'runtime';self.runtime.mkdir();self.cg=self.root/'cgroups';self.app=self.cg/'app';self.app.mkdir(parents=True)
        self.temporary={'memory.high':'15032385536','memory.max':'16106127360','memory.swap.max':'2147483648'};self.fill(self.app)
        self.pid=999999989;self.scenario=scenario;self.full=full;self.legacy=legacy;self.rc=7 if scenario=='exit7' else 0;self.session=None;self.identity_rows={};self.polls=0
        self.work=self.root/'work';self.tmp=self.root/'emulator-tmp';self.work.mkdir();self.tmp.mkdir();(self.tmp/'private-image').write_text('fixture')
        self.emunit='tokkl-emulator-5554-aabbccdd.scope';self.emscope=self.app/self.emunit;self.real_path=Path
        declaration=declare(self.project,CLASSES[-1:] if widget else CLASSES)
        self.benchmark_api=benchmark_api
        if benchmark_api:
            for relative in ['app/src/androidTest/java/'+c.replace('.','/')+'.kt' for c in API_CLASSES]+[BENCHMARK_SOURCE+n for n in BENCHMARK_SOURCES]:
                path=self.project/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((PACKAGE.parent.parent/relative).read_bytes())
            for relative in APK_PATHS:
                path=self.project/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'HOST-MODEL-ONLY APK '+relative.encode())
            declaration=declare_benchmark_api(self.project,DEVICE_BUILD)
        app_inventory=declaration['inventory'][:2] if benchmark_api else declaration['inventory']
        cases=[[row['class'],method] for row in app_inventory for method in row['methods']]
        self.info={'expected_app_cases':cases,'classes':API_CLASSES if benchmark_api else declaration['classes'],'benchmark_cases':BENCHMARK_CASES,'iterations':{'START_ITERATIONS':15,'TAP_ITERATIONS':30}}
        self.configuration=config(self.root)
        self.configuration.update(project=str(self.project),run_dir=str(self.base),checked_head='d7740f0ba984f6626907700cc0d7b651f14e4285')
        pins=[Path(CANONICAL_GATE),Path('/home/e0ipso/workspace/apps/tokkl-android/scripts/mem-gate.sh'),Path('/home/e0ipso/workspace/apps/tokkl-android/scripts/memory-capacity.py')]
        pins += [self.project/'scripts/instrumented-tests.sh',self.project/'scripts/sift_telemetry.py']
        pins += [self.project/row['source'] for row in declaration['inventory']]
        if benchmark_api:pins += [self.project/row['source'] for row in declaration['sources']+declaration['apks']]
        self.configuration['input_pins']={str(path):sha(path) for path in pins}
        text=(self.project/'scripts/instrumented-tests.sh').read_text()
        for key,var in [('server_image','IMAGE'),('proxy_image','PROXY_IMAGE')]:self.configuration[key]=re.search(r"^readonly "+var+r"='([^']+)'$",text,re.M)[1]
        if full:
            job=self.configuration['plan'][0]
        else:
            job={'api':36,'label':BENCHMARK_LABEL if benchmark_api else 'scoped-app','standalone':False,'seconds':1800,
                 'args':list(BENCHMARK_ARGS) if benchmark_api else ['scripts/instrumented-tests.sh','-Pandroid.testInstrumentationRunnerArguments.class='+','.join(declaration['classes'])],
                 'result_recipe':declaration}
            self.configuration['plan']=[job]
        # Benchmark-profile label/mode refusals belong at configuration validation,
        # before preparation/adoption. Historical direct-call injection is retained
        # in the first-attempt evidence; it is not the controller's entry path.
        if benchmark_api and scenario=='wrong-label':job['label']='other-job'
        if benchmark_api and scenario=='wrong-mode':job['standalone']=True
        if compile_first:
            self.configuration['plan_recipe']=COMPILE_FIRST
            self.configuration['plan']=[{'api':0,'label':'assemble-check','args':COMPILE_ARGS,'seconds':1800,'standalone':True},job]
        if post_input:
            self._declare_post_input_policy()
        proof=prepare(self.configuration)
        adoption=self.root/'HOST-MODELED-adoption.json'
        body=dict(configuration_sha256=proof['configuration_sha256'],runtime_sha256=proof['runtime_sha256'],lease_id=self.configuration['lease_id'],approved_by='HOST-MODEL-ONLY',cheap_live_controller_fixtures_verified=True)
        if post_input:
            from runtime.post_input_policy import policy_sha256
            body['post_input_policy_sha256']=policy_sha256(self.configuration)
        adoption.write_text(json.dumps(body))
        self.adoption=adoption
        self.checked=adopted_configuration(self.base,adoption)
        if compile_first:job=self.configuration['plan'][0]
        self.job=[job['api'],job['label'],copy.deepcopy(job['args']),job['seconds'],job['standalone']]
        if scenario=='wrongfilter':self.job[2][1]='-Pandroid.testInstrumentationRunnerArguments.class=other.Test'
        if scenario=='wrongpackage':self.job[2][1]='-Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.cache'
        if scenario=='undeclared-macro':self.job[2].append(':macrobenchmark:connectedBenchmarkAndroidTest')
        if scenario=='wrong-api':self.job[0]=31
        if scenario=='wrong-seconds':self.job[3]+=1
        if scenario=='wrong-label':self.job[1]='other-job'
        self.ns={'Path':self.controller_path,'base':self.base,'project':self.project,'app':self.app,'runtime':self.runtime,'gate':CANONICAL_GATE,'tag':'fixture','state':{'tests':[]},'save':self.save,'limits':lambda:dict(self.temporary),'temporary':self.temporary,'owned_scope':None,'now':lambda:'fixture','time':time,'json':json,'os':os,'re':re,'shutil':shutil,'ET':ET,'Monitor':Monitor,'capture':CaptureEvidence(self.capture,self.base/'failure-evidence'),'EvidenceError':sc.EvidenceError,'assess_scoped':assess_scoped,'config':self.checked}
        self.ns['Session']=self.make_session
        self.ns['subprocess']=types.SimpleNamespace(Popen=self.launch,run=self.control,STDOUT=-2,TimeoutExpired=TimeoutError)
        source=subprocess.check_output(['git','show','ec509ff:scripts/device_verification/runtime/controller.py'],cwd=PACKAGE.parent.parent,text=True) if legacy else (PACKAGE/'runtime/controller.py').read_text()
        tree=ast.parse(source)
        for name in ['snapshot','run_workload']:
            node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name)
            exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual-maintained-'+name+'>','exec'),self.ns)
    def _declare_post_input_policy(self):
        from runtime import post_input_policy as pip
        roots, paths = pip.reviewed()
        for root in roots:
            Path(root).mkdir(parents=True, exist_ok=True)
        members = []
        for path in paths:
            Path(path).write_bytes(b'frozen-gradle-index\n'+path.encode())
            members.append(path)
        extra = Path(roots[0])/'com.example'/'lib'/'1.0'/'lib-1.0.jar'
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_bytes(b'frozen-member')
        side = Path(roots[1])/'descriptor.bin'
        side.write_bytes(b'frozen-descriptor')
        members.extend([str(extra), str(side)])
        pins = self.configuration['input_pins']
        for path in members:
            pins[path] = sha(path)
        rows = []
        for path in paths:
            info = os.lstat(path)
            rows.append({'path': path, 'sha256': pins[path], 'bytes': info.st_size,
                         'device': info.st_dev, 'inode': info.st_ino})
        label = next(job['label'] for job in self.configuration['plan']
                     if job.get('result_recipe', {}).get('profile') == 'scoped-app-classes-v1')
        self.index_paths = tuple(paths)
        self.member_paths = tuple(sorted(members))
        self.descriptor_path = str(side)
        self.index_bytes = {path: Path(path).read_bytes() for path in paths}
        self.configuration['post_input_policy'] = {
            'profile': pip.PROFILE, 'job': label, 'indexes': rows,
            'dependency_membership': {'roots': roots, 'members': len(members),
                                      'sha256': pip.digest(sorted(members))}}
    def controller_path(self,*args):
        if args and str(args[0])=='/sys/fs/cgroup':return self.cg.joinpath(*args[1:])
        if args and str(args[0])=='/proc' and len(args)>2 and args[2]=='environ':return self.root/'fixture-environ'
        if args and str(args[0])=='/tmp/android-e0ipso':
            p=self.root/'shared-android';p.mkdir(exist_ok=True);return p
        return self.real_path(*args)
    def fill(self,path):
        path.mkdir(exist_ok=True)
        for k,v in dict(self.temporary,**{'memory.current':'100','memory.peak':'100','memory.events':'high 0\nmax 0\noom 0\noom_kill 0\n','memory.events.local':'high 0\nmax 0\noom 0\noom_kill 0\n','memory.pressure':'some avg10=0 total=0\nfull avg10=0 total=0','memory.swap.current':'0','cgroup.procs':'999999991\n'}).items():(path/k).write_text(v)
    def save(self):(self.base/'controller-state.json').write_text(json.dumps(self.ns['state'],indent=2)+'\n')
    def capture(self,**kwargs):
        rows={}
        paths=[self.cg,self.app]+[p for p in self.app.iterdir() if p.is_dir()]
        for p in paths:rows[str(p)]={'inode':p.stat().st_ino,'time':'fixture','stable':True,'events':dict.fromkeys(KEYS,0),'local':dict.fromkeys(KEYS,0),'current':100,'high':'15032385536','max':'16106127360','pressure':'full avg10=0 total=0'}
        if self.scenario in ['owned-high','owned-oom'] and self.identity_rows:
            key='high' if self.scenario=='owned-high' else 'oom_kill'
            rows[str(self.app/self.session.main_unit)]['local'][key]=1
            for p in [self.cg,self.app,self.app/self.session.main_unit]:rows[str(p)]['events'][key]=1
        return {'root':str(self.cg),'rows':rows,'errors':[],'time':'fixture','monotonic':time.monotonic(),'duration':0}
    def identity(self,pid):
        row=self.identity_rows.get(pid)
        if not row:return None
        folder=self.session.path/'scopes'/Path(row['cgroup']).name
        if (folder/'ack.json').exists():
            ack=sc.load(folder/'ack.json');ready=sc.load(folder/'ready.json');sc.write(folder/'released.json',dict(ack,exit_code=ready['exit_code'],released_monotonic=time.monotonic()));shutil.rmtree(row['cgroup']);self.identity_rows.pop(pid);return None
        return dict(row)
    def make_session(self,path,app,unit,seconds,capture_fn):
        self.session=sc.Session(path,app,unit,seconds,capture_fn,identity_reader=self.identity,members_reader=lambda scope:{pid for pid,row in self.identity_rows.items() if row['cgroup']==str(scope)},close_seconds=.1)
        return self.session
    def register(self,unit,pid,emulator=False):
        scope=self.app/unit;self.fill(scope);folder=self.session.path/'scopes'/unit;folder.mkdir();now=time.monotonic();reg={'nonce':self.session.request['nonce'],'unit':unit,'scope':str(scope),'pid':pid,'start_ticks':17,'inode':scope.stat().st_ino}
        self.identity_rows[pid]={'state':'S','start_ticks':17,'cgroup':str(scope)};sc.write(folder/'registered.json',reg)
        ready=dict(reg,exit_code=self.rc if not emulator else 0,only_holder_remains=True,payload_pid=pid+20,payload_start_ticks=25,payload_ended_monotonic=now,ready_monotonic=now)
        if emulator:sc.write(folder/'shutdown-request.json',{'nonce':reg['nonce'],'unit':unit,'payload_live':True,'payload':[ready['payload_pid'],25],'requested_monotonic':now-.001})
        if self.scenario!='missing-final':sc.write(folder/'ready.json',ready)
    def xml(self,path,cases,problem=None):
        path.parent.mkdir(parents=True,exist_ok=True);suite=ET.Element('testsuite',tests=str(len(cases)),failures='0',errors='0',skipped='0')
        for i,(cls,name) in enumerate(cases):
            node=ET.SubElement(suite,'testcase',classname=cls,name=name)
            if i==0 and problem in ['failure','error','skipped']:ET.SubElement(node,problem);suite.set({'failure':'failures','error':'errors','skipped':'skipped'}[problem],'1')
        if problem=='partial':suite.set('tests',str(len(cases)+1))
        ET.ElementTree(suite).write(path)
        if problem=='malformed':path.write_text('<testsuite tests="23"><testcase')
        if problem=='stale':os.utime(path,(1,1))
    def results(self):
        info=self.info;cases=copy.deepcopy(info['expected_app_cases']);mode=self.scenario
        if self.job[4]:
            self.xml(self.project/'app/build/test-results/testDebugUnitTest/TEST-host-fixture.xml',[['fixture.HostUnit','syntheticCase']])
            lint=self.project/'app/build/reports/lint-results-debug.xml';lint.parent.mkdir(parents=True,exist_ok=True);lint.write_text('<issues/>');return
        if mode=='missing':return
        if mode=='empty':cases=[]
        if mode=='missing-class':cases=[c for c in cases if c[0]!=info['classes'][1]]
        if mode=='missing-case':cases=cases[:-1]
        if mode=='wrong-case':cases[-1][1]='wrongMethod'
        if mode=='wrong-class':cases[-1][0]='other.Test'
        if mode=='duplicate':cases.append(cases[0])
        xml=self.project/'app/build/outputs/androidTest-results/connected/debug/TEST-app.xml';self.xml(xml,cases,mode)
        if self.benchmark_api:
            self.benchmark_results();return
        if self.full:
            bench=copy.deepcopy(info['benchmark_cases'])
            if mode=='missing-one-benchmark':bench=bench[:1]
            if mode=='missing-both-benchmarks':bench=[]
            if bench:self.xml(self.project/'macrobenchmark/build/outputs/androidTest-results/connected/benchmark/TEST-bench.xml',bench,'failure' if mode=='benchmark-failure' else 'stale' if mode=='stale-benchmark' else None)
            if mode=='missing-metrics':return
            data=[]
            for cls,name in info['benchmark_cases']:
                n=info['iterations']['START_ITERATIONS' if name=='coldStart' else 'TAP_ITERATIONS'];names=['timeToInitialDisplayMs'] if name=='coldStart' else ['TimerStartFirstMs','TimerStopFirstMs'];runs=[100.]*n
                if mode=='insufficient-samples':runs=runs[:-1]
                item={'className':cls,'name':name,'repeatIterations':n,'metrics':{key:{'runs':runs,'median':100.} for key in names}}
                if mode=='wrong-iterations':item['repeatIterations']=n-1
                data.append(item)
            path=self.project/'macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/fixture-benchmarkData.json';path.parent.mkdir(parents=True);path.write_text(json.dumps({'benchmarks':data}))
            if mode=='stale-metrics':os.utime(path,(1,1))
    def benchmark_results(self):
        """Synthetic disk evidence only, read by the actual maintained collector."""
        self.xml(self.project/'macrobenchmark/build/outputs/androidTest-results/connected/benchmark/TEST-bench.xml',BENCHMARK_CASES)
        folder=self.project/'macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark'
        folder.mkdir(parents=True,exist_ok=True)
        data=[]
        for case,(cls,name) in enumerate(BENCHMARK_CASES):
            n=15 if case==0 else 30;outputs=[]
            for i in range(n):
                filename=f'{cls.rsplit(".",1)[1]}_{name}_iter{i:03d}_2026-10-23-00-00-00.perfetto-trace'
                # Trace.packet { timestamp: unique value }. Wire framing fixture,
                # deliberately no claim of real device/trace-processor evidence.
                (folder/filename).write_bytes(bytes([10,2,64,case*30+i+1]))
                outputs.append({'type':'PerfettoTrace','label':f'Trace Iteration {i}','filename':filename})
            keys=['timeToInitialDisplayMs'] if case==0 else ['TimerStartFirstMs','TimerStopFirstMs']
            data.append({'className':cls,'name':name,'params':{},'sampledMetrics':{},'warmupIterations':0,
                         'repeatIterations':n,'metrics':{key:{'runs':[100.]*n,'minimum':100.,'maximum':100.,'median':100.} for key in keys},'profilerOutputs':outputs})
        (folder/'fixture-benchmarkData.json').write_text(json.dumps({'context':{'build':DEVICE_BUILD},'benchmarks':data}))
    def launch(self,cmd,stdout,stderr,cwd,env):
        # This is the only Popen implementation visible to the actual controller.
        # Record argv, never execute it. Session.wrap_command is real and checked.
        self.work.mkdir(exist_ok=True);self.tmp.mkdir(exist_ok=True)
        (self.base/'wrapped-argv.json').write_text(json.dumps(cmd,indent=2)+'\n')
        self.register(self.session.main_unit,999999990)
        if not self.job[4]:self.register(self.emunit,999999991,True)
        marker=self.runtime/'tokkl-instrumented-tests-1000-slot-1.lock'
        if not self.job[4]:marker.write_text(str(self.project)+', work directory '+str(self.work)+', temporary emulator data '+str(self.tmp)+', in '+self.emunit)
        (self.root/'fixture-environ').write_bytes(b'ANDROID_TMP='+str(self.tmp).encode()+b'\0')
        line='emulator-5554 runs tokkl-api36 on API 36' if self.scenario!='wrong-sdk-log' else 'emulator-5554 runs tokkl-api35 on API 35'
        stdout.write(('' if self.job[4] else line+'\n')+'BUILD SUCCESSFUL\n');stdout.flush();self.results()
        if self.scenario=='stale-log':os.utime(stdout.name,(1,1))
        return self
    def control(self,cmd,**kwargs):
        if cmd[:3]==['systemctl','--user','kill']:
            assert cmd[-1]==self.session.main_unit
            for row in list(self.identity_rows.values()):shutil.rmtree(row['cgroup'])
            self.identity_rows.clear();return types.SimpleNamespace(returncode=0,stdout='')
        assert cmd[:3]==['systemctl','--user','show'],cmd
        return types.SimpleNamespace(returncode=0,stdout='/app/'+self.emunit+'\n')
    def poll(self):self.polls+=1;return None if self.polls==1 else self.rc
    def wait(self,**kwargs):
        shutil.rmtree(self.work);shutil.rmtree(self.tmp);return self.rc
    def context(self):
        # Prevent SDK discovery and all real waits inside the disk fixture.
        stack=ExitStack()
        standin=self.root/'adb-standin';standin.write_text('#!/bin/sh\nexit 0\n');standin.chmod(0o700)
        original_which=shutil.which
        stack.enter_context(patch('shutil.which',lambda name,**kw:str(standin) if name=='adb' else original_which(name,**kw)))
        stack.enter_context(patch('time.sleep',lambda _:None))
        return stack
    def run(self):
        with self.context():
            return self.ns['run_workload'](*self.job)
