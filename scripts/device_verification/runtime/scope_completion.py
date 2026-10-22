"""Finite final-evidence protocol. Importing this module performs no host action.

Payloads finish while a small holder keeps each owned scope populated. Only a
clean, post-payload monitor observation authorizes release. Certified final rows
remain explicit frozen evidence after verified closure; counter baselines never
reset. Fresh ancestor observations still reject unaccounted late events.
"""
from pathlib import Path
import copy,hashlib,json,math,os,re,shutil,signal,subprocess,sys,time,uuid

HOLD_SECONDS=20
CLOSE_SECONDS=5
POST_SECONDS=1
EMULATOR=re.compile(r'^tokkl-emulator-[0-9]+-[0-9a-f]+\.scope$')
class EvidenceError(RuntimeError): pass

def require(condition,message):
    if not condition:raise EvidenceError(message)

def load(path):
    try:return json.loads(Path(path).read_text())
    except (OSError,ValueError) as e:raise EvidenceError('missing/malformed protocol evidence: '+str(path)) from e

def digest(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def write(path,value):
    path=Path(path);require(not path.exists(),'protocol record already exists: '+str(path))
    temp=path.with_name(path.name+'.'+uuid.uuid4().hex+'.writing')
    with temp.open('x') as out:
        os.chmod(temp,0o600);json.dump(value,out,sort_keys=True);out.write('\n');out.flush();os.fsync(out.fileno())
    os.replace(temp,path)
    fd=os.open(path.parent,os.O_RDONLY|os.O_DIRECTORY)
    try:os.fsync(fd)
    finally:os.close(fd)

def process_identity(pid):
    try:
        stat=Path('/proc',str(pid),'stat').read_text().rsplit(')',1)[1].split()
        if stat[0]=='Z':return {'pid':pid,'start_ticks':int(stat[19]),'state':'Z','cgroup':None}
        group=next(line.split(':',2)[2] for line in Path('/proc',str(pid),'cgroup').read_text().splitlines() if line.startswith('0::'))
        return {'pid':pid,'start_ticks':int(stat[19]),'state':stat[0], 'cgroup':str(Path('/sys/fs/cgroup')/group.lstrip('/'))}
    except FileNotFoundError:return None
    except (OSError,ValueError,IndexError,StopIteration) as e:raise EvidenceError('inaccessible process identity') from e

def members(scope):
    result=set();deadline=time.monotonic()+.25
    def visit(path):
        require(time.monotonic()<deadline,'membership read deadline exhausted')
        result.update(int(x) for x in (path/'cgroup.procs').read_text().split())
        for child in path.iterdir():
            require(time.monotonic()<deadline,'membership read deadline exhausted')
            if child.is_dir():visit(child)
    try:visit(Path(scope))
    except (OSError,ValueError) as e:raise EvidenceError('missing final scope membership') from e
    return result

def child_exit(code):return code if code>=0 else 128-code

def validate_request(request):
    for key,maximum in [('command_seconds',7235),('hold_seconds',HOLD_SECONDS),('close_seconds',CLOSE_SECONDS)]:
        value=request.get(key)
        require(isinstance(value,(int,float)) and not isinstance(value,bool) and math.isfinite(value) and 0<value<=maximum,'invalid finite protocol request: '+key)
    value=request.get('controller_deadline')
    require(isinstance(value,(int,float)) and not isinstance(value,bool) and math.isfinite(value) and time.monotonic()<value<=time.monotonic()+7560,'invalid absolute protocol deadline')
    require(isinstance(request.get('nonce'),str) and re.fullmatch('[0-9a-f]{32}',request['nonce']),'invalid protocol nonce')
    require(isinstance(request.get('app'),str) and Path(request['app']).is_absolute(),'invalid protocol app path')

def sanitized_membership(scope,pids):
    rows=[]
    for pid in sorted(pids):
        try:
            stat=Path('/proc',str(pid),'stat').read_text().rsplit(')',1)[1].split()
            rows.append({'pid':pid,'ppid':int(stat[1]),'comm':Path('/proc',str(pid),'comm').read_text().strip(),'state':stat[0],'start_ticks':int(stat[19]),'cgroup':Path('/proc',str(pid),'cgroup').read_text().strip()})
        except (OSError,ValueError,IndexError) as error:
            rows.append({'pid':pid,'identity_error':type(error).__name__})
    return {'scope':str(scope),'monotonic':time.monotonic(),'members':rows}

def holder_main(argv=None,identity_reader=None,members_reader=None):
    """CLI holder. Injectable readers are used only by owned host fixtures."""
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--session',required=True);parser.add_argument('--unit',required=True);parser.add_argument('--gate-wait-seconds',type=int,choices=[0,300],default=0);parser.add_argument('command',nargs=argparse.REMAINDER)
    args=parser.parse_args(argv);command=args.command[1:] if args.command[:1]==['--'] else args.command
    identity_reader=identity_reader or process_identity;members_reader=members_reader or members
    session=Path(args.session);request=load(session/'request.json');validate_request(request)
    require(args.unit==request['main_unit'] or EMULATOR.fullmatch(args.unit),'unrecognized owned unit')
    require(command,'missing payload command')
    scope=Path(request['app'])/args.unit;folder=session/'scopes'/args.unit
    staging=session/('registering-'+uuid.uuid4().hex);staging.mkdir(mode=0o700)
    child=None;prior_handlers={};membership_first=None
    def check_abort():
        if (session/'abort.json').exists():
            record=load(session/'abort.json');require(record['nonce']==request['nonce'],'abort identity mismatch')
            raise EvidenceError('controller aborted: '+record['reason'])
    def interrupted(signum,frame):raise InterruptedError('holder received signal '+str(signum))
    try:
        for signum in [signal.SIGTERM,signal.SIGINT]:
            prior_handlers[signum]=signal.signal(signum,interrupted)
        identity=identity_reader(os.getpid());require(identity is not None,'holder identity unavailable')
        require(identity['cgroup']==str(scope),'holder not in expected cgroup')
        registration={'nonce':request['nonce'],'unit':args.unit,'scope':str(scope),'inode':scope.stat().st_ino,'pid':os.getpid(),'start_ticks':identity['start_ticks'],'registered_monotonic':time.monotonic()}
        write(staging/'registered.json',registration)
        os.rename(staging,folder)
        # Retain inherited serialization descriptors through holder and payload.
        child=subprocess.Popen(command,start_new_session=True,close_fds=False)
        payload_identity=process_identity(child.pid)
        write(folder/'payload.json',{'pid':child.pid,'start_ticks':payload_identity['start_ticks'] if payload_identity else None})
        limit=min(time.monotonic()+request['command_seconds']+args.gate_wait_seconds,request['controller_deadline']-2)
        while child.poll() is None:
            check_abort()
            require(time.monotonic()<limit,'payload deadline exhausted');time.sleep(.01)
        payload_ended=time.monotonic();status=child.returncode;end=min(time.monotonic()+request['hold_seconds'],request['controller_deadline']-2)
        while True:
            remaining=members_reader(scope)
            if remaining=={os.getpid()}:break
            if membership_first is None:
                membership_first=sanitized_membership(scope,remaining)
                write(folder/'membership-first.json',membership_first)
            check_abort()
            require(time.monotonic()<end,'owned scope members remain; no final quiescence');time.sleep(.01)
        ready=dict(registration,payload_pid=child.pid,payload_start_ticks=payload_identity['start_ticks'] if payload_identity else None,payload_ended_monotonic=payload_ended,payload_status=status,exit_code=child_exit(status),ready_monotonic=time.monotonic(),only_holder_remains=True)
        write(folder/'ready.json',ready)
        while not (folder/'ack.json').exists():
            check_abort()
            require(time.monotonic()<end,'final capture acknowledgement timed out');time.sleep(.01)
        ack=load(folder/'ack.json');final=load(folder/'final.json')
        require(ack['nonce']==request['nonce'] and ack['holder']==[registration['pid'],registration['start_ticks']],'acknowledgement identity mismatch')
        require(ack['final_digest']==digest(final) and final['ready_digest']==digest(ready),'final evidence digest mismatch')
        require(final['snapshot_monotonic']>=ready['ready_monotonic'],'final snapshot predates payload completion')
        require(members_reader(scope)=={os.getpid()},'new payload member after final capture')
        require(time.monotonic()<end,'acknowledgement arrived after hold deadline')
        write(folder/'released.json',{'nonce':request['nonce'],'holder':ack['holder'],'final_digest':ack['final_digest'],'exit_code':ready['exit_code'],'released_monotonic':time.monotonic()})
        return ready['exit_code']
    except BaseException as error:
        if child is not None and child.poll() is None:
            try:
                os.killpg(child.pid,signal.SIGTERM);child.wait(timeout=1)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid,signal.SIGKILL);child.wait(timeout=1)
            except ProcessLookupError:pass
        try:membership_last=sanitized_membership(scope,members_reader(scope))
        except (OSError,EvidenceError) as diagnostic_error:membership_last={'scope':str(scope),'error':type(diagnostic_error).__name__}
        try:write(folder/'failure.json',{'error':type(error).__name__+': '+str(error),'time':time.monotonic(),'payload_status':child.poll() if child is not None else None,'membership_first':membership_first,'membership_last':membership_last})
        except (OSError,EvidenceError):pass
        return 125
    finally:
        for signum,previous in prior_handlers.items():signal.signal(signum,previous)

