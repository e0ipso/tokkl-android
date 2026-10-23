"""Declared app-class and benchmark/API result scopes. Never starts a workload."""
from pathlib import Path
import hashlib
import importlib.util
import json
import math
import re
import statistics
import struct
import zipfile
import io
import zlib
import xml.etree.ElementTree as ET

PROFILE = 'scoped-app-classes-v1'
PREFIX = '-Pandroid.testInstrumentationRunnerArguments.class='
FULL_ARGS = ['scripts/instrumented-tests.sh', ':macrobenchmark:connectedBenchmarkAndroidTest', '--continue']
COMPILE_FIRST = 'compile-then-scoped-app-v1'
COMPILE_ARGS = ['./gradlew', '--no-daemon', 'assembleDebug', 'assembleDebugAndroidTest', 'check', '--stacktrace']
BENCHMARK_API = 'benchmark-api-v1'
BENCHMARK_LABEL = 'benchmark-api'
BENCHMARK_ARGS = FULL_ARGS[:-1] + ['-Pandroid.testInstrumentationRunnerArguments.package='
    'com.github.e0ipso.tokkl.benchmark,com.github.e0ipso.tokkl.api']
API_CLASSES = ['com.github.e0ipso.tokkl.api.PinnedCertificateTest',
               'com.github.e0ipso.tokkl.api.ServerSmokeTest']
BENCHMARK_CASES = [('com.github.e0ipso.tokkl.benchmark.StartupBenchmark', 'coldStart'),
                   ('com.github.e0ipso.tokkl.benchmark.TimerTapBenchmark', 'startAndStop')]
BENCHMARK_SOURCE = 'macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/'
# Expression-bodied macrobenchmarks are deliberately not accepted by the app
# reader. These reviewed bytes bind their methods, metrics, assertions and helpers.
BENCHMARK_SOURCES = {
    'Iterations.kt': 'ba2dcbcdcf997c28f1353a14b08d0604817d7c25819f9e47b338d419d62be58c',
    'StartupBenchmark.kt': 'b3276f423b857aca1f3b4a079d7ec8cbbe5fa25f2e972ff1275799685c794f61',
    'TimerTapBenchmark.kt': '8b327f3bd5d24165086824b649add4571f7e8a245efcb2c190c9764355e95a61',
    'TokklApp.kt': '6b5fbd51ec51f40d09ff62a5cc0e0a18ec4a89a723d650b9aa6aa119c9a5cc9d',
}
APK_PATHS = ['app/build/outputs/apk/debug/app-debug.apk',
             'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
             'app/build/outputs/apk/benchmark/app-benchmark.apk',
             'macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk']


def validate_plan(config):
    jobs = config['plan']
    if 'plan_recipe' not in config:
        require(not jobs[0]['standalone'], 'first opening preparation preserves the device budget')
        return
    require(config['plan_recipe'] == COMPILE_FIRST, 'unsupported declared plan recipe')
    require(len(jobs) == 2 and jobs[0].get('args') == COMPILE_ARGS
            and jobs[0].get('label') == 'assemble-check' and jobs[0].get('api') == 0
            and jobs[0].get('standalone') is True and 'result_recipe' not in jobs[0]
            and jobs[1].get('standalone') is False and 'result_recipe' in jobs[1],
            'compile-first recipe requires exactly APK assemble/check then one declared scoped app job')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    """Reuse cache-neutral bounded hashing after configuration imports settle."""
    # configuration imports this module for recipe validation. Resolve the
    # primitive only on a hash call, in either package or frozen-script mode.
    if __package__:
        from .configuration import sha as bounded_sha
    else:
        from configuration import sha as bounded_sha
    return bounded_sha(path)


