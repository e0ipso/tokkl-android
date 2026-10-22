"""Controller-owned exception cleanup. No host action occurs on import.

Runtime Session/monitor modules are deliberately untouched. Signals use pidfds
bound to verified PID/start identities; no unit-name kill can target a reused unit.
"""
from pathlib import Path
import json,os,re,signal,stat,time,traceback
from scope_completion import process_identity,members,EMULATOR

class CleanupContext:
    def __init__(self,session,allocation_root,*,seconds=45):
        if not 0<seconds<=45:raise ValueError('cleanup deadline cannot exceed45s')
        self.session=session;self.allocation_root=Path(allocation_root);self.seconds=seconds
        self.proc=None;self.launcher=None;self.launcher_fd=None;self.deadline=None
        self.scopes={};self.directories={};self.security_errors=[];self.notes=[]
        self.sent=set();self.signals=[];self.last_report=None;self.calls=0
    def note_deadline(self,deadline=None):
        value=time.monotonic()+self.seconds if deadline is None else min(deadline,time.monotonic()+self.seconds)
        self.deadline=value if self.deadline is None else min(self.deadline,value)
    def attach(self,proc):
        self.proc=proc
        try:
            fd=os.pidfd_open(proc.pid);identity=process_identity(proc.pid)
            if identity is None or identity['state']=='Z':os.close(fd)
            else:self.launcher_fd=fd;self.launcher=identity
        except ProcessLookupError:pass
        self.observe()
    def _valid_unit(self,unit):return unit==self.session.main_unit or bool(EMULATOR.fullmatch(unit))
    def _load(self,path):return json.loads(Path(path).read_text())
    def _remember_directory(self,path,unit):
        path=Path(path)
        if path.name!='emulator-tmp' or path.parent.parent!=self.allocation_root or not re.fullmatch('[0-9a-f]{8}',path.parent.name):
            raise ValueError('private overlay outside exact allocation root')
        root=path.parent
        if os.path.lexists(root):
            info=root.lstat()
            if not stat.S_ISDIR(info.st_mode) or info.st_uid!=os.getuid():raise ValueError('unowned/symlink allocation root')
            key=str(root);row={'path':key,'inode':info.st_ino,'unit':unit}
            if key in self.directories and self.directories[key]!=row:raise ValueError('allocation identity changed')
            self.directories[key]=row
    def observe(self):
        s=self.session;request=s.request;errors=[]
        for folder in sorted((s.path/'scopes').iterdir()):
            try:
                r=self._load(folder/'registered.json');unit=folder.name
                if not self._valid_unit(unit) or r['unit']!=unit or r['nonce']!=request['nonce'] or r['scope']!=str(s.app/unit):raise ValueError('scope ownership/nonce mismatch')
                prior=self.scopes.get(unit)
                if prior and any(prior[k]!=r[k] for k in ['inode','pid','start_ticks','scope']):raise ValueError('registered scope identity changed')
                cp=Path(r['scope'])
                if cp.exists() and cp.stat().st_ino!=r['inode']:raise ValueError('scope inode reused')
                self.scopes[unit]=r
                if EMULATOR.fullmatch(unit) and not any(row['unit']==unit for row in self.directories.values()):
                    payload_path=folder/'payload.json'
                    if payload_path.exists():
                        payload=self._load(payload_path);ident=process_identity(payload['pid'])
                        if ident and ident['state']!='Z' and ident['start_ticks']==payload['start_ticks'] and ident['cgroup']==r['scope']:
                            env=Path('/proc',str(payload['pid']),'environ').read_bytes().split(b'\0')
                            private=next((x.split(b'=',1)[1].decode() for x in env if x.startswith(b'ANDROID_TMP=')),None)
                            if private:self._remember_directory(private,unit)
            except FileNotFoundError:continue  # Atomic registration/payload publication can still be pending.
            except (OSError,ValueError,KeyError,TypeError) as error:errors.append(type(error).__name__+': '+str(error))
        # Slots are trusted only while their launcher is verifiably in our nonce-bound main scope.
        main=self.scopes.get(s.main_unit)
        if main:
            for slot in self.allocation_root.parent.glob('tokkl-instrumented-tests-1000-slot-*.lock'):
                try:
                    text=slot.read_text();pid=re.search(r'^PID ([0-9]+),',text);unit=re.search(r'in (tokkl-emulator-[^,\s]+\.scope)',text);work=re.search(r'work directory ([^,\n]+)',text)
                    if not (pid and unit and work) or unit[1] not in self.scopes:continue
                    identity=process_identity(int(pid[1]))
                    if identity and identity['state']!='Z' and identity['cgroup']==main['scope']:
                        self._remember_directory(Path(work[1])/'emulator-tmp',unit[1])
                except (OSError,ValueError,KeyError,TypeError) as error:errors.append(type(error).__name__+': '+str(error))
        self.security_errors.extend(x for x in errors if x not in self.security_errors)
    def _intent_units(self):
        result=set()
        for p in (self.session.path/'intents').glob('*.json'):
            row=self._load(p);unit=row['unit']
            if row['nonce']!=self.session.request['nonce'] or not self._valid_unit(unit) or p.name!=unit+'.json':raise ValueError('untrusted launch intent')
            result.add(unit)
        return result
    def _snapshot(self):
        rows=[]
        for unit,r in self.scopes.items():
            cp=Path(r['scope']);item={'unit':unit,'scope':str(cp),'expected_inode':r['inode'],'exists':cp.exists(),'members':[]}
            if cp.exists():
                try:
                    item['inode']=cp.stat().st_ino
                    if item['inode']!=r['inode']:item['ownership_error']='scope inode changed'
                    else:
                        for pid in sorted(members(cp)):
                            identity=process_identity(pid)
                            if identity and identity['state']!='Z':item['members'].append(identity)
                except Exception:
                    if cp.exists():raise
                    item.update(exists=False,members=[],closure_during_cleanup_read=True)
            rows.append(item)
        unknown=[]
        try:intents=self._intent_units()
        except (OSError,ValueError,KeyError,TypeError) as error:
            intents=set();unknown.append({'ownership_error':'invalid launch-intent evidence: '+repr(error),'intents':[str(p) for p in (self.session.path/'intents').glob('*.json')]})
        for unit in intents|{self.session.main_unit}:
            if unit not in self.scopes and (unit in intents or (self.session.app/unit).exists()):unknown.append({'unit':unit,'scope':str(self.session.app/unit),'exists':(self.session.app/unit).exists(),'ownership_error':'launch intent/scope without validated registration; never signaled'})
        return rows+unknown
    def _signal_identity(self,identity,number,scope=None):
        key=(identity['pid'],identity['start_ticks'],number)
        if key in self.sent:return
        fd=None
        try:
            fd=os.pidfd_open(identity['pid']);current=process_identity(identity['pid'])
            if current is None or current['state']=='Z':return
            if current['start_ticks']!=identity['start_ticks']:
                self.notes.append({'skipped_reused_pid':identity['pid'],'expected_start':identity['start_ticks'],'actual_start':current['start_ticks']});return
            if scope and (not (current['cgroup']==scope['scope'] or current['cgroup'].startswith(scope['scope']+'/')) or not Path(scope['scope']).exists() or Path(scope['scope']).stat().st_ino!=scope['inode']):
                self.notes.append({'refused_foreign_identity':current});return
            signal.pidfd_send_signal(fd,number);self.sent.add(key);self.signals.append({'pid':current['pid'],'start_ticks':current['start_ticks'],'cgroup':current['cgroup'],'signal':number,'monotonic':time.monotonic()})
        except ProcessLookupError:pass
        finally:
            if fd is not None:os.close(fd)
    def _remove_tree(self,root):
        # Never follow internal symlinks; check the one shared cleanup deadline per entry.
        if time.monotonic()>=self.deadline:raise TimeoutError('45s cleanup deadline exhausted during overlay cleanup')
        for entry in os.scandir(root):
            if time.monotonic()>=self.deadline:raise TimeoutError('45s cleanup deadline exhausted during overlay cleanup')
            if entry.is_dir(follow_symlinks=False):self._remove_tree(Path(entry.path))
            else:os.unlink(entry.path)
        os.rmdir(root)
    def cleanup(self,reason,error=None):
        self.calls+=1;self.note_deadline()
        # Reuse run_workload's already-started finalization deadline, never restart it.
        tb=error.__traceback__ if error is not None else None
        while tb:
            if tb.tb_frame.f_code.co_name=='run_workload':
                value=tb.tb_frame.f_locals.get('cleanup_deadline')
                if isinstance(value,(int,float)):self.note_deadline(value)
            tb=tb.tb_next
        started=time.monotonic();result={'reason':str(reason),'original_error':repr(error) if error is not None else None,'started':started,'deadline':self.deadline,'call':self.calls,'errors':[],'remaining':[],'signals':self.signals,'certificate_created_by_cleanup':False}
        try:
            self.observe()
            try:self.session.abort(reason)
            except BaseException as exc:result['errors'].append('Session.abort: '+repr(exc))
            stable=0;force_at=min(started+2,self.deadline)
            while time.monotonic()<self.deadline:
                self.observe();rows=self._snapshot();code=self.proc.poll() if self.proc is not None else None
                number=signal.SIGTERM if time.monotonic()<force_at else signal.SIGKILL
                if self.proc is not None and code is None and self.launcher is not None:self._signal_identity(self.launcher,number)
                for row in rows:
                    if row.get('exists') and not row.get('ownership_error'):
                        for identity in row['members']:self._signal_identity(identity,number,self.scopes[row['unit']])
                scope_live=any(r.get('exists') or r.get('ownership_error') for r in rows)
                if not scope_live and (self.proc is None or code is not None):stable+=1
                else:stable=0
                if stable>=2:break
                time.sleep(min(.02,max(0,self.deadline-time.monotonic())))
            result['remaining']=self._snapshot()
            live=any(r.get('exists') or r.get('ownership_error') for r in result['remaining'])
            if self.proc is not None:
                result['launcher_exit']=self.proc.poll()
                if result['launcher_exit'] is None:live=True
                else:self.proc.wait(timeout=max(.001,self.deadline-time.monotonic()))
            if live:result['errors'].append('owned work remains or ownership evidence unresolved at cleanup deadline')
            else:
                for row in self.directories.values():
                    path=Path(row['path'])
                    if os.path.lexists(path):
                        info=path.lstat()
                        if info.st_ino!=row['inode'] or not stat.S_ISDIR(info.st_mode) or info.st_uid!=os.getuid():raise ValueError('allocation root identity changed; never deleted')
                        self._remove_tree(path)
            result['security_errors']=list(self.security_errors)
            for unit in self.scopes:
                if EMULATOR.fullmatch(unit) and not any(r['unit']==unit for r in self.directories.values()):result['errors'].append('missing private overlay ownership evidence for '+unit)
            result['directories']=[dict(r,removed=not os.path.lexists(r['path'])) for r in self.directories.values()]
            result['payload_outcomes']={}
            for unit in self.scopes:
                folder=self.session.path/'scopes'/unit;value={}
                for name in ['ready.json','failure.json','released.json']:
                    if (folder/name).exists():value[name]=self._load(folder/name)
                result['payload_outcomes'][unit]=value or {'payload_exit':'not reported'}
            result['success']=not live and not result['errors'] and not self.security_errors and all(r['removed'] for r in result['directories'])
        except BaseException as exc:
            result['errors'].append('cleanup exception: '+repr(exc));result['success']=False
            try:result['remaining']=self._snapshot()
            except BaseException as nested:result['remaining_evidence_error']=repr(nested)
        finally:
            result['ownership_notes']=list(self.notes)
            result['directories']=[dict(r,removed=not os.path.lexists(r['path'])) for r in self.directories.values()]
            if self.proc is not None and 'launcher_exit' not in result:
                try:result['launcher_exit']=self.proc.poll()
                except BaseException as exc:result['launcher_exit']='not reported';result['errors'].append('launcher outcome: '+repr(exc))
            result['elapsed_seconds']=time.monotonic()-started;result['deadline_exhausted']=time.monotonic()>=self.deadline
            result['certificate_preexisting']=(self.session.path/'completed.json').exists()
            self.last_report=result
            try:
                with (self.session.path/'controller-cleanup-attempts.jsonl').open('a') as out:out.write(json.dumps(result)+'\n');out.flush();os.fsync(out.fileno())
            except BaseException as exc:result['errors'].append('cleanup persistence failed: '+repr(exc));result['success']=False
        return result
    def close(self):
        if self.launcher_fd is not None:os.close(self.launcher_fd);self.launcher_fd=None