class Session:
    def __init__(self,path,app,main_unit,seconds,capture_fn,*,identity_reader=None,members_reader=None,hold_seconds=HOLD_SECONDS,close_seconds=CLOSE_SECONDS,real_systemd='/usr/bin/systemd-run'):
        require(type(seconds) is int and 0<seconds<=7200,'invalid finite job duration')
        require(isinstance(hold_seconds,(int,float)) and not isinstance(hold_seconds,bool) and 0<hold_seconds<=20,'invalid hold deadline')
        require(isinstance(close_seconds,(int,float)) and not isinstance(close_seconds,bool) and 0<close_seconds<=5,'invalid closure deadline')
        require(Path(main_unit).name==main_unit and main_unit.endswith('.scope'),'invalid main unit')
        self.path=Path(path);self.path.mkdir(mode=0o700,parents=True,exist_ok=False);(self.path/'scopes').mkdir(mode=0o700);(self.path/'intents').mkdir(mode=0o700)
        self.app=Path(app);self.main_unit=main_unit;self.capture_fn=capture_fn;self.identity_reader=identity_reader or process_identity;self.members_reader=members_reader or members
        self.request={'nonce':uuid.uuid4().hex,'app':str(self.app),'main_unit':main_unit,'command_seconds':seconds+35,'controller_deadline':time.monotonic()+seconds+360,'hold_seconds':hold_seconds,'close_seconds':close_seconds,'real_systemd':real_systemd}
        write(self.path/'request.json',self.request);self.certified={};self.retired={};self.last_raw=None
    def registrations(self):
        result={}
        for folder in (self.path/'scopes').iterdir():
            require(folder.name==self.main_unit or EMULATOR.fullmatch(folder.name),'unknown protocol scope')
            require(not (folder/'failure.json').exists(),'holder reported failure: '+str(folder))
            registration=load(folder/'registered.json')
            require(registration['nonce']==self.request['nonce'] and registration['unit']==folder.name,'registration identity mismatch')
            require(registration['scope']==str(self.app/folder.name),'registration cgroup mismatch')
            result[folder.name]=registration
        return result
    def protected_scopes(self):
        units=set(self.registrations())|{self.main_unit}
        for intent in (self.path/'intents').glob('*.json'):
            item=load(intent);require(item['nonce']==self.request['nonce'],'launch intent identity mismatch')
            require(item['unit']==self.main_unit or EMULATOR.fullmatch(item['unit']),'unknown launch intent')
            units.add(item['unit'])
        return [self.app/unit for unit in sorted(units)]
    def wrap_command(self,command,gate):
        cmd=list(command);require(cmd[0] in ['systemd-run','/usr/bin/systemd-run'],'unexpected workload launcher')
        require('--scope' in cmd and '--unit='+self.main_unit in cmd,'workload scope differs from protocol')
        require(cmd.count(gate)==1,'memory gate boundary unavailable')
        start=cmd.index(gate);require(start+1<len(cmd),'missing gated workload')
        require(cmd[start+1]=='timeout','existing timeout must remain inside holder')
        module=Path(__file__).resolve();shim=module.parent/'shim'
        original_path=next((value.split('=',1)[1] for value in cmd if value.startswith('PATH=')),os.environ.get('PATH',''))
        real_adb=shutil.which('adb',path=original_path)
        if any(Path(arg).name=='instrumented-tests.sh' for arg in cmd):
            require(real_adb is not None and os.access(real_adb,os.X_OK),'instrumented launcher SDK adb path unavailable')
        write(self.path/'tools.json',{'real_adb':real_adb})
        cmd[start:start]=[sys.executable,str(module),'hold','--session',str(self.path),'--unit',self.main_unit,'--gate-wait-seconds','300','--']
        cmd[0]=self.request['real_systemd']
        for i,value in enumerate(cmd):
            if value.startswith('PATH='):cmd[i]='PATH='+str(shim)+':'+value[5:]
        env=dict(os.environ,TOKKL_SCOPE_SESSION=str(self.path));env['PATH']=str(shim)+':'+env.get('PATH','')
        return cmd,env
    def capture(self,root=None,*,deadline=None):
        # No acknowledgements occur while a tree is being read. Every normal
        # release is synchronously verified closed before the next traversal.
        kwargs={}
        if root is not None:kwargs['root']=Path(root)
        if deadline is not None:kwargs['deadline']=deadline
        tree=self.capture_fn(**kwargs);view=copy.deepcopy(tree);frozen=[]
        for unit,certificate in self.retired.items():
            for path,row in certificate['rows'].items():
                require(path not in view['rows'],'retired scope reappeared: '+path)
                require(not Path(path).exists(),'retired scope path unexpectedly exists')
                view['rows'][path]=copy.deepcopy(row);frozen.append(path)
        # Errors are never removed or ignored, including an ENOENT.
        view['scope_completion']={'raw_rows':sorted(tree['rows']),'frozen_final_rows':frozen,'raw_errors':tree['errors']}
        self.last_raw=tree
        return view
    def accept(self,tree,assessment):
        require(assessment['abort'] is None,'resource monitor rejected final evidence')
        require(not tree['errors'],'inaccessible final resource snapshot')
        for unit,registration in self.registrations().items():
            if unit in self.certified:continue
            folder=self.path/'scopes'/unit
            if not (folder/'ready.json').exists():continue
            ready=load(folder/'ready.json');scope=str(self.app/unit)
            require(all(ready[key]==value for key,value in registration.items()),'ready identity mismatch')
            require(type(ready['exit_code']) is int and 0<=ready['exit_code']<=255,'invalid payload exit status')
            require(ready['only_holder_remains'] is True,'missing payload quiescence')
            if EMULATOR.fullmatch(unit):
                shutdown=load(folder/'shutdown-request.json')
                require(shutdown['nonce']==self.request['nonce'] and shutdown['unit']==unit and shutdown['payload_live'] is True,'emulator exit lacks a verified live shutdown request')
                require(shutdown['payload']==[ready['payload_pid'],ready['payload_start_ticks']] and shutdown['requested_monotonic']<=ready['payload_ended_monotonic'],'emulator exited before verified shutdown request')

            # An observation begun before payload completion is never final.
            if tree['monotonic']<ready['ready_monotonic']:continue
            row=tree['rows'].get(scope);require(row is not None and row['stable'],'missing/ambiguous final scope row')
            identity=self.identity_reader(registration['pid'])
            require(identity is not None and identity['state']!='Z' and identity['start_ticks']==registration['start_ticks'] and identity['cgroup']==scope,'final holder process is not live in its scope')
            require(Path(scope).stat().st_ino==registration['inode']==row['inode'],'final cgroup identity changed')
            require(self.members_reader(Path(scope))=={registration['pid']},'payload members remain at final capture')
            rows={p:copy.deepcopy(r) for p,r in tree['rows'].items() if p==scope or p.startswith(scope+'/')}
            final={'ready_digest':digest(ready),'snapshot_monotonic':tree['monotonic'],'snapshot_time':tree['time'],'rows':rows,'monitor_assessment_digest':digest(assessment)}
            write(folder/'final.json',final)
            ack={'nonce':self.request['nonce'],'holder':[registration['pid'],registration['start_ticks']],'final_digest':digest(final)}
            write(folder/'ack.json',ack)
            self.certified[unit]={'rows':rows,'ready':ready,'final':final,'ack':ack}
            self._settle(unit)
    def _settle(self,unit):
        certificate=self.certified[unit];ready=certificate['ready'];folder=self.path/'scopes'/unit;end=time.monotonic()+self.request['close_seconds']
        while True:
            require(not (folder/'failure.json').exists(),'holder rejected release')
            identity=self.identity_reader(ready['pid'])
            dead=identity is None or identity['start_ticks']!=ready['start_ticks'] or identity['state']=='Z'
            if (folder/'released.json').exists():
                released=load(folder/'released.json')
                require(released['nonce']==self.request['nonce'] and released['holder']==certificate['ack']['holder'] and released['final_digest']==certificate['ack']['final_digest'] and released['exit_code']==ready['exit_code'],'release proof mismatch')
                require(released['released_monotonic']>=certificate['final']['snapshot_monotonic'],'release proof predates final capture')
                if dead and not Path(ready['scope']).exists():
                    certificate['closed_monotonic']=time.monotonic();certificate['released']=released;self.retired[unit]=certificate
                    write(folder/'closed.json',certificate);return
            require(time.monotonic()<end,'scope closure not verified within finite deadline');time.sleep(.01)
    def abort(self,reason):
        path=self.path/'abort.json'
        if not path.exists():write(path,{'nonce':self.request['nonce'],'reason':str(reason),'time':time.monotonic()})
        else:require(load(path)['nonce']==self.request['nonce'],'abort identity mismatch')
    def await_cleanup(self,deadline=None):
        end=min(time.monotonic()+self.request['close_seconds'], deadline if deadline is not None else float('inf'))
        while True:
            remaining=[]
            for folder in (self.path/'scopes').iterdir():
                registration=load(folder/'registered.json');identity=self.identity_reader(registration['pid'])
                live=identity is not None and identity['start_ticks']==registration['start_ticks'] and identity['state']!='Z'
                if live or Path(registration['scope']).exists():remaining.append(registration['scope'])
            if not remaining:return
            require(time.monotonic()<end,'owned protocol scopes remain after cleanup: '+','.join(remaining));time.sleep(.01)
    def finalize(self,exit_code,monitor,host_available,deadline=None):
        # Must run even if proc.poll() skipped the monitoring loop entirely.
        registrations=self.registrations()
        require(self.main_unit in registrations,'missing main scope registration')
        require({str(p) for p in self.protected_scopes()}=={str(self.app/u) for u in registrations},'launch intent lacks registered final evidence')
        require(set(registrations)==set(self.retired),'missing final scope capture/release evidence')
        require(exit_code==self.retired[self.main_unit]['ready']['exit_code'],'launcher/payload exit status mismatch')
        observations=[];end=min(time.monotonic()+POST_SECONDS,deadline if deadline is not None else float('inf'))
        for _ in range(2):
            require(time.monotonic()<end,'post-closure evidence deadline exhausted')
            tree=self.capture(deadline=end);assessment=monitor.inspect(tree,owned=self.protected_scopes(),host_available=host_available())
            observations.append({'tree':tree,'assessment':assessment})
            if assessment['abort']:
                write(self.path/'post-closure-failure.json',{'observations':observations})
                raise EvidenceError('post-closure resource guard: '+assessment['abort'])
            require(time.monotonic()<end,'post-closure evidence deadline exhausted')
            require(monitor.previous['monotonic']>=max(c['closed_monotonic'] for c in self.retired.values()),'post-closure observation is stale')
        write(self.path/'completed.json',{'exit_code':exit_code,'observations':observations,'retired_scopes':list(self.retired)})
        return exit_code

