"""Declared post-build Gradle index observations. No device, Gradle or cache writes.

Synthetic trees stand in for the two reviewed index paths. The real modules
cache is never opened. Startup and helpers stay strict; only the scoped-app
post assessor may record the two indexes.
"""
from pathlib import Path
import copy
import hashlib
import inspect
import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

from test_candidate import SCRATCH, config as legacy_config
from scoped_controller_fixture import WorkloadFixture
import configuration as top_cfg
import post_input_policy as top_policy
import preparation_policy
from runtime import configuration as cfg
from runtime import post_input_policy as pkg_policy
from scoped_results import assess_benchmark_api, assess_scoped

PRODUCED = (
    '/home/e0ipso/.gradle/caches/modules-2/metadata-2.107/module-artifact.bin',
    '/home/e0ipso/.gradle/caches/modules-2/metadata-2.107/module-metadata.bin',
)
ROOTS = (
    '/home/e0ipso/.gradle/caches/modules-2/files-2.1',
    '/home/e0ipso/.gradle/caches/modules-2/metadata-2.107',
)


def rewrite(path, data):
    fd = os.open(path, os.O_WRONLY | os.O_CLOEXEC)
    try:
        os.ftruncate(fd, 0)
        os.write(fd, data)
    finally:
        os.close(fd)


def scoped_job(**overrides):
    job = {'api': 36, 'label': 'scoped-app', 'standalone': False, 'seconds': 1800,
           'args': ['scripts/instrumented-tests.sh',
                    '-Pandroid.testInstrumentationRunnerArguments.class=com.example.WidgetTest'],
           'result_recipe': {'profile': 'scoped-app-classes-v1', 'classes': ['com.example.WidgetTest'],
                             'inventory': []}}
    job.update(overrides)
    return job


def valid_declaration(module):
    """Policy shape only. Paths are the reviewed strings; nothing is opened."""
    roots, paths = module.reviewed()
    rows, pins = [], {}
    for index, path in enumerate(paths):
        digest = format(index + 1, '064x')
        rows.append({'path': path, 'sha256': digest, 'bytes': 8, 'device': 11, 'inode': 100 + index})
        pins[path] = digest
    pins['/opt/tokkl/outside-pin'] = 'cd' * 32
    members = sorted(paths)
    policy = {'profile': module.PROFILE, 'job': 'scoped-app', 'indexes': rows,
              'dependency_membership': {'roots': list(roots), 'members': len(members),
                                        'sha256': module.digest(members)}}
    return {'plan': [scoped_job()], 'input_pins': pins, 'post_input_policy': policy}, roots, paths


def synthetic_tree(module):
    """Caller has already pointed module.MODULES at an empty synthetic cache."""
    roots, paths = module.reviewed()
    for root in roots:
        Path(root).mkdir(parents=True)
    pins, rows, members = {}, [], []
    for path in paths:
        blob = b'index-' + Path(path).name.encode()
        Path(path).write_bytes(blob)
        info = os.lstat(path)
        digest = hashlib.sha256(blob).hexdigest()
        pins[path] = digest
        rows.append({'path': path, 'sha256': digest, 'bytes': info.st_size,
                     'device': info.st_dev, 'inode': info.st_ino})
        members.append(path)
    extra = Path(roots[0]) / 'member.jar'
    extra.write_bytes(b'member')
    pins[str(extra)] = hashlib.sha256(b'member').hexdigest()
    members.append(str(extra))
    policy = {'profile': module.PROFILE, 'job': 'scoped-app', 'indexes': rows,
              'dependency_membership': {'roots': roots, 'members': len(members),
                                        'sha256': module.digest(sorted(members))}}
    config = {'plan': [scoped_job()], 'input_pins': pins, 'post_input_policy': policy,
              '_sha256': 'ab' * 32}
    return config, paths, str(extra)


