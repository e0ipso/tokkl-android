"""Finite historical Catalog contract. Read-only; never executes a workload/query.

The controller checks raw capture. assess_comparison checks later offline receipts.
Neither entry point grants execution, resource accounting or Android acceptance.
"""
import argparse
import base64
import binascii
import csv
import gzip
import hashlib
import io
import json
import math
from pathlib import Path
import re
import struct
import subprocess
import sys
import types
import xml.etree.ElementTree as ET

if __package__:
    from . import scoped_results as scoped
else:
    import scoped_results as scoped

require = scoped.require
PROFILE = 'catalog-frame-api-v1'
LABEL = 'catalog-frame-api'
CLASS = 'com.github.e0ipso.tokkl.catalogprofile.CatalogFrameBenchmark'
METHODS = {'tagsFirstScroll': ('tags', 'first'), 'tagsSteadyState': ('tags', 'steady'),
           'trashFirstScroll': ('trash', 'first'), 'trashSteadyState': ('trash', 'steady')}
REVISIONS = ('d16c97a5e98ab0747041b02b8dd9a058bc84c700', '4b3891e69f3d5290de73570aa759eb1c3337914a')
ARG = '-Pandroid.testInstrumentationRunnerArguments.'
ARGS = ['scripts/instrumented-tests.sh', ':macrobenchmark:connectedBenchmarkAndroidTest',
        ARG+'package=com.github.e0ipso.tokkl.catalogprofile,com.github.e0ipso.tokkl.api']
COMMON = ['catalogRunId', 'catalogRevision', 'catalogEnvironmentSha256', 'catalogTagsViewport', 'catalogTrashViewport']
SERVER = 'ghcr.io/e0ipso/tokkl:1.8.1@sha256:70106ba2a6bc5b23c11f443f33bf2c7fb76d3af75b0335461047c4d27bd52e9b'
OVERLAY = {
    'macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/CatalogFrameBenchmark.kt': 'a15038b3b2c63c87b9dda70dbe7abd7ca8d2e7da560d156cac83480a0861cdfa',
    'macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/CatalogProbe.kt': 'ce6c4ca31e0c3c4420249ee780eb64af9b1da0327f5ea35f3a1bde85461ecb5f',
    'app/src/benchmark/java/com/github/e0ipso/tokkl/CatalogProfileActivity.kt': 'c0c5d13321d319b3b49576ab0416bb205f73469a632e7bbe76faa8bf1ac99367',
    'app/src/benchmark/AndroidManifest.xml': 'cf8ac2c9cec41f03825f6f5e72b32db1569a08f852730bf746746ea462e428f2',
    'macrobenchmark/src/benchmark/AndroidManifest.xml': '02b1194ec166d6b8d07bc7ee50bf78f88b371e05abdf2f8e509a9e5ce0883ce2',
    'macrobenchmark/src/benchmark/res/xml/catalog_profile_network_security.xml': 'e43a7b6d5d89ce3b833c4b0d34047d7a67956ec2659b5f62b9fff1937e0c5628',
    'scripts/catalog-frame-summary.py': 'b239183039f78ab509ef3d58551720a0343c4951deb71ab50d6adea916b4ef1c',
}
VERIFICATION = {'scripts/instrumented-tests.sh': '69b9d74b3e5da7c38ef140b76dde6b5c9a9ca67766b6d0d3c31320f6505fb20d',
                'scripts/sift_telemetry.py': '62ca64cee8734b2563603a525931950cd2fe034f1500494e6d91b5dae70f620f'}
HEADERS = {'frames': ['id', 'ts', 'dur', 'surface_frame_token', 'layer_name', 'expected_id', 'expected_ts', 'expected_dur'],
           'expected': ['id', 'ts', 'dur'], 'markers': ['name', 'ts', 'dur'],
           'clocks': ['ts', 'clock_id', 'clock_value'], 'sched': ['ts', 'dur', 'tid'],
           'runnable': ['ts', 'dur'], 'loss': ['lost']}
TOOL_INPUTS = {'java', 'java_release', 'android_jar', 'adb', 'aapt2', 'gradle_launcher', 'resolved_dependencies'}
ERRORS = (OSError, ValueError, KeyError, TypeError, AttributeError, OverflowError, RecursionError, ET.ParseError,
          binascii.Error, EOFError, csv.Error, subprocess.SubprocessError)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def hexhash(value):
    return isinstance(value, str) and re.fullmatch('[a-f0-9]{64}', value) is not None


def integer(value, minimum=0):
    return type(value) is int and value >= minimum


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def git(project, *args):
    # Metadata/source reads only. Never checkout, stage, install or invoke Gradle.
    return subprocess.check_output(['git', '-C', str(project), *args], stderr=subprocess.PIPE, timeout=15)


def source_paths(names):
    return {p for p in names if p.startswith(('app/src/', 'macrobenchmark/src/', 'gradle/', 'buildSrc/'))
            and (p.endswith(('.kt', '.kts', '.xml', '.toml', '.properties', '.jar')))
            or p in {'.gitattributes', 'gradlew', 'gradlew.bat', 'gradle.properties', 'settings.gradle.kts',
                     'build.gradle.kts', 'app/build.gradle.kts', 'macrobenchmark/build.gradle.kts',
                     'scripts/catalog-frame-summary.py', 'scripts/instrumented-tests.sh', 'scripts/sift_telemetry.py'}}


def historical_sources(project, revision):
    """Bind all historical production/build/API/helper sources, not current main.

    Exact seven-file driver and two verification overlays are the only source
    differences. APK outputs are separately pinned local build artifacts.
    """
    allowed = set(OVERLAY) | set(VERIFICATION) | {'docs/TAGS-TRASH-PROFILING.md', 'test/catalog_frame_summary_test.py'}
    require(set(git(project, 'diff', '--name-only', revision, 'HEAD').decode().splitlines()) == allowed,
            'Catalog composite must preserve every other historical tracked file')
    entries = git(project, 'ls-tree', '-rz', revision).split(b'\0')
    tree = {}
    for entry in entries:
        if entry:
            meta, name = entry.split(b'\t', 1)
            mode, kind, blob = meta.decode().split()
            tree[name.decode()] = (mode, kind, blob)
    expected = source_paths(tree) | set(OVERLAY) | set(VERIFICATION)
    actual = source_paths(p.relative_to(project).as_posix() for folder in ['app/src', 'macrobenchmark/src', 'gradle', 'buildSrc']
                          for p in (project/folder).rglob('*') if p.is_file())
    actual |= {p for p in expected if (project/p).is_file()}
    require(actual == expected, 'historical source membership changed')
    rows = []
    for name in sorted(expected):
        path = scoped.checked_file(project, name)
        value = scoped.sha(path)
        if name in OVERLAY or name in VERIFICATION:
            require(value == (OVERLAY | VERIFICATION)[name], 'reviewed overlay changed: '+name)
        else:
            mode, kind, blob = tree[name]
            require(kind == 'blob' and mode in ('100644', '100755'), 'unsupported historical source')
            raw = scoped.fresh_bytes(project, path, 0, 4*1024*1024)
            require(digest(raw) == value, 'historical source changed while reading')
            # Historical .gitattributes declares gradlew.bat text eol=crlf.
            # Compare Git's normalized blob identity and retain the exact
            # checkout SHA256 as the input pin. No filter command is executed.
            normalized = raw.replace(b'\r\n', b'\n') if name == 'gradlew.bat' else raw
            actual_blob = hashlib.sha1(b'blob '+str(len(normalized)).encode()+b'\0'+normalized).hexdigest()
            require(actual_blob == blob, 'historical production/build/API source changed: '+name)
        rows.append({'source': name, 'sha256': value})
    return rows