def wrap_emulator(args,request,session):
    args=list(args)
    unit=next((arg.split('=',1)[1] for arg in args if arg.startswith('--unit=')),None)
    if '--scope' not in args or not unit or not EMULATOR.fullmatch(unit):return args
    require('--user' in args and '--' in args,'unsupported emulator scope launcher')
    begin=args.index('--')+1;payload=args[begin:]
    if payload and payload[0]=='env':
        # The production launcher owns these two private directories. Do not
        # accept env options, arbitrary assignments, alternate commands or flags.
        properties=[];i=0
        while i<begin-1:
            arg=args[i]
            if arg=='-p':
                require(i+1<begin-1,'missing emulator scope property')
                properties.append(args[i+1]);i+=2;continue
            require(arg in ['--user','--scope','--quiet','--collect','--unit='+unit], 'unknown emulator scope option')
            i+=1
        required=['OOMPolicy=continue','MemoryMax=6G','MemorySwapMax=0']
        require(all(properties.count(x)==1 for x in required),'unsafe emulator scope resource envelope')
        rest=[x for x in properties if x not in required]
        require(len(rest)<=1 and all(re.fullmatch(r'PartOf=[A-Za-z0-9_.:@\\-]+\.(?:scope|service)',x) for x in rest),'unknown emulator scope property')
        require(len(payload)>2 and payload[1].startswith('ANDROID_TMP='),'missing private emulator tmp')
        private=Path(payload[1].split('=',1)[1])
        require(private.is_absolute() and private.name=='emulator-tmp' and '..' not in private.parts and '\n' not in str(private),'unsafe private emulator tmp')
        insert=begin+2
        if payload[2].startswith('XDG_RUNTIME_DIR='):
            require(payload[2]=='XDG_RUNTIME_DIR='+str(private.parent/'runtime'),'unsafe private emulator runtime')
            insert+=1
        command=args[insert:];port=unit.split('-')[2]
        require(len(command) in [17,18] and command[:2]==['emulator','-avd'] and bool(command[2]) and not command[2].startswith('-'),'unknown emulator command')
        require(command[3:10]==['-port',port,'-memory','2560','-cores','4','-gpu'] and command[10] in ['lavapipe','swiftshader_indirect'],'unknown emulator resource arguments')
        tail=command[11:]
        require(tail in [ ['-no-window','-no-audio','-no-boot-anim','-no-snapshot','-accel','on'], ['-no-window','-no-audio','-no-boot-anim','-no-snapshot','-read-only','-accel','on'] ],'unknown emulator launch arguments')
    else:
        # Preserve the existing explicitly OOM-wrapped fixture/legacy contract.
        require(payload[:4]==['choom','-n','1000','--'],'emulator OOM wrapper unavailable')
        legacy=payload[4:]
        require(len(legacy)>=3 and legacy[0]=='env' and legacy[1].startswith('ANDROID_TMP=/'),'unknown legacy emulator environment')
        command=legacy[2:]
        if command and command[0].startswith('XDG_RUNTIME_DIR=/'):command=command[1:]
        require(command and command[0]=='emulator' and (len(command)==1 or (len(command)==3 and command[1]=='-avd' and bool(command[2]))),'unknown legacy emulator command')
        insert=begin+4
    args[insert:insert]=[sys.executable,str(Path(__file__).resolve()),'hold','--session',str(session),'--unit',unit,'--']
    return args