def masked_kotlin(text):
    """Mask nested comments and string/character literals, preserving positions."""
    out = list(text)
    i = 0
    while i < len(text):
        start = i
        if text.startswith('//', i):
            end = text.find('\n', i)
            i = len(text) if end < 0 else end
        elif text.startswith('/*', i):
            depth = 1
            i += 2
            while i < len(text) and depth:
                if text.startswith('/*', i): depth += 1; i += 2
                elif text.startswith('*/', i): depth -= 1; i += 2
                else: i += 1
            require(depth == 0, 'unclosed Kotlin comment')
        elif text.startswith('"""', i):
            end = text.find('"""', i+3)
            require(end >= 0, 'unclosed Kotlin raw string')
            i = end+3
        elif text[i] in ['"', "'"]:
            quote = text[i]
            i += 1
            while i < len(text) and text[i] != quote:
                i += 2 if text[i] == '\\' else 1
            require(i < len(text), 'unclosed Kotlin literal')
            i += 1
        else:
            i += 1
            continue
        for pos in range(start, i):
            if out[pos] != '\n': out[pos] = ' '
    return ''.join(out)


def inventory(project, class_name):
    require(isinstance(class_name, str) and re.fullmatch(r'(?:[a-zA-Z_]\w*\.)+[A-Z]\w*', class_name),
            'whole qualified class names required; no method/package selectors')
    project = Path(project).resolve()
    relative = 'app/src/androidTest/java/'+class_name.replace('.', '/')+'.kt'
    path = project/relative
    require(path.resolve().is_relative_to(project) and not path.is_symlink(), 'source must stay inside checked project')
    raw = path.read_bytes()
    text = masked_kotlin(raw.decode())
    package, name = class_name.rsplit('.', 1)
    require(re.findall(r'(?m)^package\s+([\w.]+)\s*$', text) == [package], 'source package mismatch')
    test_imports = re.findall(r'(?m)^import[ \t]+(?:org\.junit\.(?:jupiter\.api\.)?Test|kotlin\.test\.Test)[^\n]*$', text)
    require([line.strip() for line in test_imports] == ['import org.junit.Test'],
            'only explicit unaliased JUnit4 Test import is supported')
    require(not re.search(r'@(?:[a-zA-Z_]\w*\.)*(RunWith|Parameterized|Ignore|SdkSuppress|Theory|TestFactory|RepeatedTest|JvmName)\b', text)
            and not re.search(r'@(?:[a-zA-Z_]\w*\.)+Test\b', text),
            'custom runner, parameterization or conditional test selection requires another reviewed recipe')
    header = re.search(r'(?m)^class\s+'+re.escape(name)+r'\s*\{', text)
    require(header is not None, 'only concrete direct classes without inheritance/constructor parameters are supported')
    depth = 0
    methods = []
    positions = {m.start(): m for m in re.finditer(r'@Test\b', text)}
    body_start = header.end()-1
    body_end = None
    for pos, char in enumerate(text):
        if pos in positions:
            require(pos > body_start and body_end is None and depth == 1, 'nested/inherited/other-class tests are unsupported')
            method = re.match(r'@Test\s+(?:public\s+)?fun\s+([a-zA-Z_]\w*)\s*\(\s*\)\s*(?::\s*Unit\s*)?\{', text[pos:])
            require(method is not None, 'test must be a direct zero-argument unparameterized Unit method')
            methods.append(method[1])
        if char == '{': depth += 1
        elif char == '}':
            depth -= 1
            if pos > body_start and depth == 0 and body_end is None: body_end = pos
        require(depth >= 0, 'unbalanced Kotlin source')
    require(depth == 0 and methods and len(methods) == len(set(methods)), 'empty/duplicate/unbalanced source test inventory')
    return {'class': class_name, 'source': relative, 'sha256': hashlib.sha256(raw).hexdigest(),
            'shape': 'direct-junit4-unparameterized', 'methods': sorted(methods)}


def declare(project, classes):
    require(isinstance(classes, list) and 1 <= len(classes) <= 5
            and len(set(classes)) == len(classes), 'one to five distinct whole classes required')
    return {'profile': PROFILE, 'classes': classes, 'inventory': [inventory(project, name) for name in classes]}