def fixture_bytes(encoded, expected):
    require(isinstance(encoded, str) and 0 < len(encoded) <= 65536 and hexhash(expected), 'fixture transport bounds/hash')
    with gzip.GzipFile(fileobj=io.BytesIO(base64.b64decode(encoded, validate=True))) as stream:
        raw = stream.read(524289)
    require(0 < len(raw) <= 524288 and digest(raw) == expected, 'decoded fixture bounds/hash')
    fixture = scoped.strict_json(raw)
    require(fixture['schema'] == 'catalog-fixture-v1' and fixture['timezone'] == 'Europe/Madrid', 'fixture schema/timezone')
    for key, count, field in [('tags', 500, 'name'), ('trash', 1000, 'description')]:
        rows = fixture[key]
        require(isinstance(rows, list) and len(rows) == count and all(integer(r['id'], 1) for r in rows)
                and len({r['id'] for r in rows}) == count, 'fixture count/IDs')
        names = {f'Measured tag {i:03d}' if key == 'tags' else f'Measured trash {i}' for i in range(count)}
        require({r[field] for r in rows} == names, 'fixture names')
    for row in fixture['trash']:
        require(row['start'] == '2026-10-19T08:00:00Z' and row['stop'] == '2026-10-19T08:05:00Z'
                and type(row['duration']) is int and row['duration'] == 300, 'fixture start/stop/duration')
    # Recursive key checking includes extra server fields, not just the driver schema.
    def secrets(value):
        if isinstance(value, dict):
            require(not any(re.search('token|password|authorization|credential|secret', k, re.I) for k in value),
                    'credentials in retained fixture')
            for v in value.values(): secrets(v)
        elif isinstance(value, list):
            for v in value: secrets(v)
    secrets(fixture)
    origin = fixture['origin']
    require(origin['kind'] == 'real-server' and origin['declared_server_image'] == SERVER, 'real-server fixture origin missing')
    cleanup(origin['cleanup'])
    return raw, fixture


def cleanup(value):
    require(isinstance(value, dict) and value.get('user_delete_status') == 204
            and value.get('deleted_auth_status') in (401, 403), 'seed cleanup unknown/incomplete')


def no_credentials(value):
    if isinstance(value, dict):
        require(not any(re.search('token|password|authorization|credential|secret', k, re.I) for k in value), 'credential field in raw Catalog evidence')
        for child in value.values(): no_credentials(child)
    elif isinstance(value, list):
        for child in value: no_credentials(child)


def parameters(args):
    require(args[:3] == ARGS, 'Catalog exact launcher/task/package pair required')
    pairs = []
    for arg in args[3:]:
        require(arg.startswith(ARG) and '=' in arg[len(ARG):], 'Catalog runner argument required')
        pairs.append(arg[len(ARG):].split('=', 1))
    keys = [p[0] for p in pairs]
    modes = [COMMON[:2]+['catalogSeed', 'catalogServerImage'], COMMON+['catalogFixture', 'catalogFixtureSha256']]
    require(keys in modes, 'Catalog exact ordered parameters required; duplicates/overrides forbidden')
    p = dict(pairs)
    require(re.fullmatch('[A-Za-z0-9_.-]{1,32}', p['catalogRunId']) is not None, 'unsafe Catalog run ID')
    require(p['catalogRevision'] in REVISIONS, 'Catalog historical revision')
    if 'catalogSeed' in p:
        require(p['catalogSeed'] == 'true' and p['catalogServerImage'] == SERVER
                and p['catalogRevision'] == REVISIONS[0], 'seed requires eager first and exact pinned server')
    else:
        require(p['catalogRevision'] == REVISIONS[1] and hexhash(p['catalogEnvironmentSha256']), 'only lazy replay with complete accepted environment is supported')
        for key in ['catalogTagsViewport', 'catalogTrashViewport']: viewport(p[key])
        fixture_bytes(p['catalogFixture'], p['catalogFixtureSha256'])
    return p


def tool_inputs(reviewed, sources):
    require(isinstance(reviewed, dict) and set(reviewed) == TOOL_INPUTS, 'complete reviewed Catalog tool inputs required')
    def checked(row):
        require(isinstance(row, dict) and set(row) == {'path', 'sha256'} and Path(row['path']).is_absolute()
                and Path(row['path']).resolve() == Path(row['path']) and hexhash(row['sha256'])
                and scoped.sha(scoped.checked_file(Path(row['path']).parent, Path(row['path']).name)) == row['sha256'], 'Catalog tool/dependency bytes changed')
    for row in reviewed.values(): checked(row)
    graph_path = Path(reviewed['resolved_dependencies']['path'])
    graph = scoped.strict_json(scoped.fresh_bytes(graph_path.parent, graph_path, 0, 4*1024*1024))
    build_rows = [r for r in sources if r['source'].endswith(('.kts', '.toml', '.properties', '.jar')) or r['source'] in ['gradlew', 'gradlew.bat']]
    require(graph['schema'] == 'catalog-resolved-dependencies-v1' and graph['build_sources_sha256'] == digest(canonical(build_rows))
            and isinstance(graph['files'], list) and 0 < len(graph['files']) <= 1024, 'Catalog resolved dependency/build provenance')
    artifacts = set(); paths = set()
    for row in graph['files']:
        require(set(row) == {'artifact', 'path', 'sha256'} and re.fullmatch('[A-Za-z0-9_.:-]+', row['artifact'])
                and row['artifact'] not in artifacts and row['path'] not in paths, 'duplicate/invalid resolved dependency')
        checked({k: row[k] for k in ['path', 'sha256']}); artifacts.add(row['artifact']); paths.add(row['path'])
    return graph['files']


def validate_plan(config):
    frozen = Path(config['run_dir'])/'run-config.json'
    if frozen.is_file():
        scoped.strict_json(scoped.fresh_bytes(frozen.parent, frozen, 0, 4*1024*1024))
    jobs = config['plan']
    require(len(jobs) == 1 and 'plan_recipe' not in config
            and config['checked_head'] == jobs[0]['result_recipe'].get('checked_head'),
            'Catalog requires one capture job and the declared composite checked HEAD')
    recipe = jobs[0]['result_recipe']; tools = recipe.get('tool_inputs', {})
    require(set(tools) == TOOL_INPUTS, 'Catalog tool inventory required')
    environment = config['environment']
    java = Path(environment['JAVA_HOME']); sdk = Path(environment['ANDROID_HOME'])
    for key, path in [('java', java/'bin/java'), ('java_release', java/'release'),
                      ('android_jar', sdk/'platforms/android-36/android.jar'), ('adb', sdk/'platform-tools/adb')]:
        require(tools[key]['path'] == str(path.resolve()), 'Catalog configured tool context differs: '+key)
    require(Path(tools['aapt2']['path']).is_relative_to(sdk/'build-tools')
            and Path(tools['aapt2']['path']).name == 'aapt2', 'Catalog SDK build tool context differs')


def declare(project, args, device_build, reviewed_tool_inputs=None, accepted_seed=None):
    p = parameters(args)
    if 'catalogSeed' in p:
        require(accepted_seed is None, 'seed cannot claim a frozen seed output in advance')
    else:
        observed = validate_seed_reference(accepted_seed)
        require(observed['fixture_transport'] == {'encoded': p['catalogFixture'], 'sha256': p['catalogFixtureSha256']}
                and all(p[k] == v for k, v in observed['replay_arguments'].items()), 'replay differs from complete accepted seed fixture/baseline')
    project = Path(project).resolve()
    require(git(project, 'rev-parse', '--show-toplevel').decode().strip() == str(project), 'Catalog project must be checked tree root')
    head = git(project, 'rev-parse', 'HEAD').decode().strip()
    tree = git(project, 'rev-parse', 'HEAD^{tree}').decode().strip()
    require(re.fullmatch('[a-f0-9]{40}', head) and re.fullmatch('[a-f0-9]{40}', tree), 'Catalog composite commit/tree')
    require(not git(project, 'diff', '--name-only', 'HEAD')
            and not git(project, 'status', '--porcelain', '--untracked-files=all'), 'dirty Catalog source tree')
    require(isinstance(device_build, dict) and type(device_build['version']['sdk']) is int
            and device_build['version']['sdk'] == 36 and all(isinstance(device_build.get(k), str) and device_build[k]
                for k in ['brand', 'device', 'fingerprint', 'id', 'model', 'type']), 'complete API36 device build required')
    sources = historical_sources(project, p['catalogRevision'])
    dependencies = tool_inputs(reviewed_tool_inputs, sources)
    wrapper = (project/'gradle/wrapper/gradle-wrapper.properties').read_text()
    version = re.search(r'distributionUrl=.*gradle-([0-9.]+)-bin.zip', wrapper)
    require(version and Path(reviewed_tool_inputs['gradle_launcher']['path']).name == 'gradle-launcher-'+version[1]+'.jar', 'historical Gradle launcher version differs')
    inventory = [scoped.inventory(project, c) for c in scoped.API_CLASSES]
    require([len(r['methods']) for r in inventory] == [3, 7], 'exact historical ten API cases required')
    inventory.append({'class': CLASS, 'methods': list(METHODS), 'source': next(iter(OVERLAY)),
                      'sha256': OVERLAY[next(iter(OVERLAY))], 'shape': 'reviewed-catalog-expression-v1'})
    apks = [{'source': name, 'sha256': scoped.sha(scoped.checked_file(project, name))} for name in scoped.APK_PATHS]
    require(all((project/r['source']).stat().st_size > 0 for r in apks), 'empty local APK')
    return {'profile': PROFILE, 'parameters': p, 'base_revision': p['catalogRevision'], 'checked_head': head,
            'checked_tree': tree, 'device_build': device_build, 'sources': sources, 'apks': apks,
            'inventory': inventory, 'driver_overlay_sha256': digest(canonical(OVERLAY)),
            'tool_inputs': reviewed_tool_inputs, 'dependency_inputs': dependencies,
            'accepted_seed': accepted_seed,
            'verification_overlay_sha256': digest(canonical(VERIFICATION))}