def record_shutdown_request(session,args,identity_reader=None):
    identity_reader=identity_reader or process_identity
    # Preserve unexpected-emulator-exit failure even while its holder is live.
    if len(args)!=4 or args[0]!='-s' or args[2:]!=['emu','kill'] or not re.fullmatch('emulator-[0-9]+',args[1]):return
    port=args[1].split('-')[1];request=load(Path(session)/'request.json')
    candidates=[p for p in (Path(session)/'intents').glob('*.json') if p.stem.startswith('tokkl-emulator-'+port+'-')]
    # Host fixture direct holders also have a registration; normal launchers
    # publish the intent before executing real systemd-run.
    candidates += [p/'registered.json' for p in (Path(session)/'scopes').glob('tokkl-emulator-'+port+'-*.scope')]
    units={load(p)['unit'] for p in candidates}
    require(len(units)<=1,'ambiguous emulator shutdown ownership')
    if not units:return
    unit=next(iter(units));folder=Path(session)/'scopes'/unit
    payload=load(folder/'payload.json');identity=identity_reader(payload['pid'])
    live=identity is not None and identity['state']!='Z' and identity['start_ticks']==payload['start_ticks'] and identity['cgroup']==str(Path(request['app'])/unit)
    proof={'nonce':request['nonce'],'unit':unit,'payload':[payload['pid'],payload['start_ticks']],'payload_live':live,'requested_monotonic':time.monotonic()}
    path=folder/'shutdown-request.json'
    if not path.exists():write(path,proof)