def install_controller(namespace):
    """Install controller-local observers, preserving the certified workload AST."""
    parent_session=namespace['Session'];parent_subprocess=namespace['subprocess']
    namespace['_active_cleanup']=None
    class ControllerSession(parent_session):
        def __init__(self,*args,**kwargs):
            super().__init__(*args,**kwargs)
            if namespace['_active_cleanup'] is not None:raise RuntimeError('previous workload context not settled')
            self.controller_cleanup=CleanupContext(self,Path(namespace['runtime'])/'tokkl-instrumented-tests-1000-runs',seconds=namespace.get('_cleanup_fixture_seconds',45))
            namespace['_active_cleanup']=self.controller_cleanup
        def capture(self,*args,**kwargs):
            self.controller_cleanup.observe()
            return super().capture(*args,**kwargs)
        def abort(self,*args,**kwargs):
            self.controller_cleanup.note_deadline()
            return super().abort(*args,**kwargs)
        def finalize(self,*args,**kwargs):
            self.controller_cleanup.note_deadline(kwargs.get('deadline'))
            return super().finalize(*args,**kwargs)
        def await_cleanup(self,*args,**kwargs):
            self.controller_cleanup.note_deadline(kwargs.get('deadline'))
            return super().await_cleanup(*args,**kwargs)
    class ControllerSubprocess:
        def __getattr__(self,name):return getattr(parent_subprocess,name)
        def Popen(self,*args,**kwargs):
            process=parent_subprocess.Popen(*args,**kwargs)
            context=namespace['_active_cleanup']
            if context is not None:context.attach(process)
            return process
    def cleanup(error=None):
        context=namespace['_active_cleanup']
        if context is None:return {'success':True,'no_active_workload':True,'certificate_created_by_cleanup':False}
        report=context.cleanup('unexpected controller exception: '+repr(error) if error is not None else 'controller final cleanup',error)
        namespace['state']['exception_cleanup']=report
        return report
    def settle(success,stable):
        context=namespace['_active_cleanup']
        if context is None:return True
        context.observe()
        if stable and context.proc is not None and context.proc.poll() is not None and not any(row.get('exists') or row.get('ownership_error') for row in context._snapshot()):
            context.close();namespace['_active_cleanup']=None;return True
        report=cleanup()
        return report['success']
    namespace.update(Session=ControllerSession,subprocess=ControllerSubprocess(),cleanup_active_workload=cleanup,settle_active_workload=settle)