def validate_recipe(project, job, pins):
    require(job['label'] == LABEL and job['api'] == 36 and job['standalone'] is False and job['seconds'] == 7200,
            'Catalog requires canonical label/API36/nonstandalone/7200 seconds')
    expected = declare(project, job['args'], job['result_recipe'].get('device_build'), job['result_recipe'].get('tool_inputs'), job['result_recipe'].get('accepted_seed'))
    require(job['result_recipe'] == expected, 'Catalog complete declaration/composite identity changed')
    for row in expected['sources']+expected['apks']:
        require(pins.get(str(Path(project)/row['source'])) == row['sha256'], 'Catalog source/local APK not input-pinned')
    for row in list(expected['tool_inputs'].values())+expected['dependency_inputs']:
        require(pins.get(row['path']) == row['sha256'], 'Catalog actual tool/dependency not input-pinned')
    if expected['accepted_seed']:
        require(all(pins.get(n) == h for n, h in expected['accepted_seed']['inputs'].items()), 'accepted seed evidence must be input-pinned')
    return expected


class Reader:
    """Bounded evidence reads, including identity checks again at closure."""
    def __init__(self, root, started):
        self.root = Path(root)
        require(self.root.is_absolute() and self.root.resolve() == self.root, 'canonical evidence root required')
        self.started = started
        self.seen = {}
        self.inodes = {}

    def read(self, name, limit=4*1024*1024, allow_empty=False):
        path = scoped.checked_file(self.root, name)
        info = path.stat()
        require(info.st_nlink == 1, 'hard-linked evidence alias')
        key = (info.st_dev, info.st_ino)
        require(key not in self.inodes or self.inodes[key] == name, 'evidence inode alias')
        self.inodes[key] = name
        if allow_empty and info.st_size == 0:
            require(info.st_mtime >= self.started, 'stale empty evidence')
            raw = b''
        else:
            raw = scoped.fresh_bytes(self.root, path, self.started, limit)
        identity = (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns, digest(raw))
        require(name not in self.seen or self.seen[name] == identity, 'evidence changed between reads')
        self.seen[name] = identity
        return raw

    def json(self, name):
        value = scoped.strict_json(self.read(name))
        if getattr(self, 'raw_catalog', False): no_credentials(value)
        return value

    def close(self):
        for name in list(self.seen):
            self.read(name, max(self.seen[name][2], 1), allow_empty=True)
        return {name: value[-1] for name, value in self.seen.items()}


def clock(value):
    require(set(value) == {'mono_ns', 'boot_ns', 'uptime_ms'} and all(integer(v) for v in value.values()), 'invalid sidecar clock')
    return value['mono_ns']


def compact(value):
    return json.dumps(value, separators=(',', ':'), ensure_ascii=False, allow_nan=False)


def viewport(text):
    require(isinstance(text, str), 'viewport argument must be a string')
    values = [float(v) for v in text.split(':')]
    require(len(values) == 4 and all(finite(v) for v in values)
            and values[2] > values[0] and values[3] > values[1], 'Catalog viewport bounds')
    values = [struct.unpack('f', struct.pack('f', v))[0] for v in values]
    require(all(finite(v) for v in values), 'Catalog viewport Float bounds')
    return values


def baselines(reader, recipe, parent):
    """Actual 9e9b36 driver schema; seed observation is not advance expectation."""
    p = recipe['parameters']; seed = 'catalogSeed' in p
    prefix = (Path(parent)/(p['catalogRunId']+'-'+p['catalogRevision']+'-')).as_posix()
    name = prefix+'baseline.json'
    combined = reader.json(name)
    require(set(combined) == {'schema', 'run', 'revision', 'mode', 'fixture_sha256', 'workloads', 'replay_arguments'}
            and combined['schema'] == 'catalog-baseline-v1' and combined['run'] == p['catalogRunId']
            and combined['revision'] == p['catalogRevision'] and combined['mode'] == ('seed' if seed else 'replay')
            and hexhash(combined['fixture_sha256']) and set(combined['workloads']) == {'tags', 'trash'}, 'combined baseline identity/schema')
    effective = dict(p)
    environments = []
    for workload in ['tags', 'trash']:
        record = reader.json(prefix+workload+'-baseline.json')
        require(record == combined['workloads'][workload]
                and set(record) == {'schema', 'run', 'revision', 'mode', 'fixture_sha256', 'workload', 'source_phase',
                                     'environment', 'environment_serialized', 'environment_sha256', 'viewport', 'viewport_serialized', 'viewport_argument'}
                and record['schema'] == 'catalog-workload-baseline-v1'
                and all(record[k] == combined[k] for k in ['run', 'revision', 'mode', 'fixture_sha256'])
                and record['workload'] == workload and record['source_phase'] in ['first', 'steady'], 'workload baseline identity/schema')
        require(compact(record['environment']) == record['environment_serialized']
                and scoped.strict_json(record['environment_serialized']) == record['environment']
                and digest(record['environment_serialized'].encode()) == record['environment_sha256'], 'baseline environment serialization/hash')
        require(compact(record['viewport']) == record['viewport_serialized']
                and scoped.strict_json(record['viewport_serialized']) == record['viewport']
                and len(record['viewport']) == 4 and all(finite(v) for v in record['viewport'])
                and viewport(record['viewport_argument']) == [struct.unpack('f', struct.pack('f', v))[0] for v in record['viewport']], 'baseline viewport serialization/Float argument')
        effective['catalog'+workload.title()+'Viewport'] = record['viewport_argument']
        environments.append(record['environment_serialized'])
    require(environments[0] == environments[1], 'environment changed across workloads')
    effective['catalogEnvironmentSha256'] = combined['workloads']['tags']['environment_sha256']
    replay = {key: effective[key] for key in COMMON[2:]}
    require(combined['replay_arguments'] == replay, 'combined replay arguments differ from observed baselines')
    if not seed:
        require(all(p[k] == v for k, v in replay.items()), 'replay baseline differs from accepted seed expectations')
    return combined, dict(recipe, parameters=effective)


def snapshot(value, recipe, fixture, workload):
    p = recipe['parameters']
    env = value['environment']
    require(set(env) == {'width', 'height', 'density_dpi', 'font_scale', 'locale', 'orientation', 'refresh_hz', 'bars', 'ime_visible', 'theme'}
            and env['font_scale'] == 1 and env['ime_visible'] is False and env['theme'] == 'light'
            and finite(env['refresh_hz']) and abs(env['refresh_hz']-60) < .1
            and all(integer(env[k], 1) for k in ['width', 'height', 'density_dpi', 'orientation'])
            and isinstance(env['locale'], str) and env['locale'] and len(env['bars']) == 4
            and all(integer(v) for v in env['bars']), 'Catalog environment shape/values')
    # JSONObject.toString uses its emitted key order. Preserve it, never sort.
    require(digest(json.dumps(env, separators=(',', ':'), ensure_ascii=False).encode()) == p['catalogEnvironmentSha256'],
            'Catalog actual environment hash')
    viewport = [float(v) for v in p['catalog'+workload.title()+'Viewport'].split(':')]
    require(len(value['viewport']) == 4 and all(finite(v) for v in value['viewport'])
            and all(abs(a-b) <= .01 for a, b in zip(value['viewport'], viewport)), 'Catalog actual viewport mismatch')
    require(value['ready'] is True and value['workload'] == workload
            and value['fixture_sha256'] == digest(fixture[0]) and integer(value['draw_serial'], 1)
            and integer(value['last_draw_ns'], 1) and integer(value['exposed_row_texts'], 1)
            and value['last_draw_ns'] <= clock(value['clock']), 'Catalog snapshot identity/readiness')
    rows = value['anchors']
    require(isinstance(rows, list) and rows and len({r['id'] for r in rows}) == len(rows), 'missing/duplicate anchors')
    for row in rows:
        i = row['index']
        require(integer(i) and i < len(fixture[1][workload]) and row['id'] == fixture[1][workload][i]['id'], 'anchor fixture order/ID')
        require(all(finite(row[k]) for k in ['left', 'top', 'right', 'bottom', 'clipped_top', 'clipped_bottom'])
                and row['left'] < row['right'] and row['top'] < row['bottom']
                and max(row['top'], viewport[1]) <= row['clipped_top'] < row['clipped_bottom'] <= min(row['bottom'], viewport[3]),
                'invalid clipped/unclipped anchor geometry')
    return value