def validate_recipe(project, job, pins):
    recipe = job.get('result_recipe')
    if isinstance(recipe, dict) and recipe.get('profile') == BENCHMARK_API:
        expected = declare_benchmark_api(project, recipe.get('device_build'))
        require(recipe == expected, 'benchmark/API recipe differs from complete source/APK inventory')
        require(job['label'] == BENCHMARK_LABEL and job['api'] == 36
                and job['standalone'] is False and job['seconds'] == 1800,
                'benchmark/API recipe requires canonical label, API36, nonstandalone, exactly 1800 seconds')
        require(job['args'] == BENCHMARK_ARGS, 'benchmark/API recipe requires exact package-pair argv')
        for row in expected['inventory'] + expected['sources'] + expected['apks']:
            require(pins.get(str(Path(project)/row['source'])) == row['sha256'],
                    'benchmark/API source/APK missing from checked input pins')
        return expected
    require(isinstance(recipe, dict) and recipe.get('profile') == PROFILE, 'unsupported declared result recipe')
    expected = declare(project, recipe.get('classes'))
    require(recipe == expected, 'declared inventory must equal every selected class source Test method and fingerprint')
    require(job['api'] == 36 and job['standalone'] is False, 'scoped app recipe is API36 device only')
    require(job['args'] == ['scripts/instrumented-tests.sh', PREFIX+','.join(recipe['classes'])],
            'scoped recipe requires exact whole-class-list argv; benchmark/package/method tasks forbidden')
    for row in expected['inventory']:
        require(pins.get(str(Path(project)/row['source'])) == row['sha256'], 'selected source missing from checked input pins')
    parser = Path(project)/'scripts/sift_telemetry.py'
    require(pins.get(str(parser)) == sha(parser), 'actual finalized JUnit parser must be input-pinned')
    return expected


