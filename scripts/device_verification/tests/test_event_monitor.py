import copy
import unittest
import tempfile
from pathlib import Path
from event_monitor_0274 import capture
from event_monitor_0274 import KEYS, Monitor

APP='/cg/user/app'
OWN=APP+'/owned'
OTHER=APP+'/other'
ANCESTOR='/cg/user'

def snapshot():
    rows={}
    for i,p in enumerate([ANCESTOR,APP,OWN,OTHER]):
        rows[p]={'inode':i+1,'time':'t0','stable':True,
            'events':dict.fromkeys(KEYS,0),'local':dict.fromkeys(KEYS,0),
            'current':100,'high':'1000','max':'2000',
            'pressure':'some avg10=3.34 avg60=0 avg300=0 total=0\nfull avg10=2.66 avg60=0 avg300=0 total=0'}
    return {'time':'t0','monotonic':1.,'errors':[],'rows':rows}

def bump(s,path,key='high',n=1):
    s['rows'][path]['local'][key]+=n
    for p,row in s['rows'].items():
        if path==p or path.startswith(p+'/'):row['events'][key]+=n

class AttributionTests(unittest.TestCase):
    def setUp(self):
        self.before=snapshot();self.after=copy.deepcopy(self.before)
        self.after.update(time='t1',monotonic=3.)
    def inspect(self,after=None):
        return Monitor(self.before,APP,OWN).inspect(after or self.after,host_available=10*1024**3)
    def test_unrelated_descendant_279_is_exactly_attributed(self):
        bump(self.after,OTHER,n=279)
        result=self.inspect();self.assertIsNone(result['abort'])
        self.assertEqual(result['origins'][0]['path'],OTHER)
        self.assertFalse(result['origins'][0]['relevant'])
        self.assertEqual(result['origins'][0]['local_delta']['high'],279)
    def test_owned_local_aborts(self):
        bump(self.after,OWN);self.assertIn('local event',self.inspect()['abort'])
    def test_app_local_aborts(self):
        bump(self.after,APP);self.assertIn('local event',self.inspect()['abort'])
    def test_ancestor_local_aborts(self):
        bump(self.after,ANCESTOR);self.assertIn('local event',self.inspect()['abort'])
    def test_owned_nested_scope_aborts(self):
        p=OWN+'/nested';self.after['rows'][p]=copy.deepcopy(self.after['rows'][OTHER]);self.after['rows'][p]['inode']=99
        bump(self.after,p);self.assertIsNotNone(self.inspect()['abort'])
    def test_all_max_oom_counters_remain_fatal_even_on_sibling(self):
        for key in KEYS[1:]:
            with self.subTest(key=key):
                s=copy.deepcopy(self.after);bump(s,OTHER,key)
                self.assertIn('max/OOM',self.inspect(s)['abort'])
    def test_missing_snapshot_aborts(self):
        self.after['errors']=[{'path':OTHER,'error':'PermissionError'}]
        self.assertIn('inaccessible',self.inspect()['abort'])
    def test_ambiguous_totals_abort(self):
        self.after['rows'][APP]['events']['high']=279
        self.assertIn('accounting',self.inspect()['abort'])
    def test_partial_attribution_aborts(self):
        bump(self.after,OTHER,n=278);self.after['rows'][APP]['events']['high']=279
        self.assertIn('accounting',self.inspect()['abort'])
    def test_racing_read_aborts(self):
        self.after['rows'][OTHER]['stable']=False
        self.assertIn('concurrent',self.inspect()['abort'])
    def test_changed_identity_aborts(self):
        self.after['rows'][OTHER]['inode']=888
        self.assertIn('identity',self.inspect()['abort'])
    def test_lost_origin_aborts(self):
        del self.after['rows'][OTHER];self.after['rows'][APP]['events']['high']=1
        self.assertIn('vanished',self.inspect()['abort'])
    def test_lost_owned_scope_aborts(self):
        del self.after['rows'][OWN];self.assertIn('disappeared',self.inspect()['abort'])
    def test_counter_reset_aborts(self):
        self.before['rows'][APP]['events']['high']=10
        self.assertIn('reset',self.inspect()['abort'])
    def test_actual_ancestor_budget_aborts_without_events(self):
        self.after['rows'][ANCESTOR]['current']=1000
        self.assertIn('budget',self.inspect()['abort'])
    def test_boot_psi_average_alone_does_not_abort(self):
        self.assertIsNone(self.inspect()['abort'])
    def test_harmful_sustained_pressure_aborts(self):
        monitor=Monitor(self.before,APP,OWN)
        for second in (11.,21.,31.):
            s=copy.deepcopy(self.after);s['monotonic']=second
            for row in s['rows'].values():row['pressure']='full avg10=0 total='+str(int(second-1)*20000)
            result=monitor.inspect(s,host_available=4999*1048576)
        self.assertIn('sustained',result['abort'])
    def test_stalls_with_ample_memory_do_not_abort(self):
        self.after['rows'][APP]['pressure']='full avg10=0 total=1000000'
        self.assertIsNone(self.inspect()['abort'])
    def test_missing_physical_memory_aborts(self):
        result=Monitor(self.before,APP,OWN).inspect(self.after)
        self.assertIn('physical',result['abort'])
    def test_new_unrelated_scope_has_zero_birth_baseline(self):
        p=OTHER+'/new';self.after['rows'][p]=copy.deepcopy(self.after['rows'][OTHER]);self.after['rows'][p]['inode']=999
        bump(self.after,p,n=3);self.assertIsNone(self.inspect()['abort'])
    def test_missing_app_snapshot_aborts(self):
        del self.after['rows'][APP];self.assertIn('missing',self.inspect()['abort'])

class CaptureTests(unittest.TestCase):
    def tree(self,root):
        for p in (root,root/'child'):
            p.mkdir(exist_ok=True)
            values={'memory.events':'\n'.join(k+' 0' for k in KEYS),
                'memory.events.local':'\n'.join(k+' 0' for k in KEYS),
                'memory.current':'100','memory.high':'1000','memory.max':'2000',
                'memory.pressure':'full avg10=0 total=0'}
            for name,value in values.items():(p/name).write_text(value)
    def test_capture_records_identity_timestamps_and_both_counter_sets(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as tmp:
            root=Path(tmp);self.tree(root);s=capture(root)
            self.assertEqual(s['errors'],[])
            self.assertEqual(len(s['rows']),2)
            row=s['rows'][str(root/'child')]
            self.assertEqual(row['inode'],(root/'child').stat().st_ino)
            self.assertTrue(row['time']);self.assertEqual(row['events'],row['local'])
    def test_missing_local_counter_is_explicit_evidence_error(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as tmp:
            root=Path(tmp);self.tree(root);(root/'child'/'memory.events.local').unlink()
            s=capture(root)
            self.assertEqual(len(s['errors']),1)
            self.assertEqual(s['errors'][0]['path'],str(root/'child'))
            self.assertIn('memory.events.local',s['errors'][0]['error'])
    def test_bad_counter_is_explicit_evidence_error(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as tmp:
            root=Path(tmp);self.tree(root);(root/'child'/'memory.events.local').write_text('high bad')
            s=capture(root);self.assertEqual(len(s['errors']),1)

if __name__=='__main__':unittest.main(verbosity=2)