class PolicyShapeTests(unittest.TestCase):
    def test_reviewed_paths_are_the_two_global_indexes(self):
        roots, paths = pkg_policy.reviewed()
        self.assertEqual(tuple(roots), ROOTS)
        self.assertEqual(tuple(paths), PRODUCED)
        self.assertEqual(pkg_policy.declared({}), ())
        self.assertIsNone(pkg_policy.policy_sha256({}))

    def test_exact_single_job_declaration_is_accepted_without_reading_indexes(self):
        config, _, paths = valid_declaration(pkg_policy)
        self.assertEqual(pkg_policy.declared(config), tuple(paths))
        self.assertEqual(pkg_policy.policy_sha256(config), pkg_policy.digest(config['post_input_policy']))

    def test_unsupported_mixed_and_identity_declarations_are_refused(self):
        config, _, paths = valid_declaration(pkg_policy)
        cases = []

        def unsupported(candidate):
            candidate['post_input_policy']['profile'] = 'other'
        cases.append(('profile', unsupported, 'unsupported post-input policy'))

        def extra_key(candidate):
            candidate['post_input_policy']['alias'] = paths[0]
        cases.append(('extra-key', extra_key, 'unsupported post-input policy'))

        def multi(candidate):
            candidate['plan'].append(scoped_job(label='second'))
        cases.append(('multi-job', multi, 'multi-job and mixed plans'))

        def mixed(candidate):
            candidate['plan_recipe'] = 'compile-then-scoped-app-v1'
        cases.append(('mixed', mixed, 'multi-job and mixed plans'))

        def benchmark(candidate):
            candidate['plan'][0]['result_recipe']['profile'] = 'benchmark-api-v1'
        cases.append(('benchmark', benchmark, 'single scoped-app API36 job'))

        def catalog(candidate):
            candidate['plan'][0]['result_recipe']['profile'] = 'catalog-frame-api-v1'
        cases.append(('catalog', catalog, 'single scoped-app API36 job'))

        def api31(candidate):
            candidate['plan'][0]['api'] = 31
        cases.append(('api31', api31, 'single scoped-app API36 job'))

        def standalone(candidate):
            candidate['plan'][0]['standalone'] = True
        cases.append(('standalone', standalone, 'single scoped-app API36 job'))

        def wildcard(candidate):
            candidate['post_input_policy']['indexes'][0]['path'] = paths[0] + '*'
        cases.append(('wildcard', wildcard, 'wildcard post-input paths'))

        def extra_path(candidate):
            candidate['post_input_policy']['indexes'].append(dict(candidate['post_input_policy']['indexes'][0]))
        cases.append(('extra-path', extra_path, 'exactly two complete'))

        def wrong_order(candidate):
            rows = candidate['post_input_policy']['indexes']
            rows[0], rows[1] = rows[1], rows[0]
        cases.append(('order', wrong_order, 'in order'))

        def wrong_path(candidate):
            candidate['post_input_policy']['indexes'][0]['path'] = '/opt/tokkl/module-artifact.bin'
        cases.append(('wrong-path', wrong_path, 'only the two reviewed'))

        def alias(candidate):
            candidate['post_input_policy']['indexes'][1]['inode'] = 100
        cases.append(('alias', alias, 'aliased declared indexes'))

        def stale(candidate):
            candidate['input_pins'][paths[0]] = 'ef' * 32
        cases.append(('stale-pin', stale, 'stale for the original index pin'))

        def membership_hash(candidate):
            candidate['post_input_policy']['dependency_membership']['sha256'] = 'ab' * 32
        cases.append(('membership-hash', membership_hash, 'differs from the original pins'))

        def membership_count(candidate):
            candidate['post_input_policy']['dependency_membership']['members'] = 0
        cases.append(('membership-count', membership_count, 'exact declared dependency membership'))

        def oversized(candidate):
            candidate['post_input_policy']['indexes'][0]['bytes'] = pkg_policy.INDEX_LIMIT + 1
        cases.append(('oversized-declaration', oversized, 'bounded declared index size'))

        for name, mutate, pattern in cases:
            with self.subTest(name=name):
                candidate = copy.deepcopy(config)
                mutate(candidate)
                with self.assertRaisesRegex(ValueError, pattern):
                    pkg_policy.declared(candidate)

    def test_incomplete_evidence_is_refused_before_any_index_read(self):
        config, _, _ = valid_declaration(pkg_policy)
        config['_sha256'] = 'ab' * 32
        with self.assertRaisesRegex(ValueError, 'incomplete post-input evidence'):
            pkg_policy.confirm(config, [], {})
        with self.assertRaisesRegex(ValueError, 'frozen configuration identity'):
            pkg_policy.observe(valid_declaration(pkg_policy)[0], {})

    def test_other_post_consumers_do_not_gain_the_entry_point(self):
        import catalog_profile
        import check_and_repro
        self.assertIn('post_input_configuration', inspect.getsource(assess_scoped))
        for function in [assess_benchmark_api, catalog_profile.assess, check_and_repro.binding,
                         preparation_policy.helper_request, cfg.execution_configuration, cfg.validate,
                         top_cfg.validate]:
            with self.subTest(function=function.__qualname__):
                self.assertNotIn('post_input_configuration', inspect.getsource(function))