def assess_scoped(config, api, label, args, seconds, record, started, exit_code, stop_reason):
    if any(job.get('label') == label and job.get('result_recipe', {}).get('profile') == BENCHMARK_API
           for job in config['plan']):
        return assess_benchmark_api(config, api, label, args, seconds, record, started, exit_code, stop_reason)
    result = {'passed': False, 'profile': PROFILE, 'reason': None}
    try:
        if __package__:
            from .configuration import validate
        else:
            from configuration import validate
        current = validate(json.loads((Path(config['run_dir'])/'run-config.json').read_text()), fresh=False)
        require(current == config, 'frozen configuration identity changed during execution')
        jobs = [job for job in config['plan'] if job['label'] == label]
        require(len(jobs) == 1, 'check identity missing/duplicated in configuration')
        job = jobs[0]
        require((api, args, seconds, False) == (job['api'], job['args'], job['seconds'], job['standalone']),
                'actual job/API/filter/deadline differs from declared configuration')
        recipe = validate_recipe(config['project'], job, config['input_pins'])
        require(exit_code == 0 and not stop_reason, 'payload/resource/finalization outcome failed')
        require(record['api_requested'] == api and record['label'] == label and record['standalone'] is False
                and record['command'][-len(args):] == args and 'TOKKL_TEST_AVD=tokkl-api36' in record['command'],
                'controller record identity/argv mismatch')
        log = Path(record['log'])
        require(log.stat().st_mtime >= started, 'stale execution log')
        devices = re.findall(r'(?m)^emulator-[0-9]+ runs .* on API [0-9]+$', log.read_text())
        require(len(devices) == 1 and re.fullmatch(r'emulator-[0-9]+ runs tokkl-api36 on API 36', devices[0])
                and devices == record['device_lines'], 'missing/mismatched actual API36 device evidence')
        files = sorted(Path(record['results']).rglob('TEST-*.xml')) if record.get('results') else []
        parser = Path(config['project'])/'scripts/sift_telemetry.py'
        spec = importlib.util.spec_from_file_location('pinned_sift_junit_parser', parser)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        counts = module.junit_counts([str(path) for path in files], started)
        cases = set()
        for path in files:
            root = ET.parse(path).getroot()
            require(root.tag in ['testsuite', 'testsuites'], 'unsupported JUnit root')
            suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
            if root.tag == 'testsuites':
                require(len(suites) == len(root), 'nested/unknown JUnit suite structure')
                for key in ['tests', 'failures', 'errors', 'skipped']:
                    if key in root.attrib:
                        require(int(root.get(key)) == sum(int(s.get(key, 0)) for s in suites), 'aggregate JUnit count mismatch')
            for suite in suites:
                cases.update((case.get('classname'), case.get('name')) for case in suite.findall('testcase'))
        expected = {(row['class'], method) for row in recipe['inventory'] for method in row['methods']}
        require(counts == record['counts'] and cases == expected and counts['tests'] == len(expected),
                'finalized count/class/method coverage differs from complete source inventory')
        require(not any(counts[key] for key in ['failures', 'errors', 'skipped']), 'failed/error/skipped app result')
        for key, pattern in [('benchmark-results', 'TEST-*.xml'), ('benchmark-data', '*-benchmarkData.json')]:
            directory = record.get(key)
            require(not directory or not any(p.stat().st_mtime >= started for p in Path(directory).rglob(pattern)),
                    'fresh unrequested benchmark execution cannot use scoped app contract')
        result.update(passed=True, app_counts=counts, app_cases=[list(case) for case in sorted(cases)],
                      junit=[{'path': str(path), 'sha256': sha(path)} for path in files],
                      recipe_sha256=hashlib.sha256(json.dumps(recipe, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
                      configuration_sha256=config['_sha256'], expected_benchmark_cases=[])
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError) as error:
        result['reason'] = str(error)
    return result


def checked_file(root, relative):
    root = Path(root).resolve()
    relative = Path(relative)
    require(not relative.is_absolute() and '..' not in relative.parts, 'unsafe artifact/source path')
    path = root/relative
    require(path.is_file() and path.resolve().is_relative_to(root)
            and not any(p.is_symlink() for p in [path, *path.parents] if p != root),
            'missing, escaped or symlink artifact/source')
    return path


def declare_benchmark_api(project, device_build):
    """Declare only the reviewed API package and exact benchmark source bytes.

    APKs must already exist and be independently reviewed by the coordinator.
    Reading them here does not establish which bytes a device installed.
    """
    require(isinstance(device_build, dict) and isinstance(device_build.get('version'), dict)
            and type(device_build['version'].get('sdk')) is int and device_build['version']['sdk'] == 36
            and all(isinstance(device_build.get(k), str) and device_build[k]
                    for k in ['brand', 'device', 'fingerprint', 'id', 'model', 'type']),
            'complete reviewed API36 benchmark device build required')
    project = Path(project).resolve()
    api_dir = project/'app/src/androidTest/java/com/github/e0ipso/tokkl/api'
    require(sorted(str(p.relative_to(project)) for p in api_dir.rglob('*.kt')) ==
            sorted('app/src/androidTest/java/'+c.replace('.', '/')+'.kt' for c in API_CLASSES),
            'API package source membership changed; review another profile')
    rows = [inventory(project, c) for c in API_CLASSES]
    require([len(row['methods']) for row in rows] == [3, 7], 'complete ten API methods required')
    directory = project/BENCHMARK_SOURCE
    require(sorted(p.name for p in directory.rglob('*.kt')) == sorted(BENCHMARK_SOURCES),
            'benchmark package source membership changed')
    sources = []
    for name, digest in BENCHMARK_SOURCES.items():
        relative = BENCHMARK_SOURCE+name
        require(sha(checked_file(project, relative)) == digest, 'reviewed benchmark behavior changed: '+name)
        sources.append({'source': relative, 'sha256': digest})
    for cls, method in BENCHMARK_CASES:
        relative = BENCHMARK_SOURCE+cls.rsplit('.', 1)[1]+'.kt'
        text = masked_kotlin(checked_file(project, relative).read_text())
        require(re.findall(r'@Test\s+fun\s+(\w+)\(\)\s*=\s*rule\.measureRepeated\(', text) == [method]
                and len(re.findall(r'@Test\b', text)) == 1, 'benchmark source method inventory changed')
        rows.append({'class': cls, 'source': relative, 'sha256': sha(project/relative),
                     'shape': 'reviewed-macrobenchmark-expression-v1', 'methods': [method]})
    for relative in ['scripts/sift_telemetry.py', 'scripts/instrumented-tests.sh']:
        sources.append({'source': relative, 'sha256': sha(checked_file(project, relative))})
    apks = [{'source': relative, 'sha256': sha(checked_file(project, relative))} for relative in APK_PATHS]
    require(all((project/row['source']).stat().st_size > 0 for row in apks), 'empty reviewed APK')
    return {'profile': BENCHMARK_API, 'device_build': device_build,
            'inventory': rows, 'sources': sources, 'apks': apks}


def fresh_bytes(root, path, started, limit):
    path = checked_file(root, Path(path).relative_to(root))
    before = path.stat()
    require(before.st_mtime >= started and 0 < before.st_size <= limit, 'stale/empty/oversized evidence: '+str(path))
    with path.open('rb') as stream:
        raw = stream.read(limit+1)
    after = path.stat()
    require(len(raw) == before.st_size
            and all(getattr(before, k) == getattr(after, k) for k in
                    ['st_dev', 'st_ino', 'st_size', 'st_mtime_ns', 'st_ctime_ns']),
            'evidence changed while reading')
    return raw


def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'duplicate JSON key')
            result[key] = value
        return result
    def constant(value):
        raise ValueError('nonfinite JSON constant: '+value)
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=constant)


