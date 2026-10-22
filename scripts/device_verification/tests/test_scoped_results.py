"""Source-complete scoped recipes through actual config/controller/Session/parser."""
from pathlib import Path
import copy
import ast
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

from test_candidate import PACKAGE,SCRATCH
from scoped_controller_fixture import WorkloadFixture,CLASSES
from scoped_results import declare,inventory,assess_scoped,PREFIX,COMPILE_ARGS,COMPILE_FIRST
from watchdog_lifetime import service_command
from runtime.configuration import validate,adopted_configuration


class ScopedIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(dir=SCRATCH)
        self.root=Path(self.temp.name)
        self.number=0

    def tearDown(self):
        self.temp.cleanup()

    def fixture(self,scenario='valid',**kw):
        self.number+=1
        return WorkloadFixture(self.root/str(self.number),scenario,**kw)

    def test_original_validator_and_actual_result_predicate_refuse_app_only(self):
        f=self.fixture(legacy=True)
        source=subprocess.check_output(['git','show','ec509ff:scripts/device_verification/runtime/configuration.py'],cwd=PACKAGE.parent.parent,text=True)
        ns={'__name__':'original_readonly_configuration'}
        exec(compile(source,'<original-configuration>','exec'),ns)
        with self.assertRaisesRegex(ValueError,'unfiltered integrated'):
            ns['validate'](dict(f.configuration,run_dir=str(self.root/'fresh-original')))
        self.assertEqual(f.run(),(False,False))
        self.assertEqual(f.ns['state']['tests'][0]['counts']['tests'],30)
        self.assertTrue((f.session.path/'completed.json').is_file())

    def test_declared_five_complete_classes_pass_actual_run_workload_and_final_capture(self):
        f=self.fixture()
        self.assertEqual([len(row['methods']) for row in f.configuration['plan'][0]['result_recipe']['inventory']],[7,7,5,4,7])
        self.assertEqual(f.run(),(True,True))
        rec=f.ns['state']['tests'][0]
        self.assertEqual(rec['result_contract']['app_counts']['tests'],30)
        self.assertEqual(rec['benchmark_counts']['tests'],0)
        self.assertEqual(rec['result_contract']['configuration_sha256'],f.checked['_sha256'])
        self.assertIn('MEM_GATE_CGROUP_MB=8192',rec['command'])
        self.assertIn('MEM_GATE_WAIT_S=300',rec['command'])
        self.assertIn('TOKKL_TEST_AVD=tokkl-api36',rec['command'])
        final=json.loads((f.session.path/'completed.json').read_text())
        self.assertEqual(len(final['observations']),2)
        self.assertEqual(len(final['retired_scopes']),2)
        self.assertEqual(f.identity_rows,{})

    def test_single_widget_class_includes_all_seven_cases(self):
        f=self.fixture(widget=True)
        self.assertEqual(f.run(),(True,True))
        self.assertEqual(f.ns['state']['tests'][0]['result_contract']['app_counts']['tests'],7)

    def test_one_unchanged_clock_failure_fails_entire_thirty_case_job(self):
        f=self.fixture()
        original=f.results
        def results():
            original()
            path=f.project/'app/build/outputs/androidTest-results/connected/debug/TEST-app.xml'
            tree=ET.parse(path);root=tree.getroot();root.set('failures','1')
            case=next(case for case in root.findall('testcase') if case.get('name')=='nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod')
            ET.SubElement(case,'failure',message='host fixture clock failure')
            tree.write(path)
        f.results=results
        self.assertEqual(f.run(),(False,False))
        record=f.ns['state']['tests'][0]
        self.assertEqual(record['counts']['tests'],30)
        self.assertEqual(record['counts']['failures'],1)
        self.assertFalse(record['result_contract']['passed'])

    def sequence(self,compile_failure=False,ordinary_full=False):
        f=self.fixture(compile_first=not ordinary_full,full=ordinary_full)
        if ordinary_full:
            config=copy.deepcopy(f.configuration);config['run_dir']=str(self.root/'full-plan-validation-only')
            config['plan'] += [dict(api=0,label='assemble-check',standalone=True,seconds=1800,args=['./gradlew','--no-daemon','assembleDebug','check','--stacktrace']),
                               dict(api=0,label='reproducible-release',standalone=True,seconds=1800,args=['env','GRADLE_OPTS=-Dorg.gradle.workers.max=1','scripts/reproducible-release.sh'])]
            f.checked=validate(config)
        namespace=f.ns
        actual=namespace['run_workload']
        calls=[];preparations=[];controls=[]
        original={'memory.high':'10737418240','memory.max':'12884901888','memory.swap.max':'2147483648'}
        shared=dict(original)
        def command(args,label,refresh=False):
            controls.append(label)
            if label=='apply-window':shared.update(f.temporary)
            if label=='immediate-rollback':shared.update(original)
            return 0
        def prepare(label,*args):
            preparations.append(label);namespace['state']['prepared_labels'].append(label)
            return True
        def run_workload(*job):
            calls.append(job[1]);f.job=list(job);f.polls=0
            if ordinary_full:
                # Full result predicates are separately exercised through the
                # actual workload; here isolate the unchanged outer continuation.
                return job[1]!='assemble-check',True
            f.rc=7 if compile_failure and job[4] else 0
            return actual(*job)
        namespace.update(config=f.checked,run_workload=run_workload,prepare=prepare,
            state_path=f.base/'controller-state.json',unit='host-modeled-timer',bounds={'fallback_seconds':4800},
            armed=False,watchdog_armed=False,_controller_error=None,result=1,
            original=original,limits=lambda:dict(shared),
            conditions=lambda:{'limits':dict(shared),'baseline_pass':True,'host_pass':True},
            command=command,service_command=service_command,
            cleanup_active_workload=lambda *args:{'success':True},settle_active_workload=lambda *args:True,
            sys=sys,plan=[(j['api'],j['label'],j['args'],j['seconds'],j['standalone']) for j in f.checked['plan']])
        namespace['state']['prepared_labels']=[f.checked['plan'][0]['label']]
        source=(PACKAGE/'runtime/controller.py').read_text()
        outer=next(n for n in ast.parse(source).body if isinstance(n,ast.Try) and n.finalbody)
        with f.context():
            exec(compile(ast.Module(body=[outer],type_ignores=[]),'<actual-maintained-outer>','exec'),namespace)
        self.assertEqual(shared,original)
        self.assertLess(controls.index('immediate-rollback'),controls.index('cancel-watchdog'))
        self.assertLess(controls.index('cancel-watchdog'),controls.index('cancel-fallback'))
        return f,calls,preparations,controls

    def test_actual_compile_first_sequence_records_both_jobs_and_complete_scope(self):
        f,calls,preparations,controls=self.sequence()
        self.assertEqual(calls,['assemble-check','scoped-app'])
        self.assertEqual(preparations,['scoped-app'])
        self.assertEqual(f.ns['result'],0)
        self.assertEqual(f.ns['state']['tests'][0]['exit'],0)
        self.assertEqual(f.ns['state']['tests'][1]['result_contract']['app_counts']['tests'],30)
        self.assertNotIn('not_run_labels',f.ns['state'])

    def test_actual_compile_failure_records_exit_and_stops_before_scoped_preparation(self):
        f,calls,preparations,controls=self.sequence(compile_failure=True)
        self.assertEqual(calls,['assemble-check'])
        self.assertEqual(preparations,[])
        self.assertEqual(f.ns['result'],1)
        self.assertEqual(f.ns['state']['sequence_stopped_at'],'assemble-check')
        self.assertEqual(f.ns['state']['not_run_labels'],['scoped-app'])
        self.assertEqual(len(f.ns['state']['tests']),1)
        self.assertEqual(f.ns['state']['tests'][0]['exit'],7)
        self.assertTrue(f.ns['state']['tests'][0]['prerequisite_failed'])
        self.assertFalse(any(path.name.startswith('completion-scoped') for path in f.base.iterdir()))

    def test_full_plan_ordinary_assertion_failure_retains_original_continuation(self):
        f,calls,preparations,controls=self.sequence(ordinary_full=True)
        self.assertEqual(calls,['full36','assemble-check','reproducible-release'])
        self.assertEqual(preparations,['assemble-check','reproducible-release'])
        self.assertEqual(f.ns['result'],1)
        self.assertNotIn('not_run_labels',f.ns['state'])
        self.assertNotIn('sequence_stopped_at',f.ns['state'])

    def test_compile_first_order_argv_profile_and_extra_job_refusals(self):
        f=self.fixture(compile_first=True)
        for mode in ['reverse','extra','no-profile','unknown','no-apk','other-task','full-second','no-recipe']:
            with self.subTest(mode=mode):
                c=copy.deepcopy(f.configuration);c['run_dir']=str(self.root/('fresh-'+mode))
                if mode=='reverse':c['plan'].reverse()
                if mode=='extra':c['plan'].append(copy.deepcopy(c['plan'][1]))
                if mode=='no-profile':del c['plan_recipe']
                if mode=='unknown':c['plan_recipe']='arbitrary-build-first'
                if mode=='no-apk':c['plan'][0]['args'].remove('assembleDebugAndroidTest')
                if mode=='other-task':c['plan'][0]['args'].append('lint')
                if mode=='full-second':c['plan'][1]=copy.deepcopy(f.configuration['plan'][1]);c['plan'][1]['args']=['scripts/instrumented-tests.sh',':macrobenchmark:connectedBenchmarkAndroidTest','--continue']
                if mode=='no-recipe':del c['plan'][1]['result_recipe']
                with self.assertRaises(ValueError):validate(c)

    def test_actual_cli_preflight_prepare_and_adoption_refusal_without_fixture_pythonpath(self):
        f=self.fixture(widget=True)
        config=copy.deepcopy(f.configuration);config['run_dir']=str(self.root/'cli-snapshot')
        path=self.root/'cli-config.json';path.write_text(json.dumps(config))
        env={key:value for key,value in os.environ.items() if key!='PYTHONPATH'}
        for script in ['preflight.py','prepare.py']:
            result=subprocess.run([sys.executable,'-B',str(PACKAGE/script),'--config',str(path)],capture_output=True,text=True,env=env,timeout=5)
            self.assertEqual(result.returncode,0,result.stderr)
        proof=json.loads(result.stdout)
        adoption=self.root/'NOT-APPROVED.json'
        adoption.write_text(json.dumps(dict(configuration_sha256=proof['configuration_sha256'],runtime_sha256=proof['runtime_sha256'],lease_id=config['lease_id'],approved_by=None,cheap_live_controller_fixtures_verified=False)))
        result=subprocess.run([sys.executable,'-B',str(Path(config['run_dir'])/'controller.py'),'--execute','--adoption',str(adoption)],capture_output=True,text=True,env=env,timeout=5)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('coordinator adoption',result.stderr)
        self.assertFalse(list(Path(config['run_dir']).glob('window-state-*.json')))

    def test_fresh_finalized_complete_results_and_exact_outcomes_required(self):
        for mode in ['missing','empty','partial','stale','failure','error','skipped','duplicate',
                     'missing-class','missing-case','wrong-class','wrong-case','wrongfilter',
                     'wrongpackage','undeclared-macro','wrong-api','wrong-sdk-log','stale-log',
                     'exit7','wrong-seconds','wrong-label']:
            with self.subTest(mode=mode):
                f=self.fixture(mode)
                self.assertFalse(f.run()[0])

    def test_truncated_result_xml_cannot_return_success(self):
        f=self.fixture('malformed')
        with self.assertRaises(ET.ParseError):
            f.run()

    def test_missing_final_evidence_and_harmful_resource_events_remain_fail_closed(self):
        for mode in ['missing-final','owned-high','owned-oom']:
            with self.subTest(mode=mode):
                f=self.fixture(mode)
                self.assertFalse(f.run()[0])
                self.assertTrue(f.ns['state']['tests'][0]['stop_reason'])

    def test_source_parser_and_frozen_config_changes_during_run_refuse(self):
        for mode in ['source','parser','configuration','in-memory-config']:
            with self.subTest(mode=mode):
                f=self.fixture()
                original=f.results
                def results():
                    original()
                    if mode=='source':
                        p=f.project/f.configuration['plan'][0]['result_recipe']['inventory'][0]['source']
                        p.write_text(p.read_text()+'\n// changed after freeze\n')
                    elif mode=='parser':
                        p=f.project/'scripts/sift_telemetry.py';p.write_text(p.read_text()+'\n# changed\n')
                    elif mode=='in-memory-config':f.checked['plan'][0]['seconds']+=1
                    else:
                        p=f.base/'run-config.json';c=json.loads(p.read_text());c['lease_id']='tampered';p.write_text(json.dumps(c))
                f.results=results
                self.assertFalse(f.run()[0])
                self.assertFalse(f.ns['state']['tests'][0]['result_contract']['passed'])

    def test_aggregate_and_record_identity_tamper_refuse_actual_parser(self):
        f=self.fixture()
        self.assertEqual(f.run(),(True,True))
        rec=f.ns['state']['tests'][0]
        p=next(Path(rec['results']).rglob('TEST-*.xml'))
        root=ET.parse(p).getroot();aggregate=ET.Element('testsuites',tests='999');aggregate.append(root);ET.ElementTree(aggregate).write(p)
        self.assertFalse(assess_scoped(f.checked,*f.job[:4],rec,0,0,None)['passed'])
        aggregate.set('tests','30');ET.ElementTree(aggregate).write(p)
        for field,value in [('counts',dict(rec['counts'],tests=29)),('api_requested',31),('label','wrong'),('device_lines',[]),('command',rec['command'][:-1]+['wrong-filter'])]:
            with self.subTest(field=field):
                changed=dict(rec,**{field:value})
                self.assertFalse(assess_scoped(f.checked,*f.job[:4],changed,0,0,None)['passed'])

    def test_unrequested_stale_benchmarks_do_not_mask_fresh_execution(self):
        for fresh in [False,True]:
            with self.subTest(fresh=fresh):
                f=self.fixture()
                original=f.results
                def results():
                    original()
                    p=f.project/'macrobenchmark/build/outputs/androidTest-results/connected/benchmark/TEST-unrequested.xml'
                    f.xml(p,f.info['benchmark_cases'],None if fresh else 'stale')
                f.results=results
                self.assertEqual(f.run()[0],not fresh)

    def test_original_full_two_benchmark_predicate_and_failure_cases_are_preserved(self):
        for mode in ['valid','missing-one-benchmark','missing-both-benchmarks','benchmark-failure','stale-benchmark','exit7']:
            with self.subTest(mode=mode):
                f=self.fixture(mode,full=True)
                self.assertEqual(f.run()[0],mode=='valid')
                self.assertNotIn('result_contract',f.ns['state']['tests'][0])
        # Even when a scoped record claims all30 app passes, both benchmark tasks
        # cannot select the app-only exception through a declaration or argv edit.
        f=self.fixture()
        c=copy.deepcopy(f.configuration);c['run_dir']=str(self.root/'fresh-full')
        c['plan'][0]['args']=['scripts/instrumented-tests.sh',':macrobenchmark:connectedBenchmarkAndroidTest','--continue']
        with self.assertRaisesRegex(ValueError,'exact whole-class-list'):
            validate(c)

    def test_config_refuses_empty_partial_duplicate_stale_source_and_mixed_profiles(self):
        f=self.fixture()
        for mode in ['empty','subset','duplicate','stale','wrong-method','wrong-source','api31','package','method','mixed','extra-args','unknown-profile','unscoped']:
            with self.subTest(mode=mode):
                c=copy.deepcopy(f.configuration);c['run_dir']=str(self.root/('fresh-'+mode))
                job=c['plan'][0];r=job['result_recipe']
                if mode=='empty':r['classes']=[];r['inventory']=[]
                if mode=='subset':r['inventory'][0]['methods']=r['inventory'][0]['methods'][:-1]
                if mode=='duplicate':r['classes'].append(r['classes'][0]);r['inventory'].append(r['inventory'][0])
                if mode=='stale':r['inventory'][0]['sha256']='0'*64
                if mode=='wrong-method':r['inventory'][0]['methods'][0]='invented'
                if mode=='wrong-source':r['inventory'][0]['source']='other.kt'
                if mode=='api31':job['api']=31
                if mode=='package':job['args']=[job['args'][0],'-Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.widget']
                if mode=='method':job['args'][1]+='#nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod'
                if mode=='mixed':job['args'].append('-Pandroid.testInstrumentationRunnerArguments.package=other')
                if mode=='extra-args':job['args'].append('--continue')
                if mode=='unknown-profile':r['profile']='arbitrary'
                if mode=='unscoped':del job['result_recipe']
                with self.assertRaises(ValueError):validate(c)

    def test_changed_recipe_invalidates_modeled_adoption_before_work(self):
        f=self.fixture()
        p=f.base/'run-config.json';c=json.loads(p.read_text());c['plan'][0]['seconds']+=1;p.write_text(json.dumps(c))
        with self.assertRaisesRegex(ValueError,'coordinator adoption'):
            adopted_configuration(f.base,f.root/'HOST-MODELED-adoption.json')

    def test_source_shapes_inheritance_and_parameterization_are_explicit_refusals(self):
        f=self.fixture(widget=True)
        p=f.project/f.configuration['plan'][0]['result_recipe']['inventory'][0]['source'];original=p.read_text()
        for source in [original.replace('class WidgetServerTest {','class WidgetServerTest : Parent() {'),
                       original.replace('class WidgetServerTest {','@RunWith(Parameterized::class)\nclass WidgetServerTest {'),
                       original.replace('@Test\n','@Test(expected = Exception::class)\n',1),
                       original.replace('import org.junit.Test','import org.junit.Test as Other'),
                       original.replace('import org.junit.Test','import org.junit.Test\nimport org.junit.Test as Other'),
                       original+'\nclass Other { @Test fun hidden() {} }',
                       original.replace('@Test\n','@org.junit.Test\n',1),
                       original.replace('class WidgetServerTest {','class WidgetServerTest {\nclass Nested { @Test fun hidden() {} }')]:
            with self.subTest(source=source[:30]):
                p.write_text(source)
                with self.assertRaises(ValueError):inventory(f.project,CLASSES[-1])
        p.write_text(original+'\n// @Test fun phantom() {}\n/* outer /* @Test */ comment */\n')
        self.assertEqual(len(inventory(f.project,CLASSES[-1])['methods']),7)

    def test_full_benchmark_source_assertions_and_comparison_threshold_are_unchanged(self):
        expected={'START_ITERATIONS':15,'TAP_ITERATIONS':30}
        source=subprocess.check_output(['git','show','d7740f0:macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/Iterations.kt'],cwd=PACKAGE.parent.parent,text=True)
        for name,n in expected.items():self.assertIn('const val '+name+' = '+str(n),source)
        taps=subprocess.check_output(['git','show','d7740f0:macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/TimerTapBenchmark.kt'],cwd=PACKAGE.parent.parent,text=True)
        self.assertIn('const val STOP_TAPS = 5',taps)
        baseline=self.root/'baseline';current=self.root/'current';baseline.mkdir();current.mkdir()
        def data(value,missing=False):return {'benchmarks':[{'className':'fixture.Benchmark','name':'fixture','metrics':{} if missing else {'elapsedMs':{'median':value}}}]}
        (baseline/'fixture-benchmarkData.json').write_text(json.dumps(data(100)))
        for value,missing,code in [(100,False,0),(110,False,0),(111,False,1),(100,True,2)]:
            (current/'fixture-benchmarkData.json').write_text(json.dumps(data(value,missing)))
            result=subprocess.run(['bash',str(PACKAGE.parent/'compare-benchmarks.sh'),str(baseline),str(current)],capture_output=True,text=True,timeout=5)
            self.assertEqual(result.returncode,code,result.stderr)


if __name__=='__main__':unittest.main(verbosity=2)
