#!/usr/bin/env python3
"""Offline catalog profiling evidence reader. Never launches Android or a trace processor.

--queries DIR writes the exact read-only queries to export with the reviewed trace processor.
--corpus FILE reads driver windows, benchmark JSON, retained traces and those CSV exports.
Synthetic host canaries exercise the reader; they are never Android performance results.
"""
import argparse
import base64
import csv
import datetime
import gzip
import hashlib
import json
import math
from pathlib import Path
import re
import random
import sys

PACKAGE = 'com.github.e0ipso.tokkl'
CLASS = PACKAGE + '.catalogprofile.CatalogFrameBenchmark'
REVISIONS = ('d16c97a5e98ab0747041b02b8dd9a058bc84c700', '4b3891e69f3d5290de73570aa759eb1c3337914a')
METHODS = {'tagsFirstScroll': ('tags', 'first'), 'tagsSteadyState': ('tags', 'steady'),
           'trashFirstScroll': ('trash', 'first'), 'trashSteadyState': ('trash', 'steady')}
# Core tables: https://perfetto.dev/docs/analysis/sql-tables . Match surface tokens,
# process and layer, retaining ambiguous/missing pairs instead of selecting an arbitrary row.
QUERIES = {
    'frames': f"""SELECT a.id, a.ts, a.dur, a.surface_frame_token, a.layer_name,
e.id AS expected_id, e.ts AS expected_ts, e.dur AS expected_dur
FROM actual_frame_timeline_slice a JOIN process p ON p.upid=a.upid
LEFT JOIN expected_frame_timeline_slice e ON e.upid=a.upid
 AND e.surface_frame_token=a.surface_frame_token AND e.layer_name=a.layer_name
WHERE p.name='{PACKAGE}' AND a.layer_name GLOB '*CatalogProfileActivity*';""",
    'expected': f"""SELECT e.id, e.ts, e.dur FROM expected_frame_timeline_slice e
JOIN process p ON p.upid=e.upid WHERE p.name='{PACKAGE}'
AND e.layer_name GLOB '*CatalogProfileActivity*';""",
    'markers': f"""SELECT s.name, s.ts, s.dur FROM slice s JOIN process_track t ON s.track_id=t.id
JOIN process p ON p.upid=t.upid WHERE p.name='{PACKAGE}' AND s.name GLOB 'catalog/*';""",
    'clocks': 'SELECT ts, clock_id, clock_value FROM clock_snapshot WHERE clock_id=3;',
    'sched': f"""SELECT s.ts, s.dur, t.tid FROM sched_slice s JOIN thread t ON t.utid=s.utid
JOIN process p ON p.upid=t.upid WHERE p.name='{PACKAGE}' AND (t.tid=p.pid OR t.name='RenderThread');""",
    'runnable': f"""SELECT s.ts, s.dur FROM thread_state s JOIN thread t ON t.utid=s.utid
JOIN process p ON p.upid=t.upid WHERE p.name='{PACKAGE}' AND t.tid=p.pid AND s.state IN ('R','R+');""",
    'loss': "SELECT coalesce(sum(value),0) AS lost FROM stats WHERE severity='data_loss';",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def strict_json(raw):
    def pairs(rows):
        result = {}
        for key, value in rows:
            require(key not in result, 'duplicate JSON key')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite JSON')))


def decode_fixture(encoded, expected):
    require(isinstance(encoded, str) and len(encoded) <= 65536, 'encoded fixture limit')
    require(re.fullmatch('[a-f0-9]{64}', expected) is not None, 'fixture hash syntax')
    import io
    with gzip.GzipFile(fileobj=io.BytesIO(base64.b64decode(encoded, validate=True))) as stream:
        raw = stream.read(524289)
    require(len(raw) <= 524288, 'decoded fixture limit')
    require(hashlib.sha256(raw).hexdigest() == expected, 'fixture hash mismatch')
    fixture = strict_json(raw)
    require(fixture['schema'] == 'catalog-fixture-v1' and fixture['timezone'] == 'Europe/Madrid', 'fixture schema/timezone')
    tags, trash = fixture['tags'], fixture['trash']
    require(len(tags) == 500 and len({r['id'] for r in tags}) == 500, 'tag count/IDs')
    require({r['name'] for r in tags} == {f'Measured tag {i:03}' for i in range(500)}, 'tag names')
    require(len(trash) == 1000 and len({r['id'] for r in trash}) == 1000, 'trash count/IDs')
    require({r['description'] for r in trash} == {f'Measured trash {i}' for i in range(1000)}, 'trash names')
    for row in trash:
        require(datetime.datetime.fromisoformat(row['start'].replace('Z', '+00:00')) ==
                datetime.datetime(2026, 10, 19, 8, tzinfo=datetime.timezone.utc), 'trash start')
        require(datetime.datetime.fromisoformat(row['stop'].replace('Z', '+00:00')) ==
                datetime.datetime(2026, 10, 19, 8, 5, tzinfo=datetime.timezone.utc) and row['duration'] == 300, 'trash stop/duration')
    return fixture


def distribution(values):
    require(values and all(isinstance(v, (int, float)) and math.isfinite(v) for v in values), 'missing/nonfinite samples')
    ordered = sorted(values)
    return {'count': len(values), **{f'p{p}': ordered[max(0, math.ceil(p / 100 * len(ordered)) - 1)]
                                   for p in (50, 90, 95, 99)}, 'max': ordered[-1]}


def paired_mean_interval(values):
    require(len(values) == 10 and all(math.isfinite(v) for v in values), 'ten paired deltas required')
    rng = random.Random(263)
    draws = sorted(sum(rng.choice(values) for _ in values)/len(values) for _ in range(2000))
    return {'mean': sum(values)/len(values), 'bootstrap_95_interval': [draws[49], draws[1949]],
            'resamples': 2000, 'seed': 263, 'interpretation': 'exploratory paired-window mean; no across-session repeatability claim'}


def anchor_signature(snapshot):
    rows = snapshot['anchors']
    require(rows and len({r['id'] for r in rows}) == len(rows), 'missing/duplicate anchors')
    return [(r['id'], r['index'], r['left'], r['top'], r['right'], r['bottom']) for r in rows]


def matched_work(a, b):
    require(a['fixture_sha256'] == b['fixture_sha256'] and a['compilation'] == b['compilation'] == 'Full', 'fixture/compilation mismatch')
    require(a['before']['environment'] == b['before']['environment'] and a['before']['viewport'] == b['before']['viewport'], 'environment mismatch')
    require(len(a['gestures']) == len(b['gestures']) == 5, 'gesture count')
    for sa, sb in [(a['before'], b['before']), (a['after'], b['after'])] + [
            (ga['after'], gb['after']) for ga, gb in zip(a['gestures'], b['gestures'])]:
        aa, bb = anchor_signature(sa), anchor_signature(sb)
        require(len(aa) == len(bb), 'anchor count mismatch')
        for ra, rb in zip(aa, bb):
            require(ra[:2] == rb[:2] and all(abs(x-y) <= 1 for x, y in zip(ra[2:], rb[2:])), 'achieved work mismatch')
    for ga, gb in zip(a['gestures'], b['gestures']):
        ea, eb = ga['events'], gb['events']
        require(len(ea) == len(eb) == 16, 'injected event count')
        for x, y in zip(ea, eb):
            require(x['accepted'] is True and y['accepted'] is True, 'refused input')
            require((x['action'], x['x'], x['y'], x['offset_ms']) == (y['action'], y['x'], y['y'], y['offset_ms']), 'motion mismatch')
            require(abs((x['event_ms']-ea[0]['event_ms']) - (y['event_ms']-eb[0]['event_ms'])) <= 1000/60, 'actual timing mismatch')
    return {'matched_end_anchors': True, 'absolute_pixel_travel': None,
            'net_row_index_travel': a['after']['anchors'][0]['index'] - a['before']['anchors'][0]['index']}


def overlap(start, end, row):
    return max(0, min(end, int(row['ts']) + int(row['dur'])) - max(start, int(row['ts'])))


def summarize(window, tables):
    require(window.get('complete') is True and not window.get('end_unknown') and not window.get('stream_unknown'), 'incomplete window')
    require(0 < window['wall_ns'] < 15_000_000_000, 'window wall limit')
    require(len(tables['loss']) == 1 and int(tables['loss'][0]['lost']) == 0, 'trace loss/unknown')
    markers = [r for r in tables['markers'] if r['name'] == window['marker']]
    require(len(markers) == 1 and 0 < int(markers[0]['dur']) < 15_000_000_000, 'missing/ambiguous/oversized phase marker')
    start = int(markers[0]['ts']); end = start + int(markers[0]['dur'])
    clocks = tables['clocks']
    require(clocks and all(int(r['clock_id']) == 3 for r in clocks), 'missing monotonic clock mapping')
    offsets = [int(r['ts']) - int(r['clock_value']) for r in clocks]
    require(max(offsets) - min(offsets) <= 100_000, 'unstable clock alignment')
    offset = offsets[0]
    begin, finish = window['begin']['start'], window['end']['end']
    require(abs((begin['boot_ns']-begin['mono_ns']) - (finish['boot_ns']-finish['mono_ns'])) <= 100_000, 'suspend/clock discontinuity')
    require(abs(begin['mono_ns'] + offset - start) <= 5_000_000 and abs(finish['mono_ns'] + offset - end) <= 5_000_000, 'marker/clock mismatch')
    rows = tables['frames']
    require(len({r['id'] for r in rows}) == len(rows), 'ambiguous FrameTimeline pairing')
    contained, boundary, unpaired = [], 0, 0
    for row in rows:
        ts, dur = int(row['ts']), int(row['dur'])
        if ts >= end or ts + max(dur, 0) <= start:
            continue
        if dur <= 0 or ts < start or ts + dur > end:
            boundary += 1
        elif row['expected_id'] in ('', None, '[NULL]'):
            unpaired += 1
        else:
            require(int(row['expected_dur']) > 0, 'invalid expected duration')
            contained.append(row)
    require(contained, 'zero paired frames')
    used = {str(r['expected_id']) for r in rows if int(r['ts']) < end and int(r['ts'])+max(0, int(r['dur'])) > start}
    expected_only = sum(1 for r in tables['expected'] if start <= int(r['ts']) < end and str(r['id']) not in used)
    require(tables['sched'] and all(int(r['dur']) >= 0 for r in tables['sched']), 'missing/incomplete CPU scheduling')
    overruns = [(int(r['ts'])+int(r['dur'])-int(r['expected_ts'])-int(r['expected_dur']))/1e6 for r in contained]
    cpu = [sum(overlap(int(r['ts']), int(r['ts'])+int(r['dur']), s) for s in tables['sched'])/1e6 for r in contained]
    callbacks = window['stream']['frames']
    delivered = [r for r in callbacks if start <= r['intended_vsync_ns']+offset < end]
    # Dropped reports refer back to a preceding callback, potentially across a phase.
    # Preserve the received sum and its ambiguity; never use it as a deadline denominator.
    dropped = sum(r['dropped_reports'] for r in delivered)
    require(callbacks and delivered and all(r['dropped_reports'] >= 0 for r in callbacks), 'missing/invalid frame callbacks')
    touches = window['stream']['touches']
    require(touches and all(touches[i]['event_ms'] <= touches[i+1]['event_ms'] for i in range(len(touches)-1)), 'missing/nonmonotonic received input')
    require(sum(r['action'] == 0 for r in touches) == 5 and sum(r['action'] == 1 for r in touches) == 5, 'received DOWN/UP count')
    injected = [event for gesture in window['gestures'] for event in gesture['events']]
    require(len(injected) == len(touches), 'injected/received event count mismatch')
    for event, received in zip(injected, touches):
        require(event['accepted'] is True and event['action'] == received['action'] and event['event_ms'] == received['event_ms']
                and abs(event['x']-received['x']) <= 1 and abs(event['y']-received['y']) <= 1, 'input delivery mismatch')
        require(received['source'] == 4098 and received['tool_type'] == 1, 'received input is not a touchscreen finger')
    return {'paired_frames': len(contained), 'boundary_frames': boundary, 'unpaired_frames': unpaired,
            'expected_without_contained_actual': expected_only, 'deadline_misses': sum(v > 0 for v in overruns),
            'deadline_denominator': len(contained), 'overrun_ms': distribution(overruns),
            'main_plus_render_cpu_execution_ms': distribution(cpu),
            'main_runnable_ns': sum(overlap(start, end, r) for r in tables['runnable']),
            'delivered_callbacks': len(delivered), 'dropped_reports_received': dropped,
            'dropped_phase_attribution': 'UNKNOWN', 'absolute_pixel_travel': None,
            'wall_ns': window['wall_ns'], 'clock_offset_ns': offset,
            'coverage_complete': unpaired == 0 and expected_only == 0,
            'samples': {'overrun_ms': overruns, 'cpu_execution_ms': cpu}}


def read_json(path):
    require(path.stat().st_size <= 16*1024*1024, 'JSON size limit')
    return strict_json(path.read_bytes())


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b''):
            h.update(chunk)
    return h.hexdigest()