def exact_junit(parser, directory, expected, recorded, started):
    directory = Path(directory)
    files = sorted(directory.rglob('TEST-*.xml'))
    require(0 < len(files) <= 12, 'missing/excess JUnit corpus')
    cases = []
    evidence = []
    for path in files:
        raw = fresh_bytes(directory, path, started, 2*1024*1024)
        root = ET.fromstring(raw)
        require(root.tag in ['testsuite', 'testsuites'], 'unsupported JUnit root')
        suites = [root] if root.tag == 'testsuite' else list(root)
        require(all(s.tag == 'testsuite' for s in suites), 'nested/unknown JUnit structure')
        for suite in suites:
            require(not any(n.tag in ['testsuite', 'testsuites'] for n in suite.iter() if n is not suite),
                    'nested JUnit structure')
            cases.extend((case.get('classname'), case.get('name')) for case in suite.findall('testcase'))
        if root.tag == 'testsuites':
            for key in ['tests', 'failures', 'errors', 'skipped']:
                if key in root.attrib:
                    require(int(root.get(key)) == sum(int(s.get(key, 0)) for s in suites), 'aggregate JUnit count mismatch')
        evidence.append({'path': str(path), 'sha256': hashlib.sha256(raw).hexdigest()})
    counts = parser.junit_counts([str(path) for path in files], started)
    require(counts == recorded and set(cases) == expected and len(cases) == len(expected)
            and counts['tests'] == len(expected), 'complete finalized source case inventory required')
    require(not any(counts[k] for k in ['failures', 'errors', 'skipped']), 'failed/error/skipped result')
    return counts, sorted(cases), evidence


def protobuf_fields(raw):
    """Bounded wire framing only. No trace event or timing interpretation."""
    raw = memoryview(raw)
    pos = 0
    def varint():
        nonlocal pos
        value = 0
        for shift in range(0, 70, 7):
            require(pos < len(raw), 'truncated trace varint')
            byte = raw[pos]; pos += 1
            require(shift < 63 or byte <= 1, 'overflow trace varint')
            value |= (byte & 127) << shift
            if byte < 128:
                return value
        raise ValueError('overlong trace varint')
    while pos < len(raw):
        tag = varint(); number, wire = tag >> 3, tag & 7
        require(0 < number < 2**29, 'invalid trace protobuf field')
        if wire == 0:
            varint(); value = None
        else:
            require(wire in [1, 2, 5], 'unsupported trace wire')
            length = varint() if wire == 2 else 8 if wire == 1 else 4
            require(length <= len(raw)-pos, 'truncated trace field')
            value = raw[pos:pos+length]; pos += length
        yield number, wire, value


def trace_evidence(directory, path, started):
    raw = fresh_bytes(directory, path, started, 16*1024*1024)
    digest = hashlib.sha256(raw).hexdigest()
    payload = raw
    member = None
    if raw.startswith(b'PK'):
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            entries = archive.infolist()
            require(len(entries) == 1 and entries[0].filename == 'Trace_output.pb'
                    and 0 < entries[0].file_size <= 64*1024*1024
                    and entries[0].compress_type in [zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED]
                    and not entries[0].flag_bits & 1, 'unsupported/oversized trace container')
            with archive.open(entries[0]) as stream:
                payload = stream.read(64*1024*1024+1)
            require(len(payload) == entries[0].file_size, 'truncated/oversized trace container')
            member = entries[0].filename
    packets = 0
    for number, wire, value in protobuf_fields(payload):
        require(number == 1 and wire == 2 and len(value) > 0, 'invalid Perfetto Trace packet')
        require(sum(1 for _ in protobuf_fields(value)) > 0, 'empty trace packet')
        packets += 1
    require(packets > 0, 'empty Perfetto trace')
    return {'path': str(path), 'sha256': digest, 'bytes': len(raw),
            'protobuf_bytes': len(payload), 'packets': packets, 'container_member': member}


