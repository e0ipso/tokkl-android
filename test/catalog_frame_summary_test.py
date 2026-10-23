"""Synthetic host canaries only. No Android, Gradle, network or performance measurements."""
import base64
import copy
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('catalog_summary', Path(__file__).resolve().parents[1]/'scripts/catalog-frame-summary.py')
summary = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(summary)


def fixture():
    return {'schema': 'catalog-fixture-v1', 'timezone': 'Europe/Madrid',
            'tags': [{'id': i+1, 'workspace_id': 7, 'name': f'Measured tag {i:03}'} for i in range(500)],
            'trash': [{'id': i+501, 'description': f'Measured trash {i}', 'start': '2026-10-19T08:00:00Z',
                       'stop': '2026-10-19T08:05:00Z', 'duration': 300} for i in range(1000)]}


def encode(value):
    raw = json.dumps(value, separators=(',', ':')).encode()
    return base64.b64encode(gzip.compress(raw)).decode(), hashlib.sha256(raw).hexdigest()


def window():
    anchor = {'id': 7, 'index': 0, 'left': 0, 'right': 100, 'top': 20, 'bottom': 40}
    snapshot = {'anchors': [anchor], 'environment': {'refresh_hz': 60}, 'viewport': [0, 0, 100, 100]}
    gestures, touches = [], []
    for gesture in range(5):
        events = []
        for offset in range(0, 151, 10):
            action = 0 if offset == 0 else 1 if offset == 150 else 2
            event = {'action': action, 'x': 50, 'y': 80-offset*60/150, 'offset_ms': offset,
                     'event_ms': 1100+gesture*200+offset, 'accepted': True}
            events.append(event)
            touches.append({**{key: event[key] for key in ('action', 'x', 'y', 'event_ms')}, 'source': 4098, 'tool_type': 1})
        gestures.append({'events': events, 'after': copy.deepcopy(snapshot)})
    return {'complete': True, 'wall_ns': 2_000_000_000, 'marker': 'catalog/tags-first-0',
            'fixture_sha256': 'a'*64, 'compilation': 'Full', 'before': snapshot, 'after': copy.deepcopy(snapshot),
            'gestures': gestures, 'begin': {'start': {'mono_ns': 1_000_000_000, 'boot_ns': 2_000_000_000}},
            'end': {'end': {'mono_ns': 3_000_000_000, 'boot_ns': 4_000_000_000}},
            'stream': {'frames': [{'seq': 0, 'intended_vsync_ns': 1_100_000_000, 'dropped_reports': 7}], 'touches': touches}}


def tables():
    return {'loss': [{'lost': 0}], 'markers': [{'name': 'catalog/tags-first-0', 'ts': 6_000_000_000, 'dur': 2_000_000_000}],
            'clocks': [{'clock_id': 3, 'ts': 5_000_000_000, 'clock_value': 0}],
            'frames': [{'id': 1, 'ts': 6_100_000_000, 'dur': 10_000_000, 'expected_id': 11,
                        'expected_ts': 6_100_000_000, 'expected_dur': 16_000_000},
                       {'id': 2, 'ts': 7_100_000_000, 'dur': 20_000_000, 'expected_id': 12,
                        'expected_ts': 7_100_000_000, 'expected_dur': 16_000_000}],
            'expected': [{'id': 11, 'ts': 6_100_000_000}, {'id': 12, 'ts': 7_100_000_000}],
            'sched': [{'ts': 6_100_000_000, 'dur': 3_000_000}, {'ts': 7_100_000_000, 'dur': 5_000_000}],
            'runnable': [{'ts': 7_105_000_000, 'dur': 2_000_000}]}


