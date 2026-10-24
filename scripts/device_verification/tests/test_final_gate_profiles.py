"""Pure disk/model fixtures for exact routes; no SDK, payload or resource work."""
from pathlib import Path
import copy
import hashlib
import importlib
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zlib

from test_candidate import PACKAGE, SCRATCH
from scoped_controller_fixture import WorkloadFixture, DEVICE_BUILD
import configuration
from runtime.configuration import validate
import final_gate_profiles as fg
import scoped_results as sr

PROJECT = PACKAGE.parent.parent


def tiny_png():
    def chunk(kind, data):
        return struct.pack('>I', len(data))+kind+data+struct.pack('>I', zlib.crc32(kind+data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 6, 0, 0, 0))+chunk(b'IDAT', zlib.compress(b'\0\0\0\0\xff'))+chunk(b'IEND', b'')


class FinalFixture(WorkloadFixture):
    """Reuse the actual maintained controller/Session fixture, never its payload."""
    def __init__(self, root, profile=fg.SCREENSHOT, phase='capture', method=None, api=None, scenario='valid'):
        super().__init__(root)
        self.scenario = scenario
        names = [r['source'] for r in fg.source_table()['sources']]
        names += fg.COMMON+['macrobenchmark/build.gradle.kts']+[r['source'] for r in fg.NATIVE_SOURCES+fg.CATALOG_SOURCES]
        names += [sr.BENCHMARK_SOURCE+n for n in sr.BENCHMARK_SOURCES]
        for relative in set(names):
            dest = self.project/relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes((PROJECT/relative).read_bytes())
        assets = self.project/fg.REFERENCE_ROOT
        shutil.copytree(PROJECT/fg.REFERENCE_ROOT, assets)
        if (profile == fg.SCREENSHOT and phase == 'compare') or (profile == fg.FULL and api != 31):
            for name in fg.screen_cases().values():
                path = assets/name
                if not path.exists():
                    path.write_bytes(tiny_png())
        for relative in sr.APK_PATHS:
            dest = self.project/relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(b'SYNTHETIC APK '+relative.encode())
        build = copy.deepcopy(DEVICE_BUILD)
        build['version']['sdk'] = api if api is not None else (31 if profile in (fg.WIDGET, fg.BENCHMARK) else 36)
        build['fingerprint'] = 'HOST-MODEL-ONLY/API'+str(build['version']['sdk'])
        self.recipe = fg.declare(self.project, profile, build, phase=phase if profile == fg.SCREENSHOT else None,
                                 method=method if profile == fg.NATIVE else None, api=api)
        label, args, seconds = fg.job_spec(self.recipe)
        job = dict(api=self.recipe['api'], label=label, args=args, seconds=seconds, standalone=False,
                   result_recipe=self.recipe)
        self.configuration['plan'] = [job]
        self.configuration['stage'] = 'compatibility' if job['api'] == 31 else 'development'
        pins = self.configuration['input_pins']
        for path in list(pins):
            pins[path] = sr.sha(path)
        for row in self.recipe['sources']+self.recipe.get('references', [])+self.recipe.get('screenshots', {}).get('references', [])+self.recipe.get('apks', []):
            pins[str(self.project/row['source'])] = row['sha256']
        (self.base/'run-config.json').write_text(json.dumps(self.configuration))
        self.checked = validate(self.configuration, fresh=False)
        self.ns['config'] = self.checked
        self.job = [job['api'], label, list(args), seconds, False]
        self.info['expected_app_cases'] = self.recipe['cases']
        self.info['classes'] = sorted({c for c, m in self.recipe['cases']})
        self.benchmark_api = profile in (fg.BENCHMARK, fg.FULL)
        self.rc = 1 if profile == fg.SCREENSHOT and phase == 'capture' else 0

    def benchmark_results(self):
        super().benchmark_results()
        path = self.project/'macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/fixture-benchmarkData.json'
        data = json.loads(path.read_text())
        data['context']['build'] = copy.deepcopy(self.recipe['device_build'])
        if self.scenario == 'last-stop-missing':
            data['benchmarks'][1]['metrics']['TimerStopFirstMs']['runs'].pop()
        if self.scenario == 'wrong-build':
            data['context']['build']['fingerprint'] = 'wrong'
        path.write_text(json.dumps(data))

    def results(self):
        super().results()
        if self.recipe['profile'] != fg.SCREENSHOT or self.recipe['phase'] != 'capture':
            return
        path = self.project/'app/build/outputs/androidTest-results/connected/debug/TEST-app.xml'
        tree = ET.parse(path)
        suite = tree.getroot()
        failures = 0
        for case in suite.findall('testcase'):
            identity = (case.get('classname'), case.get('name'))
            if identity not in fg.screen_cases():
                continue
            if identity[1] not in fg.PENDING_METHODS:
                continue
            failures += 1
            name = fg.screen_cases()[identity]
            ET.SubElement(case, 'failure', type='java.lang.AssertionError',
                          message='no reference image screenshots/'+name+' in the test assets. This capture is in /owned/'+name)
            dest = self.project/fg.CAPTURE_ROOT/'connected/fixture/screenshots/actual'/name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(tiny_png())
        if self.scenario in ('changed-old-frame', 'changed-old-diff'):
            case = next(c for c in suite.findall('testcase') if c.get('name') not in fg.PENDING_METHODS)
            name = fg.screen_cases()[(case.get('classname'), case.get('name'))]
            if self.scenario == 'changed-old-diff':
                message = name[:-4]+' differs from its reference in 21 pixels, more than 20, within x 0..1, y 0..1 of 1x1. The capture is in /owned/'+name+' and the diff in /owned/diff/'+name+'.'
            else:
                message = name[:-4]+' is 1x1, but its reference is 2x2. Record the references on tokkl-api36. This capture is in /owned/'+name+'.'
            ET.SubElement(case, 'failure', type='java.lang.AssertionError', message=message)
            failures += 1
            for kind in (('actual', 'diff') if self.scenario == 'changed-old-diff' else ('actual',)):
                dest = self.project/fg.CAPTURE_ROOT/('connected/fixture/screenshots/'+kind)/name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(tiny_png())
        suite.set('failures', str(failures))
        tree.write(path)
        captures = sorted((self.project/fg.CAPTURE_ROOT).rglob('*.png'))
        if self.scenario == 'missing-actual':
            captures[0].unlink()
        if self.scenario == 'stale-capture':
            os.utime(captures[0], (1, 1))
        if self.scenario == 'bad-png':
            captures[0].write_bytes(b'invalid')
        if self.scenario == 'symlink-capture':
            original = captures[0]
            target = self.root/'outside.png'
            original.replace(target)
            original.symlink_to(target)
        if self.scenario == 'duplicate-capture':
            source = captures[0]
            dest = self.project/fg.CAPTURE_ROOT/'connected/other/screenshots/actual'/source.name
            dest.parent.mkdir(parents=True)
            dest.write_bytes(source.read_bytes())
        if self.scenario == 'unexpected-exception':
            case = suite.find('testcase')
            ET.SubElement(case, 'error', type='java.lang.IllegalStateException')
            suite.set('errors', '1')
            tree.write(path)

    def launch(self, cmd, stdout, stderr, cwd, env):
        value = super().launch(cmd, stdout, stderr, cwd, env)
        stdout.flush()
        text = Path(stdout.name).read_text()
        api = self.job[0]
        if api == 31:
            text = text.replace('tokkl-api36 on API 36', 'tokkl-api31 on API 31')
        if self.recipe['profile'] in (fg.SCREENSHOT, fg.NATIVE, fg.WIDGET) and self.scenario != 'metadata-missing':
            row = dict(serial='emulator-5554', avd='tokkl-api'+str(api),
                       observed_at_ns=time.time_ns(), build=copy.deepcopy(self.recipe['device_build']))
            if self.scenario == 'metadata-stale':
                row['observed_at_ns'] = 1
            if self.scenario == 'metadata-future':
                row['observed_at_ns'] += 10**12
            if self.scenario == 'metadata-serial':
                row['serial'] = 'emulator-5556'
            if self.scenario == 'metadata-avd':
                row['avd'] = 'foreign-avd'
            if self.scenario == 'metadata-api':
                row['build']['version']['sdk'] = 31 if api == 36 else 36
            if self.scenario == 'metadata-build':
                row['build']['fingerprint'] = 'WRONG-HOST-MODEL'
            line = 'TOKKL_DEVICE_BUILD '+json.dumps(row, sort_keys=True)+'\n'
            text += line
            if self.scenario == 'metadata-duplicate':
                text += line
        if self.recipe['profile'] == fg.NATIVE and self.scenario != 'missing-reset':
            text += 'Reset notification permission before instrumentation on emulator-5554\n'
            if self.scenario == 'duplicate-reset':
                text += 'Reset notification permission before instrumentation on emulator-5554\n'
            if self.scenario == 'foreign-reset':
                text = text.replace('instrumentation on emulator-5554', 'instrumentation on emulator-5556')
        stdout.seek(0)
        stdout.truncate()
        stdout.write(text)
        stdout.flush()
        return value


class FinalGateProfilesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.root = Path(self.temp.name)
        self.number = 0

    def tearDown(self):
        self.temp.cleanup()

    def fixture(self, **kw):
        self.number += 1
        return FinalFixture(self.root/str(self.number), **kw)

    def contract(self, fixture):
        return fixture.ns['state']['tests'][0]['result_contract']

    def test_source_table_covers_all_sources_inheritance_sdk_and_parameters(self):
        for api, count in [(31, 697), (36, 806)]:
            inventory = fg.full_inventory(PROJECT, api)
            self.assertEqual(len(inventory['cases']), count)
            self.assertEqual(sum(c.startswith(fg.SCREEN_PACKAGE) for c, m in inventory['cases']), 0 if api == 31 else 100)
            self.assertEqual(sum(c.endswith('PomodoroDefaultsUiTest') for c, m in inventory['cases']), 12)
        cases = fg.full_inventory(PROJECT, 36)['cases']
        self.assertIn(['com.github.e0ipso.tokkl.settings.PomodoroDefaultsUiTest',
                       'eachLabelledDefaultRefusesInvalidInputAndAcceptsBothBounds[font=1, display=false, dark=false]'], cases)
        table = fg.source_table()
        self.assertEqual(len(table['sources']), 162)
        self.assertEqual(len([c for c in table['classes'] if c['inherited_from']]), 19)
        self.assertTrue(all('NativeNotificationPermissionTest' not in c['class'] for c in table['classes']))
        self.assertEqual(len(fg.screen_cases()), 100)
        self.assertEqual(len(set(fg.screen_cases().values())), 100)

    def test_capture_has_exact_68_references_and_32_pending_and_never_passes(self):
        f = self.fixture()
        self.assertEqual(len(f.recipe['references']), 68)
        self.assertEqual(len(f.recipe['pending']), 32)
        self.assertEqual(f.run(), (False, False))
        rec = f.ns['state']['tests'][0]
        result = self.contract(f)
        self.assertEqual(rec['exit'], 1)
        self.assertEqual(rec['counts'], dict(tests=100, failures=32, errors=0, skipped=0))
        self.assertTrue(result['capture_complete'], result)
        self.assertFalse(result['passed'])
        self.assertEqual(len(result['captures']['files']), 32)
        self.assertEqual(len(result['failures']), 32)
        self.assertIn('failed app-only payload', result['reason'])
        self.assertEqual(result['actual_device']['build'], f.recipe['device_build'])
        self.assertEqual(len(json.loads((f.session.path/'completed.json').read_text())['observations']), 2)
        for row in result['captures']['files']:
            self.assertEqual(hashlib.sha256(Path(row['path']).read_bytes()).hexdigest(), row['sha256'])

    def test_compare_requires_exact_100_names_and_matching_actual_build_metadata(self):
        f = self.fixture(phase='compare')
        self.assertEqual(len(f.recipe['references']), 100)
        self.assertEqual(f.recipe['pending'], [])
        self.assertEqual(f.run(), (True, True))
        result = self.contract(f)
        self.assertTrue(result['capture_complete'], result)
        self.assertEqual(result['app_counts']['tests'], 100)
        self.assertEqual(result['captures']['files'], [])
        self.assertTrue(result['passed'])
        self.assertEqual(result['comparison'], 'passed')
        self.assertEqual(result['actual_device']['build'], f.recipe['device_build'])
        self.assertFalse(result['full_app_gate'])

    def test_reference_phase_membership_and_source_change_refuse(self):
        f = self.fixture()
        with self.assertRaisesRegex(ValueError, 'reference names'):
            fg.references(f.project, 'compare')
        ref = next((f.project/fg.REFERENCE_ROOT).glob('*.png'))
        ref.rename(ref.with_name('wrong.png'))
        with self.assertRaisesRegex(ValueError, 'reference names'):
            fg.references(f.project, 'capture')
        f = self.fixture()
        path = f.project/fg.SCREEN_SOURCE/'ScreenshotTest.kt'
        path.write_text(path.read_text()+'\n// changed\n')
        with self.assertRaisesRegex(ValueError, 'source changed'):
            fg.declare(f.project, fg.SCREENSHOT, DEVICE_BUILD, phase='capture')

    def test_stale_missing_duplicate_symlink_corrupt_captures_refuse_without_erasing_failures(self):
        for scenario in ['missing-actual', 'stale-capture', 'bad-png', 'symlink-capture', 'duplicate-capture', 'unexpected-exception']:
            with self.subTest(scenario=scenario):
                f = self.fixture(scenario=scenario)
                self.assertEqual(f.run(), (False, False))
                result = self.contract(f)
                self.assertFalse(result['capture_complete'], result)
                self.assertEqual(f.ns['state']['tests'][0]['exit'], 1)
                self.assertIsNotNone(result['reason'])

    def test_capture_receipt_hash_and_runtime_mutation_refuse(self):
        for mutation in ['copy', 'origin', 'runtime']:
            f = self.fixture()
            original = fg.retain_captures
            def corrupt(*args):
                receipt = original(*args)
                if mutation == 'runtime':
                    (f.base/'final_gate_profiles.py').write_text('changed')
                else:
                    row = receipt['files'][0]
                    Path(row['path' if mutation == 'copy' else 'original']).write_bytes(tiny_png()+b'changed')
                return receipt
            with self.subTest(mutation=mutation), patch.object(fg, 'retain_captures', side_effect=corrupt):
                self.assertEqual(f.run(), (False, False))
                self.assertFalse(self.contract(f)['capture_complete'])

    def test_native_four_methods_are_four_separate_exact_jobs_with_explicit_reset(self):
        for case in fg.NATIVE_CASES:
            with self.subTest(case=case):
                f = self.fixture(profile=fg.NATIVE, method='#'.join(case))
                self.assertEqual(len(f.checked['plan']), 1)
                self.assertEqual(fg.reset_flag(f.checked, f.job[1]), '1')
                self.assertEqual(f.run(), (True, True))
                result = self.contract(f)
                self.assertEqual(result['app_counts'], dict(tests=1, failures=0, errors=0, skipped=0))
                self.assertEqual(result['app_cases'], [list(case)])
                self.assertEqual(result['actual_device']['build'], f.recipe['device_build'])
                self.assertTrue(result['passed'])
                self.assertEqual(len(json.loads((f.session.path/'completed.json').read_text())['retired_scopes']), 2)

    def test_native_reset_missing_duplicate_foreign_or_method_list_refuses(self):
        method = '#'.join(fg.NATIVE_CASES[0])
        for scenario in ['missing-reset', 'duplicate-reset', 'foreign-reset']:
            with self.subTest(scenario=scenario):
                f = self.fixture(profile=fg.NATIVE, method=method, scenario=scenario)
                self.assertEqual(f.run(), (False, False))
                self.assertIn('reset', self.contract(f)['reason'])
        f = self.fixture(profile=fg.NATIVE, method=method)
        for selector in [method+','+'#'.join(fg.NATIVE_CASES[1]), fg.NATIVE_CASES[0][0]]:
            with self.assertRaisesRegex(ValueError, 'one of four'):
                fg.declare(f.project, fg.NATIVE, DEVICE_BUILD, method=selector)

    def test_optional_inclusion_source_and_native_api_are_exact(self):
        f = self.fixture(profile=fg.NATIVE, method='#'.join(fg.NATIVE_CASES[0]))
        path = f.project/fg.NATIVE_SOURCES[0]['source']
        path.write_text(path.read_text()+'\n// different\n')
        with self.assertRaisesRegex(ValueError, 'source changed'):
            fg.validate_recipe(f.project, f.configuration['plan'][0], f.configuration['input_pins'])
        with self.assertRaisesRegex(ValueError, 'profile/API'):
            fg.declare(f.project, fg.NATIVE, DEVICE_BUILD, method='#'.join(fg.NATIVE_CASES[0]), api=31)

    def test_api31_widget_preserves_exact_original_clock_source_and_single_identity(self):
        f = self.fixture(profile=fg.WIDGET)
        self.assertEqual(f.recipe['cases'], [list(fg.WIDGET_CASE)])
        self.assertEqual(f.checked['stage'], 'compatibility')
        self.assertEqual(f.run(), (True, True))
        self.assertEqual(self.contract(f)['actual_device']['build'], f.recipe['device_build'])
        self.assertEqual(self.contract(f)['actual_device']['avd'], 'tokkl-api31')

    def test_app_only_routes_refuse_missing_stale_duplicate_or_mismatched_actual_metadata(self):
        scenarios = {'missing': 'missing/duplicate', 'duplicate': 'missing/duplicate',
                     'stale': 'stale/future', 'future': 'stale/future',
                     'serial': 'serial/AVD', 'avd': 'serial/AVD',
                     'api': 'complete reviewed API', 'build': 'differs from reviewed'}
        for profile in (fg.SCREENSHOT, fg.NATIVE, fg.WIDGET):
            for scenario, reason in scenarios.items():
                with self.subTest(profile=profile, scenario=scenario):
                    f = self.fixture(profile=profile, phase='compare', method='#'.join(fg.NATIVE_CASES[0]),
                                     scenario='metadata-'+scenario)
                    self.assertEqual(f.run(), (False, False))
                    result = self.contract(f)
                    self.assertFalse(result['passed'])
                    self.assertIn(reason, result['reason'])
                    self.assertEqual(result['app_counts']['failures'], 0)
                    self.assertEqual(result['app_counts']['tests'], 100 if profile == fg.SCREENSHOT else 1)

    def test_actual_metadata_parser_rejects_malformed_unbounded_and_incomplete_records(self):
        row = dict(serial='emulator-5554', avd='tokkl-api36', observed_at_ns=2*10**9,
                   build=copy.deepcopy(DEVICE_BUILD))
        valid = json.dumps(row)
        for payload in ['{', valid.replace('"serial":', '"serial":"duplicate","serial":'),
                        valid.replace('"observed_at_ns": 2000000000', '"observed_at_ns": true'),
                        json.dumps(dict(row, extra='unknown')), json.dumps(dict(row, build={})),
                        valid+' '*16384]:
            with self.subTest(payload=payload[:100]), self.assertRaises(ValueError):
                fg.app_device_build('TOKKL_DEVICE_BUILD '+payload, row['serial'], 36, DEVICE_BUILD, 1, 3*10**9)
        self.assertEqual(fg.app_device_build('TOKKL_DEVICE_BUILD '+valid, row['serial'], 36,
                                            DEVICE_BUILD, 1, 3*10**9), row)

    def test_clean_app_counts_cannot_override_nonzero_payload(self):
        for profile in (fg.SCREENSHOT, fg.NATIVE, fg.WIDGET):
            with self.subTest(profile=profile):
                f = self.fixture(profile=profile, phase='compare', method='#'.join(fg.NATIVE_CASES[0]))
                f.rc = 7
                self.assertEqual(f.run(), (False, False))
                result = self.contract(f)
                self.assertEqual(result['app_counts']['failures'], 0)
                self.assertEqual(f.ns['state']['tests'][0]['exit'], 7)
                self.assertIn('failed app-only payload', result['reason'])

    def test_capture_missing_metadata_retains_original_failures_and_files(self):
        f = self.fixture(scenario='metadata-missing')
        self.assertEqual(f.run(), (False, False))
        result = self.contract(f)
        self.assertTrue(result['capture_complete'])
        self.assertEqual(result['app_counts'], dict(tests=100, failures=32, errors=0, skipped=0))
        self.assertEqual(len(result['captures']['files']), 32)
        self.assertEqual(len(result['failures']), 32)
        self.assertIn('missing/duplicate', result['reason'])

    def test_compatibility_only_admits_exact_profiles_and_development_stays_api36(self):
        f = self.fixture(profile=fg.WIDGET)
        for kind in ['development31', 'legacy31', 'unknown-stage', 'bad-argv', 'bad-deadline', 'bad-build']:
            cfg = copy.deepcopy(f.configuration)
            if kind == 'development31':
                cfg['stage'] = 'development'
            elif kind == 'legacy31':
                cfg['plan'][0]['result_recipe']['profile'] = sr.PROFILE
            elif kind == 'unknown-stage':
                cfg['stage'] = 'other'
            elif kind == 'bad-argv':
                cfg['plan'][0]['args'].append('--continue')
            elif kind == 'bad-deadline':
                cfg['plan'][0]['seconds'] = 1801
            else:
                cfg['plan'][0]['result_recipe']['device_build']['version']['sdk'] = 36
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                validate(cfg, fresh=False)
        with self.assertRaises((ValueError, FileNotFoundError)):
            sr.inventory(f.project, fg.SCREEN_PACKAGE+'LightScreenshotTest')
        with self.assertRaises(ValueError):
            sr.declare(f.project, ['#'.join(fg.WIDGET_CASE)])

    def test_api31_benchmark_diagnostic_reuses_strict_75_samples_45_traces(self):
        f = self.fixture(profile=fg.BENCHMARK)
        self.assertEqual(f.run(), (True, True))
        result = self.contract(f)
        self.assertEqual(result['app_counts']['tests'], 10)
        self.assertEqual(result['benchmark_counts']['tests'], 2)
        self.assertEqual(result['metric_values'], 75)
        self.assertEqual(len(result['traces']), 45)
        self.assertFalse(result['full_app_gate'])
        foreign = f.project/'app/src/androidTest/java/com/github/e0ipso/tokkl/api/ForeignTest.kt'
        foreign.write_text('package com.github.e0ipso.tokkl.api\nclass ForeignTest {}\n')
        with self.assertRaisesRegex(ValueError, 'package membership'):
            fg.declare(f.project, fg.BENCHMARK, f.recipe['device_build'])

    def test_full_exact_per_api_cases_and_only_two_production_benchmarks(self):
        for api, count in [(31, 697), (36, 806)]:
            with self.subTest(api=api):
                f = self.fixture(profile=fg.FULL, api=api)
                self.assertEqual(f.job[2], fg.FULL_ARGS)
                self.assertEqual(f.run(), (True, True))
                result = self.contract(f)
                self.assertEqual(result['app_counts']['tests'], count)
                self.assertEqual(result['benchmark_counts']['tests'], 2)
                self.assertEqual(result['metric_values'], 75)
                self.assertEqual(len(result['traces']), 45)
                self.assertEqual(result['comparison'], 'not performed')
                self.assertTrue(result['full_app_gate'])

    def test_exact_full_inventory_missing_replaced_duplicate_and_skip_refuse(self):
        for scenario in ['missing-case', 'wrong-case', 'duplicate', 'skipped', 'stale', 'wrong-sdk-log']:
            with self.subTest(scenario=scenario):
                f = self.fixture(profile=fg.FULL, api=31, scenario=scenario)
                self.assertEqual(f.run(), (False, False))
                self.assertFalse(self.contract(f)['passed'])

    def test_benchmark_last_stop_sample_and_wrong_device_build_stay_failed(self):
        for scenario in ['last-stop-missing', 'wrong-build']:
            with self.subTest(scenario=scenario):
                f = self.fixture(profile=fg.BENCHMARK, scenario=scenario)
                self.assertEqual(f.run(), (False, False))
                self.assertIsNotNone(self.contract(f)['reason'])

    def test_legacy_post_input_policy_and_count_budget_deadline_guards_remain(self):
        f = self.fixture(profile=fg.FULL, api=31)
        for change in ['count', 'budget', 'deadline', 'post-policy']:
            cfg = copy.deepcopy(f.configuration)
            if change == 'count':
                cfg['plan'].append(copy.deepcopy(cfg['plan'][0]))
                cfg['preparation_policy']['max_preparations'] = 1
            elif change == 'budget':
                cfg['carried_reclaim_bytes'] = cfg['reclaim_budget_bytes']+1
            elif change == 'deadline':
                cfg['plan'][0]['seconds'] = 5399
            else:
                cfg['post_input_policy'] = {'profile': 'scoped-gradle-index-observation-v1'}
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate(cfg, fresh=False)

    def test_session_final_capture_failures_remain_failures_for_new_profiles(self):
        for profile in [fg.SCREENSHOT, fg.WIDGET, fg.NATIVE]:
            kw = {'profile': profile, 'scenario': 'missing-final'}
            if profile == fg.NATIVE:
                kw['method'] = '#'.join(fg.NATIVE_CASES[0])
            with self.subTest(profile=profile):
                f = self.fixture(**kw)
                self.assertEqual(f.run(), (False, False))
                self.assertIn('outcome failed', self.contract(f)['reason'])

    def test_no_config_screenshot_audit_is_phase_exact_and_never_certifies_comparison(self):
        state = fg.screenshot_audit(PROJECT)
        self.assertEqual(state['phase'], 'capture')
        self.assertEqual(len(state['references']), 68)
        self.assertEqual(len(state['pending']), 32)
        self.assertFalse(state['comparison_certified'])
        f = self.fixture(phase='compare')
        self.assertEqual(fg.screenshot_audit(f.project)['phase'], 'compare')

    def test_old_frame_failures_keep_actual_and_optional_diff_without_predicting_count(self):
        for scenario, files in [('changed-old-frame', 33), ('changed-old-diff', 34)]:
            with self.subTest(scenario=scenario):
                f = self.fixture(scenario=scenario)
                self.assertEqual(f.run(), (False, False))
                result = self.contract(f)
                self.assertTrue(result['capture_complete'], result)
                self.assertEqual(result['app_counts']['failures'], 33)
                self.assertEqual(len(result['captures']['files']), files)

    def test_non_native_explicit_zero_and_execution_clears_inherited_flag(self):
        f = self.fixture(profile=fg.BENCHMARK)
        with patch.dict(os.environ, TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION='1'):
            self.assertEqual(f.run(), (True, True))
        self.assertIn('TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION=0', f.ns['state']['tests'][0]['command'])
        with patch.dict(os.environ, TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION='1'), \
                patch.object(sys, 'argv', ['controller.py', '--execute', '--adoption', str(f.root/'modeled-adoption')]), \
                patch.object(configuration, 'adopted_configuration', return_value=f.checked):
            configuration.execution_configuration(f.base/'controller.py')
            self.assertNotIn('TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION', os.environ)

    def test_package_and_frozen_import_modes_share_exact_inventory(self):
        code = """import importlib,sys
from pathlib import Path
sys.path[:0]=[sys.argv[1],str(Path(sys.argv[1])/'runtime')]
m=importlib.import_module(sys.argv[2])
assert len(m.screen_cases())==100
assert m.source_table()['schema_version']==1
"""
        for name in ['final_gate_profiles', 'runtime.final_gate_profiles']:
            result = subprocess.run([sys.executable, '-B', '-c', code, str(PACKAGE), name],
                                    capture_output=True, text=True, timeout=5)
            self.assertEqual(result.returncode, 0, result.stdout+result.stderr)

    def test_full_launcher_argument_parser_retains_exact_catalog_exclusion(self):
        source = (PROJECT/'scripts/instrumented-tests.sh').read_text()
        start = source.index('gradle_args=()')
        end = source.index('# A native permission probe runs alone', start)
        code = 'set -euo pipefail\nRUNNER_ARGS=android.testInstrumentationRunnerArguments\n'+source[start:end]
        code += 'printf "%s\\0" "$'+'{gradle_args[@]}"\n'
        result = subprocess.run(['bash', '-c', code, 'source-parser-fixture', *fg.FULL_ARGS[1:]],
                                capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(result.stdout.decode().rstrip('\0').split('\0'), fg.FULL_ARGS[1:])
        self.assertIn('"$'+'{gradle_args[@]}"', source[source.index('run_tests() {'):])

    def test_sdk_metadata_paths_bind_only_apis_in_the_plan(self):
        from audit_plan import sdk_metadata_paths
        for apis in [[31], [36], [31, 36], []]:
            config = {'environment': {'ANDROID_HOME': '/modeled/sdk', 'JAVA_HOME': '/modeled/jdk'},
                      'plan': [dict(api=api, standalone=False) for api in apis]
                              + [dict(api=0, standalone=True)]}
            paths = [str(p) for p in sdk_metadata_paths(config)]
            for api in (31, 36):
                self.assertEqual('/modeled/sdk/system-images/android-'+str(api)+'/default/x86_64/source.properties' in paths,
                                 api in apis)
                self.assertEqual(str(Path.home()/('.android/avd/tokkl-api'+str(api)+'.avd/config.ini')) in paths,
                                 api in apis)

    def test_capture_png_rejects_crc_and_compressed_corruption(self):
        valid = tiny_png()
        fg.png(valid)
        with self.assertRaises(ValueError):
            fg.png(valid[:-1])
        corrupted = bytearray(valid)
        start = valid.index(b'IDAT')
        length = struct.unpack('>I', valid[start-4:start])[0]
        corrupted[start+4:start+4+length] = b'x'*length
        corrupted[start+4+length:start+8+length] = struct.pack('>I', zlib.crc32(corrupted[start:start+4+length]) & 0xffffffff)
        with self.assertRaises((ValueError, zlib.error)):
            fg.png(bytes(corrupted))
