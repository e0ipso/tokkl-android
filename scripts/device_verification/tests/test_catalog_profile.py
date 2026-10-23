"""Synthetic contracts through adoption/capture and the public offline assessor."""
import copy
import json
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import catalog_profile as cat
from catalog_fixture import CatalogFixture, modeled_metadata, offline, write
from runtime.configuration import adopted_configuration, validate, sha
from test_candidate import SCRATCH


class CatalogTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.root = Path(self.temp.name); self.number = 0
        self.metadata = modeled_metadata(); self.metadata.__enter__()

    def tearDown(self):
        self.metadata.close()
        self.temp.cleanup()
        self.assertFalse(self.root.exists(), 'owned fixture cleanup')

    def fixture(self, **kwargs):
        self.number += 1
        return CatalogFixture(self.root/str(self.number), **kwargs)

    def test_exact_replay_and_seed_pass_actual_controller_session_raw_hook_only(self):
        seed = self.fixture(reverse_methods=True); self.assertEqual(seed.run(), (True, True))
        reference = cat.make_seed_reference(seed.base, seed.adoption)
        replay = self.fixture(revision=cat.REVISIONS[1], seed=False, accepted_seed=reference)
        self.assertEqual(replay.run(), (True, True))
        for f in [seed, replay]:
            with self.subTest(mode=f.parameters):
                r = f.ns['state']['tests'][0]['result_contract']
                self.assertTrue(r['operational_success']); self.assertTrue(r['product_tests_success'])
                self.assertTrue(r['raw_capture_complete']); self.assertFalse(r['comparative_valid'])
                self.assertFalse(r['evidence_complete']); self.assertEqual(r['quiet_host'], 'UNKNOWN')
                self.assertEqual((r['windows'], r['traces'], r['openings']), (40, 40, 22))
                self.assertEqual(r['app_counts'], dict(tests=10, failures=0, errors=0, skipped=0))
                self.assertEqual(r['benchmark_counts'], dict(tests=4, failures=0, errors=0, skipped=0))
                self.assertEqual(r['runtime_acceptance'], 'UNVERIFIED')
                self.assertEqual(f.identity_rows, {})
                self.assertTrue((f.session.path/'completed.json').exists())

    def test_canonical_negatives_refuse_actual_adopted_entry_before_any_payload(self):
        f = self.fixture()
        path = f.base/'run-config.json'; original = path.read_bytes()
        variants = []
        for key, value in [('label', 'other'), ('label', 'tap-scoped-catalog'), ('api', 31), ('standalone', True), ('seconds', 7199), ('seconds', 7201)]:
            c = copy.deepcopy(f.configuration); c['plan'][0][key] = value; variants.append((key+str(value), c))
        for mode in ['wrapper', 'package', 'class', 'notClass', 'testFile', 'output', 'extra-task', 'duplicate', 'unknown', 'seed-mix',
                     'revision', 'environment', 'nan-viewport', 'flat-viewport', 'runid', 'base', 'composite', 'tree', 'top-head', 'apk', 'build', 'missing-pin', 'tool', 'tool-context', 'tool-pin', 'seed-expectations', 'lazy-seed', 'seed-false']:
            c = copy.deepcopy(f.configuration); j = c['plan'][0]; r = j['result_recipe']
            if mode == 'wrapper': j['args'] = ['sh', '-c']+j['args']
            if mode == 'package': j['args'][2] += ',foreign'
            if mode in ['class', 'notClass', 'testFile']: j['args'].append(cat.ARG+mode+'=foreign')
            if mode == 'output': j['args'].append(cat.ARG+'additionalTestOutputDir=/foreign')
            if mode == 'extra-task': j['args'].append(':app:check')
            if mode == 'duplicate': j['args'].append(j['args'][3])
            if mode == 'unknown': j['args'].append(cat.ARG+'catalogUnknown=x')
            if mode == 'seed-mix': j['args'].append(cat.ARG+'catalogSeed=true')
            if mode in ['revision', 'environment', 'nan-viewport', 'flat-viewport', 'runid']:
                key, value = {'revision': ('catalogRevision', '0'*40), 'environment': ('catalogEnvironmentSha256', 'wrong'),
                    'nan-viewport': ('catalogTagsViewport', '0:0:NaN:800'), 'flat-viewport': ('catalogTagsViewport', '0:0:0:800'),
                    'runid': ('catalogRunId', '../unsafe')}[mode]
                if any(arg.startswith(cat.ARG+key+'=') for arg in j['args']):
                    j['args'] = [cat.ARG+key+'='+value if arg.startswith(cat.ARG+key+'=') else arg for arg in j['args']]
                else: j['args'].append(cat.ARG+key+'='+value)
            if mode == 'base': r['base_revision'] = cat.REVISIONS[1]
            if mode == 'composite': r['checked_head'] = '0'*40; c['checked_head'] = '0'*40
            if mode == 'tree': r['checked_tree'] = '0'*40
            if mode == 'top-head': c['checked_head'] = '0'*40
            if mode == 'apk': r['apks'][0]['sha256'] = '0'*64
            if mode == 'build': r['device_build']['version']['sdk'] = 31
            if mode == 'missing-pin': del c['input_pins'][str(f.project/r['sources'][0]['source'])]
            if mode == 'tool': r['tool_inputs']['java']['sha256'] = '0'*64
            if mode == 'tool-context': c['environment']['JAVA_HOME'] = '/foreign'
            if mode == 'tool-pin': del c['input_pins'][r['tool_inputs']['java']['path']]
            if mode == 'seed-expectations':
                j['args'] += [cat.ARG+'catalogEnvironmentSha256='+'0'*64, cat.ARG+'catalogTagsViewport=0:0:400:800', cat.ARG+'catalogTrashViewport=0:0:400:800']
            if mode == 'lazy-seed': j['args'] = [a.replace(cat.REVISIONS[0], cat.REVISIONS[1]) for a in j['args']]
            if mode == 'seed-false': j['args'] = [a.replace('catalogSeed=true', 'catalogSeed=false') for a in j['args']]
            variants.append((mode, c))
        for mode, changed in variants:
            with self.subTest(mode=mode):
                write(path, changed)
                with self.assertRaises(ValueError): validate(changed, fresh=False)
                with self.assertRaises(ValueError): adopted_configuration(f.base, f.adoption)
                self.assertEqual(f.ns['state']['tests'], [])
        path.write_bytes(original)
        duplicate = original.decode().replace('{', '{\"plan\":[],', 1)
        path.write_text(duplicate)
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            adopted_configuration(f.base, f.adoption)
        self.assertEqual(f.ns['state']['tests'], [])
        path.write_bytes(original)
        self.assertEqual(adopted_configuration(f.base, f.adoption), f.checked)

    def test_historical_overlay_server_and_source_hash_changes_refuse_before_adoption(self):
        f = self.fixture(seed=True)
        for relative in [next(iter(cat.OVERLAY)), next(iter(cat.VERIFICATION)), 'app/build.gradle.kts',
                         'app/src/main/AndroidManifest.xml', cat.scoped.APK_PATHS[0]]:
            p = f.project/relative; original = p.read_bytes()
            with self.subTest(path=relative):
                p.write_bytes(original+b'\nchanged')
                with self.assertRaises(ValueError): adopted_configuration(f.base, f.adoption)
                p.write_bytes(original)
        c = copy.deepcopy(f.configuration); c['plan'][0]['args'][-1] += 'foreign'
        with self.assertRaises(ValueError): validate(c, fresh=False)

    def raw_mutation(self, f, mode):
        folder = f.project/'macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark'
        paths = sorted(folder.glob('*-window-0.json')); p = paths[0]; w = json.loads(p.read_text())
        if mode.startswith('baseline-'):
            combined_path = next(p for p in folder.glob('synthetic-*-baseline.json') if p.name.count('-') == 2)
            combined = json.loads(combined_path.read_text())
            baseline_path = next(folder.glob('*-tags-baseline.json')); record = json.loads(baseline_path.read_text())
            if mode == 'baseline-missing': combined_path.unlink(); return
            if mode == 'baseline-overwrite': baseline_path.write_bytes(baseline_path.read_bytes()); return
            if mode == 'baseline-hash': record['environment_sha256'] = '0'*64
            if mode == 'baseline-serialization':
                record['environment_serialized'] = json.dumps(record['environment'], sort_keys=True, separators=(',', ':'))
                record['environment_sha256'] = cat.digest(record['environment_serialized'].encode())
            if mode == 'baseline-geometry':
                record['viewport'][2] += 1; record['viewport_serialized'] = cat.compact(record['viewport']); record['viewport_argument'] = '0.0:0.0:401.0:800.0'
            if mode == 'baseline-source-phase': record['source_phase'] = 'steady'
            if mode == 'baseline-foreign': record['run'] = 'foreign'
            combined['workloads']['tags'] = record
            write(baseline_path, record); write(combined_path, combined); return
        if mode == 'missing-window': p.unlink(); return
        if mode == 'duplicate-window': p.with_name('extra-window.json').write_bytes(p.read_bytes()); return
        if mode == 'stale': os.utime(p, (1, 1)); return
        if mode == 'malformed': p.write_text('{'); return
        if mode == 'duplicate-json': p.write_text(p.read_text().replace('"run":', '"run":"wrong","run":')); return
        if mode == 'escaped':
            raw = p.read_bytes(); p.unlink(); foreign = self.root/'foreign.json'; foreign.write_bytes(raw); p.symlink_to(foreign); return
        if mode == 'hard-link':
            raw = p.read_bytes(); p.unlink(); foreign = self.root/'foreign.json'; foreign.write_bytes(raw); os.link(foreign, p); return
        if mode == 'trace-bytes':
            traces = sorted(folder.glob('*.perfetto-trace')); traces[1].write_bytes(traces[0].read_bytes()); return
        if mode == 'opening': next(folder.glob('*-opening-0.json')).unlink(); return
        if mode == 'final-anchor':
            path = next(folder.glob('*-final-anchor.json')); value = json.loads(path.read_text()); value['anchors'][0]['index'] = 0; write(path, value); return
        if mode == 'fixture-origin':
            path = next(folder.glob('*-fixture.json')); value = json.loads(path.read_text()); del value['origin']; write(path, value); return
        if mode == 'fixture-order':
            path = next(folder.glob('*-fixture.json')); value = json.loads(path.read_text()); value['tags'].reverse(); write(path, value); return
        if mode == 'junit':
            path = f.project/'macrobenchmark/build/outputs/androidTest-results/connected/benchmark/TEST-bench.xml'
            value = ET.parse(path); ET.SubElement(value.getroot()[0], 'skipped'); value.getroot().set('skipped', '1'); value.write(path); return
        if mode == 'generation': w['generation'] = 999
        if mode == 'complete': w['complete'] = False
        if mode == 'unknown': w['end_unknown'] = True
        if mode == 'cursor': w['stream']['frame_from'] += 1
        if mode == 'callback-sequence': w['stream']['frames'][0]['seq'] += 1
        if mode == 'closure': w['stream']['listener_removed'] = not w['stream']['listener_removed']
        if mode == 'phase': w['end']['id'] = 'foreign'
        if mode == 'input': w['gestures'][0]['events'][0]['accepted'] = False
        if mode == 'delivery': w['stream']['touches'][0]['event_ms'] += 1
        if mode == 'geometry': w['after']['anchors'][0]['clipped_bottom'] = 9999
        if mode == 'environment': w['before']['environment']['ime_visible'] = True
        if mode == 'second-method-environment':
            p = f.raw_windows[('tags', 'steady', 0)]; w = json.loads(p.read_text()); w['before']['environment']['locale'] = 'foreign'
        if mode == 'viewport': w['before']['viewport'][2] += .1
        if mode == 'travel-claim': w['absolute_pixel_travel'] = 100
        if mode == 'cleanup':
            # Seed receipt is emitted only by one method.
            path = next(folder.glob('*-seed-receipt.json')); value = json.loads(path.read_text()); value['cleanup'] = 'UNKNOWN'; write(path, value); return
        write(p, w)

    def test_raw_negative_corpus_refuses_actual_controller_result_hook(self):
        modes = ['missing-window', 'duplicate-window', 'stale', 'malformed', 'duplicate-json', 'escaped', 'hard-link',
                 'trace-bytes', 'opening', 'final-anchor', 'fixture-origin', 'fixture-order', 'junit', 'generation',
                 'complete', 'unknown', 'cursor', 'callback-sequence', 'closure', 'phase', 'input', 'delivery',
                 'geometry', 'environment', 'viewport', 'travel-claim', 'cleanup', 'second-method-environment',
                 'baseline-missing', 'baseline-overwrite', 'baseline-hash', 'baseline-serialization', 'baseline-geometry', 'baseline-source-phase', 'baseline-foreign']
        for mode in modes:
            with self.subTest(mode=mode):
                f = self.fixture(); original = f.results
                def results():
                    original(); self.raw_mutation(f, mode)
                f.results = results
                self.assertFalse(f.run()[0])
                self.assertFalse(f.ns['state']['tests'][0]['result_contract']['passed'])

    def test_transport_bounds_duplicate_keys_hash_and_seed_replay_inventory(self):
        seed = self.fixture(); self.assertEqual(seed.run(), (True, True))
        f = self.fixture(revision=cat.REVISIONS[1], seed=False, accepted_seed=cat.make_seed_reference(seed.base, seed.adoption)); args = f.job[2]
        original_config = (f.base/'run-config.json').read_bytes()
        for mode in ['hash', 'base64', 'oversized', 'duplicate-keys', 'mixed', 'label-override', 'missing-environment', 'partial-viewports', 'eager-replay', 'foreign-seed']:
            changed = list(args)
            if mode == 'hash': changed[-1] = cat.ARG+'catalogFixtureSha256='+'0'*64
            if mode == 'base64': changed[-2] = cat.ARG+'catalogFixture=!!!!'
            if mode == 'oversized': changed[-2] = cat.ARG+'catalogFixture='+'A'*65537
            if mode == 'mixed': changed += [cat.ARG+'catalogSeed=true']
            if mode == 'duplicate-keys':
                import gzip, base64
                raw = b'{"schema":0,"schema":1}'
                changed[-2:] = [cat.ARG+'catalogFixture='+base64.b64encode(gzip.compress(raw)).decode(), cat.ARG+'catalogFixtureSha256='+cat.digest(raw)]
            if mode == 'label-override': changed += [cat.ARG+'catalogRunId=other']
            if mode == 'missing-environment': changed = [a for a in changed if not a.startswith(cat.ARG+'catalogEnvironmentSha256=')]
            if mode == 'partial-viewports': changed = [a for a in changed if not a.startswith(cat.ARG+'catalogTrashViewport=')]
            if mode == 'eager-replay': changed = [a.replace(cat.REVISIONS[1], cat.REVISIONS[0]) for a in changed]
            c = copy.deepcopy(f.configuration); c['plan'][0]['args'] = changed
            if mode == 'foreign-seed': c['plan'][0]['result_recipe']['accepted_seed']['receipt_sha256'] = '0'*64
            with self.subTest(mode=mode), self.assertRaises(ValueError): validate(c, fresh=False)
            write(f.base/'run-config.json', c)
            with self.subTest(adopted=mode), self.assertRaises(ValueError): adopted_configuration(f.base, f.adoption)
        (f.base/'run-config.json').write_bytes(original_config)

    def test_incomplete_seed_cannot_declare_replay_from_combined_baseline(self):
        f = self.fixture(); original = f.results
        def results():
            original(); f.raw_windows[('trash', 'steady', 9)].unlink()
        f.results = results
        self.assertFalse(f.run()[0])
        self.assertTrue(list(Path(f.ns['state']['tests'][0]['benchmark-data']).glob('*-baseline.json')))
        with self.assertRaisesRegex(ValueError, 'incomplete seed'): cat.make_seed_reference(f.base, f.adoption)

    def pair(self, mutate=None):
        eager = self.fixture(); self.assertEqual(eager.run(), (True, True))
        lazy = self.fixture(revision=cat.REVISIONS[1], seed=False, accepted_seed=cat.make_seed_reference(eager.base, eager.adoption))
        if mutate:
            original = lazy.results
            def results():
                original(); mutate(lazy)
            lazy.results = results
        self.assertEqual(lazy.run(), (True, True))
        path = offline(self.root, eager, lazy)
        return path, eager, lazy

    def test_matched_work_timing_and_final_geometry_refuse_after_valid_raw_capture(self):
        parent = self.root
        for mode in ['work', 'timing', 'final']:
            with self.subTest(mode=mode):
                self.root = parent/mode; self.root.mkdir()
                def mutate(f):
                    path = f.raw_windows[('tags', 'first', 0)]
                    if mode == 'final': path = next(path.parent.glob('*tags-first-final-anchor.json'))
                    value = json.loads(path.read_text())
                    if mode in ['work', 'final']:
                        snap = value['after'] if mode == 'work' else value
                        for field in ['top', 'bottom', 'clipped_top', 'clipped_bottom']: snap['anchors'][0][field] += 2
                    else:
                        gesture = value['gestures'][0]
                        gesture['events'][-1]['event_ms'] += 17
                        value['stream']['touches'][15]['event_ms'] += 17
                        gesture['finger_pixels_per_second'] = -480000/167
                        for field in ['mono_ns', 'boot_ns']: gesture['after']['clock'][field] += 17000000
                        gesture['after']['clock']['uptime_ms'] += 17
                        gesture['after']['last_draw_ns'] += 17000000
                    write(path, value)
                path, _, _ = self.pair(mutate)
                r = cat.assess_comparison(path)
                self.assertFalse(r['comparative_valid']); self.assertTrue(r['operational_success'], r['reason'])
                self.assertTrue(r['product_tests_success']); self.assertIn('mismatch' if mode == 'timing' else 'anchor' if mode == 'final' else 'work', r['reason'])
                self.root = parent

    def test_complete_synthetic_offline_evidence_passes_public_pure_assessor(self):
        path, eager, lazy = self.pair()
        before = {p: sha(p) for p in self.root.rglob('*') if p.is_file()}
        result = cat.assess_comparison(path)
        self.assertTrue(result['comparative_valid'], result['reason'])
        self.assertTrue(result['matched_work']); self.assertTrue(result['quiet_host'])
        self.assertEqual(len(result['windows']), 40)
        self.assertEqual(sum(r['eager']['deadline_denominator'] for r in result['windows']), 40)
        self.assertEqual(result['absolute_pixel_travel'], 'UNKNOWN'); self.assertEqual(result['runtime_acceptance'], 'UNVERIFIED')
        self.assertEqual(before, {p: sha(p) for p in self.root.rglob('*') if p.is_file()})

    def test_offline_semantic_negatives_refuse_even_with_refrozen_hashes(self):
        path, eager, lazy = self.pair()
        original = {p: (p.read_bytes(), p.stat().st_atime_ns, p.stat().st_mtime_ns)
                    for p in self.root.rglob('*') if p.is_file()}
        corpus = json.loads((self.root/'corpus.json').read_text())
        first = next(iter(corpus['revisions'][0]['analysis'].values()))
        for mode in ['csv-only', 'wrong-query', 'wrong-trace', 'wrong-tool', 'exit', 'header', 'null', 'loss', 'clock', 'marker',
                     'expected', 'quiet-missing', 'quiet-gap', 'quiet-breach', 'quiet-foreign', 'origin', 'pin', 'changed-file', 'alias', 'work', 'timing']:
            with self.subTest(mode=mode):
                # Restore only disposable fixture inputs owned by this test.
                for p, (raw, atime, mtime) in original.items():
                    if p.is_symlink(): p.unlink()
                    p.write_bytes(raw)
                    os.utime(p, ns=(atime, mtime))
                declaration = json.loads(path.read_text())
                command = self.root/first['commands']['frames']; value = json.loads(command.read_text())
                if mode == 'csv-only': value.pop('argv')
                if mode == 'wrong-query': value['query_sha256'] = '0'*64
                if mode == 'wrong-trace': value['trace'] = 'foreign'
                if mode == 'wrong-tool': value['processor_sha256'] = '0'*64
                if mode == 'exit': value['exit'] = 1
                write(command, value)
                csv_path = self.root/first['directory']/'frames.csv'
                if mode == 'header': csv_path.write_text(csv_path.read_text().replace('expected_id', 'id'))
                if mode == 'null': csv_path.write_text(csv_path.read_text().replace(',12000000,', ',NULL,'))
                if mode == 'loss': (self.root/first['directory']/'loss.csv').write_text('lost\n1\n')
                if mode == 'clock': (self.root/first['directory']/'clocks.csv').write_text('ts,clock_id,clock_value\n0,3,0\n1,3,999999999\n')
                if mode == 'marker': (self.root/first['directory']/'markers.csv').write_text('name,ts,dur\nwrong,1,1\n')
                if mode == 'expected': (self.root/first['directory']/'expected.csv').write_text('id,ts,dur\n99,1,1\n')
                quiet = self.root/'quiet-0.json'; q = json.loads(quiet.read_text())
                if mode == 'quiet-missing': declaration['captures'][0]['quiet_receipt'] = 'missing.json'
                if mode == 'quiet-gap': q['gaps'] = [[0, 1]]
                if mode == 'quiet-breach': q['breaches'] = ['swap']
                if mode == 'quiet-foreign': q['host']['boot_id'] = 'foreign'
                write(quiet, q)
                if mode == 'origin':
                    p = self.root/'origin-0.json'; o = json.loads(p.read_text()); o['credentials_retained'] = True; write(p, o)
                if mode == 'alias':
                    p = self.root/'processor'; p.unlink(); p.symlink_to(self.root/'processor-source.txt')
                if mode in ['work', 'timing']:
                    record = lazy.ns['state']['tests'][0]; p = next(Path(record['benchmark-data']).glob('*-window-0.json')); w = json.loads(p.read_text())
                    if mode == 'work':
                        for field in ['top', 'bottom', 'clipped_top', 'clipped_bottom']: w['after']['anchors'][0][field] += 2
                    else:
                        w['gestures'][0]['events'][-1]['event_ms'] += 17
                        w['stream']['touches'][15]['event_ms'] += 17
                        w['gestures'][0]['finger_pixels_per_second'] = -480000/167
                    write(p, w)
                    original_window = next(v for v in lazy.raw_windows.values() if v.name == p.name)
                    write(original_window, w)
                # Rebind semantic CSV mutations to their derivation receipts too,
                # so negatives reach validators beyond the initial hash check.
                c = copy.deepcopy(corpus)
                for export in c['revisions'][0]['analysis'].values():
                    for key in cat.HEADERS:
                        output = self.root/export['directory']/(key+'.csv')
                        export['csv_sha256'][key] = sha(output)
                        receipt_path = self.root/export['commands'][key]; r = json.loads(receipt_path.read_text())
                        r['stdout_sha256'] = sha(output)
                        r['started'] = output.stat().st_mtime
                        r['finished'] = r['started']+.001
                        write(receipt_path, r)
                write(self.root/'corpus.json', c)
                declaration['pins'] = {str(p.relative_to(self.root)): sha(p) for p in self.root.rglob('*') if p.is_file() and p != path}
                declaration['external_pins'] = {n: sha(n) for n in declaration['external_pins']}
                if mode == 'pin': del declaration['pins']['processor']
                write(path, declaration)
                if mode == 'changed-file': (self.root/'processor').write_text('changed after freeze')
                r = cat.assess_comparison(path)
                self.assertFalse(r['comparative_valid'], mode)
                self.assertTrue(r['reason'], mode)
                if mode.startswith('quiet-'):
                    self.assertTrue(r['operational_success'], r['reason'])
                    self.assertTrue(r['product_tests_success'], r['reason'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