def stable_snapshot(value, baseline, workload):
    require(compact(value['environment']) == baseline['workloads'][workload]['environment_serialized']
            and compact(value['viewport']) == baseline['workloads'][workload]['viewport_serialized'], 'baseline environment/viewport changed within run')


def phase(value, identity):
    require(set(value) == {'id', 'start', 'end'} and value['id'] == identity
            and clock(value['start']) < clock(value['end']), 'phase handler barrier/identity')


def stream(value, frame_cursor, touch_cursor, phases, final):
    require(set(value) == {'frame_total', 'touch_total', 'phases', 'listener_removed', 'barrier_ack',
                           'dropped_boundary_attribution', 'frame_from', 'touch_from', 'frames', 'touches'}, 'stream schema')
    require(value['phases'] == phases and value['listener_removed'] is final
            and value['dropped_boundary_attribution'] == 'UNKNOWN'
            and clock(value['barrier_ack']) >= max(clock(p['end']) for p in phases), 'stream fence/phases/closure')
    for key, cursor in [('frame', frame_cursor), ('touch', touch_cursor)]:
        rows = value['frames' if key == 'frame' else 'touches']
        total = value[key+'_total']
        require(integer(total) and total <= (20000 if key == 'frame' else 4096)
                and value[key+'_from'] == cursor and [r['seq'] for r in rows] == list(range(cursor, total)), 'stream cursor holes/duplicates')
        for row in rows:
            if key == 'frame':
                require(all(integer(row[k]) for k in ['intended_vsync_ns', 'vsync_ns', 'total_ns', 'deadline_duration_ns', 'first_draw', 'dropped_reports'])
                        and clock(row['callback']) >= row['intended_vsync_ns'], 'invalid callback packet')
            else:
                require(integer(row['event_ms']) and integer(row['down_ms']) and row['down_ms'] <= row['event_ms']
                        and row['source'] == 4098 and row['tool_type'] == 1
                        and row['action'] in [0, 1, 2] and all(finite(row[k]) for k in ['x', 'y']), 'invalid received input packet')
                clock(row['clock'])
    return value['frame_total'], value['touch_total']


def gestures(window, recipe):
    view = [float(v) for v in recipe['parameters']['catalog'+window['workload'].title()+'Viewport'].split(':')]
    require(len(window['gestures']) == 5, 'five gestures required')
    injected = []
    previous = -1
    settled = clock(window['begin']['start'])
    for gesture in window['gestures']:
        events = gesture['events']
        require(gesture['requested_ms'] == 150 and len(events) == 16, 'gesture duration/event count')
        for i, event in enumerate(events):
            require(event['accepted'] is True and event['action'] == (0 if i == 0 else 1 if i == 15 else 2)
                    and event['offset_ms'] == i*10 and integer(event['event_ms']) and event['event_ms'] >= previous,
                    'refused/incorrect/nonmonotonic injected input')
            expected_y = view[1]+(view[3]-view[1])*(.8-.6*i/15)
            require(finite(event['x']) and finite(event['y']) and abs(event['x']-(view[0]+view[2])/2) <= .01
                    and abs(event['y']-expected_y) <= .01 and settled <= clock(event['before']) <= clock(event['after'])
                    <= clock(gesture['after']['clock']), 'injected coordinates/clock')
            previous = event['event_ms']
        elapsed = events[-1]['event_ms']-events[0]['event_ms']
        require(elapsed > 0 and finite(gesture['finger_pixels_per_second'])
                and math.isclose(gesture['finger_pixels_per_second'], -.6*(view[3]-view[1])*1000/elapsed, abs_tol=.01), 'actual finger speed')
        injected.extend(events)
        require(integer(gesture['after']['quiet_draw_ns']) and gesture['after']['quiet_draw_ns'] >= 50_000_000,
                'gesture settle observation incomplete')
        require(gesture['after']['quiet_draw_ns'] <= clock(gesture['after']['clock'])-gesture['after']['last_draw_ns'], 'gesture quiet draw clock mismatch')
        settled = clock(gesture['after']['clock'])
    require(settled <= clock(window['after']['clock']) <= clock(window['end']['end']), 'final snapshot/settle ordering')
    touches = window['stream']['touches']
    require(len(injected) == len(touches), 'injected/received input count mismatch')
    for event, received in zip(injected, touches):
        require((event['action'], event['event_ms']) == (received['action'], received['event_ms'])
                and abs(event['x']-received['x']) <= 1 and abs(event['y']-received['y']) <= 1
                and type(received.get('historical', False)) is bool
                and (not received.get('historical', False) or received['action'] == 2), 'input/history delivery mismatch')


