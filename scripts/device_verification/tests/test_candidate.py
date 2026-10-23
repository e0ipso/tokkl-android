"""Maintained controller tests. All cgroups/devices/services are disk fixtures.

The launcher test executes the actual boot function with external commands
replaced by argument-recording shell functions. It never runs systemd or adb.
The cleanup tests execute the actual outer controller try/finally with modeled
resource operations; fresh live cgroup integration remains a coordinator gate.
"""
from pathlib import Path
from datetime import datetime, timezone
import ast
import copy
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

import conformance
from runtime import configuration as cfg
from prepare import prepare
import scope_completion as sc
import watchdog_lifetime as watchdog
from duration_policy import window_bounds, full_suite_seconds
from controller_cleanup import install_controller
from test_event_monitor import snapshot, APP, OWN
from test_live import monitored_sample
from event_monitor_0274 import Monitor
import event_monitor_0274 as events

PACKAGE = Path(__file__).resolve().parent.parent
PROJECT = PACKAGE.parent.parent
SCRATCH = Path(os.environ['TOKKL_HOST_FIXTURE_ROOT'])


def config(directory):
    launcher = PROJECT/'scripts/instrumented-tests.sh'
    source = launcher.read_text()
    import re
    pins = [launcher, Path(cfg.CANONICAL_GATE),
            Path('/home/e0ipso/workspace/apps/tokkl-android/scripts/mem-gate.sh'),
            Path('/home/e0ipso/workspace/apps/tokkl-android/scripts/memory-capacity.py')]
    return {'schema_version': 1, 'stage': 'development', 'run_id': 'host-proof',
            'lease_id': 'NOT-A-LIVE-LEASE', 'budget_authorization': 'pure-host fixture only',
            'project': str(PROJECT), 'run_dir': str(directory/'frozen'), 'gate': cfg.CANONICAL_GATE,
            'checked_head': '0'*40, 'environment': {'PATH': '/usr/bin:/bin', 'JAVA_HOME': '/jdk', 'ANDROID_HOME': '/sdk'},
            'reclaim_budget_bytes': 0, 'carried_reclaim_bytes': 0, 'prior_window': None,
            'preparation_policy': {'max_request_bytes': 0, 'max_preparations': 4},
            'slots': 1, 'plan': [{'api': 36, 'label': 'full36', 'standalone': False, 'seconds': 5400,
                                 'args': ['scripts/instrumented-tests.sh', ':macrobenchmark:connectedBenchmarkAndroidTest', '--continue']}],
            'server_image': re.search(r"^readonly IMAGE='([^']+)'$", source, re.M)[1],
            'proxy_image': re.search(r"^readonly PROXY_IMAGE='([^']+)'$", source, re.M)[1],
            'input_pins': {str(p): cfg.sha(p) for p in pins}}


class ConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.directory = Path(self.tmp.name)
        self.config = config(self.directory)

    def tearDown(self):
        self.tmp.cleanup()

    def test_readonly_preflight_and_snapshot_never_execute_controller(self):
        self.assertTrue(conformance.verify()['conformant'])
        checked = cfg.validate(self.config)
        self.assertFalse(Path(self.config['run_dir']).exists())
        result = prepare(self.config)
        self.assertEqual(result['configuration_sha256'], checked['_sha256'])
        self.assertFalse(result['device_or_window_permission'])
        self.assertTrue((Path(result['run_dir'])/'scratch').is_dir())
        self.assertEqual((Path(result['run_dir'])/'scratch').stat().st_mode & 0o777, 0o700)
        self.assertEqual((Path(result['run_dir'])/'scope_completion.py').read_bytes(),
                         (PACKAGE/'runtime/scope_completion.py').read_bytes())
        with self.assertRaisesRegex(ValueError, 'fresh unused'):
            prepare(self.config)

    def test_failed_live_samples_are_persisted_without_invented_evidence(self):
        path = self.directory/'samples.jsonl'
        tree = {'rows': {}, 'errors': ['required scope vanished']}
        assessment = {'abort': 'unknown evidence'}
        self.assertEqual(monitored_sample(path, lambda: tree, lambda value: assessment), (tree, assessment))
        self.assertEqual(json.loads(path.read_text())['assessment'], assessment)
        def fail():
            raise OSError('capture failed before returning a sample')
        with self.assertRaises(OSError):
            monitored_sample(path, fail, lambda value: self.fail('no sample to inspect'))
        rows = [json.loads(line) for line in path.read_text().splitlines()]
        self.assertIsNone(rows[-1]['tree'])
        self.assertIn('capture failed', rows[-1]['error'])

    def test_snapshot_scratch_must_exist_and_remain_private(self):
        result = prepare(self.config)
        scratch = Path(result['run_dir'])/'scratch'
        scratch.chmod(0o755)
        with self.assertRaisesRegex(ValueError, 'scratch directory'):
            cfg.adopted_configuration(Path(result['run_dir']), self.directory/'absent-adoption.json')
        scratch.rmdir()
        with self.assertRaisesRegex(ValueError, 'scratch directory'):
            cfg.adopted_configuration(Path(result['run_dir']), self.directory/'absent-adoption.json')

    def test_default_and_missing_adoption_refuse_before_any_host_work(self):
        result = subprocess.run([sys.executable, '-B', str(PACKAGE/'runtime/controller.py')], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Execution disabled', result.stderr)
        self.assertFalse(Path(self.config['run_dir']).exists())
        prepare(self.config)
        result = subprocess.run([sys.executable, '-B', str(Path(self.config['run_dir'])/'controller.py'), '--execute'], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('--adoption', result.stderr)
        self.assertFalse(list(Path(self.config['run_dir']).glob('window-state*')))

    def test_api31_concurrency_arbitrary_commands_and_unbounded_deadlines_refuse(self):
        for change in [{'stage': 'compatibility'}, {'slots': 2}, {'reclaim_budget_bytes': cfg.MAX_BUDGET+1},
                       {'carried_reclaim_bytes': 1}, {'gate': '/some/other/gate'}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                cfg.validate(dict(self.config, **change))
        for change in [{'api': 31}, {'seconds': 0}, {'seconds': 999999},
                       {'args': ['bash', '-c', 'true']}, {'args': ['scripts/instrumented-tests.sh']}]:
            candidate = copy.deepcopy(self.config)
            candidate['plan'][0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                cfg.validate(candidate)

    def test_adoption_must_match_snapshot_and_cannot_stand_in_for_live_fixtures(self):
        result = prepare(self.config)
        adoption = dict(configuration_sha256=result['configuration_sha256'],
                        runtime_sha256=result['runtime_sha256'], lease_id=self.config['lease_id'],
                        approved_by='MODELED-HOST-FIXTURE', cheap_live_controller_fixtures_verified=False)
        path = self.directory/'adoption.json'
        path.write_text(json.dumps(adoption))
        controller = Path(result['run_dir'])/'controller.py'
        argv = ['controller.py', '--execute', '--adoption', str(path)]
        with patch.object(sys, 'argv', argv), self.assertRaisesRegex(ValueError, 'cheap owned live'):
            cfg.execution_configuration(controller)
        adoption['configuration_sha256'] = '0'*64
        path.write_text(json.dumps(adoption))
        with patch.object(sys, 'argv', argv), self.assertRaisesRegex(ValueError, 'explicit coordinator adoption'):
            cfg.execution_configuration(controller)
        self.assertFalse(list(Path(result['run_dir']).glob('window-state*')))

    def test_actual_image_and_source_pins_must_match(self):
        candidate = copy.deepcopy(self.config)
        candidate['server_image'] = 'tag-only'
        with self.assertRaisesRegex(ValueError, 'actual launcher'):
            cfg.validate(candidate)
        candidate = copy.deepcopy(self.config)
        path = next(iter(candidate['input_pins']))
        candidate['input_pins'][path] = '0'*64
        with self.assertRaisesRegex(ValueError, 'input changed'):
            cfg.validate(candidate)

    def test_carried_reservation_requires_unchanged_prior_ledger(self):
        ledger = self.directory/'prior.json'
        ledger.write_text(json.dumps({'reclaim_reserved_bytes': 123}))
        candidate = dict(self.config, reclaim_budget_bytes=1000, carried_reclaim_bytes=123,
                         preparation_policy={'max_request_bytes': 1000, 'max_preparations': 4},
                         prior_window=str(ledger), prior_window_sha256=cfg.sha(ledger))
        self.assertTrue(cfg.validate(candidate))
        candidate['carried_reclaim_bytes'] = 0
        with self.assertRaisesRegex(ValueError, 'never reset'):
            cfg.validate(candidate)
        candidate['carried_reclaim_bytes'] = 123
        ledger.write_text(json.dumps({'reclaim_reserved_bytes': 124}))
        with self.assertRaisesRegex(ValueError, 'ledger changed'):
            cfg.validate(candidate)

    def nine_gib_candidate(self):
        ledger = self.directory/'nine-gib-prior.json'
        ledger.write_text(json.dumps({'reclaim_reserved_bytes': 6191243264,
                                     'preparations': [{'label': 'historical-'+str(i)} for i in range(5)]}))
        candidate = copy.deepcopy(self.config)
        candidate.update(reclaim_budget_bytes=9*1024**3, carried_reclaim_bytes=6191243264,
                         prior_window=str(ledger), prior_window_sha256=cfg.sha(ledger),
                         preparation_policy={'max_request_bytes': 9*1024**3, 'max_preparations': 2})
        candidate['plan'].append(dict(api=0, label='assemble-check', standalone=True, seconds=1800,
            args=['./gradlew', '--no-daemon', 'assembleDebug', 'check', '--stacktrace']))
        return candidate

    def test_nine_gib_accepts_actual_carry_and_two_reviewed_local_allowances(self):
        candidate = self.nine_gib_candidate()
        checked = cfg.validate(candidate)
        self.assertEqual(checked['reclaim_budget_bytes']-checked['carried_reclaim_bytes'], 3472433152)
        self.assertEqual(checked['preparation_policy']['max_preparations'], 2)
        history = json.loads(Path(candidate['prior_window']).read_text())
        self.assertEqual(len(history['preparations']), 5)
        self.assertEqual(len(history['preparations'])+checked['preparation_policy']['max_preparations'], 7)
        self.assertEqual(cfg.validate(self.config)['reclaim_budget_bytes'], 0)
        legacy = copy.deepcopy(self.config)
        legacy.update(reclaim_budget_bytes=6*1024**3,
                      preparation_policy={'max_request_bytes': 6*1024**3, 'max_preparations': 4})
        self.assertEqual(cfg.validate(legacy)['reclaim_budget_bytes'], 6*1024**3)
        proof = prepare(candidate)  # Owned disk model only, not execution adoption.
        adoption = self.directory/'HOST-ONLY-nine-gib-adoption.json'
        adoption.write_text(json.dumps(dict(configuration_sha256=proof['configuration_sha256'],
            runtime_sha256=proof['runtime_sha256'], lease_id=candidate['lease_id'],
            approved_by='HOST-MODEL-ONLY', cheap_live_controller_fixtures_verified=True)))
        self.assertEqual(cfg.adopted_configuration(Path(proof['run_dir']), adoption), checked)
        self.assertFalse(list(Path(proof['run_dir']).glob('window-state-*')))

    def test_nine_gib_budget_carry_hash_and_allowance_refusals(self):
        candidate = self.nine_gib_candidate()
        for key in ['reclaim_budget_bytes', 'carried_reclaim_bytes']:
            for value in [9*1024**3+1, -1, True, 1.5, '9663676416', None]:
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    cfg.validate(dict(candidate, **{key: value}))
        for change in [{'reclaim_budget_bytes': 6191243263}, {'carried_reclaim_bytes': 0},
                       {'prior_window': None}, {'prior_window_sha256': '0'*64}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                cfg.validate(dict(candidate, **change))
        for value in [0, 1, 5, -1, True, 1.5]:
            bad = copy.deepcopy(candidate);bad['preparation_policy']['max_preparations'] = value
            with self.subTest(allowance=value), self.assertRaises(ValueError):cfg.validate(bad)
        bad = copy.deepcopy(candidate);del bad['prior_window_sha256']
        with self.assertRaisesRegex(ValueError, 'prior ledger changed'):cfg.validate(bad)
        Path(candidate['prior_window']).write_text('{"reclaim_reserved_bytes":0}')
        with self.assertRaisesRegex(ValueError, 'prior ledger changed'):cfg.validate(candidate)

    def test_nine_gib_ceiling_preserves_actual_opening_six_nine_boundaries(self):
        node = next(n for n in ast.parse((PACKAGE/'runtime/controller.py').read_text()).body
                    if isinstance(n, ast.FunctionDef) and n.name == 'conditions')
        meminfo = self.directory/'meminfo'
        original = {'memory.high': str(10*1024**3), 'memory.max': str(12*1024**3),
                    'memory.swap.max': str(2*1024**3)}
        ns = {'app': self.directory, 'Path': lambda _: meminfo, 'now': lambda: 'host-fixture',
              'limits': lambda: dict(original)}
        exec(compile(ast.Module(body=[node], type_ignores=[]), '<actual-opening-conditions>', 'exec'), ns)
        for current in [6*1024**3, 6*1024**3+1]:
            for host_kib in [9*1024**2, 9*1024**2-1]:
                (self.directory/'memory.current').write_text(str(current))
                meminfo.write_text('MemAvailable: '+str(host_kib)+' kB\n')
                result = ns['conditions']()
                self.assertEqual(result['baseline_pass'], current <= 6*1024**3)
                self.assertEqual(result['host_pass'], host_kib >= 9*1024**2)
                self.assertEqual(result['limits'], original)

    def bootstrap_candidate(self):
        candidate = copy.deepcopy(self.config)
        candidate['plan_recipe'] = 'benchmark-apk-bootstrap-v1'
        candidate['plan'] = [dict(api=0, label='benchmark-apk-bootstrap', standalone=True, seconds=1800,
            args=['./gradlew', '--no-daemon', ':app:assembleDebug', ':app:assembleDebugAndroidTest',
                  ':app:assembleBenchmark', ':macrobenchmark:assembleBenchmark', '--stacktrace'])]
        wrapper = PROJECT/'gradlew';candidate['input_pins'][str(wrapper)] = cfg.sha(wrapper)
        return candidate

    def test_exact_one_job_bootstrap_validates_without_executing_or_claiming_artifacts(self):
        candidate = self.bootstrap_candidate();checked = cfg.validate(candidate)
        self.assertEqual(checked['plan'], candidate['plan'])
        job = checked['plan'][0]
        self.assertEqual(set(job), {'api', 'label', 'standalone', 'seconds', 'args'})
        self.assertFalse(Path(checked['run_dir']).exists())
        self.assertEqual(checked['reclaim_budget_bytes'], 0)
        bounds = window_bounds([(job['api'], job['label'], job['args'], job['seconds'], job['standalone'])])
        self.assertEqual(bounds['job_bounds_seconds'], [2235])
        self.assertEqual(bounds['fallback_seconds'], 2520)
        # Approval and artifact identity remain separate from this read-only check.
        with self.assertRaises((ValueError, FileNotFoundError)):
            cfg.adopted_configuration(Path(checked['run_dir']), self.directory/'not-approved.json')
        self.assertFalse(Path(checked['run_dir']).exists())

    def test_bootstrap_wrong_label_api_mode_duration_and_added_recipe_fields_refuse(self):
        candidate = self.bootstrap_candidate()
        variants = [('label','assemble-check'), ('label','other-job'), ('api',36), ('api',31), ('api',False),
                    ('standalone',False), ('standalone',1), ('seconds',1799), ('seconds',1801), ('seconds',5400),
                    ('seconds',1800.0), ('result_recipe',{}), ('host_recipe','assemble-check-then-repro-v1'),
                    ('extra','ignored')]
        for field, value in variants:
            bad = copy.deepcopy(candidate);bad['plan'][0][field] = value
            with self.subTest(field=field,value=value), self.assertRaises(ValueError):cfg.validate(bad)
        for field in ['label','api','standalone','seconds','args']:
            bad = copy.deepcopy(candidate);del bad['plan'][0][field]
            with self.subTest(missing=field), self.assertRaises(ValueError):cfg.validate(bad)

    def test_bootstrap_exact_task_order_and_wrapper_flag_refusals(self):
        candidate = self.bootstrap_candidate();args = candidate['plan'][0]['args']
        alternatives = [args[:-1], args+['--continue'], args+['check'], args+['--stop'],
                        ['env']+args, ['bash','-c',' '.join(args)], args+[':app:assembleDebug'],
                        args[:2]+list(reversed(args[2:6]))+args[6:], args[:2]+args[3:],
                        args[:2]+['assembleDebug']+args[3:], ['./gradlew', ':app:assembleDebug', '--no-daemon']+args[3:]]
        for argv in alternatives:
            bad = copy.deepcopy(candidate);bad['plan'][0]['args'] = argv
            with self.subTest(argv=argv), self.assertRaises(ValueError):cfg.validate(bad)

    def test_bootstrap_job_count_missing_unknown_and_mixed_plan_profiles_refuse(self):
        candidate = self.bootstrap_candidate()
        for mode in ['empty','second-job','device-first','missing-profile','unknown','compile-first',
                     'grouped-profile','benchmark-result-profile','top-host-recipe','top-result-recipe']:
            bad = copy.deepcopy(candidate)
            if mode=='empty':bad['plan'] = []
            if mode=='second-job':bad['plan'].append(dict(api=0,label='reproducible-release',standalone=True,seconds=1800,
                args=['env','GRADLE_OPTS=-Dorg.gradle.workers.max=1','scripts/reproducible-release.sh']))
            if mode=='device-first':bad['plan'].insert(0,copy.deepcopy(self.config['plan'][0]))
            if mode=='missing-profile':del bad['plan_recipe']
            if mode=='unknown':bad['plan_recipe'] = 'arbitrary-bootstrap-v1'
            if mode=='compile-first':bad['plan_recipe'] = 'compile-then-scoped-app-v1'
            if mode=='grouped-profile':bad['plan_recipe'] = 'assemble-check-then-repro-v1'
            if mode=='benchmark-result-profile':bad['plan_recipe'] = 'benchmark-api-v1'
            if mode=='top-host-recipe':bad['host_recipe'] = 'assemble-check-then-repro-v1'
            if mode=='top-result-recipe':bad['result_recipe'] = {'profile':'benchmark-api-v1'}
            with self.subTest(mode=mode), self.assertRaises(ValueError):cfg.validate(bad)

    def test_bootstrap_preserves_pin_gate_carry_and_fresh_directory_guards(self):
        candidate = self.bootstrap_candidate()
        for change in [{'gate':'/other/gate'}, {'carried_reclaim_bytes':1}, {'reclaim_budget_bytes':cfg.MAX_BUDGET+1},
                       {'slots':2}, {'checked_head':'not-a-head'}]:
            with self.subTest(change=change), self.assertRaises(ValueError):cfg.validate(dict(candidate,**change))
        wrapper = str(PROJECT/'gradlew')
        for mode in ['missing-wrapper','changed-wrapper','changed-source','wrong-image']:
            bad = copy.deepcopy(candidate)
            if mode=='missing-wrapper':del bad['input_pins'][wrapper]
            if mode=='changed-wrapper':bad['input_pins'][wrapper] = '0'*64
            if mode=='changed-source':bad['input_pins'][str(PROJECT/'scripts/instrumented-tests.sh')] = '0'*64
            if mode=='wrong-image':bad['server_image'] = 'unreviewed:tag'
            with self.subTest(mode=mode), self.assertRaises(ValueError):cfg.validate(bad)
        Path(candidate['run_dir']).mkdir()
        with self.assertRaisesRegex(ValueError, 'fresh unused run directory'):cfg.validate(candidate)

    def test_conformance_rejects_guard_cleanup_sampler_and_reclaim_changes(self):
        other = self.directory/'package'
        shutil.copytree(PACKAGE, other, ignore=shutil.ignore_patterns('__pycache__'))
        for name, before, after in [
            ('runtime/controller.py', "safe=cleanup_result['success'] and rollback_rc==0", 'safe=rollback_rc==0'),
            ('runtime/controller.py', "if __name__ != '__main__' or '--execute' not in sys.argv:", 'if False:'),
            ('runtime/scope_completion.py', "kwargs['root']=Path(root)", "kwargs['root']=root"),
            ('runtime/local-reclaim-finite.py', 'swappiness=0', 'swappiness=1')]:
            path = other/name
            original = path.read_text()
            self.assertIn(before, original)
            path.write_text(original.replace(before, after, 1))
            with self.subTest(name=name, before=before), self.assertRaises(ValueError):
                conformance.verify(other)
            path.write_text(original)


class ActualLauncherTests(unittest.TestCase):
    def test_live_fixture_case_selection_and_real_capture_race_are_host_checked(self):
        source = (PACKAGE/'tests/live_controller_case.py').read_text()
        tree = ast.parse(source)
        supported = ast.literal_eval(next(n.value for n in tree.body if isinstance(n, ast.Assign)
                                         and any(isinstance(t, ast.Name) and t.id == 'allowed' for t in n.targets)))
        self.assertEqual(set(supported), {'success', 'nonzero', 'capture', 'unknown-disappearance'})
        # A normal unrelated sibling closing before a read is not the historical
        # missing-evidence race. Force actual disappearance after enumeration.
        with tempfile.TemporaryDirectory(dir=SCRATCH) as tmp:
            root = Path(tmp)/'tree'
            root.mkdir()
            zero = ''.join(k+' 0\n' for k in events.KEYS)
            for name, value in {'memory.events': zero, 'memory.events.local': zero, 'memory.current': '0',
                                'memory.high': 'max', 'memory.max': 'max', 'memory.pressure': 'full avg10=0'}.items():
                (root/name).write_text(value)
            actual = Path.stat
            def gone(path, *args, **kwargs):
                if path == root:
                    raise FileNotFoundError('actual cgroup read fixture')
                return actual(path, *args, **kwargs)
            with patch.object(Path, 'stat', gone):
                result = events.capture(root)
            self.assertTrue(result['errors'])
            self.assertIn('FileNotFoundError', json.dumps(result['errors']))

    def test_actual_boot_function_argv_and_holder_placement(self):
        source = (PROJECT/'scripts/instrumented-tests.sh').read_text()
        start = source.index('boot_emulator() {\n')
        function = source[start:source.index('\n}\n', start)+3]
        # API31 here is only a host argv/parser fixture; no API31 device work.
        for slots, level in [(1, 36), (2, 36), (1, 31), (2, 31)]:
            with self.subTest(slots=slots, level=level), tempfile.TemporaryDirectory(dir=SCRATCH) as tmp:
                root = Path(tmp)
                script = r'''
set -euo pipefail
work_dir=$1
SLOTS=$2
fixture_level=$3
AVD=fixture-avd
EMULATOR_MEMORY_MAX=6G
emulator_pid=''
emulator_unit=''
boot_lock=$work_dir/boot.lock
exec {boot_fd}>"$boot_lock"
exec {slot_fd}>"$work_dir/slot.lock"
exec {device_fd}>"$work_dir/device.lock"
wait_for_memory() { :; }
owned_serials() { :; }
random_hex() { printf abcdef12; }
avd_api_level() { printf '%s' "$fixture_level"; }
describe_run() { :; }
read_device() { device_avd=$AVD; }
emulator() { return 99; }
systemctl() { if [[ $2 == whoami ]]; then printf fixture-main.scope; fi; }
systemd-run() { printf '%s\0' "$@" >"$work_dir/argv"; }
adb() {
 if [[ $1 == devices ]]; then return 0; fi
 if [[ ${*: -1} == sys.boot_completed ]]; then printf 1; fi
 if [[ ${*: -1} == ro.build.version.sdk ]]; then printf '%s' "$fixture_level"; fi
}
''' + function + '\nboot_emulator\nwait "$emulator_pid"\n'
                result = subprocess.run(['bash', '-c', script, 'host-fixture', str(root), str(slots), str(level)], capture_output=True, text=True, timeout=5)
                self.assertEqual(result.returncode, 0, result.stderr)
                args = (root/'argv').read_bytes().decode().split('\0')[:-1]
                wrapped = sc.wrap_emulator(args, {}, '/session')
                holder = wrapped.index(sys.executable)
                emulator = wrapped.index('emulator')
                self.assertEqual(wrapped[:holder]+wrapped[emulator:], args)
                self.assertLess(wrapped.index('ANDROID_TMP='+str(root/'emulator-tmp')), holder)
                self.assertIn('MemoryMax=6G', wrapped)
                self.assertIn('MemorySwapMax=0', wrapped)
                self.assertEqual('-read-only' in wrapped, slots > 1)
                self.assertEqual(args[args.index('-gpu')+1], 'lavapipe' if level == 36 else 'swiftshader_indirect')
                for bad in ['LD_PRELOAD=/wrong', 'ANDROID_TMP=/other', '-i']:
                    changed = args.copy()
                    changed.insert(changed.index('emulator'), bad)
                    with self.assertRaises(sc.EvidenceError):
                        sc.wrap_emulator(changed, {}, '/session')


class ActualControllerCleanupTests(unittest.TestCase):
    def test_actual_controller_sampler_normalizes_string_roots_and_preserves_missing_evidence(self):
        tree = ast.parse((PACKAGE/'runtime/controller.py').read_text())
        function = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'run_workload')
        monitor = next(n for n in ast.walk(function) if isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id == 'Monitor')
        expression = next(k.value for k in monitor.keywords if k.arg == 'sampler')
        with tempfile.TemporaryDirectory(dir=SCRATCH) as tmp:
            base = Path(tmp)
            root, app, owned = base/'tree', base/'tree/app', base/'tree/app/owned.scope'
            zero = ''.join(key+' 0\n' for key in ['low', 'high', 'max', 'oom', 'oom_kill', 'oom_group_kill'])
            for path in [root, app, owned]:
                path.mkdir(parents=True, exist_ok=True)
                for key, value in {'memory.events': zero, 'memory.events.local': zero, 'memory.current': '0',
                                   'memory.high': 'max', 'memory.max': 'max',
                                   'memory.pressure': 'some avg10=0 total=0\nfull avg10=0 total=0\n'}.items():
                    (path/key).write_text(value)
            session = sc.Session(base/'protocol', app, 'owned.scope', 2, events.capture)
            sampler = eval(compile(ast.Expression(expression), '<actual-controller-sampler>', 'eval'), {'completion': session})
            view = sampler(str(root), time.monotonic()+1)
            self.assertFalse(view['errors'])
            self.assertIn(str(owned), view['rows'])
            missing = sampler(str(root/'absent'), time.monotonic()+1)
            self.assertTrue(missing['errors'])
            self.assertIn('FileNotFoundError', json.dumps(missing['errors']))
            result = Monitor(view, app, owned).inspect(missing, host_available=10*1024**3)
            self.assertIsNotNone(result['abort'])

    def test_actual_finally_keeps_protection_on_unknown_cleanup_or_rollback(self):
        source = (PACKAGE/'runtime/controller.py').read_text()
        tree = ast.parse(source)
        outer = next(n for n in tree.body if isinstance(n, ast.Try) and n.finalbody)
        # Execute precisely the maintained outer control, not a reimplementation.
        code = compile(ast.Module(body=[outer], type_ignores=[]), '<actual-controller-control>', 'exec')
        for case in ['success', 'workload-error', 'cleanup-error', 'rollback-error', 'readback-error', 'watch-cancel-error', 'fallback-cancel-error']:
            with self.subTest(case=case), tempfile.TemporaryDirectory(dir=SCRATCH) as tmp:
                directory = Path(tmp)
                events = []
                limits = {'value': {'memory.high': 'original'}}
                state = {'prepared_labels': ['full36']}
                ns = {'base': directory, 'state_path': directory/'state.json', 'config': {'checked_head': '0'*40},
                      'state': state, 'original': dict(limits['value']), 'temporary': {'memory.high': 'temporary'},
                      'unit': 'fixture', 'bounds': {'fallback_seconds': 60}, 'armed': False, 'watchdog_armed': False,
                      '_controller_error': None, 'result': 1, 'json': json, 'time': time, 'sys': sys,
                      'plan': [(36, 'full36', [], 5400, False)], 'service_command': watchdog.service_command}
                def save():
                    ns['state_path'].write_text(json.dumps(ns['state']))
                def command(argv, name, refresh=False):
                    events.append(name)
                    if name == 'apply-window':
                        limits['value'] = dict(ns['temporary'])
                    if name == 'immediate-rollback':
                        if case == 'rollback-error':
                            raise OSError('unknown rollback')
                        limits['value'] = dict(ns['original'])
                    if name == 'cancel-watchdog' and case == 'watch-cancel-error':
                        raise OSError('watch cancellation')
                    save()
                    return 1 if name == 'cancel-fallback' and case == 'fallback-cancel-error' else 0
                def read_limits():
                    if case == 'readback-error' and 'immediate-rollback' in events:
                        raise OSError('missing limits')
                    return dict(limits['value'])
                def workload(*args):
                    if case == 'workload-error':
                        raise RuntimeError('actual workload boundary fixture')
                    return True, True
                def cleanup(error=None):
                    events.append('owned-cleanup')
                    return {'success': case != 'cleanup-error'}
                ns.update(save=save, command=command, limits=read_limits,
                          conditions=lambda: {'limits': read_limits(), 'baseline_pass': True, 'host_pass': True},
                          run_workload=workload, cleanup_active_workload=cleanup,
                          settle_active_workload=lambda *a: True, prepare=lambda *a: True)
                exec(code, ns)
                self.assertLess(events.index('owned-cleanup'), events.index('immediate-rollback'))
                if case in ['cleanup-error', 'rollback-error', 'readback-error']:
                    self.assertNotIn('cancel-watchdog', events)
                    self.assertNotIn('cancel-fallback', events)
                    self.assertTrue(ns['state']['manual_recovery_required'])
                if case == 'watch-cancel-error':
                    self.assertNotIn('cancel-fallback', events)
                if case == 'success':
                    self.assertEqual(ns['result'], 0)
                    self.assertLess(events.index('immediate-rollback'), events.index('cancel-watchdog'))
                    self.assertLess(events.index('cancel-watchdog'), events.index('cancel-fallback'))
                else:
                    self.assertEqual(ns['result'], 1)

    def test_installed_cleanup_context_does_not_certify_unknown_evidence(self):
        # The actual cleanup installer must retain the active context until its
        # observers prove closure. No signaling or external operations are used.
        namespace = {'Session': sc.Session, 'subprocess': subprocess, 'runtime': SCRATCH, 'state': {}}
        install_controller(namespace)
        self.assertTrue(namespace['cleanup_active_workload']()['success'])
        self.assertFalse(namespace['cleanup_active_workload']()['certificate_created_by_cleanup'])
        class Unknown:
            proc = None
            def observe(self): pass
            def cleanup(self, *args): return {'success': False, 'remaining': 'unknown'}
        namespace['_active_cleanup'] = Unknown()
        self.assertFalse(namespace['settle_active_workload'](False, False))


class WatchdogAndDeadlineTests(unittest.TestCase):
    def test_real_watchdog_loop_has_finite_lifetime_and_original_cadence(self):
        now = [0.0]
        calls = []
        def sleep(seconds): now[0] += seconds
        code = watchdog.run('/fixture/state.json', 130, clock=lambda: now[0], sleep=sleep,
                            invoke=lambda remaining: calls.append((now[0], remaining)) or 0, report=lambda r: None)
        self.assertEqual(calls, [(60.0, 70.0), (120.0, 10.0)])
        self.assertEqual(code, 124)
        command = watchdog.service_command('fixture', {'fallback_seconds': 300}, Path('/fixture/state.json'))
        self.assertIn('--property=RuntimeMaxSec=420s', command)
        self.assertIn('--property=Type=exec', command)

    def test_deadlines_include_admission_cleanup_and_observed_partial_lower_bound(self):
        self.assertEqual(full_suite_seconds(2400, 2700, 300), 4500)
        bounds = window_bounds([(36, 'full36', [], 5400, False)])
        self.assertGreater(bounds['fallback_seconds'], 5400+300+45)
        for bad in [0, float('inf'), -1, True]:
            with self.assertRaises(ValueError):
                full_suite_seconds(bad, 2700, 300)


if __name__ == '__main__':
    unittest.main(verbosity=2)
