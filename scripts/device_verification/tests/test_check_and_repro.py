"""Finite recipe canaries. Only owned tiny executables and modeled cgroups."""
from contextlib import chdir
from pathlib import Path
from unittest.mock import patch
import copy
import hashlib
import json
import os
import signal
import subprocess
import sys
import tempfile
import unittest

from test_candidate import config, PACKAGE, SCRATCH
from scoped_controller_fixture import WorkloadFixture
from runtime import configuration as cfg
from runtime import check_and_repro as recipe
from prepare import prepare
from scope_completion import write


class FiniteRecipeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.root = Path(self.temp.name)
        self.project = self.root/'project'
        (self.project/'scripts').mkdir(parents=True)
        (self.project/'scripts/instrumented-tests.sh').write_bytes((PACKAGE.parent/'instrumented-tests.sh').read_bytes())
        self.payload('./gradlew', 0)
        self.payload('scripts/reproducible-release.sh', 0)
        self.config = config(self.root)
        self.base = Path(self.config['run_dir'])
        self.config['project'] = str(self.project)
        self.config['preparation_policy']['max_preparations'] = 2
        self.config['plan'].append(dict(api=0, label=recipe.LABEL, standalone=True, seconds=1800,
                                       args=recipe.argv(self.base), host_recipe=recipe.PROFILE))
        pins = self.config['input_pins']
        pins.pop(str(PACKAGE.parent/'instrumented-tests.sh'))
        for relative in ['scripts/instrumented-tests.sh', 'gradlew', 'scripts/reproducible-release.sh']:
            path = self.project/relative
            pins[str(path)] = cfg.sha(path)

    def tearDown(self):
        root = self.root
        self.temp.cleanup()
        self.assertFalse(root.exists(), 'owned fixtures must be removed')

    def payload(self, name, status):
        path = self.project/name
        path.write_text('#!/usr/bin/python3\nimport json,os,sys\nfrom pathlib import Path\np=Path("calls.jsonl")\nwith p.open("a") as out: out.write(json.dumps({"argv":sys.argv,"cwd":os.getcwd(),"java":os.environ.get("JAVA_HOME"),"workers":os.environ.get("GRADLE_OPTS")})+"\\n")\nsys.exit('+str(status)+')\n')
        path.chmod(0o700)

    def freeze(self):
        proof = prepare(self.config)
        self.adoption = self.root/'modeled-adoption.json'
        self.adoption.write_text(json.dumps(dict(configuration_sha256=proof['configuration_sha256'],
            runtime_sha256=proof['runtime_sha256'], lease_id=self.config['lease_id'],
            approved_by='HOST-MODEL-ONLY', cheap_live_controller_fixtures_verified=True)))
        self.session = self.base/'completion-assemble-check-20261023000000'
        self.session.mkdir()
        unit = 'tokkl-0273-assemble-check-20261023000000.scope'
        self.scope = '/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice/'+unit
        folder = self.session/'scopes'/unit
        folder.mkdir(parents=True)
        write(self.session/'request.json', dict(main_unit=unit, command_seconds=1835,
              app=str(Path(self.scope).parent), nonce='owned-host-fixture'))
        write(folder/'registered.json', dict(unit=unit, scope=self.scope, nonce='owned-host-fixture'))
        self.environment = dict(self.config['environment'], PATH=str(self.base/'shim')+':/usr/bin:/bin',
            TOKKL_VERIFICATION_ADOPTION=str(self.adoption), TOKKL_SCOPE_SESSION=str(self.session),
            MEM_GATE_CGROUP_MB='5000', MEM_GATE_WAIT_S='300', TMPDIR=str(self.base/'scratch'),
            TOKKL_TEST_SLOTS='1', TOKKL_TEST_AVD='tokkl-api0')

    def bound(self):
        with chdir(self.project), patch.dict(os.environ, self.environment), patch('scope_completion.process_identity', return_value={'cgroup': self.scope}):
            return recipe.binding(self.base)

    def sequence(self, first=0, second=0):
        self.payload('./gradlew', first)
        self.payload('scripts/reproducible-release.sh', second)
        session = self.root/'sequence'
        session.mkdir()
        with chdir(self.project), patch.dict(os.environ, JAVA_HOME='/jdk'):
            status = recipe.run_sequence(session)
        receipt = cfg.load(session/'check-and-repro.json')
        calls = [json.loads(row) for row in (self.project/'calls.jsonl').read_text().splitlines()]
        return status, receipt, calls

    def test_exact_two_job_recipe_validates_without_spending_or_creating_snapshot(self):
        checked = cfg.validate(self.config)
        self.assertEqual(checked['preparation_policy']['max_preparations'], 2)
        self.assertFalse(self.base.exists())
        self.assertEqual(self.config['reclaim_budget_bytes'], 0)

    def test_unknown_wrappers_profiles_positions_deadlines_and_shapes_refuse(self):
        for change in [dict(args=['bash', '-c', 'true']), dict(args=['python3', '-B', '/outside/wrapper.py']),
                       dict(host_recipe='other'), dict(seconds=1801), dict(seconds=1799), dict(label='other'),
                       dict(api=36), dict(standalone=False), dict(result_recipe={})]:
            candidate = copy.deepcopy(self.config)
            candidate['plan'][1].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                cfg.validate(candidate)
        for mutate in [lambda c: c['plan'].reverse(),
                       lambda c: c['plan'].append(dict(c['plan'][1], label='third')),
                       lambda c: c.update(plan_recipe='compile-then-scoped-app-v1')]:
            candidate = copy.deepcopy(self.config)
            mutate(candidate)
            with self.assertRaises(ValueError):
                cfg.validate(candidate)
        candidate = copy.deepcopy(self.config)
        candidate['plan'][1].pop('host_recipe')
        with self.assertRaises(ValueError):
            cfg.validate(candidate)

    def test_three_jobs_two_allowances_and_original_gate_budget_identity_guards_refuse(self):
        for mutate in [lambda c: c['plan'].append(dict(c['plan'][1], label='third')),
                       lambda c: c.update(gate='/other'), lambda c: c.update(slots=2),
                       lambda c: c.update(reclaim_budget_bytes=cfg.MAX_BUDGET+1),
                       lambda c: c.update(carried_reclaim_bytes=1), lambda c: c.update(checked_head='unknown'),
                       lambda c: c['preparation_policy'].update(max_preparations=5)]:
            candidate = copy.deepcopy(self.config)
            mutate(candidate)
            with self.assertRaises(ValueError):
                cfg.validate(candidate)

    def test_old_standalone_shapes_still_validate_and_unreviewed_compile_still_refuses(self):
        for label, command in recipe.COMMANDS:
            candidate = copy.deepcopy(self.config)
            candidate['plan'][1] = dict(api=0, label=label, args=list(command), standalone=True, seconds=1800)
            self.assertTrue(cfg.validate(candidate))
        candidate['plan'][1] = dict(api=0, label='assemble-check', args=['./gradlew', '--no-daemon', 'assembleDebug', 'assembleDebugAndroidTest', 'check', '--stacktrace'], standalone=True, seconds=1800)
        with self.assertRaises(ValueError):
            cfg.validate(candidate)

    def test_both_actual_tiny_children_keep_exact_argv_environment_cwd_and_outcomes(self):
        status, receipt, calls = self.sequence()
        self.assertEqual(status, 0)
        self.assertEqual(receipt['exit'], 0)
        self.assertEqual([row['returncode'] for row in receipt['commands']], [0, 0])
        self.assertEqual([row['status'] for row in receipt['commands']], ['exited', 'exited'])
        self.assertEqual([row['argv'] for row in receipt['commands']], [list(command) for _, command in recipe.COMMANDS])
        self.assertEqual(calls[0]['argv'], list(recipe.COMMANDS[0][1]))
        self.assertEqual(calls[1]['argv'], ['scripts/reproducible-release.sh'])
        self.assertEqual(calls[1]['workers'], '-Dorg.gradle.workers.max=1')
        self.assertTrue(all(row['cwd'] == str(self.project) and row['java'] == '/jdk' for row in calls))

    def test_first_actual_failure_is_propagated_and_second_never_starts(self):
        status, receipt, calls = self.sequence(first=7)
        self.assertEqual(status, 7)
        self.assertEqual(receipt['exit'], 7)
        self.assertEqual([row['returncode'] for row in receipt['commands']], [7, None])
        self.assertEqual(receipt['commands'][1]['status'], 'not_run')
        self.assertEqual(len(calls), 1)

    def test_second_actual_failure_fails_sequence_and_retains_first_success(self):
        status, receipt, calls = self.sequence(second=9)
        self.assertEqual(status, 9)
        self.assertEqual(receipt['exit'], 9)
        self.assertEqual([row['returncode'] for row in receipt['commands']], [0, 9])
        self.assertEqual(len(calls), 2)

    def test_child_signal_propagates_and_launch_failure_never_passes_or_starts_second(self):
        for result, expected in [(-signal.SIGTERM, 143), (OSError('owned launch refusal'), 125)]:
            session = self.root/str(expected)
            session.mkdir()
            with patch.object(recipe.subprocess, 'run', side_effect=result if isinstance(result, Exception) else None,
                              return_value=subprocess.CompletedProcess([], result) if isinstance(result, int) else None) as run:
                self.assertEqual(recipe.run_sequence(session), expected)
            self.assertEqual(run.call_count, 1)
            receipt = cfg.load(session/'check-and-repro.json')
            self.assertIsNone(receipt['commands'][1]['returncode'])
            self.assertEqual(receipt['commands'][1]['status'], 'not_run')

    def test_interrupted_sequence_retains_incomplete_receipt_and_has_no_inner_timeout(self):
        session = self.root/'interrupted'
        session.mkdir()
        with patch.object(recipe.subprocess, 'run', side_effect=InterruptedError('timeout fixture')):
            self.assertEqual(recipe.run_sequence(session), 125)
        receipt = cfg.load(session/'check-and-repro.json')
        self.assertEqual(receipt['commands'][0]['status'], 'launch_failed')
        self.assertIsNone(receipt['commands'][0]['returncode'])
        self.assertEqual(receipt['commands'][1]['status'], 'not_run')
        with self.assertRaises(FileExistsError):
            recipe.run_sequence(session)
        source = (PACKAGE/'runtime/check_and_repro.py').read_text()
        self.assertNotIn('timeout=', source)
        self.assertNotIn('shell=True', source)

    def test_frozen_helper_and_both_command_sources_are_pinned_and_adopted(self):
        self.freeze()
        self.assertEqual(self.bound()[1], self.session)
        helper = self.base/'check_and_repro.py'
        self.assertEqual(helper.read_bytes(), (PACKAGE/'runtime/check_and_repro.py').read_bytes())
        helper.write_text(helper.read_text()+'# changed\n')
        with self.assertRaisesRegex(ValueError, 'frozen runtime changed'):
            self.bound()

    def test_missing_command_pin_changed_command_or_missing_manifest_helper_refuses(self):
        candidate = copy.deepcopy(self.config)
        candidate['input_pins'].pop(str(self.project/'gradlew'))
        with self.assertRaisesRegex(ValueError, 'command sources must be pinned'):
            cfg.validate(candidate)
        self.freeze()
        path = self.project/'scripts/reproducible-release.sh'
        old = path.read_bytes()
        path.write_bytes(old+b'# changed\n')
        with self.assertRaisesRegex(ValueError, 'checked input changed'):
            self.bound()
        path.write_bytes(old)
        manifest = cfg.load(self.base/'runtime-manifest.json')
        manifest.pop('check_and_repro.py')
        (self.base/'runtime-manifest.json').write_text(json.dumps(manifest))
        adoption = cfg.load(self.adoption)
        adoption['runtime_sha256'] = hashlib.sha256(cfg.canonical(manifest)).hexdigest()
        self.adoption.write_text(json.dumps(adoption))
        with self.assertRaisesRegex(ValueError, 'runtime sources must be adopted'):
            self.bound()

    def test_unbound_entry_bad_adoption_environment_cwd_and_scope_refuse(self):
        self.freeze()
        for key in ['TOKKL_VERIFICATION_ADOPTION', 'TOKKL_SCOPE_SESSION', 'MEM_GATE_CGROUP_MB', 'PATH', 'JAVA_HOME', 'TMPDIR']:
            with self.subTest(key=key), patch.dict(self.environment, {key: '/wrong'}), self.assertRaises((ValueError, OSError)):
                self.bound()
        with chdir(self.root), patch.dict(os.environ, self.environment), self.assertRaisesRegex(ValueError, 'cwd'):
            recipe.binding(self.base)
        with chdir(self.project), patch.dict(os.environ, self.environment), patch('scope_completion.process_identity', return_value={'cgroup': '/other'}), self.assertRaisesRegex(ValueError, 'registered scope'):
            recipe.binding(self.base)
        result = subprocess.run(['/usr/bin/python3', '-B', str(self.base/'check_and_repro.py'), '--execute'],
                                env={'PATH': '/usr/bin:/bin'}, capture_output=True, text=True)
        self.assertEqual(result.returncode, 125)
        self.assertIn('requires coordinator adoption', result.stderr)
        self.assertFalse((self.project/'calls.jsonl').exists())

    def test_actual_controller_keeps_one_1800_timeout_gate_capture_and_certified_cleanup(self):
        f = WorkloadFixture(self.root/'controller')
        self.assertEqual(f.run(), (True, True))  # Existing scoped API36 shape remains accepted.
        for status in [0, 7, 9, 124]:
            with self.subTest(status=status):
                g = WorkloadFixture(self.root/('host-'+str(status)))
                g.job = [0, recipe.LABEL, recipe.argv(g.base), 1800, True]
                g.rc = status
                captures = []
                actual_capture = g.capture
                def capture(**kw):
                    captures.append(len(captures))
                    return actual_capture(**kw)
                g.ns['capture'] = capture
                self.assertEqual(g.run(), (status == 0, True))
                record = g.ns['state']['tests'][0]
                cmd = record['command']
                self.assertEqual(cmd.count('timeout'), 1)
                self.assertEqual(cmd.count('1800s'), 1)
                self.assertEqual(cmd.count(cfg.CANONICAL_GATE), 1)
                self.assertIn('MEM_GATE_CGROUP_MB=5000', cmd)
                self.assertIn('MEM_GATE_WAIT_S=300', cmd)
                self.assertIn('OOMPolicy=continue', cmd)
                self.assertEqual(cmd[-4:], recipe.argv(g.base))
                self.assertGreaterEqual(len(captures), 3)
                self.assertEqual(record['exit'], status)
                self.assertTrue((g.session.path/'completed.json').is_file())
                self.assertEqual(g.identity_rows, {})
                self.assertFalse(g.work.exists())
                self.assertFalse(g.tmp.exists())

    def test_monitor_failure_and_unknown_disappearance_never_certify_grouped_success(self):
        for scenario in ['owned-high', 'owned-oom', 'missing-final']:
            with self.subTest(scenario=scenario):
                f = WorkloadFixture(self.root/scenario, scenario)
                f.job = [0, recipe.LABEL, recipe.argv(f.base), 1800, True]
                self.assertEqual(f.run(), (False, False))
                self.assertTrue(f.ns['state']['tests'][0]['stop_reason'])

    def test_controller_monitor_accounting_fallback_and_cleanup_sources_unchanged(self):
        paths = ['controller.py', 'scope_completion.py', 'event_monitor_0274.py', 'duration_policy.py',
                 'preparation_policy.py', 'local-reclaim-finite.py', 'controller_cleanup.py',
                 'planned-watchdog.py', 'watchdog_lifetime.py', 'window-rollback.py', 'shim/adb', 'shim/systemd-run']
        for name in paths:
            original = subprocess.check_output(['git', 'show', '2c9192c:scripts/device_verification/runtime/'+name], cwd=PACKAGE)
            self.assertEqual((PACKAGE/'runtime'/name).read_bytes(), original, name)

    def test_bound_main_runs_both_tiny_children_with_adoption_and_owned_scope_modeled(self):
        self.freeze()
        with chdir(self.project), patch.dict(os.environ, self.environment), \
             patch('scope_completion.process_identity', return_value={'cgroup': self.scope}), \
             patch.object(recipe, '__file__', str(self.base/'check_and_repro.py')), \
             patch.object(sys, 'argv', [str(self.base/'check_and_repro.py'), '--execute']):
            self.assertEqual(recipe.main(), 0)
        receipt = cfg.load(self.session/'check-and-repro.json')
        self.assertEqual([row['returncode'] for row in receipt['commands']], [0, 0])
        self.assertEqual(receipt['exit'], 0)

    def test_tiny_actual_outer_timeout_leaves_incomplete_first_and_never_starts_second(self):
        # One second tests GNU timeout's process-group behavior; the maintained
        # controller canary separately pins its sole production deadline to1800.
        payload = self.project/'gradlew'
        payload.write_text('#!/usr/bin/python3\nimport os,signal\nfrom pathlib import Path\nPath("tiny-pid").write_text(str(os.getpid()))\nsignal.pause()\n')
        session = self.root/'timed'
        session.mkdir()
        probe = self.root/'probe.py'
        probe.write_text('import sys\nfrom pathlib import Path\nsys.path.insert(0, '+repr(str(PACKAGE/'runtime'))+')\nfrom check_and_repro import run_sequence\nsys.exit(run_sequence(Path('+repr(str(session))+')))\n')
        result = subprocess.run(['timeout', '--signal=TERM', '--kill-after=1s', '1s',
                                 '/usr/bin/python3', '-B', str(probe)], cwd=self.project,
                                 capture_output=True, text=True)
        self.assertEqual(result.returncode, 124)
        receipt = cfg.load(session/'check-and-repro.json')
        self.assertIsNone(receipt['exit'])
        self.assertEqual(receipt['commands'][0]['status'], 'running')
        self.assertIsNone(receipt['commands'][0]['returncode'])
        self.assertEqual(receipt['commands'][1]['status'], 'not_run')
        self.assertFalse((self.project/'calls.jsonl').exists())
        from scope_completion import process_identity
        identity = process_identity(int((self.project/'tiny-pid').read_text()))
        self.assertTrue(identity is None or identity['state'] == 'Z', identity)

    def test_actual_monitor_checks_descendant_events_through_grouped_job_envelope(self):
        f = WorkloadFixture(self.root/'descendant')
        f.job = [0, recipe.LABEL, recipe.argv(f.base), 1800, True]
        actual_capture = f.capture
        observed = []
        def capture(**kw):
            tree = actual_capture(**kw)
            if f.session is not None and f.identity_rows:
                scope = f.app/f.session.main_unit
                child = scope/'tiny-descendant'
                child.mkdir(exist_ok=True)
                row = copy.deepcopy(tree['rows'][str(scope)])
                row['inode'] = child.stat().st_ino
                row['events']['high'] = row['local']['high'] = 1
                tree['rows'][str(child)] = row
                for path in [f.cg, f.app, scope]:
                    tree['rows'][str(path)]['events']['high'] = 1
                observed.append(str(child))
            return tree
        from capture_evidence import CaptureEvidence
        f.ns['capture'] = CaptureEvidence(capture, f.base/'descendant-failure-evidence')
        self.assertEqual(f.run(), (False, False))
        self.assertTrue(observed)
        record = f.ns['state']['tests'][0]
        self.assertTrue(record['stop_reason'])
        samples = [json.loads(line) for line in Path(record['telemetry']).read_text().splitlines()]
        self.assertTrue(any(path in sample['event_tree']['rows'] for path in observed for sample in samples if sample['event_tree']))
        self.assertFalse((f.session.path/'completed.json').exists())

    def test_one_session_captures_both_child_intervals_without_monitor_restart(self):
        import threading
        from capture_evidence import CaptureEvidence
        f = WorkloadFixture(self.root/'continuous')
        f.job = [0, recipe.LABEL, recipe.argv(f.base), 1800, True]
        started = [threading.Event(), threading.Event()]
        release = [threading.Event(), threading.Event()]
        done = threading.Event()
        outcomes = []
        failures = []
        threads = []
        actual_launch, actual_capture = f.launch, f.capture
        child_index = []
        def child(command, **kw):
            index = len(child_index)
            child_index.append(command)
            started[index].set()
            if not release[index].wait(5):
                raise OSError('fixture monitor did not capture child interval')
            return subprocess.CompletedProcess(command, 0)
        def worker():
            try:
                outcomes.append(recipe.run_sequence(f.session.path))
            except BaseException as error:
                failures.append(error)
            finally:
                done.set()
        ready = []
        class Process:
            pid = f.pid
            polls = 0
            def poll(self):
                index = self.polls
                self.polls += 1
                if index < 2:
                    if not started[index].wait(5):
                        raise AssertionError('fixture child did not start')
                    return None
                if not done.wait(5):
                    raise AssertionError('fixture sequence did not finish')
                if index > 2:
                    return outcomes[0]
                self_ready = ready[0]
                self_ready['ready_monotonic'] = f.session.capture()['monotonic']
                self_ready['payload_ended_monotonic'] = self_ready['ready_monotonic']
                write(f.session.path/'scopes'/f.session.main_unit/'ready.json', self_ready)
                return None  # Holder remains for a fresh final monitor capture.
            def wait(self, **kw):
                return f.wait(**kw)
        def launch(*args, **kw):
            actual_launch(*args, **kw)
            path = f.session.path/'scopes'/f.session.main_unit/'ready.json'
            ready.append(cfg.load(path))
            path.unlink()
            thread = threading.Thread(target=worker)
            threads.append(thread)
            thread.start()
            return Process()
        observed = []
        def capture(**kw):
            tree = actual_capture(**kw)
            if f.session is not None and (f.session.path/'check-and-repro.json').exists():
                receipt = cfg.load(f.session.path/'check-and-repro.json')
                for index, row in enumerate(receipt['commands']):
                    if row['status'] == 'running':
                        observed.append(row['label'])
                        tree['host_child_status'] = row['label']
                        release[index].set()
            return tree
        f.ns['capture'] = CaptureEvidence(capture, f.base/'continuous-failure-evidence')
        f.ns['subprocess'].Popen = launch
        try:
            with patch.object(recipe.subprocess, 'run', side_effect=child):
                self.assertEqual(f.run(), (True, True))
        finally:
            for event in release:
                event.set()
            for thread in threads:
                thread.join(5)
                self.assertFalse(thread.is_alive())
        self.assertEqual(failures, [])
        self.assertEqual(outcomes, [0])
        self.assertEqual(observed, ['assemble-check', 'reproducible-release'])
        self.assertEqual(child_index, [list(command) for _, command in recipe.COMMANDS])
        record = f.ns['state']['tests'][0]
        samples = [json.loads(line) for line in Path(record['telemetry']).read_text().splitlines()]
        self.assertEqual([row['event_tree']['host_child_status'] for row in samples if 'host_child_status' in row['event_tree']], observed)
        self.assertTrue((f.session.path/'completed.json').exists())
        self.assertEqual(f.identity_rows, {})
