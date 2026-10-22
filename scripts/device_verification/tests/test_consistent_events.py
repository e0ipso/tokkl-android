import copy,json,math,unittest
from pathlib import Path
from event_monitor_0274 import Monitor,KEYS,capture
from test_event_monitor import snapshot,bump,APP,OWN,OTHER,ANCESTOR

BASE=Path(__file__).resolve().parent

class ConsistentEventsTests(unittest.TestCase):
    def setUp(self):
        self.before=snapshot();self.before['root']=ANCESTOR
        self.current=copy.deepcopy(self.before);self.current.update(time='t1',monotonic=3.)
        bump(self.current,OTHER,n=2)
        self.current['rows'][ANCESTOR]['events']['high']=1
        self.settled=copy.deepcopy(self.current);self.settled['rows'][ANCESTOR]['events']['high']=2
        self.settled.update(time='t2',monotonic=3.02)
        self.confirmed=copy.deepcopy(self.settled);self.confirmed.update(time='t3',monotonic=3.04)
        self.calls=[]
    def monitor(self,frames=None,**kwargs):
        frames=list(frames if frames is not None else [self.settled,self.confirmed])
        def sample(root,deadline):
            self.calls.append((root,deadline));return frames.pop(0)
        return Monitor(self.before,APP,OWN,sampler=sample,clock=lambda:10.,**kwargs)
    def check(self,monitor=None,current=None,**kwargs):
        return (monitor or self.monitor()).inspect(current or self.current,host_available=10*1024**3,**kwargs)
    def test_propagation_disagreement_requires_two_agreeing_counter_reads(self):
        m=self.monitor();r=self.check(m)
        self.assertIsNone(r['abort']);self.assertEqual(len(self.calls),2)
        self.assertTrue(r['sampling']['recovered']);self.assertEqual(len(r['sampling']['reads']),3)
        self.assertIs(m.previous,self.confirmed)
        self.assertFalse(r['sampling']['atomic_snapshot_claimed'])
        self.assertEqual(r['origins'][0]['local_delta']['high'],2)
        self.assertFalse(r['origins'][0]['relevant'])
        json.dumps(r)
    def test_exact_retained_case_with_explicitly_hypothetical_settled_reads(self):
        before=json.loads((BASE/'retained-previous.json').read_text())['event_tree']
        raw=json.loads((BASE/'retained-last.json').read_text())
        current=raw['event_tree'];app=raw['event_assessment']['ancestors'][-1]
        owned=raw['event_assessment']['protected']
        settled=copy.deepcopy(current)
        # Counterfactual fixture only: no post-abort sample was retained.
        for p in raw['event_assessment']['ancestors'][:-1]:settled['rows'][p]['events']['high']+=1
        settled['monotonic']+=.02;confirmed=copy.deepcopy(settled);confirmed['monotonic']+=.02
        frames=iter([settled,confirmed])
        m=Monitor(before,app,owned[0],sampler=lambda *_:next(frames),clock=lambda:10.)
        r=m.inspect(current,owned=owned[1:],host_available=10*1024**3)
        self.assertIsNone(r['abort']);self.assertTrue(r['sampling']['recovered'])
        self.assertEqual(r['origins'][0]['local_delta']['high'],2)
    def test_zero_upper_delta_is_not_permission_to_skip_accounting(self):
        self.current['rows'][ANCESTOR]['events']['high']=0
        r=self.check();self.assertIsNone(r['abort']);self.assertEqual(len(self.calls),2)
    def test_new_unrelated_event_during_retry_is_included_against_original_anchor(self):
        bump(self.settled,OTHER);bump(self.confirmed,OTHER)
        r=self.check();self.assertEqual(r['origins'][0]['local_delta']['high'],3)
    def test_racing_local_read_can_only_recover_after_two_stable_reads(self):
        self.current['rows'][OTHER]['stable']=False
        r=self.check();self.assertIsNone(r['abort']);self.assertEqual(len(self.calls),2)
    def test_consistent_initial_sample_does_not_resample(self):
        self.assertIsNone(self.check(current=self.settled)['abort']);self.assertEqual(self.calls,[])
    def test_owned_high_in_initial_ambiguous_frame_aborts_without_retry(self):
        bump(self.current,OWN)
        self.assertIn('local event',self.check()['abort']);self.assertEqual(self.calls,[])
    def test_ancestor_high_in_initial_ambiguous_frame_aborts_without_retry(self):
        bump(self.current,ANCESTOR)
        self.assertIn('local event',self.check()['abort']);self.assertEqual(self.calls,[])
    def test_owned_event_during_retry_aborts_before_confirmation(self):
        bump(self.settled,OWN)
        self.assertIn('local event',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_discovered_emulator_scope_is_protected_during_retry(self):
        r=self.check(owned=[OTHER]);self.assertIn('local event',r['abort']);self.assertEqual(self.calls,[])
    def test_local_max_and_oom_abort_even_before_hierarchy_propagates(self):
        for key in KEYS[1:]:
            with self.subTest(key=key):
                current=copy.deepcopy(self.current);current['rows'][OTHER]['local'][key]+=1
                self.assertIn('max/OOM',self.check(current=current)['abort'])
        self.assertEqual(self.calls,[])
    def test_hierarchical_max_and_oom_abort_without_local_origin(self):
        for key in KEYS[1:]:
            with self.subTest(key=key):
                current=copy.deepcopy(self.current);current['rows'][ANCESTOR]['events'][key]+=1
                self.assertIn('max/OOM',self.check(current=current)['abort'])
        self.assertEqual(self.calls,[])
    def test_oom_during_retry_aborts_immediately(self):
        self.settled['rows'][OTHER]['local']['oom_kill']+=1
        self.assertIn('max/OOM',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_ancestor_budget_aborts_without_retry(self):
        self.current['rows'][ANCESTOR]['current']=1000
        self.assertIn('budget',self.check()['abort']);self.assertEqual(self.calls,[])
    def test_ancestor_budget_during_retry_aborts(self):
        self.settled['rows'][ANCESTOR]['current']=1000
        self.assertIn('budget',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_harmful_pressure_not_deferred_by_accounting_ambiguity(self):
        self.current['monotonic']=31.
        for row in self.current['rows'].values():row['pressure']='full total=600000'
        r=self.monitor().inspect(self.current,host_available=4999*1048576)
        self.assertIn('sustained',r['abort']);self.assertEqual(self.calls,[])
    def test_missing_pressure_is_not_retryable(self):
        self.current['rows'][ANCESTOR]['pressure']=''
        self.assertIn('pressure',self.check()['abort']);self.assertEqual(self.calls,[])
    def test_missing_initial_evidence_aborts_without_retry(self):
        self.current['errors']=[{'path':OTHER,'error':'PermissionError'}]
        self.assertIn('inaccessible',self.check()['abort']);self.assertEqual(self.calls,[])
    def test_missing_evidence_during_retry_aborts(self):
        self.settled['errors']=[{'path':OTHER,'error':'PermissionError'}]
        self.assertIn('inaccessible',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_malformed_snapshot_fails_closed(self):
        del self.settled['rows'][OTHER]['local']
        self.assertIn('malformed',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_sampling_read_exception_is_explicit_and_fatal(self):
        def denied(*args):raise PermissionError('fixture denied')
        m=Monitor(self.before,APP,OWN,sampler=denied)
        r=self.check(m);self.assertIn('inaccessible',r['abort']);self.assertIn('fixture denied',r['evidence_errors'][0]['error'])
    def test_unresolved_mismatch_exhausts_exactly_three_retries(self):
        frames=[]
        for i in range(3):
            s=copy.deepcopy(self.current);s['monotonic']+=.02*(i+1);frames.append(s)
        r=self.check(self.monitor(frames))
        self.assertIn('accounting',r['abort']);self.assertIn('exhausted',r['abort'])
        self.assertEqual(len(self.calls),3);self.assertFalse(r['sampling']['recovered'])
    def test_no_quiet_pair_is_not_accepted(self):
        frames=[]
        for i in range(3):
            s=copy.deepcopy(self.settled);bump(s,OTHER,n=i);s['monotonic']+=.02*i;frames.append(s)
        r=self.check(self.monitor(frames));self.assertIn('exhausted',r['abort']);self.assertEqual(len(self.calls),3)
    def test_time_budget_exhaustion_refuses_even_a_late_consistent_read(self):
        clock=[10.]
        def slow(*args):clock[0]=10.3;return self.settled
        m=Monitor(self.before,APP,OWN,sampler=slow,clock=lambda:clock[0])
        r=self.check(m);self.assertIn('time budget exhausted',r['abort'])
        self.assertEqual(len(r['sampling']['reads']),2)
    def test_unstable_baseline_is_never_rebased(self):
        self.before['rows'][OTHER]['stable']=False
        self.assertIn('baseline was unstable',self.check()['abort']);self.assertEqual(self.calls,[])
    def test_intermediate_counter_reset_cannot_be_hidden_by_original_anchor(self):
        self.settled['rows'][OTHER]['local']['high']=1
        self.assertIn('reset during resampling',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_intermediate_identity_change_aborts(self):
        self.settled['rows'][OTHER]['inode']+=99
        self.assertIn('identity',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_disappearing_origin_during_retry_fails_closed(self):
        del self.settled['rows'][OTHER]
        self.assertIsNotNone(self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_changed_sampling_root_aborts(self):
        self.settled['root']='/wrong'
        self.assertIn('root changed',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_nonincreasing_retry_timestamp_aborts(self):
        self.settled['monotonic']=self.current['monotonic']
        self.assertIn('timestamp',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_sampling_policy_cannot_be_made_unbounded(self):
        for kwargs in [{'retry_limit':4},{'retry_limit':True},{'retry_limit':-1},{'retry_seconds':.251},{'retry_seconds':math.inf},{'retry_seconds':math.nan},{'retry_seconds':True},{'retry_seconds':0}]:
            with self.subTest(kwargs=kwargs),self.assertRaises(ValueError):self.monitor(**kwargs)
    def test_zero_retry_policy_remains_fail_safe(self):
        r=self.check(self.monitor(retry_limit=0));self.assertIn('exhausted',r['abort']);self.assertEqual(self.calls,[])
    def test_capture_deadline_expiry_is_explicit(self):
        s=capture(BASE,deadline=0.)
        self.assertEqual(s['rows'],{});self.assertIn('deadline exhausted',s['errors'][0]['error'])

    def test_owned_hierarchical_high_is_immediately_fatal_without_local_propagation(self):
        self.current['rows'][OWN]['events']['high']=1
        self.assertIn('owned hierarchical high',self.check()['abort']);self.assertEqual(self.calls,[])
    def test_owned_hierarchical_high_during_retry_is_immediately_fatal(self):
        self.settled['rows'][OWN]['events']['high']=1
        self.assertIn('owned hierarchical high',self.check()['abort']);self.assertEqual(len(self.calls),1)
    def test_owned_local_event_in_racing_frame_is_not_deferred(self):
        self.current['rows'][OWN]['stable']=False;bump(self.current,OWN)
        self.assertIn('local event',self.check()['abort']);self.assertEqual(self.calls,[])
    def test_recovered_anchor_still_detects_next_genuine_event(self):
        m=self.monitor();self.assertIsNone(self.check(m)['abort'])
        next_frame=copy.deepcopy(self.confirmed);next_frame['monotonic']+=2.;bump(next_frame,OWN)
        self.assertIn('local event',self.check(m,current=next_frame)['abort'])
    def test_genuine_harmful_pressure_during_retry_is_not_deferred(self):
        self.settled['monotonic']=31.
        for row in self.settled['rows'].values():row['pressure']='full total=600000'
        r=self.monitor().inspect(self.current,host_available=4999*1048576)
        self.assertIn('sustained',r['abort']);self.assertEqual(len(self.calls),1)

    def test_default_sampler_uses_same_root_and_finite_deadline(self):
        import event_monitor_0274 as event_monitor
        from unittest.mock import patch
        frames=iter([self.settled,self.confirmed])
        with patch.object(event_monitor,'capture',side_effect=lambda *a,**kw:next(frames)) as read:
            r=event_monitor.Monitor(self.before,APP,OWN).inspect(self.current,host_available=10*1024**3)
        self.assertIsNone(r['abort']);self.assertEqual(read.call_count,2)
        for args,kwargs in read.call_args_list:
            self.assertEqual(str(args[0]),ANCESTOR)
            self.assertGreater(kwargs['deadline'],0)

if __name__=='__main__':unittest.main(verbosity=2)
