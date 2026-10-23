"""Actual preparation function and helper binding tests; no reclaim writes."""
from pathlib import Path
import ast
import copy
import json
import os
import builtins
import runpy
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

from test_candidate import config, PACKAGE, SCRATCH
from runtime import configuration as cfg
from prepare import prepare as snapshot
import preparation_policy as policy

GIB = 1024**3
REQUEST = 3798306816
ORIGINAL = {'memory.high': str(10*GIB), 'memory.max': str(12*GIB), 'memory.swap.max': str(2*GIB)}
TEMPORARY = dict(ORIGINAL, **{'memory.high': str(14*GIB), 'memory.max': str(15*GIB)})


def root_config(directory):
    value = config(directory)
    value.update(reclaim_budget_bytes=6*GIB, preparation_policy={'max_request_bytes': 4*GIB, 'max_preparations': 4})
    return value


class ActualPreparationTests(unittest.TestCase):
    def execute(self, directory, cap, *, reserved=0, inactive=8*GIB, repeat=False, count=0,
                request=REQUEST, max_preparations=4, budget=6*GIB):
        node = next(n for n in ast.parse((PACKAGE/'runtime/controller.py').read_text()).body
                    if isinstance(n, ast.FunctionDef) and n.name == 'prepare')
        before = {'limits': ORIGINAL, 'current': 6*GIB+request-134217728, 'host_available': 12*GIB,
                  'stat': {'inactive_file': inactive, 'shmem': 0}, 'events': dict.fromkeys(['high', 'max', 'oom', 'oom_kill'], 0), 'swap': 0}
        after = copy.deepcopy(before)
        after['current'] = 6*GIB
        frames = iter([before, after])
        writes = []
        state = {'preparations': [{'label': 'full36' if repeat else 'earlier-'+str(i)} for i in range(count)],
                 'prepared_labels': [], 'reclaim_budget_bytes': budget, 'reclaim_reserved_bytes': reserved}
        ns = {'preparation_snapshot': lambda: next(frames), 'original': ORIGINAL, 'temporary': TEMPORARY,
              'config': {'preparation_policy': {'max_request_bytes': cap, 'max_preparations': max_preparations}},
              'state': state, 'time': types.SimpleNamespace(time=lambda: 1), 'os': types.SimpleNamespace(environ={}),
              'save': lambda: None, 'base': directory, 'project': directory, 'tag': 'fixture',
              'state_path': directory/'window-state-fixture.json', 'json': json,
              'subprocess': types.SimpleNamespace(STDOUT=-2, run=lambda command, **kw: writes.append(command) or types.SimpleNamespace(returncode=0))}
        exec(compile(ast.Module(body=[node], type_ignores=[]), '<actual-maintained-prepare>', 'exec'), ns)
        result = ns['prepare']('full36', 8192, opening=True)
        return result, writes, state

    def test_original_two_gib_refusal_and_explicit_four_gib_positive(self):
        with tempfile.TemporaryDirectory(dir=SCRATCH) as tmp:
            directory = Path(tmp)
            success, writes, state = self.execute(directory, 2*GIB)
            self.assertFalse(success)
            self.assertFalse(writes)
            success, writes, state = self.execute(directory, 4*GIB)
            self.assertTrue(success)
            self.assertEqual(len(writes), 1)
            self.assertEqual(state['reclaim_reserved_bytes'], REQUEST)
            self.assertEqual(writes[0][-4:], ['--state', str(directory/'window-state-fixture.json'), '--preparation', '0'])

    def test_default_disabled_cumulative_inactive_count_and_repeated_job_refuse(self):
        with tempfile.TemporaryDirectory(dir=SCRATCH) as tmp:
            for changes in [{'cap': 0}, {'cap': 4*GIB, 'reserved': 3*GIB},
                            {'cap': 4*GIB, 'inactive': REQUEST-1}, {'cap': 4*GIB, 'count': 4},
                            {'cap': 4*GIB, 'count': 1, 'repeat': True}]:
                success, writes, state = self.execute(Path(tmp), **changes)
                self.assertFalse(success)
                self.assertFalse(writes)


class HelperBindingTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.directory = Path(self.tmp.name)
        self.config = root_config(self.directory)
        proof = snapshot(self.config)
        self.run = Path(proof['run_dir'])
        self.adoption = self.directory/'MODELED-adoption.json'
        self.adoption.write_text(json.dumps(dict(configuration_sha256=proof['configuration_sha256'],
            runtime_sha256=proof['runtime_sha256'], lease_id=self.config['lease_id'], approved_by='HOST-FIXTURE-ONLY',
            cheap_live_controller_fixtures_verified=True)))
        self.state_path = self.run/'window-state-fixture.json'
        self.argv = [str(REQUEST), '--state', str(self.state_path), '--preparation', '0']
        command = ['/usr/bin/python3', str(self.run/'local-reclaim-finite.py')]+self.argv
        self.state = {'phase': 'prepared', 'configuration_sha256': proof['configuration_sha256'],
            'project': self.config['project'], 'run_id': self.config['run_id'], 'lease_id': self.config['lease_id'],
            'reclaim_budget_bytes': 6*GIB, 'reclaim_reserved_bytes': REQUEST,
            'preparations': [{'label': 'full36', 'write_count': 1, 'requested': REQUEST,
                'deficit': REQUEST-134217728, 'before': {'stat': {'inactive_file': 5*GIB}}, 'command': command}]}
        self.state_path.write_text(json.dumps(self.state))
        self.env = patch.dict(os.environ, TOKKL_VERIFICATION_ADOPTION=str(self.adoption))
        self.env.start()

    def tearDown(self):
        self.env.stop()
        self.tmp.cleanup()

    def test_bound_request_and_durable_one_write_intent_without_sysfs_access(self):
        request = policy.helper_request(self.run, self.argv)
        self.assertEqual(request['amount'], REQUEST)
        # Reserving the intent does not open memory.reclaim. Replays fail closed.
        policy.reserve_write(request)
        with self.assertRaises(FileExistsError):
            policy.reserve_write(request)
        self.assertTrue(request['intent_path'].exists())

    def test_project_label_argv_cumulative_and_inactive_tamper_refuse(self):
        for change in ['project', 'label', 'command', 'budget', 'inactive', 'refund']:
            state = copy.deepcopy(self.state)
            if change == 'project': state['project'] = '/other/project'
            if change == 'label': state['preparations'][0]['label'] = 'different'
            if change == 'command': state['preparations'][0]['command'][1] = '/other/helper.py'
            if change == 'budget': state['reclaim_budget_bytes'] = 8*GIB
            if change == 'inactive': state['preparations'][0]['before']['stat']['inactive_file'] = REQUEST-1
            if change == 'refund': state['reclaim_reserved_bytes'] -= 1
            self.state_path.write_text(json.dumps(state))
            with self.subTest(change=change), self.assertRaises(ValueError):
                policy.helper_request(self.run, self.argv)
        self.assertFalse((self.run/'preparation-write-0.json').exists())

    def test_config_and_lease_tamper_invalidates_root_adoption(self):
        changed = copy.deepcopy(self.config)
        changed['preparation_policy']['max_request_bytes'] = 3*GIB
        (self.run/'run-config.json').write_text(json.dumps(changed))
        with self.assertRaisesRegex(ValueError, 'coordinator adoption'):
            policy.helper_request(self.run, self.argv)
        (self.run/'run-config.json').write_text(json.dumps(self.config))
        adoption = json.loads(self.adoption.read_text())
        adoption['lease_id'] = 'another-lease'
        self.adoption.write_text(json.dumps(adoption))
        with self.assertRaisesRegex(ValueError, 'coordinator adoption'):
            policy.helper_request(self.run, self.argv)

    def test_helper_default_and_raw_write_without_grant_refuse_before_resource_actions(self):
        helper = self.run/'local-reclaim-finite.py'
        for args in [[], ['--write', '1'], ['1']]:
            result = subprocess.run([sys.executable, '-B', str(helper)]+args, capture_output=True, text=True, timeout=3,
                                    env={k: v for k, v in os.environ.items() if k != 'TOKKL_VERIFICATION_ADOPTION'})
            self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.run/'preparation-write-0.json').exists())

    def test_request_policy_rejects_invalid_caps_and_plan_counts(self):
        for policy_value in [{'max_request_bytes': self.config['reclaim_budget_bytes']+1, 'max_preparations': 4},
                             {'max_request_bytes': True, 'max_preparations': 4},
                             {'max_request_bytes': 4*GIB, 'max_preparations': 5},
                             {'max_request_bytes': 0, 'max_preparations': 4}]:
            candidate = dict(self.config, preparation_policy=policy_value)
            with self.subTest(policy=policy_value), self.assertRaises(ValueError):
                cfg.validate(candidate)