def benchmark_data(directory, recipe, started):
    directory = Path(directory)
    files = sorted(directory.rglob('*-benchmarkData.json'))
    require(len(files) == 1, 'exactly one benchmark JSON corpus required')
    raw = fresh_bytes(directory, files[0], started, 2*1024*1024)
    data = strict_json(raw)
    require(data['context']['build'] == recipe['device_build'], 'benchmark device build identity mismatch')
    benchmarks = data['benchmarks']
    require(isinstance(benchmarks, list) and len(benchmarks) == 2, 'exactly two benchmark metric cases required')
    seen = set(); names = set(); digests = set(); traces = []; metrics = []
    for benchmark in benchmarks:
        identity = (benchmark['className'], benchmark['name'])
        require(identity in BENCHMARK_CASES and identity not in seen, 'foreign/duplicate benchmark metric case')
        seen.add(identity)
        cold = identity == BENCHMARK_CASES[0]
        iterations = 15 if cold else 30
        require(type(benchmark['repeatIterations']) is int and benchmark['repeatIterations'] == iterations
                and benchmark['params'] == {} and benchmark['sampledMetrics'] == {}
                and benchmark['warmupIterations'] == 0, 'benchmark iteration/parameter contract changed')
        keys = ['timeToInitialDisplayMs'] if cold else ['TimerStartFirstMs', 'TimerStopFirstMs']
        require(set(benchmark['metrics']) == set(keys), 'missing/extra benchmark metrics')
        for key in keys:
            metric = benchmark['metrics'][key]; runs = metric['runs']
            require(isinstance(runs, list) and len(runs) == iterations
                    and all(type(v) in [int, float] and math.isfinite(v) and v > 0 for v in runs),
                    'complete finite positive benchmark runs required')
            for field, value in [('minimum', min(runs)), ('maximum', max(runs)), ('median', statistics.median(runs))]:
                require(type(metric[field]) in [int, float] and math.isfinite(metric[field])
                        and math.isclose(metric[field], value, rel_tol=1e-12, abs_tol=1e-12),
                        'inconsistent benchmark summary: '+field)
            metrics.append({'class': identity[0], 'test': identity[1], 'metric': key, 'runs': runs,
                            'median': statistics.median(runs)})
        outputs = benchmark['profilerOutputs']
        require(isinstance(outputs, list) and len(outputs) == iterations, 'complete trace references required')
        indices = set()
        for output in outputs:
            require(set(output) == {'type', 'label', 'filename'} and output['type'] == 'PerfettoTrace',
                    'unsupported trace reference')
            match = re.fullmatch(r'Trace Iteration (0|[1-9][0-9]*)', output['label'])
            require(match is not None, 'malformed trace iteration')
            index = int(match[1])
            name = output['filename']
            require(isinstance(name, str) and re.fullmatch(re.escape(identity[0].rsplit('.', 1)[1]+'_'+identity[1])
                    +r'_iter'+f'{index:03d}'+r'_\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}\.perfetto-trace', name)
                    and 0 <= index < iterations and index not in indices and name not in names,
                    'foreign/duplicate/unsafe trace identity/index')
            indices.add(index); names.add(name)
            artifact = trace_evidence(directory, files[0].parent/name, started)
            require(artifact['sha256'] not in digests, 'duplicated trace content')
            digests.add(artifact['sha256']); traces.append(artifact)
    actual = {p.relative_to(directory).as_posix() for p in directory.rglob('*.perfetto-trace')}
    expected = {Path(t['path']).relative_to(directory).as_posix() for t in traces}
    require(actual == expected and len(traces) == 45, 'missing/extra actual trace artifacts')
    return {'metrics': metrics, 'metric_values': sum(len(m['runs']) for m in metrics), 'traces': traces,
            'json': {'path': str(files[0]), 'sha256': hashlib.sha256(raw).hexdigest()}}


