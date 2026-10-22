"""Actual preparation function and helper binding tests; no reclaim writes."""
from pathlib import Path
import ast
import copy
import json
import os
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
    def execute(self, directory, cap, *, reserved=0, inactive=8*GIB, repeat=False, count=0):
        node = next(n for n in ast.parse((PACKAGE/'runtime/controller.py').read_text()).body
                    if isinstance(n, ast.FunctionDef) and n.name == 'prepare')
        before = {'limits': ORIGINAL, 'current': 6*GIB+REQUEST-134217728, 'host_available': 12*GIB,
                  'stat': {'inactive_file': inactive, 'shmem': 0}, 'events': dict.fromkeys(['high', 'max', 'oom', 'oom_kill'], 0), 'swap': 0}
        after = copy.deepcopy(before)
        after['current'] = 6*GIB
        frames = iter([before, after])
        writes = []
        state = {'preparations': [{'label': 'full36' if repeat else 'earlier-'+str(i)} for i in range(count)],
                 'prepared_labels': [], 'reclaim_budget_bytes': 6*GIB, 'reclaim_reserved_bytes': reserved}
        ns = {'preparation_snapshot': lambda: next(frames), 'original': ORIGINAL, 'temporary': TEMPORARY,
              'config': {'preparation_policy': {'max_request_bytes': cap, 'max_preparations': 4}},
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
        for policy_value in [{'max_request_bytes': 4*GIB+1, 'max_preparations': 4},
                             {'max_request_bytes': True, 'max_preparations': 4},
                             {'max_request_bytes': 4*GIB, 'max_preparations': 5},
                             {'max_request_bytes': 0, 'max_preparations': 4}]:
            candidate = dict(self.config, preparation_policy=policy_value)
            with self.subTest(policy=policy_value), self.assertRaises(ValueError):
                cfg.validate(candidate)


if __name__ == '__main__':
    unittest.main(verbosity=2)