class RemainingBudgetTests(unittest.TestCase):
    REQUEST = 4352847872

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.directory = Path(self.tmp.name)
        self.config = root_config(self.directory)
        # One previous preparation consumed, zero reserved bytes. Three slots
        # remain. This is host evidence, never a production continuation grant.
        prior = self.directory/'previous-host-ledger.json'
        prior.write_text(json.dumps({'reclaim_reserved_bytes': 0, 'preparations': [{'label': 'consumed'}]}))
        self.config.update(prior_window=str(prior), prior_window_sha256=cfg.sha(prior),
            preparation_policy={'max_request_bytes': self.config['reclaim_budget_bytes'], 'max_preparations': 3})

    def tearDown(self):
        self.tmp.cleanup()

    def test_original_validator_and_explicit_stricter_cap_refuse(self):
        source = subprocess.check_output(['git', 'show',
            '3749d40:scripts/device_verification/runtime/configuration.py'], cwd=PACKAGE, text=True)
        ns = {'__name__': 'original_configuration_0294', '__package__': None}
        exec(compile(source, '<original-0294-configuration>', 'exec'), ns)
        with self.assertRaisesRegex(ValueError, 'at most 4 GiB'):
            ns['validate'](self.config)
        success, writes, state = ActualPreparationTests.execute(self, self.directory, 4*GIB, request=self.REQUEST)
        self.assertFalse(success)
        self.assertFalse(writes)
        self.assertEqual(state['reclaim_reserved_bytes'], 0)
        self.assertEqual(cfg.validate(self.config)['preparation_policy']['max_request_bytes'], 6*GIB)

    def test_above_four_gib_actual_controller_parent_writer_and_durable_once(self):
        checked = cfg.validate(self.config)
        proof = snapshot(self.config)
        run = Path(proof['run_dir'])
        adoption = self.directory/'HOST-ONLY-adoption.json'
        adoption.write_text(json.dumps(dict(configuration_sha256=proof['configuration_sha256'],
            runtime_sha256=proof['runtime_sha256'], lease_id=self.config['lease_id'],
            approved_by='HOST-MODEL-ONLY', cheap_live_controller_fixtures_verified=True)))
        success, calls, state = ActualPreparationTests.execute(self, run,
            checked['preparation_policy']['max_request_bytes'], request=self.REQUEST, max_preparations=3)
        self.assertTrue(success)
        self.assertEqual(len(calls), 1)
        self.assertEqual(state['reclaim_reserved_bytes'], self.REQUEST)
        self.assertEqual(state['preparations'][0]['requested'], state['preparations'][0]['deficit']+134217728)
        state.update(phase='prepared', project=checked['project'], configuration_sha256=checked['_sha256'],
                     run_id=checked['run_id'], lease_id=checked['lease_id'])
        state_path = run/'window-state-fixture.json'
        state_path.write_text(json.dumps(state))
        argv = calls[0][2:]
        self.assertEqual(argv, [str(self.REQUEST), '--state', str(state_path), '--preparation', '0'])
        class Boundary(Exception): pass
        original_open, original_os_open = builtins.open, os.open
        reached = []
        def stop_lock(path, *args, **kw):
            if str(path) == '/run/user/1000/tokkl-android-mem-gate.lock':
                reached.append('parent-before-lock');raise Boundary()
            return original_open(path, *args, **kw)
        def stop_reclaim(path, flags, *args, **kw):
            if str(path).startswith('/sys/'):
                self.assertTrue(str(path).endswith('/memory.reclaim'))
                reached.append('writer-after-durable-intent');raise Boundary()
            if flags & os.O_DIRECT:
                # The shared validator now opens every pin for read-only DIO.
                self.assertEqual(flags & os.O_ACCMODE, os.O_RDONLY)
                self.assertFalse(flags & (os.O_CREAT | os.O_TRUNC))
                return original_os_open(path, flags, *args, **kw)
            self.assertEqual(Path(path), run)  # Actual reserve_write directory fsync.
            return original_os_open(path, flags, *args, **kw)
        helper = run/'local-reclaim-finite.py'
        with patch.dict(os.environ, TOKKL_VERIFICATION_ADOPTION=str(adoption)):
            for write in [False, True]:
                request = policy.helper_request(run, (['--write'] if write else [])+argv)
                self.assertEqual(request['amount'], self.REQUEST)
                self.assertEqual(request['write'], write)
                with patch.object(sys, 'argv', [str(helper)]+(['--write'] if write else [])+argv), \
                     patch('builtins.open', stop_lock), patch('os.open', stop_reclaim), self.assertRaises(Boundary):
                    runpy.run_path(str(helper), run_name='__main__')
            intent = run/'preparation-write-0.json'
            self.assertEqual(json.loads(intent.read_text())['amount'], self.REQUEST)
            with patch.object(sys, 'argv', [str(helper), '--write']+argv), \
                 patch('os.open', stop_reclaim), self.assertRaises(FileExistsError):
                runpy.run_path(str(helper), run_name='__main__')
            original_state = copy.deepcopy(state)
            for change in ['budget', 'inactive']:
                state = copy.deepcopy(original_state)
                if change == 'budget': state['reclaim_reserved_bytes'] = state['reclaim_budget_bytes']+1
                if change == 'inactive': state['preparations'][0]['before']['stat']['inactive_file'] = self.REQUEST-1
                state_path.write_text(json.dumps(state))
                for prefix in [[], ['--write']]:
                    with self.subTest(change=change, writer=bool(prefix)), self.assertRaises(ValueError):
                        policy.helper_request(run, prefix+argv)
            state = original_state;state_path.write_text(json.dumps(state))
            bad = json.loads(adoption.read_text());bad['lease_id'] = 'tampered-lease'
            adoption.write_text(json.dumps(bad))
            for prefix in [[], ['--write']]:
                with self.assertRaisesRegex(ValueError, 'coordinator adoption'):
                    policy.helper_request(run, prefix+argv)
        self.assertEqual(reached, ['parent-before-lock', 'writer-after-durable-intent'])
        print('ABOVE_4G_HOST '+json.dumps(dict(request_bytes=self.REQUEST,
            configuration_sha256=proof['configuration_sha256'], runtime_sha256=proof['runtime_sha256'],
            controller_reserved_bytes=state['reclaim_reserved_bytes'], paths=reached,
            actual_reclaim=False, max_preparations=checked['preparation_policy']['max_preparations'])), flush=True)

    def test_nine_gib_remaining_controller_budget_cache_and_local_slots(self):
        carry = 6191243264; remaining = 9*GIB-carry
        args = dict(cap=9*GIB, request=remaining, reserved=carry, budget=9*GIB, max_preparations=2)
        success, calls, state = ActualPreparationTests.execute(self, self.directory, **args)
        self.assertTrue(success);self.assertEqual(len(calls), 1)
        self.assertEqual(state['reclaim_reserved_bytes'], 9*GIB)
        self.assertEqual(state['preparations'][0]['requested'], remaining)
        self.assertEqual(state['preparations'][0]['target_current'], 6*GIB)
        self.assertEqual(state['preparations'][0]['host_floor'], 9*GIB)
        for change in [{'request': remaining+1}, {'inactive': remaining-1}, {'cap': remaining-1},
                       {'count': 2}, {'count': 1, 'repeat': True}]:
            bad = dict(args, **change)
            with self.subTest(change=change):
                success, calls, state = ActualPreparationTests.execute(self, self.directory, **bad)
                self.assertFalse(success);self.assertFalse(calls)
                self.assertEqual(state['reclaim_reserved_bytes'], carry)

    def test_nine_gib_actual_parent_writer_binding_and_replay_refusals(self):
        self.assert_carried_parent_writer_binding_and_replay_refusals(9*GIB, 6191243264, 5)

    def test_twelve_gib_remaining_controller_whole_request_cache_and_slots(self):
        carry = 9289646080; remaining = 12*GIB-carry
        self.assertEqual(remaining, 3595255808)
        args = dict(cap=12*GIB, request=remaining, reserved=carry, budget=12*GIB, max_preparations=2)
        success, calls, state = ActualPreparationTests.execute(self, self.directory, **args)
        self.assertTrue(success);self.assertEqual(len(calls), 1)
        self.assertEqual(state['reclaim_reserved_bytes'], 12884901888)
        self.assertEqual(state['preparations'][0]['requested'], remaining)
        self.assertEqual(state['preparations'][0]['target_current'], 6*GIB)
        self.assertEqual(state['preparations'][0]['host_floor'], 9*GIB)
        # The recorded fresh need fits without splitting or discarding carry.
        success, calls, state = ActualPreparationTests.execute(self, self.directory,
            **dict(args, request=981966848, inactive=981966848, count=1))
        self.assertTrue(success);self.assertEqual(len(calls), 1)
        self.assertEqual(state['reclaim_reserved_bytes'], 10271612928)
        for change in [{'request': remaining+1}, {'inactive': remaining-1}, {'cap': remaining-1},
                       {'cap': 0}, {'count': 2}, {'count': 1, 'repeat': True}]:
            with self.subTest(change=change):
                success, calls, state = ActualPreparationTests.execute(self, self.directory, **dict(args, **change))
                self.assertFalse(success);self.assertFalse(calls)
                self.assertEqual(state['reclaim_reserved_bytes'], carry)

    def test_twelve_gib_actual_parent_writer_binding_and_replay_refusals(self):
        self.assert_carried_parent_writer_binding_and_replay_refusals(12*GIB, 9289646080, 8)

    def test_sixteen_gib_remaining_whole_request_cache_slots_and_fresh_need(self):
        carry = 11858051072; remaining = 16*GIB-carry
        self.assertEqual(remaining, 5321818112)
        args = dict(cap=16*GIB, request=remaining, reserved=carry, budget=16*GIB, max_preparations=3)
        success, calls, state = ActualPreparationTests.execute(self, self.directory, **args)
        self.assertTrue(success);self.assertEqual(len(calls), 1)
        self.assertEqual(state['reclaim_reserved_bytes'], 17179869184)
        self.assertEqual(state['preparations'][0]['requested'], remaining)
        self.assertEqual(state['preparations'][0]['target_current'], 6*GIB)
        self.assertEqual(state['preparations'][0]['host_floor'], 9*GIB)
        success, calls, state = ActualPreparationTests.execute(self, self.directory,
            **dict(args, request=1340342272, inactive=4556406784, count=2))
        self.assertTrue(success);self.assertEqual(len(calls), 1)
        self.assertEqual(state['reclaim_reserved_bytes'], 13198393344)
        for change in [{'request': remaining+1}, {'inactive': remaining-1}, {'cap': remaining-1},
                       {'cap': 0}, {'count': 3}, {'count': 1, 'repeat': True}]:
            with self.subTest(change=change):
                success, calls, state = ActualPreparationTests.execute(self, self.directory, **dict(args, **change))
                self.assertFalse(success);self.assertFalse(calls)
                self.assertEqual(state['reclaim_reserved_bytes'], carry)

    def test_sixteen_gib_parent_writer_binding_and_replay_refusals(self):
        self.assert_carried_parent_writer_binding_and_replay_refusals(16*GIB, 11858051072, 11, allowances=3)

    def assert_carried_parent_writer_binding_and_replay_refusals(self, budget, carry, history, allowances=2):
        amount = budget-carry
        prior = Path(self.config['prior_window'])
        prior.write_text(json.dumps({'reclaim_reserved_bytes': carry,
                                    'preparations': [{'label': 'historical-'+str(i)} for i in range(history)]}))
        self.config.update(reclaim_budget_bytes=budget, carried_reclaim_bytes=carry,
            prior_window_sha256=cfg.sha(prior), preparation_policy={'max_request_bytes': budget, 'max_preparations': allowances})
        self.config['plan'].append(dict(api=0, label='assemble-check', standalone=True, seconds=1800,
            args=['./gradlew', '--no-daemon', 'assembleDebug', 'check', '--stacktrace']))
        if allowances == 3:
            self.config['plan'].append(dict(api=0, label='reproducible-release', standalone=True, seconds=1800,
                args=['env', 'GRADLE_OPTS=-Dorg.gradle.workers.max=1', 'scripts/reproducible-release.sh']))
        proof = snapshot(self.config);run = Path(proof['run_dir'])
        adoption = self.directory/'HOST-ONLY-carried-budget-adoption.json'
        adoption.write_text(json.dumps(dict(configuration_sha256=proof['configuration_sha256'],
            runtime_sha256=proof['runtime_sha256'], lease_id=self.config['lease_id'],
            approved_by='HOST-MODEL-ONLY', cheap_live_controller_fixtures_verified=True)))
        success, calls, state = ActualPreparationTests.execute(self, run, budget, reserved=carry,
            request=amount, budget=budget, max_preparations=allowances)
        self.assertTrue(success)
        state.update(phase='prepared', project=self.config['project'],
                     configuration_sha256=proof['configuration_sha256'], run_id=self.config['run_id'], lease_id=self.config['lease_id'])
        state_path = run/'window-state-fixture.json';state_path.write_text(json.dumps(state));argv = calls[0][2:]
        class Boundary(Exception): pass
        original_open, original_os_open = builtins.open, os.open
        reached = []
        def stop_lock(path, *args, **kw):
            if str(path) == '/run/user/1000/tokkl-android-mem-gate.lock':
                reached.append('parent-before-lock');raise Boundary()
            return original_open(path, *args, **kw)
        def stop_reclaim(path, flags, *args, **kw):
            if str(path).startswith('/sys/'):
                self.assertTrue(str(path).endswith('/memory.reclaim'))
                reached.append('writer-after-durable-intent');raise Boundary()
            if flags & os.O_DIRECT:
                self.assertEqual(flags & os.O_ACCMODE, os.O_RDONLY)
                self.assertFalse(flags & (os.O_CREAT | os.O_TRUNC))
            else:self.assertEqual(Path(path), run)
            return original_os_open(path, flags, *args, **kw)
        helper = run/'local-reclaim-finite.py'
        with patch.dict(os.environ, TOKKL_VERIFICATION_ADOPTION=str(adoption)):
            for prefix in [[], ['--write']]:
                self.assertEqual(policy.helper_request(run, prefix+argv)['amount'], amount)
                with patch.object(sys, 'argv', [str(helper)]+prefix+argv), patch('builtins.open', stop_lock), \
                     patch('os.open', stop_reclaim), self.assertRaises(Boundary):
                    runpy.run_path(str(helper), run_name='__main__')
            intent = run/'preparation-write-0.json';self.assertEqual(json.loads(intent.read_text())['amount'], amount)
            with self.assertRaises(FileExistsError):policy.reserve_write(policy.helper_request(run, ['--write']+argv))
            for mode in ['overbudget', 'refund', 'reset', 'inactive', 'deficit', 'label', 'command', 'replay']:
                bad = copy.deepcopy(state);row = bad['preparations'][0]
                if mode=='overbudget':bad['reclaim_reserved_bytes'] = budget+1
                if mode=='refund':bad['reclaim_reserved_bytes'] -= 1
                if mode=='reset':bad['reclaim_reserved_bytes'] = amount
                if mode=='inactive':row['before']['stat']['inactive_file'] = amount-1
                if mode=='deficit':row['deficit'] -= 1
                if mode=='label':row['label'] = 'other'
                if mode=='command':row['command'][1] = '/other/helper.py'
                if mode=='replay':bad['preparations'].append(copy.deepcopy(row))
                state_path.write_text(json.dumps(bad))
                for prefix in [[], ['--write']]:
                    with self.subTest(mode=mode, writer=bool(prefix)), self.assertRaises(ValueError):
                        policy.helper_request(run, prefix+argv)
        self.assertEqual(reached, ['parent-before-lock', 'writer-after-durable-intent'])

    def test_remaining_budget_cache_slots_and_invalid_grants_still_refuse(self):
        self.assertTrue(cfg.validate(self.config))
        for change in [{'reserved': 2*GIB}, {'inactive': self.REQUEST-1}, {'cap': 0},
                       {'count': 3}, {'count': 1, 'repeat': True}]:
            args = dict(cap=6*GIB, request=self.REQUEST, max_preparations=3);args.update(change)
            success, calls, state = ActualPreparationTests.execute(self, self.directory, **args)
            self.assertFalse(success)
            self.assertFalse(calls)
        for amount in [-1, True, 6*GIB+1]:
            bad = copy.deepcopy(self.config);bad['preparation_policy']['max_request_bytes'] = amount
            with self.subTest(amount=amount), self.assertRaises(ValueError): cfg.validate(bad)
        bad = dict(self.config, reclaim_budget_bytes=-1)
        with self.assertRaises(ValueError): cfg.validate(bad)
        bad = dict(self.config, reclaim_budget_bytes=0)
        with self.assertRaises(ValueError): cfg.validate(bad)


if __name__ == '__main__':
    unittest.main(verbosity=2)
