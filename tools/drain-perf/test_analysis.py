#!/usr/bin/env python3
"""Small deterministic checks for attribution and cost arithmetic, no Android tools."""
import importlib.util
import gzip
import json
import pathlib
import tempfile
import unittest

from rebuild import execution, parse_transcripts, seconds, union

spec = importlib.util.spec_from_file_location('logger', __import__('pathlib').Path(__file__).parent / 'proposals/drain-log-v2.py')
logger = importlib.util.module_from_spec(spec)
spec.loader.exec_module(logger)


def event(group, kind, second, **kwargs):
    return dict(group=group, event=kind, monotonic_ns=int(second * 1e9), boot_id='same', **kwargs)


class TimingTests(unittest.TestCase):
    def test_background_return_is_not_process_completion(self):
        base = dict(source='original.jsonl', source_line=1, role='assistant')
        rows = [dict(base, timestamp='2026-10-19T00:00:00Z', blocks=[
            dict(type='tool_use', id='one', name='Bash', input={'command': 'scripts/instrumented-tests.sh'})]),
            dict(base, timestamp='2026-10-19T00:00:00.050Z', blocks=[
                dict(type='tool_result', id='one', chars=50, lines=1, evidence=[
                    dict(line=1, text='Command running in background with ID: task')])]),
            dict(base, timestamp='2026-10-19T00:02:00Z', role='notification', blocks=[
                dict(type='text', text='<tool-use-id>one</tool-use-id>', chars=0)])]
        with tempfile.TemporaryDirectory(dir=pathlib.Path(__file__).parent / 'generated') as temp:
            root = pathlib.Path(temp)
            target = root / 'transcripts/session/agent-one.jsonl.gz'
            target.parent.mkdir(parents=True)
            with gzip.open(target, 'wt') as f:
                for row in rows:
                    f.write(json.dumps(row) + '\n')
            _, calls, _ = parse_transcripts(root)
        self.assertEqual(calls[0]['duration_s'], .05)
        self.assertEqual(calls[0]['process_duration_s'], 120)
        self.assertEqual(calls[0]['completion_source'], 'task_notification')

    def test_gradle_units(self):
        self.assertEqual(seconds('1h 2m 3s'), 3723)
        self.assertEqual(seconds('24m 1s'), 1441)

    def test_union_does_not_double_count_parallel_work(self):
        self.assertEqual(union([(0, 10), (5, 12), (14, 15)]), [(0, 12), (14, 15)])

    def test_reading_a_command_does_not_execute_it(self):
        self.assertEqual(execution('cat scripts/instrumented-tests.sh'), ('', ''))
        self.assertEqual(execution("cat > /tmp/wrapper <<'EOF'\nscripts/instrumented-tests.sh\nEOF"), ('', ''))
        self.assertEqual(execution('cd work && scripts/instrumented-tests.sh > run.log')[0], 'device')
        self.assertEqual(execution('/tmp/sift-w7-h3/bin/run-suite.sh full tokkl-api30')[0], 'device')

    def test_parallel_groups_keep_their_phases(self):
        rows = [event('a', 'dispatch', 0, tickets=['TKA-0001']),
                event('b', 'dispatch', 1, tickets=['TKA-0002'])]
        for i, phase in enumerate(logger.PHASES, 2):
            rows += [event('a', 'phase', i, phase=phase), event('b', 'phase', i + .2, phase=phase)]
        rows += [event('b', 'return', 8, statuses={'TKA-0002': 'done'}),
                 event('a', 'return', 10, statuses={'TKA-0001': 'done'})]
        result = {r['group']: r for r in logger.report(rows)}
        self.assertEqual(result['a']['duration_s'], 10)
        self.assertEqual(result['b']['duration_s'], 7)
        self.assertEqual(result['a']['phases_s']['bookkeep'], 5)
        self.assertFalse(result['a']['issues'])
        self.assertFalse(result['b']['issues'])

    def test_missing_stamp_is_explicit_and_has_no_made_up_phase_time(self):
        rows = [event('a', 'dispatch', 0, tickets=['TKA-0001']),
                event('a', 'phase', 1, phase='orient'),
                event('a', 'return', 10, statuses={'TKA-0001': 'done'})]
        result = logger.report(rows)[0]
        self.assertEqual(result['duration_s'], 10)
        self.assertEqual(result['phases_s'], {})
        self.assertIn('missing, duplicate or out-of-order phase stamps', result['issues'])

    def test_reboot_does_not_mix_monotonic_clocks(self):
        rows = [event('a', 'dispatch', 20, tickets=['TKA-0001']),
                event('a', 'return', 1, statuses={'TKA-0001': 'done'})]
        rows[-1]['boot_id'] = 'next-boot'
        self.assertIsNone(logger.report(rows)[0]['duration_s'])

    def test_validation_rejects_shifted_ticket_status_pairs(self):
        with self.assertRaises(ValueError):
            logger.validate('return', ['TKA-0001'], 'TKA')
        with self.assertRaises(ValueError):
            logger.validate('dispatch', ['other'], 'TKA')


if __name__ == '__main__':
    unittest.main()