class LegacyPinTests(unittest.TestCase):
    def test_default_configuration_keeps_full_pin_checks_and_refuses_the_policy(self):
        self.tmp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.addCleanup(self.tmp.cleanup)
        candidate = legacy_config(Path(self.tmp.name))
        checked = cfg.validate(candidate)
        self.assertNotIn('post_input_policy', checked)
        self.assertIsNone(pkg_policy.policy_sha256(checked))
        path = next(iter(candidate['input_pins']))
        changed = copy.deepcopy(candidate)
        changed['input_pins'][path] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'input changed'):
            cfg.validate(changed)
        opted = copy.deepcopy(candidate)
        opted['post_input_policy'] = {
            'profile': pkg_policy.PROFILE, 'job': 'full36', 'indexes': [],
            'dependency_membership': {'roots': [], 'members': 1, 'sha256': 'ab' * 32}}
        with self.assertRaisesRegex(ValueError, 'single scoped-app API36 job'):
            cfg.validate(opted)
        with self.assertRaisesRegex(ValueError, 'single scoped-app API36 job'):
            top_cfg.validate(opted)


class IndexFileTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.root = Path(self.tmp.name)
        self.modules = self.root / 'modules-2'
        self.patches = [patch.object(top_policy, 'MODULES', self.modules),
                        patch.object(pkg_policy, 'MODULES', self.modules)]
        for item in self.patches:
            item.start()
        self.config, self.paths, self.member = synthetic_tree(pkg_policy)

    def tearDown(self):
        for item in reversed(self.patches):
            item.stop()
        root = self.root
        self.tmp.cleanup()
        self.assertFalse(root.exists())

    def test_stable_tree_confirms_without_claiming_a_product_result(self):
        evidence = {}
        observed = pkg_policy.observe(self.config, evidence)
        self.assertEqual(evidence['status'], 'refused')
        self.assertNotIn('confirmed_at', evidence)
        self.assertFalse(any(row['changed'] for row in evidence['indexes']))
        pkg_policy.confirm(self.config, observed, evidence)
        self.assertEqual(evidence['status'], 'accepted')
        self.assertIn('confirmed_at', evidence)
        self.assertEqual(evidence['not_inferred'], pkg_policy.NOT_INFERRED)
        self.assertEqual(evidence['strict_pins'], len(self.config['input_pins']) - 2)
        self.assertNotEqual(evidence['before_source'], evidence['after_source'])
        self.assertEqual(evidence['dependency_membership']['members'], 3)

    def test_symlink_hardlink_missing_empty_and_oversized_indexes_are_refused(self):
        path = Path(self.paths[0])
        target = path.with_name('target.bin')
        target.write_bytes(path.read_bytes())
        path.unlink()
        path.symlink_to(target.name)
        with self.assertRaisesRegex(ValueError, 'symlinked post-input path'):
            pkg_policy.before(self.config)

    def test_hardlinked_indexes_are_refused(self):
        artifact, metadata = map(Path, self.paths)
        metadata.unlink()
        os.link(artifact, metadata)
        with self.assertRaisesRegex(ValueError, 'one unaliased regular file'):
            pkg_policy.before(self.config)

    def test_missing_index_is_refused(self):
        os.remove(self.paths[1])
        with self.assertRaisesRegex(ValueError, 'missing declared index'):
            pkg_policy.before(self.config)

    def test_empty_replacement_and_oversized_read_are_refused(self):
        rewrite(self.paths[0], b'')
        with self.assertRaisesRegex(ValueError, 'empty or oversized'):
            pkg_policy._regular(self.paths[0])
        rewrite(self.paths[0], b'index-' + Path(self.paths[0]).name.encode())
        with patch.object(pkg_policy, 'INDEX_LIMIT', 1):
            with self.assertRaisesRegex(ValueError, 'empty or oversized'):
                pkg_policy._regular(self.paths[0])

    def test_unstable_index_stays_a_refusal_capture(self):
        path = self.paths[0]
        canonical, real_sha = pkg_policy._primitives()

        def changing(target):
            if os.fspath(target) == path:
                fd = os.open(path, os.O_WRONLY | os.O_APPEND | os.O_CLOEXEC)
                try:
                    os.write(fd, b'unstable')
                finally:
                    os.close(fd)
            return real_sha(target)

        evidence = {}
        with patch.object(pkg_policy, '_primitives', return_value=(canonical, changing)):
            with self.assertRaisesRegex(ValueError, 'changed while observed'):
                pkg_policy.observe(self.config, evidence)
        self.assertEqual(evidence['status'], 'refused')
        self.assertNotIn('confirmed_at', evidence)
        self.assertEqual(evidence['before_source'], pkg_policy.BEFORE)
        self.assertEqual(evidence['after_source'], pkg_policy.AFTER)

    def test_unreviewed_root_and_membership_delta_are_refused(self):
        (self.modules / 'metadata-2.99').mkdir()
        with self.assertRaisesRegex(ValueError, 'unreviewed Gradle dependency root'):
            pkg_policy.membership(self.config)
        (self.modules / 'metadata-2.99').rmdir()
        extra = self.modules / 'files-2.1' / 'added.jar'
        extra.write_bytes(b'added')
        with self.assertRaisesRegex(ValueError, r'dependency membership changed: 1 added'):
            pkg_policy.membership(self.config)
        extra.unlink()
        os.remove(self.member)
        with self.assertRaisesRegex(ValueError, 'removed'):
            pkg_policy.membership(self.config)


class ScopedObservationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.patches = []
        cls.env = None
        cls.tmp = tempfile.TemporaryDirectory(dir=SCRATCH)
        cls.root = Path(cls.tmp.name)
        try:
            cls.modules = cls.root / 'gradle-modules'
            cls.patches = [patch.object(top_policy, 'MODULES', cls.modules),
                           patch.object(pkg_policy, 'MODULES', cls.modules)]
            for item in cls.patches:
                item.start()
            cls.fixture = WorkloadFixture(cls.root / 'case', widget=True, post_input=True)
            cls.env = patch.dict(os.environ, {'TOKKL_VERIFICATION_ADOPTION': str(cls.fixture.adoption)})
            cls.env.start()
        except Exception:
            cls.tearDownClass()
            raise

    @classmethod
    def tearDownClass(cls):
        if cls.env is not None:
            cls.env.stop()
        for item in reversed(cls.patches):
            item.stop()
        root = cls.root
        cls.tmp.cleanup()
        assert not root.exists()

    def clean(self):
        fixture = self.fixture
        for path, data in fixture.index_bytes.items():
            rewrite(path, data)
        rewrite(fixture.descriptor_path, b'frozen-descriptor')

    def test_startup_and_helper_refuse_drift_that_post_assessment_records(self):
        self.clean()
        fixture = self.fixture
        path = fixture.index_paths[1]
        disk = json.loads((fixture.base / 'run-config.json').read_text())
        self.assertEqual(cfg.validate(disk, fresh=False)['_sha256'], fixture.checked['_sha256'])
        self.assertEqual(disk['input_pins'][path], fixture.checked['input_pins'][path])
        rewrite(path, b'observed-module-metadata')
        try:
            for checker in (cfg.validate, top_cfg.validate):
                with self.subTest(checker=checker.__module__), self.assertRaisesRegex(ValueError, 'checked input changed: .*/module-metadata.bin'):
                    checker(disk, fresh=False)
            argv = ['1', '--state', str(fixture.base / 'window-state-x.json'), '--preparation', '0']
            with self.assertRaisesRegex(ValueError, 'checked input changed: .*/module-metadata.bin'):
                preparation_policy.helper_request(fixture.base, argv)
            with patch.object(sys, 'argv', ['controller.py', '--execute', '--adoption', str(fixture.adoption)]):
                with self.assertRaisesRegex(ValueError, 'checked input changed: .*/module-metadata.bin'):
                    cfg.execution_configuration(fixture.base / 'controller.py')
            evidence = {}
            current = top_cfg.post_input_configuration(fixture.checked, evidence)
            self.assertEqual(current, fixture.checked)
            self.assertEqual(current['_sha256'], fixture.checked['_sha256'])
            self.assertEqual(current['input_pins'][path], disk['input_pins'][path])
            self.assertEqual(json.loads((fixture.base / 'run-config.json').read_text())['input_pins'][path], disk['input_pins'][path])
            row = next(item for item in evidence['indexes'] if item['path'] == path)
            self.assertTrue(row['changed'])
            self.assertEqual(row['before']['sha256'], disk['input_pins'][path])
            self.assertNotEqual(row['after']['sha256'], row['before']['sha256'])
            self.assertEqual((row['after']['device'], row['after']['inode']), (row['before']['device'], row['before']['inode']))
            other = next(item for item in evidence['indexes'] if item['path'] != path)
            self.assertFalse(other['changed'])
            self.assertEqual(evidence['status'], 'accepted')
            self.assertEqual(evidence['configuration_sha256'], fixture.checked['_sha256'])
            self.assertEqual(evidence['not_inferred'], pkg_policy.NOT_INFERRED)
            self.assertEqual(evidence['original_pins'] - evidence['strict_pins'], 2)
            self.assertIn('confirmed_at', evidence)
        finally:
            rewrite(path, fixture.index_bytes[path])

    def test_synthetic_scoped_result_keeps_product_failures_unsuccessful(self):
        self.clean()
        fixture = self.fixture
        path = fixture.index_paths[1]
        original_pin = fixture.checked['input_pins'][path]
        identity = fixture.checked['_sha256']
        rewrite(path, b'post-closure-module-metadata')
        try:
            self.assertEqual(fixture.run(), (True, True))
            contract = fixture.ns['state']['tests'][0]['result_contract']
            self.assertTrue(contract['passed'])
            self.assertEqual(contract['app_counts']['tests'], 7)
            self.assertEqual(contract['configuration_sha256'], identity)
            self.assertEqual(contract['input_policy']['status'], 'accepted')
            self.assertEqual(contract['input_policy']['configuration_sha256'], identity)
            self.assertEqual(fixture.checked['input_pins'][path], original_pin)
            self.assertEqual(json.loads((fixture.base / 'run-config.json').read_text())['input_pins'][path], original_pin)
            row = next(item for item in contract['input_policy']['indexes'] if item['path'] == path)
            self.assertTrue(row['changed'])
            self.assertNotEqual(row['after']['sha256'], original_pin)
            record = fixture.ns['state']['tests'][0]
            payload = assess_scoped(fixture.checked, *fixture.job[:4], record, 0, 7, None)
            self.assertFalse(payload['passed'])
            self.assertIn('payload', payload['reason'])
            self.assertEqual(payload['input_policy']['status'], 'accepted')
            stopped = assess_scoped(fixture.checked, *fixture.job[:4], record, 0, 0, 'resource')
            self.assertFalse(stopped['passed'])
            folder = fixture.root / 'empty-junit'
            folder.mkdir()
            ET.ElementTree(ET.Element('testsuite', tests='0', failures='0', errors='0', skipped='0')).write(folder / 'TEST-empty.xml')
            empty = assess_scoped(fixture.checked, *fixture.job[:4], dict(record, results=str(folder)), 0, 0, None)
            self.assertFalse(empty['passed'])
            self.assertIn('empty', empty['reason'])
            src = next(Path(record['results']).rglob('TEST-*.xml'))
            failed_dir = fixture.root / 'failed-junit'
            failed_dir.mkdir()
            tree = ET.parse(src)
            root = tree.getroot()
            ET.SubElement(root.find('testcase'), 'failure', message='clock')
            root.set('failures', '1')
            tree.write(failed_dir / 'TEST-app.xml')
            counts = dict(record['counts'], failures=1)
            failed = assess_scoped(fixture.checked, *fixture.job[:4], dict(record, results=str(failed_dir), counts=counts), 0, 0, None)
            self.assertFalse(failed['passed'])
            self.assertIn('failed/error/skipped', failed['reason'])
            partial_dir = fixture.root / 'partial-junit'
            partial_dir.mkdir()
            tree = ET.parse(src)
            root = tree.getroot()
            root.remove(root.find('testcase'))
            root.set('tests', str(len(root.findall('testcase'))))
            tree.write(partial_dir / 'TEST-app.xml')
            partial = assess_scoped(fixture.checked, *fixture.job[:4], dict(record, results=str(partial_dir)), 0, 0, None)
            self.assertFalse(partial['passed'])
            self.assertIn('coverage', partial['reason'])
            self.assertEqual(fixture.checked['_sha256'], identity)
            self.assertEqual(fixture.checked['input_pins'][path], original_pin)
        finally:
            rewrite(path, fixture.index_bytes[path])

    def test_without_the_policy_the_same_index_pin_stays_strict(self):
        self.clean()
        fixture = self.fixture
        bare = copy.deepcopy(fixture.configuration)
        del bare['post_input_policy']
        bare['run_dir'] = str(fixture.root / 'bare-run')
        checked = cfg.validate(bare)
        self.assertNotEqual(checked['_sha256'], fixture.checked['_sha256'])
        self.assertIsNone(pkg_policy.policy_sha256(checked))
        path = fixture.index_paths[0]
        rewrite(path, b'legacy-index-drift')
        try:
            with self.assertRaisesRegex(ValueError, 'checked input changed: .*/module-artifact.bin'):
                cfg.validate(bare)
        finally:
            rewrite(path, fixture.index_bytes[path])

    def test_non_index_drift_remains_a_refusal_with_refusal_time_capture(self):
        self.clean()
        fixture = self.fixture
        path = fixture.descriptor_path
        original = Path(path).read_bytes()
        rewrite(path, b'drifted-descriptor')
        evidence = {}
        try:
            with self.assertRaisesRegex(ValueError, 'checked input changed: .*/descriptor.bin'):
                top_cfg.post_input_configuration(fixture.checked, evidence)
            self.assertEqual(evidence['status'], 'refused')
            self.assertNotIn('confirmed_at', evidence)
            self.assertEqual(len(evidence['indexes']), 2)
            self.assertEqual(evidence['before_source'], pkg_policy.BEFORE)
            self.assertEqual(evidence['after_source'], pkg_policy.AFTER)
            self.assertEqual(evidence['configuration_sha256'], fixture.checked['_sha256'])
        finally:
            rewrite(path, original)

    def test_dependency_addition_is_refused_on_startup_and_post_assessment(self):
        self.clean()
        fixture = self.fixture
        extra = self.modules / 'files-2.1' / 'added.jar'
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_bytes(b'added')
        disk = json.loads((fixture.base / 'run-config.json').read_text())
        try:
            with self.assertRaisesRegex(ValueError, r'dependency membership changed: 1 added'):
                cfg.validate(disk, fresh=False)
            evidence = {}
            with self.assertRaisesRegex(ValueError, r'dependency membership changed: 1 added'):
                top_cfg.post_input_configuration(fixture.checked, evidence)
            self.assertEqual(evidence['status'], 'refused')
            self.assertNotIn('confirmed_at', evidence)
        finally:
            extra.unlink()

    def test_stale_policy_adoption_and_carry_are_refused(self):
        self.clean()
        fixture = self.fixture
        stale = copy.deepcopy(fixture.configuration)
        stale['post_input_policy']['indexes'][0]['sha256'] = 'ef' * 32
        with self.assertRaisesRegex(ValueError, 'stale for the original index pin'):
            cfg.validate(stale, fresh=False)
        text = fixture.adoption.read_text()
        try:
            body = json.loads(text)
            body['post_input_policy_sha256'] = '0' * 64
            fixture.adoption.write_text(json.dumps(body))
            with self.assertRaisesRegex(ValueError, 'frozen post-input policy'):
                cfg.adopted_configuration(fixture.base, fixture.adoption)
        finally:
            fixture.adoption.write_text(text)
        carried = copy.deepcopy(fixture.configuration)
        carried['run_dir'] = str(fixture.root / 'carry-run')
        carried.update(reclaim_budget_bytes=100, carried_reclaim_bytes=5,
                       preparation_policy={'max_request_bytes': 100, 'max_preparations': 1},
                       prior_window=None)
        with self.assertRaisesRegex(ValueError, 'nonzero carry requires prior ledger'):
            cfg.validate(carried)
        ledger = fixture.root / 'prior.json'
        ledger.write_text(json.dumps({'reclaim_reserved_bytes': 5}))
        carried.update(prior_window=str(ledger), prior_window_sha256=cfg.sha(ledger))
        self.assertTrue(cfg.validate(carried))
        ledger.write_text(json.dumps({'reclaim_reserved_bytes': 6}))
        with self.assertRaisesRegex(ValueError, 'ledger changed'):
            cfg.validate(carried)

    def test_post_assessment_requires_the_matching_adoption(self):
        self.clean()
        with patch.dict(os.environ):
            os.environ.pop('TOKKL_VERIFICATION_ADOPTION', None)
            with self.assertRaisesRegex(ValueError, 'matching root adoption'):
                top_cfg.post_input_configuration(self.fixture.checked, {})