def read_tables(directory):
    result = {}
    for key in QUERIES:
        path = directory/(key+'.csv')
        require(path.stat().st_size <= 32*1024*1024, 'CSV size limit')
        with path.open(newline='') as stream:
            result[key] = list(csv.DictReader(stream))
    return result


def reassemble_frames(raw_windows):
    """Late callbacks belong to their timestamped phase, even when exported in a later packet."""
    groups = {}
    closed = set()
    for key, (window, _) in sorted(raw_windows.items()):
        identity = (key[0], key[1], window['generation'])
        group = groups.setdefault(identity, {})
        for frame in window['stream']['frames']:
            require(frame['seq'] not in group, 'duplicate callback sequence')
            group[frame['seq']] = frame
        if window['stream'].get('listener_removed') is True:
            closed.add(identity)
    require(set(groups) == closed, 'callback listener closure unknown')
    for key, (window, _) in raw_windows.items():
        group = groups[(key[0], key[1], window['generation'])]
        sequences = sorted(group)
        require(sequences and sequences == list(range(sequences[0], sequences[-1]+1)), 'missing callback segment')
        window['stream']['frames'] = [group[index] for index in sequences]


def corpus(path):
    spec = read_json(path)
    require(spec['schema'] == 'catalog-corpus-v1', 'corpus schema')
    require(re.fullmatch('[a-f0-9]{64}', spec['overlay_sha256']) is not None, 'overlay identity')
    require([r['revision'] for r in spec['revisions']] == list(REVISIONS), 'exact revision pair required')
    revisions = []
    traces_seen = set()
    for revision in spec['revisions']:
        traced = {}
        for benchmark_path in revision['benchmarks']:
            benchmark_path = path.parent/benchmark_path
            data = read_json(benchmark_path)
            for method in data['benchmarks']:
                require(method['className'] == CLASS and method['name'] in METHODS, 'unexpected benchmark method')
                require(method['repeatIterations'] == 10 and method['warmupIterations'] == 0, 'benchmark iteration mismatch')
                workload, phase = METHODS[method['name']]
                require(len(method['profilerOutputs']) == 10, 'missing traces')
                for output in method['profilerOutputs']:
                    require(output['type'] == 'PerfettoTrace', 'wrong profiler output')
                    label = re.fullmatch(r'Trace Iteration ([0-9])', output['label'])
                    require(label is not None, 'trace ordinal')
                    key = (workload, phase, int(label[1]))
                    require(key not in traced, 'duplicate trace ordinal')
                    name = output['filename']
                    require(Path(name).name == name and name.endswith('.perfetto-trace'), 'unsafe trace reference')
                    trace = benchmark_path.parent/name
                    require(trace.is_file() and trace.stat().st_size > 0, 'missing/empty trace')
                    sha = digest(trace)
                    require(sha not in traces_seen, 'duplicate trace bytes')
                    traces_seen.add(sha)
                    traced[key] = (name, sha)
        require(len(traced) == 40, 'exactly forty trace windows per revision')
        raw_windows = {}
        for filename in revision['windows']:
            window = read_json(path.parent/filename)
            require(window['revision'] == revision['revision'], 'window revision')
            key = (window['workload'], window['phase'], window['index'])
            require(key in traced and key not in raw_windows, 'foreign/duplicate window')
            name, sha = traced[key]
            export = revision['analysis'][name]
            require(export['trace_sha256'] == sha, 'trace/CSV identity mismatch')
            directory = path.parent/export['directory']
            require(set(export['csv_sha256']) == set(QUERIES), 'complete SQL exports required')
            for query in QUERIES:
                require(digest(directory/(query+'.csv')) == export['csv_sha256'][query], 'CSV hash mismatch')
            raw_windows[key] = (window, directory)
        require(set(raw_windows) == set(traced), 'missing window sidecars')
        reassemble_frames(raw_windows)
        windows = {}
        for key, (window, directory) in raw_windows.items():
            windows[key] = (window, summarize(window, read_tables(directory)))
        revisions.append(windows)
    results = []
    for key in sorted(revisions[0]):
        a, b = revisions[0][key], revisions[1][key]
        work = matched_work(a[0], b[0])
        require(a[1]['coverage_complete'] and b[1]['coverage_complete'], 'incomplete FrameTimeline coverage')
        results.append({'workload': key[0], 'phase': key[1], 'index': key[2], 'work': work,
                        'eager': a[1], 'lazy': b[1]})
    pooled = {}
    for workload, phase in METHODS.values():
        cells = [r for r in results if (r['workload'], r['phase']) == (workload, phase)]
        pooled[workload+'-'+phase] = {
            revision: {metric: distribution([sample for row in cells for sample in row[revision]['samples'][metric]])
                       for metric in ('overrun_ms', 'cpu_execution_ms')} for revision in ('eager', 'lazy')}
        pooled[workload+'-'+phase]['paired_p95_overrun_delta_ms'] = [
            row['lazy']['overrun_ms']['p95']-row['eager']['overrun_ms']['p95'] for row in cells]
        pooled[workload+'-'+phase]['paired_p95_delta_interval'] = paired_mean_interval(
            pooled[workload+'-'+phase]['paired_p95_overrun_delta_ms'])
    return {'schema': 'catalog-summary-v1', 'overlay_sha256': spec['overlay_sha256'], 'windows': results, 'pooled': pooled,
            'runtime_acceptance': 'UNVERIFIED', 'host_quiet': 'requires separate root-owned telemetry validation',
            'opening_benefit': 'requires separate unchanged opening-probe comparison',
            'interpretation': 'Frame evidence and matched endpoints only; no automatic no-regression conclusion.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--queries', type=Path)
    group.add_argument('--corpus', type=Path)
    args = parser.parse_args()
    if args.queries:
        args.queries.mkdir(parents=True, exist_ok=True)
        for name, query in QUERIES.items():
            with (args.queries/(name+'.sql')).open('x') as stream:
                stream.write(query+'\n')
    else:
        print(json.dumps(corpus(args.corpus), indent=2, allow_nan=False))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, csv.Error, json.JSONDecodeError) as failure:
        print('catalog evidence rejected: '+str(failure), file=sys.stderr)
        sys.exit(1)