def raw_capture(reader, recipe):
    """Validate actual driver files; no invented corpus/export files at capture."""
    reader.raw_catalog = True
    p = recipe['parameters']
    files = list(reader.root.rglob('*-benchmarkData.json'))
    require(len(files) == 1, 'exactly one Catalog benchmark JSON required')
    parent = files[0].parent.relative_to(reader.root)
    baseline, effective = baselines(reader, recipe, parent)
    recipe = effective
    data = reader.json(files[0].relative_to(reader.root).as_posix())
    require(data['context']['build'] == recipe['device_build'] and len(data['benchmarks']) == 4, 'Catalog metric/device inventory')
    traces = {}; hashes = set(); windows = {}; methods = set(); openings = []; fixtures = []; seed_receipts = []; final_anchors = {}; transports = []; readiness = {}
    all_names = set()
    for benchmark in data['benchmarks']:
        method = benchmark['name']
        require(benchmark['className'] == CLASS and method in METHODS and method not in methods, 'Catalog benchmark method inventory')
        methods.add(method)
        workload, mode = METHODS[method]
        require(benchmark['repeatIterations'] == 10 and benchmark['warmupIterations'] == 0
                and benchmark['params'] == {}, 'Catalog benchmark iterations/parameters')
        # Retain platform metrics, checking every reported sample without deriving
        # deadline misses from macrobenchmark or callback totals.
        for metrics in [benchmark['metrics'], benchmark['sampledMetrics']]:
            require(isinstance(metrics, dict), 'Catalog metric map')
            for metric in metrics.values():
                runs = metric['runs']
                values = [x for run in runs for x in (run if isinstance(run, list) else [run])]
                require(len(runs) == 10 and values and all(finite(v) for v in values), 'Catalog complete finite metric samples')
        prefix = f"{p['catalogRunId']}-{p['catalogRevision']}-{workload}-{mode}-"
        # Sidecars and traces are siblings of benchmarkData in AGP output.
        parent = files[0].parent.relative_to(reader.root)
        def name(suffix):
            return (parent/(prefix+suffix)).as_posix()
        transport = reader.json(name('fixture-transport.json'))
        fixture = fixture_bytes(transport['encoded'], transport['sha256'])
        require(transport['sha256'] == baseline['fixture_sha256'], 'baseline fixture hash differs')
        transports.append(canonical(transport))
        require(reader.read(name('fixture.json'), 524288) == fixture[0], 'method fixture/transport bytes differ')
        if 'catalogFixture' in p:
            require(transport == {'encoded': p['catalogFixture'], 'sha256': p['catalogFixtureSha256']}, 'replay fixture regenerated/reordered')
        fixtures.append(fixture[0])
        seed_name = name('seed-receipt.json')
        if (reader.root/seed_name).exists():
            receipt = reader.json(seed_name)
            require(receipt['phase'] == 'closed' and receipt['declared_server_image'] == SERVER
                    and re.fullmatch(r'http://(?:10\.0\.2\.2|127\.0\.0\.1):[0-9]+/?', receipt['server'])
                    and re.fullmatch(r'catalog-[a-f0-9]{16}@example.com', receipt['email'])
                    and integer(receipt['user_id'], 1) and integer(receipt['workspace_id'], 1), 'seed receipt phase/server/identity')
            cleanup(receipt['cleanup'])
            require(receipt['cleanup'] == fixture[1]['origin']['cleanup'], 'seed cleanup receipt/fixture mismatch')
            seed_receipts.append(seed_name)
        indices = list(range(10)) if mode == 'first' else [0]
        frames = 0; touches = 0; phases = []; accumulated = []
        for i in range(10):
            if i in indices:
                opening = reader.json(name(f'opening-{i}.json'))
                ready = reader.json(name(f'ready-{i}.json'))
                require(set(opening) == {'schema', 'run', 'revision', 'workload', 'phase', 'fixture_sha256',
                                        'compilation', 'absolute_pixel_travel', 'generation', 'snapshot', 'stream'}
                        and opening['schema'] == 'catalog-window-v1' and opening['run'] == p['catalogRunId']
                        and opening['revision'] == p['catalogRevision'] and opening['generation'] == i
                        and opening['workload'] == workload and opening['phase'] == mode
                        and opening['compilation'] == 'Full' and opening['fixture_sha256'] == digest(fixture[0])
                        and opening['absolute_pixel_travel'] is None, 'opening generation/fixture identity')
                snap = snapshot(opening['snapshot'], recipe, fixture, workload)
                snapshot(ready, recipe, fixture, workload)
                stable_snapshot(snap, baseline, workload); stable_snapshot(ready, baseline, workload)
                readiness[(workload, mode, i)] = ready
                require(clock(ready['clock']) <= clock(snap['clock']) and integer(ready['quiet_draw_ns']), 'opening ready observation')
                phases = opening['stream']['phases']
                require(len(phases) == 1, 'one hardware opening phase required')
                phase(phases[0], 'opening')
                require(clock(phases[0]['end']) <= clock(ready['clock']), 'opening hardware commit readiness')
                frames, touches = stream(opening['stream'], 0, 0, phases, False)
                require(touches == 0, 'opening cannot contain measured touches')
                accumulated = list(opening['stream']['frames'])
                openings.append(opening)
            window = reader.json(name(f'window-{i}.json'))
            require(set(window) == {'schema', 'run', 'revision', 'workload', 'phase', 'fixture_sha256', 'compilation',
                                    'absolute_pixel_travel', 'index', 'generation', 'marker', 'gestures', 'before',
                                    'begin', 'after', 'end', 'complete', 'wall_ns', 'stream'}, 'window schema/incomplete flags')
            identity = f'{workload}-{mode}-{i}'
            require(window['schema'] == 'catalog-window-v1' and window['run'] == p['catalogRunId']
                    and window['revision'] == p['catalogRevision'] and (window['workload'], window['phase'], window['index']) == (workload, mode, i)
                    and window['generation'] == (i if mode == 'first' else 0) and window['fixture_sha256'] == digest(fixture[0])
                    and window['compilation'] == 'Full' and window['absolute_pixel_travel'] is None
                    and window['complete'] is True and integer(window['wall_ns'], 1) and window['wall_ns'] < 15_000_000_000
                    and window['marker'] == 'catalog/'+identity, 'window identity/completion/wall')
            require(set(window['begin']) == {'id', 'start'} and window['begin']['id'] == identity, 'begin RPC barrier')
            phase(window['end'], identity)
            require(window['begin'] == {k: window['end'][k] for k in ['id', 'start']}, 'begin/end handler mismatch')
            phases = phases + [window['end']]
            frames, touches = stream(window['stream'], frames, touches, phases, mode == 'first' or i == 9)
            accumulated.extend(window['stream']['frames'])
            for snap in [window['before'], window['after']]+[g['after'] for g in window['gestures']]:
                snapshot(snap, recipe, fixture, workload)
                stable_snapshot(snap, baseline, workload)
            require(window['before']['anchors'] == opening['snapshot']['anchors'], 'initial anchors did not reset')
            require(clock(window['before']['clock']) <= clock(window['begin']['start'])
                    < clock(window['end']['end']) <= clock(window['stream']['barrier_ack']), 'window snapshot/handler ordering')
            gestures(window, recipe)
            windows[(workload, mode, i)] = window
            if mode == 'first' or i == 9:
                for key, previous_window in windows.items():
                    if key[:2] == (workload, mode) and previous_window['generation'] == window['generation']:
                        # Reassemble across steady windows by generation; delivery
                        # order must never determine phase attribution.
                        previous_window['stream']['frames'] = list(accumulated)
        final = snapshot(reader.json(name('final-anchor.json')), recipe, fixture, workload)
        stable_snapshot(final, baseline, workload)
        require(any(a['index'] == (499 if workload == 'tags' else 999) for a in final['anchors']), 'final terminal anchor not achieved')
        final_anchors[(workload, mode)] = final
        outputs = benchmark['profilerOutputs']
        require(len(outputs) == 10, 'ten Catalog trace references required')
        seen = set()
        for output in outputs:
            require(set(output) == {'type', 'label', 'filename'} and output['type'] == 'PerfettoTrace', 'Catalog profiler reference')
            match = re.fullmatch(r'Trace Iteration ([0-9])', output['label'])
            require(match is not None, 'Catalog trace iteration')
            index = int(match[1]); filename = output['filename']
            require(index not in seen and isinstance(filename, str) and re.fullmatch(
                r'CatalogFrameBenchmark_'+method+f'_iter{index:03d}'+r'_\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}\.perfetto-trace', filename),
                'Catalog foreign/duplicate/escaped trace reference')
            seen.add(index)
            relative = (parent/filename).as_posix()
            raw = reader.read(relative, 16*1024*1024)
            # Reuse the maintained bounded Perfetto container/wire validator.
            artifact = scoped.trace_evidence(reader.root, reader.root/relative, reader.started)
            require(artifact['sha256'] == digest(raw) and digest(raw) not in hashes, 'duplicate/changed trace bytes')
            hashes.add(digest(raw)); traces[(workload, mode, index)] = relative
        all_names.update(reader.seen)
    require(len(windows) == len(traces) == 40 and len(openings) == 22 and len(set(fixtures)) == 1, 'Catalog complete window/trace/opening/fixture inventory')
    require(len(set(transports)) == 1, 'fixture transport changed across methods')
    prefix = (parent/(p['catalogRunId']+'-'+p['catalogRevision']+'-')).as_posix()
    source_openings = []
    for workload in ['tags', 'trash']:
        record = baseline['workloads'][workload]; source_phase = record['source_phase']
        ready = readiness[(workload, source_phase, 0)]
        require(clock(ready['clock']) == min(clock(v['clock']) for k, v in readiness.items() if k[0] == workload), 'baseline source_phase is not the first ready observation')
        ready_name = next(n for n in reader.seen if n.endswith(f'-{workload}-{source_phase}-ready-0.json'))
        opening_name = ready_name.replace('-ready-0.json', '-opening-0.json')
        source_openings.append(opening_name)
        baseline_name = prefix+workload+'-baseline.json'
        require((reader.root/ready_name).stat().st_mtime <= (reader.root/baseline_name).stat().st_mtime
                <= (reader.root/opening_name).stat().st_mtime, 'baseline not frozen between ready/opening observations')
    require(max((reader.root/(prefix+w+'-baseline.json')).stat().st_mtime for w in ['tags', 'trash'])
            <= (reader.root/(prefix+'baseline.json')).stat().st_mtime
            <= max((reader.root/n).stat().st_mtime for n in source_openings), 'combined baseline timing/closure')
    require(len(seed_receipts) == (1 if 'catalogSeed' in p else 0), 'seed/replay receipt inventory')
    actual = {f.relative_to(reader.root).as_posix() for f in reader.root.rglob('*') if f.is_file()}
    require(actual == all_names, 'missing/extra raw Catalog artifact')
    return {'windows': windows, 'traces': traces, 'fixture_sha256': digest(fixtures[0]), 'openings': len(openings), 'final_anchors': final_anchors,
            'baseline': baseline, 'baseline_sha256': digest(reader.read(prefix+'baseline.json')),
            'replay_arguments': baseline['replay_arguments'], 'fixture_transport': scoped.strict_json(transports[0])}


def original_copies(config, record, started):
    """The unchanged collector dereferences links. Inspect its original inputs too.

    These directories already exist and are read only. Checking only retained
    files would lose adopted-path symlink/hard-link provenance at copytree.
    """
    pins = {}
    for key, source in [('results', 'app/build/outputs/androidTest-results/connected/debug'),
                        ('benchmark-results', 'macrobenchmark/build/outputs/androidTest-results/connected/benchmark'),
                        ('benchmark-data', 'macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark')]:
        original = Reader(Path(config['project'])/source, started)
        retained = Reader(Path(record[key]), started)
        actual = {p.relative_to(original.root).as_posix() for p in original.root.rglob('*') if p.is_file() or p.is_symlink()}
        copied = {p.relative_to(retained.root).as_posix() for p in retained.root.rglob('*') if p.is_file() or p.is_symlink()}
        require(actual == copied and actual, 'original/retained Catalog corpus membership changed')
        for name in actual:
            require(original.read(name, 64*1024*1024, allow_empty=True) == retained.read(name, 64*1024*1024, allow_empty=True), 'original/retained Catalog bytes changed')
        pins.update({str(original.root/name): h for name, h in original.close().items()})
        retained.close()
    return pins