def adb_shim_main(args=None):
    args=sys.argv[1:] if args is None else args
    session=Path(os.environ['TOKKL_SCOPE_SESSION']);record_shutdown_request(session,args)
    real=load(session/'tools.json')['real_adb'];require(real is not None and Path(real).is_absolute(),'real adb path unavailable')
    os.execv(real,[real]+list(args))

def shim_main(args=None):
    args=sys.argv[1:] if args is None else args
    session=Path(os.environ['TOKKL_SCOPE_SESSION']);request=load(session/'request.json')
    wrapped=wrap_emulator(args,request,session)
    if wrapped!=list(args):
        unit=next(arg.split('=',1)[1] for arg in args if arg.startswith('--unit='))
        write(session/'intents'/(unit+'.json'),{'nonce':request['nonce'],'unit':unit})
    os.execv(request['real_systemd'],[request['real_systemd']]+wrapped)

if __name__=='__main__':
    if len(sys.argv)>1 and sys.argv[1]=='hold':sys.exit(holder_main(sys.argv[2:]))
    if len(sys.argv)>1 and sys.argv[1]=='shim':shim_main(sys.argv[2:])
    if len(sys.argv)>1 and sys.argv[1]=='adb-shim':adb_shim_main(sys.argv[2:])
    raise SystemExit('use the controller protocol; no standalone workload is started')
