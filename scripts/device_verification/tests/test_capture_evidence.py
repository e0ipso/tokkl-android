"""Actual disk reader/Session/Monitor plus unchanged outer failure cleanup."""
from pathlib import Path
import copy
import json
import os
import shutil
import tempfile
import unittest
from unittest.mock import patch

import capture_evidence as diagnostics
from capture_evidence import CaptureEvidence,FRAME_COUNT,FRAME_BYTES,FILE_BYTES
from event_monitor_0274 import capture,Monitor,KEYS
from scope_completion import Session
from test_candidate import PACKAGE,SCRATCH
import test_scoped_results as scoped_tests
from scoped_controller_fixture import WorkloadFixture


class CaptureEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(dir=SCRATCH);self.base=Path(self.temp.name)
        self.root=self.base/'cgroups/user.slice';self.app=self.root/'app.slice'
        self.own=self.app/'owned.scope';self.other=self.app/'other.service'
        for path in [self.root,self.app,self.own,self.other]:
            path.mkdir(parents=True,exist_ok=True)
            for name,value in {'memory.events':self.events(), 'memory.events.local':self.events(),
                               'memory.current':'1','memory.high':'max','memory.max':'max',
                               'memory.pressure':'some total=0\nfull total=0'}.items():
                (path/name).write_text(value)

    def tearDown(self):self.temp.cleanup()

    def events(self,n=0,key='high'):
        return ''.join(k+' '+str(n if k==key else 0)+'\n' for k in KEYS)

    def set_counter(self,path,n,local=False,key='high'):
        (path/('memory.events.local' if local else 'memory.events')).write_text(self.events(n,key))

    def session(self,reader=None):
        self.observer=CaptureEvidence(reader or (lambda **kwargs:capture(kwargs.pop('root',self.root),**kwargs)),self.base/'failure-evidence')
        self.protocol=Session(self.base/'protocol',self.app,'owned.scope',60,self.observer)
        return self.protocol

    def test_exact_retained_four_reads_reproduce_original_failure_without_new_evidence(self):
        facts=json.loads((PACKAGE/'tests/fixtures/ancestor-high-0292.json').read_text())
        frames=[facts['anchor']]+[row['snapshot'] for row in facts['reads']]
        observer=CaptureEvidence(lambda **kwargs:copy.deepcopy(frames.pop(0)),self.base/'failure-evidence')
        app=Path('/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice')
        unit=Path(facts['protected'][0]).name
        session=Session(self.base/'protocol',app,unit,1800,observer)
        anchor=session.capture()
        monitor=Monitor(anchor,app,app/unit,sampler=lambda root,deadline:session.capture(root=Path(root),deadline=deadline))
        result=monitor.inspect(session.capture(),owned=facts['protected'][1:],host_available=facts['host_available'])
        self.assertEqual(result['abort'],facts['expected_abort'])
        self.assertEqual([r['assessment_abort'] for r in result['sampling']['reads']],[r['assessment_abort'] for r in facts['reads']])
        self.assertEqual(len(result['sampling']['reads']),4);self.assertFalse(frames)
        receipt=observer.persist(result['abort'],{'assessment':result})
        self.assertTrue(receipt['durable'])
        saved=json.loads(Path(receipt['path']).read_text())
        self.assertEqual(len(saved['raw_captures']),5)
        self.assertEqual(saved['raw_captures'][0]['tree']['rows'],facts['anchor']['rows'])
        self.assertEqual(saved['raw_captures'][-1]['tree']['rows'],facts['reads'][-1]['snapshot']['rows'])

    def test_real_disk_reader_and_session_require_two_agreeing_resamples(self):
        session=self.session();baseline=session.capture()
        self.set_counter(self.root,1);self.set_counter(self.app,2)
        self.set_counter(self.other,2);self.set_counter(self.other,2,local=True)
        calls=[]
        def sample(root,deadline):
            calls.append(root);self.set_counter(self.root,2)
            return session.capture(root=Path(root),deadline=deadline)
        monitor=Monitor(baseline,self.app,self.own,sampler=sample)
        result=monitor.inspect(session.capture(),host_available=10*1024**3)
        self.assertIsNone(result['abort']);self.assertTrue(result['sampling']['recovered'])
        self.assertEqual(len(calls),2)
        self.assertFalse((self.base/'failure-evidence').exists())
        self.assertEqual(len(self.observer.frames),4)

    def test_true_owned_high_and_unrelated_max_oom_still_abort(self):
        for target,key in [(self.own,'high'),(self.other,'max'),(self.other,'oom_kill')]:
            with self.subTest(key=key):
                observer=CaptureEvidence(lambda **kw:capture(self.root),self.base/('failure-'+key))
                base=observer();self.set_counter(target,1,local=True,key=key)
                current=observer();result=Monitor(base,self.app,self.own).inspect(current,host_available=10*1024**3)
                self.assertIsNotNone(result['abort'])
                self.assertEqual(len(result['sampling']['reads']),1)
                self.assertTrue(observer.persist(result['abort'],result)['durable'])
                self.set_counter(target,0,local=True,key=key)

    def test_actual_reader_missing_counter_transient_and_generation_remain_abort(self):
        for mode in ['missing','transient','generation']:
            with self.subTest(mode=mode):
                observer=CaptureEvidence(lambda **kw:capture(self.root),self.base/('failure-'+mode))
                baseline=observer()
                if mode=='missing':(self.other/'memory.events.local').unlink()
                elif mode=='transient':self.other.rename(self.other.with_name('gone'));shutil.rmtree(self.other.with_name('gone'))
                else:
                    self.other.rename(self.other.with_name('old-generation'))
                    shutil.copytree(self.other.with_name('old-generation'),self.other)
                result=Monitor(baseline,self.app,self.own).inspect(observer(),owned=[self.other],host_available=10*1024**3)
                self.assertIsNotNone(result['abort'])
                self.assertTrue(observer.persist(result['abort'],result)['durable'])
                if mode=='missing':(self.other/'memory.events.local').write_text(self.events())
                elif mode=='transient':shutil.copytree(self.own,self.other)
                else:shutil.rmtree(self.other.with_name('old-generation'))

    def test_reader_exception_durable_and_original_exception_not_replaced(self):
        error=PermissionError('exact original reader failure')
        def denied(**kwargs):raise error
        observer=CaptureEvidence(denied,self.base/'failure-evidence')
        with self.assertRaises(PermissionError) as raised:observer()
        self.assertIs(raised.exception,error)
        saved=json.loads(next((self.base/'failure-evidence').glob('*.json')).read_text())
        self.assertIn('exact original',saved['raw_captures'][0]['exception'])
        with patch.object(diagnostics,'_fsync',side_effect=OSError('disk fsync failure')):
            with self.assertRaises(PermissionError) as raised:observer()
        self.assertIs(raised.exception,error)

    def test_five_immutable_frame_copies_byte_caps_and_explicit_oversize_gap(self):
        tree={'text':'before'};observer=CaptureEvidence(lambda:tree,self.base/'failure-evidence')
        for _ in range(8):self.assertIs(observer(),tree)
        tree['text']='after'
        self.assertEqual(len(observer.frames),FRAME_COUNT)
        self.assertEqual(json.loads(observer.frames[-1])['tree']['text'],'before')
        tree['text']='x'*(FRAME_BYTES+1);observer()
        self.assertIn('diagnostic_gap',json.loads(observer.frames[-1]))
        self.assertLessEqual(observer.memory()['serialized_bytes'],FRAME_COUNT*FRAME_BYTES)
        receipt=observer.persist('test','x'*(diagnostics.CONTEXT_BYTES+1))
        self.assertTrue(receipt['durable']);self.assertLessEqual(receipt['bytes'],FILE_BYTES)
        self.assertIn('diagnostic_gap',json.loads(Path(receipt['path']).read_text())['context'])

    def test_actual_outer_abort_keeps_original_reason_cleanup_and_rollback_on_diagnostic_error(self):
        test=scoped_tests.ScopedIntegrationTests();test.root=self.base/'outer';test.root.mkdir();test.number=0
        original=test.fixture
        def event_fixture(*args,**kwargs):return original('owned-high',**kwargs)
        with patch.object(test,'fixture',side_effect=event_fixture),patch.object(diagnostics,'_fsync',side_effect=OSError('diagnostic-only failure')):
            fixture,calls,preparations,controls=test.sequence()
        record=fixture.ns['state']['tests'][0]
        self.assertIn('local event',record['stop_reason'])
        sample=json.loads(Path(record['telemetry']).read_text().splitlines()[-1])
        self.assertFalse(sample['failure_evidence']['durable'])
        self.assertIn('diagnostic-only failure',sample['failure_evidence']['diagnostic_error'])
        self.assertEqual(calls,['assemble-check']);self.assertFalse(preparations)
        self.assertEqual(fixture.ns['result'],1)
        self.assertIn('immediate-rollback',controls);self.assertIn('cancel-fallback',controls)


if __name__=='__main__':unittest.main(verbosity=2)