def assess(config, api, label, args, seconds, record, started, exit_code, stop_reason):
    result = {'profile': PROFILE, 'passed': False, 'operational_success': False, 'product_tests_success': False,
              'raw_capture_complete': False, 'evidence_complete': False, 'matched_work': 'UNKNOWN', 'quiet_host': 'UNKNOWN',
              'comparative_valid': False, 'runtime_acceptance': 'UNVERIFIED', 'absolute_pixel_travel': 'UNKNOWN',
              'composed_rows': 'UNKNOWN', 'dropped_phase_attribution': 'UNKNOWN', 'late_callback_boundary': 'UNKNOWN', 'reason': None}
    try:
        if __package__:
            from .configuration import validate
        else:
            from configuration import validate
        root = Path(config['run_dir'])
        current = validate(scoped.strict_json((root/'run-config.json').read_bytes()), fresh=False)
        require(current == config, 'frozen Catalog configuration identity changed')
        jobs = [j for j in config['plan'] if j['label'] == label]
        require(len(jobs) == 1, 'Catalog configured job identity')
        job = jobs[0]
        require((api, label, args, seconds, False) == (job['api'], LABEL, job['args'], job['seconds'], job['standalone']), 'actual Catalog job differs')
        recipe = validate_recipe(config['project'], job, config['input_pins'])
        require(config['checked_head'] == recipe['checked_head'], 'Catalog configured composite HEAD mismatch')
        require(exit_code == record['exit'] == 0 and not stop_reason and not record['stop_reason'], 'Catalog operational/finalization failure')
        require(record['api_requested'] == api and record['label'] == label and record['standalone'] is False
                and record['command'][-len(args):] == args and 'TOKKL_TEST_AVD=tokkl-api36' in record['command'], 'Catalog controller identity/argv')
        clean = record['cleanup']
        require(all(clean[k] is True for k in ['work_directories_removed', 'private_android_tmp_removed', 'emulator_scopes_absent'])
                and clean['new_shared_android_files'] == [] and clean['work_directories'] and clean['private_android_tmp']
                and clean['observed_android_tmp_env'] and all(v['ANDROID_TMP'] in clean['private_android_tmp'] for v in clean['observed_android_tmp_env'].values()),
                'Catalog controller cleanup incomplete')
        reader = Reader(root, started)
        text = reader.read(Path(record['log']).relative_to(root).as_posix(), 16*1024*1024).decode()
        devices = re.findall(r'(?m)^emulator-[0-9]+ runs .* on API [0-9]+$', text)
        require(len(devices) == 1 and re.fullmatch(r'emulator-[0-9]+ runs tokkl-api36 on API 36', devices[0])
                and devices == record['device_lines'], 'Catalog actual API36 device evidence')
        manifest = scoped.strict_json((root/'runtime-manifest.json').read_bytes())
        require(manifest and all(scoped.sha(scoped.checked_file(root, n)) == h for n, h in manifest.items())
                and manifest.get('catalog_profile.py') == scoped.sha(__file__)
                and manifest.get('scoped_results.py') == scoped.sha(scoped.__file__), 'Catalog frozen runtime changed')
        result['operational_success'] = True
        parser = source_module(config['project'], 'scripts/sift_telemetry.py', VERIFICATION['scripts/sift_telemetry.py'])
        for key in ['results', 'benchmark-results', 'benchmark-data']:
            directory = Path(record[key])
            require(directory.is_dir() and directory.resolve() == directory and directory.is_relative_to(root), 'Catalog result corpus escapes run')
        original_files = original_copies(config, record, started)
        expected = {(r['class'], m) for r in recipe['inventory'][:2] for m in r['methods']}
        app = scoped.exact_junit(parser, record['results'], expected, record['counts'], started)
        bench = scoped.exact_junit(parser, record['benchmark-results'], {(CLASS, m) for m in METHODS}, record['benchmark_counts'], started)
        require(sorted((c['class'], c['test']) for c in record['benchmark_cases']) == bench[1], 'Catalog recorded benchmark cases')
        result.update(product_tests_success=True, app_counts=app[0], benchmark_counts=bench[0])
        raw = Reader(Path(record['benchmark-data']), started)
        capture = raw_capture(raw, recipe)
        result.update(passed=True, raw_capture_complete=True, windows=len(capture['windows']), traces=len(capture['traces']),
                      openings=capture['openings'], fixture_sha256=capture['fixture_sha256'], raw_files=raw.close(),
                      original_files=original_files,
                      recipe_sha256=digest(canonical(recipe)), configuration_sha256=config['_sha256'],
                      capture_started=started, runtime_sha256=digest(canonical(manifest)), opening_benefit='UNVERIFIED',
                      baseline_sha256=capture['baseline_sha256'], replay_arguments=capture['replay_arguments'], fixture_transport=capture['fixture_transport'],
                      apks=recipe['apks'], full_app_gate=False, offline_assessment='PENDING')
        reader.close()
    except ERRORS as error:
        result.update(passed=False, raw_capture_complete=False, reason=str(error))
    return result


def tables(reader, directory):
    result = {}
    for key, header in HEADERS.items():
        raw = reader.read((Path(directory)/(key+'.csv')).as_posix(), 32*1024*1024).decode()
        rows = list(csv.reader(io.StringIO(raw, newline=''), strict=True))
        require(rows and rows[0] == header and all(len(r) == len(header) for r in rows[1:]), 'CSV header/arity: '+key)
        for row in rows[1:]:
            for field, cell in zip(header, row):
                if field in ['layer_name', 'name']:
                    require(cell and '\0' not in cell, 'CSV text cell')
                elif cell == '[NULL]':
                    require(key == 'frames' and field in ['expected_id', 'expected_ts', 'expected_dur'], 'unexpected CSV NULL')
                else:
                    require(re.fullmatch(r'-?(?:0|[1-9][0-9]*)', cell), 'CSV integer/NULL representation')
        result[key] = [dict(zip(header, row)) for row in rows[1:]]
    return result


def query_module(project):
    return source_module(project, 'scripts/catalog-frame-summary.py', OVERLAY['scripts/catalog-frame-summary.py'])


def source_module(project, name, expected):
    path = scoped.checked_file(project, name)
    raw = scoped.fresh_bytes(Path(project), path, 0, 4*1024*1024)
    require(digest(raw) == expected, 'reviewed parser/query source changed')
    module = types.ModuleType('reviewed_catalog_source')
    module.__file__ = str(path)
    # No importlib cache writes into a historical project during pure assessment.
    exec(compile(raw, str(path), 'exec'), module.__dict__)
    return module


def exports(reader, declaration, export, trace, queries, started):
    """Validate retained derivation, never run the processor or a shell wrapper."""
    tool = declaration['processor']
    require(set(export['csv_sha256']) == set(export['commands']) == set(HEADERS), 'seven exports and command receipts required')
    require(export['trace_sha256'] == digest(reader.read(trace, 16*1024*1024)), 'export trace input identity')
    for key in HEADERS:
        receipt = reader.json(export['commands'][key])
        query = declaration['queries'][key]
        query_raw = reader.read(query)
        require(query_raw == (queries[key]+'\n').encode(), 'literal reviewed Catalog SQL required')
        stdout = (Path(export['directory'])/(key+'.csv')).as_posix()
        require(receipt['schema'] == 'catalog-export-command-v1' and receipt['trace'] == trace
                and receipt['trace_sha256'] == export['trace_sha256'] and receipt['processor_sha256'] == tool['sha256']
                and receipt['query_sha256'] == digest(query_raw) and receipt['stdout'] == stdout
                and receipt['stdout_sha256'] == export['csv_sha256'][key] == digest(reader.read(stdout, 32*1024*1024))
                and receipt['argv'] == [str(reader.root/tool['path']), 'query', '-f', str(reader.root/query), str(reader.root/trace)]
                and type(receipt['exit']) is int and receipt['exit'] == 0, 'export argv/hash/exit derivation receipt')
        require(reader.read(receipt['stderr'], allow_empty=True) == b'', 'export stderr must be retained and empty')
        require(finite(receipt['started']) and finite(receipt['finished'])
                and started <= receipt['started'] <= (reader.root/stdout).stat().st_mtime <= receipt['finished'],
                'export execution freshness')
        wrapper = reader.json(receipt['wrapper'])
        require(wrapper['schema'] == 'catalog-bounded-export-v1' and wrapper['closed'] is True
                and wrapper['cleanup_complete'] is True and wrapper['actual_affinity'] == [wrapper['cpu']]
                and integer(wrapper['cpu']) and wrapper['source_sha256'] == digest(reader.read(wrapper['source']))
                and finite(wrapper['wall_seconds']) and 0 <= wrapper['wall_seconds'] <= wrapper['wall_limit_seconds']
                and receipt['finished']-receipt['started'] <= wrapper['wall_seconds']+.01
                and finite(wrapper['cpu_seconds']) and 0 <= wrapper['cpu_seconds'] <= wrapper['cpu_limit_seconds']
                and 0 < wrapper['wall_limit_seconds'] <= 60 and 0 < wrapper['cpu_limit_seconds'] <= 30
                and wrapper['command_receipt'] == export['commands'][key], 'bounded export wrapper/closure receipt')