class CatalogEvidenceTest(unittest.TestCase):
    def test_real_shape_round_trip_preserves_exact_counts_and_order(self):
        value = fixture()
        self.assertEqual(summary.decode_fixture(*encode(value)), value)

    def test_transport_rejects_hash_corruption_and_both_size_limits(self):
        encoded, digest = encode(fixture())
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            summary.decode_fixture(encoded, '0'*64)
        with self.assertRaisesRegex(ValueError, 'encoded fixture limit'):
            summary.decode_fixture('A'*65537, digest)
        bomb = b'x'*524289
        with self.assertRaisesRegex(ValueError, 'decoded fixture limit'):
            summary.decode_fixture(base64.b64encode(gzip.compress(bomb)).decode(), hashlib.sha256(bomb).hexdigest())

    def test_fixture_rejects_duplicate_ids_and_changed_server_span(self):
        value = fixture(); value['tags'][1]['id'] = value['tags'][0]['id']
        with self.assertRaisesRegex(ValueError, 'tag count/IDs'):
            summary.decode_fixture(*encode(value))
        value = fixture(); value['trash'][0]['duration'] = 1
        with self.assertRaisesRegex(ValueError, 'stop/duration'):
            summary.decode_fixture(*encode(value))

    def test_deadlines_use_two_timeline_frames_not_one_callback_or_seven_drops(self):
        result = summary.summarize(window(), tables())
        self.assertEqual((result['deadline_misses'], result['deadline_denominator']), (1, 2))
        self.assertEqual((result['delivered_callbacks'], result['dropped_reports_received']), (1, 7))
        self.assertEqual(result['overrun_ms']['p95'], 4)
        self.assertEqual(result['overrun_ms']['p50'], -6)
        self.assertEqual(result['main_plus_render_cpu_execution_ms']['p95'], 5)
        self.assertEqual(result['main_runnable_ns'], 2_000_000)
        self.assertIsNone(result['absolute_pixel_travel'])
        self.assertEqual(result['dropped_phase_attribution'], 'UNKNOWN')

    def test_boundary_frame_is_reported_and_excluded_from_deadline_denominator(self):
        t = tables()
        t['frames'].append({'id': 3, 'ts': 7_999_000_000, 'dur': 2_000_000, 'expected_id': 13,
                            'expected_ts': 7_999_000_000, 'expected_dur': 16_000_000})
        t['expected'].append({'id': 13, 'ts': 7_999_000_000})
        result = summary.summarize(window(), t)
        self.assertEqual((result['boundary_frames'], result['deadline_denominator']), (1, 2))
        self.assertTrue(result['coverage_complete'])

    def test_missing_actual_and_missing_expected_are_exposed(self):
        t = tables(); t['expected'].append({'id': 13, 'ts': 7_500_000_000})
        result = summary.summarize(window(), t)
        self.assertEqual(result['expected_without_contained_actual'], 1)
        self.assertFalse(result['coverage_complete'])
        t = tables(); t['frames'][0]['expected_id'] = ''
        self.assertEqual(summary.summarize(window(), t)['unpaired_frames'], 1)

    def test_clock_mapping_is_required_and_not_inferred_from_wall_time(self):
        for mutate, message in [
            (lambda t: t.update(clocks=[]), 'clock mapping'),
            (lambda t: t['clocks'].append({'clock_id': 3, 'ts': 5_001_000_000, 'clock_value': 0}), 'unstable clock'),
            (lambda t: t['markers'][0].update(ts=6_100_000_000), 'marker/clock mismatch')]:
            with self.subTest(message=message):
                t = tables(); mutate(t)
                with self.assertRaisesRegex(ValueError, message):
                    summary.summarize(window(), t)

    def test_trace_loss_zero_frames_and_ambiguous_pairing_fail_closed(self):
        for mutate, message in [
            (lambda t: t['loss'][0].update(lost=1), 'trace loss'),
            (lambda t: t.update(frames=[]), 'zero paired'),
            (lambda t: t['frames'].append(copy.deepcopy(t['frames'][0])), 'ambiguous')]:
            with self.subTest(message=message):
                t = tables(); mutate(t)
                with self.assertRaisesRegex(ValueError, message):
                    summary.summarize(window(), t)

    def test_missing_target_received_input_is_not_accepted_as_delivered(self):
        w = window(); w['stream']['touches'].pop(2)
        with self.assertRaisesRegex(ValueError, 'event count mismatch'):
            summary.summarize(w, tables())

    def test_equal_commands_with_different_anchors_reject_matched_work(self):
        a, b = window(), window()
        self.assertTrue(summary.matched_work(a, b)['matched_end_anchors'])
        b['gestures'][2]['after']['anchors'][0]['index'] = 1
        with self.assertRaisesRegex(ValueError, 'achieved work mismatch'):
            summary.matched_work(a, b)

    def test_relative_actual_input_timing_and_environment_must_match(self):
        a, b = window(), window(); b['gestures'][0]['events'][1]['event_ms'] += 25
        with self.assertRaisesRegex(ValueError, 'actual timing mismatch'):
            summary.matched_work(a, b)
        b = window(); b['before']['environment']['refresh_hz'] = 90
        with self.assertRaisesRegex(ValueError, 'environment mismatch'):
            summary.matched_work(a, b)

    def test_nearest_rank_percentile_and_bad_json(self):
        self.assertEqual(summary.distribution(list(range(1, 101)))['p95'], 95)
        for raw in ['{"a":1,"a":2}', '{"a":NaN}']:
            with self.assertRaises(ValueError):
                summary.strict_json(raw)

    def test_exploratory_interval_is_reproducible_and_requires_ten_pairs(self):
        result = summary.paired_mean_interval([2.5]*10)
        self.assertEqual(result['bootstrap_95_interval'], [2.5, 2.5])
        self.assertEqual(result, summary.paired_mean_interval([2.5]*10))
        with self.assertRaisesRegex(ValueError, 'ten paired'):
            summary.paired_mean_interval([2.5]*9)

    def test_late_callback_is_attributed_after_generation_reassembly(self):
        a, b = window(), window()
        a['generation'] = b['generation'] = 0
        a['stream']['listener_removed'] = False
        b['stream']['listener_removed'] = True
        b['stream']['frames'][0].update(seq=1, intended_vsync_ns=2_100_000_000, dropped_reports=0)
        raw = {('tags', 'steady', 0): (a, None), ('tags', 'steady', 1): (b, None)}
        summary.reassemble_frames(raw)
        self.assertEqual(summary.summarize(a, tables())['delivered_callbacks'], 2)

    def test_duplicate_gap_and_unclosed_callback_streams_reject(self):
        for sequence, closed, message in [(0, True, 'duplicate'), (2, True, 'missing callback'), (1, False, 'closure unknown')]:
            with self.subTest(message=message):
                a, b = window(), window()
                a['generation'] = b['generation'] = 0
                a['stream']['listener_removed'] = False
                b['stream']['listener_removed'] = closed
                b['stream']['frames'][0]['seq'] = sequence
                with self.assertRaisesRegex(ValueError, message):
                    summary.reassemble_frames({('tags', 'steady', 0): (a, None), ('tags', 'steady', 1): (b, None)})

    def test_sql_pairing_retains_missing_and_duplicate_expected_rows(self):
        with sqlite3.connect(':memory:') as db:
            db.row_factory = sqlite3.Row
            db.executescript('''
CREATE TABLE process(upid INTEGER, name TEXT);
CREATE TABLE actual_frame_timeline_slice(id INTEGER, ts INTEGER, dur INTEGER, upid INTEGER, surface_frame_token INTEGER, layer_name TEXT);
CREATE TABLE expected_frame_timeline_slice(id INTEGER, ts INTEGER, dur INTEGER, upid INTEGER, surface_frame_token INTEGER, layer_name TEXT);
INSERT INTO process VALUES(1,'com.github.e0ipso.tokkl');
INSERT INTO actual_frame_timeline_slice VALUES(1,10,5,1,42,'CatalogProfileActivity');
''')
            missing = dict(db.execute(summary.QUERIES['frames']).fetchone())
            self.assertIsNone(missing['expected_id'])
            db.executemany('INSERT INTO expected_frame_timeline_slice VALUES(?,?,?,?,?,?)',
                           [(2, 10, 16, 1, 42, 'CatalogProfileActivity'), (3, 10, 16, 1, 42, 'CatalogProfileActivity')])
            rows = list(db.execute(summary.QUERIES['frames']))
            self.assertEqual([row['id'] for row in rows], [1, 1])

    def test_corpus_rejects_a_named_but_missing_trace_before_comparing(self):
        with tempfile.TemporaryDirectory(prefix='catalog-reader-canary-') as scratch:
            directory = Path(scratch)
            benchmark = {'benchmarks': [{'className': summary.CLASS, 'name': 'tagsFirstScroll',
                'repeatIterations': 10, 'warmupIterations': 0, 'profilerOutputs': [
                    {'type': 'PerfettoTrace', 'label': f'Trace Iteration {i}', 'filename': f'missing-{i}.perfetto-trace'} for i in range(10)]}]}
            (directory/'benchmark.json').write_text(json.dumps(benchmark))
            corpus = {'schema': 'catalog-corpus-v1', 'overlay_sha256': 'a'*64,
                      'revisions': [{'revision': revision, 'benchmarks': ['benchmark.json']} for revision in summary.REVISIONS]}
            (directory/'corpus.json').write_text(json.dumps(corpus))
            with self.assertRaisesRegex(ValueError, 'missing/empty trace'):
                summary.corpus(directory/'corpus.json')


if __name__ == '__main__':
    unittest.main(verbosity=2)
