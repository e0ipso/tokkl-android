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
import io
import zipfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

from test_candidate import PACKAGE,SCRATCH
from scoped_controller_fixture import WorkloadFixture,CLASSES
import scoped_results as scoped
import configuration as hash_config
from scoped_results import declare,inventory,assess_scoped,PREFIX,COMPILE_ARGS,COMPILE_FIRST
from watchdog_lifetime import service_command
from runtime.configuration import validate,adopted_configuration
from scoped_results import BENCHMARK_API,BENCHMARK_ARGS,BENCHMARK_LABEL,APK_PATHS,declare_benchmark_api
from scoped_controller_fixture import DEVICE_BUILD


class ScopedHashTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def test_empty_alignment_boundaries_and_changed_bytes_match_every_byte(self):
        path = self.root/'input'
        for size in [0, 1, 65535, 65536, 65537, 3*65536+17]:
            data = (bytes(range(256))*((size+255)//256))[:size]
            path.write_bytes(data)
            with self.subTest(size=size), patch.object(Path, 'read_bytes', side_effect=AssertionError('whole-file fallback')):
                self.assertEqual(scoped.sha(path), hashlib.sha256(data).hexdigest())
        previous = scoped.sha(path)
        with path.open('r+b') as out:
            out.seek(65537);out.write(b'changed')
        self.assertNotEqual(scoped.sha(path), previous)

    def test_missing_short_unsupported_and_observed_identity_mutation_refuse(self):
        path = self.root/'input';path.write_bytes(b'a'*(2*65536+17))
        with self.assertRaises(FileNotFoundError):scoped.sha(self.root/'missing')
        for count in [0, 1, 65537]:
            with self.subTest(short_read=count), patch.object(os, 'preadv', return_value=count), self.assertRaises(OSError):
                scoped.sha(path)
        with patch.object(hash_config, '_direct_alignment', side_effect=OSError('unsupported direct I/O')),              patch.object(Path, 'open', side_effect=AssertionError('buffered fallback')),              self.assertRaisesRegex(OSError, 'unsupported direct I/O'):
            scoped.sha(path)
        original = os.preadv
        def mutate(fd, buffers, offset):
            count = original(fd, buffers, offset)
            if offset == 0:
                with path.open('r+b', buffering=0) as out:out.write(b'b')
            return count
        with patch.object(os, 'preadv', side_effect=mutate), self.assertRaisesRegex(ValueError, 'input changed during hashing'):
            scoped.sha(path)

    def test_package_and_standalone_import_orders_use_the_same_bounded_primitive(self):
        path = self.root/'input';path.write_bytes(b'checked source bytes')
        expected = hashlib.sha256(b'checked source bytes').hexdigest()
        code = """
import importlib,json,sys
from pathlib import Path
from unittest.mock import patch
sys.path[:0]=[sys.argv[1],str(Path(sys.argv[1])/'runtime')]
recipe_name,config_name,first,path,expected=sys.argv[2:]
for name in ([recipe_name,config_name] if first=='recipe' else [config_name,recipe_name]):
    importlib.import_module(name)
recipe=importlib.import_module(recipe_name);config=importlib.import_module(config_name)
with patch.object(config,'sha',wraps=config.sha) as bounded, patch.object(Path,'read_bytes',side_effect=AssertionError('whole-file fallback')):
    assert recipe.sha(path)==expected
    bounded.assert_called_once_with(path)
print(json.dumps(dict(recipe=recipe_name,first=first,digest=expected,bounded_primitive=True)))
"""
        for prefix in ['', 'runtime.']:
            for first in ['recipe', 'configuration']:
                result = subprocess.run([sys.executable, '-B', '-c', code, str(PACKAGE), prefix+'scoped_results',
                    prefix+'configuration', first, str(path), expected], capture_output=True, text=True, timeout=5)
                with self.subTest(prefix=prefix, first=first):
                    self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
                    self.assertTrue(json.loads(result.stdout)['bounded_primitive'])

    def test_large_scoped_hash_matches_digest_under_existing_host_memory_cap(self):
        path = self.root/'large-sparse-input'
        with path.open('wb') as out:out.truncate(96*1024**2+17)
        reference = subprocess.check_output(['sha256sum', str(path)], text=True).split()[0]
        result = subprocess.run([sys.executable, '-B', str(PACKAGE/'tests/hash_memory_probe.py'),
            str(PACKAGE/'runtime'), str(self.root/'unused-adoption'), str(path), 'scoped-sha'],
            capture_output=True, text=True, timeout=15)
        print('SCOPED_HASH_PROBE '+result.stdout.strip(), flush=True)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        receipt = json.loads(result.stdout)
        self.assertEqual(receipt['status'], 'validated')
        self.assertEqual(receipt['digest'], reference)
        self.assertFalse(receipt['resource_actions'])
        self.assertGreater(receipt['fixture_bytes'], receipt['address_space_limit_bytes'])
        self.assertLess(receipt['peak_rss_bytes'], receipt['address_space_limit_bytes'])


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


class BenchmarkApiTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(dir=SCRATCH)
        self.root=Path(self.temp.name);self.number=0

    def tearDown(self):
        self.temp.cleanup()
        self.assertFalse(self.root.exists())

    def fixture(self,scenario='valid',mutate=None):
        self.number+=1
        f=WorkloadFixture(self.root/str(self.number),scenario,benchmark_api=True)
        if mutate:
            original=f.results
            def results():
                original();mutate(f)
            f.results=results
        return f

    def data_path(self,f):
        return f.project/'macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/fixture-benchmarkData.json'

    def change_data(self,f,fn):
        path=self.data_path(f);data=json.loads(path.read_text());fn(data);path.write_text(json.dumps(data))

    def run_validated_plan(self,f):
        # Exercise the actual outer plan-to-call path as well as its real
        # run_workload/Session/assessor. Only external operations are modeled.
        namespace=f.ns;actual=namespace['run_workload'];calls=[]
        original={'memory.high':'10737418240','memory.max':'12884901888','memory.swap.max':'2147483648'}
        shared=dict(original)
        expected=[(j['api'],j['label'],j['args'],j['seconds'],j['standalone']) for j in f.checked['plan']]
        self.assertEqual(validate(f.configuration,fresh=False),f.checked)
        self.assertEqual(expected,[tuple(f.job)])
        def command(args,label,refresh=False):
            if label=='apply-window':shared.update(f.temporary)
            if label=='immediate-rollback':shared.update(original)
            return 0
        def run_workload(*job):
            self.assertIn(job,expected);calls.append(job)
            return actual(*job)
        namespace.update(run_workload=run_workload,
            state_path=f.base/'controller-state.json',unit='host-modeled-benchmark-timer',
            bounds={'fallback_seconds':3000},armed=False,watchdog_armed=False,_controller_error=None,result=1,
            original=original,limits=lambda:dict(shared),
            conditions=lambda:{'limits':dict(shared),'baseline_pass':True,'host_pass':True},
            command=command,service_command=service_command,sys=sys,
            cleanup_active_workload=lambda *args:{'success':True},settle_active_workload=lambda *args:True)
        namespace['state']['prepared_labels']=[BENCHMARK_LABEL]
        tree=ast.parse((PACKAGE/'runtime/controller.py').read_text())
        plan_assignment=next(n for n in tree.body if isinstance(n,ast.Assign)
                             and any(isinstance(t,ast.Name) and t.id=='plan' for t in n.targets))
        outer=next(n for n in tree.body if isinstance(n,ast.Try) and n.finalbody)
        with f.context():
            exec(compile(ast.Module(body=[plan_assignment,outer],type_ignores=[]),'<actual-validated-benchmark-plan>','exec'),namespace)
        self.assertEqual(calls,expected)
        self.assertEqual(shared,original)
        return namespace['result']

    def test_exact_profile_passes_actual_collector_and_retains_75_values_45_traces(self):
        f=self.fixture();self.assertEqual(self.run_validated_plan(f),0)
        rec=f.ns['state']['tests'][0];r=rec['result_contract']
        self.assertEqual(r['profile'],BENCHMARK_API)
        self.assertEqual(r['app_counts'],dict(tests=10,failures=0,errors=0,skipped=0))
        self.assertEqual(r['benchmark_counts'],dict(tests=2,failures=0,errors=0,skipped=0))
        self.assertEqual(r['metric_values'],75)
        self.assertEqual(len(r['traces']),45)
        self.assertEqual(len({t['sha256'] for t in r['traces']}),45)
        self.assertEqual(r['apks'],f.configuration['plan'][0]['result_recipe']['apks'])
        self.assertEqual(r['comparison'],'not performed');self.assertFalse(r['full_app_gate'])
        self.assertEqual(f.identity_rows,{})
        self.assertTrue((f.session.path/'completed.json').is_file())

    def test_current_command_filter_overrides_and_job_identities_refuse(self):
        f=self.fixture()
        variants=[BENCHMARK_ARGS[:-1],BENCHMARK_ARGS+['--continue'],
                  BENCHMARK_ARGS+['-Pandroid.testInstrumentationRunnerArguments.class=other.Test'],
                  BENCHMARK_ARGS+['-Pandroid.testInstrumentationRunnerArguments.notClass=other.Test'],
                  BENCHMARK_ARGS+['-Pandroid.testInstrumentationRunnerArguments.testFile=/owned/file'],
                  BENCHMARK_ARGS+[':app:check'],
                  BENCHMARK_ARGS[:-1]+[BENCHMARK_ARGS[-1].replace('benchmark,com.github.e0ipso.tokkl.api','api,com.github.e0ipso.tokkl.benchmark')],
                  BENCHMARK_ARGS[:-1]+[BENCHMARK_ARGS[-1]+',com.github.e0ipso.tokkl.api']]
        for i,args in enumerate(variants):
            with self.subTest(args=args):
                c=copy.deepcopy(f.configuration);c['run_dir']=str(self.root/f'bad-args-{i}');c['plan'][0]['args']=args
                with self.assertRaises(ValueError):validate(c)
        for key,value in [('label','other-job'),('label','tap-scoped-api'),('api',31),('standalone',True),('seconds',1799),('seconds',1801),('seconds',5400)]:
            with self.subTest(key=key,value=value):
                c=copy.deepcopy(f.configuration);c['run_dir']=str(self.root/f'bad-{key}-{value}');c['plan'][0][key]=value
                with self.assertRaises(ValueError):validate(c)

    def test_undeclared_label_and_wrong_mode_configs_refuse_before_preparation_or_runtime(self):
        # Preserve the first attempt's failed assertion/log verbatim outside the
        # checkout. Here the negative modifies CONFIG before its actual validator,
        # rather than an unreachable separate internal-call tuple after adoption.
        for mode in ['wrong-label','wrong-mode']:
            with self.subTest(mode=mode):
                with self.assertRaises(ValueError):self.fixture(mode)
                self.assertFalse((self.root/str(self.number)/'evidence').exists())
        f=self.fixture();p=f.base/'run-config.json';c=json.loads(p.read_text())
        c['plan'][0]['label']='other-job';p.write_text(json.dumps(c))
        with self.assertRaises(ValueError):adopted_configuration(f.base,f.root/'HOST-MODELED-adoption.json')
        self.assertEqual(f.ns['state']['tests'],[])
        self.assertFalse(list(f.base.glob('completion-*')))

    def test_source_complete_recipe_pins_and_apk_identity_cannot_be_truncated(self):
        f=self.fixture()
        for mode in ['subset','benchmark-method','source','device','extra','missing-pin','missing-apk-pin']:
            with self.subTest(mode=mode):
                c=copy.deepcopy(f.configuration);c['run_dir']=str(self.root/('bad-'+mode));r=c['plan'][0]['result_recipe']
                if mode=='subset':r['inventory'][0]['methods'].pop()
                if mode=='benchmark-method':r['inventory'][2]['methods']=['invented']
                if mode=='source':r['sources'][0]['sha256']='0'*64
                if mode=='device':r['device_build']['version']['sdk']=31
                if mode=='extra':r['unchecked']='override'
                if mode=='missing-pin':del c['input_pins'][str(f.project/r['inventory'][0]['source'])]
                if mode=='missing-apk-pin':del c['input_pins'][str(f.project/APK_PATHS[0])]
                with self.assertRaises(ValueError):validate(c)

    def test_added_api_source_method_unsupported_shape_and_changed_benchmark_refuse(self):
        for mode in ['new-file','new-method','inheritance','parameterized','benchmark']:
            with self.subTest(mode=mode):
                f=self.fixture();r=f.configuration['plan'][0]['result_recipe'];p=f.project/r['inventory'][0]['source']
                if mode=='new-file':p.with_name('Extra.kt').write_text('package com.github.e0ipso.tokkl.api\nclass Extra { @Test fun extra() {} }')
                if mode=='new-method':p.write_text(p.read_text().replace('class PinnedCertificateTest {','class PinnedCertificateTest {\n@Test fun added() {}'))
                if mode=='inheritance':p.write_text(p.read_text().replace('class PinnedCertificateTest {','class PinnedCertificateTest : Parent() {'))
                if mode=='parameterized':p.write_text(p.read_text().replace('class PinnedCertificateTest {','@RunWith(Parameterized::class)\nclass PinnedCertificateTest {'))
                if mode=='benchmark':
                    p=f.project/r['inventory'][2]['source'];p.write_text(p.read_text()+'\n// changed benchmark bytes\n')
                with self.assertRaises(ValueError):declare_benchmark_api(f.project,DEVICE_BUILD)

    def test_api_missing_duplicate_skipped_failed_and_actual_job_changes_refuse(self):
        for mode in ['missing','empty','partial','stale','failure','error','skipped','duplicate','missing-class','missing-case',
                     'wrong-class','wrong-case','wrongfilter','wrongpackage','undeclared-macro','wrong-api','wrong-sdk-log','stale-log','exit7','wrong-seconds']:
            with self.subTest(mode=mode):self.assertFalse(self.fixture(mode).run()[0])

    def test_benchmark_junit_complete_membership_outcomes_aggregates_and_freshness(self):
        for mode in ['missing','duplicate','skipped','failure','error','stale','foreign','aggregate','partial']:
            with self.subTest(mode=mode):
                def mutate(f):
                    path=f.project/'macrobenchmark/build/outputs/androidTest-results/connected/benchmark/TEST-bench.xml'
                    root=ET.parse(path).getroot()
                    if mode=='missing':root.remove(root[-1]);root.set('tests','1')
                    if mode=='duplicate':root.append(copy.deepcopy(root[0]));root.set('tests','3')
                    if mode in ['skipped','failure','error']:ET.SubElement(root[0],mode);root.set({'failure':'failures','error':'errors','skipped':'skipped'}[mode],'1')
                    if mode=='foreign':root[0].set('classname','foreign.Benchmark')
                    if mode=='partial':root.set('tests','9')
                    if mode=='aggregate':
                        aggregate=ET.Element('testsuites',tests='99');aggregate.append(root);root=aggregate
                    ET.ElementTree(root).write(path)
                    if mode=='stale':os.utime(path,(1,1))
                self.assertFalse(self.fixture(mutate=mutate).run()[0])

    def test_metric_corpus_case_device_iteration_and_every_sample_are_mandatory(self):
        for mode in ['missing','extra-corpus','stale','truncated','duplicate-key','duplicate-case','foreign-case','wrong-device',
                     'iteration','missing-metric','extra-metric','short-runs','null','nan','infinite','zero','negative','bool',
                     'median','minimum','maximum','params','sampled']:
            with self.subTest(mode=mode):
                def mutate(f):
                    path=self.data_path(f)
                    if mode=='missing':path.unlink();return
                    if mode=='extra-corpus':path.with_name('extra-benchmarkData.json').write_bytes(path.read_bytes());return
                    if mode=='stale':os.utime(path,(1,1));return
                    if mode=='truncated':path.write_text('{');return
                    if mode=='duplicate-key':path.write_text(path.read_text().replace('"context":', '"context":{},"context":',1));return
                    def change(d):
                        b=d['benchmarks'][0];m=b['metrics']['timeToInitialDisplayMs']
                        if mode=='duplicate-case':d['benchmarks'][1]=copy.deepcopy(b)
                        if mode=='foreign-case':b['name']='other'
                        if mode=='wrong-device':d['context']['build']['fingerprint']='foreign'
                        if mode=='iteration':b['repeatIterations']=14
                        if mode=='missing-metric':b['metrics']={}
                        if mode=='extra-metric':b['metrics']['other']=copy.deepcopy(m)
                        if mode=='short-runs':m['runs'].pop()
                        if mode in ['null','nan','infinite','zero','negative','bool']:m['runs'][0]={'null':None,'nan':float('nan'),'infinite':float('inf'),'zero':0,'negative':-1,'bool':True}[mode]
                        if mode in ['median','minimum','maximum']:m[mode]=101.
                        if mode=='params':b['params']={'partial':'yes'}
                        if mode=='sampled':b['sampledMetrics']={'other':{}}
                    self.change_data(f,change)
                self.assertFalse(self.fixture(mutate=mutate).run()[0])

    def test_trace_reference_indices_paths_and_complete_actual_artifacts(self):
        for mode in ['missing-ref','duplicate-ref','wrong-index','foreign','unsafe','missing','extra','duplicate-bytes','empty','stale','truncated','malformed']:
            with self.subTest(mode=mode):
                def mutate(f):
                    path=self.data_path(f);d=json.loads(path.read_text());refs=d['benchmarks'][0]['profilerOutputs'];trace=path.parent/refs[0]['filename']
                    if mode=='missing-ref':refs.pop()
                    if mode=='duplicate-ref':refs[-1]=copy.deepcopy(refs[0])
                    if mode=='wrong-index':refs[0]['label']='Trace Iteration 99'
                    if mode=='foreign':refs[0]['filename']='Other_'+refs[0]['filename']
                    if mode=='unsafe':refs[0]['filename']='../'+refs[0]['filename']
                    if mode=='missing':trace.unlink()
                    if mode=='extra':trace.with_name('extra.perfetto-trace').write_bytes(trace.read_bytes())
                    if mode=='duplicate-bytes':(path.parent/refs[1]['filename']).write_bytes(trace.read_bytes())
                    if mode=='empty':trace.write_bytes(b'')
                    if mode=='stale':os.utime(trace,(1,1))
                    if mode=='truncated':trace.write_bytes(b'\x0a\x08\x40\x01')
                    if mode=='malformed':trace.write_bytes(b'not a trace')
                    path.write_text(json.dumps(d))
                self.assertFalse(self.fixture(mutate=mutate).run()[0])

    def test_last_stop_sample_and_last_trace_cannot_be_omitted(self):
        for mode in ['last-sample','last-trace']:
            with self.subTest(mode=mode):
                def mutate(f):
                    path=self.data_path(f);d=json.loads(path.read_text());b=d['benchmarks'][-1]
                    if mode=='last-sample':b['metrics']['TimerStopFirstMs']['runs'][-1]=None
                    if mode=='last-trace':(path.parent/b['profilerOutputs'][-1]['filename']).unlink()
                    path.write_text(json.dumps(d))
                self.assertFalse(self.fixture(mutate=mutate).run()[0])

    def test_zip_trace_container_positive_and_integrity_refusals(self):
        for mode in ['valid','crc','multiple','unsafe','bad-protobuf','late-bad-field','oversized']:
            with self.subTest(mode=mode):
                def mutate(f):
                    path=self.data_path(f);d=json.loads(path.read_text());trace=path.parent/d['benchmarks'][0]['profilerOutputs'][0]['filename']
                    raw=trace.read_bytes();buf=io.BytesIO()
                    if mode=='bad-protobuf':raw=b'\x0a\x08\x40\x01'
                    if mode=='late-bad-field':raw=b'\x0a\x03\x40\x01\x80'
                    with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as archive:
                        archive.writestr('../Trace_output.pb' if mode=='unsafe' else 'Trace_output.pb',raw)
                        if mode=='multiple':archive.writestr('extra.pb',raw)
                    value=bytearray(buf.getvalue())
                    if mode=='crc':value[14:18]=b'\x01\x02\x03\x04';where=value.index(b'PK\x01\x02');value[where+16:where+20]=b'\x01\x02\x03\x04'
                    if mode=='oversized':where=value.index(b'PK\x01\x02');value[where+24:where+28]=(64*1024*1024+1).to_bytes(4,'little')
                    trace.write_bytes(value)
                self.assertEqual(self.fixture(mutate=mutate).run()[0],mode=='valid')

    def test_missing_finalization_and_harmful_resource_evidence_remain_controller_failures(self):
        for mode in ['missing-final','owned-high','owned-oom']:
            with self.subTest(mode=mode):
                f=self.fixture(mode);self.assertFalse(f.run()[0]);self.assertTrue(f.ns['state']['tests'][0]['stop_reason'])

    def test_cleanup_and_record_forgery_cannot_produce_profile_pass(self):
        f=self.fixture();self.assertEqual(f.run(),(True,True));rec=f.ns['state']['tests'][0]
        for key,value in [('work_directories_removed',False),('private_android_tmp_removed',False),('emulator_scopes_absent',False),
                          ('new_shared_android_files',['foreign']),('observed_android_tmp_env',{}),('private_android_tmp',[])]:
            with self.subTest(cleanup=key):
                changed=copy.deepcopy(rec);changed['cleanup'][key]=value
                self.assertFalse(assess_scoped(f.checked,*f.job[:4],changed,0,0,None)['passed'])
        for key,value in [('exit',7),('stop_reason','resource failure'),('counts',dict(rec['counts'],tests=9)),
                          ('benchmark_counts',dict(rec['benchmark_counts'],tests=1)),('benchmark_cases',[]),
                          ('api_requested',31),('label','other-job'),('standalone',True),
                          ('device_lines',[]),('command',rec['command'][:-1]+['override'])]:
            with self.subTest(field=key):
                self.assertFalse(assess_scoped(f.checked,*f.job[:4],dict(rec,**{key:value}),0,0,None)['passed'])
        args=list(f.job[:4]);args[1]='other-job'
        self.assertFalse(assess_scoped(f.checked,*args,rec,0,0,None)['passed'])

    def test_foreign_result_directory_and_symlink_trace_refuse_assessor(self):
        f=self.fixture();self.assertEqual(f.run(),(True,True));rec=f.ns['state']['tests'][0]
        changed=copy.deepcopy(rec);changed['results']=str(f.project/'app/build/outputs/androidTest-results/connected/debug')
        self.assertFalse(assess_scoped(f.checked,*f.job[:4],changed,0,0,None)['passed'])
        folder=Path(rec['benchmark-data']);trace=next(folder.glob('*.perfetto-trace'))
        retained=self.root/'foreign-trace';retained.write_bytes(trace.read_bytes());trace.unlink();trace.symlink_to(retained)
        self.assertFalse(assess_scoped(f.checked,*f.job[:4],rec,0,0,None)['passed'])

    def test_source_parser_apk_runtime_and_config_mutations_during_execution_refuse(self):
        for mode in ['source','parser','apk','runtime','config']:
            with self.subTest(mode=mode):
                def mutate(f):
                    if mode=='source':path=f.project/f.configuration['plan'][0]['result_recipe']['inventory'][0]['source']
                    if mode=='parser':path=f.project/'scripts/sift_telemetry.py'
                    if mode=='apk':path=f.project/APK_PATHS[0]
                    if mode=='runtime':path=f.base/'scope_completion.py'
                    if mode=='config':path=f.base/'run-config.json'
                    path.write_bytes(path.read_bytes()+b'\nchanged')
                self.assertFalse(self.fixture(mutate=mutate).run()[0])

    def test_existing_full_fallback_still_requires_unfiltered_5400_seconds(self):
        f=self.fixture()
        for args,seconds in [(BENCHMARK_ARGS,5400),(['scripts/instrumented-tests.sh',':macrobenchmark:connectedBenchmarkAndroidTest','--continue'],1800)]:
            c=copy.deepcopy(f.configuration);c['run_dir']=str(self.root/'unscoped');job=c['plan'][0]
            del job['result_recipe'];job.update(args=args,seconds=seconds)
            with self.assertRaises(ValueError):validate(c)


if __name__=='__main__':unittest.main(verbosity=2)