def quiet_receipt(reader, name, declaration, intervals, revision, configuration_sha256):
    value = reader.json(name)
    authority = declaration['quiet_authority']
    require(value['schema'] == 'catalog-quiet-receipt-v1' and value['authority_sha256'] == digest(reader.read(authority))
            and value['revision'] == revision and value['configuration_sha256'] == configuration_sha256
            and value['host'] == declaration['host'] and value['intervals'] == intervals
            and value['continuous'] is True and value['observer_closed'] is True
            and value['unknown_intervals'] == [] and value['gaps'] == [] and value['breaches'] == []
            and value['verdict'] == 'PASS' and value['thresholds_sha256'] == digest(reader.read(value['thresholds']))
            and value['inputs'] and all(digest(reader.read(p, 32*1024*1024)) == h for p, h in value['inputs'].items()),
            'external quiet-host receipt incomplete/foreign/breached')
    # The external authority owns interpretation and sampling. This code binds
    # its bounded receipt; it does not reinterpret admission as quiet evidence.


def certified_seed(run, adoption, state_path):
    """A complete raw seed capture is required; a combined baseline alone fails."""
    run = Path(run)
    raw = scoped.strict_json((run/'run-config.json').read_bytes())
    require(len(raw['plan']) == 1 and 'catalogSeed' in parameters(raw['plan'][0]['args'])
            and raw['plan'][0]['result_recipe'].get('accepted_seed') is None,
            'accepted seed reference must identify a canonical eager seed, never replay/cycles')
    if __package__:
        from .configuration import adopted_configuration
    else:
        from configuration import adopted_configuration
    config = adopted_configuration(run, adoption)
    require(config['run_dir'] == str(run), 'accepted seed run identity')
    state = scoped.strict_json(Path(state_path).read_bytes())
    require(len(state['tests']) == 1, 'accepted seed record inventory')
    record = state['tests'][0]; job = config['plan'][0]
    receipt = record['result_contract']
    require(receipt['passed'] is True and receipt['raw_capture_complete'] is True, 'incomplete seed cannot authorize replay')
    observed = assess(config, job['api'], job['label'], job['args'], job['seconds'], record,
                      receipt['capture_started'], record['exit'], record['stop_reason'])
    require(observed['passed'] and observed == receipt, 'accepted seed capture changed or incomplete: '+str(observed['reason']))
    return config, record, observed


def seed_inputs(config, record, observed, adoption, state):
    root = Path(config['run_dir'])
    manifest = scoped.strict_json((root/'runtime-manifest.json').read_bytes())
    inputs = dict(config['input_pins']) | observed['original_files']
    names = [root/'run-config.json', root/'runtime-manifest.json', Path(adoption), Path(state), Path(record['log'])]
    names += [root/n for n in manifest]
    for key in ['results', 'benchmark-results', 'benchmark-data']:
        names += [p for p in Path(record[key]).rglob('*') if p.is_file()]
    for path in names:
        require(path.is_absolute() and path.resolve() == path, 'accepted seed input must be canonical')
        inputs[str(path)] = scoped.sha(path)
    return inputs


def make_seed_reference(run_dir, adoption_path):
    """Public read-only declaration after a COMPLETE raw seed capture.

    This binds later replay inputs. It certifies no quiet/performance conclusion
    and grants no execution, preparation or accounting allowance.
    """
    run = Path(run_dir).resolve(); adoption = Path(adoption_path).resolve()
    state = run/'controller-state.json'
    config, record, observed = certified_seed(run, adoption, state)
    return {'schema': 'catalog-accepted-seed-v1', 'run_dir': str(run), 'adoption': str(adoption),
            'controller_state': str(state), 'configuration_sha256': config['_sha256'],
            'receipt_sha256': digest(canonical(observed)), 'inputs': seed_inputs(config, record, observed, adoption, state)}


def validate_seed_reference(reference):
    require(isinstance(reference, dict) and set(reference) == {'schema', 'run_dir', 'adoption', 'controller_state',
            'configuration_sha256', 'receipt_sha256', 'inputs'} and reference['schema'] == 'catalog-accepted-seed-v1',
            'complete accepted seed recipe reference required')
    for key in ['run_dir', 'adoption', 'controller_state']:
        path = Path(reference[key])
        require(path.is_absolute() and path.resolve() == path, 'canonical accepted seed reference required')
    require(reference['controller_state'] == str(Path(reference['run_dir'])/'controller-state.json')
            and isinstance(reference['inputs'], dict) and 0 < len(reference['inputs']) <= 8192
            and all(Path(n).is_absolute() and Path(n).resolve() == Path(n) and hexhash(h) and scoped.sha(n) == h
                    for n, h in reference['inputs'].items()), 'accepted seed input changed/incomplete')
    config, record, observed = certified_seed(Path(reference['run_dir']), reference['adoption'], reference['controller_state'])
    require(reference['configuration_sha256'] == config['_sha256'] and reference['receipt_sha256'] == digest(canonical(observed))
            and reference['inputs'] == seed_inputs(config, record, observed, reference['adoption'], reference['controller_state']), 'accepted seed reference identity/complete pins differ')
    return observed