def assess_benchmark_api(config, api, label, args, seconds, record, started, exit_code, stop_reason):
    result = {'passed': False, 'profile': BENCHMARK_API, 'reason': None}
    try:
        if __package__:
            from .configuration import validate
        else:
            from configuration import validate
        run_dir = Path(config['run_dir'])
        current = validate(json.loads((run_dir/'run-config.json').read_text()), fresh=False)
        require(current == config, 'frozen configuration identity changed during execution')
        jobs = [j for j in config['plan'] if j['label'] == label]
        require(len(jobs) == 1, 'missing/duplicate configured job')
        job = jobs[0]
        require((api, args, seconds, False) == (job['api'], job['args'], job['seconds'], job['standalone']),
                'actual job/API/filter/deadline differs from declared configuration')
        recipe = validate_recipe(config['project'], job, config['input_pins'])
        require(exit_code == 0 and not stop_reason and record['exit'] == 0 and not record['stop_reason'],
                'payload/resource/finalization outcome failed')
        require(record['api_requested'] == api and record['label'] == label and record['standalone'] is False
                and record['command'][-len(args):] == args and 'TOKKL_TEST_AVD=tokkl-api36' in record['command'],
                'controller record identity/argv mismatch')
        cleanup = record['cleanup']
        require(all(cleanup[k] is True for k in ['work_directories_removed', 'private_android_tmp_removed', 'emulator_scopes_absent'])
                and cleanup['new_shared_android_files'] == [] and cleanup['work_directories']
                and cleanup['private_android_tmp'] and cleanup['observed_android_tmp_env']
                and all(v['ANDROID_TMP'] in cleanup['private_android_tmp'] for v in cleanup['observed_android_tmp_env'].values()),
                'incomplete controller cleanup evidence')
        log = Path(record['log'])
        text = fresh_bytes(run_dir, log, started, 16*1024*1024).decode()
        devices = re.findall(r'(?m)^emulator-[0-9]+ runs .* on API [0-9]+$', text)
        require(len(devices) == 1 and re.fullmatch(r'emulator-[0-9]+ runs tokkl-api36 on API 36', devices[0])
                and devices == record['device_lines'], 'missing/mismatched actual API36 device evidence')
        manifest = strict_json((run_dir/'runtime-manifest.json').read_bytes())
        require(manifest and all(sha(checked_file(run_dir, name)) == digest for name, digest in manifest.items())
                and manifest.get('scoped_results.py') == sha(__file__), 'frozen runtime identity changed')
        parser_path = Path(config['project'])/'scripts/sift_telemetry.py'
        spec = importlib.util.spec_from_file_location('pinned_benchmark_junit_parser', parser_path)
        parser = importlib.util.module_from_spec(spec); spec.loader.exec_module(parser)
        for key in ['results', 'benchmark-results', 'benchmark-data']:
            directory = Path(record[key])
            require(directory.is_dir() and directory.resolve() == directory
                    and directory.is_relative_to(run_dir), 'result corpus must stay inside the frozen run directory')
        inventory_rows = recipe['inventory']
        app_expected = {(r['class'], m) for r in inventory_rows[:2] for m in r['methods']}
        app = exact_junit(parser, record['results'], app_expected, record['counts'], started)
        bench = exact_junit(parser, record['benchmark-results'], set(BENCHMARK_CASES), record['benchmark_counts'], started)
        require(sorted((c['class'], c['test']) for c in record['benchmark_cases']) == bench[1],
                'controller benchmark case identity mismatch')
        evidence = benchmark_data(record['benchmark-data'], recipe, started)
        result.update(evidence, passed=True, app_counts=app[0], app_cases=app[1], benchmark_counts=bench[0],
                      benchmark_cases=bench[1], junit=app[2]+bench[2], apks=recipe['apks'],
                      configuration_sha256=config['_sha256'],
                      runtime_sha256=hashlib.sha256(json.dumps(manifest, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
                      recipe_sha256=hashlib.sha256(json.dumps(recipe, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
                      full_app_gate=False, comparison='not performed')
    except (OSError, ValueError, KeyError, TypeError, AttributeError, OverflowError, ET.ParseError,
            zipfile.BadZipFile, RuntimeError, struct.error, zlib.error) as error:
        result['reason'] = str(error)
    return result