def assess_comparison(declaration_path):
    """Public pure offline entry point for a separately frozen evidence declaration.

    Pins cover every read, including adopted raw runs, tool/query/command inputs,
    fixture origin and the external quiet authority. The caller reviews this
    declaration after offline work; it cannot expand the device argv grammar.
    """
    result = {'profile': PROFILE, 'operational_success': False, 'product_tests_success': False,
              'evidence_complete': False, 'matched_work': False, 'quiet_host': False, 'comparative_valid': False,
              'runtime_acceptance': 'UNVERIFIED', 'absolute_pixel_travel': 'UNKNOWN', 'composed_rows': 'UNKNOWN',
              'dropped_phase_attribution': 'UNKNOWN', 'late_callback_boundary': 'UNKNOWN', 'reason': None}
    try:
        declaration_path = Path(declaration_path)
        reader = Reader(declaration_path.parent.resolve(), 0)
        declaration = scoped.strict_json(declaration_path.read_bytes())
        require(declaration['schema'] == 'catalog-offline-declaration-v1' and declaration['reviewed_by']
                and declaration['host']['boot_id'] and declaration['host']['runtime_id'] and declaration['host']['host_id'],
                'reviewed offline declaration/host identity required')
        pins = declaration['pins']
        require(pins and all(hexhash(h) for h in pins.values()), 'offline exact input pins required')
        for name, h in pins.items():
            require(digest(reader.read(name, 64*1024*1024, allow_empty=True)) == h, 'offline input changed: '+name)
        external = declaration['external_pins']
        require(external and all(Path(n).is_absolute() and Path(n).resolve() == Path(n) and hexhash(h)
                and scoped.sha(n) == h for n, h in external.items()), 'offline external source pins changed')
        require([r['revision'] for r in declaration['captures']] == list(REVISIONS), 'ordered eager/lazy captures required')
        corpus = reader.json(declaration['corpus'])
        require(corpus['schema'] == 'catalog-corpus-v1' and corpus['overlay_sha256'] == digest(canonical(OVERLAY))
                and [r['revision'] for r in corpus['revisions']] == list(REVISIONS), 'driver corpus overlay/revision pair')
        tool = declaration['processor']
        require(tool['version'] and tool['source_version'] and tool['source_url'].startswith('https://')
                and tool['sha256'] == digest(reader.read(tool['path'], 64*1024*1024))
                and tool['source_sha256'] == digest(reader.read(tool['source'])), 'reviewed processor source/version/bytes')
        captures = []; recipes = []; metrics = []; adopted = []
        # Establish both raw outcomes before inspecting offline exports/quiet
        # evidence, so those missing stages cannot erase known product success.
        for item in declaration['captures']:
            if __package__:
                from .configuration import adopted_configuration
            else:
                from configuration import adopted_configuration
            run = reader.root/item['run']
            config = adopted_configuration(run, reader.root/item['adoption'])
            state = reader.json(item['controller_state'])
            require(len(config['plan']) == len(state['tests']) == 1 and config['run_dir'] == str(run), 'offline run/record inventory')
            record = state['tests'][0]; job = config['plan'][0]
            observed = assess(config, job['api'], job['label'], job['args'], job['seconds'], record,
                              item['started'], record['exit'], record['stop_reason'])
            require(observed['passed'], 'raw capture failed: '+str(observed['reason']))
            require(record['result_contract'] == observed and item['started'] == observed['capture_started'],
                    'offline raw receipt/timestamp differs from certified capture')
            require(all(external.get(n) == h for n, h in observed['original_files'].items()), 'original raw input not externally pinned')
            adopted.append((config, record))
        result.update(operational_success=True, product_tests_success=True)
        trace_hashes = set()
        for item, revision_spec, (config, record) in zip(declaration['captures'], corpus['revisions'], adopted):
            run = reader.root/item['run']
            job = config['plan'][0]
            require(item['revision'] == job['result_recipe']['base_revision'], 'offline historical identity')
            for field in ['results', 'benchmark-results', 'benchmark-data']:
                for file in Path(record[field]).rglob('*'):
                    if file.is_file(): reader.read(file.relative_to(reader.root).as_posix(), 64*1024*1024)
            reader.read(Path(record['log']).relative_to(reader.root).as_posix(), 16*1024*1024)
            recipe = job['result_recipe']; recipes.append(recipe)
            raw_reader = Reader(Path(record['benchmark-data']), item['started'])
            captured = raw_capture(raw_reader, recipe)
            raw_pins = raw_reader.close()
            for name, h in raw_pins.items():
                relative = (Path(record['benchmark-data'])/name).relative_to(reader.root).as_posix()
                require(pins.get(relative) == h, 'offline raw input not pinned')
            summary = query_module(config['project'])
            traces = captured['traces']; windows = captured['windows']
            parent = Path(record['benchmark-data']).relative_to(reader.root)
            bench_paths = [(parent/Path(n)).as_posix() for n in raw_pins if n.endswith('-benchmarkData.json')]
            window_paths = [(parent/Path(n)).as_posix() for n in raw_pins if re.search(r'-window-[0-9]\.json$', n)]
            require(revision_spec['benchmarks'] == bench_paths and set(revision_spec['windows']) == set(window_paths)
                    and len(revision_spec['windows']) == 40 and set(revision_spec['analysis']) == {Path(n).name for n in traces.values()},
                    'corpus references differ from validated raw capture')
            intervals = []; per_window = {}
            for key, window in windows.items():
                relative = (parent/traces[key]).as_posix()
                export = revision_spec['analysis'][Path(relative).name]
                trace_hash = digest(reader.read(relative, 16*1024*1024))
                require(trace_hash not in trace_hashes, 'trace bytes reused across revision pair')
                trace_hashes.add(trace_hash)
                exports(reader, declaration, export, relative, summary.QUERIES, item['started'])
                table = tables(reader, export['directory'])
                actual = table['frames']; expected = table['expected']
                require(actual and expected and len({r['id'] for r in expected}) == len(expected), 'populated/unique expected frames required')
                expected_map = {r['id']: r for r in expected}
                paired = set()
                for frame in actual:
                    if frame['expected_id'] != '[NULL]':
                        require(frame['expected_id'] in expected_map and frame['expected_id'] not in paired, 'missing/ambiguous expected pairing')
                        e = expected_map[frame['expected_id']]; paired.add(frame['expected_id'])
                        require((frame['expected_ts'], frame['expected_dur']) == (e['ts'], e['dur']), 'expected frame identity changed')
                stats = summary.summarize(window, table)
                marker = next(r for r in table['markers'] if r['name'] == window['marker'])
                phase_start = int(marker['ts']); phase_end = phase_start+int(marker['dur'])
                for thread in {r['tid'] for r in table['sched']}:
                    rows = sorted((int(r['ts']), int(r['dur'])) for r in table['sched'] if r['tid'] == thread)
                    require(all(a+duration <= b for (a, duration), (b, _) in zip(rows, rows[1:])), 'overlapping guest scheduling slices')
                stats['phase_main_plus_render_scheduled_cpu_ns'] = sum(summary.overlap(phase_start, phase_end, r) for r in table['sched'])
                stats['unfinished_frames'] = sum(int(r['dur']) <= 0 and phase_start <= int(r['ts']) < phase_end for r in actual)
                require(stats['coverage_complete'], 'incomplete FrameTimeline comparison coverage')
                intervals.append([window['begin']['start']['mono_ns'], window['end']['end']['mono_ns']])
                per_window[key] = stats
            origin = reader.json(item['fixture_origin'])
            require(origin['schema'] == 'catalog-fixture-origin-v1' and origin['fixture_sha256'] == captured['fixture_sha256']
                    and origin['server_image'] == SERVER and origin['proxy_image'] == config['proxy_image']
                    and origin['owned_server_identity'] and origin['credential_source_sha256'] == digest(reader.read(origin['credential_source']))
                    and origin['source_receipt_sha256'] == digest(reader.read(origin['source_receipt']))
                    and origin['credentials_retained'] is False and origin['historical_diagnostic_server'] == '1.5.0', 'real-server source provenance/declared 1.8.1 condition')
            cleanup(origin['cleanup'])
            quiet_receipt(reader, item['quiet_receipt'], declaration, intervals, item['revision'], config['_sha256'])
            captures.append(captured); metrics.append(per_window)
        require(recipes[0]['driver_overlay_sha256'] == recipes[1]['driver_overlay_sha256']
                and recipes[0]['verification_overlay_sha256'] == recipes[1]['verification_overlay_sha256']
                and recipes[0]['device_build'] == recipes[1]['device_build']
                and captures[0]['fixture_sha256'] == captures[1]['fixture_sha256']
                and 'catalogSeed' in recipes[0]['parameters'] and 'catalogFixture' in recipes[1]['parameters']
                and recipes[1]['accepted_seed']['run_dir'] == str(reader.root/declaration['captures'][0]['run'])
                and recipes[1]['accepted_seed']['configuration_sha256'] == adopted[0][0]['_sha256'], 'paired driver/device/fixture/accepted seed identities')
        require({k: v['sha256'] for k, v in recipes[0]['tool_inputs'].items() if k != 'resolved_dependencies'}
                == {k: v['sha256'] for k, v in recipes[1]['tool_inputs'].items() if k != 'resolved_dependencies'}
                and [(r['artifact'], r['sha256']) for r in recipes[0]['dependency_inputs']]
                == [(r['artifact'], r['sha256']) for r in recipes[1]['dependency_inputs']], 'paired tool/dependency inputs differ')
        for key in captures[0]['final_anchors']:
            a = summary.anchor_signature(captures[0]['final_anchors'][key]); b = summary.anchor_signature(captures[1]['final_anchors'][key])
            require(len(a) == len(b) and all(x[:2] == y[:2] and all(abs(p-q) <= 1 for p, q in zip(x[2:], y[2:])) for x, y in zip(a, b)), 'final matched anchor geometry differs')
        rows = []
        for key in captures[0]['windows']:
            a, b = captures[0]['windows'][key], captures[1]['windows'][key]
            work = summary.matched_work(a, b)
            rows.append({'workload': key[0], 'phase': key[1], 'index': key[2], 'work': work,
                         'eager': metrics[0][key], 'lazy': metrics[1][key]})
        hashes = reader.close()
        require(all(pins.get(n) == h for n, h in hashes.items()), 'offline read lacks frozen declaration pin')
        # Also pin adoption, config, manifest and each immutable runtime source,
        # which the existing adopted entry reads rather than Reader.
        for item in declaration['captures']:
            run = reader.root/item['run']
            names = ['run-config.json', 'runtime-manifest.json']+list(scoped.strict_json((run/'runtime-manifest.json').read_bytes()))
            for name in names:
                relative = (run/name).relative_to(reader.root).as_posix()
                require(pins.get(relative) == scoped.sha(run/name), 'offline adoption/runtime input not pinned')
            require(item['adoption'] in pins, 'offline root adoption not pinned')
        result.update(operational_success=True, product_tests_success=True, evidence_complete=True, matched_work=True,
                      quiet_host=True, comparative_valid=True, windows=rows, declaration_sha256=digest(canonical(declaration)),
                      interpretation='Matched endpoints and phase-contained frames; no automatic no-regression or across-session repeatability claim.')
    except ERRORS as error:
        result['reason'] = str(error)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline-declaration', type=Path, required=True)
    args = parser.parse_args()
    result = assess_comparison(args.offline_declaration)
    print(json.dumps(result, indent=2, allow_nan=False))
    return 0 if result['comparative_valid'] else 1


if __name__ == '__main__':
    sys.exit(main())
